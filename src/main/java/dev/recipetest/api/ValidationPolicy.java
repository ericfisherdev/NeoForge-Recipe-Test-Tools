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

/** How the runner compares produced output against expected recipe results. */
public record ValidationPolicy(
        Mode mode,
        ItemTolerance itemTolerance,
        FluidTolerance fluidTolerance,
        NbtCompare nbtCompare,
        int samples,
        double distributionTolerance) {

    public ValidationPolicy {
        if (samples < 1) {
            throw new IllegalArgumentException("samples must be >= 1, got " + samples);
        }
        if (distributionTolerance < 0.0 || distributionTolerance > 1.0) {
            throw new IllegalArgumentException("distributionTolerance must be in [0,1], got " + distributionTolerance);
        }
    }

    /** Default exact-match policy with single sample, structural NBT comparison. */
    public static final ValidationPolicy DEFAULT =
            new ValidationPolicy(Mode.EXACT, ItemTolerance.EXACT, FluidTolerance.EXACT, NbtCompare.STRUCTURAL, 1, 0.05);

    public enum Mode {
        EXACT,
        DISTRIBUTION,
        SUBSET
    }

    public enum NbtCompare {
        STRUCTURAL,
        IGNORE,
        EXACT
    }

    /** Per-stack item-count tolerance applied when comparing actual vs. expected outputs. */
    public record ItemTolerance(int count) {
        public ItemTolerance {
            if (count < 0) {
                throw new IllegalArgumentException("itemTolerance.count must be >= 0, got " + count);
            }
        }

        public static final ItemTolerance EXACT = new ItemTolerance(0);
    }

    /** Per-tank fluid-amount tolerance applied when comparing actual vs. expected outputs. */
    public record FluidTolerance(int amount) {
        public FluidTolerance {
            if (amount < 0) {
                throw new IllegalArgumentException("fluidTolerance.amount must be >= 0, got " + amount);
            }
        }

        public static final FluidTolerance EXACT = new FluidTolerance(0);
    }
}
