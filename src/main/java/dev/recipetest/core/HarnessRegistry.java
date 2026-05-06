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

import dev.recipetest.api.MachineSpec;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Process-wide registry of validated {@link MachineSpec}s, keyed by recipeType.
 * Populated by {@code SpecLoader} on each datapack reload; queried by the
 * {@code /recipe_test} command surface.
 *
 * <p><b>Concurrency model.</b> The backing map is held in a {@code volatile} field and is itself
 * an immutable snapshot. Reload passes call {@link #replaceAll(Map)} to swap the entire map in
 * one atomic publish, so a reader on the server thread always sees either the pre-reload state
 * or the post-reload state — never an intermediate "registry is being rebuilt" view.
 *
 * <p>{@link #register(MachineSpec)} and {@link #clear()} remain available for direct programmatic
 * use (test setup, future single-spec hot-reload). They publish via the same volatile field but
 * each call is a separate publish — callers that want bulk-atomic semantics must use
 * {@link #replaceAll(Map)}.
 */
public final class HarnessRegistry {

    private static final HarnessRegistry INSTANCE = new HarnessRegistry();

    private volatile Map<ResourceLocation, MachineSpec> specs = Map.of();

    private HarnessRegistry() {}

    public static HarnessRegistry instance() {
        return INSTANCE;
    }

    /**
     * Atomically replace every entry. Single volatile write, so concurrent readers see either
     * the previous snapshot or the new one — not a half-built mix. Use this from the loader
     * instead of clear-then-many-register.
     */
    public void replaceAll(Map<ResourceLocation, MachineSpec> newSpecs) {
        Objects.requireNonNull(newSpecs, "newSpecs must not be null");
        Map<ResourceLocation, MachineSpec> snapshot = Map.copyOf(newSpecs);
        this.specs = snapshot;
    }

    /**
     * Insert (or replace) a single spec. Each call is its own volatile publish; for bulk
     * reload-time updates use {@link #replaceAll(Map)} so readers don't see partial state.
     */
    public void register(MachineSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        Map<ResourceLocation, MachineSpec> current = specs;
        Map<ResourceLocation, MachineSpec> next = new java.util.HashMap<>(current);
        next.put(spec.recipeType(), spec);
        specs = Map.copyOf(next);
    }

    /** Look up a spec by recipeType. */
    public Optional<MachineSpec> byRecipeType(ResourceLocation recipeType) {
        return Optional.ofNullable(specs.get(recipeType));
    }

    /** Snapshot of all registered specs. Order is not stable; callers wanting ordering should
     *  use {@link #byModid()}. */
    public Collection<MachineSpec> all() {
        return List.copyOf(specs.values());
    }

    public int size() {
        return specs.size();
    }

    /**
     * Specs grouped by mod id (the namespace of the recipeType {@code ResourceLocation}),
     * mod ids in alphabetical order, specs within each group ordered by recipeType path.
     */
    public SequencedMap<String, List<MachineSpec>> byModid() {
        SequencedMap<String, List<MachineSpec>> grouped = new LinkedHashMap<>();
        Map<String, List<MachineSpec>> tmp = new TreeMap<>();
        for (MachineSpec spec : specs.values()) {
            tmp.computeIfAbsent(spec.recipeType().getNamespace(), k -> new java.util.ArrayList<>())
                    .add(spec);
        }
        for (Map.Entry<String, List<MachineSpec>> e : tmp.entrySet()) {
            List<MachineSpec> sorted = e.getValue().stream()
                    .sorted((a, b) ->
                            a.recipeType().getPath().compareTo(b.recipeType().getPath()))
                    .toList();
            grouped.put(e.getKey(), sorted);
        }
        return grouped;
    }

    /** Drop all entries. For atomic reload-time replacement use {@link #replaceAll(Map)}. */
    public void clear() {
        specs = Map.of();
    }
}
