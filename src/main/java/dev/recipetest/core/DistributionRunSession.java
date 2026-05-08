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

import com.mojang.logging.LogUtils;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.TestContext;
import dev.recipetest.api.TestResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.slf4j.Logger;

/**
 * Sample-loop orchestrator for {@code validation.mode = "distribution"} runs. Submits {@code N}
 * fresh {@link RecipeTestRunner} instances back-to-back through the injected
 * {@link RunnerSubmitter}, buckets each completed run into a channel via
 * {@link ChannelExtractor}, and on completion of the final sample hands the assembled histogram
 * to {@link DistributionValidator#verdict} for the PASS/FAIL decision.
 *
 * <p><b>Why a session.</b> The kit's existing {@link RecipeTestRunner} drives one recipe
 * one time. Distribution-mode wants the same recipe driven {@code N} times with the same
 * placement, then one combined verdict. Wrapping that orchestration in its own class keeps the
 * runner state machine simple and lets tests exercise the sample-loop logic without booting a
 * full Minecraft server (synthetic {@link RunnerSubmitter} implementations skip the real run).
 *
 * <p><b>Submission strategy.</b> Samples are submitted serially: the next runner is queued only
 * after the current one publishes a {@link TestResult}. Single-runner-at-a-time is the same
 * invariant the GameTest auto-generator relies on for its dynamic batch — concurrent runners
 * would step on each other's machine placement at the same {@link TestContext#origin}. The
 * session is one-shot; cancel replaces the next-submit with a no-op and emits a
 * {@link DistributionValidator.Result} based on whatever observations had landed.
 */
public final class DistributionRunSession {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Submission seam — production wires this to {@link RunSessionScheduler#submit}, tests
     * inject a synthetic submitter that fakes a {@link TestResult} immediately. Capturing
     * everything {@link RecipeTestRunner}'s constructor needs keeps the session class free of a
     * direct {@code RecipeTestRunner} import path that would force the test classpath to load
     * the rest of the runner's transitive dependencies.
     */
    public interface RunnerSubmitter {
        void submit(
                MachineSpec spec,
                RecipeHolder<?> holder,
                TestContext ctx,
                RecipeAdapter adapter,
                String specSource,
                Consumer<TestResult> callback);
    }

    private final MachineSpec spec;
    private final RecipeHolder<?> holder;
    private final TestContext ctx;
    private final RecipeAdapter adapter;
    private final String specSource;
    private final int totalSamples;
    private final double tolerance;
    private final Map<String, Double> expectedWeights;
    private final RunnerSubmitter submitter;
    private final Consumer<DistributionValidator.Result> done;

    private final Map<String, Long> observed = new ConcurrentHashMap<>();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    /**
     * Trampoline guard. When a sync submitter calls back into the session inside
     * {@code submitter.submit()}, the inner {@link #submitNext} would otherwise recurse —
     * Phase 5 AC#1 wants 1000 samples, which would blow the stack. Instead, the inner call sees
     * {@code submitting == true}, flips {@link #needsAnotherSubmit}, and unwinds; the outer
     * loop then keeps iterating. Async submitters take the empty-flag path: the outer loop
     * exits after one submit, and the eventual async callback re-enters {@link #submitNext}
     * fresh.
     */
    private final AtomicBoolean submitting = new AtomicBoolean();

    private final AtomicBoolean needsAnotherSubmit = new AtomicBoolean();

    private DistributionRunSession(
            MachineSpec spec,
            RecipeHolder<?> holder,
            TestContext ctx,
            RecipeAdapter adapter,
            String specSource,
            int totalSamples,
            double tolerance,
            Map<String, Double> expectedWeights,
            RunnerSubmitter submitter,
            Consumer<DistributionValidator.Result> done) {
        // Validate session-internal fields up front. The pass-through fields (spec, holder, ctx,
        // adapter, specSource) are forwarded verbatim to the submitter — production submitters
        // dereference them and will NPE if a caller passes null, but tests using a synthetic
        // submitter that doesn't touch the fields shouldn't be forced to construct a real
        // MachineSpec just to exercise the sample loop.
        if (totalSamples < 1) {
            throw new IllegalArgumentException("totalSamples must be >= 1, got " + totalSamples);
        }
        this.spec = spec;
        this.holder = holder;
        this.ctx = ctx;
        this.adapter = adapter;
        this.specSource = specSource;
        this.tolerance = tolerance;
        this.expectedWeights = Map.copyOf(Objects.requireNonNull(expectedWeights, "expectedWeights"));
        this.submitter = Objects.requireNonNull(submitter, "submitter");
        this.done = Objects.requireNonNull(done, "done");
        this.totalSamples = totalSamples;
    }

    /**
     * Start a session. Invokes {@code submitter} once for the first sample; subsequent samples
     * are submitted from the per-run callback so the queue depth stays at 1. Returns the live
     * session so callers can {@link #cancel} mid-run.
     */
    public static DistributionRunSession start(
            MachineSpec spec,
            RecipeHolder<?> holder,
            TestContext ctx,
            RecipeAdapter adapter,
            String specSource,
            int totalSamples,
            double tolerance,
            Map<String, Double> expectedWeights,
            RunnerSubmitter submitter,
            Consumer<DistributionValidator.Result> done) {
        DistributionRunSession session = new DistributionRunSession(
                spec, holder, ctx, adapter, specSource, totalSamples, tolerance, expectedWeights, submitter, done);
        session.submitNext();
        return session;
    }

    /**
     * Abort the session. Subsequent run completions are still recorded (the runner already
     * dispatched can't be cancelled mid-flight), but the session won't queue another sample and
     * emits the final verdict against whatever histogram had been accumulated by the time
     * cancel was called.
     */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            // If no sample is in flight (e.g. cancel called after the final completion already
            // queued the verdict), drive the verdict ourselves; otherwise the in-flight callback
            // will see cancelled=true and finalise.
            tryFinalise();
        }
    }

    /** Total samples requested. */
    public int totalSamples() {
        return totalSamples;
    }

    /** Samples completed so far — useful for progress reporting. */
    public int completedSamples() {
        return completed.get();
    }

    /** True after the final {@link DistributionValidator.Result} has been published. */
    public boolean isFinished() {
        return finished.get();
    }

    // ---- internals ----

    private void submitNext() {
        // Trampoline: if a submit/callback chain is already running on this thread, just flag
        // the desire for another submit and return. The outer loop picks it up on the next
        // iteration without growing the stack. Async submitters that don't fire the callback
        // synchronously take the empty-flag path: the loop exits, and the async callback's
        // own submitNext() call re-enters here fresh after the outer loop has released the CAS.
        if (!submitting.compareAndSet(false, true)) {
            needsAnotherSubmit.set(true);
            return;
        }
        try {
            do {
                needsAnotherSubmit.set(false);
                if (cancelled.get() || completed.get() >= totalSamples) {
                    tryFinalise();
                    return;
                }
                try {
                    submitter.submit(spec, holder, ctx, adapter, specSource, this::onSampleComplete);
                } catch (RuntimeException e) {
                    LOGGER.error(
                            "recipe_test: distribution session aborted — submitter threw on sample {}/{}: {}",
                            completed.get() + 1,
                            totalSamples,
                            e.toString());
                    cancelled.set(true);
                    tryFinalise();
                    return;
                }
                // If the callback fired synchronously, onSampleComplete called submitNext() which
                // saw submitting=true and set needsAnotherSubmit. Loop back. If the callback was
                // async, the flag stays false and we exit; the callback will re-enter later.
            } while (needsAnotherSubmit.get());
        } finally {
            submitting.set(false);
        }
        // TOCTOU drain: a concurrent async callback may have lost the CAS race above —
        // observed submitting=true after our `while` evaluated false, set
        // needsAnotherSubmit, and returned without re-submitting. With the CAS released, the
        // flag would otherwise stay true forever and the session would stall. Drain it here
        // so the orphaned re-entry request still drives forward.
        //
        // Synchronous submitters always exit the do/while with needsAnotherSubmit=false (the
        // sync callback's CAS-fail set the flag, which the loop's read-and-clear consumed) so
        // this branch is a no-op for them.
        if (needsAnotherSubmit.compareAndSet(true, false)) {
            submitNext();
        }
    }

    private void onSampleComplete(TestResult result) {
        try {
            String channel = ChannelExtractor.channelOf(result);
            observed.merge(channel, 1L, Long::sum);
        } catch (RuntimeException e) {
            // Defensive — channel extraction is supposed to be infallible, but a malformed
            // TestResult shouldn't kill the whole session. Log and bucket under whatever fallback
            // we can derive without dereferencing the bad result, so the run still counts toward
            // totalSamples and DistributionValidator gets a complete histogram.
            String fallback = INVALID_RESULT_CHANNEL;
            if (result != null && result.recipeId() != null) {
                fallback = result.recipeId().toString();
            }
            LOGGER.warn(
                    "recipe_test: channel extraction failed for sample {}/{} — bucketing under '{}': {}",
                    completed.get() + 1,
                    totalSamples,
                    fallback,
                    e.toString());
            observed.merge(fallback, 1L, Long::sum);
        }
        int n = completed.incrementAndGet();
        if (cancelled.get() || n >= totalSamples) {
            tryFinalise();
        } else {
            submitNext();
        }
    }

    /** Bucket key used when a sample's {@link TestResult} is malformed enough that even the
     *  fallback to {@code recipeId().toString()} would NPE. Surfaces the failure in the
     *  histogram instead of silently dropping the run. */
    static final String INVALID_RESULT_CHANNEL = "__invalid_result__";

    private void tryFinalise() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        int sampleCount = completed.get();
        DistributionValidator.Result verdict;
        if (sampleCount == 0) {
            // No samples landed (cancel or submitter failure on the very first call).
            // DistributionValidator.verdict requires totalSamples >= 1 — emitting an empty FAIL
            // result directly is more honest than fabricating a sample to slip past that guard.
            verdict = new DistributionValidator.Result(false, 0, tolerance, java.util.List.of());
        } else {
            try {
                verdict = DistributionValidator.verdict(
                        new LinkedHashMap<>(observed), expectedWeights, sampleCount, tolerance);
            } catch (RuntimeException e) {
                LOGGER.error("recipe_test: distribution verdict failed — emitting empty result: {}", e.toString());
                verdict = new DistributionValidator.Result(false, sampleCount, tolerance, java.util.List.of());
            }
        }
        try {
            done.accept(verdict);
        } catch (RuntimeException e) {
            LOGGER.warn("recipe_test: distribution session callback threw: {}", e.toString());
        }
    }
}
