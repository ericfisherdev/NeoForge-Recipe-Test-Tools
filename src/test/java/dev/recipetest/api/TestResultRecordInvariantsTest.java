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
package dev.recipetest.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Invariants for the new {@link TestResult} family of records. */
class TestResultRecordInvariantsTest {

    private static final ResourceLocation RECIPE_ID = ResourceLocation.parse("examplemod:recipes/thing");
    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.parse("examplemod:thing");
    private static final ResourceLocation STICK = ResourceLocation.parse("minecraft:stick");
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");

    // ---- ItemSnapshot ----

    @Test
    @DisplayName("ItemSnapshot rejects negative count")
    void itemSnapshotNegativeCount() {
        assertThrows(IllegalArgumentException.class, () -> new ItemSnapshot(STICK, -1, Optional.empty()));
    }

    @Test
    @DisplayName("ItemSnapshot rejects null id / nbt")
    void itemSnapshotNullFields() {
        assertThrows(NullPointerException.class, () -> new ItemSnapshot(null, 1, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new ItemSnapshot(STICK, 1, null));
    }

    // ---- FluidSnapshot ----

    @Test
    @DisplayName("FluidSnapshot rejects negative amount")
    void fluidSnapshotNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new FluidSnapshot(WATER, -1, Optional.empty()));
    }

    // ---- IoSnapshot ----

    @Test
    @DisplayName("IoSnapshot defensively copies its lists")
    void ioSnapshotDefensiveCopy() {
        List<ItemSnapshot> items = new ArrayList<>();
        items.add(ItemSnapshot.of(STICK, 1));
        IoSnapshot snap = new IoSnapshot(items, List.of());
        items.clear();
        assertEquals(1, snap.items().size(), "mutating source list must not affect snapshot");
    }

    @Test
    @DisplayName("IoSnapshot.empty() returns the same singleton")
    void ioSnapshotEmptySingleton() {
        assertSame(IoSnapshot.empty(), IoSnapshot.empty());
        assertTrue(IoSnapshot.empty().items().isEmpty());
        assertTrue(IoSnapshot.empty().fluids().isEmpty());
    }

    // ---- DiffEntry ----

    @Test
    @DisplayName("DiffEntry rejects empty path / reason")
    void diffEntryRejectsBlanks() {
        assertThrows(IllegalArgumentException.class, () -> new DiffEntry("", "a", "b", "reason"));
        assertThrows(IllegalArgumentException.class, () -> new DiffEntry("/x", "a", "b", ""));
    }

    // ---- DiffPayload ----

    @Test
    @DisplayName("DiffPayload rejects empty mismatches list")
    void diffPayloadEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new DiffPayload(List.of()));
    }

    @Test
    @DisplayName("DiffPayload defensively copies its mismatches list")
    void diffPayloadDefensiveCopy() {
        List<DiffEntry> entries = new ArrayList<>();
        entries.add(new DiffEntry("/items/0", "stick x1", "", "missing"));
        DiffPayload payload = new DiffPayload(entries);
        DiffEntry replacement = new DiffEntry("/items/1", "x", "y", "z");
        entries.set(0, replacement);
        assertNotSame(replacement, payload.mismatches().get(0));
    }

    // ---- Diagnostics ----

    @Test
    @DisplayName("Diagnostics.empty() returns the same singleton")
    void diagnosticsEmptySingleton() {
        assertSame(Diagnostics.empty(), Diagnostics.empty());
        assertEquals(0L, Diagnostics.empty().energyConsumed());
    }

    // ---- TestResult ----

    private static TestResult passResult() {
        return new TestResult(
                RECIPE_ID,
                RECIPE_TYPE,
                "examplemod:thing.json",
                RunStatus.PASS,
                42,
                IoSnapshot.empty(),
                IoSnapshot.empty(),
                Optional.empty(),
                Diagnostics.empty());
    }

    @Test
    @DisplayName("TestResult.PASS without diff is valid")
    void testResultPassNoDiff() {
        TestResult result = passResult();
        assertEquals(RunStatus.PASS, result.status());
        assertTrue(result.diff().isEmpty());
    }

    @Test
    @DisplayName("TestResult.FAIL requires a diff payload")
    void testResultFailRequiresDiff() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestResult(
                        RECIPE_ID,
                        RECIPE_TYPE,
                        "examplemod:thing.json",
                        RunStatus.FAIL,
                        42,
                        IoSnapshot.empty(),
                        IoSnapshot.empty(),
                        Optional.empty(),
                        Diagnostics.empty()));
    }

    @Test
    @DisplayName("TestResult.CANCELLED is valid without a diff payload")
    void testResultCancelledNoDiff() {
        TestResult result = new TestResult(
                RECIPE_ID,
                RECIPE_TYPE,
                "examplemod:thing.json",
                RunStatus.CANCELLED,
                7,
                IoSnapshot.empty(),
                IoSnapshot.empty(),
                Optional.empty(),
                Diagnostics.empty());
        assertEquals(RunStatus.CANCELLED, result.status());
        assertTrue(result.diff().isEmpty());
    }

    @Test
    @DisplayName("TestResult.CANCELLED rejects an attached diff payload")
    void testResultCancelledRejectsDiff() {
        DiffPayload diff = new DiffPayload(List.of(new DiffEntry("/items/0", "a", "b", "x")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestResult(
                        RECIPE_ID,
                        RECIPE_TYPE,
                        "examplemod:thing.json",
                        RunStatus.CANCELLED,
                        7,
                        IoSnapshot.empty(),
                        IoSnapshot.empty(),
                        Optional.of(diff),
                        Diagnostics.empty()));
    }

    @Test
    @DisplayName("TestResult.PASS rejects an attached diff payload")
    void testResultPassRejectsDiff() {
        DiffPayload diff = new DiffPayload(List.of(new DiffEntry("/items/0", "a", "b", "x")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestResult(
                        RECIPE_ID,
                        RECIPE_TYPE,
                        "examplemod:thing.json",
                        RunStatus.PASS,
                        42,
                        IoSnapshot.empty(),
                        IoSnapshot.empty(),
                        Optional.of(diff),
                        Diagnostics.empty()));
    }

    @Test
    @DisplayName("TestResult rejects negative ticksElapsed")
    void testResultNegativeTicks() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestResult(
                        RECIPE_ID,
                        RECIPE_TYPE,
                        "examplemod:thing.json",
                        RunStatus.PASS,
                        -1,
                        IoSnapshot.empty(),
                        IoSnapshot.empty(),
                        Optional.empty(),
                        Diagnostics.empty()));
    }

    @Test
    @DisplayName("TestResult rejects empty specSource")
    void testResultEmptySpecSource() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestResult(
                        RECIPE_ID,
                        RECIPE_TYPE,
                        "",
                        RunStatus.PASS,
                        0,
                        IoSnapshot.empty(),
                        IoSnapshot.empty(),
                        Optional.empty(),
                        Diagnostics.empty()));
    }
}
