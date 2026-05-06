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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.CustomBinding;
import dev.recipetest.api.InputBinding;
import dev.recipetest.api.ItemBinding;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.OutputBinding;
import dev.recipetest.api.Side;
import dev.recipetest.api.TickBudget;
import dev.recipetest.api.ValidationPolicy;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
class HarnessRegistryTest {

    @BeforeEach
    void resetRegistry() {
        HarnessRegistry.instance().clear();
    }

    @Test
    @DisplayName("instance() returns the same singleton")
    void singletonIdentity() {
        assertSame(HarnessRegistry.instance(), HarnessRegistry.instance());
    }

    @Test
    @DisplayName("register / byRecipeType round-trip")
    void registerAndLookup() {
        MachineSpec spec = makeSpec("examplemod:grinder", "examplemod:grinder");
        HarnessRegistry.instance().register(spec);

        Optional<MachineSpec> found = HarnessRegistry.instance().byRecipeType(spec.recipeType());
        assertTrue(found.isPresent());
        assertSame(spec, found.get());
        assertEquals(1, HarnessRegistry.instance().size());
    }

    @Test
    @DisplayName("byRecipeType returns empty for unknown key")
    void byRecipeTypeMiss() {
        assertFalse(HarnessRegistry.instance().byRecipeType(rl("nope:nope")).isPresent());
    }

    @Test
    @DisplayName("register replaces an existing entry with the same recipeType")
    void registerReplaces() {
        MachineSpec first = makeSpec("examplemod:grinder", "examplemod:grinder_v1");
        MachineSpec second = makeSpec("examplemod:grinder", "examplemod:grinder_v2");
        HarnessRegistry.instance().register(first);
        HarnessRegistry.instance().register(second);
        assertEquals(1, HarnessRegistry.instance().size());
        assertSame(
                second,
                HarnessRegistry.instance().byRecipeType(first.recipeType()).orElseThrow());
    }

    @Test
    @DisplayName("register rejects null spec")
    void registerNullRejected() {
        assertThrows(
                NullPointerException.class, () -> HarnessRegistry.instance().register(null));
    }

    @Test
    @DisplayName("all() returns immutable snapshot")
    void allIsImmutable() {
        HarnessRegistry.instance().register(makeSpec("a:x", "a:x"));
        var snapshot = HarnessRegistry.instance().all();
        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    }

    @Test
    @DisplayName("byModid() groups specs by namespace, alphabetically; paths within sorted by path")
    void byModidGrouping() {
        HarnessRegistry.instance().register(makeSpec("zeta:c", "zeta:c"));
        HarnessRegistry.instance().register(makeSpec("alpha:b", "alpha:b"));
        HarnessRegistry.instance().register(makeSpec("alpha:a", "alpha:a"));
        HarnessRegistry.instance().register(makeSpec("beta:x", "beta:x"));

        SequencedMap<String, List<MachineSpec>> grouped =
                HarnessRegistry.instance().byModid();
        assertNotNull(grouped);
        assertEquals(List.of("alpha", "beta", "zeta"), List.copyOf(grouped.sequencedKeySet()));

        List<MachineSpec> alpha = grouped.get("alpha");
        assertEquals(2, alpha.size());
        assertEquals("a", alpha.get(0).recipeType().getPath(), "alphabetical within group");
        assertEquals("b", alpha.get(1).recipeType().getPath());
    }

    @Test
    @DisplayName("clear() drops all entries")
    void clearEmptiesRegistry() {
        HarnessRegistry.instance().register(makeSpec("a:x", "a:x"));
        assertEquals(1, HarnessRegistry.instance().size());
        HarnessRegistry.instance().clear();
        assertEquals(0, HarnessRegistry.instance().size());
        assertTrue(HarnessRegistry.instance().all().isEmpty());
    }

    @Test
    @DisplayName("replaceAll swaps the entire snapshot in one publish")
    void replaceAllSwaps() {
        HarnessRegistry.instance().register(makeSpec("old:one", "old:one"));
        HarnessRegistry.instance().register(makeSpec("old:two", "old:two"));
        assertEquals(2, HarnessRegistry.instance().size());

        MachineSpec a = makeSpec("new:a", "new:a");
        MachineSpec b = makeSpec("new:b", "new:b");
        HarnessRegistry.instance().replaceAll(java.util.Map.of(a.recipeType(), a, b.recipeType(), b));

        assertEquals(2, HarnessRegistry.instance().size());
        assertTrue(HarnessRegistry.instance().byRecipeType(rl("new:a")).isPresent());
        assertTrue(HarnessRegistry.instance().byRecipeType(rl("new:b")).isPresent());
        assertFalse(HarnessRegistry.instance().byRecipeType(rl("old:one")).isPresent());
    }

    @Test
    @DisplayName("replaceAll with empty map empties the registry")
    void replaceAllEmpty() {
        HarnessRegistry.instance().register(makeSpec("a:x", "a:x"));
        HarnessRegistry.instance().replaceAll(java.util.Map.of());
        assertEquals(0, HarnessRegistry.instance().size());
    }

    @Test
    @DisplayName("replaceAll rejects null map")
    void replaceAllNullRejected() {
        assertThrows(
                NullPointerException.class, () -> HarnessRegistry.instance().replaceAll(null));
    }

    @Test
    @DisplayName("replaceAll rejects map where key != spec.recipeType() and leaves prior snapshot intact")
    void replaceAllKeyMismatchRejected() {
        // Seed with a known entry so we can assert the throw path doesn't publish.
        MachineSpec seed = makeSpec("seed:keep", "seed:keep");
        HarnessRegistry.instance().register(seed);
        assertEquals(1, HarnessRegistry.instance().size());

        MachineSpec spec = makeSpec("real:type", "real:type");
        ResourceLocation wrongKey = rl("wrong:key");
        java.util.Map<ResourceLocation, MachineSpec> bad = java.util.Map.of(wrongKey, spec);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> HarnessRegistry.instance().replaceAll(bad));
        assertTrue(ex.getMessage().contains("wrong:key"));
        assertTrue(ex.getMessage().contains("real:type"));

        // Atomicity: seed must still be present, the throwing replaceAll never published.
        assertEquals(1, HarnessRegistry.instance().size());
        assertSame(
                seed, HarnessRegistry.instance().byRecipeType(seed.recipeType()).orElseThrow());
        assertFalse(HarnessRegistry.instance().byRecipeType(rl("wrong:key")).isPresent());
        assertFalse(HarnessRegistry.instance().byRecipeType(rl("real:type")).isPresent());
    }

    @Test
    @DisplayName("replaceAll rejects null spec value and leaves prior snapshot intact")
    void replaceAllNullValueRejected() {
        MachineSpec seed = makeSpec("seed:keep", "seed:keep");
        HarnessRegistry.instance().register(seed);
        assertEquals(1, HarnessRegistry.instance().size());

        java.util.Map<ResourceLocation, MachineSpec> bad = new java.util.HashMap<>();
        bad.put(rl("a:x"), null);
        assertThrows(
                NullPointerException.class, () -> HarnessRegistry.instance().replaceAll(bad));

        // Atomicity: seed must still be present, the throwing replaceAll never published.
        assertEquals(1, HarnessRegistry.instance().size());
        assertSame(
                seed, HarnessRegistry.instance().byRecipeType(seed.recipeType()).orElseThrow());
        assertFalse(HarnessRegistry.instance().byRecipeType(rl("a:x")).isPresent());
    }

    @Test
    @DisplayName("concurrent register() calls do not lose updates (CAS loop)")
    void concurrentRegisterDoesNotLoseUpdates() throws InterruptedException {
        int threads = 8;
        int perThread = 25;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        try {
            for (int t = 0; t < threads; t++) {
                int threadIndex = t;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            String id = "mod_" + threadIndex + ":spec_" + i;
                            HarnessRegistry.instance().register(makeSpec(id, id));
                        }
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(
                    done.await(10, java.util.concurrent.TimeUnit.SECONDS), "register threads did not finish in time");
        } finally {
            pool.shutdownNow();
        }
        // Without the CAS loop, racing read-modify-write on a volatile field would lose updates
        // and the size would be < threads*perThread. With the AtomicReference.getAndUpdate path
        // every register() retries until it observes its own write; the final size must be exact.
        assertEquals(threads * perThread, HarnessRegistry.instance().size());
    }

    private static MachineSpec makeSpec(String recipeType, String block) {
        return new MachineSpec(
                1,
                rl(recipeType),
                rl(block),
                Optional.empty(),
                List.of(),
                new InputBinding(
                        Optional.of(new ItemBinding(
                                "ItemHandler", Side.INTERNAL, List.of(0), Optional.empty(), Optional.empty())),
                        Optional.empty(),
                        List.<CustomBinding>of()),
                new OutputBinding(
                        Optional.of(new ItemBinding(
                                "ItemHandler", Side.INTERNAL, List.of(1), Optional.empty(), Optional.empty())),
                        Optional.empty(),
                        List.<CustomBinding>of()),
                Optional.empty(),
                TickBudget.AUTO,
                ValidationPolicy.DEFAULT,
                Optional.empty());
    }

    private static ResourceLocation rl(String s) {
        return ResourceLocation.parse(s);
    }
}
