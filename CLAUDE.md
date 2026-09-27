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

## Interface

Every screen extends `UiScreen` and takes its measurements from `Ui`. That exists because each screen used to carry
its own hardcoded offsets, which is why buttons jumped between screens and gaps never matched. `UiScreen` owns the
framed panel, the scrolling, and a button bar pinned a fixed distance from the bottom edge; `UiList` owns the row
arithmetic. Put anything positional in `Ui`, never in a screen.

**Never mutate `buttonList` inside `actionPerformed`.** `GuiScreen.mouseClicked` walks the list by index and re-reads
`size()` every iteration, calling `actionPerformed` from inside that loop — so replacing the list mid-click makes the
loop continue over the new buttons and fire them too. Adding an auto-bind rule that way inserted two buttons ahead of
the add button each time, moving it forward faster than the loop index, and the game died with no stack trace. Call
`requestRebuild()` instead; it rebuilds before the next frame.

Other traps the same code has already fallen into:

- Returning from a pushed screen re-runs `initGui`, which rebuilds text fields from the model. Capture what the
  player typed before pushing, and in `beforeScroll()` — scrolling rebuilds too, and silently discarded input.
- A `GuiTextField` works out its horizontal scroll from the width it has **when the text is set**. Built at a
  placeholder size and resized afterwards, it renders blank until clicked. Build it at its real size.
- Rows scrolled out of the panel must stop being drawn *and* clickable. Screens with variable-length lists build only
  the rows that fit, so buttons and fields cannot disagree about where the edge is.
- Scrolling is pixel-smooth, clipped with `glScissor` — which works in real window pixels from the bottom-left while
  everything else is in scaled GUI pixels from the top-left, so both axes are converted through `ScaledResolution`.
- **What scrolls is declared, not inferred.** `addBottomBar` and `markFooter` name the chrome; everything else is
  content. Deciding it by id — anything below `ID_PRIMARY` — silently exempted every screen numbering its rows from a
  high base (`ID_TYPE_BASE = 100`, `ID_UP_BASE = 200`), so those buttons drew outside the clip and over the title
  while their own text fields were cut at it. The same reasoning killed the earlier position-based footer test.
- `panelHeightHint()` is the height of the **content**; `UiScreen` adds the title band and padding. When each dialog
  did that arithmetic itself, all four picked a different constant and every one of them broke the day the band grew
  from 14 to 24 — `GuiProfileColors` lost its reset button off the bottom edge without a trace.
- When a fix aimed at a screenshot misses twice, print the actual numbers. Three rounds went into a `glScissor` rect
  that turned out to be arithmetically correct; one diagnostic line showed the real defect was four pixels of
  clearance above the footer.

## Icons and colours

Icons come in three kinds: a registry item, a sprite from the bundled Phosphor sheet, or a PNG from
`<game folder>/RadialMenu/icons`.

**Draw items with the content, before any button.** `IconRenderer` copies the state sequence `GuiContainer` uses
around its slots, including `GL_RESCALE_NORMAL` and forcing the lightmap to full brightness. A block is drawn scaled
ten times, so without the former the scale lands in the normals and the lighting is computed against the wrong ones —
flat sprites ignore lighting and look fine, which makes the symptom look selective and sent two earlier diagnoses
astray. Drawing an item *after* a vanilla button inherits state that dims it; put a preview beside a button, not on
it.

The sheet is **baked ahead of time** — 1.7.10's font renderer cannot load a TrueType file, so the glyph approach
newer radial-menu mods use does not transfer. `tools/GenerateIconAtlas.java` renders it, dependency-free from a JDK.
**That generator is gitignored** at the author's request, so the committed `phosphor.png` (2048×1024) and
`icons/phosphor.json` are the source of truth — a clean clone cannot rebuild them.

Draw sprites with explicit texture coordinates; `Gui.drawTexturedModalRect` assumes a 256×256 sheet.

**Colours inherit: menu → profile → mod config.** `core/Colors` owns the parsing, because it was duplicated in the
renderer and in `IconSpec` and had drifted. The colour picker writes `#RRGGBB` while the config writes
`0xAARRGGBB`, so a six-digit value read as eight is alpha zero — that is how a chosen ring colour once turned the
ring invisible. `Colors.over` layers an override on a resolved colour and keeps the inherited opacity when the
override has none.

Icon tints distinguish two kinds of blank: **null keeps the artwork's own colours** (the PNG tab's checkbox), while
**empty means inherit**. Collapsing both to white is the bug to avoid.

## Profiles

Menus live in `<game folder>/RadialMenu/`, not `config/` — one file per profile under `profiles/`, plus
`settings.json` and `icons/`. Auto-bind rules live *inside* each profile so copying the file carries them.

Renaming a profile to a different capitalisation is a rename of the same file on Windows, not a collision — check
`ProfileStorage.isSameFile` before refusing, and rename through a temporary name. Writing the new file and deleting
the old one would delete the file just written.

Gson is 2.2.4 in 1.7.10, which **ignores `@SerializedName` on enum constants** — `LowercaseEnumAdapterFactory`
handles enum casing instead.

## Status

Working: template setup, mixin accessor, `core/` + 77 tests, profiles with auto-bind, colours and a management GUI,
wheel rendering and lifecycle, keybind action (tap/toggle/hold), profile-switch action, command action with
placeholders, action chains, submenu-as-action-type with per-menu layout and colours, entry reordering, the full
editor, and `/radialmenu`.

Not built yet: inventory moves, backpack integration, mob-effect icons.
