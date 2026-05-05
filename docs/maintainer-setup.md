# Maintainer Setup

One-time setup steps required after merging the bootstrap PR. Everything below is **outside** the
codebase: GitHub repo settings, branch protection, secrets, etc.

## 1. Branch protection on `1.21.1`

Settings → Branches → Add rule → Branch name pattern: `1.21.1`.

Enable:

- **Require a pull request before merging**
- **Require status checks to pass before merging** → Search & require:
  - `Build & Test` (from `ci.yml`)
  - `Markdown lint` (from `ci.yml`)
  - `Secret scan` (from `ci.yml`)
  - `PR Title (Conventional Commits) / lint`
  - `Analyze (java)` (from `codeql.yml`)
- **Require branches to be up to date before merging**
- **Require conversation resolution before merging**
- **Do not allow bypassing the above settings**

> Configure protection only **after** the first green CI run lands on `1.21.1`, otherwise
> protection will reject the bootstrap merge itself.

## 2. Repository secrets

Settings → Secrets and variables → Actions → New repository secret.

| Secret | Required? | Used by | Notes |
|---|---|---|---|
| `CODECOV_TOKEN` | Required if the repo is private (public OSS repos can use OIDC) | `ci.yml → coverage` | Get from <https://app.codecov.io>. |
| `GITLEAKS_LICENSE` | Required for org accounts; not needed for personal public repos | `ci.yml → secret-scan` | Get from <https://gitleaks.io>. |

`GITHUB_TOKEN` is provided automatically — no setup needed.

## 3. Repository labels

The PR labeler and release-drafter expect these labels to exist. Create them once via
Settings → Labels (or `gh label create`):

| Label | Color | Description |
|---|---|---|
| `area:api` | `#0052cc` | Public API surface |
| `area:spec` | `#0e8a16` | Spec / codec / loader |
| `area:core` | `#5319e7` | Core simulation engine |
| `area:command` | `#fbca04` | `/recipe_test` command surface |
| `area:gametest` | `#d4c5f9` | GameTest hooks |
| `ci` | `#bfdadc` | CI / GitHub Actions |
| `build` | `#bfdadc` | Build system / Gradle |
| `docs` | `#0075ca` | Documentation |
| `dependencies` | `#0366d6` | Dependabot / dep bumps |
| `resources` | `#c5def5` | Datapack / asset resources |
| `tests` | `#c2e0c6` | Tests only |
| `feat` | `#a2eeef` | New feature (release-drafter → minor) |
| `fix` | `#d73a4a` | Bug fix (release-drafter → patch) |
| `breaking` | `#b60205` | Breaking change (release-drafter → major) |
| `chore` | `#cccccc` | Routine maintenance |
| `refactor` | `#fef2c0` | Refactor without behavior change |
| `bug` | `#d73a4a` | Bug report |
| `enhancement` | `#a2eeef` | Feature request |

## 4. Local sanity checks

After cloning, every contributor should be able to run:

```sh
./gradlew clean build check
./gradlew runClient   # opens title screen, mod listed in "Mods"
./gradlew runServer   # reaches "Done (Xs)!" and idles
```

Useful one-offs:

- `./gradlew spotlessApply` — auto-fix formatting + license headers
- `./gradlew spotlessCheck` — verify formatting only
- `./gradlew checkstyleMain` — run only Checkstyle on production sources
- `./gradlew jacocoTestReport` — HTML at `build/reports/jacoco/test/html/index.html`
- `./gradlew dependencyUpdates` — flag outdated dependencies (Gradle Versions Plugin)

## 5. Negative tests (what *should* fail)

These prove the quality net works. Run them once after bootstrap and document any drift:

| Inject | Expected failure |
|---|---|
| `System.out.println("x");` in any prod source | `checkstyleMain` fails |
| Removing the Spotless license header from a Java file | `spotlessCheck` fails |
| Returning `null` from a method called in a chain inside `dev.recipetest.*` | `compileJava` fails (NullAway) |
| Opening a PR titled `bad title` | `pr-title.yml` fails |
| Committing a fake AWS key | `secret-scan` (gitleaks) fails |

## 6. Pinned versions reference

The bootstrap pins everything Phase 0 leans on. Keep these in sync with `gradle.properties`
and the Dependabot PRs that arrive weekly.

| Component | Version | Source of truth |
|---|---|---|
| Minecraft | 1.21.1 | `gradle.properties: minecraft_version` |
| NeoForge | 21.1.213 | `gradle.properties: neo_version` |
| NeoGradle plugin | 7.0.170 | `build.gradle: net.neoforged.gradle.userdev` |
| Parchment mappings | 2024.11.17 (for MC 1.21.1) | `gradle.properties` |
| Java toolchain | 21 (Temurin) | `build.gradle` + CI `setup-java@v4` |
| Spotless | `${spotless_version}` | `gradle.properties` |
| ErrorProne plugin | `${errorprone_plugin_version}` | `gradle.properties` |
| ErrorProne core | `${errorprone_core_version}` | `gradle.properties` |
| NullAway | `${nullaway_version}` | `gradle.properties` |
| Gradle Versions Plugin | `${versions_plugin_version}` | `gradle.properties` |
| Checkstyle | `${checkstyle_tool_version}` | `gradle.properties` |
| JaCoCo | `${jacoco_tool_version}` | `gradle.properties` |
