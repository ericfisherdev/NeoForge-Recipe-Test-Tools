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

import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.TestResult;
import java.util.Objects;

/**
 * Pure function from a {@link TestResult} to a distribution-mode channel identifier. The
 * sample-loop driver uses it to bucket each completed run into the right channel before handing
 * the histogram to {@link DistributionValidator}.
 *
 * <p>Strategy is deliberately simple — the kit's L1 path covers single-output recipes well,
 * and the L2 SPI's {@code weights()} contract keys channels by string regardless of source. We
 * pick the first-most-specific identifier the {@link IoSnapshot} carries:
 *
 * <ol>
 *   <li>The first {@code ItemSnapshot}'s id, if any items were observed.
 *   <li>Otherwise the first {@code FluidSnapshot}'s id, if any fluids were observed.
 *   <li>Otherwise the recipe id itself (so a run that produced nothing still buckets distinctly
 *       from runs that produced output, instead of all collapsing into a single empty channel).
 * </ol>
 *
 * <p>Recipe types whose channel identity isn't captured by this scheme — a centrifuge that
 * produces three items per run, for example — are expected to register an extension that
 * controls channel identity end-to-end via {@code validateOutput()}, bypassing the sample-loop
 * driver entirely.
 */
public final class ChannelExtractor {

    private ChannelExtractor() {}

    /** Bucket {@code result} into a channel identifier. Never returns null or empty. */
    public static String channelOf(TestResult result) {
        Objects.requireNonNull(result, "result");
        IoSnapshot actual = result.actual();
        if (!actual.items().isEmpty()) {
            return actual.items().get(0).id().toString();
        }
        if (!actual.fluids().isEmpty()) {
            return actual.fluids().get(0).id().toString();
        }
        return result.recipeId().toString();
    }
}
