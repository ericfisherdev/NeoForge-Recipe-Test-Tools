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

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side mod config controlling the harness's runtime knobs. Registered against
 * {@code ModConfig.Type.SERVER} from {@code RecipeTestMod} so each world has its own values.
 *
 * <p>All entries are read directly from the {@link ModConfigSpec.IntValue} accessors at
 * use-site — there is no caching layer. NeoForge's config system already returns a hot snapshot
 * after world load, so reads are cheap; that lets the user tweak values via {@code
 * world/serverconfig/recipe_test-server.toml} without restarting.
 */
public final class HarnessConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Per-tick MSPT budget the bulk scheduler may spend advancing runners. */
    public static final ModConfigSpec.IntValue BULK_MSPT_BUDGET_MS = BUILDER.comment(
                    "Maximum milliseconds the bulk recipe-test runner may spend in any one server tick.",
                    "Lower values keep the live game responsive at the cost of slower bulk completion.")
            .defineInRange("bulkMsptBudgetMs", 30, 1, 200);

    /** Emit a progress line every N completed recipes. */
    public static final ModConfigSpec.IntValue PROGRESS_REPORT_EVERY_N = BUILDER.comment(
                    "Emit a bulk progress line after this many completed recipes (whichever fires first vs.",
                    "the every-ticks gate). Set to 1 to stream every result.")
            .defineInRange("progressReportEveryN", 5, 1, 1000);

    /** Time-based fallback for progress emission. */
    public static final ModConfigSpec.IntValue PROGRESS_REPORT_EVERY_TICKS = BUILDER.comment(
                    "Maximum ticks between bulk progress lines (whichever fires first vs. the every-N gate).")
            .defineInRange("progressReportEveryTicks", 100, 1, 100_000);

    /** Floor applied to {@code TickBudget.AUTO} resolutions. */
    public static final ModConfigSpec.IntValue DEFAULT_TICK_BUDGET_FLOOR = BUILDER.comment(
                    "Minimum tick budget the harness will assume when a spec uses tickBudget=\"auto\".",
                    "Recipe-specific processingTime, when known, takes precedence.")
            .defineInRange("defaultTickBudgetFloor", 200, 1, 100_000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private HarnessConfig() {}
}
