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

/**
 * Optional pre/post-tick adjustments. Commands run as the test runner against the GameTest
 * structure — gives spec authors a vanilla-syntax escape hatch without writing Java.
 *
 * @param preTickCommands commands run before the first tick
 * @param postRunCommands commands run after the recipe completes (or times out)
 * @param warmupTicks number of ticks elapsed before the runner starts comparing output
 */
public record LifecycleHooks(List<String> preTickCommands, List<String> postRunCommands, int warmupTicks) {

    public LifecycleHooks {
        preTickCommands = List.copyOf(preTickCommands);
        postRunCommands = List.copyOf(postRunCommands);
        if (warmupTicks < 0) {
            throw new IllegalArgumentException("warmupTicks must be >= 0, got " + warmupTicks);
        }
    }
}
