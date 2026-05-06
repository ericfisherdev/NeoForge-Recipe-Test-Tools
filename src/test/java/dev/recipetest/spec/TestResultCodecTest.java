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
package dev.recipetest.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.recipetest.api.Diagnostics;
import dev.recipetest.api.DiffEntry;
import dev.recipetest.api.DiffPayload;
import dev.recipetest.api.FluidSnapshot;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.ItemSnapshot;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestResult;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestResultCodecTest {

    private static final ResourceLocation RECIPE_ID = ResourceLocation.parse("forestry:carpenter/circuit_board_basic");
    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.parse("forestry:carpenter");
    private static final ResourceLocation CIRCUIT = ResourceLocation.parse("forestry:circuit_board");
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");

    @Test
    @DisplayName("PASS result round-trips losslessly")
    void roundTripPass() {
        TestResult original = new TestResult(
                RECIPE_ID,
                RECIPE_TYPE,
                "forestry:carpenter.json",
                RunStatus.PASS,
                80,
                new IoSnapshot(List.of(ItemSnapshot.of(CIRCUIT, 1)), List.of()),
                new IoSnapshot(List.of(ItemSnapshot.of(CIRCUIT, 1)), List.of()),
                Optional.empty(),
                new Diagnostics(List.of(FluidSnapshot.of(WATER, 1000)), 0L, List.of(), List.of()));

        TestResult roundTripped = encodeDecode(original);
        assertEquals(original, roundTripped);
    }

    @Test
    @DisplayName("FAIL result round-trips with diff payload")
    void roundTripFail() {
        DiffPayload diff = new DiffPayload(List.of(new DiffEntry("/items/0", "circuit x1", "", "missing")));
        TestResult original = new TestResult(
                RECIPE_ID,
                RECIPE_TYPE,
                "forestry:carpenter.json",
                RunStatus.FAIL,
                200,
                new IoSnapshot(List.of(ItemSnapshot.of(CIRCUIT, 1)), List.of()),
                IoSnapshot.empty(),
                Optional.of(diff),
                Diagnostics.empty());

        TestResult roundTripped = encodeDecode(original);
        assertEquals(original, roundTripped);
        assertTrue(roundTripped.diff().isPresent());
        assertEquals(1, roundTripped.diff().orElseThrow().mismatches().size());
    }

    @Test
    @DisplayName("TIMEOUT result round-trips without diff")
    void roundTripTimeout() {
        TestResult original = new TestResult(
                RECIPE_ID,
                RECIPE_TYPE,
                "forestry:carpenter.json",
                RunStatus.TIMEOUT,
                1,
                IoSnapshot.empty(),
                IoSnapshot.empty(),
                Optional.empty(),
                Diagnostics.empty());

        TestResult roundTripped = encodeDecode(original);
        assertEquals(original, roundTripped);
        assertFalse(roundTripped.diff().isPresent());
    }

    @Test
    @DisplayName("Encoded JSON omits diff field when absent")
    void encodedOmitsAbsentDiff() {
        TestResult original = new TestResult(
                RECIPE_ID,
                RECIPE_TYPE,
                "forestry:carpenter.json",
                RunStatus.PASS,
                80,
                IoSnapshot.empty(),
                IoSnapshot.empty(),
                Optional.empty(),
                Diagnostics.empty());

        DataResult<JsonElement> encoded = TestResultCodec.CODEC.encodeStart(JsonOps.INSTANCE, original);
        JsonElement json = encoded.result().orElseThrow();
        assertFalse(json.getAsJsonObject().has("diff"), "encoded form should omit absent diff field");
    }

    @Test
    @DisplayName("Status codec rejects unknown status string")
    void statusRejectsUnknown() {
        DataResult<RunStatus> result =
                TestResultCodec.STATUS_CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createString("BOGUS"));
        assertTrue(result.error().isPresent());
    }

    private static TestResult encodeDecode(TestResult original) {
        DataResult<JsonElement> encoded = TestResultCodec.CODEC.encodeStart(JsonOps.INSTANCE, original);
        JsonElement json = encoded.result().orElseThrow(() -> new AssertionError("encode failed: " + encoded.error()));
        DataResult<TestResult> decoded = TestResultCodec.CODEC.parse(JsonOps.INSTANCE, json);
        return decoded.result().orElseThrow(() -> new AssertionError("decode failed: " + decoded.error()));
    }
}
