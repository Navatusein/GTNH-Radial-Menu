# RadialMenu

**Hold a key. Point. Let go.** A client-side radial menu for Minecraft 1.7.10 that puts actions on a wheel instead of
on keys you no longer have.

![The wheel](/docs/assets/wheel.png)

In a pack the size of GTNH there are not enough keys on a keyboard to go round, and the useful ones are long gone. So
the mod does the one thing the controls screen cannot: it **presses a keybinding that has no key assigned at all**.
Everything else — submenus, profiles, scripts, the editor — exists to serve that.

No network channels, no server side. Everything it does is something you could have done by hand, so it works on
unmodified servers and nobody else needs the mod.

---

## Contents

- [What a slot can do](#what-a-slot-can-do)
- [Submenus](#submenus)
- [Scripts](#scripts)
- [Icons](#icons)
- [Profiles](#profiles)
- [Install](#install)
- [Controls](#controls)
- [Editing](#editing)
- [The way it looks](#the-way-it-looks)
- [Files and commands](#files-and-commands)
- [Development](#development)

---

## What a slot can do

| | Action | What it does | Details |
|---|---|---|---|
| ⌨ | **Keybind** | Presses a keybinding — **including one with no key assigned**. Tap, toggle, or hold for a set time. Can hold Shift, Ctrl, Alt or sneak with it. | [Parameters](docs/PROFILE_FORMAT.md#keybind--press-a-keybinding) |
| 💬 | **Command** | Sends chat lines or slash commands, with `{player}` `{dim}` `{x}` `{y}` `{z}` placeholders. Give it several and it cycles through them. | [Parameters](docs/PROFILE_FORMAT.md#command--send-chat-lines-or-slash-commands) |
| 🔀 | **Profile switch** | Switches to another profile, or cycles to the next one. | [Profiles](#profiles) · [parameters](docs/PROFILE_FORMAT.md#profileswitch--change-the-active-profile) |
| ⭕ | **Submenu** | A nested wheel with its own slot count, layout and colours — in place of this one, or unfolded as a ring around it. | [Submenus](#submenus) · [parameters](docs/PROFILE_FORMAT.md#how-a-submenu-arrives) |
| ⛓ | **Chain** | Runs several of the above in order, with an optional delay between the steps. | [Parameters](docs/PROFILE_FORMAT.md#sequence--a-chain-of-actions) |
| 📜 | **Script** | Runs Lua: ask the server something, read the reply, and build a wheel out of what it said. | [Scripts](#scripts) · [guide](docs/SCRIPTING.md) |

The point of the first one bears repeating: a mod that registers a keybinding but has no key left for it is normally
unusable. Here it is a slot, and the mod writes the press into the binding itself — so AE2's terminal, Draconic
Evolution's goggles and the backpack you never had a key for all work from the wheel.

![An entry under the cursor](/docs/assets/wheel-hover.png)

An entry can also be marked **Keep menu open**, which leaves the wheel up so you can fire it again, or pick the next
thing without reopening.

---

## Submenus

A submenu arrives one of two ways, and it is one setting on the entry.

**Replace** is the classic wheel: choosing the entry takes the screen over, and a right-click walks back out the way
you came in.

**Inline** unfolds it as a ring around the entry instead — and it opens by **pointing at it** rather than by choosing
it, so a whole branch is one movement away. The parent stays on screen, which means several rings are open at once and
the entry you came from is still where you left it.

![An inline submenu](/docs/assets/wheel-inline.png)

![Two inline rings at once](/docs/assets/wheel-inline-deep.png)

Each ring is drawn with the same sector size as the entry it grew from, so an entry is the same size to aim at however
deep it sits. One branch per menu stays open at a time, and a short hover delay keeps the wheel from unfolding
everything the cursor passes over on its way.

---

## Scripts

Some menus cannot be written down in advance: the list of your homes, the warps a server knows, what is in your
inventory right now. A **script** is for those. It is Lua, it runs inside the client, and it can do only what you could
have done by hand — send chat, read the replies, press a binding, and put a wheel up.

```lua
local playerName = player.name

chat.send("/home list " .. playerName)

local list = chat.await("^" .. playerName .. ": %d+ / %d+: (.+)$", 60)

if not list then
  notify("no answer from /home list")
  return
end

local homes = {}

for name in list:gmatch("[^,%s]+") do
  homes[#homes + 1] = {
    key = name,
    label = name,
    icon = "minecraft:bed"
  }
end

local pick = menu.open(homes, { title = "Homes", slots = 8 })

if pick then
  chat.send("/home " .. pick .. " " .. playerName)
end
```

| The wheel that script builds | A script menu with a branch of its own |
|---|---|
| ![A script's wheel](/docs/assets/wheel-script.png) | ![A script's inline submenu](/docs/assets/wheel-script-inline.png) |

A wheel a script built says **SCRIPT** in the header: it cannot be edited, and it is gone the moment it is answered.

Scripts are written in the game, in an editor with syntax highlighting, line numbers and the whole API one click away.
A script that fails says which line, and the editor marks it when you open it again.

![The script editor](/docs/assets/script-editor.png)

**The scripting guide is [docs/SCRIPTING.md](docs/SCRIPTING.md)** — what a script can ask for, what it can build, and a
set of complete scripts to start from. Scripts live inside the profile, so a profile you hand to someone else brings
them along; `enableScripts` in the config refuses to run them at all.

---

## Icons

Five kinds, searchable by the name you read in game rather than by registry id.

| Every item and block, subtypes included | The bundled [Phosphor](https://phosphoricons.com/) set |
|---|---|
| ![Items](/docs/assets/icon-picker-items.png) | ![Sprites](/docs/assets/icon-picker-sprites.png) |

Status effects, vanilla or modded:

![Status effects](/docs/assets/icon-picker-effects.png)

And two more: **your own PNGs**, dropped into `RadialMenu/icons`; and **a player's head**, read off the skin the game
is already drawing them with and kept in a cache — so a slot named after someone still shows their face when they are
offline, which is exactly when you want it.

Sprites and PNGs can be tinted. How big an icon is drawn is a setting, and the plate behind it follows.

---

## Profiles

A profile is one wheel tree, one file, one set of colours — `Mining`, `Building`, `That one server`.

![The profile manager](/docs/assets/profiles.png)

Profiles switch by hand, from a slot, from a keybinding, or **on their own**: each profile carries its own auto-bind
rules, so joining a server or loading a world picks the right wheel without being asked.

| Auto-bind rules | The last three versions, kept |
|---|---|
| ![Auto-bind rules](/docs/assets/auto-bind-rules.png) | ![Backups](/docs/assets/backups.png) |

**Use current world** fills a rule in exactly as the client will later compare it, which is the reliable way to get a
server address right. Every save keeps the previous three versions of the profile, because the loss worth guarding
against is not a half-written file but an editor that did exactly what it was told.

---

## Install

Requirements:

- Minecraft 1.7.10 with Forge
- [GTNHLib](https://github.com/GTNewHorizons/GTNHLib)

Put the jar in `mods/`. The mod is **client-side only** — it does not need to be on the server, and the server does not
need to know about it.

---

## Controls

| Key | What it does |
|---|---|
| <kbd>R</kbd> (hold) | Open the wheel. Release over a sector to run it. |
| <kbd>Right click</kbd> | Step back: fold an open branch, leave a submenu, or close the wheel. |
| <kbd>Esc</kbd> | Close the wheel, wherever you are in it. |
| <kbd>Shift</kbd> + <kbd>Left click</kbd> | Edit the slot under the cursor, empty or not. |
| <kbd>Shift</kbd> + <kbd>Left click</kbd> in the middle | Settings of the menu you are looking at. |

The open key can be a mouse button. Next profile, previous profile and the editor have keybindings of their own,
unbound by default. If you would rather click than release, or turn the wheel with the scroll wheel instead of pointing
at it, both are in the config.

---

## Editing

Hold <kbd>Shift</kbd> and the wheel says **EDIT**. Click a sector to edit it — an empty one makes a new entry — or
click the hole in the middle for the menu's own settings.

![Edit mode](/docs/assets/wheel-edit.png)

A slot is an appearance and an action, and the fields under the tabs are whatever that action needs, so there is
nothing on the screen that does not apply to it.

| A keybind slot | A submenu: its shape and its colours |
|---|---|
| ![The slot editor](/docs/assets/slot-editor.png) | ![A submenu's settings](/docs/assets/slot-editor-submenu.png) |

| A chain of steps | A script slot |
|---|---|
| ![A chain](/docs/assets/slot-editor-chain.png) | ![A script slot](/docs/assets/slot-editor-script.png) |

The menu's own screen holds its layout, its colours and the order of its entries — reorder them, duplicate one, remove
one, or move one into another menu entirely. Nothing is written until you press Save.

![Menu settings](/docs/assets/menu-settings.png)

A menu is either **fixed** or **dynamic**. Fixed keeps every entry at the same angle whatever its neighbours do, which
is what makes muscle memory work, and empty positions stay as gaps. Dynamic divides the ring by however many entries
there are.

---

## The way it looks

Colours inherit down a chain: **menu → profile → mod config**. A submenu that sets nothing looks like its profile, and
a profile that sets nothing looks like the mod's settings — so one menu of destructive actions can be red without
repainting everything else.

![Profile colours](/docs/assets/profile-colors.png)

Rather than picking six colours by hand, pick one **accent** and the editor fills the rest in from it. What it writes
are ordinary colours, yours to adjust one at a time afterwards.

The shape is yours too:

| Outline on, sectors touching | No outline at all |
|---|---|
| ![Outlined](/docs/assets/wheel.png) | ![No outline](/docs/assets/wheel-no-outline.png) |

| A five pixel gap between sectors | Vanilla slot plates under the icons |
|---|---|
| ![A sector gap](/docs/assets/wheel-gap.png) | ![Slot plates](/docs/assets/wheel-plates.png) |

Settings live in **Mods → RadialMenu → Config**, in five categories.

![The config categories](/docs/assets/config.png)

| Category | What it holds |
|---|---|
| **Behaviour** | Release or click to select, choosing by scrolling, how the editor opens, whether the game keeps taking input while the wheel is up, whether scripts may run |
| **Wheel** | Radii, the gap between sectors, line thickness, which lines are drawn, the soft edge, the plate behind each icon, icon size |
| **Colors** | Ring fill and outline, highlight fill and outline, the wash behind the screen, sprite tint |
| **Accent** | How far each of those colours lands from an accent colour, and how opaque it is |
| **Animation** | How the wheel arrives — none, fade, zoom, or one of three staggered orders — and how far the sector under the cursor leans out |

![The wheel settings](/docs/assets/config-wheel.png)

---

## Files and commands

Menus live in the game folder, not in `config/` — one file per profile, so one can be copied between installations or
handed to someone else:

```
RadialMenu/
├── profiles/
│   └── Mining.json
├── backups/
├── icons/
├── cache/
└── settings.json
```

A broken file never stops the client from starting: anything unreadable is repaired or skipped. The format is
documented for hand-editing in [docs/PROFILE_FORMAT.md](docs/PROFILE_FORMAT.md) — every action type, its parameters,
and which of them are required.

```
/radialmenu edit             # the profile manager
/radialmenu profiles         # list them, and say which is active
/radialmenu profile <name>   # switch
/radialmenu reload           # re-read the profiles from disk
```

---

## Development

Built from the GTNewHorizons [ExampleMod](https://github.com/GTNewHorizons/ExampleMod1.7.10) build script. The build
needs JDK 25.

```shell
./gradlew build          # compile, test, format check, jar
./gradlew runClient      # development client
./gradlew test           # unit tests
./gradlew spotlessApply  # format; the build fails on violations
```

The jar for the game is `build/libs/radialmenu-<version>.jar` — not the `-dev` one, which is built against development
mappings.
