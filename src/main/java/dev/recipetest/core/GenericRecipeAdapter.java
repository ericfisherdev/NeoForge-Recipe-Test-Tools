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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Last-resort adapter that always applies. Pulls items from {@link Recipe#getIngredients()} and
 * the primary output from {@link Recipe#getResultItem(HolderLookup.Provider)} — the only two
 * surfaces the {@code Recipe<?>} contract guarantees on every recipe regardless of mod.
 *
 * <p>Many modded recipes (Forestry's machine recipes, Mekanism's chemical recipes, etc.) do not
 * override {@code getIngredients()} and use a recipe-class-specific input API instead. For those
 * recipes this adapter returns empty inputs and an empty/zero output and the runner relies on a
 * registered {@link dev.recipetest.api.RecipeTestExtension} to inject the actual ingredients via
 * {@link dev.recipetest.api.RecipeTestExtension#injectInputs} returning {@link
 * dev.recipetest.api.RecipeTestExtension.InjectionDecision#HANDLED} and validate the actual output
 * via {@link dev.recipetest.api.RecipeTestExtension#validateOutput}.
 *
 * <p>This is the architectural contract: the kit ships only a generic adapter; consumer mods
 * own the per-recipe-class injection / validation through the L2 SPI. Mod-specific adapters in
 * the kit's source tree contradict the mod-agnostic thru-line, so they do not exist here.
 *
 * <p>Registered last in {@link RecipeAdapters} so that {@link VanillaShapedAdapter} and
 * {@link VanillaShapelessAdapter} continue to handle vanilla-style recipes with fully populated
 * {@code getIngredients()}.
 */
public final class GenericRecipeAdapter implements RecipeAdapter {

    @Override
    public boolean appliesTo(Recipe<?> recipe) {
        return true;
    }

    @Override
    public Layout defaultLayout() {
        return Layout.SHAPELESS;
    }

    @Override
    public List<ItemStack> extractInputItems(Recipe<?> recipe) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) {
            return List.of();
        }
        List<ItemStack> result = new ArrayList<>(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack[] candidates = ingredient.getItems();
            result.add(candidates.length == 0 ? ItemStack.EMPTY : candidates[0].copy());
        }
        return result;
    }

    @Override
    public List<int[]> extractInputPositions(Recipe<?> recipe) {
        return List.of();
    }

    @Override
    public ItemStack extractPrimaryOutput(Recipe<?> recipe, HolderLookup.Provider registries) {
        return recipe.getResultItem(registries).copy();
    }
}
