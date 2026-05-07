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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.Test;

class RecipeTestExtensionTest {

    @Test
    void defaultMethodsFallThrough() {
        RecipeTestExtension<Recipe<?>> ext = () -> ResourceLocation.fromNamespaceAndPath("test", "kind");

        assertEquals(-1, ext.tickBudgetOverride(null), "default tick budget signals fall-through");
        assertEquals(
                RecipeTestExtension.InjectionDecision.FALL_THROUGH,
                ext.injectInputs(null, null),
                "default injectInputs signals fall-through");
        assertTrue(ext.supportedKinds().isEmpty(), "no kinds claimed by default");
        assertTrue(ext.weights(null).isEmpty(), "no per-output weights by default");
        assertTrue(
                ext.resolveCustomBinding(ResourceLocation.fromNamespaceAndPath("test", "x"), "ref", null)
                        .isEmpty(),
                "custom binding falls through by default");
        assertTrue(ext.validateOutput(null, null, IoSnapshot.empty()).isEmpty(), "validation falls through by default");
    }

    @Test
    void overridingHookSurfacesValue() {
        ResourceLocation kind = ResourceLocation.fromNamespaceAndPath("mekanism", "gas");
        RecipeTestExtension<Recipe<?>> ext = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return ResourceLocation.fromNamespaceAndPath("mekanism", "compressing");
            }

            @Override
            public java.util.Set<ResourceLocation> supportedKinds() {
                return java.util.Set.of(kind);
            }

            @Override
            public int tickBudgetOverride(net.minecraft.world.item.crafting.RecipeHolder<Recipe<?>> holder) {
                return 400;
            }
        };

        assertEquals(java.util.Set.of(kind), ext.supportedKinds());
        assertEquals(400, ext.tickBudgetOverride(null));
        assertEquals(ResourceLocation.fromNamespaceAndPath("mekanism", "compressing"), ext.recipeType());
    }
}
