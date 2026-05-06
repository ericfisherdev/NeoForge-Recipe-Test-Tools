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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.CustomBinding;
import dev.recipetest.api.InputBinding;
import dev.recipetest.api.ItemBinding;
import dev.recipetest.api.Layout;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.OutputBinding;
import dev.recipetest.api.Side;
import dev.recipetest.api.TickBudget;
import dev.recipetest.api.ValidationPolicy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One pass + one fail test per validator rule documented in {@link SpecValidator}. Uses a real
 * vanilla block ({@code minecraft:stone}) so the {@code BLOCK} registry check passes by default;
 * vanilla recipe types ({@code minecraft:crafting}) for the same reason.
 */
class ValidatorTest {

    private static final ResourceLocation STONE = ResourceLocation.parse("minecraft:stone");
    private static final ResourceLocation CRAFTING = ResourceLocation.parse("minecraft:crafting");

    private static final Predicate<ResourceLocation> ALL_KNOWN = rl -> true;
    private static final Predicate<ResourceLocation> BLOCK_KNOWN = Set.of(STONE)::contains;

    @Test
    @DisplayName("Rule 1 PASS: version=1 is accepted")
    void versionOnePasses() {
        MachineSpec spec = baseSpec();
        List<ValidationIssue> issues = SpecValidator.validate(spec, ALL_KNOWN, BLOCK_KNOWN);
        assertNoIssueAt(issues, "/version");
    }

    @Test
    @DisplayName("Rule 1 FAIL: version=2 is rejected with ERROR at /version")
    void versionTwoFails() {
        MachineSpec spec = withVersion(baseSpec(), 2);
        List<ValidationIssue> issues = SpecValidator.validate(spec, ALL_KNOWN, BLOCK_KNOWN);
        assertHasError(issues, "/version");
    }

    @Test
    @DisplayName("Rule 2 PASS: known recipeType emits no warning")
    void knownRecipeTypePasses() {
        MachineSpec spec = baseSpec();
        List<ValidationIssue> issues = SpecValidator.validate(spec, ALL_KNOWN, BLOCK_KNOWN);
        assertNoIssueAt(issues, "/recipeType");
    }

    @Test
    @DisplayName("Rule 2 FAIL: unknown recipeType emits WARN at /recipeType (does not block)")
    void unknownRecipeTypeWarns() {
        MachineSpec spec = baseSpec();
        Predicate<ResourceLocation> noneKnown = rl -> false;
        List<ValidationIssue> issues = SpecValidator.validate(spec, noneKnown, BLOCK_KNOWN);
        ValidationIssue issue = findAt(issues, "/recipeType");
        assertEquals(ValidationIssue.Severity.WARN, issue.severity());
        assertTrue(SpecValidator.isRegistryEligible(issues), "warnings must not block registration");
    }

    @Test
    @DisplayName("Rule 3 PASS: registered block is accepted")
    void registeredBlockPasses() {
        MachineSpec spec = baseSpec();
        List<ValidationIssue> issues = SpecValidator.validate(spec, ALL_KNOWN, BLOCK_KNOWN);
        assertNoIssueAt(issues, "/block");
    }

    @Test
    @DisplayName("Rule 3 FAIL: unresolved block emits ERROR at /block")
    void unresolvedBlockFails() {
        MachineSpec spec = withBlock(baseSpec(), ResourceLocation.parse("nonexistent:nope"));
        List<ValidationIssue> issues = SpecValidator.validate(spec, ALL_KNOWN, BLOCK_KNOWN);
        assertHasError(issues, "/block");
    }

    @Test
    @DisplayName("Rule 4 PASS: shaped3x3 with 9 slots passes")
    void shaped3x3WithNineSlotsPasses() {
        MachineSpec spec = withInputItems(baseSpec(), nineSlotShaped3x3());
        List<ValidationIssue> issues = SpecValidator.validate(spec, ALL_KNOWN, BLOCK_KNOWN);
        assertNoIssueAt(issues, "/inputs/items/slots");
    }

    @Test
    @DisplayName("Rule 4 FAIL: shaped3x3 with 3 slots emits ERROR at /inputs/items/slots")
    void shaped3x3WithThreeSlotsFails() {
        MachineSpec spec = withInputItems(
                baseSpec(),
                new ItemBinding(
                        "ItemHandler",
                        Side.INTERNAL,
                        List.of(0, 1, 2),
                        Optional.of(Layout.SHAPED3X3),
                        Optional.empty()));
        List<ValidationIssue> issues = SpecValidator.validate(spec, ALL_KNOWN, BLOCK_KNOWN);
        ValidationIssue issue = findAt(issues, "/inputs/items/slots");
        assertEquals(ValidationIssue.Severity.ERROR, issue.severity());
        assertTrue(issue.fixHint().contains("9"), "fix hint should mention required count");
        assertTrue(issue.fixHint().contains("3"), "fix hint should mention actual count");
    }

    @Test
    @DisplayName("Rule 5 PASS: distribution mode with 100 samples passes")
    void distributionWithEnoughSamplesPasses() {
        MachineSpec spec = withValidation(baseSpec(), distributionPolicy(100));
        List<ValidationIssue> issues = SpecValidator.validate(spec, ALL_KNOWN, BLOCK_KNOWN);
        assertNoIssueAt(issues, "/validation/samples");
    }

    @Test
    @DisplayName("Rule 5 FAIL: distribution mode with 5 samples emits ERROR at /validation/samples")
    void distributionWithTooFewSamplesFails() {
        MachineSpec spec = withValidation(baseSpec(), distributionPolicy(5));
        List<ValidationIssue> issues = SpecValidator.validate(spec, ALL_KNOWN, BLOCK_KNOWN);
        ValidationIssue issue = findAt(issues, "/validation/samples");
        assertEquals(ValidationIssue.Severity.ERROR, issue.severity());
    }

    @Test
    @DisplayName("Rule 6 PASS: primary in slots passes")
    void primaryInSlotsPasses() {
        MachineSpec spec = withOutputItems(
                baseSpec(),
                new ItemBinding("ItemHandler", Side.INTERNAL, List.of(0, 1, 2), Optional.empty(), Optional.of(1)));
        List<ValidationIssue> issues = SpecValidator.validate(spec, ALL_KNOWN, BLOCK_KNOWN);
        assertNoIssueAt(issues, "/outputs/items/primary");
    }

    @Test
    @DisplayName("Rule 6 FAIL: primary not in slots emits ERROR at /outputs/items/primary")
    void primaryNotInSlotsFails() {
        MachineSpec spec = withOutputItems(
                baseSpec(),
                new ItemBinding("ItemHandler", Side.INTERNAL, List.of(0, 1, 2), Optional.empty(), Optional.of(99)));
        List<ValidationIssue> issues = SpecValidator.validate(spec, ALL_KNOWN, BLOCK_KNOWN);
        ValidationIssue issue = findAt(issues, "/outputs/items/primary");
        assertEquals(ValidationIssue.Severity.ERROR, issue.severity());
        assertTrue(issue.message().contains("99"), "message should reference the bad primary value");
    }

    @Test
    @DisplayName("isRegistryEligible: returns true on warnings-only, false when any error present")
    void registryEligibility() {
        MachineSpec good = baseSpec();
        assertTrue(SpecValidator.isRegistryEligible(SpecValidator.validate(good, ALL_KNOWN, BLOCK_KNOWN)));

        MachineSpec broken = withBlock(baseSpec(), ResourceLocation.parse("nonexistent:nope"));
        assertFalse(SpecValidator.isRegistryEligible(SpecValidator.validate(broken, ALL_KNOWN, BLOCK_KNOWN)));
    }

    // ---- builders ----

    private static MachineSpec baseSpec() {
        return new MachineSpec(
                1,
                CRAFTING,
                STONE,
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

    private static MachineSpec withVersion(MachineSpec s, int version) {
        return new MachineSpec(
                version,
                s.recipeType(),
                s.block(),
                s.blockState(),
                s.neighbors(),
                s.inputs(),
                s.outputs(),
                s.energy(),
                s.tickBudget(),
                s.validation(),
                s.lifecycle());
    }

    private static MachineSpec withBlock(MachineSpec s, ResourceLocation block) {
        return new MachineSpec(
                s.version(),
                s.recipeType(),
                block,
                s.blockState(),
                s.neighbors(),
                s.inputs(),
                s.outputs(),
                s.energy(),
                s.tickBudget(),
                s.validation(),
                s.lifecycle());
    }

    private static MachineSpec withInputItems(MachineSpec s, ItemBinding items) {
        InputBinding ib = new InputBinding(
                Optional.of(items), s.inputs().fluids(), s.inputs().custom());
        return new MachineSpec(
                s.version(),
                s.recipeType(),
                s.block(),
                s.blockState(),
                s.neighbors(),
                ib,
                s.outputs(),
                s.energy(),
                s.tickBudget(),
                s.validation(),
                s.lifecycle());
    }

    private static MachineSpec withOutputItems(MachineSpec s, ItemBinding items) {
        OutputBinding ob = new OutputBinding(
                Optional.of(items), s.outputs().fluids(), s.outputs().custom());
        return new MachineSpec(
                s.version(),
                s.recipeType(),
                s.block(),
                s.blockState(),
                s.neighbors(),
                s.inputs(),
                ob,
                s.energy(),
                s.tickBudget(),
                s.validation(),
                s.lifecycle());
    }

    private static MachineSpec withValidation(MachineSpec s, ValidationPolicy v) {
        return new MachineSpec(
                s.version(),
                s.recipeType(),
                s.block(),
                s.blockState(),
                s.neighbors(),
                s.inputs(),
                s.outputs(),
                s.energy(),
                s.tickBudget(),
                v,
                s.lifecycle());
    }

    private static ItemBinding nineSlotShaped3x3() {
        return new ItemBinding(
                "ItemHandler",
                Side.INTERNAL,
                List.of(0, 1, 2, 3, 4, 5, 6, 7, 8),
                Optional.of(Layout.SHAPED3X3),
                Optional.empty());
    }

    private static ValidationPolicy distributionPolicy(int samples) {
        return new ValidationPolicy(
                ValidationPolicy.Mode.DISTRIBUTION,
                ValidationPolicy.ItemTolerance.EXACT,
                ValidationPolicy.FluidTolerance.EXACT,
                ValidationPolicy.NbtCompare.STRUCTURAL,
                samples,
                0.05);
    }

    // ---- assertion helpers ----

    private static ValidationIssue findAt(List<ValidationIssue> issues, String path) {
        return issues.stream()
                .filter(i -> i.jsonPath().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected an issue at " + path + " but none found in " + issues));
    }

    private static void assertHasError(List<ValidationIssue> issues, String path) {
        ValidationIssue issue = findAt(issues, path);
        assertEquals(
                ValidationIssue.Severity.ERROR,
                issue.severity(),
                () -> "expected ERROR at " + path + " got " + issue.severity());
    }

    private static void assertNoIssueAt(List<ValidationIssue> issues, String path) {
        boolean any = issues.stream().anyMatch(i -> i.jsonPath().equals(path));
        assertFalse(any, () -> "expected no issue at " + path + " but found: " + issues);
    }
}
