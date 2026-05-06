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

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-thread tick driver for in-flight {@link RecipeTestRunner} instances. Listens to
 * {@link ServerTickEvent.Post} (subscribed in {@code RecipeTestMod}) and advances every active
 * runner exactly once per server tick.
 *
 * <p>Single instance — runs are submitted via {@link #submit(RecipeTestRunner)} from the command
 * thread, but {@link #onServerTick} fires on the server thread, so we use a thread-safe queue.
 */
public final class RunSessionScheduler {

    private static final RunSessionScheduler INSTANCE = new RunSessionScheduler();

    private final ConcurrentLinkedQueue<RecipeTestRunner> active = new ConcurrentLinkedQueue<>();

    private RunSessionScheduler() {}

    public static RunSessionScheduler instance() {
        return INSTANCE;
    }

    /** Queue a runner for tick-driven advancement. The first tick after submission begins
     *  execution. */
    public void submit(RecipeTestRunner runner) {
        Objects.requireNonNull(runner, "runner must not be null");
        active.add(runner);
    }

    /** Number of runs currently executing — used by tests and by {@code /recipe_test list}. */
    public int activeCount() {
        return active.size();
    }

    /**
     * Tick handler — call from a {@link ServerTickEvent.Post} listener.
     *
     * <p>Drains via {@link ConcurrentLinkedQueue#poll()} up to the size at entry, advances each
     * runner exactly once, and re-queues the unfinished ones at the tail. Snapshotting the size
     * up front means runners submitted during this tick (e.g. by lifecycle commands or by the
     * runner's own callback) wait until the next tick instead of being advanced twice. CLQ's
     * iterator is weakly consistent, so the drain pattern keeps the per-tick semantics
     * deterministic regardless of concurrent {@link #submit} calls.
     */
    public void onServerTick(ServerTickEvent.Post event) {
        int budget = active.size();
        for (int i = 0; i < budget; i++) {
            RecipeTestRunner runner = active.poll();
            if (runner == null) {
                return;
            }
            runner.advance();
            if (!runner.isDone()) {
                active.offer(runner);
            }
        }
    }
}
