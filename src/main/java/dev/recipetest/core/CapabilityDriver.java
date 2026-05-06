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

import dev.recipetest.api.EnergySpec;
import dev.recipetest.api.FluidBinding;
import dev.recipetest.api.FluidSnapshot;
import dev.recipetest.api.ItemBinding;
import dev.recipetest.api.ItemSnapshot;
import dev.recipetest.api.Layout;
import dev.recipetest.api.Side;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Drives a machine's {@link IItemHandler} / {@link IFluidHandler} / {@link IEnergyStorage}
 * capabilities according to a {@link dev.recipetest.api.MachineSpec}. Capability resolution from
 * a {@code BlockEntity} happens in the runner; this class operates against already-resolved
 * handler instances so each method is independently testable.
 *
 * <p>Layout dispatch lives in {@link SlotPlan}; this class only translates plan assignments into
 * {@code insertItem} / {@code fill} / {@code receiveEnergy} calls.
 */
public final class CapabilityDriver {

    private CapabilityDriver() {}

    // ---- side helpers ----

    /**
     * Translate a {@link Side} into a {@link Direction} for capability lookup.
     *
     * @return concrete direction, or {@link Optional#empty()} for {@link Side#INTERNAL} and
     *     {@link Side#ANY} (both query the internal / null-side capability)
     */
    public static Optional<Direction> toDirection(Side side) {
        Objects.requireNonNull(side, "side must not be null");
        return switch (side) {
            case TOP -> Optional.of(Direction.UP);
            case BOTTOM -> Optional.of(Direction.DOWN);
            case NORTH -> Optional.of(Direction.NORTH);
            case SOUTH -> Optional.of(Direction.SOUTH);
            case EAST -> Optional.of(Direction.EAST);
            case WEST -> Optional.of(Direction.WEST);
            case ANY, INTERNAL -> Optional.empty();
        };
    }

    // ---- item injection ----

    /**
     * Insert recipe ingredients into an {@link IItemHandler} according to the binding's layout.
     *
     * @param binding item input binding
     * @param handler resolved item handler (already side-aware)
     * @param stacks ingredient stacks in recipe declaration order
     * @param positions for {@link Layout#SHAPED3X3} only — one {@code [x, y]} pair per stack;
     *     pass {@link List#of()} for shapeless / ordered layouts
     * @return injection result reporting any rejected slots and warnings
     */
    public static InjectionResult injectItems(
            ItemBinding binding, IItemHandler handler, List<ItemStack> stacks, List<int[]> positions) {
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        Objects.requireNonNull(stacks, "stacks must not be null");
        Objects.requireNonNull(positions, "positions must not be null (use List.of() for non-shaped layouts)");

        Layout layout = binding.layout().orElse(Layout.SHAPELESS);
        SlotPlan.Plan plan = SlotPlan.dispatch(layout, stacks.size(), binding.slots(), positions);

        List<Integer> rejected = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (SlotPlan.Assignment assignment : plan.assignments()) {
            ItemStack stack = stacks.get(assignment.inputIndex());
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remaining = handler.insertItem(assignment.slot(), stack, false);
            if (!remaining.isEmpty()) {
                rejected.add(assignment.slot());
                warnings.add("slot " + assignment.slot() + " rejected " + remaining.getCount() + " of "
                        + stack.getCount() + " (kind=" + BuiltInRegistries.ITEM.getKey(remaining.getItem()) + ")");
            }
        }
        return new InjectionResult(rejected, 0, 0L, warnings);
    }

    // ---- fluid injection ----

    /**
     * Fill recipe fluids into an {@link IFluidHandler}. The Forge fluid API has no per-tank fill
     * primitive — the handler chooses where to put each stack — so the binding's {@code tanks}
     * list is informational for the read side only here.
     *
     * @return total millibuckets the handler refused (zero on full acceptance)
     */
    public static int injectFluids(FluidBinding binding, IFluidHandler handler, List<FluidStack> fluids) {
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        Objects.requireNonNull(fluids, "fluids must not be null");

        int leftover = 0;
        for (FluidStack stack : fluids) {
            if (stack.isEmpty()) {
                continue;
            }
            int filled = handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
            leftover += stack.getAmount() - filled;
        }
        return leftover;
    }

    // ---- energy injection ----

    /**
     * Pre-fill an energy storage to the spec's {@code preFill} target. Single bulk call as
     * required by the Phase-2 plan ("insert as one bulk operation, ignoring simulate modes").
     *
     * @return FE actually accepted by the storage (may be less than {@code spec.preFill()} for
     *     storages whose capacity is below the request)
     */
    public static long injectEnergy(EnergySpec spec, IEnergyStorage storage) {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(storage, "storage must not be null");
        if (spec.preFill() == 0L) {
            return 0L;
        }
        int request = (int) Math.min(spec.preFill(), Integer.MAX_VALUE);
        return storage.receiveEnergy(request, false);
    }

    // ---- output reading ----

    /** Read the declared item slots and produce snapshot records, skipping empty slots. */
    public static List<ItemSnapshot> readItems(ItemBinding binding, IItemHandler handler) {
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(handler, "handler must not be null");

        List<ItemSnapshot> result = new ArrayList<>();
        for (int slot : binding.slots()) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Optional<String> nbt = stack.getComponents().isEmpty()
                    ? Optional.empty()
                    : Optional.of(stack.getComponents().toString());
            result.add(new ItemSnapshot(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), nbt));
        }
        return result;
    }

    /** Read the declared fluid tanks and produce snapshot records, skipping empty tanks. */
    public static List<FluidSnapshot> readFluids(FluidBinding binding, IFluidHandler handler) {
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(handler, "handler must not be null");

        List<FluidSnapshot> result = new ArrayList<>();
        for (int tank : binding.tanks()) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (stack.isEmpty()) {
                continue;
            }
            result.add(new FluidSnapshot(
                    BuiltInRegistries.FLUID.getKey(stack.getFluid()), stack.getAmount(), Optional.empty()));
        }
        return result;
    }

    /** Read the current energy stored — used to compute {@code energyConsumed}. */
    public static long readEnergy(IEnergyStorage storage) {
        Objects.requireNonNull(storage, "storage must not be null");
        return storage.getEnergyStored();
    }
}
