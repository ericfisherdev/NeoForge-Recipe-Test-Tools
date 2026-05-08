# NeoForge Recipe Test Harness

Datapack-driven recipe testing harness for NeoForge mods. This worktree targets
**Minecraft 1.21.1 / NeoForge 21.1.213 / Java 21**.

## Status

Phase 0 (bootstrap) — empty but buildable. The full phase roadmap lives outside
this repo in the maintainer's planning vault; per-phase status is mirrored in
PR descriptions and release-drafter notes.

## Quick start

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

Consumer mods integrating the harness should start with
[`docs/consumer-quickstart.md`](docs/consumer-quickstart.md); the matching CI
workflow template lives at
[`docs/templates/recipe-tests.yml`](docs/templates/recipe-tests.yml). Tuning options
and troubleshooting are in [`docs/ci.md`](docs/ci.md). When JSON specs aren't
enough — probabilistic outputs, gas/heat tanks, custom `RecipeInput` shapes — the
L2 escape hatch is documented at
[`docs/extension-authoring.md`](docs/extension-authoring.md).

## Repo layout

```text
src/main/java/dev/recipetest/
  api/          Public-facing extension surface (Phase 5)
  spec/         JSON spec + codecs + loader      (Phase 1)
  core/         Simulation engine                 (Phases 2-3)
  command/      /recipe_test command surface      (Phase 2)
  gametest/     GameTest auto-generation          (Phase 4)
src/main/resources/
  META-INF/neoforge.mods.toml
  pack.mcmeta
src/test/java/dev/recipetest/
  RecipeTestModTest.java        Smoke test
```

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
