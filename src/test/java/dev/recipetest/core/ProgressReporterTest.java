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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.BulkProgress;
import dev.recipetest.api.BulkResult;
import dev.recipetest.api.Diagnostics;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProgressReporterTest {

    private static final ResourceLocation RECIPE = ResourceLocation.parse("forestry:carpenter/circuit_board_basic");
    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.parse("forestry:carpenter");

    /** Build dummy {@link TestResult}s matching the supplied counts so {@link BulkResult}'s
     *  count-vs-results.size invariant is satisfied. */
    private static List<TestResult> dummyResults(Map<RunStatus, Integer> counts) {
        List<TestResult> out = new ArrayList<>();
        for (Map.Entry<RunStatus, Integer> entry : counts.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                out.add(new TestResult(
                        RECIPE,
                        RECIPE_TYPE,
                        "test.json",
                        entry.getKey(),
                        1,
                        IoSnapshot.empty(),
                        IoSnapshot.empty(),
                        Optional.empty(),
                        Diagnostics.empty()));
            }
        }
        return out;
    }

    @Test
    @DisplayName("progressJson is a single line and includes the run id")
    void progressJsonShape() {
        BulkProgress progress = new BulkProgress(
                "bulk-deadbeef",
                "forestry:carpenter",
                10,
                3,
                Map.of(RunStatus.PASS, 2, RunStatus.FAIL, 1),
                Optional.empty(),
                0);
        String json = ProgressReporter.progressJson(progress);
        assertFalse(json.contains("\n"), () -> "expected single-line NDJSON: " + json);
        assertTrue(json.contains("bulk-deadbeef"));
        assertTrue(json.contains("\"completed\":3"));
    }

    @Test
    @DisplayName("progressPretty includes counts in non-zero status order")
    void progressPrettyCounts() {
        BulkProgress progress = new BulkProgress(
                "bulk-1", "all", 5, 3, Map.of(RunStatus.PASS, 2, RunStatus.TIMEOUT, 1), Optional.empty(), 0);
        String line = ProgressReporter.progressPretty(progress);
        assertTrue(line.contains("3/5"));
        assertTrue(line.contains("pass=2"));
        assertTrue(line.contains("timeout=1"));
        assertFalse(line.contains("fail="));
    }

    @Test
    @DisplayName("progressPretty surfaces a hint when no completions yet")
    void progressPrettyNoCompletions() {
        BulkProgress progress = new BulkProgress("bulk-1", "all", 5, 0, Map.of(), Optional.empty(), 0);
        String line = ProgressReporter.progressPretty(progress);
        assertTrue(line.contains("no completions yet"));
    }

    @Test
    @DisplayName("resultPretty header changes on cancellation")
    void resultPrettyCancelled() {
        BulkResult result = new BulkResult(
                "bulk-1",
                "all",
                Map.of(RunStatus.CANCELLED, 1, RunStatus.PASS, 2),
                100L,
                40,
                12L,
                true,
                dummyResults(Map.of(RunStatus.CANCELLED, 1, RunStatus.PASS, 2)));
        List<String> lines = ProgressReporter.resultPretty(result);
        assertTrue(lines.get(0).startsWith("BULK CANCELLED"));
    }

    @Test
    @DisplayName("resultPretty done header on success")
    void resultPrettyDone() {
        Map<RunStatus, Integer> counts = Map.of(RunStatus.PASS, 3);
        BulkResult result = new BulkResult("bulk-1", "all", counts, 50L, 20, 10L, false, dummyResults(counts));
        List<String> lines = ProgressReporter.resultPretty(result);
        assertTrue(lines.get(0).startsWith("BULK DONE"));
        assertTrue(lines.stream().anyMatch(l -> l.contains("wallClock: 50 ms")));
    }

    @Test
    @DisplayName("resultJson is single-line and round-trippable")
    void resultJsonShape() {
        Map<RunStatus, Integer> counts = Map.of(RunStatus.PASS, 1);
        BulkResult result = new BulkResult("bulk-1", "all", counts, 5L, 2, 1L, false, dummyResults(counts));
        String json = ProgressReporter.resultJson(result);
        assertFalse(json.contains("\n"));
        assertTrue(json.contains("\"runId\":\"bulk-1\""));
    }
}
