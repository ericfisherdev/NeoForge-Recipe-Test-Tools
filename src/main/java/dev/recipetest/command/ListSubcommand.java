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
import dev.recipetest.core.HarnessRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code /recipe_test list} and {@code /recipe_test list <recipeType>}.
 *
 * <p>For player sources we emit pretty chat lines grouped by mod id; for non-player sources
 * (rcon, console, command blocks) we emit a single newline-separated structured block of
 * {@code namespace:path} lines so wrappers can parse the output.
 */
final class ListSubcommand {

    private static final SimpleCommandExceptionType BAD_RESOURCE_LOCATION = new SimpleCommandExceptionType(
            Component.literal("argument is not a valid recipeType (expected namespace:path)"));

    private static final SimpleCommandExceptionType NOT_REGISTERED =
            new SimpleCommandExceptionType(Component.literal("no spec registered for that recipeType"));

    private ListSubcommand() {}

    static int listAll(CommandContext<CommandSourceStack> ctx) {
        HarnessRegistry registry = HarnessRegistry.instance();
        if (registry.size() == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("No machine specs registered."), false);
            return 0;
        }

        boolean structured = ctx.getSource().getEntity() == null;
        if (structured) {
            for (MachineSpec spec : registry.all()) {
                ctx.getSource()
                        .sendSuccess(() -> Component.literal(spec.recipeType().toString()), false);
            }
            return registry.size();
        }

        ctx.getSource().sendSuccess(() -> headerComponent("Registered specs (" + registry.size() + ")"), false);
        for (Map.Entry<String, List<MachineSpec>> group : registry.byModid().entrySet()) {
            ctx.getSource()
                    .sendSuccess(() -> Component.literal("  " + group.getKey()).withStyle(ChatFormatting.GRAY), false);
            for (MachineSpec spec : group.getValue()) {
                ctx.getSource().sendSuccess(() -> Component.literal("    " + spec.recipeType()), false);
            }
        }
        return registry.size();
    }

    static int listOne(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "recipeType");
        ResourceLocation rl = ResourceLocation.tryParse(raw);
        if (rl == null) {
            throw BAD_RESOURCE_LOCATION.create();
        }
        Optional<MachineSpec> spec = HarnessRegistry.instance().byRecipeType(rl);
        if (spec.isEmpty()) {
            throw NOT_REGISTERED.create();
        }
        MachineSpec s = spec.get();
        ctx.getSource().sendSuccess(() -> headerComponent("Spec for " + s.recipeType()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  block: " + s.block()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  tickBudget: " + s.tickBudget()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  inputs: " + ListFormatter.summarizeInputs(s)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  outputs: " + ListFormatter.summarizeOutputs(s)), false);
        s.energy().ifPresent(e -> ctx.getSource()
                .sendSuccess(
                        () -> Component.literal("  energy: " + e.capability() + " preFill=" + e.preFill()), false));
        return 1;
    }

    private static Component headerComponent(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GOLD);
    }
}
