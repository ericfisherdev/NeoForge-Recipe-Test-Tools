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
import dev.recipetest.core.HarnessRegistry;
import dev.recipetest.core.RunSessionScheduler;
import dev.recipetest.spec.CapabilityProbe;
import dev.recipetest.spec.SpecLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(RecipeTestMod.MODID)
public final class RecipeTestMod {

    public static final String MODID = "recipe_test";

    private static final Logger LOGGER = LogUtils.getLogger();

    public RecipeTestMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(RunSessionScheduler.instance()::onServerTick);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("recipe_test: bootstrap ok");
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        var registries = event.getRegistryAccess();
        event.addListener(new SpecLoader(
                HarnessRegistry.instance(),
                rl -> registries
                        .registry(Registries.RECIPE_TYPE)
                        .map(reg -> reg.containsKey(rl))
                        .orElse(false),
                BuiltInRegistries.BLOCK::containsKey));
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        RecipeTestCommand.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        CapabilityProbe.probeAll(event.getServer().overworld());
    }
}
