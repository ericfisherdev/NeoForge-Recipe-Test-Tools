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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.RecipeTestExtension;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtensionRegistryTest {

    @BeforeEach
    @AfterEach
    void resetRegistry() {
        ExtensionRegistry.instance().resetForTesting();
    }

    @Test
    void emptyRegistryReturnsNoMatches() {
        assertTrue(ExtensionRegistry.instance()
                .forRecipeType(ResourceLocation.fromNamespaceAndPath("forestry", "carpenter"))
                .isEmpty());
        assertFalse(
                ExtensionRegistry.instance().isKindSupported(ResourceLocation.fromNamespaceAndPath("mekanism", "gas")));
    }

    @Test
    void replaceForTestingIndexesByRecipeTypeAndKind() {
        ResourceLocation rt = ResourceLocation.fromNamespaceAndPath("forestry", "centrifuge");
        ResourceLocation kind = ResourceLocation.fromNamespaceAndPath("forestry", "products");

        RecipeTestExtension<Recipe<?>> ext = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return rt;
            }

            @Override
            public Set<ResourceLocation> supportedKinds() {
                return Set.of(kind);
            }
        };
        ExtensionRegistry.instance().replaceForTesting(List.of(ext));

        assertTrue(ExtensionRegistry.instance().forRecipeType(rt).isPresent());
        assertSame(ext, ExtensionRegistry.instance().forRecipeType(rt).orElseThrow());
        assertTrue(ExtensionRegistry.instance().isKindSupported(kind));
    }

    @Test
    void duplicateRecipeTypeKeepsFirst() {
        ResourceLocation rt = ResourceLocation.fromNamespaceAndPath("forestry", "carpenter");
        RecipeTestExtension<Recipe<?>> first = () -> rt;
        RecipeTestExtension<Recipe<?>> second = () -> rt;

        ExtensionRegistry.instance().replaceForTesting(List.of(first, second));

        assertSame(first, ExtensionRegistry.instance().forRecipeType(rt).orElseThrow());
        assertEquals(1, ExtensionRegistry.instance().all().size());
    }

    @Test
    void unionOfKindsAcrossExtensions() {
        ResourceLocation k1 = ResourceLocation.fromNamespaceAndPath("a", "x");
        ResourceLocation k2 = ResourceLocation.fromNamespaceAndPath("b", "y");

        RecipeTestExtension<Recipe<?>> e1 = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return ResourceLocation.fromNamespaceAndPath("e1", "rt");
            }

            @Override
            public Set<ResourceLocation> supportedKinds() {
                return Set.of(k1);
            }
        };
        RecipeTestExtension<Recipe<?>> e2 = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return ResourceLocation.fromNamespaceAndPath("e2", "rt");
            }

            @Override
            public Set<ResourceLocation> supportedKinds() {
                return Set.of(k2);
            }
        };
        ExtensionRegistry.instance().replaceForTesting(List.of(e1, e2));

        assertTrue(ExtensionRegistry.instance().isKindSupported(k1));
        assertTrue(ExtensionRegistry.instance().isKindSupported(k2));
        assertFalse(ExtensionRegistry.instance().isKindSupported(ResourceLocation.fromNamespaceAndPath("z", "z")));
    }

    @Test
    void kindKnownPredicateReflectsRegistryState() {
        var predicate = ExtensionRegistry.instance().kindKnownPredicate();
        ResourceLocation kind = ResourceLocation.fromNamespaceAndPath("mekanism", "gas");
        assertFalse(predicate.test(kind));

        RecipeTestExtension<Recipe<?>> ext = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return ResourceLocation.fromNamespaceAndPath("mekanism", "compressing");
            }

            @Override
            public Set<ResourceLocation> supportedKinds() {
                return Set.of(kind);
            }
        };
        ExtensionRegistry.instance().replaceForTesting(List.of(ext));

        // The predicate is bound to the registry, not to the snapshot at predicate-creation time,
        // so it picks up the registration.
        assertTrue(predicate.test(kind));
    }
}
