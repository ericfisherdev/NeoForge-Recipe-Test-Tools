---
name: Bug report
about: The harness itself is misbehaving — runtime crash, wrong test verdict, broken codec, etc.
title: "fix: <short description>"
labels: ["bug"]
assignees: []
---

<!--
If you're stuck writing a spec for your machine but the harness itself is
working fine, use the "Spec authoring help" template instead.
If you're new and not sure: this template is the right one when you'd describe
the symptom as "the harness did something wrong" rather than "I don't know how
to write a spec for X".
-->

## What happened

<!-- Describe the bug. -->

## Expected

<!-- What you expected to happen instead. -->

## Steps to reproduce

1.
2.
3.

## Environment

- Recipe-test kit version: <!-- e.g. 1.0.0 -->
- Minecraft version: 1.21.1
- NeoForge version:
- Java version: 21
- OS:
- Other mods on the classpath that might interact:

## Logs / stacktrace

<!-- Paste relevant log excerpts or attach `latest.log` / `crash-reports/`. -->

```text

```

## Spec / TestResult JSON (if applicable)

<!--
If the bug surfaces during a `/recipe_test run` or in the JUnit XML report,
paste the spec and the `<system-out>` from the failing testcase here.
-->

```json

```
