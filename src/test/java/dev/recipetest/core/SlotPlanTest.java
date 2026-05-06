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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.recipetest.api.Layout;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlotPlanTest {

    @Test
    @DisplayName("shapeless packs inputs left-to-right into slots")
    void shapelessPacksLeftToRight() {
        SlotPlan.Plan plan = SlotPlan.dispatch(Layout.SHAPELESS, 3, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), List.of());
        assertEquals(
                List.of(new SlotPlan.Assignment(0, 0), new SlotPlan.Assignment(1, 1), new SlotPlan.Assignment(2, 2)),
                plan.assignments());
    }

    @Test
    @DisplayName("ordered keeps input index = slot index")
    void orderedPreservesIndex() {
        SlotPlan.Plan plan = SlotPlan.dispatch(Layout.ORDERED, 2, List.of(5, 7), List.of());
        assertEquals(List.of(new SlotPlan.Assignment(0, 5), new SlotPlan.Assignment(1, 7)), plan.assignments());
    }

    @Test
    @DisplayName("shapeless / ordered with more inputs than slots throws")
    void packOrderedTooManyInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SlotPlan.dispatch(Layout.SHAPELESS, 4, List.of(0, 1, 2), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> SlotPlan.dispatch(Layout.ORDERED, 4, List.of(0, 1, 2), List.of()));
    }

    @Test
    @DisplayName("shaped3x3 places inputs at slot index y*3 + x")
    void shaped3x3Coordinates() {
        // Two-by-two pattern at top-left: positions [(0,0), (1,0), (0,1), (1,1)]
        // Expected slots: 0, 1, 3, 4 (in row-major order: y*3+x)
        SlotPlan.Plan plan = SlotPlan.dispatch(
                Layout.SHAPED3X3,
                4,
                List.of(0, 1, 2, 3, 4, 5, 6, 7, 8),
                List.of(new int[] {0, 0}, new int[] {1, 0}, new int[] {0, 1}, new int[] {1, 1}));
        assertEquals(
                List.of(
                        new SlotPlan.Assignment(0, 0),
                        new SlotPlan.Assignment(1, 1),
                        new SlotPlan.Assignment(2, 3),
                        new SlotPlan.Assignment(3, 4)),
                plan.assignments());
    }

    @Test
    @DisplayName("shaped3x3 honours non-contiguous slot list")
    void shaped3x3RespectsCustomSlotList() {
        // Pattern position (2, 2) → row-major index 8 → slots[8] = 99
        SlotPlan.Plan plan = SlotPlan.dispatch(
                Layout.SHAPED3X3, 1, List.of(10, 11, 12, 13, 14, 15, 16, 17, 99), List.of(new int[] {2, 2}));
        assertEquals(List.of(new SlotPlan.Assignment(0, 99)), plan.assignments());
    }

    @Test
    @DisplayName("shaped3x3 rejects slot list != 9 elements")
    void shaped3x3WrongSlotCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SlotPlan.dispatch(Layout.SHAPED3X3, 1, List.of(0), List.of(new int[] {0, 0})));
    }

    @Test
    @DisplayName("shaped3x3 rejects mismatched positions count")
    void shaped3x3MismatchedPositions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SlotPlan.dispatch(
                        Layout.SHAPED3X3, 2, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), List.of(new int[] {0, 0})));
    }

    @Test
    @DisplayName("shaped3x3 rejects out-of-range coordinates")
    void shaped3x3CoordOutOfRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SlotPlan.dispatch(
                        Layout.SHAPED3X3, 1, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), List.of(new int[] {3, 0})));
        assertThrows(
                IllegalArgumentException.class,
                () -> SlotPlan.dispatch(
                        Layout.SHAPED3X3, 1, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), List.of(new int[] {0, -1})));
    }

    @Test
    @DisplayName("shaped3x3 rejects malformed position pair")
    void shaped3x3MalformedPosition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SlotPlan.dispatch(
                        Layout.SHAPED3X3, 1, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), List.of(new int[] {0, 0, 0})));
    }

    @Test
    @DisplayName("zero inputs always yields empty plan")
    void zeroInputs() {
        for (Layout layout : Layout.values()) {
            List<Integer> slots = layout == Layout.SHAPED3X3 ? List.of(0, 1, 2, 3, 4, 5, 6, 7, 8) : List.of(0);
            SlotPlan.Plan plan = SlotPlan.dispatch(layout, 0, slots, List.of());
            assertEquals(List.of(), plan.assignments(), layout + " should produce empty plan for 0 inputs");
        }
    }
}
