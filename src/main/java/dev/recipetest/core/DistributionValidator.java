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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Pure-logic verdict for distribution-mode validation. Given a histogram of channel observations
 * from N runs and a map of expected per-channel weights, decides whether every channel's
 * observed frequency is within ±tolerance of its declared weight. Per-channel deltas are
 * captured in the result so a failing run can show exactly which channel diverged and by how
 * much.
 *
 * <p><b>Pure.</b> No runner, scheduler, or world dependency. The harness's distribution-mode
 * runner (added in Phase 5 PR-C) collects observations across N runs and hands them to this
 * class for the verdict; tests pass synthetic histograms directly.
 *
 * <p><b>Channel identity.</b> Channels are keyed by string. The runner uses item / fluid IDs
 * serialised to strings, but the validator treats them as opaque keys — anything {@code .equals}
 * comparable works.
 */
public final class DistributionValidator {

    private DistributionValidator() {}

    /**
     * Compare an observed histogram against expected weights with {@code ±tolerance} per
     * channel.
     *
     * @param observed counts per channel; values must be {@code >= 0}
     * @param expectedWeights probabilities per channel; values should be in {@code [0, 1]} and
     *     ideally sum to roughly 1 (the validator tolerates drift since the relative comparison
     *     against tolerance still produces meaningful per-channel verdicts)
     * @param totalSamples the total run count; must be {@code >= 1}. Provided separately rather
     *     than derived from {@code sum(observed)} so a missing-channel observation (count = 0)
     *     still divides correctly when no run produced that output
     * @param tolerance allowed absolute deviation between observed frequency and expected
     *     weight; must be in {@code [0, 1]}
     * @return a {@link Result} with PASS/FAIL verdict and per-channel deltas. The result is
     *     stable-sorted by absolute delta descending so the worst offender is first.
     */
    public static Result verdict(
            Map<String, Long> observed, Map<String, Double> expectedWeights, int totalSamples, double tolerance) {
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(expectedWeights, "expectedWeights");
        if (totalSamples < 1) {
            throw new IllegalArgumentException("totalSamples must be >= 1, got " + totalSamples);
        }
        // Reject NaN explicitly — < / > comparisons silently return false for NaN, so without this
        // the subsequent range check would treat NaN as in-range and pass invalid input downstream.
        if (Double.isNaN(tolerance) || tolerance < 0.0 || tolerance > 1.0) {
            throw new IllegalArgumentException("tolerance must be in [0,1], got " + tolerance);
        }
        for (Map.Entry<String, Long> e : observed.entrySet()) {
            if (e.getKey() == null) {
                throw new IllegalArgumentException("observed contains a null channel key");
            }
            if (e.getValue() == null || e.getValue() < 0L) {
                throw new IllegalArgumentException("observed['" + e.getKey() + "'] must be >= 0, got " + e.getValue());
            }
        }
        for (Map.Entry<String, Double> e : expectedWeights.entrySet()) {
            if (e.getKey() == null) {
                throw new IllegalArgumentException("expectedWeights contains a null channel key");
            }
            if (e.getValue() == null || Double.isNaN(e.getValue()) || e.getValue() < 0.0 || e.getValue() > 1.0) {
                throw new IllegalArgumentException(
                        "expectedWeights['" + e.getKey() + "'] must be in [0,1], got " + e.getValue());
            }
        }

        // Walk the union of channel keys so a channel that's expected but never observed
        // (observed = 0) and a channel observed but not expected (weight = 0) both surface.
        TreeSet<String> allChannels = new TreeSet<>();
        allChannels.addAll(observed.keySet());
        allChannels.addAll(expectedWeights.keySet());

        List<ChannelDelta> deltas = new ArrayList<>(allChannels.size());
        boolean pass = true;
        for (String channel : allChannels) {
            long count = observed.getOrDefault(channel, 0L);
            double observedFrequency = (double) count / totalSamples;
            double expected = expectedWeights.getOrDefault(channel, 0.0);
            double delta = observedFrequency - expected;
            boolean withinTolerance = Math.abs(delta) <= tolerance;
            if (!withinTolerance) {
                pass = false;
            }
            deltas.add(new ChannelDelta(channel, count, observedFrequency, expected, delta, withinTolerance));
        }
        // Worst offender first (largest absolute delta), keeping per-channel detail useful in
        // failure reports.
        deltas.sort(Comparator.comparingDouble((ChannelDelta d) -> Math.abs(d.delta()))
                .reversed());
        return new Result(pass, totalSamples, tolerance, List.copyOf(deltas));
    }

    /**
     * Verdict + per-channel detail. Use {@link #toDiffMap()} to render the breakdown into a
     * shape compatible with the harness's existing diff infrastructure.
     */
    public record Result(boolean pass, int totalSamples, double tolerance, List<ChannelDelta> channels) {

        public Result {
            Objects.requireNonNull(channels, "channels");
            channels = List.copyOf(channels);
        }

        /** Human-readable summary of every channel that failed the tolerance check. Empty list
         *  on PASS. */
        public List<ChannelDelta> failedChannels() {
            List<ChannelDelta> failed = new ArrayList<>();
            for (ChannelDelta c : channels) {
                if (!c.withinTolerance()) {
                    failed.add(c);
                }
            }
            return List.copyOf(failed);
        }

        /** Channel-keyed map of {@code observed - expected} deltas. Useful for embedding into
         *  the harness's diff JSON without inventing new types. */
        public Map<String, Double> toDiffMap() {
            Map<String, Double> map = new LinkedHashMap<>();
            for (ChannelDelta c : channels) {
                map.put(c.channel(), c.delta());
            }
            return map;
        }
    }

    /**
     * Per-channel observation in a distribution run.
     *
     * @param channel opaque channel identifier
     * @param observedCount number of runs that produced this channel
     * @param observedFrequency {@code observedCount / totalSamples}
     * @param expectedWeight probability declared by the extension's {@code weights()} map
     * @param delta {@code observedFrequency - expectedWeight}; sign matters
     * @param withinTolerance {@code |delta| <= tolerance}
     */
    public record ChannelDelta(
            String channel,
            long observedCount,
            double observedFrequency,
            double expectedWeight,
            double delta,
            boolean withinTolerance) {

        public ChannelDelta {
            Objects.requireNonNull(channel, "channel");
        }
    }
}
