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
import java.util.Objects;

/**
 * Aggregate result of a bulk recipe-test execution. Emitted once per run when the
 * {@link dev.recipetest.core.TickScheduler TickScheduler} drains its queue (either to completion
 * or via cancellation).
 *
 * @param runId unique identifier for the bulk run
 * @param recipeTypeLabel either a recipe-type id or the literal {@code "all"}
 * @param countsByStatus tally of every {@link TestResult#status()} produced; statuses with zero
 *     count may be omitted
 * @param wallClockMs wall-clock duration from first runner submission to last result
 * @param totalEngineTicks server ticks the scheduler spent advancing runners (excludes idle)
 * @param peakMsptBudgetUsedMs maximum milliseconds spent inside the scheduler in any one tick;
 *     useful for verifying the configured budget is respected
 * @param cancelled true when {@code /recipe_test cancel} halted the run before all queued
 *     recipes completed; in this case the in-flight runner produces a {@link RunStatus#CANCELLED}
 *     entry and any still-queued recipes are absent from {@link #results()}
 * @param results per-recipe results in completion order
 */
public record BulkResult(
        String runId,
        String recipeTypeLabel,
        Map<RunStatus, Integer> countsByStatus,
        long wallClockMs,
        int totalEngineTicks,
        long peakMsptBudgetUsedMs,
        boolean cancelled,
        List<TestResult> results) {

    public BulkResult {
        Objects.requireNonNull(runId, "BulkResult.runId must not be null");
        Objects.requireNonNull(recipeTypeLabel, "BulkResult.recipeTypeLabel must not be null");
        Objects.requireNonNull(countsByStatus, "BulkResult.countsByStatus must not be null");
        Objects.requireNonNull(results, "BulkResult.results must not be null");
        if (runId.isEmpty()) {
            throw new IllegalArgumentException("BulkResult.runId must not be empty");
        }
        if (recipeTypeLabel.isEmpty()) {
            throw new IllegalArgumentException("BulkResult.recipeTypeLabel must not be empty");
        }
        if (wallClockMs < 0) {
            throw new IllegalArgumentException("BulkResult.wallClockMs must be >= 0, got " + wallClockMs);
        }
        if (totalEngineTicks < 0) {
            throw new IllegalArgumentException("BulkResult.totalEngineTicks must be >= 0, got " + totalEngineTicks);
        }
        if (peakMsptBudgetUsedMs < 0) {
            throw new IllegalArgumentException(
                    "BulkResult.peakMsptBudgetUsedMs must be >= 0, got " + peakMsptBudgetUsedMs);
        }
        int countsSum = 0;
        for (Map.Entry<RunStatus, Integer> entry : countsByStatus.entrySet()) {
            int v = entry.getValue();
            if (v < 0) {
                throw new IllegalArgumentException(
                        "BulkResult.countsByStatus[" + entry.getKey() + "] must be >= 0, got " + v);
            }
            countsSum += v;
        }
        if (countsSum != results.size()) {
            throw new IllegalArgumentException(
                    "BulkResult.countsByStatus sum (" + countsSum + ") must equal results.size()=" + results.size());
        }
        countsByStatus = Map.copyOf(countsByStatus);
        results = List.copyOf(results);
    }

    /** Total result count summed from {@link #countsByStatus()}. */
    public int total() {
        int sum = 0;
        for (Integer count : countsByStatus.values()) {
            sum += count;
        }
        return sum;
    }
}
