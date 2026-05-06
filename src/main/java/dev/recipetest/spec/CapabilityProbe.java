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

import com.mojang.logging.LogUtils;
import dev.recipetest.api.EnergySpec;
import dev.recipetest.api.FluidBinding;
import dev.recipetest.api.ItemBinding;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.Side;
import dev.recipetest.core.CapabilityDriver;
import dev.recipetest.core.HarnessRegistry;
import dev.recipetest.gametest.TestStructures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * One-shot check, run on {@code ServerStartedEvent}, that places each registered spec's machine
 * block in an isolated probe location and asks the level for each capability the spec declares.
 * Missing capabilities are recorded so {@code /recipe_test list} can surface them and so the
 * server log carries a warning at boot — the spec's own validation only catches schema problems,
 * not "this block doesn't actually expose the capability you're trying to drive".
 */
public final class CapabilityProbe {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Probe location — sits well below the build limit and well below any reasonable structure. */
    private static final BlockPos PROBE_ORIGIN = new BlockPos(0, -60, 0);

    private static final Map<ResourceLocation, List<String>> ISSUES = Collections.synchronizedMap(new HashMap<>());

    private CapabilityProbe() {}

    /** Most-recent probe results keyed by recipeType — empty list means clean. */
    public static List<String> issuesFor(ResourceLocation recipeType) {
        synchronized (ISSUES) {
            return List.copyOf(ISSUES.getOrDefault(recipeType, List.of()));
        }
    }

    /** Drop every recorded issue. Public so tests can reset; called automatically when the
     *  server starts a fresh probe pass. */
    public static void clear() {
        synchronized (ISSUES) {
            ISSUES.clear();
        }
    }

    /**
     * Run the probe against every spec in {@link HarnessRegistry}. Best-effort — any per-spec
     * exception is caught and logged; the probe never aborts the server start.
     */
    public static void probeAll(ServerLevel level) {
        clear();
        var specs = HarnessRegistry.instance().all();
        if (specs.isEmpty()) {
            return;
        }
        // Force-load the probe chunk so getCapability has something to look at.
        level.getChunk(PROBE_ORIGIN);
        try {
            for (MachineSpec spec : specs) {
                try {
                    List<String> issues = probeOne(level, spec);
                    if (!issues.isEmpty()) {
                        ISSUES.put(spec.recipeType(), List.copyOf(issues));
                        for (String issue : issues) {
                            LOGGER.warn("recipe_test capability probe: {}: {}", spec.recipeType(), issue);
                        }
                    }
                } catch (RuntimeException ex) {
                    LOGGER.warn(
                            "recipe_test capability probe: {} threw {}; spec skipped",
                            spec.recipeType(),
                            ex.toString());
                }
            }
        } finally {
            // The probe places the block at PROBE_ORIGIN; clear it whether or not we threw.
            level.setBlockAndUpdate(PROBE_ORIGIN, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.invalidateCapabilities(PROBE_ORIGIN);
        }
    }

    private static List<String> probeOne(ServerLevel level, MachineSpec spec) {
        List<String> issues = new ArrayList<>();
        // Place just the machine block at PROBE_ORIGIN — neighbours are skipped because the
        // probe is testing capability *presence*, not recipe behaviour.
        if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(spec.block())) {
            issues.add("/block: " + spec.block() + " not registered");
            return issues;
        }
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(spec.block());
        level.setBlockAndUpdate(PROBE_ORIGIN, block.defaultBlockState());
        level.invalidateCapabilities(PROBE_ORIGIN);
        try {
            spec.inputs().items().ifPresent(binding -> checkItem(level, binding, "/inputs/items/capability", issues));
            spec.outputs().items().ifPresent(binding -> checkItem(level, binding, "/outputs/items/capability", issues));
            spec.inputs()
                    .fluids()
                    .ifPresent(binding -> checkFluid(level, binding, "/inputs/fluids/capability", issues));
            spec.outputs()
                    .fluids()
                    .ifPresent(binding -> checkFluid(level, binding, "/outputs/fluids/capability", issues));
            spec.energy().ifPresent(energy -> checkEnergy(level, energy, "/energy/capability", issues));
        } finally {
            // Per-spec cleanup so the next spec starts from AIR.
            TestStructures.tearDown(level, new TestStructures.Placement(PROBE_ORIGIN, List.of()));
        }
        return issues;
    }

    private static void checkItem(ServerLevel level, ItemBinding binding, String path, List<String> issues) {
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, PROBE_ORIGIN, directionFor(binding.side()));
        if (handler == null) {
            issues.add(path + ": ItemHandler not exposed on side " + binding.side());
        }
    }

    private static void checkFluid(ServerLevel level, FluidBinding binding, String path, List<String> issues) {
        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, PROBE_ORIGIN, directionFor(binding.side()));
        if (handler == null) {
            issues.add(path + ": FluidHandler not exposed on side " + binding.side());
        }
    }

    private static void checkEnergy(ServerLevel level, EnergySpec energy, String path, List<String> issues) {
        var storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, PROBE_ORIGIN, directionFor(energy.side()));
        if (storage == null) {
            issues.add(path + ": EnergyStorage not exposed on side " + energy.side());
        }
    }

    private static @Nullable Direction directionFor(Side side) {
        Optional<Direction> dir = CapabilityDriver.toDirection(side);
        return dir.orElse(null);
    }
}
