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
package dev.recipetest.spec;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.recipetest.RecipeTestMod;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.core.HarnessRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/**
 * Discovers, parses, validates, and registers {@code MachineSpec} JSON files from every loaded
 * datapack at {@code data/<modid>/recipe_test/machines/<name>.json}. Wired via
 * {@code AddReloadListenerEvent}; runs on every {@code /reload}.
 *
 * <p>For each file:
 *
 * <ol>
 *   <li>Parse via {@link MachineSpecCodec#CODEC}; codec errors surface as ERROR-severity issues
 *       with a JSON pointer to the offending field.
 *   <li>Validate via {@link SpecValidator#validate}; rules from {@code json-spec.md} produce
 *       {@link ValidationIssue}s.
 *   <li>If no ERROR-severity issues, insert into {@link HarnessRegistry}. WARN-severity issues
 *       are logged but don't block registration.
 *   <li>Errors and warnings are logged with the file's resource id so spec authors can locate
 *       the problem.
 * </ol>
 */
public final class SpecLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /**
     * Folder under {@code data/<namespace>/} the listener scans. The {@code SimpleJsonResourceReloadListener}
     * peels {@code .json} off the file names automatically.
     */
    public static final String FOLDER = "recipe_test/machines";

    private final HarnessRegistry registry;
    private final Predicate<ResourceLocation> recipeTypeKnown;
    private final Predicate<ResourceLocation> blockKnown;

    public SpecLoader(
            HarnessRegistry registry,
            Predicate<ResourceLocation> recipeTypeKnown,
            Predicate<ResourceLocation> blockKnown) {
        super(new Gson(), FOLDER);
        this.registry = registry;
        this.recipeTypeKnown = recipeTypeKnown;
        this.blockKnown = blockKnown;
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        registry.clear();
        int loaded = 0;
        int rejected = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                Optional<MachineSpec> spec = parse(id, entry.getValue());
                if (spec.isEmpty()) {
                    rejected++;
                    continue;
                }
                if (validateAndRegister(id, spec.get())) {
                    loaded++;
                } else {
                    rejected++;
                }
            } catch (RuntimeException ex) {
                LOGGER.error(
                        "[{}] {}: unexpected error loading spec: {}", RecipeTestMod.MODID, id, ex.getMessage(), ex);
                rejected++;
            }
        }
        LOGGER.info("[{}] reload complete: {} spec(s) registered, {} rejected", RecipeTestMod.MODID, loaded, rejected);
    }

    private Optional<MachineSpec> parse(ResourceLocation id, JsonElement json) {
        var result = MachineSpecCodec.CODEC.parse(JsonOps.INSTANCE, json);
        if (result.error().isPresent()) {
            LOGGER.error(
                    "[{}] {}: codec parse failed at {}",
                    RecipeTestMod.MODID,
                    id,
                    result.error().get().message());
            return Optional.empty();
        }
        return Optional.of(result.result().orElseThrow());
    }

    private boolean validateAndRegister(ResourceLocation id, MachineSpec spec) {
        List<ValidationIssue> issues = SpecValidator.validate(spec, recipeTypeKnown, blockKnown);
        for (ValidationIssue issue : issues) {
            switch (issue.severity()) {
                case ERROR -> LOGGER.error(
                        "[{}] {} ERROR at {}: {} — {}",
                        RecipeTestMod.MODID,
                        id,
                        issue.jsonPath(),
                        issue.message(),
                        issue.fixHint());
                case WARN -> LOGGER.warn(
                        "[{}] {} WARN at {}: {} — {}",
                        RecipeTestMod.MODID,
                        id,
                        issue.jsonPath(),
                        issue.message(),
                        issue.fixHint());
            }
        }
        if (!SpecValidator.isRegistryEligible(issues)) {
            return false;
        }
        registry.register(spec);
        return true;
    }
}
