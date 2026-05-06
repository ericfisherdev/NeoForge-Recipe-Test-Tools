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

import dev.recipetest.api.Diagnostics;
import dev.recipetest.api.DiffEntry;
import dev.recipetest.api.DiffPayload;
import dev.recipetest.api.EnergySpec;
import dev.recipetest.api.FluidSnapshot;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.ItemBinding;
import dev.recipetest.api.ItemSnapshot;
import dev.recipetest.api.Layout;
import dev.recipetest.api.LifecycleHooks;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.Side;
import dev.recipetest.api.TestContext;
import dev.recipetest.api.TestResult;
import dev.recipetest.api.TickBudget;
import dev.recipetest.api.ValidationPolicy;
import dev.recipetest.gametest.TestStructures;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Single-recipe runner. Drives a {@link MachineSpec} + {@link RecipeHolder} pair through the
 * full place→inject→tick→read→diff→cleanup lifecycle, tick by tick. Each instance is one-shot
 * and lives as long as the run takes; the runner is driven by {@link RunSessionScheduler}, which
 * calls {@link #advance()} once per server tick.
 *
 * <p>The state machine intentionally separates "non-tick-spending" transitions (PLACE → commands
 * → INJECT) from "tick-spending" ones (WARMUP loop, TICK loop) so the runner can complete setup
 * within a single {@link net.neoforged.neoforge.event.tick.ServerTickEvent.Post} when warmup is
 * zero, while still letting the actual recipe burn ticks.
 */
public final class RecipeTestRunner {

    /** Default fallback when {@link TickBudget#AUTO} is in play and no recipe-specific budget is
     *  available. Matches the json-spec default. */
    public static final int DEFAULT_AUTO_BUDGET = 200;

    enum Phase {
        PLACE,
        PRE_TICK_COMMANDS,
        WARMUP,
        INJECT,
        TICK,
        POST_TICK_COMMANDS,
        REPORT,
        CLEANUP,
        DONE
    }

    private final MachineSpec spec;
    private final RecipeHolder<?> recipeHolder;
    private final TestContext ctx;
    private final RecipeAdapter adapter;
    private final String specSource;
    private final Consumer<TestResult> callback;

    private Phase phase = Phase.PLACE;
    private int phaseTicks = 0;
    private int recipeTicks = 0;

    private @Nullable TestStructures.Placement placement;
    private @Nullable IoSnapshot expected;
    private @Nullable IoSnapshot lastActual;
    private final List<String> warnings = new ArrayList<>();
    private long initialEnergy = 0L;
    private long energyConsumed = 0L;

    public RecipeTestRunner(
            MachineSpec spec,
            RecipeHolder<?> recipeHolder,
            TestContext ctx,
            RecipeAdapter adapter,
            String specSource,
            Consumer<TestResult> callback) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.recipeHolder = Objects.requireNonNull(recipeHolder, "recipeHolder");
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.specSource = Objects.requireNonNull(specSource, "specSource");
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    /** True when the runner has finished all phases and can be removed from the scheduler. */
    public boolean isDone() {
        return phase == Phase.DONE;
    }

    /**
     * Advance the state machine until the next tick boundary or completion. Called once per
     * {@code ServerTickEvent.Post} by the scheduler.
     */
    public void advance() {
        try {
            while (!isDone()) {
                if (advanceOne()) {
                    return;
                }
            }
        } catch (RuntimeException ex) {
            handleException(ex);
        }
    }

    /** @return true if the next phase needs to wait for a tick boundary. */
    private boolean advanceOne() {
        switch (phase) {
            case PLACE -> {
                placement = TestStructures.placeMachine(spec, ctx);
                phase = Phase.PRE_TICK_COMMANDS;
                return false;
            }
            case PRE_TICK_COMMANDS -> {
                runCommands(
                        spec.lifecycle().map(LifecycleHooks::preTickCommands).orElse(List.of()));
                phaseTicks = 0;
                phase = Phase.WARMUP;
                return false;
            }
            case WARMUP -> {
                int warmup = spec.lifecycle().map(LifecycleHooks::warmupTicks).orElse(0);
                if (phaseTicks >= warmup) {
                    phase = Phase.INJECT;
                    return false;
                }
                phaseTicks++;
                return true;
            }
            case INJECT -> {
                expected = computeExpected();
                injectInputs();
                phase = Phase.TICK;
                return true;
            }
            case TICK -> {
                IoSnapshot actual = readOutputs();
                lastActual = actual;
                if (matchesExpected(actual)) {
                    phase = Phase.POST_TICK_COMMANDS;
                    return false;
                }
                int budget = resolveBudget();
                if (recipeTicks >= budget) {
                    phase = Phase.POST_TICK_COMMANDS;
                    return false;
                }
                recipeTicks++;
                return true;
            }
            case POST_TICK_COMMANDS -> {
                runCommands(
                        spec.lifecycle().map(LifecycleHooks::postRunCommands).orElse(List.of()));
                phase = Phase.REPORT;
                return false;
            }
            case REPORT -> {
                callback.accept(buildResult());
                phase = Phase.CLEANUP;
                return false;
            }
            case CLEANUP -> {
                TestStructures.tearDown(ctx.level(), placement);
                phase = Phase.DONE;
                return true;
            }
            case DONE -> {
                return true;
            }
        }
        throw new IllegalStateException("unreachable phase " + phase);
    }

    // ---- expected / actual computation ----

    private IoSnapshot computeExpected() {
        Recipe<?> recipe = recipeHolder.value();
        List<ItemSnapshot> items = new ArrayList<>();
        ItemStack primary = adapter.extractPrimaryOutput(recipe, ctx.registries());
        if (!primary.isEmpty()) {
            items.add(Snapshots.of(primary));
        }
        for (ItemStack extra : adapter.extractAdditionalOutputs(recipe, ctx.registries())) {
            if (!extra.isEmpty()) {
                items.add(Snapshots.of(extra));
            }
        }
        List<FluidSnapshot> fluids = new ArrayList<>();
        for (FluidStack fluid : adapter.extractOutputFluids(recipe)) {
            if (!fluid.isEmpty()) {
                fluids.add(Snapshots.of(fluid));
            }
        }
        return new IoSnapshot(items, fluids);
    }

    private IoSnapshot readOutputs() {
        List<ItemSnapshot> items = spec.outputs()
                .items()
                .map(binding -> resolveItemHandler(binding.side())
                        .map(handler -> CapabilityDriver.readItems(binding, handler))
                        .orElseGet(List::of))
                .orElse(List.of());
        List<FluidSnapshot> fluids = spec.outputs()
                .fluids()
                .map(binding -> resolveFluidHandler(binding.side())
                        .map(handler -> CapabilityDriver.readFluids(binding, handler))
                        .orElseGet(List::of))
                .orElse(List.of());
        return new IoSnapshot(items, fluids);
    }

    private boolean matchesExpected(IoSnapshot actual) {
        Objects.requireNonNull(expected, "expected snapshot must be set before matchesExpected");
        return ResultDiffer.diff(expected, actual, spec.validation()).isEmpty();
    }

    // ---- injection ----

    private void injectInputs() {
        Recipe<?> recipe = recipeHolder.value();

        spec.inputs().items().ifPresent(binding -> {
            Optional<IItemHandler> handler = resolveItemHandler(binding.side());
            if (handler.isEmpty()) {
                warnings.add("input items capability missing on side " + binding.side());
                return;
            }
            // Choose layout: spec wins, else adapter default.
            Layout layout = binding.layout().orElse(adapter.defaultLayout());
            ItemBinding effective = new ItemBinding(
                    binding.capability(), binding.side(), binding.slots(), Optional.of(layout), binding.primary());
            List<ItemStack> stacks = adapter.extractInputItems(recipe);
            List<int[]> positions = layout == Layout.SHAPED3X3 ? adapter.extractInputPositions(recipe) : List.of();
            InjectionResult result = CapabilityDriver.injectItems(effective, handler.get(), stacks, positions);
            if (!result.fullyAccepted()) {
                warnings.addAll(result.warnings());
            }
        });

        spec.inputs().fluids().ifPresent(binding -> {
            Optional<IFluidHandler> handler = resolveFluidHandler(binding.side());
            if (handler.isEmpty()) {
                warnings.add("input fluids capability missing on side " + binding.side());
                return;
            }
            List<FluidStack> fluids = adapter.extractInputFluids(recipe);
            int leftover = CapabilityDriver.injectFluids(binding, handler.get(), fluids);
            if (leftover > 0) {
                warnings.add(leftover + "mB fluid input refused");
            }
        });

        spec.energy().ifPresent(energy -> {
            Optional<IEnergyStorage> storage = resolveEnergy(energy.side());
            if (storage.isEmpty()) {
                warnings.add("energy capability missing on side " + energy.side());
                return;
            }
            initialEnergy = CapabilityDriver.injectEnergy(energy, storage.get());
        });
    }

    // ---- capability resolution ----

    private Optional<IItemHandler> resolveItemHandler(Side side) {
        return Optional.ofNullable(
                ctx.level().getCapability(Capabilities.ItemHandler.BLOCK, ctx.origin(), directionFor(side)));
    }

    private Optional<IFluidHandler> resolveFluidHandler(Side side) {
        return Optional.ofNullable(
                ctx.level().getCapability(Capabilities.FluidHandler.BLOCK, ctx.origin(), directionFor(side)));
    }

    private Optional<IEnergyStorage> resolveEnergy(Side side) {
        return Optional.ofNullable(
                ctx.level().getCapability(Capabilities.EnergyStorage.BLOCK, ctx.origin(), directionFor(side)));
    }

    private static @Nullable Direction directionFor(Side side) {
        return CapabilityDriver.toDirection(side).orElse(null);
    }

    // ---- lifecycle commands ----

    private void runCommands(List<String> commands) {
        if (commands.isEmpty()) {
            return;
        }
        BlockPos origin = ctx.origin();
        CommandSourceStack source = ctx.server()
                .createCommandSourceStack()
                .withPosition(Vec3.atCenterOf(origin))
                .withSuppressedOutput();
        for (String command : commands) {
            ctx.server().getCommands().performPrefixedCommand(source, command);
        }
    }

    // ---- result construction ----

    private TestResult buildResult() {
        IoSnapshot actual = lastActual == null ? IoSnapshot.empty() : lastActual;
        Optional<DiffPayload> diff =
                ResultDiffer.diff(Objects.requireNonNullElse(expected, IoSnapshot.empty()), actual, spec.validation());

        int budget = resolveBudget();
        boolean budgetExhausted = recipeTicks >= budget;
        RunStatus status = determineStatus(diff, budgetExhausted, actual);

        // Compute energy consumed for diagnostics.
        Optional<EnergySpec> energy = spec.energy();
        if (energy.isPresent() && energy.get().trackConsumption()) {
            Optional<IEnergyStorage> storage = resolveEnergy(energy.get().side());
            energyConsumed = storage.map(s -> initialEnergy - CapabilityDriver.readEnergy(s))
                    .orElse(0L);
            if (energyConsumed < 0) {
                energyConsumed = 0L;
            }
        }

        Diagnostics diagnostics = new Diagnostics(
                List.of(), // fluidConsumed: precise tracking deferred to Phase 3+
                energyConsumed,
                List.copyOf(warnings),
                List.of());

        // TestResult invariant: diff present iff status == FAIL. For TIMEOUT, surface what's
        // missing via diagnostics.warnings instead of populating diff.
        Optional<DiffPayload> reportedDiff = status == RunStatus.FAIL ? diff : Optional.empty();
        if (status == RunStatus.TIMEOUT && diff.isPresent()) {
            String summary = "TIMEOUT after " + recipeTicks + " ticks; "
                    + diff.get().mismatches().stream()
                            .map(DiffEntry::reason)
                            .distinct()
                            .toList();
            warnings.add(summary);
            diagnostics = new Diagnostics(
                    diagnostics.fluidConsumed(), diagnostics.energyConsumed(), List.copyOf(warnings), List.of());
        }

        int totalTicks = phaseTicks + recipeTicks;
        return new TestResult(
                recipeHolder.id(),
                spec.recipeType(),
                specSource,
                status,
                totalTicks,
                Objects.requireNonNullElse(expected, IoSnapshot.empty()),
                actual,
                reportedDiff,
                diagnostics);
    }

    private static RunStatus determineStatus(Optional<DiffPayload> diff, boolean budgetExhausted, IoSnapshot actual) {
        if (diff.isEmpty()) {
            return RunStatus.PASS;
        }
        if (budgetExhausted && actual.items().isEmpty() && actual.fluids().isEmpty()) {
            return RunStatus.TIMEOUT;
        }
        return RunStatus.FAIL;
    }

    // ---- error path ----

    private void handleException(RuntimeException ex) {
        Diagnostics diagnostics = new Diagnostics(
                List.of(),
                0L,
                List.of("runner exception: " + ex.getClass().getSimpleName() + ": " + ex.getMessage()),
                List.of());
        TestResult result = new TestResult(
                recipeHolder.id(),
                spec.recipeType(),
                specSource,
                RunStatus.ERROR,
                phaseTicks + recipeTicks,
                Objects.requireNonNullElse(expected, IoSnapshot.empty()),
                Objects.requireNonNullElse(lastActual, IoSnapshot.empty()),
                Optional.empty(),
                diagnostics);
        try {
            callback.accept(result);
        } finally {
            try {
                TestStructures.tearDown(ctx.level(), placement);
            } catch (RuntimeException ignored) {
                // best-effort cleanup
            }
            phase = Phase.DONE;
        }
    }

    // ---- helpers ----

    private int resolveBudget() {
        return switch (spec.tickBudget()) {
            case TickBudget.Auto auto -> DEFAULT_AUTO_BUDGET;
            case TickBudget.Fixed fixed -> fixed.ticks();
        };
    }

    /** Honour the validation policy when comparing actual vs expected during the tick loop. */
    @SuppressWarnings("unused")
    private ValidationPolicy validation() {
        return spec.validation();
    }
}
