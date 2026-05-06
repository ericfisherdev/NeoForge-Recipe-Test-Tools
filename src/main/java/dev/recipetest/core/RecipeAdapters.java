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
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Process-wide registry of {@link RecipeAdapter}s. Built-ins for vanilla shaped + shapeless are
 * registered eagerly; mods adding L2 adapters in Phase 5 will use {@link #register(RecipeAdapter)}.
 *
 * <p>Lookup order is registration order — built-ins are registered first, so subclass-specific
 * adapters added later take precedence over the catch-all {@link VanillaShapelessAdapter}.
 */
public final class RecipeAdapters {

    private static final CopyOnWriteArrayList<RecipeAdapter> ADAPTERS = new CopyOnWriteArrayList<>();

    static {
        ADAPTERS.add(new VanillaShapedAdapter());
        ADAPTERS.add(new VanillaShapelessAdapter());
    }

    private RecipeAdapters() {}

    /** Register a custom adapter — newly added adapters take precedence over earlier entries. */
    public static void register(RecipeAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter must not be null");
        // Insert at the front so mod-specific adapters override the vanilla catch-all.
        ADAPTERS.add(0, adapter);
    }

    /** First adapter whose {@link RecipeAdapter#appliesTo(Recipe)} returns true. */
    public static Optional<RecipeAdapter> findFor(Recipe<?> recipe) {
        Objects.requireNonNull(recipe, "recipe must not be null");
        for (RecipeAdapter adapter : ADAPTERS) {
            if (adapter.appliesTo(recipe)) {
                return Optional.of(adapter);
            }
        }
        return Optional.empty();
    }

    /** Snapshot of currently-registered adapters in lookup order. */
    public static List<RecipeAdapter> snapshot() {
        return List.copyOf(ADAPTERS);
    }
}
