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
import dev.recipetest.api.RecipeTestExtension;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Process-wide registry of L2 {@link RecipeTestExtension} implementations. Indexed by both
 * {@link RecipeTestExtension#recipeType()} (for runner dispatch) and by
 * {@link RecipeTestExtension#supportedKinds()} (for spec-time validation of
 * {@link dev.recipetest.api.CustomBinding#kind} values).
 *
 * <p><b>Lifecycle.</b> {@link #scan} is called once at mod common-setup. It walks
 * {@link ServiceLoader} for classes registered under
 * {@code META-INF/services/dev.recipetest.api.RecipeTestExtension} and stores them in an
 * immutable snapshot. Extensions live in mod jars that aren't reloaded after game start, so a
 * second scan would see the same set; {@link #scan} guards against double-scan via a flag.
 *
 * <p><b>Thread safety.</b> The backing maps are wrapped in an {@link AtomicReference} to a
 * single immutable {@link Snapshot}, so reads from any thread (validator on reload, runner on
 * server tick) see a consistent view. Tests can swap a synthetic snapshot via
 * {@link #replaceForTesting} without involving {@link ServiceLoader}.
 */
public final class ExtensionRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ExtensionRegistry INSTANCE = new ExtensionRegistry();

    /**
     * Initial snapshot — empty. Stays empty until {@link #scan} runs at common-setup, at which
     * point it's atomically replaced with the discovered extensions. Reads taken before
     * {@code scan()} (notably the validator on a datapack reload before common-setup completes)
     * see an empty registry, which is correct: nothing has been registered yet.
     */
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    private boolean scanned;

    private ExtensionRegistry() {}

    public static ExtensionRegistry instance() {
        return INSTANCE;
    }

    /**
     * Discover extensions via {@link ServiceLoader} and publish the snapshot. Idempotent —
     * subsequent calls log and return without re-scanning. Call from {@code FMLCommonSetupEvent}.
     */
    public synchronized void scan() {
        if (scanned) {
            LOGGER.debug("recipe_test: ExtensionRegistry already scanned, skipping");
            return;
        }
        // Use the stream API so a single faulty provider — bad service-file entry, NoClassDefFoundError
        // on init, exception in a no-arg constructor — only logs a warning instead of aborting
        // discovery of every later provider in the chain.
        ServiceLoader<RecipeTestExtension> loader = ServiceLoader.load(RecipeTestExtension.class);
        List<RecipeTestExtension<?>> discovered = new java.util.ArrayList<>();
        for (ServiceLoader.Provider<RecipeTestExtension> provider :
                (Iterable<ServiceLoader.Provider<RecipeTestExtension>>) loader.stream()::iterator) {
            try {
                discovered.add(provider.get());
            } catch (ServiceConfigurationError | RuntimeException e) {
                LOGGER.warn(
                        "recipe_test: skipping RecipeTestExtension provider {} — {}: {}",
                        provider.type().getName(),
                        e.getClass().getSimpleName(),
                        e.getMessage());
            }
        }
        snapshot.set(Snapshot.from(discovered));
        scanned = true;
        LOGGER.info(
                "recipe_test: ExtensionRegistry loaded {} extension(s){}",
                discovered.size(),
                discovered.isEmpty() ? "" : ":");
        for (RecipeTestExtension<?> ext : discovered) {
            LOGGER.info(
                    "recipe_test:   {} → {}", ext.recipeType(), ext.getClass().getName());
        }
    }

    /** Test seam — install a fixed set of extensions, bypassing {@link ServiceLoader}. */
    public synchronized void replaceForTesting(Collection<RecipeTestExtension<?>> extensions) {
        snapshot.set(Snapshot.from(extensions));
        scanned = true;
    }

    /** Test seam — reset to the empty snapshot and unmark "scanned" so the next call to
     *  {@link #scan} runs again. */
    public synchronized void resetForTesting() {
        snapshot.set(Snapshot.EMPTY);
        scanned = false;
    }

    /** Look up the extension that owns {@code recipeType}, if any. */
    public Optional<RecipeTestExtension<?>> forRecipeType(ResourceLocation recipeType) {
        Objects.requireNonNull(recipeType, "recipeType");
        return Optional.ofNullable(currentSnapshot().byRecipeType.get(recipeType));
    }

    /** True if some registered extension claims {@code kind} via {@code supportedKinds()}.
     *  Used by {@code SpecValidator} to fail fast on unresolved {@code CustomBinding.kind}. */
    public boolean isKindSupported(ResourceLocation kind) {
        Objects.requireNonNull(kind, "kind");
        return currentSnapshot().supportedKinds.contains(kind);
    }

    /** Predicate variant for callers that want a {@link java.util.function.Predicate} they can
     *  pass to {@code SpecValidator.validate(...)} without coupling the validator to this
     *  class. */
    public java.util.function.Predicate<ResourceLocation> kindKnownPredicate() {
        return this::isKindSupported;
    }

    /** Snapshot of all registered extensions in registration order. Order is the
     *  ServiceLoader iteration order, which is unspecified — callers should not depend on it. */
    public Collection<RecipeTestExtension<?>> all() {
        return currentSnapshot().byRecipeType.values();
    }

    /**
     * Returns the current snapshot, asserting non-null. {@code AtomicReference.get()} has a
     * {@code @Nullable} signature, but we seed with {@link Snapshot#EMPTY} and every writer
     * publishes a non-null value, so this can never return null at runtime — centralising the
     * assertion keeps NullAway happy at every call site.
     */
    private Snapshot currentSnapshot() {
        return Objects.requireNonNull(snapshot.get(), "snapshot is null — should be impossible");
    }

    /** Immutable view of the registry. Swapped atomically by {@link #scan} so readers always
     *  see a consistent (recipeType-map, kinds-set) pair. */
    private record Snapshot(
            Map<ResourceLocation, RecipeTestExtension<?>> byRecipeType, Set<ResourceLocation> supportedKinds) {

        static final Snapshot EMPTY = new Snapshot(Map.of(), Set.of());

        Snapshot {
            // Map.copyOf produces an unmodifiable Map with unspecified iteration order — using
            // it here would silently discard the LinkedHashMap registration order this snapshot
            // is documented to preserve. Wrap a fresh LinkedHashMap copy instead so all() and
            // byRecipeType iteration mirror ServiceLoader's discovery sequence.
            byRecipeType = Collections.unmodifiableMap(new LinkedHashMap<>(byRecipeType));
            supportedKinds = Set.copyOf(supportedKinds);
        }

        static Snapshot from(Collection<RecipeTestExtension<?>> extensions) {
            Map<ResourceLocation, RecipeTestExtension<?>> byType = new LinkedHashMap<>();
            Set<ResourceLocation> kinds = new HashSet<>();
            for (RecipeTestExtension<?> ext : extensions) {
                ResourceLocation rt =
                        Objects.requireNonNull(ext.recipeType(), "RecipeTestExtension.recipeType() must not be null");
                if (byType.containsKey(rt)) {
                    LOGGER.warn(
                            "recipe_test: duplicate RecipeTestExtension for {} — keeping first ({}); ignoring {}",
                            rt,
                            byType.get(rt).getClass().getName(),
                            ext.getClass().getName());
                    continue;
                }
                byType.put(rt, ext);
                kinds.addAll(ext.supportedKinds());
            }
            return new Snapshot(byType, kinds);
        }
    }
}
