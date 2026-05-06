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

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.recipetest.RecipeTestMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Root {@code /recipe_test} command — restricted to op (permission level 2). Subcommand bodies
 * live in their own classes; this file just wires the literal tree.
 */
public final class RecipeTestCommand {

    /** Required permission level for every {@code /recipe_test} subcommand in Phase 1. */
    public static final int PERMISSION_LEVEL = 2;

    private RecipeTestCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(RecipeTestMod.MODID)
                .requires(src -> src.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("list")
                        .executes(ListSubcommand::listAll)
                        .then(Commands.argument("recipeType", StringArgumentType.string())
                                .executes(ListSubcommand::listOne)))
                .then(Commands.literal("schema").executes(SchemaSubcommand::dump)));
    }
}
