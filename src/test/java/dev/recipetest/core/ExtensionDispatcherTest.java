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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.Diagnostics;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.RecipeTestExtension;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestResult;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.Test;

class ExtensionDispatcherTest {

    private static final ResourceLocation RT = ResourceLocation.fromNamespaceAndPath("test", "rt");

    @Test
    void emptyExtensionInjectionDefaultsToFallThrough() {
        RecipeTestExtension.InjectionDecision decision = ExtensionDispatcher.tryInject(Optional.empty(), null, null);
        assertEquals(RecipeTestExtension.InjectionDecision.FALL_THROUGH, decision);
    }

    @Test
    void injectionDecisionPropagatesFromExtension() {
        RecipeTestExtension<Recipe<?>> ext = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return RT;
            }

            @Override
            public InjectionDecision injectInputs(dev.recipetest.api.TestContext ctx, RecipeHolder<Recipe<?>> holder) {
                return InjectionDecision.HANDLED;
            }
        };

        RecipeTestExtension.InjectionDecision decision = ExtensionDispatcher.tryInject(Optional.of(ext), null, null);
        assertEquals(RecipeTestExtension.InjectionDecision.HANDLED, decision);
    }

    @Test
    void injectionExceptionFallsThroughInsteadOfPropagating() {
        RecipeTestExtension<Recipe<?>> ext = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return RT;
            }

            @Override
            public InjectionDecision injectInputs(dev.recipetest.api.TestContext ctx, RecipeHolder<Recipe<?>> holder) {
                throw new IllegalStateException("synthetic injection failure");
            }
        };

        RecipeTestExtension.InjectionDecision decision = ExtensionDispatcher.tryInject(Optional.of(ext), null, null);
        assertEquals(
                RecipeTestExtension.InjectionDecision.FALL_THROUGH,
                decision,
                "thrown extension errors must not abort the run");
    }

    @Test
    void injectionNullReturnFallsThrough() {
        RecipeTestExtension<Recipe<?>> ext = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return RT;
            }

            @Override
            public InjectionDecision injectInputs(dev.recipetest.api.TestContext ctx, RecipeHolder<Recipe<?>> holder) {
                return null;
            }
        };
        assertEquals(
                RecipeTestExtension.InjectionDecision.FALL_THROUGH,
                ExtensionDispatcher.tryInject(Optional.of(ext), null, null));
    }

    @Test
    void tickBudgetOverrideDefaultsToMinusOneWhenAbsent() {
        assertEquals(-1, ExtensionDispatcher.tryTickBudgetOverride(Optional.empty(), null));
    }

    @Test
    void tickBudgetOverrideForwardsExtensionValue() {
        RecipeTestExtension<Recipe<?>> ext = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return RT;
            }

            @Override
            public int tickBudgetOverride(RecipeHolder<Recipe<?>> holder) {
                return 200;
            }
        };
        assertEquals(200, ExtensionDispatcher.tryTickBudgetOverride(Optional.of(ext), null));
    }

    @Test
    void tickBudgetOverrideExceptionFallsBackToMinusOne() {
        RecipeTestExtension<Recipe<?>> ext = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return RT;
            }

            @Override
            public int tickBudgetOverride(RecipeHolder<Recipe<?>> holder) {
                throw new IllegalStateException("boom");
            }
        };
        assertEquals(-1, ExtensionDispatcher.tryTickBudgetOverride(Optional.of(ext), null));
    }

    @Test
    void validateOutputDefaultsToEmpty() {
        assertTrue(ExtensionDispatcher.tryValidateOutput(Optional.empty(), null, null, IoSnapshot.empty())
                .isEmpty());
    }

    @Test
    void validateOutputForwardsExtensionResult() {
        TestResult forced = new TestResult(
                ResourceLocation.fromNamespaceAndPath("test", "id"),
                RT,
                "test:rt.json",
                RunStatus.PASS,
                7,
                IoSnapshot.empty(),
                IoSnapshot.empty(),
                Optional.empty(),
                new Diagnostics(List.of(), 0L, List.of(), List.of()));
        RecipeTestExtension<Recipe<?>> ext = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return RT;
            }

            @Override
            public Optional<TestResult> validateOutput(
                    dev.recipetest.api.TestContext ctx, RecipeHolder<Recipe<?>> holder, IoSnapshot actual) {
                return Optional.of(forced);
            }
        };

        Optional<TestResult> result =
                ExtensionDispatcher.tryValidateOutput(Optional.of(ext), null, null, IoSnapshot.empty());
        assertTrue(result.isPresent());
        assertSame(forced, result.get());
    }

    @Test
    void validateOutputNullReturnFallsBackToEmpty() {
        RecipeTestExtension<Recipe<?>> ext = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return RT;
            }

            @Override
            public Optional<TestResult> validateOutput(
                    dev.recipetest.api.TestContext ctx, RecipeHolder<Recipe<?>> holder, IoSnapshot actual) {
                return null;
            }
        };
        assertTrue(ExtensionDispatcher.tryValidateOutput(Optional.of(ext), null, null, IoSnapshot.empty())
                .isEmpty());
    }

    @Test
    void validateOutputExceptionFallsBackToEmpty() {
        RecipeTestExtension<Recipe<?>> ext = new RecipeTestExtension<>() {
            @Override
            public ResourceLocation recipeType() {
                return RT;
            }

            @Override
            public Optional<TestResult> validateOutput(
                    dev.recipetest.api.TestContext ctx, RecipeHolder<Recipe<?>> holder, IoSnapshot actual) {
                throw new IllegalStateException("synthetic validation failure");
            }
        };
        assertTrue(ExtensionDispatcher.tryValidateOutput(Optional.of(ext), null, null, IoSnapshot.empty())
                .isEmpty());
    }
}
