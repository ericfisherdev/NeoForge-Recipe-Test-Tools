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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.recipetest.api.DiffEntry;
import dev.recipetest.api.FluidSnapshot;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.ItemSnapshot;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestResult;
import dev.recipetest.spec.TestResultCodec;
import java.util.List;

/**
 * Two output flavours for a {@link TestResult}:
 *
 * <ul>
 *   <li>{@link #toJson(TestResult)} — single-line minified JSON for rcon, console, command-block
 *       sources where machine parsing matters more than human readability.
 *   <li>{@link #toPretty(TestResult)} — multi-line summary lines for in-game player chat.
 * </ul>
 *
 * <p>Pure functions; no side-channel state. The {@code RunSubcommand} / {@code DiffSubcommand}
 * pick a flavour by querying {@link net.minecraft.commands.CommandSourceStack#getEntity()}
 * (player iff non-null).
 */
public final class ReportFormatter {

    private ReportFormatter() {}

    /** Single-line minified JSON suitable for rcon. */
    public static String toJson(TestResult result) {
        JsonElement encoded = TestResultCodec.CODEC
                .encodeStart(JsonOps.INSTANCE, result)
                .result()
                .orElseThrow(() -> new IllegalStateException("TestResult failed to encode"));
        return new GsonBuilder().disableHtmlEscaping().create().toJson(encoded);
    }

    /** Pretty multi-line text for in-game chat — one line per attribute, indented. */
    public static List<String> toPretty(TestResult result) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add(statusHeader(result));
        lines.add("  recipe: " + result.recipeId());
        lines.add("  type: " + result.recipeType());
        lines.add("  spec: " + result.specSource());
        lines.add("  ticks: " + result.ticksElapsed());
        lines.addAll(formatSnapshot("expected", result.expected()));
        lines.addAll(formatSnapshot("actual", result.actual()));
        result.diff().ifPresent(diff -> {
            lines.add("  diff:");
            for (DiffEntry entry : diff.mismatches()) {
                lines.add("    " + entry.path() + ": " + entry.expected() + " → " + entry.actual() + "  ("
                        + entry.reason() + ")");
            }
        });
        if (!result.diagnostics().warnings().isEmpty()) {
            lines.add("  warnings:");
            for (String w : result.diagnostics().warnings()) {
                lines.add("    " + w);
            }
        }
        if (result.diagnostics().energyConsumed() > 0) {
            lines.add("  energyConsumed: " + result.diagnostics().energyConsumed() + " FE");
        }
        return List.copyOf(lines);
    }

    /** Human-readable side-by-side diff lines for {@code /recipe_test diff}. */
    public static List<String> toDiffSummary(TestResult result) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add(statusHeader(result));
        lines.add("  recipe: " + result.recipeId());
        lines.add(String.format("  %-32s | %-32s | reason", "expected", "actual"));
        lines.add("  " + "-".repeat(32) + "-+-" + "-".repeat(32) + "-+-" + "-".repeat(20));
        if (result.diff().isPresent()) {
            for (DiffEntry entry : result.diff().get().mismatches()) {
                lines.add(String.format("  %-32s | %-32s | %s", entry.expected(), entry.actual(), entry.reason()));
            }
        } else {
            lines.add("  (no mismatches)");
        }
        return List.copyOf(lines);
    }

    private static String statusHeader(TestResult result) {
        return result.status() == RunStatus.PASS ? "PASS" : result.status() + " (" + result.recipeId() + ")";
    }

    private static List<String> formatSnapshot(String label, IoSnapshot snap) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("  " + label + ":");
        if (snap.items().isEmpty() && snap.fluids().isEmpty()) {
            lines.add("    (none)");
            return lines;
        }
        for (ItemSnapshot item : snap.items()) {
            lines.add("    item " + item.id() + " x" + item.count()
                    + item.nbt().map(n -> " " + n).orElse(""));
        }
        for (FluidSnapshot fluid : snap.fluids()) {
            lines.add("    fluid " + fluid.id() + " " + fluid.amount() + "mB"
                    + fluid.nbt().map(n -> " " + n).orElse(""));
        }
        return lines;
    }
}
