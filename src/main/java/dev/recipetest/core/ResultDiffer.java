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

import dev.recipetest.api.DiffEntry;
import dev.recipetest.api.DiffPayload;
import dev.recipetest.api.FluidSnapshot;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.ItemSnapshot;
import dev.recipetest.api.ValidationPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Compares an expected {@link IoSnapshot} against the actual reading from the machine and
 * produces a structured {@link DiffPayload}. Returns {@link Optional#empty()} for a passing
 * comparison.
 *
 * <p>Modes:
 *
 * <ul>
 *   <li>{@link ValidationPolicy.Mode#EXACT} — every expected stack must be present and counts
 *       must match within {@code itemTolerance}/{@code fluidTolerance}; extra actual stacks
 *       count as mismatches.
 *   <li>{@link ValidationPolicy.Mode#SUBSET} — same matching rules, but extra actual stacks
 *       are permitted (used when a machine produces side outputs the recipe doesn't declare).
 *   <li>{@link ValidationPolicy.Mode#DISTRIBUTION} — out of scope for the single-recipe runner;
 *       reported as a single mismatch so the caller surfaces it as {@code FAIL} rather than
 *       silently passing.
 * </ul>
 *
 * <p>NBT comparison is handled via {@link ValidationPolicy.NbtCompare}: for PR-A both
 * {@code STRUCTURAL} and {@code EXACT} compare the stringified component patch byte-for-byte;
 * a future phase can swap in a true structural comparison once components are parsed.
 */
public final class ResultDiffer {

    private ResultDiffer() {}

    /**
     * Compute the diff. Returns {@link Optional#empty()} when actual matches expected per the
     * given policy.
     */
    public static Optional<DiffPayload> diff(IoSnapshot expected, IoSnapshot actual, ValidationPolicy policy) {
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(actual, "actual must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        if (policy.mode() == ValidationPolicy.Mode.DISTRIBUTION) {
            return Optional.of(new DiffPayload(List.of(new DiffEntry(
                    "/validation/mode",
                    "distribution",
                    "(unsupported)",
                    "distribution mode requires the bulk runner — out of scope for single-recipe execution"))));
        }

        List<DiffEntry> mismatches = new ArrayList<>();
        diffItems(expected.items(), actual.items(), policy, mismatches);
        diffFluids(expected.fluids(), actual.fluids(), policy, mismatches);

        return mismatches.isEmpty() ? Optional.empty() : Optional.of(new DiffPayload(mismatches));
    }

    // ---- items ----

    private static void diffItems(
            List<ItemSnapshot> expected, List<ItemSnapshot> actual, ValidationPolicy policy, List<DiffEntry> out) {
        List<ItemSnapshot> remaining = new ArrayList<>(actual);
        for (int i = 0; i < expected.size(); i++) {
            ItemSnapshot exp = expected.get(i);
            int matchIdx = findItemMatch(exp, remaining, policy.nbtCompare());
            String path = "/items/" + i;
            if (matchIdx < 0) {
                out.add(new DiffEntry(path, renderItem(exp), "(missing)", "missing item"));
                continue;
            }
            ItemSnapshot got = remaining.remove(matchIdx);
            int delta = Math.abs(exp.count() - got.count());
            if (delta > policy.itemTolerance().count()) {
                out.add(new DiffEntry(
                        path,
                        renderItem(exp),
                        renderItem(got),
                        "count mismatch (delta=" + delta + ", tolerance="
                                + policy.itemTolerance().count() + ")"));
            }
        }

        if (policy.mode() != ValidationPolicy.Mode.SUBSET) {
            for (ItemSnapshot extra : remaining) {
                out.add(new DiffEntry("/items/extra", "(none)", renderItem(extra), "unexpected extra item"));
            }
        }
    }

    private static int findItemMatch(ItemSnapshot expected, List<ItemSnapshot> pool, ValidationPolicy.NbtCompare mode) {
        for (int i = 0; i < pool.size(); i++) {
            ItemSnapshot candidate = pool.get(i);
            if (!candidate.id().equals(expected.id())) {
                continue;
            }
            if (!nbtMatches(expected.nbt(), candidate.nbt(), mode)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    // ---- fluids ----

    private static void diffFluids(
            List<FluidSnapshot> expected, List<FluidSnapshot> actual, ValidationPolicy policy, List<DiffEntry> out) {
        List<FluidSnapshot> remaining = new ArrayList<>(actual);
        for (int i = 0; i < expected.size(); i++) {
            FluidSnapshot exp = expected.get(i);
            int matchIdx = findFluidMatch(exp, remaining, policy.nbtCompare());
            String path = "/fluids/" + i;
            if (matchIdx < 0) {
                out.add(new DiffEntry(path, renderFluid(exp), "(missing)", "missing fluid"));
                continue;
            }
            FluidSnapshot got = remaining.remove(matchIdx);
            int delta = Math.abs(exp.amount() - got.amount());
            if (delta > policy.fluidTolerance().amount()) {
                out.add(new DiffEntry(
                        path,
                        renderFluid(exp),
                        renderFluid(got),
                        "amount mismatch (delta=" + delta + "mB, tolerance="
                                + policy.fluidTolerance().amount() + "mB)"));
            }
        }

        if (policy.mode() != ValidationPolicy.Mode.SUBSET) {
            for (FluidSnapshot extra : remaining) {
                out.add(new DiffEntry("/fluids/extra", "(none)", renderFluid(extra), "unexpected extra fluid"));
            }
        }
    }

    private static int findFluidMatch(
            FluidSnapshot expected, List<FluidSnapshot> pool, ValidationPolicy.NbtCompare mode) {
        for (int i = 0; i < pool.size(); i++) {
            FluidSnapshot candidate = pool.get(i);
            if (!candidate.id().equals(expected.id())) {
                continue;
            }
            if (!nbtMatches(expected.nbt(), candidate.nbt(), mode)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    // ---- shared helpers ----

    private static boolean nbtMatches(
            Optional<String> expected, Optional<String> actual, ValidationPolicy.NbtCompare mode) {
        return switch (mode) {
            case IGNORE -> true;
                // PR-A treats STRUCTURAL identically to EXACT (string equality) — proper key-aware
                // comparison is deferred until a parsed component-patch representation lands.
            case STRUCTURAL, EXACT -> expected.equals(actual);
        };
    }

    private static String renderItem(ItemSnapshot s) {
        StringBuilder sb = new StringBuilder(s.id().toString()).append(" x").append(s.count());
        s.nbt().ifPresent(n -> sb.append(' ').append(n));
        return sb.toString();
    }

    private static String renderFluid(FluidSnapshot s) {
        StringBuilder sb = new StringBuilder(s.id().toString())
                .append(' ')
                .append(s.amount())
                .append("mB");
        s.nbt().ifPresent(n -> sb.append(' ').append(n));
        return sb.toString();
    }
}
