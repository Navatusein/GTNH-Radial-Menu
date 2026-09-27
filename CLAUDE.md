# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Minecraft 1.7.10 Forge mod built from the GTNewHorizons [ExampleMod1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10) template. The working directory is named `GTNH-Radial-Menu`, but the checkout is still the unmodified template: every placeholder (`MyMod` / `mymodid` / `com.myname.mymodid`) is intact, and there is no git repository yet.

Before real work starts, the rename pass touches: `gradle.properties` (`modName`, `modId`, `modGroup`, `generateGradleTokenClass`), the package dir `src/main/java/com/myname/mymodid/`, `MyMod.java` (class name, `MODID`, `@Mod` name, both `@SidedProxy` class-name strings — these are string literals no refactor tool will catch), `src/main/resources/mcmod.info`, and `LICENSE-template` → `LICENSE`. `git init` is also still pending, and the build derives the mod version from git tags, so until then builds are unversioned.

## Commands

```bash
./gradlew setupDecompWorkspace   # one-time: decompile/deobfuscate MC, needed before anything else
./gradlew build                  # compile + test + jar
./gradlew runClient              # launch dev client (logs in as `developmentEnvironmentUserName`)
./gradlew runServer              # launch dev server
./gradlew spotlessApply          # format; CI fails on spotlessCheck
./gradlew test --tests '*SomeTest.someMethod'   # single test
```

On Windows use `gradlew.bat`; the Bash tool's `./gradlew` works too.

## Build system

`build.gradle.kts` is two lines — all build logic lives in the GTNH convention plugins (`gtnhsettingsconvention` in `settings.gradle.kts`, `gtnhconvention` in `build.gradle.kts`), which wrap RetroFuturaGradle. **Never add logic to `build.gradle.kts` or `settings.gradle.kts`**; the whole point of the template is that they stay replaceable on update. Instead:

- `gradle.properties` — the actual configuration surface (mod identity, mixins, shadowing, publishing, AT file). Toggling features here is how you enable them.
- `dependencies.gradle` — dependencies. Header comment documents the custom configurations (`devOnlyNonPublishable`, `runtimeOnlyNonPublishable`, `shadowImplementation`, `rfg.deobf(...)` for obfuscated jars).
- `repositories.gradle` — extra repos.
- `addon.gradle[.kts]` / `addon.late.gradle[.kts]` — custom build logic, auto-applied if present. `addon[.late].local.gradle[.kts]` is gitignored, for uncommitted local tweaks (extra JVM args, etc.).

Gradle configuration cache and parallel execution are on, so any `addon.gradle` logic must be configuration-cache compatible.

## Things that bite

- **`Tags.VERSION` has no source file.** `generateGradleTokenClass` in `gradle.properties` makes the build emit `<modGroup>.Tags` with a `VERSION` constant from the git-tag-derived version. `MyMod.java` and `CommonProxy.java` reference it, so the IDE shows unresolved-symbol errors until the first `./gradlew build` (or `setupDecompWorkspace`). Rename `generateGradleTokenClass` in lockstep with `modGroup`.
- **Java 25 toolchain, Java 8 target.** `.java-version` is 25 and `enableModernJavaSyntax = jabel` lets you write modern *syntax* (var, switch expressions, records-adjacent sugar), but it compiles to Java 8 bytecode running on the MC 1.7.10 JVM — **Java 9+ APIs are not available at runtime** and will fail late, not at compile time.
- **Formatting is not local.** Spotless config comes from the GTNH blowdryer share (`gtnh.settings.blowdryerTag` in `gradle.properties`, cached in `gtnhShared/`). Don't hand-tune the eclipse format; run `spotlessApply`. The `json` block ratchets from `origin/master`, so it only formats files changed against that ref.
- **Mixins are off** (`usesMixins = false`). Enabling them is a `gradle.properties` change (`usesMixins`, `mixinsPackage`, plus `mixinPlugin`/`coreModClass` for the Early/Late variant) — dependencies are then wired in automatically. The README links example commits for the three registration styles; GTNH `IMixins` is the recommended one.
- CI (`.github/workflows/build-and-test.yml`) delegates to `GTNewHorizons/GTNH-Actions-Workflows`; it builds and runs a server-startup smoke test, so a crash on dedicated-server init fails the build. Keep client-only code behind `ClientProxy`.

## Mod structure

Standard Forge 1.7.10 sided-proxy layout: `MyMod` is the `@Mod` entry point holding `MODID`, a log4j `Logger`, and a `@SidedProxy` field; each lifecycle event (`preInit`/`init`/`postInit`/`serverStarting`) just delegates to the proxy. `CommonProxy` holds shared logic, `ClientProxy extends CommonProxy` overrides for client-only concerns (renderers, keybinds, GUI). `Config` reads a Forge `Configuration` file in `preInit`.

For a radial-menu mod this means: keybind/GUI/rendering registration belongs in `ClientProxy` only, and any server interaction needs an explicit packet channel (none is set up yet).
