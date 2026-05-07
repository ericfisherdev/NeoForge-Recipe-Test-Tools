# Consumer Quickstart

Five-minute path from "I have a NeoForge mod with custom recipes" to "every recipe
is auto-tested on every PR." Aimed at mod authors using the recipe_test harness for
the first time.

## Prerequisites

- NeoForge 21.1.213 (Minecraft 1.21.1)
- Java 21
- A mod that ships custom recipes via vanilla's recipe-codec system

## 1. Add the dependency

In your mod's `build.gradle`:

```groovy
dependencies {
    implementation "net.neoforged:neoforge:${neo_version}"
    implementation "dev.recipetest:recipe-test:${recipe_test_version}"
}
```

In `src/main/resources/META-INF/neoforge.mods.toml`, declare the dependency so the
loader pulls it in:

```toml
[[dependencies.<your_modid>]]
modId = "recipe_test"
type = "required"
versionRange = "[1.0,)"
ordering = "BEFORE"
side = "BOTH"
```

## 2. Write one spec per recipe type

Drop a JSON file at `src/main/resources/data/<your_modid>/recipe_test/machines/<name>.json`:

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

Full schema reference lives in [`json-spec.md`](../localfiles/docs/specs/json-spec.md).
Validate locally with:

```sh
./gradlew runServer
# in-game: /reload, then check the logs for [recipe_test] validation messages
```

## 3. Run tests locally

```sh
./gradlew runGameTestServer --no-daemon
```

You should see:

```
[recipe_test] registered N dynamic GameTest(s)
[recipe_test]   recipe_test.yourmod.carpenter.yourmod.recipe_one
[recipe_test]   recipe_test.yourmod.carpenter.yourmod.recipe_two
...
```

When done, `build/gametest/results/recipe-test.xml` has the JUnit-format report;
the server prints the usual `N required tests passed :)` summary.

## 4. Wire it into CI

Copy [`docs/templates/recipe-tests.yml`](templates/recipe-tests.yml) into
`.github/workflows/recipe-tests.yml`. On the next push, the GHA check runs your
recipes, uploads the JUnit XML, and surfaces failures on the PR via
`dorny/test-reporter`.

See [`ci.md`](ci.md) for tuning options (`RECIPE_TEST_FILTER`,
`RECIPE_TEST_SOFT_FAIL`, memory, namespace gotchas).

## 5. Add a deliberately broken spec to verify the pipeline works

Quick sanity check before merging:

```json
{
  "version": 1,
  "recipeType": "yourmod:carpenter",
  "block": "yourmod:carpenter_block",
  "inputs": {
    "items": {
      "capability": "minecraft:item_handler",
      "side": "north",
      "slots": [99]
    }
  },
  "outputs": { ... }
}
```

Slot `99` doesn't exist on the block, so injection will fail; the JUnit report
should show one `<failure>` with `recipe-test FAIL ... 1 mismatch(es)`. Revert
once you've confirmed the workflow surfaces the failure on the PR.

## What you don't have to do

- **No Java test code.** Every recipe with a matching spec gets a test for free.
- **No structure NBTs.** The harness ships a 5×5×5 air template that every test
  loads.
- **No batch coordination.** All dynamic tests run in a single batch named
  `recipe_test_dynamic`; only one runs at a time, so there's no chance of two
  harness runners colliding on the same chunk.

## When recipes don't fit the JSON path

If your recipe has probabilistic outputs, custom RecipeInput types, gas systems,
or other shape-breakers, Phase 5's L2 extension SPI is the escape hatch. Until
that ships, write a small `@GameTest`-annotated method by hand for those recipes
and let the dynamic generator handle the rest.

## Where to file issues

Open an issue at <https://github.com/ericfisherdev/NeoForge-Recipe-Test-Tools/issues>
with:

- The failing test name (e.g. `recipe_test.yourmod.carpenter.yourmod.broken`)
- The contents of `<system-out>` from the JUnit XML — it includes the harness
  status, ticks elapsed, warnings, and (on FAIL) the `resultJson` payload
- A link to the spec and recipe JSONs in your repo
