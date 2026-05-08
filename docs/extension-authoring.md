# Extension Authoring Guide

NeoForge Recipe Test Kit (`recipe_test`) covers about 80% of NeoForge machine recipes through
JSON specs alone (the L1 path). The other 20% — recipes with probabilistic outputs, custom
`RecipeInput` shapes, gas/heat/mana storage, multi-block coordination — register a small
`RecipeTestExtension` to fill in what JSON can't express. This guide walks through writing one
from scratch.

## When you actually need an extension

Try the JSON-only path first. Reach for an extension only when:

- The recipe has **probabilistic outputs** (centrifuges, crushers, ore processing). The
  kit can't infer the per-channel weights from `Recipe.getResults()` — most modded
  probabilistic recipes embed the weights in the recipe class itself, so the kit needs
  your code to reach in and pull them out.
- The recipe consumes inputs from a **non-standard storage type**: gas tanks, heat reservoirs,
  mana pools, anything outside `IItemHandler` / `IFluidHandler` / `IEnergyStorage`.
- The recipe's **`RecipeInput` shape** isn't a list of stacks — recipes that take a single
  composite ingredient, recipes that read from a multi-block grid.
- The recipe's **processing time is on the recipe object**, not on your spec, and you want
  the kit to honour it without making every consumer hand-author tickBudget per recipe.

If your recipe fits the standard "items in, items/fluids/energy out" shape, the JSON spec is
enough — you don't need this guide.

## What you ship

An extension consists of:

1. A **Java class** implementing `dev.recipetest.api.RecipeTestExtension<R>`, where `R` is your concrete `Recipe` subtype.
2. A **services file** at `src/main/resources/META-INF/services/dev.recipetest.api.RecipeTestExtension` listing the fully-qualified class name.
3. A **`mods.toml` dependency** on `recipe_test` so your jar declares the kit as a
   runtime requirement.

That's it. There's no annotation processor, no registration event, no DI container. The
kit's `ExtensionRegistry` walks `ServiceLoader` at common-setup time and indexes whatever
it finds.

## The SPI surface

```java
public interface RecipeTestExtension<R extends Recipe<?>> {
    ResourceLocation recipeType();

    default InjectionDecision injectInputs(TestContext ctx, RecipeHolder<R> holder) {
        return InjectionDecision.FALL_THROUGH;
    }

    default Optional<TestResult> validateOutput(TestContext ctx, RecipeHolder<R> holder, IoSnapshot actual) {
        return Optional.empty();
    }

    default int tickBudgetOverride(RecipeHolder<R> holder) {
        return -1;
    }

    default Optional<CustomHandler> resolveCustomBinding(ResourceLocation kind, String ref, BlockEntity be) {
        return Optional.empty();
    }

    default Set<ResourceLocation> supportedKinds() {
        return Set.of();
    }

    default Map<String, Double> weights(RecipeHolder<R> holder) {
        return Map.of();
    }

    enum InjectionDecision { HANDLED, FALL_THROUGH }
}
```

Every method has a default. You override only the hooks you need, and the kit keeps doing
its L1 thing for the rest.

## Hook reference

| Hook                      | When to override                                                                                                                  | What returning the default means                                                  |
| ------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `recipeType()`            | Always (this is how the registry indexes you).                                                                                    | N/A — required.                                                                   |
| `injectInputs()`          | Your recipe takes inputs the L1 path can't express (gas, custom RecipeInput).                                                     | `FALL_THROUGH` → kit's L1 inject runs.                                            |
| `validateOutput()`        | You want full control of the actual-vs-expected diff. Distribution-mode runs handle their own validation, so most don't need this.| `Optional.empty()` → kit's L1 differ runs.                                        |
| `tickBudgetOverride()`    | The recipe carries `processingTime` as a field instead of as a spec field.                                                        | `-1` → spec's `tickBudget` is used.                                               |
| `resolveCustomBinding()`  | You need to inject/read from non-item/fluid/energy storage. Pair with `supportedKinds()`.                                         | `Optional.empty()` → spec validation rejects the binding's `kind`.                |
| `supportedKinds()`        | You declare which `CustomBinding.kind` values your `resolveCustomBinding()` can handle. Used by spec validator at server start.   | Empty set → spec referencing your kind fails validation.                          |
| `weights()`               | Distribution-mode recipes — return the expected per-channel probability map.                                                      | Empty map → distribution validator FAILs every channel against an empty expected. |

## A worked example: weighted output extension

Imagine a hypothetical mod's `MyCentrifugeRecipe` that returns a list of
`WeightedOutput(ItemStack stack, float weight)` from `getProducts()`. The recipe is
probabilistic — each run produces one of N possible items. The kit's L1 path can't
validate this because it doesn't know the weights.

```java
package com.example.mymod.recipetests;

import com.example.mymod.MyCentrifugeRecipe;
import dev.recipetest.api.RecipeTestExtension;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class MyCentrifugeExtension implements RecipeTestExtension<MyCentrifugeRecipe> {

    private static final ResourceLocation RECIPE_TYPE =
            ResourceLocation.fromNamespaceAndPath("mymod", "centrifuge");

    @Override
    public ResourceLocation recipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Map<String, Double> weights(RecipeHolder<MyCentrifugeRecipe> holder) {
        Map<String, Double> out = new HashMap<>();
        for (var weighted : holder.value().getProducts()) {
            String channel = BuiltInRegistries.ITEM.getKey(weighted.stack().getItem()).toString();
            out.merge(channel, (double) weighted.weight(), Double::sum);
        }
        // Normalise so weights sum to 1.0 — the validator tolerates drift but it's cleaner.
        double total = out.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total > 0) {
            out.replaceAll((k, v) -> v / total);
        }
        return out;
    }
}
```

Services file at `src/main/resources/META-INF/services/dev.recipetest.api.RecipeTestExtension`:

```text
com.example.mymod.recipetests.MyCentrifugeExtension
```

The matching JSON spec at `src/main/resources/data/mymod/recipe_test/machines/centrifuge.json`:

```json
{
  "version": 1,
  "recipeType": "mymod:centrifuge",
  "block": "mymod:centrifuge_block",
  "inputs": {
    "items": {
      "capability": "minecraft:item_handler",
      "side": "north",
      "slots": [0]
    }
  },
  "outputs": {
    "items": {
      "capability": "minecraft:item_handler",
      "side": "south",
      "slots": [1]
    }
  },
  "tickBudget": "auto",
  "validation": {
    "mode": "distribution",
    "samples": 1000,
    "distributionTolerance": 0.05
  }
}
```

When `runGameTestServer` boots, the kit:

1. Discovers `MyCentrifugeExtension` via `ServiceLoader` at common-setup.
2. Validates the spec at datapack-load: `validation.mode = "distribution"` requires a
   registered extension for `mymod:centrifuge` — found.
3. At test execution, the dynamic GameTest for each centrifuge recipe runs through
   `DistributionRunSession` for 1000 samples, buckets each run's output into channels, and
   compares the histogram against `MyCentrifugeExtension.weights(...)` via
   `DistributionValidator.verdict`.
4. PASS if every channel's observed frequency is within ±0.05 of the declared weight; FAIL
   otherwise with per-channel deltas in the report.

## Custom binding example

Recipes that consume from a non-standard storage type — a gas tank, a heat reservoir —
declare a `CustomBinding` in the spec and the extension provides a `CustomHandler` that knows
how to inject/read.

Spec snippet:

```json
"inputs": {
  "items": { ... },
  "custom": [
    { "kind": "mymod:gas", "ref": "input_gas_tank" }
  ]
}
```

Extension snippet:

```java
@Override
public Set<ResourceLocation> supportedKinds() {
    return Set.of(ResourceLocation.fromNamespaceAndPath("mymod", "gas"));
}

@Override
public Optional<CustomHandler> resolveCustomBinding(ResourceLocation kind, String ref, BlockEntity be) {
    if (!kind.toString().equals("mymod:gas")) {
        return Optional.empty();
    }
    if (be instanceof MyGasTankBlockEntity tank && "input_gas_tank".equals(ref)) {
        return Optional.of(new MyGasHandler(tank));
    }
    return Optional.empty();
}

@Override
public InjectionDecision injectInputs(TestContext ctx, RecipeHolder<MyGasRecipe> holder) {
    // Your extension owns injection — walk spec.inputs().custom(), call resolveCustomBinding
    // for each, hand the handler the recipe's expected gas amount.
    // ...
    return InjectionDecision.HANDLED;
}
```

Returning `HANDLED` from `injectInputs` tells the kit to skip its L1 inject path. If you
also want L1 to run on top (e.g. to inject items the regular way while you handle the gas
separately), return `FALL_THROUGH` instead.

## Common pitfalls

| Symptom                                                                     | Likely cause                                                                                                                     |
| --------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `unresolved custom binding kind 'mymod:gas'` at server start                | Your extension's `supportedKinds()` doesn't include the kind. Check the resource location is exactly the same (case, namespace). |
| `distribution mode requires a registered RecipeTestExtension`               | Your extension's `recipeType()` doesn't match the spec's. They must be string-equal `ResourceLocation`s.                         |
| Distribution test fails with all channels at 100% delta                    | Your `weights()` is empty. Override it. The validator walks the union of observed and expected channels.                         |
| Tests pass locally, fail in CI with a different histogram                  | Recipe RNG isn't deterministic. Either seed it from the spec or accept a wider `distributionTolerance`.                          |
| `NullPointerException` thrown from one of your hooks aborts the run        | The kit wraps each hook call in try/catch and falls back to L1 — but the test still likely fails. Check logs for the warn.       |
| You changed your extension class name and CI still uses the old one        | The services file is text — update both the class name in code and the entry in `META-INF/services/...`.                         |

## Testing your extension

The kit ships unit-test seams for the parts you'll exercise locally:

- `ExtensionRegistry.replaceForTesting(List<RecipeTestExtension<?>>)` — install your
  extension into the registry without going through ServiceLoader.
- `DistributionValidator.verdict(observed, expected, samples, tolerance)` — pure function,
  easy to test against synthetic histograms.
- `ChannelExtractor.channelOf(TestResult)` — pure function, easy to assert against synthetic
  results.

For end-to-end verification, write a `runGameTestServer` integration test in your mod that
exercises a known recipe and asserts the JUnit XML report shows the expected pass/fail.

## Where to file issues

Open an issue at <https://github.com/ericfisherdev/NeoForge-Recipe-Test-Tools/issues> with:

- Your extension class (or a minimal repro)
- The spec JSON
- The full `recipe-test.xml` `<system-out>` block
- What you expected vs. what happened

Extension-authoring questions are first-class — the kit only earns its keep if writing
one against an unrelated mod takes <30 minutes. If yours took longer, the docs have a gap and
we want to fix it.
