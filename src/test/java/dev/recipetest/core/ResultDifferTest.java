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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.DiffEntry;
import dev.recipetest.api.DiffPayload;
import dev.recipetest.api.FluidSnapshot;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.ItemSnapshot;
import dev.recipetest.api.ValidationPolicy;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultDifferTest {

    private static final ResourceLocation STICK = ResourceLocation.parse("minecraft:stick");
    private static final ResourceLocation PLANK = ResourceLocation.parse("minecraft:oak_planks");
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final ResourceLocation LAVA = ResourceLocation.parse("minecraft:lava");

    private static final ValidationPolicy EXACT = ValidationPolicy.DEFAULT;
    private static final ValidationPolicy SUBSET = new ValidationPolicy(
            ValidationPolicy.Mode.SUBSET,
            ValidationPolicy.ItemTolerance.EXACT,
            ValidationPolicy.FluidTolerance.EXACT,
            ValidationPolicy.NbtCompare.STRUCTURAL,
            1,
            0.05);

    @Test
    @DisplayName("Identical items + fluids → no diff")
    void identicalSnapshotsMatch() {
        IoSnapshot snap = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 4)), List.of(FluidSnapshot.of(WATER, 1000)));
        assertTrue(ResultDiffer.diff(snap, snap, EXACT).isEmpty());
    }

    @Test
    @DisplayName("Empty expected and actual → no diff")
    void bothEmptyMatch() {
        assertTrue(
                ResultDiffer.diff(IoSnapshot.empty(), IoSnapshot.empty(), EXACT).isEmpty());
    }

    @Test
    @DisplayName("Missing item produces 'missing item' entry")
    void missingItem() {
        IoSnapshot expected = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 4)), List.of());
        IoSnapshot actual = IoSnapshot.empty();
        DiffPayload diff =
                ResultDiffer.diff(expected, actual, EXACT).orElseThrow(() -> new AssertionError("expected diff"));
        assertEquals(1, diff.mismatches().size());
        DiffEntry e = diff.mismatches().get(0);
        assertEquals("/items/0", e.path());
        assertEquals("missing item", e.reason());
        assertEquals("(missing)", e.actual());
    }

    @Test
    @DisplayName("Count mismatch beyond tolerance produces 'count mismatch'")
    void countMismatch() {
        IoSnapshot expected = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 4)), List.of());
        IoSnapshot actual = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 2)), List.of());
        DiffPayload diff = ResultDiffer.diff(expected, actual, EXACT).orElseThrow();
        assertEquals(1, diff.mismatches().size());
        DiffEntry e = diff.mismatches().get(0);
        assertTrue(e.reason().startsWith("count mismatch"), () -> "unexpected reason: " + e.reason());
        assertEquals("/items/0", e.path());
    }

    @Test
    @DisplayName("Count mismatch within tolerance does not produce a diff")
    void countWithinTolerance() {
        IoSnapshot expected = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 4)), List.of());
        IoSnapshot actual = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 5)), List.of());
        ValidationPolicy lenient = new ValidationPolicy(
                ValidationPolicy.Mode.EXACT,
                new ValidationPolicy.ItemTolerance(1),
                ValidationPolicy.FluidTolerance.EXACT,
                ValidationPolicy.NbtCompare.STRUCTURAL,
                1,
                0.05);
        assertTrue(ResultDiffer.diff(expected, actual, lenient).isEmpty());
    }

    @Test
    @DisplayName("Extra actual item triggers 'unexpected extra' under EXACT")
    void extraActualUnderExact() {
        IoSnapshot expected = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 4)), List.of());
        IoSnapshot actual = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 4), ItemSnapshot.of(PLANK, 1)), List.of());
        DiffPayload diff = ResultDiffer.diff(expected, actual, EXACT).orElseThrow();
        assertEquals(1, diff.mismatches().size());
        assertEquals("unexpected extra item", diff.mismatches().get(0).reason());
        assertEquals("/items/extra", diff.mismatches().get(0).path());
    }

    @Test
    @DisplayName("Extra actual item is allowed under SUBSET")
    void extraActualUnderSubset() {
        IoSnapshot expected = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 4)), List.of());
        IoSnapshot actual = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 4), ItemSnapshot.of(PLANK, 1)), List.of());
        assertTrue(ResultDiffer.diff(expected, actual, SUBSET).isEmpty());
    }

    @Test
    @DisplayName("ID mismatch surfaces as missing on expected and extra on actual")
    void idMismatch() {
        IoSnapshot expected = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 1)), List.of());
        IoSnapshot actual = new IoSnapshot(List.of(ItemSnapshot.of(PLANK, 1)), List.of());
        DiffPayload diff = ResultDiffer.diff(expected, actual, EXACT).orElseThrow();
        assertEquals(2, diff.mismatches().size());
        assertEquals("missing item", diff.mismatches().get(0).reason());
        assertEquals("unexpected extra item", diff.mismatches().get(1).reason());
    }

    @Test
    @DisplayName("Missing fluid produces 'missing fluid' entry")
    void missingFluid() {
        IoSnapshot expected = new IoSnapshot(List.of(), List.of(FluidSnapshot.of(WATER, 1000)));
        IoSnapshot actual = IoSnapshot.empty();
        DiffPayload diff = ResultDiffer.diff(expected, actual, EXACT).orElseThrow();
        assertEquals(1, diff.mismatches().size());
        assertEquals("missing fluid", diff.mismatches().get(0).reason());
        assertEquals("/fluids/0", diff.mismatches().get(0).path());
    }

    @Test
    @DisplayName("Fluid amount mismatch beyond tolerance produces 'amount mismatch'")
    void fluidAmountMismatch() {
        IoSnapshot expected = new IoSnapshot(List.of(), List.of(FluidSnapshot.of(WATER, 1000)));
        IoSnapshot actual = new IoSnapshot(List.of(), List.of(FluidSnapshot.of(WATER, 500)));
        DiffPayload diff = ResultDiffer.diff(expected, actual, EXACT).orElseThrow();
        DiffEntry e = diff.mismatches().get(0);
        assertTrue(e.reason().startsWith("amount mismatch"));
        assertEquals("/fluids/0", e.path());
    }

    @Test
    @DisplayName("Fluid amount within tolerance is accepted")
    void fluidWithinTolerance() {
        IoSnapshot expected = new IoSnapshot(List.of(), List.of(FluidSnapshot.of(WATER, 1000)));
        IoSnapshot actual = new IoSnapshot(List.of(), List.of(FluidSnapshot.of(WATER, 1050)));
        ValidationPolicy lenient = new ValidationPolicy(
                ValidationPolicy.Mode.EXACT,
                ValidationPolicy.ItemTolerance.EXACT,
                new ValidationPolicy.FluidTolerance(50),
                ValidationPolicy.NbtCompare.STRUCTURAL,
                1,
                0.05);
        assertTrue(ResultDiffer.diff(expected, actual, lenient).isEmpty());
    }

    @Test
    @DisplayName("Fluid id mismatch surfaces as missing + extra")
    void fluidIdMismatch() {
        IoSnapshot expected = new IoSnapshot(List.of(), List.of(FluidSnapshot.of(WATER, 1000)));
        IoSnapshot actual = new IoSnapshot(List.of(), List.of(FluidSnapshot.of(LAVA, 1000)));
        DiffPayload diff = ResultDiffer.diff(expected, actual, EXACT).orElseThrow();
        assertEquals(2, diff.mismatches().size());
    }

    @Test
    @DisplayName("NBT IGNORE mode treats different NBT as a match")
    void nbtIgnore() {
        IoSnapshot expected = new IoSnapshot(List.of(new ItemSnapshot(STICK, 1, Optional.of("{Damage:5}"))), List.of());
        IoSnapshot actual = new IoSnapshot(List.of(new ItemSnapshot(STICK, 1, Optional.of("{Damage:7}"))), List.of());
        ValidationPolicy ignoreNbt = new ValidationPolicy(
                ValidationPolicy.Mode.EXACT,
                ValidationPolicy.ItemTolerance.EXACT,
                ValidationPolicy.FluidTolerance.EXACT,
                ValidationPolicy.NbtCompare.IGNORE,
                1,
                0.05);
        assertTrue(ResultDiffer.diff(expected, actual, ignoreNbt).isEmpty());
    }

    @Test
    @DisplayName("NBT STRUCTURAL/EXACT mode treats different NBT as no match")
    void nbtStrict() {
        IoSnapshot expected = new IoSnapshot(List.of(new ItemSnapshot(STICK, 1, Optional.of("{Damage:5}"))), List.of());
        IoSnapshot actual = new IoSnapshot(List.of(new ItemSnapshot(STICK, 1, Optional.of("{Damage:7}"))), List.of());
        DiffPayload diff = ResultDiffer.diff(expected, actual, EXACT).orElseThrow();
        // missing + extra
        assertEquals(2, diff.mismatches().size());
    }

    @Test
    @DisplayName("DISTRIBUTION mode is rejected with a single descriptive entry")
    void distributionRejected() {
        ValidationPolicy distribution = new ValidationPolicy(
                ValidationPolicy.Mode.DISTRIBUTION,
                ValidationPolicy.ItemTolerance.EXACT,
                ValidationPolicy.FluidTolerance.EXACT,
                ValidationPolicy.NbtCompare.STRUCTURAL,
                1000,
                0.05);
        DiffPayload diff = ResultDiffer.diff(IoSnapshot.empty(), IoSnapshot.empty(), distribution)
                .orElseThrow();
        assertEquals(1, diff.mismatches().size());
        assertEquals("/validation/mode", diff.mismatches().get(0).path());
    }

    @Test
    @DisplayName("Mixed item + fluid mismatches both surface in the same diff")
    void mixedMismatch() {
        IoSnapshot expected =
                new IoSnapshot(List.of(ItemSnapshot.of(STICK, 4)), List.of(FluidSnapshot.of(WATER, 1000)));
        IoSnapshot actual = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 1)), List.of(FluidSnapshot.of(WATER, 100)));
        DiffPayload diff = ResultDiffer.diff(expected, actual, EXACT).orElseThrow();
        assertEquals(2, diff.mismatches().size());
        assertFalse(diff.mismatches().stream().anyMatch(e -> e.path().equals("/items/extra")));
    }

    @Test
    @DisplayName("Duplicate same-id item stacks pair as multisets regardless of order")
    void duplicateItemStacksSwappedOrder() {
        IoSnapshot expected = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 1), ItemSnapshot.of(STICK, 64)), List.of());
        IoSnapshot actual = new IoSnapshot(List.of(ItemSnapshot.of(STICK, 64), ItemSnapshot.of(STICK, 1)), List.of());
        assertTrue(ResultDiffer.diff(expected, actual, EXACT).isEmpty());
    }

    @Test
    @DisplayName("Duplicate same-id fluid stacks pair as multisets regardless of order")
    void duplicateFluidStacksSwappedOrder() {
        IoSnapshot expected =
                new IoSnapshot(List.of(), List.of(FluidSnapshot.of(WATER, 250), FluidSnapshot.of(WATER, 1000)));
        IoSnapshot actual =
                new IoSnapshot(List.of(), List.of(FluidSnapshot.of(WATER, 1000), FluidSnapshot.of(WATER, 250)));
        assertTrue(ResultDiffer.diff(expected, actual, EXACT).isEmpty());
    }
}
