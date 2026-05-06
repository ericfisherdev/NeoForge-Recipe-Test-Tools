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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.recipetest.api.BulkProgress;
import dev.recipetest.api.BulkResult;
import dev.recipetest.api.Diagnostics;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.ItemSnapshot;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BulkResultCodecTest {

    private static final ResourceLocation RECIPE_ID = ResourceLocation.parse("forestry:carpenter/circuit_board_basic");
    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.parse("forestry:carpenter");
    private static final ResourceLocation CIRCUIT = ResourceLocation.parse("forestry:circuit_board");

    @Test
    @DisplayName("BulkProgress round-trips losslessly")
    void progressRoundTrip() {
        BulkProgress original = new BulkProgress(
                "bulk-abc12345",
                "forestry:carpenter",
                10,
                3,
                Map.of(RunStatus.PASS, 2, RunStatus.FAIL, 1),
                Optional.of(RECIPE_ID),
                70);
        BulkProgress decoded = roundTrip(BulkResultCodec.PROGRESS_CODEC, original);
        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("BulkResult round-trips with non-empty results list")
    void resultRoundTrip() {
        TestResult inner = new TestResult(
                RECIPE_ID,
                RECIPE_TYPE,
                "forestry:carpenter.json",
                RunStatus.PASS,
                80,
                new IoSnapshot(List.of(ItemSnapshot.of(CIRCUIT, 1)), List.of()),
                new IoSnapshot(List.of(ItemSnapshot.of(CIRCUIT, 1)), List.of()),
                Optional.empty(),
                Diagnostics.empty());
        BulkResult original = new BulkResult(
                "bulk-abc12345", "forestry:carpenter", Map.of(RunStatus.PASS, 1), 42L, 80, 14L, false, List.of(inner));
        BulkResult decoded = roundTrip(BulkResultCodec.RESULT_CODEC, original);
        assertEquals(original, decoded);
        assertEquals(1, decoded.results().size());
    }

    @Test
    @DisplayName("BulkResult cancelled flag survives round-trip")
    void resultCancelledRoundTrip() {
        BulkResult original = new BulkResult(
                "bulk-abc12345",
                "all",
                Map.of(RunStatus.CANCELLED, 1, RunStatus.PASS, 2),
                500L,
                200,
                28L,
                true,
                List.of());
        BulkResult decoded = roundTrip(BulkResultCodec.RESULT_CODEC, original);
        assertEquals(original, decoded);
        assertTrue(decoded.cancelled());
    }

    private static <T> T roundTrip(com.mojang.serialization.Codec<T> codec, T input) {
        DataResult<JsonElement> encoded = codec.encodeStart(JsonOps.INSTANCE, input);
        JsonElement json = encoded.result().orElseThrow(() -> new AssertionError("encode failed: " + encoded.error()));
        DataResult<T> decoded = codec.parse(JsonOps.INSTANCE, json);
        return decoded.result().orElseThrow(() -> new AssertionError("decode failed: " + decoded.error()));
    }
}
