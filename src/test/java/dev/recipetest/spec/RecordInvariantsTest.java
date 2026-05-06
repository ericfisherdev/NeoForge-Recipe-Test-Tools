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

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.recipetest.api.CustomBinding;
import dev.recipetest.api.FluidBinding;
import dev.recipetest.api.InputBinding;
import dev.recipetest.api.ItemBinding;
import dev.recipetest.api.NeighborSpec;
import dev.recipetest.api.OutputBinding;
import dev.recipetest.api.Side;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Negative tests for record-level invariants — direct programmatic construction (not via codec).
 * Codec-driven invariants are exercised in {@link CodecTest}.
 */
class RecordInvariantsTest {

    private static final ResourceLocation EXAMPLE = ResourceLocation.parse("examplemod:thing");

    @Test
    @DisplayName("CustomBinding rejects null kind")
    void customBindingNullKind() {
        assertThrows(NullPointerException.class, () -> new CustomBinding(null, "ref"));
    }

    @Test
    @DisplayName("CustomBinding rejects null ref")
    void customBindingNullRef() {
        assertThrows(NullPointerException.class, () -> new CustomBinding(EXAMPLE, null));
    }

    @Test
    @DisplayName("CustomBinding rejects blank ref")
    void customBindingBlankRef() {
        assertThrows(IllegalArgumentException.class, () -> new CustomBinding(EXAMPLE, "   "));
    }

    @Test
    @DisplayName("FluidBinding rejects negative tank index")
    void fluidBindingNegativeIndex() {
        assertThrows(
                IllegalArgumentException.class, () -> new FluidBinding("FluidHandler", Side.INTERNAL, List.of(0, -1)));
    }

    @Test
    @DisplayName("FluidBinding rejects empty tanks (existing invariant, retested)")
    void fluidBindingEmptyTanks() {
        assertThrows(IllegalArgumentException.class, () -> new FluidBinding("FluidHandler", Side.INTERNAL, List.of()));
    }

    @Test
    @DisplayName("ItemBinding rejects empty slots")
    void itemBindingEmptySlots() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ItemBinding("ItemHandler", Side.INTERNAL, List.of(), Optional.empty(), Optional.empty()));
    }

    @Test
    @DisplayName("ItemBinding rejects negative slot index")
    void itemBindingNegativeSlot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ItemBinding(
                        "ItemHandler", Side.INTERNAL, List.of(0, -3), Optional.empty(), Optional.empty()));
    }

    @Test
    @DisplayName("InputBinding rejects null Optional fields")
    void inputBindingNullOptionals() {
        assertThrows(NullPointerException.class, () -> new InputBinding(null, Optional.empty(), List.of()));
        assertThrows(NullPointerException.class, () -> new InputBinding(Optional.empty(), null, List.of()));
    }

    @Test
    @DisplayName("OutputBinding rejects null Optional fields")
    void outputBindingNullOptionals() {
        assertThrows(NullPointerException.class, () -> new OutputBinding(null, Optional.empty(), List.of()));
        assertThrows(NullPointerException.class, () -> new OutputBinding(Optional.empty(), null, List.of()));
    }

    @Test
    @DisplayName("NeighborSpec rejects null components")
    void neighborSpecNullComponents() {
        assertThrows(NullPointerException.class, () -> new NeighborSpec(null, EXAMPLE));
        assertThrows(NullPointerException.class, () -> new NeighborSpec(BlockPos.ZERO, null));
    }
}
