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

import dev.recipetest.api.FluidSnapshot;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.ItemSnapshot;
import java.util.List;
import java.util.Objects;

/**
 * Result of reading a machine's output side via {@link CapabilityDriver}. Wraps the user-visible
 * {@link IoSnapshot} along with an energy reading the runner can subtract from the pre-fill to
 * compute {@code energyConsumed}.
 *
 * @param items items observed in the declared output slots, in slot-order
 * @param fluids fluids observed in the declared output tanks, in tank-order
 * @param energyStored current FE in the energy storage (or {@code 0} when no energy spec)
 */
public record ReadResult(List<ItemSnapshot> items, List<FluidSnapshot> fluids, long energyStored) {

    public ReadResult {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(fluids, "fluids must not be null");
        if (energyStored < 0) {
            throw new IllegalArgumentException("energyStored must be >= 0, got " + energyStored);
        }
        items = List.copyOf(items);
        fluids = List.copyOf(fluids);
    }

    /** Repackage as the public {@link IoSnapshot} used in {@code TestResult}. */
    public IoSnapshot toIoSnapshot() {
        return new IoSnapshot(items, fluids);
    }

    private static final ReadResult EMPTY = new ReadResult(List.of(), List.of(), 0L);

    /** Singleton empty reading (no outputs declared, no energy tracked). */
    public static ReadResult empty() {
        return EMPTY;
    }
}
