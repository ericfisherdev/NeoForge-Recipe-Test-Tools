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
package dev.recipetest.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.gametest.DatapackScanner.RecipeRef;
import java.util.List;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DynamicGameTestGeneratorTest {

    @Test
    void emitsOneTestFunctionPerRef() {
        List<RecipeRef> refs = List.of(
                new RecipeRef(rl("forestry", "carpenter"), rl("forestry", "circuit_board_basic")),
                new RecipeRef(rl("forestry", "carpenter"), rl("forestry", "circuit_board_advanced")));

        List<TestFunction> fns = DynamicGameTestGenerator.buildFunctions(refs, null);

        assertEquals(2, fns.size());
        assertTrue(fns.stream().allMatch(fn -> fn.testName().startsWith("recipe_test.forestry.carpenter.")));
        assertTrue(fns.stream().allMatch(fn -> "recipe_test:empty5".equals(fn.structureName())));
    }

    @Test
    void filterRegexExcludesNonMatchingNames() {
        List<RecipeRef> refs = List.of(
                new RecipeRef(rl("forestry", "carpenter"), rl("forestry", "alpha")),
                new RecipeRef(rl("forestry", "centrifuge"), rl("forestry", "beta")));

        // Filter keeps only carpenter tests
        List<TestFunction> fns = DynamicGameTestGenerator.buildFunctions(refs, "recipe_test\\.forestry\\.carpenter\\.");
        assertEquals(1, fns.size());
        assertTrue(fns.get(0).testName().contains("carpenter"));
    }

    @Test
    void invalidFilterRegexFallsBackToNoFilter() {
        List<RecipeRef> refs =
                List.of(new RecipeRef(rl("forestry", "carpenter"), rl("forestry", "circuit_board_basic")));

        // Unbalanced bracket — invalid regex; generator should log + emit all refs.
        List<TestFunction> fns = DynamicGameTestGenerator.buildFunctions(refs, "[unclosed");
        assertEquals(1, fns.size());
    }

    @Test
    void emptyRefsProducesEmptyOutput() {
        assertEquals(0, DynamicGameTestGenerator.buildFunctions(List.of(), null).size());
        assertEquals(
                0,
                DynamicGameTestGenerator.buildFunctions(List.of(), "anything").size());
    }

    @Test
    void testNamesMatchTestNamingFormat() {
        List<RecipeRef> refs =
                List.of(new RecipeRef(rl("forestry", "carpenter"), rl("forestry", "circuit_board_basic")));
        List<TestFunction> fns = DynamicGameTestGenerator.buildFunctions(refs, null);
        assertEquals(
                TestNaming.testName(refs.get(0).recipeType(), refs.get(0).recipeId()),
                fns.get(0).testName());
    }

    private static ResourceLocation rl(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
