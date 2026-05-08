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
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestContext;
import dev.recipetest.api.TestResult;
import dev.recipetest.core.KitRegistry;
import dev.recipetest.core.RecipeAdapter;
import dev.recipetest.core.RecipeAdapters;
import dev.recipetest.core.RecipeTestRunner;
import dev.recipetest.core.RunSessionScheduler;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * {@code /recipe_test run <recipeType> <recipeId>} — kicks off an async runner against the chosen
 * recipe and reports the {@link TestResult} when it completes.
 */
final class RunSubcommand {

    private static final SimpleCommandExceptionType BAD_RESOURCE_LOCATION =
            new SimpleCommandExceptionType(Component.literal("argument is not a valid namespace:path"));
    private static final SimpleCommandExceptionType SPEC_NOT_REGISTERED =
            new SimpleCommandExceptionType(Component.literal("no spec registered for that recipeType"));
    private static final SimpleCommandExceptionType RECIPE_NOT_FOUND =
            new SimpleCommandExceptionType(Component.literal("recipe not found"));
    static final SimpleCommandExceptionType RECIPE_TYPE_MISMATCH =
            new SimpleCommandExceptionType(Component.literal("recipe's type doesn't match the spec's recipeType"));
    private static final SimpleCommandExceptionType NO_ADAPTER =
            new SimpleCommandExceptionType(Component.literal("no RecipeAdapter applies to this recipe"));

    private RunSubcommand() {}

    static int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation recipeType =
                parseId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "recipeType"));
        ResourceLocation recipeId =
                parseId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "recipeId"));

        MachineSpec spec = KitRegistry.instance().byRecipeType(recipeType).orElseThrow(SPEC_NOT_REGISTERED::create);

        ServerLevel level = ctx.getSource().getLevel();
        RecipeManager recipeManager = level.getServer().getRecipeManager();
        RecipeHolder<?> holder = recipeManager.byKey(recipeId).orElseThrow(RECIPE_NOT_FOUND::create);

        verifyRecipeType(level, holder, recipeType);

        RecipeAdapter adapter = RecipeAdapters.findFor(holder.value()).orElseThrow(NO_ADAPTER::create);

        BlockPos origin = ctx.getSource().getPosition() != null
                ? BlockPos.containing(ctx.getSource().getPosition()).above(2)
                : new BlockPos(0, 64, 0);
        TestContext testContext = new TestContext(level.getServer(), level, origin, level.registryAccess());
        Consumer<TestResult> callback = result -> reportResult(ctx.getSource(), result);

        String specSource = spec.recipeType() + ".json";
        RecipeTestRunner runner = new RecipeTestRunner(spec, holder, testContext, adapter, specSource, callback);
        RunSessionScheduler.instance().submit(runner);

        ctx.getSource().sendSuccess(() -> Component.literal("Scheduled recipe-test run for " + recipeId), false);
        return 1;
    }

    private static void reportResult(CommandSourceStack source, TestResult result) {
        boolean structured = source.getEntity() == null;
        if (structured) {
            source.sendSuccess(() -> Component.literal(ReportFormatter.toJson(result)), false);
        } else {
            for (String line : ReportFormatter.toPretty(result)) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        if (result.status() == RunStatus.ERROR) {
            source.sendFailure(Component.literal("Run errored — see warnings."));
        }
    }

    static ResourceLocation parseId(String raw) throws CommandSyntaxException {
        ResourceLocation rl = ResourceLocation.tryParse(raw);
        if (rl == null) {
            throw BAD_RESOURCE_LOCATION.create();
        }
        return rl;
    }

    /** Helper exposed for {@link DiffSubcommand}'s shared lookup. */
    static Optional<MachineSpec> findSpec(ResourceLocation recipeType) {
        return KitRegistry.instance().byRecipeType(recipeType);
    }

    /**
     * Throws {@link #RECIPE_TYPE_MISMATCH} if the resolved recipe's actual {@code RecipeType}
     * doesn't match the {@code recipeType} the command was invoked with. Shared between {@link
     * RunSubcommand} and {@link DiffSubcommand} so both reject mismatches consistently.
     */
    static void verifyRecipeType(ServerLevel level, RecipeHolder<?> holder, ResourceLocation expectedRecipeType)
            throws CommandSyntaxException {
        ResourceLocation actualType = level.registryAccess()
                .registry(net.minecraft.core.registries.Registries.RECIPE_TYPE)
                .orElseThrow()
                .getKey(holder.value().getType());
        if (actualType == null || !actualType.equals(expectedRecipeType)) {
            throw RECIPE_TYPE_MISMATCH.create();
        }
    }
}
