# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Targets Minecraft 1.21.1 / NeoForge 21.1.213 / Java 21 throughout.

## [Unreleased]

### Added

- `docs/troubleshooting.md` — top spec-authoring mistakes with cause / symptom / fix triplets.
- Spec-authoring help issue template at `.github/ISSUE_TEMPLATE/spec_authoring_help.md`.
- Maven publish workflow keyed on tag pushes; publishes to GitHub Packages with artifact coordinates `dev.recipetest:recipe_test`.
- POM metadata (name, description, license, SCM, issue management) on the published artifact for downstream consumer discoverability.
- Javadoc jar published alongside the main + sources jars.
- `core/GenericRecipeAdapter` — last-resort `RecipeAdapter` registered after the vanilla
  shaped/shapeless adapters. Always applies; pulls items via `Recipe.getIngredients()` and
  the primary output via `Recipe.getResultItem(HolderLookup.Provider)` — the only two
  surfaces the `Recipe<?>` contract guarantees on every recipe regardless of mod. Most
  modded recipes (Forestry's machine recipes, Mekanism's chemical recipes) don't override
  `getIngredients()`, so the generic adapter returns empty inputs for them and the runner
  relies on a registered `RecipeTestExtension` to inject ingredients via `injectInputs()`
  returning `HANDLED` and validate the actual output via `validateOutput()`. Eliminates the
  "no RecipeAdapter applies to this recipe" error for any recipe.

### Changed

- README rewritten consumer-first: pitch + spec example up top, dev commands moved below, dedicated Documentation section.
- Bug-report issue template clarifies "this is for kit bugs, not spec-help."
- Renamed user-facing branding from "Recipe Test Harness" to "NeoForge Recipe Test Kit"
  across mod_name, POM `name`, README, schema description, issue templates,
  `docs/*.md`, `pack.mcmeta`, and the L2 SPI / runner javadoc.
- **Breaking (pre-v1):** Renamed code identifiers to match the new branding:
  `HarnessConfig` → `KitConfig`, `HarnessRegistry` → `KitRegistry`. Imports and
  references updated across the codebase. The `recipe_test` mod ID is unchanged
  (it's a registry namespace, not a display name; renaming would invalidate every
  existing spec datapack at `data/<modid>/recipe_test/machines/`).

### Removed

- `compat/forestry/ForestryCarpenterAdapter` — mod-specific adapter that violated the
  kit's mod-agnostic thru-line. Per-recipe-class extraction now lives in consumer mods
  via the `RecipeTestExtension` SPI; ForestryCE's own L2 extensions ship in ForestryCE's
  source tree, not here.
- Test fixture `compat/forestry/ForestryCarpenterAdapterTest` removed alongside the adapter.
- `RecipeTestMod.onCommonSetup` no longer registers the Forestry-specific adapter; the
  static `RecipeAdapters` block is now the only registration site.

## [1.0.0] — TBD

First public release. Five engineering phases (Bootstrap → Skeleton → Single Recipe →
Bulk-and-Tick-Budgeting → GameTest Auto-Generation → L2 Extension SPI) closed and merged.
The harness is feature-complete and mod-agnostic; real-world examples live in external
consumer mods rather than this repo.

### Highlights

- Datapack-driven JSON spec (one file per machine `RecipeType`).
- Capability-aware runner that drives recipes end-to-end against a live `runGameTestServer`.
- Bulk runner with MSPT budget, deterministic shuffle, and cancellation.
- Auto-generated dynamic GameTests (one per recipe), with JUnit XML output and a drop-in
  GitHub Actions workflow.
- L2 SPI for non-standard recipes (probabilistic outputs, custom `RecipeInput` shapes,
  gas / heat / mana storage), discovered via `ServiceLoader`.

### Phase 5 — L2 Extension SPI (PRs #22, #23, #24, #25, cleanup #27)

- `RecipeTestExtension<R>` SPI: `recipeType()`, `injectInputs()`, `validateOutput()`,
  `tickBudgetOverride()`, `resolveCustomBinding()`, `supportedKinds()`, `weights()`.
- `ExtensionRegistry` walks `ServiceLoader` at common-setup; conflict detection per
  `RecipeType`.
- `CustomHandler` abstraction for non-standard storage kinds.
- Spec validator rejects `validation.mode == "distribution"` without a registered extension
  for the recipe type, and rejects `inputs.custom[].kind` values that no extension claims.
- `DistributionValidator` + `DistributionRunSession`: sample-loop driver that compares
  observed per-channel frequencies to declared weights against a configurable tolerance.
- `docs/extension-authoring.md` walks an outsider through writing a stub extension in
  under 30 minutes against a synthetic example.
- Cleanup PR #27 reverted an in-repo reference subproject (`examples/mekanism-gas-stub/`)
  to keep the harness mod-agnostic; real-world examples live in external consumer mods.

### Phase 4 — GameTest auto-generation (PRs #20, #21)

- `DynamicGameTestGenerator` registers one dynamic test per (spec, recipe) pair.
- `JunitXmlReporter` writes `build/gametest/results/recipe-test.xml` in JUnit/Surefire
  format. Each `<testcase>` carries a `<system-out>` payload with the harness `TestResult`
  JSON for inline diff inspection.
- 5×5×5 air structure template (`empty5.nbt`) bundled with the harness; consumer mods
  don't ship structures.
- `RECIPE_TEST_FILTER` env var (regex over test names) and `RECIPE_TEST_SOFT_FAIL` (demote
  failures to warnings) for CI tuning.
- Drop-in GHA workflow template at `docs/templates/recipe-tests.yml`; `dorny/test-reporter`
  surfaces failing recipe IDs on PRs.
- Five CodeRabbit iterations: jvmArguments heap guidance, race fixes, cache-provider
  consolidation, accumulation reset, MIT cache template.

### Phase 3 — Bulk + Tick Budgeting (PR #18)

- `/recipe_test bulk <recipeType|all>` runs every matching recipe; `/recipe_test cancel`
  halts mid-flight.
- `TickScheduler` advances exactly one runner per tick; respects an MSPT budget
  (`bulkMsptBudgetMs`, default 30 ms) so dev servers stay at 20 TPS.
- Deterministic shuffle keyed on `runId.hashCode()` + stable `(recipeType, holder.id)`
  baseline sort: two consecutive bulk runs against the same recipe set produce identical
  aggregate counts.
- `BulkProgress` + `BulkResult` records with strict count invariants; codecs round-trip.
- `ProgressReporter`: NDJSON progress every N completed runs (default 5), one line per
  emission for rcon clients.
- `HarnessConfig` mod config for runtime knobs.
- `RunStatus.CANCELLED` added; cancellation tick tracks `totalEngineTicks`, `peakMspt`,
  and budget warnings like the normal path.
- `nanoTime()` for elapsed/MSPT measurement; `wallClockMs` clamped ≥ 0 against backward
  `currentTimeMillis()` jumps.
- Six CodeRabbit iterations: scheduler drain ordering, double-publish guards, tighter
  bulk invariants, deterministic shuffle, peek-then-remove job staging.

### Phase 2 — Single recipe execution (PRs #16, #17, follow-up #19)

- `/recipe_test run <recipeType> <recipeId>` queues a recipe against the spec'd machine
  block; `/recipe_test diff <runId>` returns the result. Async, ServerTickEvent-driven.
- `RecipeAdapter` SPI for recipe-input extraction; built-in adapters for vanilla shaped
  and shapeless recipes.
- `RecipeTestRunner` drives placement → injection → tick advancement → output read →
  diff. Cleans up the placed block on completion.
- `ResultDiffer` produces `DiffPayload` with per-channel mismatches; supports
  `validation.mode = "exact" | "subset" | "distribution"` and `nbtCompare = "exact" |
  "structural" | "ignore"`.
- `CapabilityProbe` warns at server start when a spec'd block doesn't expose the
  capability the spec declares.
- `Snapshots` records (`ItemSnapshot`, `FluidSnapshot`, `IoSnapshot`) decouple report
  payload from `ItemStack`/`FluidStack` so codecs and equality work without bootstrapping.
- ForestryCE Carpenter adapter (PR #19): reflection-based, registered unconditionally,
  inert when ForestryCE absent. End-to-end PASS verified locally.
- Five CodeRabbit iterations: TICK off-by-one, scheduler drain via poll/offer, callback
  isolation via `safePublish`, double-publish guard on `tearDown` failure, lifecycle
  command level binding.

### Phase 1 — Skeleton + JSON spec (PRs #14, #15)

- `MachineSpec` record tree + JSON codec: `version`, `recipeType`, `block`, `blockState`,
  `neighbors`, `inputs`, `outputs`, `energy`, `tickBudget`, `validation`, `lifecycle`.
- `SpecValidator` with seven rules (record-level invariants, layout/slot consistency,
  custom-binding kind resolution, recipe-type / block registry checks via injected
  predicates for testability).
- Atomic registry swap: `HarnessRegistry.specs` is `AtomicReference<Map<...>>`; readers
  see either the pre-reload or post-reload snapshot, never an in-flight rebuild.
- `/recipe_test list [<recipeType>]` and `/recipe_test schema` commands; all subcommands
  op-gated (level 2).
- JSON Schema export (Draft 7) at `<world>/recipe_test/schema/machine_spec.schema.json`
  for VS Code completion.
- 70 unit tests covering codec round-trips, validator rules, registry concurrency.

### Phase 0 — Bootstrap (PR #1)

- NeoForge MDK skeleton with full quality + CI stack: Spotless (Palantir Java Format),
  Checkstyle, ErrorProne, NullAway (broad scope: `dev.recipetest`), JaCoCo (60% line
  floor), Gradle Versions Plugin, Apache-2.0 license, JUnit 5.
- GitHub Actions workflows: CI (build, coverage, markdown lint, secret scan), CodeQL,
  PR labeler, PR title gate (semantic), Release Drafter, Dependabot.
- Issue templates (bug, feature), CODEOWNERS, PR template, maintainer setup guide.
- Pinned versions: NeoGradle 7.0.170, Parchment 2024.11.17, Spotless 6.25.0, ErrorProne
  plugin 4.0.1, NullAway 0.11.3, Checkstyle 10.18.1, JaCoCo 0.8.12.

### Dependency updates (Dependabot, PRs #2-#13)

- Bumps for `actions/checkout`, `actions/setup-java`, `actions/upload-artifact`,
  `actions/download-artifact`, `actions/labeler`, `gradle/actions`,
  `github/codeql-action`, `codecov/codecov-action`, `release-drafter`,
  `amannn/action-semantic-pull-request`, `org.junit:junit-bom`.

[Unreleased]: https://github.com/ericfisherdev/NeoForge-Recipe-Test-Tools/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/ericfisherdev/NeoForge-Recipe-Test-Tools/releases/tag/v1.0.0
