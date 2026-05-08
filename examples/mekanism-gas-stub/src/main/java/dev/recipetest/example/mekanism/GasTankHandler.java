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

import dev.recipetest.api.CustomHandler;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Bridges a {@link GasTank} to the harness's {@link CustomHandler} contract. The harness's
 * {@code dev.recipetest.api.RecipeTestExtension#resolveCustomBinding} returns one of these
 * for each {@code CustomBinding} of {@code kind = "mekanism_gas_stub:gas"} the spec declares.
 *
 * <p>The example only ever uses a single tank per machine, so the handler is a thin wrapper.
 * A real consumer with multiple tanks per machine would dispatch on the {@code ref} field of
 * the binding ({@code "input_gas_tank"}, {@code "byproduct_gas_tank"}, etc.) inside their
 * extension's {@code resolveCustomBinding} and return a different handler instance per tank.
 */
public final class GasTankHandler implements CustomHandler {

    /** Same {@code ResourceLocation} the spec's {@code CustomBinding.kind} field carries.
     *  Centralised so the extension and the spec JSON stay in sync. */
    public static final ResourceLocation GAS_KIND = ResourceLocation.parse("mekanism_gas_stub:gas");

    private final GasTank tank;

    public GasTankHandler(GasTank tank) {
        this.tank = Objects.requireNonNull(tank, "tank");
    }

    @Override
    public InjectResult inject(Object payload) {
        // The extension's injectInputs hook builds the payload from the recipe's expected gas
        // input, so by contract it's always a GasStack here. Defensive instanceof keeps a
        // misbehaving caller from NPE-ing the cast.
        if (!(payload instanceof GasStack stack)) {
            return InjectResult.refused("payload must be a GasStack, got "
                    + (payload == null ? "null" : payload.getClass().getName()));
        }
        // Pre-check capacity before mutating the tank. CustomHandler's contract is that a
        // refused injection leaves storage unmodified — calling tank.fill() first and then
        // returning Refused on a partial accept would silently violate that, leaving the
        // next read with junk. A different gas type triggers a tank replacement at fill time,
        // so the available space for the incoming stack is the full capacity in that case.
        GasStack current = tank.snapshot();
        int maxAcceptable = (!current.isEmpty() && !current.gas().equals(stack.gas()))
                ? tank.capacity()
                : tank.capacity() - current.amount();
        if (stack.amount() > maxAcceptable) {
            return InjectResult.refused(
                    "tank capacity exceeded: requested " + stack.amount() + ", accepted " + maxAcceptable);
        }
        tank.fill(stack);
        return InjectResult.accepted();
    }

    @Override
    public Snapshot read() {
        // The Snapshot's payload is the GasStack itself — record equality on GasStack gives
        // the harness's differ a structural compare for free.
        return new Snapshot(GAS_KIND, tank.snapshot());
    }
}
