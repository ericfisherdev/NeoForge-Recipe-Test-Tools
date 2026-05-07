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
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.recipetest.api.Diagnostics;
import dev.recipetest.api.FluidSnapshot;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.ItemSnapshot;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestResult;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ChannelExtractorTest {

    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath("forestry", "carpenter/x");
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("forestry", "carpenter");

    @Test
    void firstItemIdWins() {
        TestResult r = withItems(List.of(
                ItemSnapshot.of(ResourceLocation.parse("minecraft:stone"), 1),
                ItemSnapshot.of(ResourceLocation.parse("minecraft:dirt"), 1)));
        assertEquals("minecraft:stone", ChannelExtractor.channelOf(r));
    }

    @Test
    void firstFluidIdWinsWhenNoItems() {
        TestResult r = withFluids(List.of(FluidSnapshot.of(ResourceLocation.parse("minecraft:water"), 1000)));
        assertEquals("minecraft:water", ChannelExtractor.channelOf(r));
    }

    @Test
    void recipeIdFallbackWhenSnapshotEmpty() {
        TestResult r = withResult(IoSnapshot.empty());
        assertEquals("forestry:carpenter/x", ChannelExtractor.channelOf(r));
    }

    @Test
    void itemsTakePrecedenceOverFluids() {
        TestResult r = withSnapshot(new IoSnapshot(
                List.of(ItemSnapshot.of(ResourceLocation.parse("minecraft:stone"), 1)),
                List.of(FluidSnapshot.of(ResourceLocation.parse("minecraft:water"), 1000))));
        assertEquals("minecraft:stone", ChannelExtractor.channelOf(r));
    }

    @Test
    void rejectsNullResult() {
        assertThrows(NullPointerException.class, () -> ChannelExtractor.channelOf(null));
    }

    // ---- helpers ----

    private static TestResult withItems(List<ItemSnapshot> items) {
        return withSnapshot(new IoSnapshot(items, List.of()));
    }

    private static TestResult withFluids(List<FluidSnapshot> fluids) {
        return withSnapshot(new IoSnapshot(List.of(), fluids));
    }

    private static TestResult withSnapshot(IoSnapshot actual) {
        return withResult(actual);
    }

    private static TestResult withResult(IoSnapshot actual) {
        return new TestResult(
                RECIPE_ID,
                TYPE,
                "forestry:carpenter.json",
                RunStatus.PASS,
                1,
                IoSnapshot.empty(),
                actual,
                Optional.empty(),
                new Diagnostics(List.of(), 0L, List.of(), List.of()));
    }
}
