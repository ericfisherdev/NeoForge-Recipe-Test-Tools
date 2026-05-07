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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DistributionValidatorTest {

    @Test
    void exactMatchPasses() {
        Map<String, Long> observed = Map.of("a", 700L, "b", 300L);
        Map<String, Double> weights = Map.of("a", 0.7, "b", 0.3);
        DistributionValidator.Result r = DistributionValidator.verdict(observed, weights, 1000, 0.0);
        assertTrue(r.pass());
        assertTrue(r.failedChannels().isEmpty());
    }

    @Test
    void withinTolerancePasses() {
        Map<String, Long> observed = Map.of("a", 720L, "b", 280L); // a is 0.72 vs expected 0.70
        Map<String, Double> weights = Map.of("a", 0.7, "b", 0.3);
        DistributionValidator.Result r = DistributionValidator.verdict(observed, weights, 1000, 0.05);
        assertTrue(r.pass());
        // Channel deltas are sorted by absolute delta descending
        assertEquals("a", r.channels().get(0).channel());
        assertEquals(0.02, r.channels().get(0).delta(), 1.0e-9);
    }

    @Test
    void outOfToleranceFails() {
        Map<String, Long> observed = Map.of("a", 100L, "b", 900L); // wildly off declared 0.7/0.3
        Map<String, Double> weights = Map.of("a", 0.7, "b", 0.3);
        DistributionValidator.Result r = DistributionValidator.verdict(observed, weights, 1000, 0.05);
        assertFalse(r.pass());
        assertEquals(2, r.failedChannels().size());
    }

    @Test
    void missingExpectedChannelObservedZeroFails() {
        Map<String, Long> observed = Map.of("a", 1000L); // never produced "b"
        Map<String, Double> weights = Map.of("a", 0.7, "b", 0.3);
        DistributionValidator.Result r = DistributionValidator.verdict(observed, weights, 1000, 0.05);
        assertFalse(r.pass());
        // "b" is in failedChannels with observed 0/1000 and expected 0.3 → delta -0.3
        DistributionValidator.ChannelDelta b = r.channels().stream()
                .filter(c -> c.channel().equals("b"))
                .findFirst()
                .orElseThrow();
        assertEquals(0L, b.observedCount());
        assertEquals(-0.3, b.delta(), 1.0e-9);
    }

    @Test
    void unexpectedChannelObservedFails() {
        Map<String, Long> observed = Map.of("a", 700L, "surprise", 300L);
        Map<String, Double> weights = Map.of("a", 0.7); // surprise has expected weight 0
        DistributionValidator.Result r = DistributionValidator.verdict(observed, weights, 1000, 0.05);
        assertFalse(r.pass());
        DistributionValidator.ChannelDelta surprise = r.channels().stream()
                .filter(c -> c.channel().equals("surprise"))
                .findFirst()
                .orElseThrow();
        assertEquals(0.3, surprise.delta(), 1.0e-9);
    }

    @Test
    void deltaListIsSortedByAbsoluteDeltaDescending() {
        Map<String, Long> observed = Map.of("a", 750L, "b", 250L);
        Map<String, Double> weights = Map.of("a", 0.7, "b", 0.3);
        DistributionValidator.Result r = DistributionValidator.verdict(observed, weights, 1000, 0.1);
        // Both deltas are 0.05; either order is fine, but check the sort is non-increasing by |delta|
        double prev = Double.MAX_VALUE;
        for (DistributionValidator.ChannelDelta c : r.channels()) {
            assertTrue(Math.abs(c.delta()) <= prev, "expected non-increasing |delta| order");
            prev = Math.abs(c.delta());
        }
    }

    @Test
    void toDiffMapPreservesAllChannels() {
        Map<String, Long> observed = Map.of("a", 500L, "b", 500L);
        Map<String, Double> weights = Map.of("a", 0.5, "b", 0.5);
        DistributionValidator.Result r = DistributionValidator.verdict(observed, weights, 1000, 0.0);
        Map<String, Double> diff = r.toDiffMap();
        assertEquals(2, diff.size());
        assertTrue(diff.containsKey("a"));
        assertTrue(diff.containsKey("b"));
    }

    @Test
    void rejectsBadInputs() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> DistributionValidator.verdict(Map.of(), Map.of(), 0, 0.05),
                        "totalSamples must be >= 1"),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> DistributionValidator.verdict(Map.of(), Map.of(), 10, -0.1),
                        "tolerance must be in [0,1]"),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> DistributionValidator.verdict(Map.of(), Map.of(), 10, 1.5),
                        "tolerance must be in [0,1]"),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> DistributionValidator.verdict(buildMap("a", -1L), Map.of(), 10, 0.05),
                        "observed count must be >= 0"),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> DistributionValidator.verdict(Map.of(), buildWeightMap("a", 1.5), 10, 0.05),
                        "expected weight must be in [0,1]"));
    }

    @Test
    void nanToleranceAndNanWeightsAreRejected() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> DistributionValidator.verdict(Map.of(), Map.of(), 10, Double.NaN),
                        "NaN tolerance must not silently pass the range check"),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> DistributionValidator.verdict(Map.of(), buildWeightMap("a", Double.NaN), 10, 0.05),
                        "NaN expected weight must not silently pass the range check"));
    }

    @Test
    void nullChannelKeysAreRejected() {
        Map<String, Long> badObserved = new LinkedHashMap<>();
        badObserved.put(null, 1L);
        Map<String, Double> badWeights = new LinkedHashMap<>();
        badWeights.put(null, 0.5);
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> DistributionValidator.verdict(badObserved, Map.of(), 10, 0.05)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> DistributionValidator.verdict(Map.of(), badWeights, 10, 0.05)));
    }

    @Test
    void emptyInputsProduceEmptyPass() {
        DistributionValidator.Result r = DistributionValidator.verdict(Map.of(), Map.of(), 1, 0.05);
        assertTrue(r.pass());
        assertTrue(r.channels().isEmpty());
    }

    private static Map<String, Long> buildMap(String key, long value) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }

    private static Map<String, Double> buildWeightMap(String key, double value) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }
}
