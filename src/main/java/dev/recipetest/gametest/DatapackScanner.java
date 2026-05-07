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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.locating.IModFile;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Walks every loaded {@code IModFile} and extracts:
 *
 * <ol>
 *   <li>The set of {@code recipeType} values declared by spec files at
 *       {@code data/<modid>/recipe_test/machines/<name>.json}.
 *   <li>The set of {@code (recipeType, recipeId)} pairs whose {@code "type"} field matches one of
 *       those spec types — i.e. the recipes that have a registered harness spec.
 * </ol>
 *
 * <p><b>Why this lives here, not in {@code SpecLoader}.</b> {@code RegisterGameTestsEvent} fires
 * before any datapack is read by the server's {@code ResourceManager}, so {@code HarnessRegistry}
 * is empty when the dynamic GameTest generator needs to enumerate recipes. The scanner walks the
 * mod jars directly via NIO so it works at registration time without touching the server's
 * registries.
 *
 * <p>Datapack overrides applied at runtime (e.g. server-owner packs in {@code world/datapacks/})
 * are <em>not</em> visible to this scanner — they're served through the live
 * {@code ResourceManager}, not through {@code IModFile}. CI workflows running
 * {@code runGameTestServer} only see mod-bundled recipes; this is documented in {@code
 * docs/ci.md}.
 */
public final class DatapackScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Subdirectory under each {@code data/<ns>/} that holds harness spec JSON files. */
    private static final String SPECS_SUBDIR = "recipe_test/machines";

    /**
     * Vanilla 1.21+ recipe directory under each {@code data/<ns>/}. Pre-1.20.5 packs used
     * {@code recipes/} — this scanner targets the modern path because Phase 4 runs against
     * 21.1.213.
     */
    private static final String RECIPES_SUBDIR = "recipe";

    private DatapackScanner() {}

    /** Reference to a recipe whose type has a registered harness spec. */
    public record RecipeRef(ResourceLocation recipeType, ResourceLocation recipeId) {
        public RecipeRef {
            Objects.requireNonNull(recipeType, "recipeType");
            Objects.requireNonNull(recipeId, "recipeId");
        }
    }

    /**
     * Scan every loaded mod file. Returns refs in deterministic order
     * (sorted by recipeType then recipeId) so the resulting GameTest names are stable across
     * server starts — Phase 4 acceptance criterion #6 depends on this.
     */
    public static List<RecipeRef> scan() {
        List<Path> dataRoots = ModList.get().getModFiles().stream()
                .map(info -> safeFindResource(info.getFile(), "data"))
                .filter(java.util.Objects::nonNull)
                .toList();
        return scanRoots(dataRoots);
    }

    /**
     * Test seam: scan a fixed list of {@code data/} root directories. Each root corresponds to
     * the {@code data/} dir inside one mod jar. The production {@link #scan()} method assembles
     * this list from {@link ModList}.
     */
    static List<RecipeRef> scanRoots(List<Path> dataRoots) {
        Set<ResourceLocation> specTypes = new LinkedHashSet<>();
        for (Path root : dataRoots) {
            collectSpecTypes(root, specTypes);
        }
        if (specTypes.isEmpty()) {
            return List.of();
        }
        Map<ResourceLocation, ResourceLocation> recipeMatches = new LinkedHashMap<>();
        for (Path root : dataRoots) {
            collectRecipes(root, specTypes, recipeMatches);
        }
        List<RecipeRef> refs = new ArrayList<>(recipeMatches.size());
        recipeMatches.forEach((id, type) -> refs.add(new RecipeRef(type, id)));
        refs.sort((a, b) -> {
            int byType = a.recipeType().compareTo(b.recipeType());
            return byType != 0 ? byType : a.recipeId().compareTo(b.recipeId());
        });
        return refs;
    }

    // ---- spec discovery ----

    private static void collectSpecTypes(Path dataDir, Set<ResourceLocation> specTypes) {
        if (!Files.isDirectory(dataDir)) {
            return;
        }
        try (Stream<Path> namespaces = Files.list(dataDir)) {
            namespaces.filter(Files::isDirectory).forEach(nsDir -> {
                Path machinesDir = nsDir.resolve(SPECS_SUBDIR);
                if (!Files.isDirectory(machinesDir)) {
                    return;
                }
                walkJsonFiles(machinesDir, specPath -> readSpecRecipeType(specPath, specTypes));
            });
        } catch (IOException e) {
            LOGGER.warn("recipe_test: spec scan failed for {}: {}", dataDir, e.toString());
        }
    }

    private static void readSpecRecipeType(Path specPath, Set<ResourceLocation> specTypes) {
        JsonElement root = parseJson(specPath);
        if (root == null || !root.isJsonObject()) {
            return;
        }
        JsonObject obj = root.getAsJsonObject();
        JsonElement typeField = obj.get("recipeType");
        if (typeField == null || !typeField.isJsonPrimitive()) {
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(typeField.getAsString());
        if (rl != null) {
            specTypes.add(rl);
        }
    }

    // ---- recipe discovery ----

    private static void collectRecipes(
            Path dataDir, Set<ResourceLocation> specTypes, Map<ResourceLocation, ResourceLocation> matches) {
        if (!Files.isDirectory(dataDir)) {
            return;
        }
        try (Stream<Path> namespaces = Files.list(dataDir)) {
            namespaces.filter(Files::isDirectory).forEach(nsDir -> {
                Path recipesDir = nsDir.resolve(RECIPES_SUBDIR);
                if (!Files.isDirectory(recipesDir)) {
                    return;
                }
                String namespace = nsDir.getFileName().toString();
                walkJsonFiles(
                        recipesDir,
                        recipePath -> readRecipeMatch(namespace, recipesDir, recipePath, specTypes, matches));
            });
        } catch (IOException e) {
            LOGGER.warn("recipe_test: recipe scan failed for {}: {}", dataDir, e.toString());
        }
    }

    private static void readRecipeMatch(
            String namespace,
            Path recipesDir,
            Path recipePath,
            Set<ResourceLocation> specTypes,
            Map<ResourceLocation, ResourceLocation> matches) {
        JsonElement root = parseJson(recipePath);
        if (root == null || !root.isJsonObject()) {
            return;
        }
        JsonObject obj = root.getAsJsonObject();
        JsonElement typeField = obj.get("type");
        if (typeField == null || !typeField.isJsonPrimitive()) {
            return;
        }
        ResourceLocation typeRl = ResourceLocation.tryParse(typeField.getAsString());
        if (typeRl == null || !specTypes.contains(typeRl)) {
            return;
        }
        String relative = recipesDir.relativize(recipePath).toString().replace('\\', '/');
        if (!relative.endsWith(".json")) {
            return;
        }
        String idPath = relative.substring(0, relative.length() - ".json".length());
        ResourceLocation recipeId = ResourceLocation.tryBuild(namespace, idPath);
        if (recipeId == null) {
            return;
        }
        matches.putIfAbsent(recipeId, typeRl);
    }

    // ---- helpers ----

    /**
     * Resolve a top-level resource by name. {@link IModFile#findResource} requires the path
     * components to be split — passing {@code "data"} returns the {@code data/} root inside the
     * mod jar's filesystem.
     */
    private static @Nullable Path safeFindResource(IModFile file, String first) {
        try {
            return file.findResource(first);
        } catch (RuntimeException e) {
            // Some IModFile implementations throw on dummy/synthetic mods. Swallow — they have
            // no datapack contributions to scan.
            return null;
        }
    }

    private static void walkJsonFiles(Path dir, java.util.function.Consumer<Path> visitor) {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(visitor);
        } catch (IOException e) {
            LOGGER.warn("recipe_test: walk failed for {}: {}", dir, e.toString());
        }
    }

    private static @Nullable JsonElement parseJson(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader);
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.debug("recipe_test: failed to parse {}: {}", path, e.toString());
            return null;
        }
    }
}
