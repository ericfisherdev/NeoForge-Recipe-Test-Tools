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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;

/**
 * Process-wide registry of validated {@link MachineSpec}s, keyed by recipeType.
 * Populated by {@code SpecLoader} on each datapack reload; queried by the
 * {@code /recipe_test} command surface.
 *
 * <p><b>Concurrency model.</b> The backing map is held in an {@link AtomicReference} and is
 * itself an immutable snapshot. {@link #replaceAll(Map)} swaps the entire map in one atomic
 * publish, so a reader on the server thread always sees either the pre-reload state or the
 * post-reload state — never an intermediate "registry is being rebuilt" view.
 *
 * <p>{@link #register(MachineSpec)} uses an atomic compare-and-set loop so concurrent calls
 * cannot lose updates. Each {@link #register(MachineSpec)} / {@link #clear()} call is its own
 * publish — callers wanting bulk-atomic semantics must use {@link #replaceAll(Map)}.
 *
 * <p>Within any single reader method, the snapshot reference is captured once at the start so
 * derived structures like {@link #byModid()} see a consistent view even if a writer publishes
 * mid-iteration.
 */
public final class HarnessRegistry {

    private static final HarnessRegistry INSTANCE = new HarnessRegistry();

    private final AtomicReference<Map<ResourceLocation, MachineSpec>> specs = new AtomicReference<>(Map.of());

    private HarnessRegistry() {}

    public static HarnessRegistry instance() {
        return INSTANCE;
    }

    /**
     * Atomically replace every entry. Single atomic write, so concurrent readers see either
     * the previous snapshot or the new one — not a half-built mix. Use this from the loader
     * instead of clear-then-many-register.
     */
    public void replaceAll(Map<ResourceLocation, MachineSpec> newSpecs) {
        Objects.requireNonNull(newSpecs, "newSpecs must not be null");
        // byRecipeType / byModid assume the map key matches the spec's recipeType. Validate
        // before publishing so a bad caller fails fast instead of silently corrupting lookups.
        for (Map.Entry<ResourceLocation, MachineSpec> entry : newSpecs.entrySet()) {
            Objects.requireNonNull(
                    entry.getValue(),
                    () -> "null spec for recipeType " + entry.getKey() + " in HarnessRegistry.replaceAll");
            ResourceLocation declared = entry.getValue().recipeType();
            if (!entry.getKey().equals(declared)) {
                throw new IllegalArgumentException("HarnessRegistry.replaceAll: map key " + entry.getKey()
                        + " does not match spec.recipeType() " + declared);
            }
        }
        specs.set(Map.copyOf(newSpecs));
    }

    /**
     * Insert (or replace) a single spec. CAS-loop under the hood so concurrent register() calls
     * cannot lose updates. For bulk reload-time updates use {@link #replaceAll(Map)} so readers
     * don't see partial state.
     */
    public void register(MachineSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        specs.getAndUpdate(current -> {
            Map<ResourceLocation, MachineSpec> next = new HashMap<>(current);
            next.put(spec.recipeType(), spec);
            return Map.copyOf(next);
        });
    }

    /** Look up a spec by recipeType. */
    public Optional<MachineSpec> byRecipeType(ResourceLocation recipeType) {
        return Optional.ofNullable(currentSnapshot().get(recipeType));
    }

    /** Snapshot of all registered specs. Order is not stable; callers wanting ordering should
     *  use {@link #byModid()}. */
    public Collection<MachineSpec> all() {
        return List.copyOf(currentSnapshot().values());
    }

    public int size() {
        return currentSnapshot().size();
    }

    /**
     * Specs grouped by mod id (the namespace of the recipeType {@code ResourceLocation}),
     * mod ids in alphabetical order, specs within each group ordered by recipeType path.
     */
    public SequencedMap<String, List<MachineSpec>> byModid() {
        Map<ResourceLocation, MachineSpec> snapshot = currentSnapshot();
        SequencedMap<String, List<MachineSpec>> grouped = new LinkedHashMap<>();
        Map<String, List<MachineSpec>> tmp = new TreeMap<>();
        for (MachineSpec spec : snapshot.values()) {
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
        specs.set(Map.of());
    }

    /**
     * Returns the current snapshot, asserting non-null. The {@link AtomicReference} is seeded
     * with {@link Map#of()} and every writer publishes a non-null map, so this can never return
     * null at runtime — but {@code AtomicReference.get()} has a {@code @Nullable} signature, so
     * this helper centralizes the assertion to keep NullAway happy at every call site.
     */
    private Map<ResourceLocation, MachineSpec> currentSnapshot() {
        return Objects.requireNonNull(specs.get(), "registry snapshot is null — should be impossible");
    }
}
