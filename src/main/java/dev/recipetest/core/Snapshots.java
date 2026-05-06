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

import dev.recipetest.api.FluidSnapshot;
import dev.recipetest.api.ItemSnapshot;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Conversions between live {@link ItemStack} / {@link FluidStack} instances and the immutable
 * snapshot records used in {@code TestResult}. Pulled out so the runner doesn't restate the
 * components-to-string pattern in three places.
 */
public final class Snapshots {

    private Snapshots() {}

    /** ItemStack → ItemSnapshot. Empty stacks are reported with count zero — callers should
     *  filter empties before calling if they want a "skip empty" semantic. */
    public static ItemSnapshot of(ItemStack stack) {
        Optional<String> nbt = stack.getComponents().isEmpty()
                ? Optional.empty()
                : Optional.of(stack.getComponents().toString());
        return new ItemSnapshot(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), nbt);
    }

    /** FluidStack → FluidSnapshot. */
    public static FluidSnapshot of(FluidStack stack) {
        Optional<String> nbt = stack.getComponents().isEmpty()
                ? Optional.empty()
                : Optional.of(stack.getComponents().toString());
        return new FluidSnapshot(BuiltInRegistries.FLUID.getKey(stack.getFluid()), stack.getAmount(), nbt);
    }
}
