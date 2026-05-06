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
 * Immutable, JSON-friendly view of a single item stack used in {@link TestResult} reports.
 * Holds only the registry id, count, and an optional pre-rendered NBT/components tag string —
 * deliberately decoupled from {@code net.minecraft.world.item.ItemStack} so equality and codecs
 * work without bootstrapping the Minecraft registries.
 *
 * @param id item registry id (e.g. {@code "minecraft:stick"})
 * @param count stack size; must be {@code >= 0} (zero allowed for "absent" placeholder positions)
 * @param nbt optional component-patch / NBT representation in stringified form
 */
public record ItemSnapshot(ResourceLocation id, int count, Optional<String> nbt) {

    public ItemSnapshot {
        Objects.requireNonNull(id, "ItemSnapshot.id must not be null");
        Objects.requireNonNull(nbt, "ItemSnapshot.nbt Optional must not be null");
        if (count < 0) {
            throw new IllegalArgumentException("ItemSnapshot.count must be >= 0, got " + count);
        }
    }

    /** Convenience constructor for an item with no NBT. */
    public static ItemSnapshot of(ResourceLocation id, int count) {
        return new ItemSnapshot(id, count, Optional.empty());
    }
}
