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
 * Process-wide registry of {@link RecipeAdapter}s. Built-ins for vanilla shaped, vanilla
 * shapeless, and a generic last-resort fallback are registered eagerly; mods adding L2 adapters
 * via the {@link dev.recipetest.api.RecipeTestExtension} SPI bypass adapter selection entirely
 * by returning {@link dev.recipetest.api.RecipeTestExtension.InjectionDecision#HANDLED} from
 * {@code injectInputs} — the harness only ships generic adapters; mod-specific extraction lives
 * in consumer mods.
 *
 * <p>Lookup order is registration order. {@link VanillaShapedAdapter} matches first for shaped
 * recipes; {@link VanillaShapelessAdapter} matches anything with non-empty
 * {@link Recipe#getIngredients()}; {@link GenericRecipeAdapter} catches everything else as a
 * last resort. Mods that need recipe-class-specific input/output extraction implement
 * {@link dev.recipetest.api.RecipeTestExtension} on the consumer-mod side rather than registering
 * an adapter here.
 */
public final class RecipeAdapters {

    private static final CopyOnWriteArrayList<RecipeAdapter> ADAPTERS = new CopyOnWriteArrayList<>();

    static {
        ADAPTERS.add(new VanillaShapedAdapter());
        ADAPTERS.add(new VanillaShapelessAdapter());
        ADAPTERS.add(new GenericRecipeAdapter());
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
