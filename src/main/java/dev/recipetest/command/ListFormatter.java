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
package dev.recipetest.command;

import dev.recipetest.api.InputBinding;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.OutputBinding;

/**
 * Pure formatting helpers used by {@link ListSubcommand}. Extracted so we can unit-test the
 * string-building logic without standing up a {@code CommandSourceStack} / Minecraft runtime.
 */
final class ListFormatter {

    private ListFormatter() {}

    static String summarizeInputs(MachineSpec spec) {
        InputBinding b = spec.inputs();
        StringBuilder sb = new StringBuilder();
        b.items().ifPresent(it -> sb.append("items[").append(it.slots().size()).append(" slots] "));
        b.fluids().ifPresent(f -> sb.append("fluids[").append(f.tanks().size()).append(" tanks] "));
        if (!b.custom().isEmpty()) {
            sb.append("custom[").append(b.custom().size()).append("] ");
        }
        return sb.toString().stripTrailing();
    }

    static String summarizeOutputs(MachineSpec spec) {
        OutputBinding b = spec.outputs();
        StringBuilder sb = new StringBuilder();
        b.items().ifPresent(it -> sb.append("items[").append(it.slots().size()).append(" slots] "));
        b.fluids().ifPresent(f -> sb.append("fluids[").append(f.tanks().size()).append(" tanks] "));
        if (!b.custom().isEmpty()) {
            sb.append("custom[").append(b.custom().size()).append("] ");
        }
        return sb.toString().stripTrailing();
    }
}
