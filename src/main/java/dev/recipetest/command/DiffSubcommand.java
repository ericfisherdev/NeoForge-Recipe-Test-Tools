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
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.TestContext;
import dev.recipetest.api.TestResult;
import dev.recipetest.core.RecipeAdapter;
import dev.recipetest.core.RecipeAdapters;
import dev.recipetest.core.RecipeTestRunner;
import dev.recipetest.core.RunSessionScheduler;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * {@code /recipe_test diff <recipeType> <recipeId>} — runs the recipe once and prints a
 * side-by-side expected/actual table. Distinct from {@code /recipe_test run} only in the way the
 * result is rendered — the actual harness work is identical.
 */
final class DiffSubcommand {

    private static final SimpleCommandExceptionType SPEC_NOT_REGISTERED =
            new SimpleCommandExceptionType(Component.literal("no spec registered for that recipeType"));
    private static final SimpleCommandExceptionType RECIPE_NOT_FOUND =
            new SimpleCommandExceptionType(Component.literal("recipe not found"));
    private static final SimpleCommandExceptionType NO_ADAPTER =
            new SimpleCommandExceptionType(Component.literal("no RecipeAdapter applies to this recipe"));

    private DiffSubcommand() {}

    static int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation recipeType =
                RunSubcommand.parseId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "recipeType"));
        ResourceLocation recipeId =
                RunSubcommand.parseId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "recipeId"));

        MachineSpec spec = RunSubcommand.findSpec(recipeType).orElseThrow(SPEC_NOT_REGISTERED::create);

        ServerLevel level = ctx.getSource().getLevel();
        RecipeManager recipeManager = level.getServer().getRecipeManager();
        RecipeHolder<?> holder = recipeManager.byKey(recipeId).orElseThrow(RECIPE_NOT_FOUND::create);
        RunSubcommand.verifyRecipeType(level, holder, recipeType);
        RecipeAdapter adapter = RecipeAdapters.findFor(holder.value()).orElseThrow(NO_ADAPTER::create);

        BlockPos origin = ctx.getSource().getPosition() != null
                ? BlockPos.containing(ctx.getSource().getPosition()).above(2)
                : new BlockPos(0, 64, 0);
        TestContext testContext = new TestContext(level.getServer(), level, origin, level.registryAccess());

        Consumer<TestResult> callback = result -> {
            boolean structured = ctx.getSource().getEntity() == null;
            if (structured) {
                ctx.getSource().sendSuccess(() -> Component.literal(ReportFormatter.toJson(result)), false);
            } else {
                for (String line : ReportFormatter.toDiffSummary(result)) {
                    ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                }
            }
        };

        String specSource = spec.recipeType() + ".json";
        RecipeTestRunner runner = new RecipeTestRunner(spec, holder, testContext, adapter, specSource, callback);
        RunSessionScheduler.instance().submit(runner);

        ctx.getSource().sendSuccess(() -> Component.literal("Scheduled recipe-test diff for " + recipeId), false);
        return 1;
    }
}
