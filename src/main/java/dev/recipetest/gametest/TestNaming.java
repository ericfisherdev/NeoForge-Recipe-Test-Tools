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
package dev.recipetest.gametest;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Encodes a (recipeType, recipeId) pair into a stable, identifier-safe GameTest test name.
 *
 * <p>GameTest test names are looked up by string equality and surfaced through Gradle's
 * {@code --tests} filter, which treats them as Java identifiers. We therefore restrict the output
 * to {@code [a-z0-9_.]} and route {@code /:- } through {@code _}. The encoding is
 * deterministic — the same input always yields the same output across reloads, which is the
 * stability guarantee Phase 4 acceptance criterion #6 depends on.
 *
 * <p>Format: {@code recipe_test.<typeNs>.<typePath>.<idNs>.<idPath>}.
 *
 * <p>Including the recipeId namespace as a separate segment keeps recipes from different mods
 * targeting the same recipe-type distinct (e.g. an addon mod could add carpenter recipes under its
 * own namespace; collapsing them with the host mod's namespace would silently overwrite each
 * other's test names).
 */
public final class TestNaming {

    /** Top-level prefix on every dynamic test name; matches {@code RecipeTestMod.MODID}. */
    public static final String PREFIX = "recipe_test";

    private TestNaming() {}

    /**
     * Build the canonical test name for the given recipeType/recipeId pair.
     *
     * @throws NullPointerException if either argument is null
     */
    public static String testName(ResourceLocation recipeType, ResourceLocation recipeId) {
        Objects.requireNonNull(recipeType, "recipeType");
        Objects.requireNonNull(recipeId, "recipeId");
        return PREFIX
                + '.'
                + sanitize(recipeType.getNamespace())
                + '.'
                + sanitize(recipeType.getPath())
                + '.'
                + sanitize(recipeId.getNamespace())
                + '.'
                + sanitize(recipeId.getPath());
    }

    /**
     * Replace every char outside {@code [a-z0-9_]} with {@code _}. Path separators ({@code /}),
     * resource-location separators ({@code :}), hyphens ({@code -}), spaces, and uppercase letters
     * all collapse to a stable lowercase form. Empty input returns {@code "_"} to keep the
     * dotted segments well-formed.
     */
    static String sanitize(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.isEmpty()) {
            return "_";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c + ('a' - 'A')));
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}
