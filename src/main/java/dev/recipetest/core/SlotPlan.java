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

import dev.recipetest.api.Layout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure ingredient-index-to-handler-slot dispatch math, factored out of {@link CapabilityDriver}
 * so the layout algorithms can be unit-tested without needing a Minecraft world or bootstrapped
 * registries.
 *
 * <p>One {@link Plan} is produced per item binding for a given recipe. The runner consumes the
 * plan to drive {@code IItemHandler.insertItem} calls in order.
 */
public final class SlotPlan {

    private SlotPlan() {}

    /**
     * One ingredient → handler-slot mapping.
     *
     * @param inputIndex zero-based index into the recipe's ingredient list
     * @param slot the {@code IItemHandler} slot index that ingredient should be inserted into
     */
    public record Assignment(int inputIndex, int slot) {}

    /**
     * Ordered list of assignments. Order matches the runner's intended insertion order, so
     * {@link Layout#SHAPED3X3} produces assignments in pattern order even when the underlying
     * slot indices are non-contiguous.
     */
    public record Plan(List<Assignment> assignments) {
        public Plan {
            Objects.requireNonNull(assignments, "Plan.assignments must not be null");
            assignments = List.copyOf(assignments);
        }
    }

    /**
     * Compute a slot plan for the given layout.
     *
     * <p>Positions are required for {@link Layout#SHAPED3X3}; ignored for the other layouts.
     *
     * @param layout the layout strategy to apply
     * @param inputCount number of ingredients in the recipe (must be {@code >= 0})
     * @param slots {@code IItemHandler} slot indices declared on the binding
     * @param positions for {@code SHAPED3X3} only — one {@code [x, y]} pair per ingredient,
     *     same order as {@code inputCount}; pass an empty list (not null) for other layouts
     * @return computed plan
     * @throws IllegalArgumentException for layout-specific shape violations (see notes per case)
     */
    public static Plan dispatch(Layout layout, int inputCount, List<Integer> slots, List<int[]> positions) {
        Objects.requireNonNull(layout, "layout must not be null");
        Objects.requireNonNull(slots, "slots must not be null");
        Objects.requireNonNull(positions, "positions must not be null (use List.of() for non-shaped layouts)");
        if (inputCount < 0) {
            throw new IllegalArgumentException("inputCount must be >= 0, got " + inputCount);
        }
        return switch (layout) {
            case SHAPELESS -> packOrdered(inputCount, slots, "shapeless");
            case ORDERED -> packOrdered(inputCount, slots, "ordered");
            case SHAPED3X3 -> shaped3x3(inputCount, slots, positions);
        };
    }

    private static Plan packOrdered(int inputCount, List<Integer> slots, String layoutName) {
        if (inputCount > slots.size()) {
            throw new IllegalArgumentException(
                    layoutName + " has " + inputCount + " ingredients but only " + slots.size() + " slot(s) declared");
        }
        List<Assignment> assignments = new ArrayList<>(inputCount);
        for (int i = 0; i < inputCount; i++) {
            assignments.add(new Assignment(i, slots.get(i)));
        }
        return new Plan(assignments);
    }

    private static Plan shaped3x3(int inputCount, List<Integer> slots, List<int[]> positions) {
        if (slots.size() != 9) {
            throw new IllegalArgumentException("shaped3x3 requires exactly 9 slot indices, got " + slots.size());
        }
        if (positions.size() != inputCount) {
            throw new IllegalArgumentException(
                    "shaped3x3 expected " + inputCount + " position pairs, got " + positions.size());
        }
        List<Assignment> assignments = new ArrayList<>(inputCount);
        for (int i = 0; i < inputCount; i++) {
            int[] pos = positions.get(i);
            if (pos == null || pos.length != 2) {
                throw new IllegalArgumentException("shaped3x3 position " + i + " must be a [x, y] pair, got "
                        + (pos == null ? "null" : "length " + pos.length));
            }
            int x = pos[0];
            int y = pos[1];
            if (x < 0 || x > 2 || y < 0 || y > 2) {
                throw new IllegalArgumentException(
                        "shaped3x3 position " + i + " out of range [0..2]: (" + x + ", " + y + ")");
            }
            assignments.add(new Assignment(i, slots.get(y * 3 + x)));
        }
        return new Plan(assignments);
    }
}
