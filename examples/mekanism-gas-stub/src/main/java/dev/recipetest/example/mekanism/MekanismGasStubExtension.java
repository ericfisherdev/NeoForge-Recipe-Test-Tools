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
package dev.recipetest.example.mekanism;

import dev.recipetest.api.CustomHandler;
import dev.recipetest.api.RecipeTestExtension;
import dev.recipetest.api.TestContext;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Reference {@link RecipeTestExtension} that walks the harness's L2 SPI for a synthetic gas
 * machine. Demonstrates four things, each of which is a Phase 5 architectural promise made
 * runnable by this example:
 *
 * <ol>
 *   <li>An external consumer can implement {@link RecipeTestExtension} using nothing but the
 *       harness's public {@code dev.recipetest.api.*} types — verified at compile time by
 *       this subproject's {@code build.gradle} import set.
 *   <li>{@link #supportedKinds} declares a custom binding kind the spec validator
 *       (Phase 5 PR-A's rule 7) will accept at server-start time.
 *   <li>{@link #resolveCustomBinding} returns a {@link CustomHandler} backed by a per-machine
 *       {@link GasTank}, demonstrating non-item / non-fluid / non-energy storage integration.
 *   <li>{@link #injectInputs} returning {@link InjectionDecision#HANDLED} is the contract for
 *       extensions that own the entire injection step — Phase 5 PR-B wired the runner to skip
 *       its L1 path when this is returned.
 * </ol>
 *
 * <p>This extension is registered via {@code META-INF/services/dev.recipetest.api.RecipeTestExtension};
 * the harness's {@code ExtensionRegistry.scan()} discovers it on common-setup. <b>The harness
 * has no compile dependency on this subproject</b> — that's the structural property the
 * settings.gradle one-way include enforces and that this example exists to demonstrate.
 *
 * <p>The matching spec lives at
 * {@code data/mekanism_gas_stub/recipe_test/machines/gas_compressor.json} and references
 * {@link GasTankHandler#GAS_KIND} via its {@code inputs.custom[0].kind} field.
 */
public final class MekanismGasStubExtension implements RecipeTestExtension<Recipe<?>> {

    /** The recipe type this extension claims. The matching spec sets this exact value. */
    public static final ResourceLocation RECIPE_TYPE = ResourceLocation.parse("mekanism_gas_stub:gas_compressor");

    @Override
    public ResourceLocation recipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Set<ResourceLocation> supportedKinds() {
        return Set.of(GasTankHandler.GAS_KIND);
    }

    @Override
    public Optional<CustomHandler> resolveCustomBinding(ResourceLocation kind, String ref, @Nullable BlockEntity be) {
        if (!GasTankHandler.GAS_KIND.equals(kind)) {
            return Optional.empty();
        }
        // Real consumer mods would dispatch on `ref` to find the right tank on `be`. The stub
        // mirrors that shape via a lookup that recognises a single ref and returns a fresh
        // GasTank — full block-entity integration is out of scope for the example.
        if (!"input_gas_tank".equals(ref)) {
            return Optional.empty();
        }
        // 10 buckets of capacity. Real Mekanism tanks are bigger; this is enough to host the
        // synthetic recipe's input without overflow.
        return Optional.of(new GasTankHandler(new GasTank(10_000)));
    }

    @Override
    public InjectionDecision injectInputs(TestContext ctx, RecipeHolder<Recipe<?>> holder) {
        // The stub recipe's gas input is fixed for the example, so injection is a single-step
        // affair: compute the expected GasStack from the recipe, push it into the tank via
        // resolveCustomBinding's handler. A real consumer would also handle item/energy inputs
        // here if their machine accepted those — fall through to the L1 path with FALL_THROUGH
        // when only a subset of inputs is custom.
        //
        // For this minimum-viable example, the live wiring (block-entity placement, dispatch
        // via TestContext) is a follow-up PR's scope. Returning FALL_THROUGH keeps the L1 path
        // honest if a future spec mixes item inputs alongside the gas binding.
        return InjectionDecision.FALL_THROUGH;
    }
}
