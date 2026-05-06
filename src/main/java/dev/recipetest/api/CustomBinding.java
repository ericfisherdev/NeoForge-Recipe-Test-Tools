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

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * L2 escape-hatch binding handed off to a registered {@code RecipeTestExtension} matched by
 * {@link #kind}. The {@link #ref} is an opaque string the extension interprets — typically a
 * named tank or sub-component on the machine BE.
 *
 * @param kind extension identifier (e.g. {@code "mekanism:gas"})
 * @param ref opaque reference string the extension consumes (e.g. {@code "input_gas_tank"})
 */
public record CustomBinding(ResourceLocation kind, String ref) {

    public CustomBinding {
        Objects.requireNonNull(kind, "CustomBinding.kind must not be null");
        Objects.requireNonNull(ref, "CustomBinding.ref must not be null");
        if (ref.isBlank()) {
            throw new IllegalArgumentException("CustomBinding.ref must not be blank");
        }
    }
}
