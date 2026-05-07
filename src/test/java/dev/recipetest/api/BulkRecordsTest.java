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
package dev.recipetest.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BulkRecordsTest {

    private static final ResourceLocation RECIPE = ResourceLocation.parse("forestry:carpenter/circuit_board_basic");
    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.parse("forestry:carpenter");

    /** Build dummy {@link TestResult}s — one per per-status count entry — used to satisfy
     *  {@code BulkResult}'s count-vs-results.size invariant in tests. FAIL status carries a
     *  minimal diff payload because the {@link TestResult} constructor enforces it. */
    private static List<TestResult> dummyResults(Map<RunStatus, Integer> counts) {
        List<TestResult> out = new ArrayList<>();
        for (Map.Entry<RunStatus, Integer> entry : counts.entrySet()) {
            RunStatus status = entry.getKey();
            Optional<DiffPayload> diff = status == RunStatus.FAIL
                    ? Optional.of(new DiffPayload(List.of(new DiffEntry("/items/0", "expected", "actual", "stub"))))
                    : Optional.empty();
            for (int i = 0; i < entry.getValue(); i++) {
                out.add(new TestResult(
                        RECIPE,
                        RECIPE_TYPE,
                        "test.json",
                        status,
                        1,
                        IoSnapshot.empty(),
                        IoSnapshot.empty(),
                        diff,
                        Diagnostics.empty()));
            }
        }
        return out;
    }

    // ---- BulkProgress ----

    @Test
    @DisplayName("BulkProgress rejects empty runId / recipeType")
    void progressRejectsEmptyStrings() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BulkProgress("", "forestry:carpenter", 1, 0, Map.of(), Optional.empty(), 0));
        assertThrows(
                IllegalArgumentException.class, () -> new BulkProgress("run", "", 1, 0, Map.of(), Optional.empty(), 0));
    }

    @Test
    @DisplayName("BulkProgress rejects negative or out-of-range counts")
    void progressRejectsBadCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BulkProgress("run", "all", -1, 0, Map.of(), Optional.empty(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BulkProgress("run", "all", 5, 6, Map.of(), Optional.empty(), 0));
        // Use a matching status map so the ctor reaches the etaTicks check rather than failing
        // on the new sum-vs-completed invariant first.
        assertThrows(
                IllegalArgumentException.class,
                () -> new BulkProgress("run", "all", 5, 1, Map.of(RunStatus.PASS, 1), Optional.empty(), -1));
    }

    @Test
    @DisplayName("BulkProgress rejects countsByStatus sum that disagrees with completed")
    void progressRejectsCountsSumMismatch() {
        // sum > completed
        assertThrows(
                IllegalArgumentException.class,
                () -> new BulkProgress("run", "all", 5, 1, Map.of(RunStatus.PASS, 2), Optional.empty(), 0));
        // sum < completed
        assertThrows(
                IllegalArgumentException.class,
                () -> new BulkProgress("run", "all", 5, 3, Map.of(RunStatus.PASS, 1), Optional.empty(), 0));
    }

    @Test
    @DisplayName("BulkProgress.remaining computes total - completed")
    void progressRemaining() {
        BulkProgress p = new BulkProgress("run", "all", 10, 3, Map.of(RunStatus.PASS, 3), Optional.of(RECIPE), 14);
        assertEquals(7, p.remaining());
    }

    @Test
    @DisplayName("BulkProgress defensively copies countsByStatus")
    void progressDefensiveCopy() {
        Map<RunStatus, Integer> counts = new HashMap<>();
        counts.put(RunStatus.PASS, 1);
        BulkProgress p = new BulkProgress("run", "all", 5, 1, counts, Optional.empty(), 0);
        counts.put(RunStatus.FAIL, 99);
        assertEquals(1, p.countsByStatus().size());
    }

    // ---- BulkResult ----

    @Test
    @DisplayName("BulkResult.total sums countsByStatus")
    void resultTotalSumsCounts() {
        Map<RunStatus, Integer> counts = Map.of(RunStatus.PASS, 7, RunStatus.FAIL, 2, RunStatus.TIMEOUT, 1);
        BulkResult r = new BulkResult("run", "all", counts, 123L, 40, 12L, false, dummyResults(counts));
        assertEquals(10, r.total());
    }

    @Test
    @DisplayName("BulkResult rejects countsByStatus that disagrees with results.size()")
    void resultRejectsCountsResultsMismatch() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BulkResult("run", "all", Map.of(RunStatus.PASS, 3), 0L, 0, 0L, false, List.of()));
    }

    @Test
    @DisplayName("BulkResult rejects negative wallClock / engineTicks / peak")
    void resultRejectsNegatives() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BulkResult("run", "all", Map.of(), -1L, 0, 0L, false, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BulkResult("run", "all", Map.of(), 0L, -1, 0L, false, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BulkResult("run", "all", Map.of(), 0L, 0, -1L, false, List.of()));
    }

    @Test
    @DisplayName("BulkResult rejects empty runId / recipeType")
    void resultRejectsEmptyStrings() {
        assertThrows(
                IllegalArgumentException.class, () -> new BulkResult("", "all", Map.of(), 0L, 0, 0L, false, List.of()));
        assertThrows(
                IllegalArgumentException.class, () -> new BulkResult("run", "", Map.of(), 0L, 0, 0L, false, List.of()));
    }

    @Test
    @DisplayName("BulkResult cancelled flag round-trips")
    void resultCancelledFlag() {
        Map<RunStatus, Integer> counts = Map.of(RunStatus.CANCELLED, 1);
        BulkResult r = new BulkResult("run", "all", counts, 5L, 2, 1L, true, dummyResults(counts));
        assertTrue(r.cancelled());
        assertEquals(1, r.total());
    }
}
