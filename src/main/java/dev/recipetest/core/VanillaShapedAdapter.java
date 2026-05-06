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
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Adapter for vanilla {@link ShapedRecipe} (and any subclass that exposes {@code pattern}). */
public final class VanillaShapedAdapter implements RecipeAdapter {

    @Override
    public boolean appliesTo(Recipe<?> recipe) {
        return recipe instanceof ShapedRecipe;
    }

    @Override
    public Layout defaultLayout() {
        return Layout.SHAPED3X3;
    }

    @Override
    public List<ItemStack> extractInputItems(Recipe<?> recipe) {
        ShapedRecipe shaped = (ShapedRecipe) recipe;
        NonNullList<Ingredient> ingredients = shaped.pattern.ingredients();
        List<ItemStack> result = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            result.add(representativeStack(ingredient));
        }
        return result;
    }

    @Override
    public List<int[]> extractInputPositions(Recipe<?> recipe) {
        ShapedRecipe shaped = (ShapedRecipe) recipe;
        int width = shaped.pattern.width();
        int height = shaped.pattern.height();
        NonNullList<Ingredient> ingredients = shaped.pattern.ingredients();
        List<int[]> positions = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!ingredients.get(y * width + x).isEmpty()) {
                    positions.add(new int[] {x, y});
                }
            }
        }
        return positions;
    }

    @Override
    public ItemStack extractPrimaryOutput(Recipe<?> recipe, HolderLookup.Provider registries) {
        return recipe.getResultItem(registries).copy();
    }

    private static ItemStack representativeStack(Ingredient ingredient) {
        ItemStack[] candidates = ingredient.getItems();
        return candidates.length == 0 ? ItemStack.EMPTY : candidates[0].copy();
    }
}
