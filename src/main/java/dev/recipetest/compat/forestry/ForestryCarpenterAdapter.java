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
package dev.recipetest.compat.forestry;

import com.mojang.logging.LogUtils;
import dev.recipetest.api.Layout;
import dev.recipetest.core.RecipeAdapter;
import dev.recipetest.core.VanillaShapedAdapter;
import dev.recipetest.core.VanillaShapelessAdapter;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Adapter for ForestryCE's {@code ICarpenterRecipe}. Pure reflection — the harness has no
 * compile-time dependency on ForestryCE, so this class loads cleanly whether or not Forestry is
 * present at runtime. If Forestry isn't on the classpath, the static-init resolution returns
 * {@code null} for the carpenter class and {@link #appliesTo(Recipe)} always returns false; the
 * adapter is a no-op rather than a hard error.
 *
 * <p>The wrapped recipe (returned by {@code ICarpenterRecipe.getCraftingGridRecipe()}) is a
 * standard vanilla {@link ShapedRecipe} or shapeless equivalent, so item dispatch delegates back
 * to {@link VanillaShapedAdapter} / {@link VanillaShapelessAdapter}. Only the fluid extraction is
 * Forestry-specific.
 *
 * <p>Phase-3 scope: handles the typical case (3×3 grid + input fluid). The carpenter's "box" slot
 * (e.g. carton/crate ingredient) is not yet modelled in the spec's {@code inputs.items.slots}, so
 * recipes with a non-empty box are still extracted correctly for the grid + fluid but the box
 * itself is ignored. A future spec extension can add the box slot.
 */
public final class ForestryCarpenterAdapter implements RecipeAdapter {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final @Nullable Class<?> CARPENTER_CLASS;
    private static final @Nullable Method GET_CRAFTING_GRID_RECIPE;
    private static final @Nullable Method GET_INPUT_FLUID;

    static {
        Class<?> cls = null;
        Method gridMethod = null;
        Method fluidMethod = null;
        try {
            cls = Class.forName("forestry.api.recipes.ICarpenterRecipe");
            gridMethod = cls.getMethod("getCraftingGridRecipe");
            fluidMethod = cls.getMethod("getInputFluid");
        } catch (ClassNotFoundException ex) {
            // Forestry not on classpath — adapter remains inert.
        } catch (NoSuchMethodException ex) {
            LOGGER.warn(
                    "recipe_test forestry: ICarpenterRecipe found but expected method missing — adapter inert ({})",
                    ex.getMessage());
            cls = null;
        }
        CARPENTER_CLASS = cls;
        GET_CRAFTING_GRID_RECIPE = gridMethod;
        GET_INPUT_FLUID = fluidMethod;
    }

    private final VanillaShapedAdapter shaped = new VanillaShapedAdapter();
    private final VanillaShapelessAdapter shapeless = new VanillaShapelessAdapter();

    @Override
    public boolean appliesTo(Recipe<?> recipe) {
        return CARPENTER_CLASS != null && CARPENTER_CLASS.isInstance(recipe);
    }

    @Override
    public Layout defaultLayout() {
        return Layout.SHAPED3X3;
    }

    @Override
    public List<ItemStack> extractInputItems(Recipe<?> recipe) {
        Recipe<?> grid = unwrapGridRecipe(recipe);
        if (grid instanceof ShapedRecipe) {
            return shaped.extractInputItems(grid);
        }
        if (grid != null) {
            return shapeless.extractInputItems(grid);
        }
        return List.of();
    }

    @Override
    public List<int[]> extractInputPositions(Recipe<?> recipe) {
        Recipe<?> grid = unwrapGridRecipe(recipe);
        if (grid instanceof ShapedRecipe) {
            return shaped.extractInputPositions(grid);
        }
        return List.of();
    }

    @Override
    public List<FluidStack> extractInputFluids(Recipe<?> recipe) {
        if (GET_INPUT_FLUID == null) {
            return List.of();
        }
        try {
            Object value = GET_INPUT_FLUID.invoke(recipe);
            if (value instanceof FluidStack stack && !stack.isEmpty()) {
                return List.of(stack);
            }
        } catch (ReflectiveOperationException ex) {
            LOGGER.warn("recipe_test forestry: getInputFluid reflection failed: {}", ex.toString());
        }
        return List.of();
    }

    @Override
    public ItemStack extractPrimaryOutput(Recipe<?> recipe, HolderLookup.Provider registries) {
        Recipe<?> grid = unwrapGridRecipe(recipe);
        if (grid != null) {
            return grid.getResultItem(registries).copy();
        }
        return ItemStack.EMPTY;
    }

    private @Nullable Recipe<?> unwrapGridRecipe(Recipe<?> outer) {
        if (GET_CRAFTING_GRID_RECIPE == null) {
            return null;
        }
        try {
            Object value = GET_CRAFTING_GRID_RECIPE.invoke(outer);
            if (value instanceof Recipe<?> wrapped) {
                return wrapped;
            }
        } catch (ReflectiveOperationException ex) {
            LOGGER.warn("recipe_test forestry: getCraftingGridRecipe reflection failed: {}", ex.toString());
        }
        return null;
    }
}
