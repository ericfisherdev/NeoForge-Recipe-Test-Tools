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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BulkRecordsTest {

    private static final ResourceLocation RECIPE = ResourceLocation.parse("forestry:carpenter/circuit_board_basic");

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
        assertThrows(
                IllegalArgumentException.class,
                () -> new BulkProgress("run", "all", 5, 1, Map.of(), Optional.empty(), -1));
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
        BulkResult r = new BulkResult(
                "run",
                "all",
                Map.of(RunStatus.PASS, 7, RunStatus.FAIL, 2, RunStatus.TIMEOUT, 1),
                123L,
                40,
                12L,
                false,
                List.of());
        assertEquals(10, r.total());
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
        BulkResult r = new BulkResult("run", "all", Map.of(RunStatus.CANCELLED, 1), 5L, 2, 1L, true, List.of());
        assertTrue(r.cancelled());
        assertEquals(1, r.total());
    }
}
