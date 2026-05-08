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
package dev.recipetest.example.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.CustomHandler;
import dev.recipetest.api.RecipeTestExtension;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Exercises the {@link MekanismGasStubExtension} from the position the harness's
 * {@code ExtensionRegistry} would. Demonstrates the SPI contract holds for an external
 * consumer that imports nothing beyond {@code dev.recipetest.api.*}.
 */
class MekanismGasStubExtensionTest {

    @Test
    void recipeTypeIsTheExpectedNamespace() {
        MekanismGasStubExtension ext = new MekanismGasStubExtension();
        assertEquals(ResourceLocation.parse("mekanism_gas_stub:gas_compressor"), ext.recipeType());
    }

    @Test
    void supportedKindsAdvertisesGasBinding() {
        MekanismGasStubExtension ext = new MekanismGasStubExtension();
        assertTrue(ext.supportedKinds().contains(GasTankHandler.GAS_KIND));
    }

    @Test
    void resolveCustomBindingReturnsHandlerForGasKind() {
        MekanismGasStubExtension ext = new MekanismGasStubExtension();
        Optional<CustomHandler> handler =
                ext.resolveCustomBinding(GasTankHandler.GAS_KIND, "input_gas_tank", null);
        assertTrue(handler.isPresent(), "extension must produce a handler for its declared kind");
        assertInstanceOf(GasTankHandler.class, handler.get());
    }

    @Test
    void resolveCustomBindingFallsThroughForOtherKinds() {
        MekanismGasStubExtension ext = new MekanismGasStubExtension();
        Optional<CustomHandler> handler = ext.resolveCustomBinding(
                ResourceLocation.parse("forestry:products"), "any_ref", null);
        assertTrue(handler.isEmpty(), "extension only handles its own kind");
    }

    @Test
    void resolveCustomBindingFallsThroughForUnknownRef() {
        MekanismGasStubExtension ext = new MekanismGasStubExtension();
        Optional<CustomHandler> handler =
                ext.resolveCustomBinding(GasTankHandler.GAS_KIND, "byproduct_gas_tank", null);
        assertTrue(handler.isEmpty(), "stub only knows the input tank ref");
    }

    @Test
    void handlerInjectAcceptsMatchingGasUpToCapacity() {
        GasTank tank = new GasTank(10_000);
        GasTankHandler handler = new GasTankHandler(tank);
        GasStack input = new GasStack(ResourceLocation.parse("mekanism_gas_stub:hydrogen"), 1_000);

        CustomHandler.InjectResult result = handler.inject(input);
        assertSame(CustomHandler.InjectResult.accepted(), result);

        CustomHandler.Snapshot snapshot = handler.read();
        assertEquals(GasTankHandler.GAS_KIND, snapshot.kind());
        assertEquals(input, snapshot.payload());
    }

    @Test
    void handlerRefusesPayloadOfWrongType() {
        GasTankHandler handler = new GasTankHandler(new GasTank(10_000));
        CustomHandler.InjectResult result = handler.inject("not-a-gas-stack");
        var refused = assertInstanceOf(CustomHandler.InjectResult.Refused.class, result);
        assertTrue(refused.reason().contains("GasStack"));
    }

    @Test
    void handlerRefusesOverCapacityFill() {
        GasTank tank = new GasTank(500);
        GasTankHandler handler = new GasTankHandler(tank);
        GasStack tooMuch = new GasStack(ResourceLocation.parse("mekanism_gas_stub:hydrogen"), 1_000);

        CustomHandler.InjectResult result = handler.inject(tooMuch);
        var refused = assertInstanceOf(CustomHandler.InjectResult.Refused.class, result);
        assertTrue(refused.reason().contains("capacity"));
    }

    @Test
    void extensionImplementsRecipeTestExtension() {
        // Compile-time assertion that the extension actually satisfies the SPI shape — proves
        // the harness's public types are stable and consumable from a separate Gradle module.
        RecipeTestExtension<?> ext = new MekanismGasStubExtension();
        assertEquals("mekanism_gas_stub", ext.recipeType().getNamespace());
    }
}
