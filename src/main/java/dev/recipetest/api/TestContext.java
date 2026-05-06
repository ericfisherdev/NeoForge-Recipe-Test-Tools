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

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Runtime context handed to the recipe-test runner. Captures the world the test runs in, where in
 * the world its machine sits, and the registries used to resolve recipe outputs.
 *
 * <p>{@code origin} is the {@link BlockPos} of the machine block. Neighbour blocks declared in the
 * spec are placed at relative offsets from this origin.
 *
 * @param server the server hosting the test (used to dispatch lifecycle commands and tick timing)
 * @param level the world the test runs in (single-level harness for Phase 2 — overworld)
 * @param origin where the machine is placed
 * @param registries registry access for output-stack resolution (e.g. {@code Recipe.getResultItem})
 */
public record TestContext(MinecraftServer server, ServerLevel level, BlockPos origin, RegistryAccess registries) {

    public TestContext {
        Objects.requireNonNull(server, "TestContext.server must not be null");
        Objects.requireNonNull(level, "TestContext.level must not be null");
        Objects.requireNonNull(origin, "TestContext.origin must not be null");
        Objects.requireNonNull(registries, "TestContext.registries must not be null");
    }
}
