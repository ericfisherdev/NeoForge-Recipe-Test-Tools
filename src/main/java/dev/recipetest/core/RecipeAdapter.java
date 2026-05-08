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
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Pluggable extraction layer between a {@link Recipe} object and the runner. The kit needs to
 * know what ingredients to inject, where they sit in a shaped pattern, what fluid the recipe
 * consumes, and what to expect as output — none of which are surfaced uniformly by {@code Recipe}.
 *
 * <p>Phase 2 ships built-in vanilla adapters via {@link RecipeAdapters}; the L2 SPI in Phase 5
 * will let mods register their own adapters for non-vanilla recipe types (Forestry Carpenter's
 * fluid input, Mekanism gas inputs, etc.).
 *
 * <p>All extraction methods may assume {@link #appliesTo(Recipe)} returned {@code true} for the
 * incoming recipe.
 */
public interface RecipeAdapter {

    /** True when this adapter knows how to extract data from {@code recipe}. */
    boolean appliesTo(Recipe<?> recipe);

    /**
     * Default layout the runner should use when the spec doesn't pin one explicitly. Vanilla
     * shaped recipes need {@link Layout#SHAPED3X3}; everything else defaults to
     * {@link Layout#SHAPELESS}.
     */
    Layout defaultLayout();

    /**
     * Items to inject, in declaration order. Empty {@link ItemStack}s are filtered out — the
     * shaped-pattern positions returned by {@link #extractInputPositions(Recipe)} use the same
     * order.
     */
    List<ItemStack> extractInputItems(Recipe<?> recipe);

    /**
     * Per-ingredient {@code [x, y]} grid coordinates for a shaped pattern; same order as
     * {@link #extractInputItems(Recipe)}. Returns an empty list for non-shaped recipes — the
     * runner only consults this when {@link Layout#SHAPED3X3} is in effect.
     */
    List<int[]> extractInputPositions(Recipe<?> recipe);

    /**
     * Fluid inputs the recipe consumes (e.g. Forestry Carpenter's {@code liquid}). Default is
     * empty — vanilla item-only recipes don't have fluid inputs.
     */
    default List<FluidStack> extractInputFluids(Recipe<?> recipe) {
        return List.of();
    }

    /** Primary expected output (compared against {@code outputs.items.primary}). */
    ItemStack extractPrimaryOutput(Recipe<?> recipe, HolderLookup.Provider registries);

    /**
     * Additional expected output items beyond the primary (e.g. side products of probabilistic
     * recipes). Default empty — single-output recipes return only the primary.
     */
    default List<ItemStack> extractAdditionalOutputs(Recipe<?> recipe, HolderLookup.Provider registries) {
        return List.of();
    }

    /** Expected fluid outputs (e.g. Forestry Squeezer). Default empty. */
    default List<FluidStack> extractOutputFluids(Recipe<?> recipe) {
        return List.of();
    }
}
