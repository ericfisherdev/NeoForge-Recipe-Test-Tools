---
name: Spec authoring help
about: You're trying to write a spec for a machine and something isn't working
title: "spec: <recipeType> — <short description>"
labels: ["spec-help"]
assignees: []
---

<!--
First check docs/troubleshooting.md — most spec authoring symptoms are
documented there with a cause + fix. This template is for cases the
troubleshooting guide doesn't cover.
-->

## What you're trying to test

- **Mod:** <!-- yourmod 1.2.3 -->
- **Recipe type:** <!-- e.g. yourmod:carpenter -->
- **Block:** <!-- e.g. yourmod:carpenter_block -->
- **One example recipe ID:** <!-- e.g. yourmod:carpenter/circuit_board_basic -->

## What's happening

<!-- TIMEOUT? FAIL with diff? Validation error at spec load? Something else? -->

## Your spec

```json

```

## The recipe JSON (or a representative one)

<!-- Paste the recipe's data file contents -->

```json

```

## Server log excerpt

<!-- Anything `[recipe_test]` printed, plus the surrounding 10 lines for context. -->

```text

```

## What you've already tried

<!-- Reproduce the troubleshooting guide checklist if relevant. -->

- [ ] Read `docs/troubleshooting.md`
- [ ] Confirmed the block ID resolves (`/give @s <block>` works)
- [ ] Confirmed the recipe ID resolves (recipe shows in JEI / appears in recipe book)
- [ ] Inspected actual slot contents via `/data get block <x> <y> <z>` after running

## Environment

- Recipe-test harness version:
- Minecraft version: 1.21.1
- NeoForge version:
- Java version: 21
- Other mods that might interact:
