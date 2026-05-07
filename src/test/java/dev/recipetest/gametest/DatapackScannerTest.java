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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatapackScannerTest {

    @Test
    void emitsOneRefPerMatchingRecipe(@TempDir Path tmp) throws IOException {
        Path data = tmp.resolve("mod1/data");
        writeSpec(data.resolve("forestry/recipe_test/machines/carpenter.json"), "forestry:carpenter");
        writeRecipe(data.resolve("forestry/recipe/carpenter/circuit_board_basic.json"), "forestry:carpenter");
        writeRecipe(data.resolve("forestry/recipe/carpenter/circuit_board_advanced.json"), "forestry:carpenter");
        writeRecipe(data.resolve("forestry/recipe/crafting/torch.json"), "minecraft:crafting_shaped");

        List<DatapackScanner.RecipeRef> refs = DatapackScanner.scanRoots(List.of(data));

        assertEquals(2, refs.size());
        assertTrue(refs.stream().allMatch(r -> r.recipeType().equals(rl("forestry", "carpenter"))));
        // Sorted by recipeId path: advanced before basic
        assertEquals(
                rl("forestry", "carpenter/circuit_board_advanced"), refs.get(0).recipeId());
        assertEquals(
                rl("forestry", "carpenter/circuit_board_basic"), refs.get(1).recipeId());
    }

    @Test
    void crossModSpecAndRecipesPairCorrectly(@TempDir Path tmp) throws IOException {
        Path consumerData = tmp.resolve("consumer/data");
        Path forestryData = tmp.resolve("forestry/data");
        // Consumer mod ships the spec
        writeSpec(consumerData.resolve("consumer/recipe_test/machines/carpenter.json"), "forestry:carpenter");
        // Forestry mod ships the recipes
        writeRecipe(forestryData.resolve("forestry/recipe/carpenter/widget.json"), "forestry:carpenter");

        List<DatapackScanner.RecipeRef> refs = DatapackScanner.scanRoots(List.of(consumerData, forestryData));

        assertEquals(1, refs.size());
        assertEquals(rl("forestry", "carpenter"), refs.get(0).recipeType());
        assertEquals(rl("forestry", "carpenter/widget"), refs.get(0).recipeId());
    }

    @Test
    void recipesWithoutMatchingSpecAreSkipped(@TempDir Path tmp) throws IOException {
        Path data = tmp.resolve("mod/data");
        writeRecipe(data.resolve("forestry/recipe/carpenter/widget.json"), "forestry:carpenter");

        List<DatapackScanner.RecipeRef> refs = DatapackScanner.scanRoots(List.of(data));
        assertEquals(0, refs.size(), "no spec → no refs");
    }

    @Test
    void malformedJsonDoesNotAbortScan(@TempDir Path tmp) throws IOException {
        Path data = tmp.resolve("mod/data");
        writeSpec(data.resolve("forestry/recipe_test/machines/carpenter.json"), "forestry:carpenter");
        writeRaw(data.resolve("forestry/recipe/carpenter/broken.json"), "this is not json {");
        writeRecipe(data.resolve("forestry/recipe/carpenter/good.json"), "forestry:carpenter");

        List<DatapackScanner.RecipeRef> refs = DatapackScanner.scanRoots(List.of(data));
        assertEquals(1, refs.size());
        assertEquals(rl("forestry", "carpenter/good"), refs.get(0).recipeId());
    }

    @Test
    void specWithoutRecipeTypeFieldIsIgnored(@TempDir Path tmp) throws IOException {
        Path data = tmp.resolve("mod/data");
        writeRaw(data.resolve("forestry/recipe_test/machines/incomplete.json"), "{\"version\":1,\"block\":\"x:y\"}");
        writeRecipe(data.resolve("forestry/recipe/carpenter/widget.json"), "forestry:carpenter");

        List<DatapackScanner.RecipeRef> refs = DatapackScanner.scanRoots(List.of(data));
        assertEquals(0, refs.size());
    }

    @Test
    void emptyDataRootProducesEmptyResult(@TempDir Path tmp) {
        List<DatapackScanner.RecipeRef> refs = DatapackScanner.scanRoots(List.of(tmp));
        assertEquals(0, refs.size());
    }

    @Test
    void resultOrderIsDeterministic(@TempDir Path tmp) throws IOException {
        Path data = tmp.resolve("mod/data");
        writeSpec(data.resolve("forestry/recipe_test/machines/carpenter.json"), "forestry:carpenter");
        // Insert recipes in non-alphabetical order in different namespaces
        writeRecipe(data.resolve("a/recipe/carpenter/zzz.json"), "forestry:carpenter");
        writeRecipe(data.resolve("z/recipe/carpenter/aaa.json"), "forestry:carpenter");

        List<DatapackScanner.RecipeRef> refs = DatapackScanner.scanRoots(List.of(data));
        assertEquals(2, refs.size());
        // ResourceLocation.compareTo orders by path first then namespace, so 'carpenter/aaa'
        // beats 'carpenter/zzz' regardless of which mod owns it.
        assertEquals(rl("z", "carpenter/aaa"), refs.get(0).recipeId());
        assertEquals(rl("a", "carpenter/zzz"), refs.get(1).recipeId());
    }

    // ---- helpers ----

    private static void writeSpec(Path path, String recipeType) throws IOException {
        writeRaw(path, "{\"version\":1,\"recipeType\":\"" + recipeType + "\"}");
    }

    private static void writeRecipe(Path path, String type) throws IOException {
        writeRaw(path, "{\"type\":\"" + type + "\",\"some\":\"payload\"}");
    }

    private static void writeRaw(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static ResourceLocation rl(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
