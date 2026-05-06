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
import dev.recipetest.core.TickScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * {@code /recipe_test cancel [runId]} — halts the active bulk run. With no arg, cancels whichever
 * run is currently active (Phase 3 enforces a single in-flight run). Emits a success message
 * either way; the actual {@code BulkResult} with {@code cancelled: true} fires from the
 * scheduler's tick handler.
 */
final class CancelSubcommand {

    private CancelSubcommand() {}

    static int cancelCurrent(CommandContext<CommandSourceStack> ctx) {
        return cancel(ctx, null);
    }

    static int cancelById(CommandContext<CommandSourceStack> ctx) {
        String runId = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "runId");
        return cancel(ctx, runId);
    }

    private static int cancel(
            CommandContext<CommandSourceStack> ctx, java.lang.@org.jetbrains.annotations.Nullable String runId) {
        boolean cancelled = TickScheduler.instance().cancel(runId);
        if (!cancelled) {
            ctx.getSource()
                    .sendFailure(Component.literal(
                            runId == null
                                    ? "no active bulk run to cancel"
                                    : "no active bulk run with id '" + runId + "'"));
            return 0;
        }
        ctx.getSource()
                .sendSuccess(() -> Component.literal("Cancellation requested; final result will follow."), false);
        return 1;
    }
}
