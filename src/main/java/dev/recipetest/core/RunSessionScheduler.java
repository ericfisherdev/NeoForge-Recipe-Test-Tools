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

import java.util.Iterator;
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

    /** Tick handler — call from a {@link ServerTickEvent.Post} listener. */
    public void onServerTick(ServerTickEvent.Post event) {
        Iterator<RecipeTestRunner> it = active.iterator();
        while (it.hasNext()) {
            RecipeTestRunner runner = it.next();
            runner.advance();
            if (runner.isDone()) {
                it.remove();
            }
        }
    }
}
