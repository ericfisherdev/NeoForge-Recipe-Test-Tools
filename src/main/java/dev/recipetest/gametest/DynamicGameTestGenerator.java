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

import com.mojang.logging.LogUtils;
import dev.recipetest.RecipeTestMod;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestContext;
import dev.recipetest.api.TestResult;
import dev.recipetest.api.TickBudget;
import dev.recipetest.core.HarnessRegistry;
import dev.recipetest.core.RecipeAdapter;
import dev.recipetest.core.RecipeAdapters;
import dev.recipetest.core.RecipeTestRunner;
import dev.recipetest.core.RunSessionScheduler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Discovers spec/recipe pairs from loaded mod jars at GameTest registration time and emits one
 * {@link TestFunction} per pair via a {@link GameTestGenerator}-annotated method.
 *
 * <p><b>Why a generator and not {@code @GameTest} methods.</b> {@code @GameTest} requires a method
 * for every test, which is the exact friction Phase 4 set out to remove. {@link
 * GameTestGenerator} lets us produce the test list dynamically — the generator method runs once at
 * registration and we return however many {@link TestFunction}s the scanner found.
 *
 * <p><b>What the generated test does.</b> At execution time, each TestFunction receives a {@link
 * GameTestHelper}. The handler resolves the {@link MachineSpec} (now available — by execution
 * time the server has loaded datapacks), constructs a {@link RecipeTestRunner}, submits it to the
 * existing {@link RunSessionScheduler}, and uses {@link GameTestHelper#succeedWhen} to wait for
 * the runner to publish a {@link TestResult}. PASS marks the test passed; non-PASS throws a
 * {@link GameTestAssertException} carrying the diff/diagnostics so the harness's failure data
 * surfaces in test reports.
 *
 * <p>The class is annotated {@link GameTestHolder} with {@code "recipe_test"} so NeoForge's
 * namespace filter ({@code -Dneoforge.enabledGameTestNamespaces=...}) treats every dynamic test
 * as belonging to this mod, regardless of which mod owns the underlying recipe.
 */
@GameTestHolder(RecipeTestMod.MODID)
@PrefixGameTestTemplate(false)
public final class DynamicGameTestGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Structure shipped at {@code data/recipe_test/structure/empty5.nbt}. Every dynamic test
     * loads the same template — the harness places the machine itself inside the empty region.
     */
    private static final String STRUCTURE_NAME = RecipeTestMod.MODID + ":empty5";

    /**
     * Default batch — TestFunction's {@code batchName} groups tests for parallel/serial scheduling
     * inside the GameTest framework. Single batch keeps execution serial, which the harness
     * relies on (only one {@link RecipeTestRunner} should hold the test region at a time).
     */
    private static final String BATCH = "recipe_test_dynamic";

    /**
     * Slack added to {@code timeoutTicks} on top of the spec's tick budget. Covers warmup phase,
     * pre-tick command latency, and post-tick verification. 60 ticks (3 seconds at 20 TPS) is
     * generous enough for typical machines without dragging out CI time.
     */
    private static final int TIMEOUT_BUFFER = 60;

    /** Place the machine in the middle of the 5×5×5 empty region — relative coords (2, 1, 2). */
    private static final BlockPos MACHINE_POS = new BlockPos(2, 1, 2);

    /** Env var: regex pattern that test names must match to be registered. Empty/unset = no
     *  filter. Applied at registration time so filtered-out recipes never appear in the
     *  GameTestRegistry. */
    static final String FILTER_ENV = "RECIPE_TEST_FILTER";

    /** Env var: when {@code "true"}, non-PASS runs log a warning and still pass the GameTest.
     *  For triage workflows where you want to see all results without halting on the first
     *  failure. */
    static final String SOFT_FAIL_ENV = "RECIPE_TEST_SOFT_FAIL";

    private DynamicGameTestGenerator() {}

    /**
     * Invoked once by {@code GameTestRegistry.register} during {@code RegisterGameTestsEvent}
     * dispatch. Walks {@link DatapackScanner} output, applies {@link #FILTER_ENV} regex if set,
     * and produces one TestFunction per surviving pair.
     */
    @GameTestGenerator
    public static Collection<TestFunction> generate() {
        List<DatapackScanner.RecipeRef> refs = DatapackScanner.scan();
        return buildFunctions(refs, System.getenv(FILTER_ENV));
    }

    /** Package-private for unit tests — lets us pass synthetic refs without invoking ModList. */
    static List<TestFunction> buildFunctions(List<DatapackScanner.RecipeRef> refs, @Nullable String filterRegex) {
        Pattern filter = compileFilter(filterRegex);
        List<TestFunction> out = new ArrayList<>();
        int filtered = 0;
        for (DatapackScanner.RecipeRef ref : refs) {
            String name = TestNaming.testName(ref.recipeType(), ref.recipeId());
            if (filter != null && !filter.matcher(name).find()) {
                filtered++;
                continue;
            }
            out.add(buildFunction(ref, name));
        }
        LOGGER.info(
                "recipe_test: registered {} dynamic GameTest(s){}",
                out.size(),
                filtered > 0 ? " (" + filtered + " filtered out by " + FILTER_ENV + ")" : "");
        for (TestFunction fn : out) {
            LOGGER.info("recipe_test:   {}", fn.testName());
        }
        return out;
    }

    private static TestFunction buildFunction(DatapackScanner.RecipeRef ref, String testName) {
        Consumer<GameTestHelper> body = helper -> runOne(helper, ref);
        // Timeout: spec budget is unknown at registration time (HarnessRegistry not populated
        // yet), so use the conservative auto-budget cap plus buffer. Tests whose actual recipe
        // budget exceeds this will TIMEOUT here even if the runner would have passed — accepted
        // tradeoff for v1; CI docs flag this as a tuning knob.
        int maxTicks = RecipeTestRunner.DEFAULT_AUTO_BUDGET + TIMEOUT_BUFFER;
        boolean required = !isSoftFail();
        return new TestFunction(
                BATCH,
                testName,
                STRUCTURE_NAME,
                Rotation.NONE,
                maxTicks,
                /* setupTicks */ 0L,
                required,
                /* manualOnly */ false,
                /* maxAttempts */ 1,
                /* requiredSuccesses */ 1,
                /* skyAccess */ false,
                body);
    }

    private static void runOne(GameTestHelper helper, DatapackScanner.RecipeRef ref) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        if (server == null) {
            helper.fail("server not available for recipe-test");
            return;
        }

        Optional<MachineSpec> specOpt = HarnessRegistry.instance().byRecipeType(ref.recipeType());
        if (specOpt.isEmpty()) {
            helper.fail("no MachineSpec registered for " + ref.recipeType());
            return;
        }
        MachineSpec spec = specOpt.get();

        RecipeManager recipeManager = server.getRecipeManager();
        Optional<RecipeHolder<?>> holderOpt = recipeManager.byKey(ref.recipeId());
        if (holderOpt.isEmpty()) {
            helper.fail("recipe not loaded: " + ref.recipeId());
            return;
        }
        RecipeHolder<?> holder = holderOpt.get();

        ResourceLocation actualType = level.registryAccess()
                .registry(Registries.RECIPE_TYPE)
                .orElseThrow()
                .getKey(holder.value().getType());
        if (actualType == null || !actualType.equals(ref.recipeType())) {
            helper.fail("recipe-type mismatch for " + ref.recipeId() + ": expected " + ref.recipeType() + ", got "
                    + actualType);
            return;
        }

        Optional<RecipeAdapter> adapterOpt = RecipeAdapters.findFor(holder.value());
        if (adapterOpt.isEmpty()) {
            helper.fail("no RecipeAdapter applies to " + ref.recipeId());
            return;
        }

        BlockPos origin = helper.absolutePos(MACHINE_POS);
        TestContext ctx = new TestContext(server, level, origin, level.registryAccess());
        AtomicReference<TestResult> resultRef = new AtomicReference<>();
        Consumer<TestResult> callback = resultRef::set;
        String specSource = spec.recipeType() + ".json";

        RecipeTestRunner runner = new RecipeTestRunner(spec, holder, ctx, adapterOpt.get(), specSource, callback);
        RunSessionScheduler.instance().submit(runner);

        boolean softFail = isSoftFail();
        helper.succeedWhen(() -> {
            TestResult r = resultRef.get();
            if (r == null) {
                throw new GameTestAssertException("recipe-test runner has not finished");
            }
            if (r.status() == RunStatus.PASS) {
                return;
            }
            String message = formatFailure(ref, r);
            if (softFail) {
                LOGGER.warn("recipe_test: soft-fail {} → {}", ref.recipeId(), message);
                return;
            }
            throw new GameTestAssertException(message);
        });
    }

    private static String formatFailure(DatapackScanner.RecipeRef ref, TestResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.status())
                .append(' ')
                .append(ref.recipeId())
                .append(" after ")
                .append(r.ticksElapsed())
                .append(" ticks");
        r.diff().ifPresent(d -> sb.append("; ").append(d.mismatches().size()).append(" mismatch(es)"));
        if (!r.diagnostics().warnings().isEmpty()) {
            sb.append("; warnings=").append(r.diagnostics().warnings());
        }
        return sb.toString();
    }

    private static @Nullable Pattern compileFilter(@Nullable String regex) {
        if (regex == null || regex.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            LOGGER.warn(
                    "recipe_test: invalid {} regex '{}' — running with no filter ({})",
                    FILTER_ENV,
                    regex,
                    e.getDescription());
            return null;
        }
    }

    private static boolean isSoftFail() {
        String raw = System.getenv(SOFT_FAIL_ENV);
        return raw != null && raw.trim().toLowerCase(Locale.ROOT).equals("true");
    }

    /** Defensive helper used by tests — keeps {@link TickBudget} unused warning suppressed even
     *  though the runtime path doesn't currently consult it for timeoutTicks. */
    @SuppressWarnings("unused")
    private static int budgetTicks(MachineSpec spec) {
        return switch (spec.tickBudget()) {
            case TickBudget.Auto auto -> RecipeTestRunner.DEFAULT_AUTO_BUDGET;
            case TickBudget.Fixed fixed -> fixed.ticks();
        };
    }
}
