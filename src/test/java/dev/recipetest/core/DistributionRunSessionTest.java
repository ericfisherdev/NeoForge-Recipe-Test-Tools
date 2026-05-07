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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.Diagnostics;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.ItemSnapshot;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestContext;
import dev.recipetest.api.TestResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.Test;

class DistributionRunSessionTest {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("forestry", "centrifuge");
    private static final ResourceLocation RECIPE =
            ResourceLocation.fromNamespaceAndPath("forestry", "centrifuge/honey");
    private static final ResourceLocation HONEY = ResourceLocation.parse("minecraft:honey_bottle");
    private static final ResourceLocation WAX = ResourceLocation.parse("minecraft:honeycomb");

    @Test
    void runsAllSamplesAndProducesPassWhenWithinTolerance() {
        // 70 honey, 30 wax → matches expected 0.7 / 0.3 exactly.
        WeightedSubmitter submitter =
                WeightedSubmitter.alternating(List.of(HONEY, HONEY, HONEY, HONEY, HONEY, HONEY, HONEY, WAX, WAX, WAX));
        AtomicReference<DistributionValidator.Result> verdict = new AtomicReference<>();

        DistributionRunSession session = DistributionRunSession.start(
                FAKE_SPEC,
                FAKE_HOLDER,
                FAKE_CTX,
                FAKE_ADAPTER,
                "test:centrifuge.json",
                10,
                0.05,
                Map.of(HONEY.toString(), 0.7, WAX.toString(), 0.3),
                submitter,
                verdict::set);

        assertTrue(session.isFinished());
        assertNotNull(verdict.get());
        assertTrue(verdict.get().pass());
        assertEquals(10, verdict.get().totalSamples());
        assertEquals(10, submitter.submittedCount());
    }

    @Test
    void failsWhenObservedDeviatesBeyondTolerance() {
        // All honey → 1.0 / 0.0 vs expected 0.7 / 0.3, way outside tolerance 0.05.
        WeightedSubmitter submitter = WeightedSubmitter.constant(HONEY, 10);
        AtomicReference<DistributionValidator.Result> verdict = new AtomicReference<>();

        DistributionRunSession.start(
                FAKE_SPEC,
                FAKE_HOLDER,
                FAKE_CTX,
                FAKE_ADAPTER,
                "test:centrifuge.json",
                10,
                0.05,
                Map.of(HONEY.toString(), 0.7, WAX.toString(), 0.3),
                submitter,
                verdict::set);

        assertFalse(verdict.get().pass());
        assertFalse(verdict.get().failedChannels().isEmpty());
    }

    @Test
    void submitterFailureFinalisesWithPartialVerdict() {
        // After 3 successful samples, the submitter throws from its 4th submit. The session's
        // catch path flips cancelled and finalises with whatever histogram had landed.
        WeightedSubmitter submitter = WeightedSubmitter.failOnSubmit(HONEY, 4);
        AtomicReference<DistributionValidator.Result> verdict = new AtomicReference<>();

        DistributionRunSession session = DistributionRunSession.start(
                FAKE_SPEC,
                FAKE_HOLDER,
                FAKE_CTX,
                FAKE_ADAPTER,
                "test:centrifuge.json",
                100,
                0.05,
                Map.of(HONEY.toString(), 1.0),
                submitter,
                verdict::set);

        assertTrue(session.isFinished());
        assertEquals(3, session.completedSamples());
        assertNotNull(verdict.get());
    }

    @Test
    void zeroCompletedSamplesEmitsEmptyFailResult() {
        // Submitter throws on its very first call → no samples land.
        WeightedSubmitter submitter = WeightedSubmitter.failOnSubmit(HONEY, 1);
        AtomicReference<DistributionValidator.Result> verdict = new AtomicReference<>();

        DistributionRunSession session = DistributionRunSession.start(
                FAKE_SPEC,
                FAKE_HOLDER,
                FAKE_CTX,
                FAKE_ADAPTER,
                "test:centrifuge.json",
                10,
                0.05,
                Map.of(HONEY.toString(), 1.0),
                submitter,
                verdict::set);

        assertTrue(session.isFinished());
        assertEquals(0, session.completedSamples());
        assertNotNull(verdict.get());
        // Zero samples must produce a FAIL — fabricating a sample to slip past the verdict's
        // totalSamples >= 1 guard would distort the verdict's reported sample count.
        assertFalse(verdict.get().pass());
        assertEquals(0, verdict.get().totalSamples());
        assertTrue(verdict.get().channels().isEmpty());
    }

    @Test
    void invalidTestResultBucketsUnderFallbackChannel() {
        // Submitter returns a TestResult that null-trips channel extraction. The session's
        // catch path should still bucket the run somewhere instead of dropping it.
        DistributionRunSession.RunnerSubmitter submitter = (spec, holder, ctx, adapter, source, callback) -> {
            // ChannelExtractor.channelOf rejects null with NPE; bucket falls back to recipeId
            // — but here we simulate a result where even recipeId is null by throwing from the
            // synthetic submitter via a custom TestResult subclass... easier: make a normal
            // result and throw from a forced extractor failure.
            // Cheat: pass null result so channelOf NPEs, exercising the fallback path.
            callback.accept(null);
        };
        AtomicReference<DistributionValidator.Result> verdict = new AtomicReference<>();

        DistributionRunSession session = DistributionRunSession.start(
                FAKE_SPEC,
                FAKE_HOLDER,
                FAKE_CTX,
                FAKE_ADAPTER,
                "test:centrifuge.json",
                3,
                0.05,
                Map.of(HONEY.toString(), 1.0),
                submitter,
                verdict::set);

        assertTrue(session.isFinished());
        assertEquals(3, session.completedSamples());
        assertNotNull(verdict.get());
        // Three runs all bucketed under the invalid-result channel.
        assertTrue(
                verdict.get().channels().stream()
                        .anyMatch(c -> c.channel().equals(DistributionRunSession.INVALID_RESULT_CHANNEL)
                                && c.observedCount() == 3),
                "fallback channel should carry all three sample observations");
    }

    @Test
    void synchronousSubmitterDoesNotOverflowStackOnLargeSampleCount() {
        // Synchronous submitter — without the trampoline, this would recurse 5000 deep and
        // blow the stack on most JVMs. With the trampoline, the call depth stays constant.
        WeightedSubmitter submitter = WeightedSubmitter.constant(HONEY, 5000);
        AtomicReference<DistributionValidator.Result> verdict = new AtomicReference<>();

        DistributionRunSession session = DistributionRunSession.start(
                FAKE_SPEC,
                FAKE_HOLDER,
                FAKE_CTX,
                FAKE_ADAPTER,
                "test:centrifuge.json",
                5000,
                0.05,
                Map.of(HONEY.toString(), 1.0),
                submitter,
                verdict::set);

        assertTrue(session.isFinished());
        assertEquals(5000, session.completedSamples());
        assertTrue(verdict.get().pass());
    }

    @Test
    void cancelAfterCompletionIsIdempotent() {
        // Run a small session to completion, then call cancel() — should be a no-op rather than
        // re-emitting the verdict.
        AtomicReference<DistributionValidator.Result> verdict = new AtomicReference<>();
        DistributionRunSession session = DistributionRunSession.start(
                FAKE_SPEC,
                FAKE_HOLDER,
                FAKE_CTX,
                FAKE_ADAPTER,
                "test:centrifuge.json",
                3,
                0.05,
                Map.of(HONEY.toString(), 1.0),
                WeightedSubmitter.constant(HONEY, 3),
                verdict::set);

        DistributionValidator.Result first = verdict.get();
        session.cancel();
        // cancel after finished should not produce a different result.
        assertEquals(first, verdict.get());
    }

    @Test
    void rejectsZeroSamples() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DistributionRunSession.start(
                        FAKE_SPEC,
                        FAKE_HOLDER,
                        FAKE_CTX,
                        FAKE_ADAPTER,
                        "test:centrifuge.json",
                        0,
                        0.05,
                        Map.of(),
                        WeightedSubmitter.constant(HONEY, 0),
                        r -> {}));
    }

    @Test
    void verdictCallbackThrowingDoesNotPropagate() {
        WeightedSubmitter submitter = WeightedSubmitter.constant(HONEY, 1);
        // Should NOT throw out of start()
        DistributionRunSession.start(
                FAKE_SPEC,
                FAKE_HOLDER,
                FAKE_CTX,
                FAKE_ADAPTER,
                "test:centrifuge.json",
                1,
                0.05,
                Map.of(HONEY.toString(), 1.0),
                submitter,
                r -> {
                    throw new IllegalStateException("synthetic callback failure");
                });
    }

    // ---- test doubles ----

    /** Synthetic submitter — invokes the callback immediately with a TestResult whose first
     *  item is taken from a scripted sequence so the session sees a deterministic histogram. */
    private static final class WeightedSubmitter implements DistributionRunSession.RunnerSubmitter {
        private final List<ResourceLocation> sequence;
        private int index;
        private int submitted;
        private int failOnSubmitN = -1;

        private WeightedSubmitter(List<ResourceLocation> sequence) {
            this.sequence = sequence;
        }

        static WeightedSubmitter alternating(List<ResourceLocation> seq) {
            return new WeightedSubmitter(new ArrayList<>(seq));
        }

        static WeightedSubmitter constant(ResourceLocation single, int count) {
            List<ResourceLocation> seq = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                seq.add(single);
            }
            return new WeightedSubmitter(seq);
        }

        /** Build a submitter that throws from its Nth submit() call, exercising the session's
         *  catch-and-finalise path. */
        static WeightedSubmitter failOnSubmit(ResourceLocation channel, int failOnNthSubmit) {
            WeightedSubmitter s = constant(channel, 1000);
            s.failOnSubmitN = failOnNthSubmit;
            return s;
        }

        int submittedCount() {
            return submitted;
        }

        @Override
        public void submit(
                MachineSpec spec,
                RecipeHolder<?> holder,
                TestContext ctx,
                RecipeAdapter adapter,
                String specSource,
                Consumer<TestResult> callback) {
            submitted++;
            if (failOnSubmitN > 0 && submitted == failOnSubmitN) {
                throw new IllegalStateException("synthetic submitter failure");
            }
            ResourceLocation channel = sequence.get(Math.min(index++, sequence.size() - 1));
            TestResult r = new TestResult(
                    RECIPE,
                    TYPE,
                    specSource,
                    RunStatus.PASS,
                    20,
                    IoSnapshot.empty(),
                    new IoSnapshot(List.of(ItemSnapshot.of(channel, 1)), List.of()),
                    Optional.empty(),
                    new Diagnostics(List.of(), 0L, List.of(), List.of()));
            callback.accept(r);
        }
    }

    private static final MachineSpec FAKE_SPEC = null;
    private static final RecipeHolder<?> FAKE_HOLDER = null;
    private static final TestContext FAKE_CTX = null;
    private static final RecipeAdapter FAKE_ADAPTER = null;
}
