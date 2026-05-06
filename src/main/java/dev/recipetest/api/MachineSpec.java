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
package dev.recipetest.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Top-level immutable spec describing how the harness can drive a single machine's recipes.
 * Loaded from datapack files at {@code data/<modid>/recipe_test/machines/<name>.json}.
 *
 * @param version schema version (currently must be {@code 1})
 * @param recipeType the {@code RecipeType} this spec is for (e.g. {@code "forestry:carpenter"})
 * @param block the machine block to place during testing
 * @param blockState optional block-state property overrides applied at placement time
 * @param neighbors optional adjacent blocks to place around the machine origin
 * @param inputs how recipe inputs map onto machine storage
 * @param outputs how recipe outputs map onto machine storage
 * @param energy optional energy configuration; absent means autonomous / no energy needed
 * @param tickBudget how long the runner waits for the recipe to complete
 * @param validation comparison policy applied to actual vs. expected output
 * @param lifecycle optional pre/post-tick command hooks
 */
public record MachineSpec(
        int version,
        ResourceLocation recipeType,
        ResourceLocation block,
        Optional<Map<String, String>> blockState,
        List<NeighborSpec> neighbors,
        InputBinding inputs,
        OutputBinding outputs,
        Optional<EnergySpec> energy,
        TickBudget tickBudget,
        ValidationPolicy validation,
        Optional<LifecycleHooks> lifecycle) {

    public MachineSpec {
        blockState = blockState.map(Map::copyOf);
        neighbors = List.copyOf(neighbors);
    }
}
