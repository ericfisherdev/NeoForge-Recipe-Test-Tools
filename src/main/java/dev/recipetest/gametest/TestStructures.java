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
package dev.recipetest.gametest;

import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.NeighborSpec;
import dev.recipetest.api.TestContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * Places the machine block (plus declared neighbours and blockState property overrides) at the
 * origin in {@link TestContext#level()} and clears the area on tear-down. Stateless: the runner
 * passes its {@link TestContext} on every call.
 */
public final class TestStructures {

    private TestStructures() {}

    /** Set of block positions the most recent {@link #placeMachine} call modified — recorded for
     *  tear-down. Returned to the runner so the runner can hand it back to {@link #tearDown}. */
    public record Placement(BlockPos machine, List<BlockPos> neighbours) {
        public Placement {
            Objects.requireNonNull(machine, "machine pos must not be null");
            Objects.requireNonNull(neighbours, "neighbours must not be null");
            neighbours = List.copyOf(neighbours);
        }
    }

    /**
     * Place {@code spec.block()} at {@code ctx.origin()}, applying the spec's blockState property
     * overrides, and lay down each declared neighbour at its relative offset. The runner is
     * responsible for ensuring the chunk is force-loaded before calling.
     *
     * @throws IllegalArgumentException if the block id, or any neighbour block id, doesn't resolve
     *     in {@code BuiltInRegistries.BLOCK} (this is a hard error — validation should have caught
     *     it at spec-load time, but a defensive check keeps the failure mode obvious)
     */
    public static Placement placeMachine(MachineSpec spec, TestContext ctx) {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");

        ServerLevel level = ctx.level();
        BlockPos origin = ctx.origin();

        BlockState machineState = applyOverrides(resolveBlock(spec.block()).defaultBlockState(), spec.blockState());
        level.setBlockAndUpdate(origin, machineState);

        List<BlockPos> neighbourPositions = new ArrayList<>(spec.neighbors().size());
        for (NeighborSpec neighbour : spec.neighbors()) {
            BlockPos pos = origin.offset(neighbour.offset());
            level.setBlockAndUpdate(pos, resolveBlock(neighbour.block()).defaultBlockState());
            neighbourPositions.add(pos);
        }

        return new Placement(origin, neighbourPositions);
    }

    /**
     * Restore every position that {@link #placeMachine} touched to {@code minecraft:air}. Safe to
     * call from a {@code finally} block — tolerates a {@code null} placement (no-op) so callers
     * can declare placement before the {@code try} and clean it up regardless of where they
     * threw.
     */
    public static void tearDown(ServerLevel level, @Nullable Placement placement) {
        Objects.requireNonNull(level, "level must not be null");
        if (placement == null) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        level.setBlockAndUpdate(placement.machine(), air);
        for (BlockPos pos : placement.neighbours()) {
            level.setBlockAndUpdate(pos, air);
        }
        level.invalidateCapabilities(placement.machine());
        for (BlockPos pos : placement.neighbours()) {
            level.invalidateCapabilities(pos);
        }
    }

    // ---- helpers ----

    private static Block resolveBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("block '" + id + "' not registered (validator should have caught this)");
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    /**
     * Apply each {@code (propertyName -> stringValue)} override to {@code base}. Properties not
     * known on the block, or values the property can't parse, fall through unchanged with no
     * exception — the validator already warns on shape; the runtime placer should be tolerant
     * because mod block-state schemas can change between server starts.
     */
    private static BlockState applyOverrides(BlockState base, java.util.Optional<Map<String, String>> overrides) {
        if (overrides.isEmpty()) {
            return base;
        }
        BlockState state = base;
        for (Map.Entry<String, String> entry : overrides.get().entrySet()) {
            Property<?> prop = base.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (prop == null) {
                continue;
            }
            state = setProperty(state, prop, entry.getValue());
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setProperty(
            BlockState state, Property<T> prop, String rawValue) {
        return prop.getValue(rawValue).map(v -> state.setValue(prop, (T) v)).orElse(state);
    }
}
