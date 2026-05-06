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
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * Adapter for any single-output, item-only recipe whose ingredient list is the entire input set —
 * vanilla {@link ShapelessRecipe}, smelting/blasting/smoking, stonecutting, etc. Anything more
 * exotic (fluid inputs, secondary outputs) needs a dedicated adapter.
 */
public final class VanillaShapelessAdapter implements RecipeAdapter {

    @Override
    public boolean appliesTo(Recipe<?> recipe) {
        // Catch-all for the simple case: ingredients() returns the full input set, and there's
        // no shaped pattern to honour. ShapedRecipe is matched first (registry order) so this
        // never picks up a shaped recipe by accident.
        return !recipe.getIngredients().isEmpty();
    }

    @Override
    public Layout defaultLayout() {
        return Layout.SHAPELESS;
    }

    @Override
    public List<ItemStack> extractInputItems(Recipe<?> recipe) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
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
