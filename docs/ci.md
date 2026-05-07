# CI Integration

The recipe_test harness runs every recipe-spec pair as a dynamic GameTest. Pointing
GitHub Actions at `runGameTestServer` is enough to get one CI test per loaded recipe
without writing any Java.

## Quick start

1. Copy [`docs/templates/recipe-tests.yml`](templates/recipe-tests.yml) into your consumer
   mod at `.github/workflows/recipe-tests.yml`.
2. Confirm your `build.gradle`'s `runs.gameTestServer` block already exists (the
   NeoGradle template includes it).
3. Push. The workflow runs `./gradlew runGameTestServer`, the harness walks loaded mod
   jars to find every spec/recipe pair, and the JUnit XML reporter writes
   `build/gametest/results/recipe-test.xml`.

## Output format

`build/gametest/results/recipe-test.xml` is a JUnit-format file with one
`<testcase>` per dynamic recipe test. Test names follow the format
`recipe_test.<typeNs>.<typePath>.<idNs>.<idPath>` — the same scheme Gradle's
`--tests` flag accepts. Failures carry the harness `TestResult` JSON in
`<system-out>` so reviewers can see the diff inline:

```xml
<testcase classname="dev.recipetest.gametest.DynamicGameTestGenerator"
          name="recipe_test.forestry.carpenter.forestry.circuit_board_basic"
          time="0.450">
  <system-out>
recipeId=forestry:carpenter/circuit_board_basic
recipeType=forestry:carpenter
specSource=forestry:carpenter.json
status=PASS
ticksElapsed=24
  </system-out>
</testcase>
```

The XML validates as a Surefire-compatible suite, so any reporter that accepts
JUnit XML (`dorny/test-reporter`, `mikepenz/action-junit-report`, GitHub's built-in
test summary, GitLab CI, Buildkite Test Analytics) will render it.

## Environment variables

| Variable               | Purpose                                                                                          |
| ---------------------- | ------------------------------------------------------------------------------------------------ |
| `RECIPE_TEST_FILTER`   | Regex applied to test names at registration. Filtered-out recipes never appear in the registry. |
| `RECIPE_TEST_SOFT_FAIL`| `true` demotes any non-PASS run to a logged warning. Use during triage / bisect runs.            |

The filter regex uses `Pattern.find` (substring match), so
`recipe_test\.forestry\.carpenter\.` matches all carpenter tests; anchor with
`^...$` if you need exact matches. An invalid regex is logged and ignored — no
tests get filtered.

## Memory tuning

`runGameTestServer` boots a real Minecraft server. The default `4G` heap (`-Xmx4G`)
fits a few hundred recipes; mods with large neighbour blueprints or many machines
should bump to `6G`–`8G`:

```yaml
env:
  GRADLE_OPTS: -Xmx8G -Dfile.encoding=UTF-8
```

If you're seeing OOM failures *during chunk loading* (not during a specific recipe),
the heap is the suspect; if a single recipe is slow, the per-test `timeoutTicks` is
where to look (see "tuning timeouts" below).

## Scoping CI runs

Combine `RECIPE_TEST_FILTER` with Gradle's `--tests` for two layers of selection:

```sh
# At registration: only register carpenter tests.
RECIPE_TEST_FILTER='recipe_test\.forestry\.carpenter\.' \
    ./gradlew runGameTestServer

# At execution: register everything but only run one specific recipe.
./gradlew runGameTestServer \
    --tests recipe_test.forestry.carpenter.forestry.circuit_board_basic
```

`--tests` works because every dynamic test is a real `TestFunction` in vanilla's
`GameTestRegistry`. Wildcards work the same way (`--tests 'recipe_test.*.basic'`).

## Tuning timeouts

Each generated TestFunction is given `maxTicks = 260` by default
(`DEFAULT_AUTO_BUDGET=200` plus a `60`-tick buffer). Recipes whose actual budget
exceeds 200 ticks will TIMEOUT inside the GameTest framework even when the harness
runner would have eventually passed them. If your CI keeps surfacing TIMEOUT for
long-running recipes:

1. Bump the spec's `tickBudget` in your spec JSON — this is the runner's own cap.
2. (Once per-spec timeouts are wired) the framework `timeoutTicks` will follow the
   spec automatically. Until then, the simplest fix is splitting CI into a fast
   pass (filter out the slow recipes) and a slow pass that waives the timeout via
   `RECIPE_TEST_SOFT_FAIL=true`.

## Namespace gotcha

Every dynamic test ships under the `recipe_test` namespace, regardless of which mod
owns the underlying recipe. If your `runGameTestServer` task sets
`-Dneoforge.enabledGameTestNamespaces=<your_modid>`, the recipe_test tests get
filtered out — change it to `recipe_test`, or leave it empty:

```groovy
runs {
    gameTestServer {
        // empty = no namespace filter; recipe_test is always included.
        systemProperty 'neoforge.enabledGameTestNamespaces', ''
    }
}
```

## What's *not* covered

- **World datapacks.** The scanner walks loaded mod jars, not server-owner packs in
  `world/datapacks/`. Tests that depend on a datapack-only override won't appear.
- **Probabilistic outputs.** Phase 5 adds the L2 SPI for recipes whose outputs vary
  per run. Until then, the harness compares against the recipe's declared output
  exactly.
- **Multi-machine pipelines.** Out of scope for v1; tracked in the project roadmap.

## Troubleshooting

| Symptom                                    | Likely cause                                                                                  |
| ------------------------------------------ | --------------------------------------------------------------------------------------------- |
| `0 dynamic GameTest(s)` in logs            | No specs found under `data/<modid>/recipe_test/machines/`. Check namespace casing.            |
| `recipe not loaded` failure                | Recipe JSON exists but datapack failed to load it. Inspect server log for codec errors.       |
| `Missing test structure: recipe_test:empty5` | The recipe_test mod isn't on the classpath at runtime. Check `dependencies` in `build.gradle`. |
| `RecipeAdapter not found`                  | Recipe type matches a spec but no adapter handles it. Phase 5's L2 SPI is the escape hatch.   |
