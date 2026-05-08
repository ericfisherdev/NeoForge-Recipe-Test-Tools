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

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Synthetic gas stack — the example's stand-in for a real mod's {@code GasStack} type. Carries
 * a gas identifier and an amount in millibuckets. Pure data; no NBT, no temperature, no
 * other mod-specific complexity. The point of this example isn't to replicate Mekanism's
 * real API — it's to demonstrate that the harness's L2 SPI is shape-agnostic enough to host
 * any custom-storage abstraction a real consumer would build.
 */
public record GasStack(ResourceLocation gas, int amount) {

    public GasStack {
        Objects.requireNonNull(gas, "gas");
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0, got " + amount);
        }
    }

    public static final GasStack EMPTY = new GasStack(ResourceLocation.parse("mekanism_gas_stub:empty"), 0);

    public boolean isEmpty() {
        return amount == 0;
    }
}
