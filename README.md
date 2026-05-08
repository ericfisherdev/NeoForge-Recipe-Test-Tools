# NeoForge Recipe Test Kit

Datapack-driven recipe testing kit for NeoForge mods. Drop one JSON spec
per machine into your mod, ship a CI workflow, and every recipe becomes an
auto-generated GameTest that runs against a real `runGameTestServer`.

[![CI](https://github.com/ericfisherdev/NeoForge-Recipe-Test-Tools/actions/workflows/ci.yml/badge.svg?branch=1.21.1)](https://github.com/ericfisherdev/NeoForge-Recipe-Test-Tools/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

**Targets:** Minecraft 1.21.1 / NeoForge 21.1.213 / Java 21.

## Why

Modded machine recipes break in three ways that vanilla `recipes/` files don't
catch:

1. The recipe parses fine, but the **block can't accept the inputs** because
   the slot indices, capability sides, or layout in your machine's
   `IItemHandler` don't match the recipe's expectations.
2. The recipe runs, but takes **longer than your tick budget** — so the
   integration regresses on a future hotfix.
3. The recipe runs, but produces **subtly different outputs** (NBT drift, tag
   loss, item count off-by-one).

The harness drives every recipe end-to-end against a live server, asserts the
machine produced what the recipe says it should, and surfaces deltas on PRs.
No `@GameTest` boilerplate per recipe — one spec covers every recipe of a
given type.

## What you write

One JSON file per machine type:

```json
{
  "version": 1,
  "recipeType": "yourmod:carpenter",
  "block": "yourmod:carpenter_block",
  "inputs": {
    "items": {
      "capability": "minecraft:item_handler",
      "side": "north",
      "slots": [0, 1, 2, 3, 4, 5, 6, 7, 8],
      "layout": "shaped3x3"
    }
  },
  "outputs": {
    "items": {
      "capability": "minecraft:item_handler",
      "side": "south",
      "slots": [9],
      "primary": 9
    }
  },
  "tickBudget": "auto",
  "validation": "exact"
}
```

That's it for any recipe whose machine exposes its inventory through standard
NeoForge `IItemHandler` / `IFluidHandler` / `IEnergyStorage` capabilities. For
machines that don't (probabilistic outputs, gas tanks, custom `RecipeInput`
shapes), an L2 Java SPI is documented in
[`docs/extension-authoring.md`](docs/extension-authoring.md).

## What you get

- **Auto-generated GameTests.** Every recipe of every spec'd type becomes a
  named test; one run, one JUnit XML report.
- **CI integration.** Drop in the workflow template at
  [`docs/templates/recipe-tests.yml`](docs/templates/recipe-tests.yml); failures
  surface on PRs via `dorny/test-reporter` with the failing recipe id and the
  mismatch payload.
- **In-game commands.** `/recipe_test run`, `/diff`, `/bulk`, `/cancel`,
  `/list`, `/schema` for interactive debugging.
- **Bulk + tick budgeting.** `/recipe_test bulk forestry:carpenter` runs every
  matching recipe within an MSPT budget so dev servers stay at 20 TPS.
- **Distribution mode.** Probabilistic recipes (centrifuge-style weighted
  outputs) get a sample-loop driver and a per-channel tolerance verdict.

## Quick start

**Consumer mods** — add the harness as a dep, write a spec, run it:

→ [`docs/consumer-quickstart.md`](docs/consumer-quickstart.md) — five-minute
walkthrough from "I have a mod" to "every recipe is auto-tested on every PR."

**This repo (development)** — building / testing the harness itself:

```sh
./gradlew clean build check    # build + tests + spotless + checkstyle + errorprone + nullaway + jacoco
./gradlew runClient            # launches Minecraft client with the mod loaded
./gradlew runServer            # launches a dedicated server with the mod loaded
./gradlew runGameTestServer    # boots a GameTest server; runs every dynamic recipe test
./gradlew spotlessApply        # auto-fix formatting + license headers
```

Coverage report after `./gradlew test`:
`build/reports/jacoco/test/html/index.html`

JUnit XML report after `./gradlew runGameTestServer`:
`build/gametest/results/recipe-test.xml`

## Documentation

- [`docs/consumer-quickstart.md`](docs/consumer-quickstart.md) — five-minute
  consumer onboarding
- [`docs/extension-authoring.md`](docs/extension-authoring.md) — L2 Java SPI
  for non-standard recipe shapes
- [`docs/troubleshooting.md`](docs/troubleshooting.md) — common spec authoring
  mistakes and how to fix them
- [`docs/ci.md`](docs/ci.md) — CI tuning (`RECIPE_TEST_FILTER`,
  `RECIPE_TEST_SOFT_FAIL`, memory, namespace gotchas)
- [`docs/templates/recipe-tests.yml`](docs/templates/recipe-tests.yml) — drop-in
  GitHub Actions workflow

## Repo layout

```text
src/main/java/dev/recipetest/
  api/          Public-facing extension surface (Phase 5)
  spec/         JSON spec + codecs + loader      (Phase 1)
  core/         Simulation engine                 (Phases 2-3)
  command/      /recipe_test command surface      (Phase 2)
  gametest/     GameTest auto-generation          (Phase 4)
  compat/       Built-in adapters (e.g. ForestryCE Carpenter)
src/main/resources/
  META-INF/neoforge.mods.toml
  pack.mcmeta
src/test/java/dev/recipetest/
  ...
```

## Status

Harness is feature-complete through Phase 5 (L2 SPI). The
[`CHANGELOG.md`](CHANGELOG.md) tracks every release; the rolling release notes
are draft-published by Release Drafter.

## Contributing

Issues welcome at
[github.com/ericfisherdev/NeoForge-Recipe-Test-Tools/issues](https://github.com/ericfisherdev/NeoForge-Recipe-Test-Tools/issues);
templates exist for bug reports, spec-authoring help, and feature requests.

If you're sending PRs, run the one-time hooks setup after cloning so local
checks match CI exactly:

```sh
./scripts/setup-hooks.sh
```

This wires `.githooks/pre-commit` (markdownlint on staged `.md` files) and
`.githooks/pre-push` (`./gradlew build check` + full markdownlint + optional
`gitleaks`). Both match the `markdownlint-cli2` version pinned by the CI
workflow (currently `v0.14.0`).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
