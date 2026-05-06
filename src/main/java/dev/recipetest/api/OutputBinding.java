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
 * Recipe-output → machine-storage mapping. Mirrors {@link InputBinding} but is read by the
 * runner after the recipe completes to compare actual vs. expected output.
 */
public record OutputBinding(Optional<ItemBinding> items, Optional<FluidBinding> fluids, List<CustomBinding> custom) {

    public OutputBinding {
        java.util.Objects.requireNonNull(items, "OutputBinding.items Optional must not be null");
        java.util.Objects.requireNonNull(fluids, "OutputBinding.fluids Optional must not be null");
        java.util.Objects.requireNonNull(custom, "OutputBinding.custom must not be null");
        custom = List.copyOf(custom);
    }
}
