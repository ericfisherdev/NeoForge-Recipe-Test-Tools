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
import dev.recipetest.api.DiffEntry;
import dev.recipetest.api.DiffPayload;
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

    /** Build results with distinct recipe IDs per entry so failure-listing assertions can
     *  identify individual recipes by name. FAIL status requires a non-empty diff payload per
     *  TestResult's invariant; non-FAIL statuses require an empty Optional. */
    private static List<TestResult> distinctResults(RunStatus status, int count, String prefix) {
        Optional<DiffPayload> diff = status == RunStatus.FAIL
                ? Optional.of(new DiffPayload(List.of(new DiffEntry("/x", "e", "a", "stub"))))
                : Optional.empty();
        List<TestResult> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new TestResult(
                    ResourceLocation.parse(prefix + i),
                    RECIPE_TYPE,
                    "test.json",
                    status,
                    1,
                    IoSnapshot.empty(),
                    IoSnapshot.empty(),
                    diff,
                    Diagnostics.empty()));
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

    @Test
    @DisplayName("resultPretty enumerates non-PASS recipe IDs in a labeled section")
    void resultPrettyListsFailures() {
        List<TestResult> results = new ArrayList<>();
        results.addAll(distinctResults(RunStatus.PASS, 2, "test:pass/"));
        results.addAll(distinctResults(RunStatus.FAIL, 2, "test:fail/"));
        BulkResult result = new BulkResult(
                "bulk-1", "test:type", Map.of(RunStatus.PASS, 2, RunStatus.FAIL, 2), 100L, 40, 12L, false, results);
        List<String> lines = ProgressReporter.resultPretty(result);
        assertTrue(
                lines.stream().anyMatch(l -> l.equals("  fail (2):")), () -> "missing fail section header: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.equals("    test:fail/0")), () -> "missing fail/0 entry: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.equals("    test:fail/1")), () -> "missing fail/1 entry: " + lines);
        // PASS entries are summarized in counts only — no per-recipe spam.
        assertFalse(
                lines.stream().anyMatch(l -> l.contains("test:pass/")),
                () -> "PASS recipes should not be listed individually: " + lines);
    }

    @Test
    @DisplayName("resultPretty omits the failures section when every recipe PASSed")
    void resultPrettyNoFailuresWhenAllPass() {
        Map<RunStatus, Integer> counts = Map.of(RunStatus.PASS, 5);
        BulkResult result = new BulkResult(
                "bulk-1", "all", counts, 50L, 20, 10L, false, distinctResults(RunStatus.PASS, 5, "test:p/"));
        List<String> lines = ProgressReporter.resultPretty(result);
        assertFalse(
                lines.stream().anyMatch(l -> l.matches("\\s+(fail|timeout|error|cancelled) \\(.*")),
                () -> "no non-PASS section should appear when all PASS: " + lines);
    }

    @Test
    @DisplayName("resultPretty caps per-status enumeration with an overflow indicator")
    void resultPrettyOverflowCap() {
        int over = ProgressReporter.FAILURE_LIST_CAP_PER_STATUS + 7;
        Map<RunStatus, Integer> counts = Map.of(RunStatus.FAIL, over);
        BulkResult result =
                new BulkResult("bulk-1", "x", counts, 1L, 1, 0L, false, distinctResults(RunStatus.FAIL, over, "x:r/"));
        List<String> lines = ProgressReporter.resultPretty(result);
        long enumerated = lines.stream().filter(l -> l.startsWith("    x:r/")).count();
        assertTrue(
                enumerated == ProgressReporter.FAILURE_LIST_CAP_PER_STATUS,
                () -> "expected " + ProgressReporter.FAILURE_LIST_CAP_PER_STATUS + " entries, got " + enumerated);
        assertTrue(
                lines.stream().anyMatch(l -> l.contains("… and 7 more")), () -> "overflow indicator missing: " + lines);
    }

    @Test
    @DisplayName("resultPretty groups multiple non-PASS statuses in stable enum order")
    void resultPrettyMultiStatusOrdering() {
        List<TestResult> results = new ArrayList<>();
        // Insert TIMEOUT first to verify the output STILL sorts by RunStatus.values() order
        // (FAIL before TIMEOUT before ERROR), regardless of input ordering.
        results.addAll(distinctResults(RunStatus.TIMEOUT, 1, "t:timeout/"));
        results.addAll(distinctResults(RunStatus.FAIL, 1, "t:fail/"));
        results.addAll(distinctResults(RunStatus.ERROR, 1, "t:error/"));
        BulkResult result = new BulkResult(
                "bulk-1",
                "t",
                Map.of(RunStatus.FAIL, 1, RunStatus.TIMEOUT, 1, RunStatus.ERROR, 1),
                1L,
                1,
                0L,
                false,
                results);
        List<String> lines = ProgressReporter.resultPretty(result);
        int failHeader = -1, timeoutHeader = -1, errorHeader = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals("  fail (1):")) {
                failHeader = i;
            } else if (lines.get(i).equals("  timeout (1):")) {
                timeoutHeader = i;
            } else if (lines.get(i).equals("  error (1):")) {
                errorHeader = i;
            }
        }
        assertTrue(
                failHeader >= 0 && timeoutHeader >= 0 && errorHeader >= 0, () -> "all 3 headers must appear: " + lines);
        // FAIL is declared before TIMEOUT before ERROR in RunStatus.values() — outputs follow.
        assertTrue(failHeader < timeoutHeader, () -> "fail should precede timeout: " + lines);
        assertTrue(timeoutHeader < errorHeader, () -> "timeout should precede error: " + lines);
    }
}
