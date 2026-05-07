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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.recipetest.api.Layout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ForestryCarpenterAdapter}. The harness's CI classpath does not include
 * ForestryCE, so the adapter's static-init falls back to the inert state — these tests assert
 * that fallback rather than verifying carpenter-specific extraction (which lives in the manual
 * Forestry integration procedure documented in {@code progress.md}).
 */
class ForestryCarpenterAdapterTest {

    @Test
    @DisplayName("Forestry's ICarpenterRecipe is absent on the unit-test classpath")
    void forestryClassIsNotPresent() {
        // Sanity check — if this ever flips, the rest of the suite needs revisiting because the
        // adapter would actually be active.
        boolean present;
        try {
            Class.forName("forestry.api.recipes.ICarpenterRecipe");
            present = true;
        } catch (ClassNotFoundException e) {
            present = false;
        }
        assertFalse(present, "ForestryCE classes should not be on the harness's CI classpath");
    }

    @Test
    @DisplayName("appliesTo returns false when Forestry isn't present")
    void appliesToWhenForestryAbsent() {
        ForestryCarpenterAdapter adapter = new ForestryCarpenterAdapter();
        // CARPENTER_CLASS short-circuits to false; the recipe arg is never dereferenced.
        assertFalse(adapter.appliesTo(null));
    }

    @Test
    @DisplayName("Default layout is SHAPED3X3")
    void defaultLayoutShaped3x3() {
        assertEquals(Layout.SHAPED3X3, new ForestryCarpenterAdapter().defaultLayout());
    }

    @Test
    @DisplayName("Inert adapter returns empty extractions instead of throwing")
    void inertExtractionsAreEmpty() {
        // Pre-condition: Forestry is absent in CI; if a future setup adds it, skip.
        try {
            Class.forName("forestry.api.recipes.ICarpenterRecipe");
            assumeTrue(false, "Forestry on classpath — skip the inert-fallback assertions");
        } catch (ClassNotFoundException ignored) {
            // expected
        }
        ForestryCarpenterAdapter adapter = new ForestryCarpenterAdapter();
        assertTrue(adapter.extractInputItems(null).isEmpty());
        assertTrue(adapter.extractInputPositions(null).isEmpty());
        assertTrue(adapter.extractInputFluids(null).isEmpty());
    }
}
