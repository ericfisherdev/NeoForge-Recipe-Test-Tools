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
package dev.recipetest.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.recipetest.api.BulkProgress;
import dev.recipetest.api.BulkResult;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestResult;
import dev.recipetest.spec.BulkResultCodec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Two output flavours for {@link BulkProgress} and {@link BulkResult}:
 *
 * <ul>
 *   <li>{@link #progressJson(BulkProgress)} / {@link #resultJson(BulkResult)} — single-line
 *       NDJSON-shaped output for rcon, console, and command-block sources.
 *   <li>{@link #progressPretty(BulkProgress)} / {@link #resultPretty(BulkResult)} — chat
 *       summaries for in-game players.
 * </ul>
 *
 * <p>Pure functions; the bulk subcommand picks a flavour by querying
 * {@link net.minecraft.commands.CommandSourceStack#getEntity()} (player iff non-null).
 */
public final class ProgressReporter {

    private ProgressReporter() {}

    public static String progressJson(BulkProgress progress) {
        JsonElement encoded = BulkResultCodec.PROGRESS_CODEC
                .encodeStart(JsonOps.INSTANCE, progress)
                .result()
                .orElseThrow(() -> new IllegalStateException("BulkProgress failed to encode"));
        return new GsonBuilder().disableHtmlEscaping().create().toJson(encoded);
    }

    public static String resultJson(BulkResult result) {
        JsonElement encoded = BulkResultCodec.RESULT_CODEC
                .encodeStart(JsonOps.INSTANCE, result)
                .result()
                .orElseThrow(() -> new IllegalStateException("BulkResult failed to encode"));
        return new GsonBuilder().disableHtmlEscaping().create().toJson(encoded);
    }

    public static String progressPretty(BulkProgress progress) {
        StringBuilder sb = new StringBuilder();
        sb.append("[")
                .append(progress.runId())
                .append("] ")
                .append(progress.completed())
                .append("/")
                .append(progress.total())
                .append(" (")
                .append(progress.recipeTypeLabel())
                .append(") ");
        appendCounts(sb, progress.countsByStatus());
        return sb.toString();
    }

    public static List<String> resultPretty(BulkResult result) {
        List<String> lines = new ArrayList<>();
        String header = result.cancelled() ? "BULK CANCELLED" : "BULK DONE";
        lines.add(header + " (" + result.runId() + ", " + result.recipeTypeLabel() + ")");
        lines.add("  total: " + result.total());
        StringBuilder counts = new StringBuilder("  ");
        appendCounts(counts, result.countsByStatus());
        lines.add(counts.toString());
        lines.add("  wallClock: " + result.wallClockMs() + " ms");
        lines.add("  engineTicks: " + result.totalEngineTicks());
        lines.add("  peakMspt: " + result.peakMsptBudgetUsedMs() + " ms");
        appendNonPassRecipes(lines, result.results());
        return List.copyOf(lines);
    }

    /**
     * Non-PASS recipe ids per status, grouped, capped at {@link #FAILURE_LIST_CAP_PER_STATUS}
     * entries per status with an overflow indicator.
     */
    static final int FAILURE_LIST_CAP_PER_STATUS = 25;

    private static void appendNonPassRecipes(List<String> lines, List<TestResult> results) {
        Map<RunStatus, List<TestResult>> byStatus = new EnumMap<>(RunStatus.class);
        for (TestResult r : results) {
            if (r.status() == RunStatus.PASS) {
                continue;
            }
            byStatus.computeIfAbsent(r.status(), s -> new ArrayList<>()).add(r);
        }
        if (byStatus.isEmpty()) {
            return;
        }
        // Iterate RunStatus.values() so the section order is stable across runs (FAIL, TIMEOUT,
        // ERROR, CANCELLED) instead of being dictated by the order recipes finished in.
        for (RunStatus status : RunStatus.values()) {
            List<TestResult> entries = byStatus.get(status);
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            String label = status.name().toLowerCase(java.util.Locale.ROOT);
            lines.add("  " + label + " (" + entries.size() + "):");
            int shown = Math.min(entries.size(), FAILURE_LIST_CAP_PER_STATUS);
            for (int i = 0; i < shown; i++) {
                lines.add("    " + entries.get(i).recipeId());
            }
            int overflow = entries.size() - shown;
            if (overflow > 0) {
                lines.add("    … and " + overflow + " more (use /recipe_test diff <runId> for the full list)");
            }
        }
    }

    private static void appendCounts(StringBuilder sb, Map<RunStatus, Integer> counts) {
        boolean first = true;
        for (RunStatus status : RunStatus.values()) {
            int count = counts.getOrDefault(status, 0);
            if (count == 0) {
                continue;
            }
            if (!first) {
                sb.append(' ');
            }
            sb.append(status.name().toLowerCase(java.util.Locale.ROOT))
                    .append('=')
                    .append(count);
            first = false;
        }
        if (first) {
            // No non-zero entries — surface a hint so the line isn't blank.
            sb.append("(no completions yet)");
        }
    }
}
