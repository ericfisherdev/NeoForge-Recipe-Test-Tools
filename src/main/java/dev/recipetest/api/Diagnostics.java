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

import java.util.List;
import java.util.Objects;

/**
 * Side-channel observations from a recipe run: fluids consumed, energy delta, plus any warnings
 * or log lines the runner accumulated. Always present on a {@link TestResult}, even when empty.
 *
 * @param fluidConsumed fluids drained during the run, ordered by tank index
 * @param energyConsumed FE difference between {@code energy.preFill} and post-run reading; zero
 *     when {@link EnergySpec#trackConsumption()} is false or no energy spec is declared
 * @param warnings non-fatal kit warnings (e.g. injection leftovers, unrecognised side)
 * @param logs free-form runner messages, chronologically ordered
 */
public record Diagnostics(
        List<FluidSnapshot> fluidConsumed, long energyConsumed, List<String> warnings, List<String> logs) {

    public Diagnostics {
        Objects.requireNonNull(fluidConsumed, "Diagnostics.fluidConsumed must not be null");
        Objects.requireNonNull(warnings, "Diagnostics.warnings must not be null");
        Objects.requireNonNull(logs, "Diagnostics.logs must not be null");
        fluidConsumed = List.copyOf(fluidConsumed);
        warnings = List.copyOf(warnings);
        logs = List.copyOf(logs);
    }

    private static final Diagnostics EMPTY = new Diagnostics(List.of(), 0L, List.of(), List.of());

    /** Singleton diagnostics with no observations. */
    public static Diagnostics empty() {
        return EMPTY;
    }
}
