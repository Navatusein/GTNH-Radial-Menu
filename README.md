# RadialMenu

**Hold a key, point, let go.** A client-side radial menu for Minecraft 1.7.10, built for packs with more keybindings
than a keyboard has keys.

![Twelve keybindings with no key, on one ring](/docs/assets/hero.png)

A pack the size of GT New Horizons registers hundreds of keybindings, and the keyboard ran out long ago. RadialMenu
puts them on a wheel instead — and does the one thing the controls screen cannot: **it presses a keybinding that has
no key assigned at all.** The quest book, the magnet, the backpack, the waypoint you never found a key for: one ring,
one key.

It is client-side only. No packets, no server part — everything it does is something you could have done by hand, so
it works on any server and nobody else needs the mod.

- [Quick start](#quick-start)
- [What an entry can do](#what-an-entry-can-do)
- [Submenus](#submenus)
- [Make it yours](#make-it-yours)
- [Scripts](#scripts)
- [Profiles](#profiles)
- [The editor](#the-editor)
- [Settings](#settings)
- [Files and commands](#files-and-commands)
- [Development](#development)

---

## Quick start

1. Put the jar in `mods/` next to [GTNHLib](https://github.com/GTNewHorizons/GTNHLib). Forge 1.7.10, client only.
2. In game, hold <kbd>R</kbd>. A starter wheel opens.
3. Hold <kbd>Shift</kbd> and click a sector to change it — an empty one makes a new entry.

| Key | What it does |
|---|---|
| <kbd>R</kbd>, held | Open the wheel. Let go over an entry to run it. |
| <kbd>Right click</kbd> | One step back: fold a ring, leave a submenu, or close. |
| <kbd>Esc</kbd> | Close, wherever you are. |
| <kbd>Shift</kbd> + click | Edit the entry under the cursor. In the middle: the menu's own settings. |

The open key can be a mouse button. Switching profiles and opening the editor have keybindings of their own, unbound
by default — this mod is not going to take keys from you.

---

## What an entry can do

| | Action | What it does |
|---|---|---|
| ⌨ | **Keybind** | Presses any keybinding, **bound or not**. Tap, toggle, or hold for a while; with Shift, Ctrl, Alt or sneak held along. |
| 💬 | **Command** | Sends chat or slash commands, with `{player}` `{x}` `{y}` `{z}` `{dim}` filled in. Several lines cycle. |
| ⭕ | **Submenu** | A wheel inside the wheel, with its own size, layout and colours. |
| ⛓ | **Chain** | Several actions in a row, with a delay between them if you want one. |
| 🔀 | **Profile** | Switches to another profile, or to the next one. |
| 📜 | **Script** | Lua that asks the server something and builds a wheel out of the answer. |

![The cursor sweeping round the wheel](/docs/assets/clip-hover.gif)

The press goes straight into the binding, the way the keyboard would have put it there, so mods that check for their
key in any of the usual ways react to it — AE2's terminal, Draconic Evolution's toggles and both backpack mods
included. Every parameter is listed in [docs/PROFILE_FORMAT.md](docs/PROFILE_FORMAT.md).

---

## Submenus

A submenu either takes the wheel's place, or unfolds around it as a ring — one setting on the entry.

An **inline** ring opens by *pointing* at its entry, not by choosing it, so a whole branch is one movement away and the
menu you came from never leaves the screen.

![A ring unfolding as the cursor reaches its entry](/docs/assets/clip-inline.gif)

| A ring around its entry | Rings inside rings |
|---|---|
| ![An inline submenu](/docs/assets/wheel-inline.png) | ![Two inline rings at once](/docs/assets/wheel-inline-deep.png) |

A **replacing** submenu takes the whole wheel, and the header says how you got there. Right click walks back out.

![A submenu that replaced the wheel](/docs/assets/wheel-replace.png)

An entry in a ring is the same size to aim at as the one it grew from, a short hover delay stops the wheel unfolding
everything the cursor crosses, and only one branch per menu is open at a time.

---

## Make it yours

Every picture below is the same menu. Nothing about the look is fixed.

| Gaps, no outline | Outlined, touching | Slot plates | Wide gaps, big icons |
|---|---|---|---|
| ![Default](/docs/assets/style-default.png) | ![Outlined](/docs/assets/style-outlined.png) | ![Slot plates](/docs/assets/style-plates.png) | ![Wide](/docs/assets/style-wide.png) |

| Red | Green | Gold | Purple |
|---|---|---|---|
| ![Red accent](/docs/assets/style-red.png) | ![Green accent](/docs/assets/style-green.png) | ![Gold accent](/docs/assets/style-gold.png) | ![Purple accent](/docs/assets/style-purple.png) |

**Pick one colour, get a palette.** Choose an accent and the editor fills in all six colours from it — ring, highlight,
both outlines, the wash behind the screen. What it writes are plain colours, yours to adjust one by one afterwards.

**Colours inherit: menu → profile → mod settings.** A submenu that sets nothing looks like its profile, and a profile
that sets nothing looks like the mod's settings. So a single branch can stand out without repainting the rest:

| Each menu its own colour | Darken the world behind the wheel |
|---|---|
| ![A blue wheel with a green ring](/docs/assets/style-per-menu.png) | ![The background dimmed](/docs/assets/style-dim.png) |

**The shape is settings too:** both radii, the gap between sectors, line width, which outlines are drawn, a soft edge,
icon size, and vanilla's own slot plates behind the icons.

**And so is the way it arrives:**

| Zoom | Fade | Staggered |
|---|---|---|
| ![Zoom](/docs/assets/reveal-zoom.gif) | ![Fade](/docs/assets/reveal-fade.gif) | ![Staggered](/docs/assets/reveal-stagger.gif) |

Slowed down here to be seen; in game the wheel is there in under a fifth of a second. There is also none at all, and
staggered the other way round. The sector under the cursor leaning out is a setting of its own.

---

## Scripts

Some menus cannot be written down in advance: your homes on a server, its warps, what you are carrying. A script is for
those — Lua that runs inside the client and can do what you could have done by hand: send chat, read the reply, press
a binding, and put up a wheel.

```lua
chat.send("/home list")

local reply = chat.await("^Homes: (.+)$", 5)

if not reply then
  return
end

local homes = {}

for name in reply:gmatch("[^,%s]+") do
  homes[#homes + 1] = {
    key = name,
    label = name,
    icon = "minecraft:bed"
  }
end

local pick = menu.open(homes, {
  title = "Homes"
})

if pick then
  chat.send("/home " .. pick)
end
```

| The wheel it builds | A script's menu with a branch |
|---|---|
| ![A wheel built by a script](/docs/assets/wheel-script.png) | ![A script's inline submenu](/docs/assets/wheel-script-inline.png) |

A wheel a script built says **SCRIPT** in its header: it is answered once and gone, so there is nothing to edit.

A script can also ask questions — a whole form at once, which will not close until every answer is valid:

![A form a script asked for](/docs/assets/script-form.png)

Scripts are written in the game, in an editor with highlighting, folding and the whole API a click away. A script that
fails says on which line.

![The script editor](/docs/assets/script-editor.png)

**The scripting guide is [docs/SCRIPTING.md](docs/SCRIPTING.md)** — every call, with complete scripts to start from.
Scripts travel inside the profile; `enableScripts` in the settings turns them off entirely.

---

## Profiles

A profile is a whole wheel in one file: `Mining`, `Building`, `That one server`.

![The profile manager](/docs/assets/profiles.png)

Switch by hand, from an entry, from a keybinding — or **let it happen**: each profile carries rules for the servers
and worlds it belongs to, and joining one picks the right wheel. *Use current world* fills a rule in exactly as it will
later be compared.

| Auto-bind rules | The last three versions, kept |
|---|---|
| ![Auto-bind rules](/docs/assets/auto-bind-rules.png) | ![Kept versions](/docs/assets/backups.png) |

Every save keeps the three before it. A half-written file is the rare accident; the common one is an editor that did
exactly what it was told.

---

## The editor

Hold <kbd>Shift</kbd> and the wheel says **EDIT**. Click an entry to change it, an empty sector to add one, or the
middle for the menu itself.

![Edit mode](/docs/assets/wheel-edit.png)

An entry is a look and an action, and the fields are whatever that action needs — nothing on the screen that does not
apply.

| A keybinding | A submenu: shape and colours |
|---|---|
| ![The entry editor](/docs/assets/slot-editor.png) | ![A submenu's settings](/docs/assets/slot-editor-submenu.png) |

| A chain | A script |
|---|---|
| ![A chain of steps](/docs/assets/slot-editor-chain.png) | ![A script entry](/docs/assets/slot-editor-script.png) |

A menu's own screen holds its layout, its colours and its entries — drag them into order, duplicate, delete, or move
one into another menu. Nothing is written until you save.

| Menu settings | A profile's colours |
|---|---|
| ![Menu settings](/docs/assets/menu-settings.png) | ![Profile colours](/docs/assets/profile-colors.png) |

A **fixed** menu keeps every entry at the same angle whatever its neighbours do — that is what muscle memory needs —
and leaves gaps where entries were removed. A **dynamic** one shares the circle among whatever is there.

**Icons** come in five kinds, searched by the name you see in game: every item and block with its subtypes, the
bundled [Phosphor](https://phosphoricons.com/) set, status effects (modded ones too), your own PNGs from
`RadialMenu/icons`, and players' heads — remembered, so a teammate's entry still shows their face when they are
offline.

| Items and blocks | Phosphor icons |
|---|---|
| ![Items](/docs/assets/icon-picker-items.png) | ![Sprites](/docs/assets/icon-picker-sprites.png) |

![Status effects](/docs/assets/icon-picker-effects.png)

---

## Settings

**Mods → RadialMenu → Config.** What lives here is how the wheel behaves for you; a profile handed to someone else
carries its colours, never your controls.

![The settings categories](/docs/assets/config.png)

| Category | What it holds |
|---|---|
| **Behaviour** | Release or click to choose, choosing with the scroll wheel, how the editor opens, input while the wheel is up, scripts on or off |
| **Wheel** | Radii, gaps, outlines, the soft edge, slot plates, icon size, the dimmed background |
| **Colors** | The colours every profile starts from |
| **Accent** | How the six colours are worked out from one |
| **Animation** | How the wheel arrives, and how the entry under the cursor reacts |

![The wheel settings](/docs/assets/config-wheel.png)

---

## Files and commands

Menus live in the game folder, one file per profile, ready to copy to another instance or hand to a friend:

```
RadialMenu/
├── profiles/      one .json per profile
├── backups/       the last three versions of each
├── icons/         your own PNGs
├── cache/         players' heads
└── settings.json  which profile is active
```

A broken file never stops the game from starting — what cannot be read is repaired or skipped. The format is documented
for hand-editing, or for asking an assistant to write one, in [docs/PROFILE_FORMAT.md](docs/PROFILE_FORMAT.md).

```
/radialmenu edit             the profile manager
/radialmenu profiles         list them, and which is active
/radialmenu profile <name>   switch
/radialmenu reload           read the files again
```

---

## Development

Built on the GT New Horizons [ExampleMod](https://github.com/GTNewHorizons/ExampleMod1.7.10) build script; it needs
JDK 25.

```shell
./gradlew build          # compile, test, format check, jar
./gradlew runClient      # development client
./gradlew test           # unit tests
./gradlew spotlessApply  # format; the build fails on violations
```

The jar for the game is `build/libs/radialmenu-<version>.jar`, not the `-dev` one.
