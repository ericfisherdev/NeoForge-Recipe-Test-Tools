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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.Layout;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecipeAdaptersTest {

    @Test
    @DisplayName("Built-in vanilla adapters are registered at class init")
    void builtinsRegistered() {
        List<RecipeAdapter> snapshot = RecipeAdapters.snapshot();
        assertTrue(
                snapshot.stream().anyMatch(a -> a instanceof VanillaShapedAdapter),
                "VanillaShapedAdapter must be registered");
        assertTrue(
                snapshot.stream().anyMatch(a -> a instanceof VanillaShapelessAdapter),
                "VanillaShapelessAdapter must be registered");
    }

    @Test
    @DisplayName("VanillaShapedAdapter defaults to SHAPED3X3 layout")
    void shapedDefaultLayout() {
        assertEquals(Layout.SHAPED3X3, new VanillaShapedAdapter().defaultLayout());
    }

    @Test
    @DisplayName("VanillaShapelessAdapter defaults to SHAPELESS layout")
    void shapelessDefaultLayout() {
        assertEquals(Layout.SHAPELESS, new VanillaShapelessAdapter().defaultLayout());
    }

    @Test
    @DisplayName("findFor rejects null recipe")
    void findForRejectsNull() {
        assertThrows(NullPointerException.class, () -> RecipeAdapters.findFor(null));
    }

    @Test
    @DisplayName("snapshot returns a defensive copy")
    void snapshotIsDefensive() {
        List<RecipeAdapter> snap1 = RecipeAdapters.snapshot();
        List<RecipeAdapter> snap2 = RecipeAdapters.snapshot();
        // Same content, but each call returns a fresh immutable list.
        assertEquals(snap1, snap2);
        // Confirm the snapshot is immutable so a misbehaving caller can't pollute the registry.
        assertThrows(UnsupportedOperationException.class, () -> snap1.add(new VanillaShapelessAdapter()));
    }

    @Test
    @DisplayName("RunSessionScheduler is a process-wide singleton")
    void schedulerSingleton() {
        assertSame(RunSessionScheduler.instance(), RunSessionScheduler.instance());
        assertEquals(0, RunSessionScheduler.instance().activeCount());
    }
}
