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

import java.util.List;
import java.util.Objects;

/**
 * Outcome of a {@link CapabilityDriver} injection step. Captures both successes and partial
 * failures so the runner can decide whether to surface a {@code FAIL}/{@code ERROR} or proceed
 * with warnings.
 *
 * @param itemSlotsRejected slot indices whose {@code insertItem} returned a non-empty leftover
 * @param fluidLeftover total millibuckets the fluid handler refused (zero when no fluids declared)
 * @param energyAccepted FE the energy storage actually accepted from the {@code preFill} request
 * @param warnings non-fatal messages, suitable for {@code Diagnostics.warnings}
 */
public record InjectionResult(
        List<Integer> itemSlotsRejected, int fluidLeftover, long energyAccepted, List<String> warnings) {

    public InjectionResult {
        Objects.requireNonNull(itemSlotsRejected, "itemSlotsRejected must not be null");
        Objects.requireNonNull(warnings, "warnings must not be null");
        if (fluidLeftover < 0) {
            throw new IllegalArgumentException("fluidLeftover must be >= 0, got " + fluidLeftover);
        }
        if (energyAccepted < 0) {
            throw new IllegalArgumentException("energyAccepted must be >= 0, got " + energyAccepted);
        }
        itemSlotsRejected = List.copyOf(itemSlotsRejected);
        warnings = List.copyOf(warnings);
    }

    /** True when the kit fully delivered the inputs the recipe asked for. */
    public boolean fullyAccepted() {
        return itemSlotsRejected.isEmpty() && fluidLeftover == 0;
    }

    private static final InjectionResult EMPTY = new InjectionResult(List.of(), 0, 0L, List.of());

    /** Singleton representing a no-op injection (no inputs declared). */
    public static InjectionResult empty() {
        return EMPTY;
    }
}
