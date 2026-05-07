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
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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

    private static boolean forestryPresent() {
        try {
            Class.forName("forestry.api.recipes.ICarpenterRecipe");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Test
    @DisplayName("Forestry's ICarpenterRecipe is absent on the unit-test classpath")
    void forestryClassIsNotPresent() {
        // Pin the contract for the no-Forestry classpath. If a future setup adds Forestry to
        // the test classpath, skip rather than fail — the inert-fallback assertions below
        // simply don't apply in that case and the adapter is exercised by manual integration.
        assumeFalse(forestryPresent(), "ForestryCE present — inert-fallback contract doesn't apply");
    }

    @Test
    @DisplayName("appliesTo returns false when Forestry isn't present")
    void appliesToWhenForestryAbsent() {
        assumeFalse(forestryPresent(), "ForestryCE present — appliesTo would legitimately match");
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
        assumeFalse(forestryPresent(), "ForestryCE present — extractions would touch a real recipe");
        ForestryCarpenterAdapter adapter = new ForestryCarpenterAdapter();
        assertTrue(adapter.extractInputItems(null).isEmpty());
        assertTrue(adapter.extractInputPositions(null).isEmpty());
        assertTrue(adapter.extractInputFluids(null).isEmpty());
    }
}
