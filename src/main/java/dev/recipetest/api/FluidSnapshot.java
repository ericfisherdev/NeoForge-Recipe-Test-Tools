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

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable, JSON-friendly view of a fluid stack used in {@link TestResult} reports.
 *
 * @param id fluid registry id (e.g. {@code "minecraft:water"})
 * @param amount millibuckets contained; must be {@code >= 0}
 * @param nbt optional component-patch / NBT representation in stringified form
 */
public record FluidSnapshot(ResourceLocation id, int amount, Optional<String> nbt) {

    public FluidSnapshot {
        Objects.requireNonNull(id, "FluidSnapshot.id must not be null");
        Objects.requireNonNull(nbt, "FluidSnapshot.nbt Optional must not be null");
        if (amount < 0) {
            throw new IllegalArgumentException("FluidSnapshot.amount must be >= 0, got " + amount);
        }
    }

    /** Convenience constructor for a fluid with no NBT. */
    public static FluidSnapshot of(ResourceLocation id, int amount) {
        return new FluidSnapshot(id, amount, Optional.empty());
    }
}
