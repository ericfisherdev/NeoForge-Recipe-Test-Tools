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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TestNamingTest {

    @Test
    void simpleNameContainsAllSegments() {
        String name = TestNaming.testName(
                ResourceLocation.fromNamespaceAndPath("forestry", "carpenter"),
                ResourceLocation.fromNamespaceAndPath("forestry", "circuit_board_basic"));
        assertEquals("recipe_test.forestry.carpenter.forestry.circuit_board_basic", name);
    }

    @Test
    void nestedRecipePathIsSanitized() {
        String name = TestNaming.testName(
                ResourceLocation.fromNamespaceAndPath("forestry", "carpenter"),
                ResourceLocation.fromNamespaceAndPath("forestry", "carpenter/circuit_board_basic"));
        assertEquals("recipe_test.forestry.carpenter.forestry.carpenter_circuit_board_basic", name);
    }

    @Test
    void uppercaseAndPunctuationCollapseToUnderscores() {
        // ResourceLocation paths only allow lowercase, but defensive sanitisation should still
        // handle any bad input the scanner might surface from a malformed datapack.
        assertAll(
                () -> assertEquals("foo_bar", TestNaming.sanitize("foo-bar")),
                () -> assertEquals("foo_bar", TestNaming.sanitize("foo bar")),
                () -> assertEquals("foo_bar", TestNaming.sanitize("FOO_BAR")),
                () -> assertEquals("foo_bar", TestNaming.sanitize("foo:bar")),
                () -> assertEquals("foo_bar", TestNaming.sanitize("foo/bar")),
                () -> assertEquals("a_b_c", TestNaming.sanitize("a/b:c")));
    }

    @Test
    void emptySegmentBecomesUnderscore() {
        assertEquals("_", TestNaming.sanitize(""));
    }

    @Test
    void deterministicAcrossInvocations() {
        ResourceLocation type = ResourceLocation.fromNamespaceAndPath("forestry", "carpenter");
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("forestry", "circuit_board_basic");
        assertEquals(TestNaming.testName(type, id), TestNaming.testName(type, id));
    }

    @Test
    void nullArgumentsAreRejected() {
        ResourceLocation type = ResourceLocation.fromNamespaceAndPath("forestry", "carpenter");
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> TestNaming.testName(null, type)),
                () -> assertThrows(NullPointerException.class, () -> TestNaming.testName(type, null)));
    }

    @Test
    void prefixIsRecipeTest() {
        String name = TestNaming.testName(
                ResourceLocation.fromNamespaceAndPath("a", "b"), ResourceLocation.fromNamespaceAndPath("c", "d"));
        assertTrue(name.startsWith(TestNaming.PREFIX + '.'));
    }
}
