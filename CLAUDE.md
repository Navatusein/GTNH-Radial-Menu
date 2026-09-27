# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**RadialMenu** — a client-side Minecraft 1.7.10 Forge mod (GTNH ecosystem) built from the GTNewHorizons
[ExampleMod1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10) template. Hold a key, get a radial menu, bind
actions to its slots.

The point of the mod: **press a keybinding that has no physical key assigned**. In a pack with dozens of mods there
aren't enough keys to go around, so actions live on menu slots instead of in the controls screen. Everything else —
submenus, profiles, the editor — exists to serve that.

Client-only by design: no packets, no server component, works on unmodified servers.

## Commands

```bash
./gradlew build                  # compile + test + spotless + jar
./gradlew runClient              # dev client
./gradlew test                   # unit tests (core/ only)
./gradlew spotlessApply          # format; the build fails on violations
./gradlew test --tests '*RadialGeometryTest.deadZoneSelectsNothing'
```

**Toolchain gotcha**: the build needs JDK 25. Gradle's auto-provisioning is broken here — foojay serves a JDK **21**
archive for a 25 request and Gradle rejects it. A real JDK 25 lives at `D:\tools\jdk25` and is registered in
`~/.gradle/gradle.properties` via `org.gradle.java.installations.paths` (forward slashes — backslashes are escapes in
a properties file). On a fresh machine that file has to be recreated.

## Architecture

### `core/` has no Minecraft imports. Keep it that way.

`src/main/java/com/navatusein/radialmenu/core/` holds the menu tree, profiles, action *data*, JSON codec and the
angle maths. It is unit tested on its own and is the layer that would survive a port to 1.12+/modern. Verify with:

```bash
grep -r "net.minecraft" src/main/java/com/navatusein/radialmenu/core/   # must be empty
```

### Actions are data, not behaviour

`ActionSpec` is `{type, params: Map<String,String>, steps: List<ActionSpec>}`. Executors (`client/action/`) are
registered by type id and are the only place Minecraft enters the pipeline. `ActionTypes` holds editor-facing field
descriptors, and `GuiSlotEditor` builds its widgets from them — so **a new action type needs a descriptor and an
executor, and no GUI code**. `steps` is the reserved hook for action chains; it is parsed today, executed from phase 2.

MineMenu's equivalent is a closed `enum` with a bespoke screen per type. Don't reintroduce that shape.

### One node type for the tree

`MenuNode.children != null` means category; `action != null` means leaf. A `null` element inside `children` is an
empty slot, which only happens under `SlotLayout.Mode.FIXED`. `normalize()` repairs hand-edited files rather than
throwing — a broken profile must never stop the client from starting.

## The keybind injection mechanism

This is the crux, and it is verified against the decompiled 1.7.10 source, not assumed:

- `KeyBinding.setKeyBindState` and `KeyBinding.onTick` both start with `if (keyCode != 0)` and look the binding up in
  a static map keyed by key code. **An unbound binding has key code 0 and is unreachable through them** — hence the
  mixin accessor writing the private fields directly.
- Two fields matter, because mods read state two ways: `pressed` backs `getIsKeyPressed()` (movement, holds),
  `pressTime` backs `isPressed()` (what AE2, Draconic Evolution and AdventureBackpack2 all use).
- Those mods read it inside an `InputEvent.KeyInputEvent` handler **on the FML bus**, so bumping `pressTime` is not
  enough — the event must be posted too, or their handler never runs.
- `pressTime` is bumped **once per press**, never per tick. Vanilla increments it from the keyboard event only;
  per-tick bumping makes `isPressed()` fire repeatedly and, e.g., flickers a flight toggle on and off.
- The private `unpressKey()` is exactly `{pressTime = 0; pressed = false;}`, so `KeyInjector.release()` writing both
  fields is equivalent — no `@Invoker` needed.
- **Known gap**: `Minecraft.runTick` checks `keyBindTogglePerspective` and `keyBindSmoothCamera` *inside* the
  `while (Keyboard.next())` loop, so vanilla only asks about them when a real key event arrives — a synthetic press is
  never seen. Every other vanilla binding (`keyBindInventory`, `keyBindDrop`, `keyBindChat`, hotbar slots) is checked
  outside the loop and works fine. `VanillaKeyEffects` applies those two directly instead. A mod that polls
  `Keyboard.isKeyDown(kb.getKeyCode())` rather than its own binding object is likewise unreachable, permanently.

Mixin accessor names are **MCP** (`pressed`, `pressTime`); the refmap remaps them to SRG. Writing SRG names in source
breaks the dev environment. Check `build/tmp/mixins/mixins.radialmenu.refmap.json` to confirm a mapping resolved.

`Mixins.java` lists class names **relative to** the `package` in `mixins.radialmenu.early.json`. Both that file and
the plain `mixins.radialmenu.json` must exist — the build registers the latter in the manifest whether you use it or
not, and a missing file aborts mixin init at launch.

## Wheel lifecycle — the ordering is load-bearing

1. `ClientTickEvent(START)` polls the open key from hardware (`Keyboard.isKeyDown`, or `Mouse.isButtonDown(code+100)`
   — mouse buttons are negative key codes offset by −100). Polling, not `isPressed()`, because hold-to-open needs
   state and `isPressed()` consumes the press.
2. Rising edge opens `GuiRadialWheel`.
3. Release → resolve the hovered slot → `displayGuiScreen(null)` → **queue the action for the next tick**.
4. Next tick → executor → `KeyInjector`.

**Close before injecting.** The alternative — keeping the wheel up and temporarily faking `currentScreen = null` —
breaks whenever the receiving handler opens a GUI of its own (AdventureBackpack2 opens the backpack), because
restoring `currentScreen` afterwards clobbers it. Closing first also genuinely restores `inGameHasFocus`, which
AdventureBackpack2 checks before reacting at all.

Two screen flags matter: `doesGuiPauseGame()` must return false, and `allowUserInput` must be set — 1.7.10 gates its
entire keyboard/mouse block on `currentScreen == null || currentScreen.allowUserInput`, so without it the player
stops moving. Opening any screen still runs `unPressAllKeys()` once, so a key already held goes dead; `HeldKeyResync`
re-applies physical state right after the screen becomes current.

## Storage

Menus live in `<game folder>/RadialMenu/`, not `config/` — one file per profile under `profiles/`, plus
`settings.json` and `icons/`. Auto-bind rules live *inside* each profile so copying the file carries them along.
Only the scalar look-and-feel settings use GTNHLib `@Config` in `config/RadialMenu/`.

Gson is 2.2.4 in 1.7.10, which **ignores `@SerializedName` on enum constants** — `LowercaseEnumAdapterFactory`
handles enum casing instead. Don't reach for the annotation.

## Status

Working: template setup, mixin accessor, `core/` + tests, profiles with auto-bind, wheel rendering and lifecycle,
keybind action (tap/toggle/hold), profile-switch action, slot editor, keybind picker, item icon picker,
`/radialmenu` command.

Not built yet: profile-management GUI, the bundled Phosphor sprite atlas (`IconSpec.Kind.SPRITE` and `FILE` currently
draw a tinted placeholder), command actions, action chains, inventory moves, backpack integration.
