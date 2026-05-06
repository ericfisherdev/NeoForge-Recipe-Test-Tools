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

import java.util.List;

/**
 * Maps recipe fluid ingredients (or results) onto a machine's fluid-handler tanks.
 *
 * <p>The JSON form accepts either a single {@code "tank"} integer or an explicit {@code "tanks"}
 * array; both forms are normalized into the {@link #tanks} list (always non-empty).
 *
 * @param capability the capability identifier (e.g. {@code "FluidHandler"})
 * @param side which side of the block to query (defaults to {@link Side#INTERNAL})
 * @param tanks tank indices the binding covers (always at least one)
 */
public record FluidBinding(String capability, Side side, List<Integer> tanks) {

    public FluidBinding {
        java.util.Objects.requireNonNull(capability, "FluidBinding.capability must not be null");
        java.util.Objects.requireNonNull(side, "FluidBinding.side must not be null");
        java.util.Objects.requireNonNull(tanks, "FluidBinding.tanks must not be null");
        if (tanks.isEmpty()) {
            throw new IllegalArgumentException("FluidBinding requires at least one tank index");
        }
        for (Integer t : tanks) {
            if (t == null || t < 0) {
                throw new IllegalArgumentException("FluidBinding.tanks indices must be non-negative, got " + t);
            }
        }
        tanks = List.copyOf(tanks);
    }

    /**
     * Convenience accessor for the first tank index — most fluid bindings reference exactly one
     * tank, in which case this matches the {@code "tank"} JSON field.
     */
    public int tank() {
        return tanks.get(0);
    }
}
