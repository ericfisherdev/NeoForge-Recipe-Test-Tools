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
import dev.recipetest.api.BulkProgress;
import dev.recipetest.api.BulkResult;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestContext;
import dev.recipetest.api.TestResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Cooperative bulk runner. Owns a queue of pending {@link Job}s and at most one in-flight
 * {@link RecipeTestRunner}; advances the runner one server tick at a time inside an MSPT budget
 * pulled from {@link HarnessConfig#BULK_MSPT_BUDGET_MS}.
 *
 * <p>Single in-flight job is the Phase 3 simplification — every spec shares the same structure
 * region, so two runners cannot place blocks simultaneously without conflicts. Phase 4 may
 * partition structures and revisit.
 *
 * <p>Concurrency: only the server thread reads or mutates the active state; the cancel flag is
 * volatile because {@code /recipe_test cancel} runs on the same thread but a defensive write
 * keeps memory semantics obvious.
 */
public final class TickScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final TickScheduler INSTANCE = new TickScheduler();

    /** One enqueued recipe in a bulk run. */
    public record Job(MachineSpec spec, RecipeHolder<?> recipe, RecipeAdapter adapter, String specSource) {
        public Job {
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(recipe, "recipe");
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(specSource, "specSource");
        }
    }

    /** Snapshot of an active bulk run — exposed for {@code /recipe_test cancel} lookup and tests. */
    public static final class ActiveRun {
        final String runId;
        final String recipeTypeLabel;
        final TestContext ctx;
        final Deque<Job> queue;
        final int total;
        final long startMillis;
        final Consumer<BulkProgress> progressSink;
        final Consumer<BulkResult> finalSink;

        final List<TestResult> results = new ArrayList<>();
        final EnumMap<RunStatus, Integer> counts = new EnumMap<>(RunStatus.class);
        long peakMsptBudgetUsedMs;
        int totalEngineTicks;
        int ticksSinceLastProgress;
        int completedSinceLastProgress;
        volatile boolean cancelRequested;

        @Nullable
        RecipeTestRunner current;

        ActiveRun(
                String runId,
                String recipeTypeLabel,
                TestContext ctx,
                Deque<Job> queue,
                int total,
                Consumer<BulkProgress> progressSink,
                Consumer<BulkResult> finalSink) {
            this.runId = runId;
            this.recipeTypeLabel = recipeTypeLabel;
            this.ctx = ctx;
            this.queue = queue;
            this.total = total;
            this.startMillis = System.currentTimeMillis();
            this.progressSink = progressSink;
            this.finalSink = finalSink;
        }

        public String runId() {
            return runId;
        }

        public int completed() {
            return results.size();
        }

        public int remaining() {
            return total - completed();
        }
    }

    private final AtomicReference<@Nullable ActiveRun> active = new AtomicReference<>();

    private TickScheduler() {}

    public static TickScheduler instance() {
        return INSTANCE;
    }

    /**
     * Generate a fresh bulk run id. Callers that need a deterministic shuffle seed call this
     * before {@link #submit} so they can derive the seed from the same id they later pass in.
     */
    public static String newRunId() {
        return "bulk-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Submit a bulk run with the given {@code runId}. Throws {@link IllegalStateException} if
     * another run is already active — Phase 3 enforces one bulk-in-flight at a time.
     */
    public void submit(
            String runId,
            String recipeTypeLabel,
            TestContext ctx,
            List<Job> jobs,
            Consumer<BulkProgress> progressSink,
            Consumer<BulkResult> finalSink) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(recipeTypeLabel, "recipeTypeLabel");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(jobs, "jobs");
        Objects.requireNonNull(progressSink, "progressSink");
        Objects.requireNonNull(finalSink, "finalSink");

        Deque<Job> queue = new ArrayDeque<>(jobs);
        ActiveRun run = new ActiveRun(runId, recipeTypeLabel, ctx, queue, jobs.size(), progressSink, finalSink);
        if (!active.compareAndSet(null, run)) {
            ActiveRun existing = active.get();
            String existingId = existing == null ? "<unknown>" : existing.runId;
            throw new IllegalStateException("another bulk run is already active (runId=" + existingId + ")");
        }
    }

    /**
     * Cancel the active run by id. Returns {@code true} if a run with that id was found and
     * marked for cancellation. Pass {@code null} to cancel whichever run is currently active
     * (the {@code /recipe_test cancel} no-arg form).
     */
    public boolean cancel(@Nullable String runId) {
        ActiveRun run = active.get();
        if (run == null) {
            return false;
        }
        if (runId != null && !runId.equals(run.runId)) {
            return false;
        }
        run.cancelRequested = true;
        if (run.current != null) {
            run.current.cancel();
        }
        return true;
    }

    /** Diagnostic: id of the active run, or empty if none. Used by tests + the list command. */
    public Optional<String> activeRunId() {
        ActiveRun run = active.get();
        return run == null ? Optional.empty() : Optional.of(run.runId);
    }

    /** Number of recipes left to advance in the active run; 0 if no run is active. */
    public int queueDepth() {
        ActiveRun run = active.get();
        return run == null ? 0 : run.queue.size() + (run.current != null ? 1 : 0);
    }

    /** Hook for {@code RecipeTestMod}'s {@link ServerTickEvent.Post} listener. */
    public void onServerTick(ServerTickEvent.Post event) {
        ActiveRun run = active.get();
        if (run == null) {
            return;
        }

        long tickStart = System.currentTimeMillis();
        long budget = HarnessConfig.BULK_MSPT_BUDGET_MS.get();

        // Drain on cancel: emit any in-flight CANCELLED result, then finalise. The cancel-tick
        // is real work the harness did; track it in totalEngineTicks / peakMspt so the final
        // BulkResult reflects the actual cost of the cancellation pass and an MSPT spike on
        // the cancellation tick still surfaces in the WARN log.
        if (run.cancelRequested) {
            if (run.current != null) {
                run.current.advance();
                if (run.current.isDone()) {
                    run.current = null;
                }
                run.totalEngineTicks++;
                long cancelElapsed = System.currentTimeMillis() - tickStart;
                if (cancelElapsed > run.peakMsptBudgetUsedMs) {
                    run.peakMsptBudgetUsedMs = cancelElapsed;
                }
                if (cancelElapsed > budget) {
                    LOGGER.warn(
                            "recipe_test bulk: cancel tick exceeded MSPT budget ({} ms > {} ms) for run {}",
                            cancelElapsed,
                            budget,
                            run.runId);
                }
            }
            finaliseRun(run, true);
            return;
        }

        // Start a new runner if no one is in flight and there's work queued.
        if (run.current == null) {
            if (run.queue.isEmpty()) {
                finaliseRun(run, false);
                return;
            }
            run.current = startNextRunner(run);
        }

        // Advance the current runner exactly once per server tick. The runner internally
        // chains non-tick-spending phases (PLACE → preTickCommands → REPORT → CLEANUP) into a
        // single advance() call, so one invocation per tick is correct.
        run.current.advance();
        run.totalEngineTicks++;
        run.ticksSinceLastProgress++;
        if (run.current.isDone()) {
            run.current = null;
        }

        long elapsed = System.currentTimeMillis() - tickStart;
        if (elapsed > run.peakMsptBudgetUsedMs) {
            run.peakMsptBudgetUsedMs = elapsed;
        }
        if (elapsed > budget) {
            LOGGER.warn(
                    "recipe_test bulk: tick exceeded MSPT budget ({} ms > {} ms) for run {}",
                    elapsed,
                    budget,
                    run.runId);
        }

        // Finalise on the same tick the last runner completes; otherwise a /recipe_test cancel
        // arriving in the gap between the last completion and the next tick would mislabel the
        // already-finished run as cancelled.
        if (run.current == null && run.queue.isEmpty()) {
            finaliseRun(run, false);
            return;
        }

        maybeEmitProgress(run);
    }

    private RecipeTestRunner startNextRunner(ActiveRun run) {
        Job job = run.queue.poll();
        if (job == null) {
            throw new IllegalStateException("startNextRunner called with empty queue");
        }
        Consumer<TestResult> resultSink = result -> {
            run.results.add(result);
            run.counts.merge(result.status(), 1, Integer::sum);
            run.completedSinceLastProgress++;
        };
        return new RecipeTestRunner(job.spec, job.recipe, run.ctx, job.adapter, job.specSource, resultSink);
    }

    private void maybeEmitProgress(ActiveRun run) {
        int everyN = HarnessConfig.PROGRESS_REPORT_EVERY_N.get();
        int everyTicks = HarnessConfig.PROGRESS_REPORT_EVERY_TICKS.get();
        boolean emit = run.completedSinceLastProgress >= everyN || run.ticksSinceLastProgress >= everyTicks;
        if (!emit || run.completed() == 0) {
            return;
        }
        run.completedSinceLastProgress = 0;
        run.ticksSinceLastProgress = 0;
        // Surfacing the in-flight recipe id requires plumbing through RecipeTestRunner; deferred
        // for Phase 3 so the scheduler doesn't reach into runner internals just for diagnostics.
        Optional<ResourceLocation> inFlight = Optional.empty();
        BulkProgress progress = new BulkProgress(
                run.runId, run.recipeTypeLabel, run.total, run.completed(), Map.copyOf(run.counts), inFlight, 0);
        try {
            run.progressSink.accept(progress);
        } catch (RuntimeException ex) {
            LOGGER.warn("recipe_test bulk: progress sink threw {}", ex.toString());
        }
    }

    private void finaliseRun(ActiveRun run, boolean cancelled) {
        long wallClockMs = System.currentTimeMillis() - run.startMillis;
        BulkResult result = new BulkResult(
                run.runId,
                run.recipeTypeLabel,
                Map.copyOf(run.counts),
                wallClockMs,
                run.totalEngineTicks,
                run.peakMsptBudgetUsedMs,
                cancelled,
                List.copyOf(run.results));
        active.set(null);
        try {
            run.finalSink.accept(result);
        } catch (RuntimeException ex) {
            LOGGER.warn("recipe_test bulk: final sink threw {}", ex.toString());
        }
    }
}
