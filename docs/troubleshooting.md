# Troubleshooting

Common spec-authoring mistakes and how to fix them. Each entry has a **symptom**
(what you see), a **cause** (what's actually wrong), and a **fix** (the smallest
change that resolves it). Cross-references to `json-spec.md` rules and to the
test fixtures live alongside each entry.

## 1. "Recipe never matches" — slot layout mismatch

**Symptom.** `/recipe_test run` returns `TIMEOUT`, the kit logs report
`tickBudget exceeded with no output produced`, and the machine never produces
anything despite the inputs being placed.

**Cause.** Your `inputs.items.layout` says `shaped3x3` but the recipe pattern
isn't actually 3×3, or the slot indices in `slots` don't match the slot layout
of the machine's `IItemHandler`. The kit inserts ingredients at the slots
your spec declares; the machine's BE matches the recipe against its actual
inventory layout. If those disagree, the BE never sees a valid input and never
runs the recipe.

**Fix.** Cross-check three things:

1. The recipe pattern dimensions (`Recipe.getWidth()` / `getHeight()`) match the
   `layout` you declared.
2. The slot indices in `slots` correspond to the machine's actual input slots.
   For a custom BE, this is the slot range you registered with the
   `ItemStackHandler`.
3. The `capability` and `side` resolve to the same handler the BE uses for
   recipe matching. Some machines expose different handlers per side (input
   from `north`, output to `south`); using the wrong side gives you an empty
   handler.

When in doubt, drop a `lifecycle.preTickCommands` entry like
`"data get block ~ ~ ~"` and confirm via console that the items landed in the
slots you expect.

## 2. "Block not found" or `unresolved block`

**Symptom.** `/recipe_test list <recipeType>` reports the spec was loaded but
the runtime probe in `[recipe_test] capability missing on side X` warning
fires for `block` itself, or `/recipe_test run` immediately fails with
`Block 'yourmod:carpenter' not registered`.

**Cause.** The `block` field references a block that doesn't exist or whose
namespace is wrong. Common variants:

- Mod ID typo (`fortry:carpenter` vs `forestry:carpenter`).
- The block is registered under a different name than you assumed (e.g.
  Forestry registers `forestry:carpenter`, but its `RecipeType` may live at
  `forestry:carpenter` *or* at a different path).
- The mod isn't on the classpath when the spec loads.

**Fix.** In-game, run `/recipe_test list` to see the spec parse and then
`/give @s yourmod:carpenter_block` — if the give command fails, the registry
ID is wrong. Use `/data get block <x> <y> <z>` on a real machine instance to
confirm the actual block ID.

## 3. "Energy never consumed"

**Symptom.** Spec declares `energy.preFill: 1000000`, the kit reports
`energyConsumed: 0` in diagnostics, and the recipe TIMEOUTs.

**Cause.** Either:

- `energy.capability` doesn't match the BE's actual capability. NeoForge's
  built-in is `IEnergyStorage` (the kit's alias is just `EnergyStorage` in
  spec syntax); some mods wrap it in their own capability that doesn't accept
  Forge Energy.
- `energy.side` is `ANY` but the BE only accepts energy from a specific side.
  `ANY` resolves to the *first* responding side; if that side rejects energy
  insertion, you're stuck.
- The machine consumes energy *from a different storage* than the one the
  capability exposes (e.g. it has an internal buffer, and the public capability
  is read-only from the outside).

**Fix.** Check the mod's source for the side bindings on its energy
capability. If the energy is internal, you may need to use
`lifecycle.preTickCommands` with `data merge block` to seed the BE NBT
directly, the way the Forestry Carpenter procedure in the project's plan
documents (`progress.md` Phase 2 lessons learnt).

## 4. "Output mismatch — phantom NBT diff"

**Symptom.** The recipe runs, the output item is correct, but the kit
reports `FAIL` with a `nbt` mismatch on the output. The diff shows a property
like `custom_data` or `display.lore` differs even though you didn't set those
yourself.

**Cause.** The recipe's "result" item carries components (NBT in the legacy
sense) that the wrapped recipe form doesn't surface. The wrapped form returns
the inner item without components; the outer form returns it with components.
The kit compares against `Recipe.getResultItem()`, which by default is the
outer call — but built-in adapters for some recipe types pull from the inner
call inadvertently.

**Fix.** Either:

- Set `validation.nbtCompare: "structural"` to ignore component-level NBT
  drift. This is the right answer when the components are decorative (lore,
  custom_data tags that don't affect gameplay).
- File an issue with the recipe class name and a sample expected vs actual NBT
  blob. Most "phantom NBT diff" cases are bugs in the kit's adapter that we
  fix once and ship.

This was the cause of the bug fixed in `e3433b6` for the ForestryCE Carpenter
adapter — see `progress.md` Phase 2 lessons learnt for the full forensic.

## 5. "Distribution mode FAILs every channel"

**Symptom.** A spec with `validation.mode: "distribution"`,
`validation.samples: 1000`, `validation.tolerance: 0.05` fails with every
channel reporting `expected weight unknown` or every channel `0.0` expected.

**Cause.** Distribution mode requires a registered `RecipeTestExtension` for
your `recipeType` that implements `weights()`. Without an extension, the
kit has no source of per-channel weights — every observed frequency
compares against an empty expected map and fails.

**Fix.** Either:

1. Register an extension via the SPI (see
   [`extension-authoring.md`](extension-authoring.md)) that returns the
   per-channel weights from your `Recipe` instance.
2. Switch to `validation.mode: "exact"` if your recipe is actually
   deterministic (no probabilistic outputs).

The kit now rejects this misconfiguration at spec-load time with a clear
"distribution mode requires a registered extension" message — but if you saw
the older "FAIL every channel" symptom, this is what was happening underneath.

## 6. "Distribution mode is flaky"

**Symptom.** A distribution-mode spec passes locally but flakes in CI, or
passes most runs and fails maybe one in twenty.

**Cause.** Your `samples` count is too low for the `tolerance` you set, given
the underlying probability distribution. The standard rule of thumb is
`tolerance ≥ 3·sqrt(p·(1-p)/samples)` for a 99.7% confidence interval on each
channel.

**Fix.** Bump `samples`, loosen `tolerance`, or both. For a recipe with one
channel at `p ≈ 0.5`, 1000 samples at `tolerance: 0.05` is usually robust.
Tighter tolerances (`0.01` or below) need 10× the samples.

## 7. "Custom binding kind not resolved"

**Symptom.** Server start fails with
`unresolved custom binding kind 'yourmod:gas'`.

**Cause.** Your spec declares `inputs.custom[].kind = "yourmod:gas"` but no
registered `RecipeTestExtension` claims that kind via `supportedKinds()` or
returns a non-empty result from `resolveCustomBinding()`.

**Fix.** Implement and register a `RecipeTestExtension` whose
`supportedKinds()` includes the kind, and whose `resolveCustomBinding()`
returns a `CustomHandler` for it. Walk-through:
[`extension-authoring.md`](extension-authoring.md).

## 8. "Reload doesn't pick up spec changes"

**Symptom.** Edit a spec file in `data/<modid>/recipe_test/machines/`, run
`/reload`, but `/recipe_test list` still shows the old version.

**Cause.** Either:

- The file path is wrong (case-sensitive on Linux; `Machines` ≠ `machines`).
- The datapack was disabled. Run `/datapack list` to confirm yours is in
  `Available datapacks` *and* `Enabled datapacks`.
- The spec failed to load and the loader fell back to the previous registry
  snapshot. Check the server log for `[recipe_test] validation` ERROR entries.

**Fix.** Read the validation errors at the top of the server log immediately
after `/reload`. The kit uses an atomic swap: a failed reload leaves the
previous snapshot in place rather than corrupting the registry, which is why
you see the old version persist.

## 9. "Bulk run is slow / times out"

**Symptom.** `/recipe_test bulk yourmod:carpenter` against 50 recipes takes
several minutes, or the wall-clock budget exceeds CI job limits.

**Cause.** The default `bulkMsptBudgetMs` is 30 ms, meaning the scheduler
drains at most 30 ms of work per tick. At 20 TPS that's 60% of available
server time; the rest goes to the regular game loop. If your recipes
themselves are slow (warmup ticks, animation phases), wall-clock cost stacks
up.

**Fix.** Either:

- Increase `bulkMsptBudgetMs` in the kit's config (trade TPS headroom for
  throughput).
- Use `RECIPE_TEST_FILTER` to narrow the run to a subset.
- Profile a single representative recipe to see if its `tickBudget` is
  inflated; the per-recipe budget is what dominates wall-clock.

## 10. "JUnit XML report empty / missing"

**Symptom.** `runGameTestServer` finishes, the server logs report passing
tests, but `build/gametest/results/recipe-test.xml` is missing or has no
testcases.

**Cause.** Either:

- The kit wasn't on the classpath at boot (your `build.gradle` is missing
  the dep, or the dep is `compileOnly` instead of `implementation`).
- No specs were discovered. The loader scans
  `data/<modid>/recipe_test/machines/`; if no datapack contributed any files
  there, the registry is empty and no tests are generated.
- `RECIPE_TEST_FILTER` filtered everything out — its regex is overly strict
  (e.g. anchored with `^` when you meant a substring match).

**Fix.** Run `/recipe_test list` after `runGameTestServer` boots interactively
(swap the run task for `runServer` temporarily) to confirm specs loaded. The
XML report is only written if at least one dynamic test was registered.

## Where to file issues

If the symptom doesn't match anything above, open an issue at
[github.com/ericfisherdev/NeoForge-Recipe-Test-Tools/issues](https://github.com/ericfisherdev/NeoForge-Recipe-Test-Tools/issues)
using the **Spec authoring help** template. Attach:

- Your spec JSON.
- The recipe JSON.
- The relevant section of the server log.
- The `<system-out>` payload from the JUnit XML if `runGameTestServer` ran.

## See also

- [`consumer-quickstart.md`](consumer-quickstart.md) — initial onboarding
- [`extension-authoring.md`](extension-authoring.md) — when JSON isn't enough
- [`ci.md`](ci.md) — CI tuning, `RECIPE_TEST_FILTER`, memory
