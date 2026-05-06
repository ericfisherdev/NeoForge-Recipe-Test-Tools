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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.Diagnostics;
import dev.recipetest.api.DiffEntry;
import dev.recipetest.api.DiffPayload;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.ItemSnapshot;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestResult;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportFormatterTest {

    private static final ResourceLocation RECIPE_ID = ResourceLocation.parse("forestry:carpenter/circuit_board_basic");
    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.parse("forestry:carpenter");
    private static final ResourceLocation CIRCUIT = ResourceLocation.parse("forestry:circuit_board");

    private static TestResult passResult() {
        return new TestResult(
                RECIPE_ID,
                RECIPE_TYPE,
                "forestry:carpenter.json",
                RunStatus.PASS,
                80,
                new IoSnapshot(List.of(ItemSnapshot.of(CIRCUIT, 1)), List.of()),
                new IoSnapshot(List.of(ItemSnapshot.of(CIRCUIT, 1)), List.of()),
                Optional.empty(),
                Diagnostics.empty());
    }

    private static TestResult failResult() {
        DiffPayload diff =
                new DiffPayload(List.of(new DiffEntry("/items/0", "circuit x1", "(missing)", "missing item")));
        return new TestResult(
                RECIPE_ID,
                RECIPE_TYPE,
                "forestry:carpenter.json",
                RunStatus.FAIL,
                200,
                new IoSnapshot(List.of(ItemSnapshot.of(CIRCUIT, 1)), List.of()),
                IoSnapshot.empty(),
                Optional.of(diff),
                Diagnostics.empty());
    }

    @Test
    @DisplayName("toJson produces a single line that round-trips through the codec")
    void jsonSingleLineRoundTrip() {
        String json = ReportFormatter.toJson(passResult());
        assertFalse(json.contains("\n"), () -> "JSON output must be a single line: " + json);
        assertTrue(json.contains("\"status\":\"PASS\""));
        assertTrue(json.contains(RECIPE_ID.toString()));
    }

    @Test
    @DisplayName("toPretty prefixes PASS with the bare PASS marker")
    void prettyPassHeader() {
        List<String> lines = ReportFormatter.toPretty(passResult());
        assertEquals("PASS", lines.get(0));
    }

    @Test
    @DisplayName("toPretty for FAIL includes diff entries")
    void prettyFailIncludesDiff() {
        List<String> lines = ReportFormatter.toPretty(failResult());
        assertTrue(
                lines.stream().anyMatch(l -> l.contains("/items/0") && l.contains("missing item")),
                () -> "expected diff line in output, got " + lines);
    }

    @Test
    @DisplayName("toPretty omits diagnostics blocks when empty")
    void prettyOmitsEmptyDiagnostics() {
        List<String> lines = ReportFormatter.toPretty(passResult());
        assertFalse(lines.stream().anyMatch(l -> l.contains("warnings")));
        assertFalse(lines.stream().anyMatch(l -> l.contains("energyConsumed")));
    }

    @Test
    @DisplayName("toDiffSummary renders a side-by-side table for failed runs")
    void diffSummaryTable() {
        List<String> lines = ReportFormatter.toDiffSummary(failResult());
        assertTrue(
                lines.stream().anyMatch(l -> l.contains("expected") && l.contains("actual") && l.contains("reason")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("circuit x1") && l.contains("(missing)")));
    }

    @Test
    @DisplayName("toDiffSummary on a passing run prints '(no mismatches)'")
    void diffSummaryNoMismatches() {
        List<String> lines = ReportFormatter.toDiffSummary(passResult());
        assertTrue(lines.stream().anyMatch(l -> l.contains("(no mismatches)")));
    }
}
