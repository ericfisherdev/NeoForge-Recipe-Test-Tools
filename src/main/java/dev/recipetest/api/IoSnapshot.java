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
import java.util.Objects;

/**
 * Bundle of items + fluids representing one side (expected or actual) of a {@link TestResult}.
 *
 * @param items item snapshots, ordered by output slot index when read from a machine
 * @param fluids fluid snapshots, ordered by tank index when read from a machine
 */
public record IoSnapshot(List<ItemSnapshot> items, List<FluidSnapshot> fluids) {

    public IoSnapshot {
        Objects.requireNonNull(items, "IoSnapshot.items must not be null");
        Objects.requireNonNull(fluids, "IoSnapshot.fluids must not be null");
        items = List.copyOf(items);
        fluids = List.copyOf(fluids);
    }

    private static final IoSnapshot EMPTY = new IoSnapshot(List.of(), List.of());

    /** Singleton empty snapshot — both lists empty. */
    public static IoSnapshot empty() {
        return EMPTY;
    }
}
