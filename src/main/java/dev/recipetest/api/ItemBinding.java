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
import java.util.Optional;

/**
 * Maps recipe item ingredients (or results, when used in {@link OutputBinding}) onto a machine's
 * item-handler slots.
 *
 * @param capability the capability identifier the harness will query (e.g. {@code "ItemHandler"})
 * @param side which side of the block to query (defaults to {@link Side#INTERNAL})
 * @param slots explicit slot indices the binding covers
 * @param layout how recipe ingredients map onto {@code slots} (input bindings only)
 * @param primary slot index to compare against {@code recipe.getResultItem()} (output bindings)
 */
public record ItemBinding(
        String capability, Side side, List<Integer> slots, Optional<Layout> layout, Optional<Integer> primary) {

    public ItemBinding {
        slots = List.copyOf(slots);
    }
}
