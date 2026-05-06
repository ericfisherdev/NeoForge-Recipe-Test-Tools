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

/**
 * How long the harness will let a recipe tick before declaring it stalled.
 *
 * <ul>
 *   <li>{@link Auto} — use {@code recipe.getProcessingTime()} if present, else 200 ticks.
 *   <li>{@link Fixed} — hard cap of N ticks; {@code TIMEOUT} if no output by tick N.
 * </ul>
 */
public sealed interface TickBudget permits TickBudget.Auto, TickBudget.Fixed {

    /** Singleton instance for the {@code "auto"} tick budget. */
    Auto AUTO = new Auto();

    /** Marker singleton for the {@code "auto"} tick budget. */
    record Auto() implements TickBudget {}

    /** Hard tick cap. */
    record Fixed(int ticks) implements TickBudget {
        public Fixed {
            if (ticks <= 0) {
                throw new IllegalArgumentException("tickBudget must be > 0, got " + ticks);
            }
        }
    }
}
