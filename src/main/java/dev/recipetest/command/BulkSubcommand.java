/*
 * Copyright (c) 2026 ericfisherdev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.recipetest.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.recipetest.api.BulkProgress;
import dev.recipetest.api.BulkResult;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.TestContext;
import dev.recipetest.core.HarnessRegistry;
import dev.recipetest.core.ProgressReporter;
import dev.recipetest.core.RecipeAdapter;
import dev.recipetest.core.RecipeAdapters;
import dev.recipetest.core.TickScheduler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * {@code /recipe_test bulk <recipeType>} and {@code /recipe_test bulk all} — enqueue every
 * recipe matching the requested scope into the {@link TickScheduler} and return a run id
 * immediately. Progress and final result stream through {@link ProgressReporter}.
 */
final class BulkSubcommand {

    /** Fixed origin for bulk-run placement — well below the world surface so chunks aren't
     *  fighting structures the user actually built. */
    private static final BlockPos BULK_ORIGIN = new BlockPos(0, -60, 0);

    private static final SimpleCommandExceptionType BAD_RESOURCE_LOCATION =
            new SimpleCommandExceptionType(Component.literal("argument is not a valid namespace:path"));
    private static final SimpleCommandExceptionType SPEC_NOT_REGISTERED =
            new SimpleCommandExceptionType(Component.literal("no spec registered for that recipeType"));
    private static final SimpleCommandExceptionType ALREADY_RUNNING =
            new SimpleCommandExceptionType(Component.literal("a bulk run is already in flight"));

    private BulkSubcommand() {}

    /** {@code /recipe_test bulk <recipeType>} — every recipe of one type. */
    static int runOne(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation recipeType =
                parseId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "recipeType"));
        MachineSpec spec = HarnessRegistry.instance().byRecipeType(recipeType).orElseThrow(SPEC_NOT_REGISTERED::create);
        ServerLevel level = ctx.getSource().getLevel();
        List<TickScheduler.Job> jobs = collectJobs(level, List.of(spec));
        if (jobs.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("no recipes found for " + recipeType));
            return 0;
        }
        return submit(ctx.getSource(), recipeType.toString(), level, jobs, /* shuffleSeed */ null);
    }

    /** {@code /recipe_test bulk all} — every recipe across every registered spec. */
    static int runAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        Collection<MachineSpec> specs = HarnessRegistry.instance().all();
        if (specs.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("no specs registered"));
            return 0;
        }
        List<TickScheduler.Job> jobs = collectJobs(level, specs);
        if (jobs.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("no recipes found across registered specs"));
            return 0;
        }
        // Deterministic shuffle keyed on the run id avoids systematic bias toward early specs
        // if the run is interrupted; aggregate counts remain identical between two consecutive
        // bulk-all runs because the same set of jobs is processed regardless of order.
        return submit(ctx.getSource(), "all", level, jobs, /* shuffleSeed */ Long.valueOf(System.nanoTime()));
    }

    private static int submit(
            CommandSourceStack source,
            String recipeTypeLabel,
            ServerLevel level,
            List<TickScheduler.Job> jobs,
            java.lang.@org.jetbrains.annotations.Nullable Long shuffleSeed)
            throws CommandSyntaxException {
        if (TickScheduler.instance().activeRunId().isPresent()) {
            throw ALREADY_RUNNING.create();
        }
        if (shuffleSeed != null) {
            Collections.shuffle(jobs, new Random(shuffleSeed));
        }
        TestContext testContext = new TestContext(level.getServer(), level, BULK_ORIGIN, level.registryAccess());

        boolean structured = source.getEntity() == null;
        Consumer<BulkProgress> progressSink = structured
                ? p -> source.sendSuccess(() -> Component.literal(ProgressReporter.progressJson(p)), false)
                : p -> source.sendSuccess(() -> Component.literal(ProgressReporter.progressPretty(p)), false);
        Consumer<BulkResult> finalSink = structured
                ? r -> source.sendSuccess(() -> Component.literal(ProgressReporter.resultJson(r)), false)
                : r -> {
                    for (String line : ProgressReporter.resultPretty(r)) {
                        source.sendSuccess(() -> Component.literal(line), false);
                    }
                };

        String runId = TickScheduler.instance().submit(recipeTypeLabel, testContext, jobs, progressSink, finalSink);
        source.sendSuccess(
                () -> Component.literal("Scheduled bulk run " + runId + " (" + jobs.size() + " recipes)"), false);
        return 1;
    }

    /**
     * Resolve every recipe matching the given specs into {@link TickScheduler.Job}s. Recipes
     * without an applicable {@link RecipeAdapter} are silently skipped — Phase 5's L2 SPI will
     * provide adapters for non-vanilla recipe types.
     */
    private static List<TickScheduler.Job> collectJobs(ServerLevel level, Collection<MachineSpec> specs) {
        RecipeManager recipeManager = level.getServer().getRecipeManager();
        List<TickScheduler.Job> jobs = new ArrayList<>();
        for (MachineSpec spec : specs) {
            for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                ResourceLocation actualType = level.registryAccess()
                        .registry(Registries.RECIPE_TYPE)
                        .orElseThrow()
                        .getKey(holder.value().getType());
                if (actualType == null || !actualType.equals(spec.recipeType())) {
                    continue;
                }
                java.util.Optional<RecipeAdapter> adapter = RecipeAdapters.findFor(holder.value());
                if (adapter.isEmpty()) {
                    continue;
                }
                jobs.add(new TickScheduler.Job(spec, holder, adapter.get(), spec.recipeType() + ".json"));
            }
        }
        return jobs;
    }

    private static ResourceLocation parseId(String raw) throws CommandSyntaxException {
        ResourceLocation rl = ResourceLocation.tryParse(raw);
        if (rl == null) {
            throw BAD_RESOURCE_LOCATION.create();
        }
        return rl;
    }
}
