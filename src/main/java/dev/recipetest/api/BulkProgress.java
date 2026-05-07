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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Mid-run snapshot of a bulk recipe-test execution. Streamed as NDJSON for non-player command
 * sources and rendered as a single chat line for players.
 *
 * @param runId unique identifier for the bulk run; matches the id returned by
 *     {@code /recipe_test bulk}
 * @param recipeTypeLabel either a recipe-type id (e.g. {@code "forestry:carpenter"}) or the
 *     literal {@code "all"} for the every-spec mode
 * @param total total number of recipes scheduled
 * @param completed number of recipes that have produced a {@link TestResult}
 * @param countsByStatus map of {@link RunStatus} → count among completed runs (statuses with
 *     zero count may be omitted)
 * @param inFlightRecipeId id of the recipe currently advancing on the runner, when one is in
 *     flight
 * @param etaTicks rough estimate of remaining server ticks until completion (zero when unknown)
 */
public record BulkProgress(
        String runId,
        String recipeTypeLabel,
        int total,
        int completed,
        Map<RunStatus, Integer> countsByStatus,
        Optional<ResourceLocation> inFlightRecipeId,
        int etaTicks) {

    public BulkProgress {
        Objects.requireNonNull(runId, "BulkProgress.runId must not be null");
        Objects.requireNonNull(recipeTypeLabel, "BulkProgress.recipeTypeLabel must not be null");
        Objects.requireNonNull(countsByStatus, "BulkProgress.countsByStatus must not be null");
        Objects.requireNonNull(inFlightRecipeId, "BulkProgress.inFlightRecipeId Optional must not be null");
        if (runId.isEmpty()) {
            throw new IllegalArgumentException("BulkProgress.runId must not be empty");
        }
        if (recipeTypeLabel.isEmpty()) {
            throw new IllegalArgumentException("BulkProgress.recipeTypeLabel must not be empty");
        }
        if (total < 0) {
            throw new IllegalArgumentException("BulkProgress.total must be >= 0, got " + total);
        }
        if (completed < 0 || completed > total) {
            throw new IllegalArgumentException(
                    "BulkProgress.completed (" + completed + ") must be in [0, total=" + total + "]");
        }
        if (etaTicks < 0) {
            throw new IllegalArgumentException("BulkProgress.etaTicks must be >= 0, got " + etaTicks);
        }
        int countsSum = 0;
        for (Map.Entry<RunStatus, Integer> entry : countsByStatus.entrySet()) {
            int v = entry.getValue();
            if (v < 0) {
                throw new IllegalArgumentException(
                        "BulkProgress.countsByStatus[" + entry.getKey() + "] must be >= 0, got " + v);
            }
            countsSum += v;
        }
        if (countsSum > completed) {
            throw new IllegalArgumentException(
                    "BulkProgress.countsByStatus sum (" + countsSum + ") must not exceed completed=" + completed);
        }
        countsByStatus = Map.copyOf(countsByStatus);
    }

    /** Convenience accessor: total - completed. */
    public int remaining() {
        return total - completed;
    }
}
