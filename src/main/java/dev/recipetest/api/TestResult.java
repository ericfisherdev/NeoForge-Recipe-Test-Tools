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
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Result of running a single recipe through the kit. Serialised to JSON exactly as documented
 * in {@code json-spec.md#TestResult Output Schema}.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>{@link #diff} is present only when {@link #status} is {@link RunStatus#FAIL};
 *   <li>{@link #diff} is absent on PASS, TIMEOUT, ERROR, and SKIPPED runs;
 *   <li>{@link #ticksElapsed} is always {@code >= 0}.
 * </ul>
 *
 * @param recipeId fully-qualified recipe id (e.g. {@code "forestry:carpenter/circuit_board_basic"})
 * @param recipeType recipe type (e.g. {@code "forestry:carpenter"})
 * @param specSource source identifier of the spec file (e.g. {@code "forestry:carpenter.json"})
 * @param status final outcome
 * @param ticksElapsed total ticks the runner consumed (warmup + active recipe)
 * @param expected expected output snapshot
 * @param actual actual output snapshot read from the machine
 * @param diff optional diff payload — present iff status is FAIL
 * @param diagnostics side-channel observations (fluids consumed, energy delta, warnings, logs)
 */
public record TestResult(
        ResourceLocation recipeId,
        ResourceLocation recipeType,
        String specSource,
        RunStatus status,
        int ticksElapsed,
        IoSnapshot expected,
        IoSnapshot actual,
        Optional<DiffPayload> diff,
        Diagnostics diagnostics) {

    public TestResult {
        Objects.requireNonNull(recipeId, "TestResult.recipeId must not be null");
        Objects.requireNonNull(recipeType, "TestResult.recipeType must not be null");
        Objects.requireNonNull(specSource, "TestResult.specSource must not be null");
        Objects.requireNonNull(status, "TestResult.status must not be null");
        Objects.requireNonNull(expected, "TestResult.expected must not be null");
        Objects.requireNonNull(actual, "TestResult.actual must not be null");
        Objects.requireNonNull(diff, "TestResult.diff Optional must not be null");
        Objects.requireNonNull(diagnostics, "TestResult.diagnostics must not be null");
        if (ticksElapsed < 0) {
            throw new IllegalArgumentException("TestResult.ticksElapsed must be >= 0, got " + ticksElapsed);
        }
        if (specSource.isEmpty()) {
            throw new IllegalArgumentException("TestResult.specSource must not be empty");
        }
        if (status == RunStatus.FAIL && diff.isEmpty()) {
            throw new IllegalArgumentException("TestResult with status FAIL must carry a diff payload");
        }
        if (status != RunStatus.FAIL && diff.isPresent()) {
            throw new IllegalArgumentException("TestResult with status " + status + " must not carry a diff payload");
        }
    }
}
