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
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Process-wide registry of validated {@link MachineSpec}s, keyed by recipeType.
 * Populated by {@code SpecLoader} on each datapack reload; queried by the
 * {@code /recipe_test} command surface.
 *
 * <p>Concurrent-safe: the loader runs on the reload thread, commands run on the server thread,
 * and {@link #all()} / {@link #byModid()} take a snapshot at call time.
 */
public final class HarnessRegistry {

    private static final HarnessRegistry INSTANCE = new HarnessRegistry();

    private final Map<ResourceLocation, MachineSpec> specs = new ConcurrentHashMap<>();

    private HarnessRegistry() {}

    public static HarnessRegistry instance() {
        return INSTANCE;
    }

    /** Insert (or replace) a spec keyed by its {@code recipeType}. */
    public void register(MachineSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        specs.put(spec.recipeType(), spec);
    }

    /** Look up a spec by recipeType. */
    public Optional<MachineSpec> byRecipeType(ResourceLocation recipeType) {
        return Optional.ofNullable(specs.get(recipeType));
    }

    /** Snapshot of all registered specs in insertion order is not preserved; callers that need
     *  ordering should use {@link #byModid()} which sorts deterministically. */
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

    /** Drop all entries — invoked by the loader at the start of every reload pass. */
    public void clear() {
        specs.clear();
    }
}
