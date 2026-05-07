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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * L2 escape hatch — a pluggable strategy a consumer mod registers (via {@link
 * java.util.ServiceLoader}) to handle recipe types whose shape doesn't fit the JSON-only L1
 * path: probabilistic outputs, custom {@code RecipeInput} types, gas/heat/mana storage, etc.
 *
 * <p>One extension owns one {@link #recipeType()}. The harness consults it at four points:
 *
 * <ol>
 *   <li>{@link #injectInputs} — override how inputs are pushed into the machine.
 *   <li>{@link #validateOutput} — override the actual-vs-expected comparison.
 *   <li>{@link #tickBudgetOverride} — supply a recipe-specific tick cap.
 *   <li>{@link #resolveCustomBinding} — return a {@link CustomHandler} for each
 *       {@link CustomBinding} on the spec whose {@link CustomBinding#kind} the extension claims
 *       via {@link #supportedKinds}.
 * </ol>
 *
 * <p>Every method has a default that falls through to the harness's L1 behaviour, so an
 * extension can override only the hooks it needs.
 *
 * <p><b>Type parameter.</b> {@code R} is the concrete {@link Recipe} subtype this extension
 * handles. The harness performs the cast at the call site — by contract, every recipe handed
 * to an extension's hooks satisfies {@code recipe.getType()}'s registered name equal to
 * {@link #recipeType()}, so the cast is safe. Extensions that prefer not to deal with the
 * generic can declare {@code RecipeTestExtension<Recipe<?>>}.
 *
 * <h2>Discovery</h2>
 *
 * Implementations are loaded via {@code java.util.ServiceLoader}. Add a file at
 * {@code META-INF/services/dev.recipetest.api.RecipeTestExtension} listing one fully-qualified
 * class name per line. {@code ExtensionRegistry} scans during mod common-setup; a missing
 * services file is fine — extensions are opt-in.
 */
public interface RecipeTestExtension<R extends Recipe<?>> {

    /**
     * The recipe type this extension owns (e.g. {@code forestry:centrifuge}). Returned at
     * registration time so the registry can index by recipeType. Must be non-null and stable
     * for the JVM lifetime.
     */
    ResourceLocation recipeType();

    /**
     * Override input injection for non-standard {@code RecipeInput} shapes. Default falls
     * through to the L1 path: the runner uses the spec's {@link InputBinding} and the registered
     * {@link dev.recipetest.api.MachineSpec spec adapter} to push items / fluids / energy.
     *
     * <p>Implementations that override should call back into the harness via {@code ctx} for any
     * sub-operations they don't want to reimplement (item injection through the regular
     * {@code IItemHandler}, for example).
     */
    default void injectInputs(TestContext ctx, RecipeHolder<R> holder) {
        // Default: signal "fall through to L1". Runner detects the no-op return and proceeds
        // with its standard injection path.
    }

    /**
     * Override the runner's actual-vs-expected comparison. Returning a non-empty Optional
     * commits to the result and the runner skips its own diff. Returning empty falls through
     * to the L1 differ.
     *
     * @param actual the {@link IoSnapshot} the runner read from the machine
     */
    default Optional<TestResult> validateOutput(TestContext ctx, RecipeHolder<R> holder, IoSnapshot actual) {
        return Optional.empty();
    }

    /**
     * Per-recipe tick-budget override. Return {@code -1} to use the spec's
     * {@link MachineSpec#tickBudget()}; otherwise return a positive integer.
     *
     * <p>Useful when the recipe carries its processing time as a field (e.g.
     * {@code AbstractCookingRecipe.cookingTime}) that the spec author can't easily inline.
     */
    default int tickBudgetOverride(RecipeHolder<R> holder) {
        return -1;
    }

    /**
     * Resolve a {@link CustomBinding} to a runtime {@link CustomHandler}. The harness invokes
     * this once per binding at injection time, after the machine block entity has been placed.
     *
     * <p>Implementations are expected to claim the {@code kind} via {@link #supportedKinds};
     * the registry uses that set to detect "unresolved kind" at spec-validation time, before the
     * runner ever needs to call this method.
     *
     * @param kind the {@link CustomBinding#kind} from the spec
     * @param ref the {@link CustomBinding#ref} from the spec — opaque to the harness; the
     *     extension interprets it (typically as a tank/sub-component name)
     * @param be the placed machine block entity, or {@code null} if the BE hasn't materialised
     *     (treat as a soft refusal — return {@link Optional#empty})
     */
    default Optional<CustomHandler> resolveCustomBinding(ResourceLocation kind, String ref, BlockEntity be) {
        return Optional.empty();
    }

    /**
     * Set of {@link CustomBinding#kind} values this extension can resolve. Used by the harness
     * at spec-validation time to fail fast on unknown kinds, satisfying Phase 5 acceptance
     * criterion #4. Default empty — only override when the extension also implements
     * {@link #resolveCustomBinding}.
     */
    default Set<ResourceLocation> supportedKinds() {
        return Set.of();
    }

    /**
     * Per-output weights for distribution-mode validation. Keys are channel identifiers
     * (typically {@link ResourceLocation} item / fluid IDs serialised to string); values are
     * probabilities in {@code [0, 1]} that should sum to roughly 1 (the validator tolerates
     * small drift). Default empty — distribution-mode validation falls back to expecting
     * uniform weights across the recipe's declared outputs.
     */
    default Map<String, Double> weights(RecipeHolder<R> holder) {
        return Map.of();
    }
}
