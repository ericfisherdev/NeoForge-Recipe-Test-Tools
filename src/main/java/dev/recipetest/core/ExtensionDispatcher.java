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

import com.mojang.logging.LogUtils;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.RecipeTestExtension;
import dev.recipetest.api.TestContext;
import dev.recipetest.api.TestResult;
import java.util.Optional;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.slf4j.Logger;

/**
 * Centralises the unchecked-cast bookkeeping needed to invoke {@link RecipeTestExtension} hooks
 * from a runner that holds a {@code RecipeHolder<?>} of unknown type-parameter. By contract the
 * cast is safe — every recipe handed to the dispatcher's caller has a {@code RecipeType} whose
 * registered name equals the extension's {@link RecipeTestExtension#recipeType()} — but Java's
 * generics are erased so the cast is necessary, and isolating it here keeps {@link
 * RecipeTestRunner} free of {@code @SuppressWarnings} clutter.
 *
 * <p><b>Misbehaving extensions are isolated.</b> Each call wraps the extension invocation in a
 * try/catch so a runtime exception only logs and returns the fall-through default — the harness
 * doesn't propagate a single bad extension's failure as the run's failure mode. Returning the
 * fall-through default means the runner's L1 path runs as if no extension were registered.
 */
public final class ExtensionDispatcher {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ExtensionDispatcher() {}

    /**
     * Ask the extension whether it wants to handle injection. Returns {@link
     * RecipeTestExtension.InjectionDecision#FALL_THROUGH} when no extension is registered, when
     * the extension's hook returns null, or when it throws.
     */
    public static RecipeTestExtension.InjectionDecision tryInject(
            Optional<RecipeTestExtension<?>> extension, TestContext ctx, RecipeHolder<?> holder) {
        if (extension.isEmpty()) {
            return RecipeTestExtension.InjectionDecision.FALL_THROUGH;
        }
        RecipeTestExtension<?> ext = extension.get();
        try {
            RecipeTestExtension.InjectionDecision decision = unchecked(ext).injectInputs(ctx, uncheckedHolder(holder));
            return decision == null ? RecipeTestExtension.InjectionDecision.FALL_THROUGH : decision;
        } catch (RuntimeException e) {
            LOGGER.warn(
                    "recipe_test: extension {} threw from injectInputs — falling through: {}",
                    ext.getClass().getName(),
                    e.toString());
            return RecipeTestExtension.InjectionDecision.FALL_THROUGH;
        }
    }

    /**
     * Ask the extension for a per-recipe tick-budget override. Returns {@code -1} (the SPI's
     * "use spec value" sentinel) when no extension is registered or the extension declines.
     */
    public static int tryTickBudgetOverride(Optional<RecipeTestExtension<?>> extension, RecipeHolder<?> holder) {
        if (extension.isEmpty()) {
            return -1;
        }
        RecipeTestExtension<?> ext = extension.get();
        try {
            return unchecked(ext).tickBudgetOverride(uncheckedHolder(holder));
        } catch (RuntimeException e) {
            LOGGER.warn(
                    "recipe_test: extension {} threw from tickBudgetOverride — using spec value: {}",
                    ext.getClass().getName(),
                    e.toString());
            return -1;
        }
    }

    /**
     * Ask the extension to fully validate the actual snapshot. Empty Optional means "fall
     * through to the L1 differ" — the runner builds its own {@link TestResult}. A non-empty
     * Optional commits to that result and the runner skips its L1 differ entirely.
     */
    public static Optional<TestResult> tryValidateOutput(
            Optional<RecipeTestExtension<?>> extension, TestContext ctx, RecipeHolder<?> holder, IoSnapshot actual) {
        if (extension.isEmpty()) {
            return Optional.empty();
        }
        RecipeTestExtension<?> ext = extension.get();
        try {
            Optional<TestResult> result = unchecked(ext).validateOutput(ctx, uncheckedHolder(holder), actual);
            return result == null ? Optional.empty() : result;
        } catch (RuntimeException e) {
            LOGGER.warn(
                    "recipe_test: extension {} threw from validateOutput — using L1 differ: {}",
                    ext.getClass().getName(),
                    e.toString());
            return Optional.empty();
        }
    }

    // ---- raw-type adapters ----

    /** Cast the wildcard extension to its raw form. Safe by contract — see class javadoc. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RecipeTestExtension unchecked(RecipeTestExtension<?> ext) {
        return (RecipeTestExtension) ext;
    }

    /** Same contract-driven cast for the holder. The runtime check happens at the runner's
     *  recipe-type-mismatch guard before this code is ever reached. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RecipeHolder uncheckedHolder(RecipeHolder<?> holder) {
        return (RecipeHolder) holder;
    }
}
