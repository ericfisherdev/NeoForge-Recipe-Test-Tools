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
package dev.recipetest;

import com.mojang.logging.LogUtils;
import dev.recipetest.command.RecipeTestCommand;
import dev.recipetest.compat.forestry.ForestryCarpenterAdapter;
import dev.recipetest.core.ExtensionRegistry;
import dev.recipetest.core.HarnessConfig;
import dev.recipetest.core.HarnessRegistry;
import dev.recipetest.core.RecipeAdapters;
import dev.recipetest.core.RunSessionScheduler;
import dev.recipetest.core.TickScheduler;
import dev.recipetest.gametest.DynamicGameTestGenerator;
import dev.recipetest.gametest.JunitXmlReporter;
import dev.recipetest.spec.CapabilityProbe;
import dev.recipetest.spec.SpecLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(RecipeTestMod.MODID)
public final class RecipeTestMod {

    public static final String MODID = "recipe_test";

    private static final Logger LOGGER = LogUtils.getLogger();

    public RecipeTestMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, HarnessConfig.SPEC);
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onRegisterGameTests);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(RunSessionScheduler.instance()::onServerTick);
        NeoForge.EVENT_BUS.addListener(TickScheduler.instance()::onServerTick);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("recipe_test: bootstrap ok");
        // Compat adapters are registered unconditionally — each adapter's static-init guards
        // itself against the target mod being absent. ForestryCarpenterAdapter, for example,
        // returns false from appliesTo() when ICarpenterRecipe isn't on the classpath, so
        // having it in the registry is a no-op when Forestry isn't loaded.
        RecipeAdapters.register(new ForestryCarpenterAdapter());
        // Discover L2 extensions via ServiceLoader. Idempotent — safe even when common-setup
        // fires more than once. Must run before SpecLoader's first apply() so unresolved-kind
        // validation has the full extension set to consult.
        ExtensionRegistry.instance().scan();
    }

    private void onRegisterGameTests(RegisterGameTestsEvent event) {
        // Install the JUnit XML reporter before tests register so it captures every pass/fail.
        // The install is idempotent — safe even when this event fires twice during dev reloads.
        JunitXmlReporter.install();
        // Register the @GameTestGenerator-annotated method on DynamicGameTestGenerator. Vanilla's
        // GameTestRegistry will invoke the generator and pull in every dynamic recipe test the
        // datapack scanner discovered.
        for (var method : DynamicGameTestGenerator.class.getDeclaredMethods()) {
            event.register(method);
        }
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        var registries = event.getRegistryAccess();
        event.addListener(new SpecLoader(
                HarnessRegistry.instance(),
                rl -> registries
                        .registry(Registries.RECIPE_TYPE)
                        .map(reg -> reg.containsKey(rl))
                        .orElse(false),
                BuiltInRegistries.BLOCK::containsKey,
                ExtensionRegistry.instance().kindKnownPredicate()));
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        RecipeTestCommand.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        CapabilityProbe.probeAll(event.getServer().overworld());
    }
}
