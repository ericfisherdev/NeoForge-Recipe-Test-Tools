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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.EnergySpec;
import dev.recipetest.api.Side;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests covering the parts of {@link CapabilityDriver} that don't construct {@code
 * ItemStack} or {@code FluidStack} — those still need a real Minecraft bootstrap, so
 * item/fluid paths are exercised via GameTests in the runner phase. {@link IEnergyStorage} is
 * a pure-int interface so it's fully testable here.
 */
class CapabilityDriverTest {

    @Test
    @DisplayName("toDirection maps cardinal sides to Direction")
    void toDirectionCardinal() {
        assertEquals(Optional.of(Direction.UP), CapabilityDriver.toDirection(Side.TOP));
        assertEquals(Optional.of(Direction.DOWN), CapabilityDriver.toDirection(Side.BOTTOM));
        assertEquals(Optional.of(Direction.NORTH), CapabilityDriver.toDirection(Side.NORTH));
        assertEquals(Optional.of(Direction.SOUTH), CapabilityDriver.toDirection(Side.SOUTH));
        assertEquals(Optional.of(Direction.EAST), CapabilityDriver.toDirection(Side.EAST));
        assertEquals(Optional.of(Direction.WEST), CapabilityDriver.toDirection(Side.WEST));
    }

    @Test
    @DisplayName("toDirection collapses ANY and INTERNAL to empty")
    void toDirectionInternal() {
        assertTrue(CapabilityDriver.toDirection(Side.ANY).isEmpty());
        assertTrue(CapabilityDriver.toDirection(Side.INTERNAL).isEmpty());
    }

    @Test
    @DisplayName("toDirection rejects null side")
    void toDirectionNull() {
        assertThrows(NullPointerException.class, () -> CapabilityDriver.toDirection(null));
    }

    @Test
    @DisplayName("injectEnergy receives the requested preFill")
    void injectEnergyHappyPath() {
        FakeEnergy storage = new FakeEnergy(0, 100_000);
        EnergySpec spec = new EnergySpec("EnergyStorage", Side.ANY, 50_000L, false);
        long accepted = CapabilityDriver.injectEnergy(spec, storage);
        assertEquals(50_000L, accepted);
        assertEquals(50_000, storage.stored);
        assertEquals(1, storage.receiveCalls);
    }

    @Test
    @DisplayName("injectEnergy returns 0 when preFill is 0 and never touches the storage")
    void injectEnergyZeroNoOp() {
        FakeEnergy storage = new FakeEnergy(0, 1);
        EnergySpec spec = new EnergySpec("EnergyStorage", Side.ANY, 0L, false);
        assertEquals(0L, CapabilityDriver.injectEnergy(spec, storage));
        assertEquals(0, storage.receiveCalls);
    }

    @Test
    @DisplayName("injectEnergy clamps preFill above Integer.MAX_VALUE to Integer.MAX_VALUE")
    void injectEnergyClampsLong() {
        FakeEnergy storage = new FakeEnergy(0, Integer.MAX_VALUE);
        EnergySpec spec = new EnergySpec("EnergyStorage", Side.ANY, Long.MAX_VALUE, false);
        long accepted = CapabilityDriver.injectEnergy(spec, storage);
        assertEquals(Integer.MAX_VALUE, accepted);
        assertEquals(Integer.MAX_VALUE, storage.lastReceiveRequest);
    }

    @Test
    @DisplayName("injectEnergy reports actual acceptance below preFill")
    void injectEnergyPartialAcceptance() {
        FakeEnergy storage = new FakeEnergy(0, 1_000);
        EnergySpec spec = new EnergySpec("EnergyStorage", Side.ANY, 5_000L, false);
        long accepted = CapabilityDriver.injectEnergy(spec, storage);
        assertEquals(1_000L, accepted);
        assertEquals(1_000, storage.stored);
    }

    @Test
    @DisplayName("readEnergy returns the storage's current value")
    void readEnergyForwards() {
        FakeEnergy storage = new FakeEnergy(12_345, 100_000);
        assertEquals(12_345L, CapabilityDriver.readEnergy(storage));
    }

    @Test
    @DisplayName("InjectionResult.empty is a singleton with zero leftovers")
    void injectionResultEmpty() {
        assertSame(InjectionResult.empty(), InjectionResult.empty());
        assertTrue(InjectionResult.empty().fullyAccepted());
        assertEquals(0, InjectionResult.empty().fluidLeftover());
    }

    @Test
    @DisplayName("InjectionResult.fullyAccepted false when slots rejected")
    void injectionResultRejectedSlots() {
        InjectionResult r = new InjectionResult(List.of(3), 0, 0L, List.of("slot 3 rejected"));
        assertFalse(r.fullyAccepted());
    }

    @Test
    @DisplayName("InjectionResult.fullyAccepted false when fluid leftover")
    void injectionResultFluidLeftover() {
        InjectionResult r = new InjectionResult(List.of(), 250, 0L, List.of("250mB rejected"));
        assertFalse(r.fullyAccepted());
    }

    @Test
    @DisplayName("InjectionResult rejects negatives")
    void injectionResultNegatives() {
        assertThrows(IllegalArgumentException.class, () -> new InjectionResult(List.of(), -1, 0L, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new InjectionResult(List.of(), 0, -1L, List.of()));
    }

    @Test
    @DisplayName("ReadResult.empty is a singleton with zero energy")
    void readResultEmpty() {
        assertSame(ReadResult.empty(), ReadResult.empty());
        assertEquals(0L, ReadResult.empty().energyStored());
        assertTrue(ReadResult.empty().items().isEmpty());
        assertTrue(ReadResult.empty().fluids().isEmpty());
    }

    @Test
    @DisplayName("ReadResult rejects negative energyStored")
    void readResultNegativeEnergy() {
        assertThrows(IllegalArgumentException.class, () -> new ReadResult(List.of(), List.of(), -1L));
    }

    /** Minimal {@link IEnergyStorage} that tracks call counts so tests can assert behaviour. */
    private static final class FakeEnergy implements IEnergyStorage {
        int stored;
        final int capacity;
        int receiveCalls;
        int lastReceiveRequest;

        FakeEnergy(int stored, int capacity) {
            this.stored = stored;
            this.capacity = capacity;
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            receiveCalls++;
            lastReceiveRequest = toReceive;
            int accepted = Math.min(toReceive, capacity - stored);
            if (!simulate) {
                stored += accepted;
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            int extracted = Math.min(toExtract, stored);
            if (!simulate) {
                stored -= extracted;
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return stored;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
