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
descriptors, and `GuiActionEditor` builds its widgets from them — so **a new action type needs a descriptor and an
executor, and no GUI code**. `steps` holds the nested actions of a chain; `ActionType.chain` is what makes the editor
offer a step list instead of trusting the type id.

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

**Some mods never look past the key code.** JourneyMap's `Constants.isPressed` returns false outright when
`getKeyCode() == 0` and never reads the press counter, so its zoom and minimap keys were unreachable while its map
toggle — which calls `isPressed()` directly — worked. `KeyInjector` lends an unbound binding `KEY_F15` for the
length of the press: a code LWJGL defines that no keyboard produces, so the check passes and nothing can collide
with it. The static `KeyBinding.hash` map is left alone, because the press is written to the binding directly rather
than dispatched through the map. `lendKeyCodeToUnbound` turns it off if some mod's `KeyBinding` mixin objects.

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

**`keepOpen` used to cost you every mod that checks `currentScreen` or `inGameHasFocus`.** Such an entry leaves the
wheel up, so step 3's close never happens and the injection lands while a screen is open — and AdventureBackpack2,
for one, checks focus before reacting at all. `WithoutScreen` now clears both flags for the length of the call and
puts them back **only if the screen is still clear**, which is what makes it safe: a handler that opened a GUI of its
own keeps it. Nothing renders during a tick, so no frame sees the substitution, and writing the fields directly
avoids `displayGuiScreen`'s `unPressAllKeys`, its `initGui`, and the cursor re-centring that would move the player's
aim off the sector.

**Cover both moments a mod can read a key.** Besides the `KeyInputEvent` posted during the injection, a mod may read
from its own `ClientTickEvent` handler — NEI does, at phase **START** (`if (event.phase == Phase.END) return;`).
`WithoutScreen` therefore wraps the tick as well: a `HIGH` listener hides the screen, a `LOW` one puts it back, and
the mod's own listener sits at `LOWEST` so it acts on the real screen. The arming is for the **next** tick, because
NEI's handler runs before ours in the same phase, and it is only cleared after a START phase — clearing it at the END
phase of the same tick would spend it before the reader that matters ever looked.

**Read the phase out of the mod's source, never out of a description of it.** A bug report said NEI read at END, and
three fixes in a row carefully wrapped the phase in which NEI does nothing at all. Every measurement was correct and
said `press 1->1`; the wrong assumption was upstream of all of them. Checking `ClientHandler.tickEvent` would have
taken one minute against six client runs.

**Check the version you are reading against the pack's.** `dependencies.gradle` pins NEI 2.8.91-GTNH, where the
overlay keys really were polled with `Keyboard.isKeyDown` and no Forge keybinding existed. GTNH Daily 758 ships
2.8.147-GTNH, which registers them properly — `nei.options.keys.world.chunkoverlay` appears in the picker and works.
An analysis of "this mod cannot be driven at all" was drawn from the stale jar and was wrong.

`java -p` on the pack's own jars is the way to settle these; the bug report that corrected this one carried the
bytecode offsets.

Two screen flags matter: `doesGuiPauseGame()` must return false, and `allowUserInput` must be set — 1.7.10 gates its
entire keyboard/mouse block on `currentScreen == null || currentScreen.allowUserInput`, so without it the player
stops moving. Opening any screen still runs `unPressAllKeys()` once, so a key already held goes dead; `HeldKeyResync`
re-applies physical state right after the screen becomes current.

## Interface

Every screen extends `UiScreen` and takes its measurements from `Ui`. That exists because each screen used to carry
its own hardcoded offsets, which is why buttons jumped between screens and gaps never matched. `UiScreen` owns the
framed panel, the scrolling, and a button bar pinned a fixed distance from the bottom edge; `UiList` owns the row
arithmetic. Put anything positional in `Ui`, never in a screen.

`GuiActionEditor` is the shared "what this does" half: the type tabs, the generated fields, and a chain's step list.
`GuiSlotEditor` puts an appearance section above it, `GuiStepEditor` edits one step of a chain with the same controls
minus title, icon and keep-open — those belong to the entry that owns the chain, not to an action. A submenu is
offered on a slot and not as a step, because it has no executor: it is a shape, not something to run.

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
- **A framed box is drawn at `viewportLeft/Right/Top/Bottom`, and its content indents from that by `Ui.GAP`.** Never
  measure a box from the panel or from the content column directly: those were two competing definitions, one of them
  off by a gap, and the result was screens with visibly different padding for the same thing. `panelHeightHint()` is
  the height of the *content*, and `UiScreen` adds the title band, the frame gap and the padding.
- **The clip runs only on screens with a framed viewport.** It exists to stop a scrolling row spilling past the
  frame; applied to a screen that frames its own box inside `drawContent`, it cut the top and bottom edges off that
  box instead — the profile list drew with no bottom border and so looked bottomless.
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

**An item's own renderer can throw, and it is not ours to fix.** Binnie's gene items ask their breeding system for a
colour and NPE when there is none, which there is not on the bare meta-0 stack the picker builds — it killed the
client mid-scroll, and would do the same in the world if such an item were an entry's icon. `IconRenderer` catches
it, restores the matrix and lighting in a `finally` (the throw comes from inside the item renderer, so neither would
unwind on its own), and **remembers the item**: the picker redraws every frame, so merely catching would mean sixty
stack traces a second.

The ring is drawn as full-width sectors with a line on each boundary and an edge at each radius. It used to draw the
sectors a couple of degrees narrow instead, leaving wedges of bare world between them — which also made the wheel lie
about itself, since the angle a click resolves to never knew about the gaps and aiming at one still picked a
neighbour. The edges are drawn inwards from their radius so turning the lines on cannot change the wheel's size.

**Colours inherit: menu → profile → mod config.** `core/Colors` owns the parsing, because it was duplicated in the
renderer and in `IconSpec` and had drifted. The colour picker writes `#RRGGBB` while the config writes
`0xAARRGGBB`, so a six-digit value read as eight is alpha zero — that is how a chosen ring colour once turned the
ring invisible. `Colors.over` layers an override on a resolved colour and keeps the inherited opacity when the
override has none.

Icon tints distinguish two kinds of blank: **null keeps the artwork's own colours** (the PNG tab's checkbox), while
**empty means inherit**. Collapsing both to white is the bug to avoid.

`MenuStyle.of` takes every colour explicitly and has no shorter overload. It briefly had two that differed only in
which optional colour the third argument meant — the types matched either way round, so the compiler would have said
nothing while the colours quietly swapped.

## Profiles

Menus live in `<game folder>/RadialMenu/`, not `config/` — one file per profile under `profiles/`, plus
`settings.json` and `icons/`. Auto-bind rules live *inside* each profile so copying the file carries them.

`docs/PROFILE_FORMAT.md` documents that file for someone writing one by hand, or for an assistant asked to generate
one. It is written from the reader, not from intent, so keep it in step with `core/model/` and `core/action/` —
notably the action types' parameter names and which of them are required.

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

Not built yet: inventory moves, backpack integration, mob-effect icons. `GuiMenuSettings` still carries its own copy
of the generated-field code rather than sharing `GuiActionEditor`'s; it edits a node through `SubmenuFields` rather
than an action, so the two have not been merged.
