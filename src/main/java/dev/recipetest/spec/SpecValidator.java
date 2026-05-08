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

import dev.recipetest.api.CustomBinding;
import dev.recipetest.api.ItemBinding;
import dev.recipetest.api.Layout;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.ValidationPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Spec-time validation. Each rule is documented in {@code json-spec.md}
 * §"Validation Rules (Spec-Time)" and produces a {@link ValidationIssue} with the JSON Pointer
 * into the offending field plus a one-line fix hint.
 *
 * <p>Rules implemented:
 *
 * <ol>
 *   <li>{@code version} other than 1 → {@link ValidationIssue.Severity#ERROR}
 *   <li>Unknown {@code recipeType} (not present in {@code RecipeManager} types) →
 *       {@link ValidationIssue.Severity#WARN}
 *   <li>Unresolved {@code block} (not in {@code BuiltInRegistries.BLOCK}) →
 *       {@link ValidationIssue.Severity#ERROR}
 *   <li>{@code inputs.items.layout = "shaped3x3"} with {@code slots.length != 9} →
 *       {@link ValidationIssue.Severity#ERROR}
 *   <li>{@code validation.mode = "distribution"} with {@code samples < 10} →
 *       {@link ValidationIssue.Severity#ERROR}
 *   <li>{@code outputs.items.primary} not contained in {@code outputs.items.slots} →
 *       {@link ValidationIssue.Severity#ERROR}
 *   <li>{@code inputs.custom[i].kind} or {@code outputs.custom[i].kind} that no registered
 *       {@code RecipeTestExtension} claims via {@code supportedKinds()} →
 *       {@link ValidationIssue.Severity#ERROR} (Phase 5 AC#4)
 *   <li>{@code validation.mode = "distribution"} for a {@code recipeType} with no registered
 *       {@code RecipeTestExtension} → {@link ValidationIssue.Severity#ERROR}. Distribution
 *       mode needs the extension's {@code weights()} to define the expected per-channel
 *       distribution; without it, every channel would FAIL against an empty expected map
 *       (Phase 5 AC#5)
 * </ol>
 *
 * <p>Capability-presence checking is deferred to Phase 2 (needs a temp world placement).
 */
public final class SpecValidator {

    /** Current schema version the harness understands. */
    public static final int SUPPORTED_VERSION = 1;

    private SpecValidator() {}

    /**
     * Validate a spec.
     *
     * @param spec the parsed spec
     * @param recipeTypeKnown predicate answering "does this recipeType exist on this server?".
     *     Pass {@code rl -> true} to skip rule 2 entirely (handy for unit tests that don't have a
     *     {@code RecipeManager} on the classpath).
     * @param blockKnown predicate answering "is this block in the BLOCK registry?". Production
     *     callers wire {@code BuiltInRegistries.BLOCK::containsKey}; tests can pass any
     *     {@code Predicate} so they don't need to bootstrap the Minecraft registry.
     * @return zero-or-more issues; empty means the spec is registry-eligible
     */
    public static List<ValidationIssue> validate(
            MachineSpec spec, Predicate<ResourceLocation> recipeTypeKnown, Predicate<ResourceLocation> blockKnown) {
        // Default both extension predicates to "everything resolves" so existing call sites that
        // don't know about extensions don't start emitting spurious errors. Production wires
        // these from ExtensionRegistry; tests pass their own.
        return validate(spec, recipeTypeKnown, blockKnown, kind -> true, recipeType -> true);
    }

    /**
     * Validate a spec with extension-aware kind resolution. Back-compat overload — distribution
     * extension predicate defaults to "always present".
     *
     * @param customKindKnown predicate answering "does some registered {@code
     *     RecipeTestExtension} claim this {@link CustomBinding#kind}?". Wire to
     *     {@code ExtensionRegistry.instance().kindKnownPredicate()} in production.
     */
    public static List<ValidationIssue> validate(
            MachineSpec spec,
            Predicate<ResourceLocation> recipeTypeKnown,
            Predicate<ResourceLocation> blockKnown,
            Predicate<ResourceLocation> customKindKnown) {
        return validate(spec, recipeTypeKnown, blockKnown, customKindKnown, recipeType -> true);
    }

    /**
     * Validate a spec with full extension awareness — both custom-binding kind resolution
     * (rule 7) and distribution-mode extension presence (rule 8).
     *
     * @param customKindKnown predicate answering "does some registered {@code
     *     RecipeTestExtension} claim this {@link CustomBinding#kind}?". Wire to
     *     {@code ExtensionRegistry.instance().kindKnownPredicate()}.
     * @param recipeTypeHasExtension predicate answering "is a {@code RecipeTestExtension}
     *     registered for this recipeType?". Used by rule 8 — distribution mode requires one.
     */
    public static List<ValidationIssue> validate(
            MachineSpec spec,
            Predicate<ResourceLocation> recipeTypeKnown,
            Predicate<ResourceLocation> blockKnown,
            Predicate<ResourceLocation> customKindKnown,
            Predicate<ResourceLocation> recipeTypeHasExtension) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(recipeTypeKnown, "recipeTypeKnown");
        Objects.requireNonNull(blockKnown, "blockKnown");
        Objects.requireNonNull(customKindKnown, "customKindKnown");
        Objects.requireNonNull(recipeTypeHasExtension, "recipeTypeHasExtension");
        List<ValidationIssue> issues = new ArrayList<>();
        validateVersion(spec, issues);
        validateRecipeType(spec, recipeTypeKnown, issues);
        validateBlock(spec, blockKnown, issues);
        spec.inputs().items().ifPresent(items -> validateInputItemLayout(items, issues));
        validateDistributionSamples(spec.validation(), issues);
        spec.outputs().items().ifPresent(items -> validatePrimaryInSlots(items, issues));
        validateCustomBindingKinds(spec, customKindKnown, issues);
        validateDistributionExtensionPresent(spec, recipeTypeHasExtension, issues);
        return List.copyOf(issues);
    }

    private static void validateVersion(MachineSpec spec, List<ValidationIssue> out) {
        if (spec.version() != SUPPORTED_VERSION) {
            out.add(ValidationIssue.error(
                    "/version", "unsupported schema version " + spec.version(), "set version to " + SUPPORTED_VERSION));
        }
    }

    private static void validateRecipeType(
            MachineSpec spec, Predicate<ResourceLocation> recipeTypeKnown, List<ValidationIssue> out) {
        if (!recipeTypeKnown.test(spec.recipeType())) {
            out.add(ValidationIssue.warn(
                    "/recipeType",
                    "recipeType '" + spec.recipeType() + "' is not registered on this server",
                    "verify the providing mod is loaded; this is a warning because the mod may be optional"));
        }
    }

    private static void validateBlock(
            MachineSpec spec, Predicate<ResourceLocation> blockKnown, List<ValidationIssue> out) {
        if (!blockKnown.test(spec.block())) {
            out.add(ValidationIssue.error(
                    "/block",
                    "block '" + spec.block() + "' is not registered",
                    "use a ResourceLocation that resolves in the BLOCK registry"));
        }
    }

    private static void validateInputItemLayout(ItemBinding items, List<ValidationIssue> out) {
        Optional<Layout> layout = items.layout();
        if (layout.isPresent()
                && layout.get() == Layout.SHAPED3X3
                && items.slots().size() != 9) {
            out.add(ValidationIssue.error(
                    "/inputs/items/slots",
                    "shaped3x3 layout requires exactly 9 slots",
                    "expected 9 slots for shaped3x3 layout, got "
                            + items.slots().size()));
        }
    }

    private static void validateDistributionSamples(ValidationPolicy policy, List<ValidationIssue> out) {
        if (policy.mode() == ValidationPolicy.Mode.DISTRIBUTION && policy.samples() < 10) {
            out.add(ValidationIssue.error(
                    "/validation/samples",
                    "distribution mode needs at least 10 samples for meaningful statistics",
                    "set samples to >= 10, got " + policy.samples()));
        }
    }

    private static void validateCustomBindingKinds(
            MachineSpec spec, Predicate<ResourceLocation> customKindKnown, List<ValidationIssue> out) {
        List<CustomBinding> inputs = spec.inputs().custom();
        for (int i = 0; i < inputs.size(); i++) {
            CustomBinding cb = inputs.get(i);
            if (!customKindKnown.test(cb.kind())) {
                out.add(ValidationIssue.error(
                        "/inputs/custom/" + i + "/kind",
                        "unresolved custom binding kind '" + cb.kind() + "'",
                        "register a RecipeTestExtension whose supportedKinds() contains '" + cb.kind() + "'"));
            }
        }
        List<CustomBinding> outputs = spec.outputs().custom();
        for (int i = 0; i < outputs.size(); i++) {
            CustomBinding cb = outputs.get(i);
            if (!customKindKnown.test(cb.kind())) {
                out.add(ValidationIssue.error(
                        "/outputs/custom/" + i + "/kind",
                        "unresolved custom binding kind '" + cb.kind() + "'",
                        "register a RecipeTestExtension whose supportedKinds() contains '" + cb.kind() + "'"));
            }
        }
    }

    private static void validateDistributionExtensionPresent(
            MachineSpec spec, Predicate<ResourceLocation> recipeTypeHasExtension, List<ValidationIssue> out) {
        if (spec.validation().mode() != ValidationPolicy.Mode.DISTRIBUTION) {
            return;
        }
        if (!recipeTypeHasExtension.test(spec.recipeType())) {
            out.add(ValidationIssue.error(
                    "/validation/mode",
                    "distribution mode requires a registered RecipeTestExtension for recipeType '" + spec.recipeType()
                            + "' to supply per-channel weights",
                    "register a RecipeTestExtension for '" + spec.recipeType()
                            + "' that overrides weights(), or change validation.mode to 'exact' / 'subset'"));
        }
    }

    private static void validatePrimaryInSlots(ItemBinding outputItems, List<ValidationIssue> out) {
        Optional<Integer> primary = outputItems.primary();
        if (primary.isPresent() && !outputItems.slots().contains(primary.get())) {
            out.add(ValidationIssue.error(
                    "/outputs/items/primary",
                    "primary slot " + primary.get() + " is not in outputs.items.slots " + outputItems.slots(),
                    "set primary to one of " + outputItems.slots()));
        }
    }

    /**
     * Convenience: filter issues for those at {@link ValidationIssue.Severity#ERROR} severity.
     * Empty result means the spec is registry-eligible (warnings still emit but don't block).
     */
    public static List<ValidationIssue> errorsOnly(List<ValidationIssue> issues) {
        return issues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .toList();
    }

    /**
     * Convenience: does this spec validate cleanly (no errors)?
     *
     * @see #errorsOnly(List)
     */
    public static boolean isRegistryEligible(List<ValidationIssue> issues) {
        return errorsOnly(issues).isEmpty();
    }
}
