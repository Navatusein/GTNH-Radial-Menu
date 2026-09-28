# GTNH-Radial-Menu

## Content

- [Information](#information)
- [Installation](#installation)
- [Usage](#usage)
- [Profiles](#profiles)
- [Configuration](#configuration)
- [Development](#development)

<a id="information"></a>

## Information

Client-side radial menu for Minecraft 1.7.10. Hold a key, point at a sector, release — the entry fires.

The point of the mod is to **press a keybinding that has no key assigned**. In a pack the size of GTNH there are
not enough keys on a keyboard to go round, so actions live on menu slots instead of in the controls screen. A slot
can also send a command, switch profiles, open a submenu, or run several of those in order.

The mod registers no network channels and has no server side: everything it does is something you could have done
by hand, so it works on unmodified servers and other players need nothing.

#### Controls

<kbd>R</kbd> - Hold to open the wheel, release to run the highlighted entry

<kbd>Right click</kbd> - Back to the parent menu, or close

<kbd>Esc</kbd> - Close

<kbd>Shift</kbd> + <kbd>Left click</kbd> - Edit the slot under the cursor, empty or not

<kbd>Shift</kbd> + <kbd>Left click</kbd> in the middle - Settings of the menu you are looking at

Next profile, previous profile and the profile editor have keybindings of their own, unbound by default. The open
key can be a mouse button.

#### What a slot can do

| Action | What it does |
|---|---|
| Keybind | Presses a keybinding, including one with no key assigned. Tap, toggle, or hold for a set time |
| Command | Sends chat lines or slash commands, with `{player}` `{dim}` `{x}` `{y}` `{z}` placeholders |
| Profile switch | Switches to another profile, or cycles to the next one |
| Submenu | Opens a nested wheel with its own slot count, layout and colours |
| Chain | Runs several of the above in order, with an optional delay between them |

#### Icons

Any item or block from the registry, subtypes included; a sprite from the bundled
[Phosphor](https://phosphoricons.com/) set; a status effect, vanilla or modded; or your own PNG dropped into
`RadialMenu/icons`.

<a id="installation"></a>

## Installation

Requirements:

- Minecraft 1.7.10 with Forge
- [GTNHLib](https://github.com/GTNewHorizons/GTNHLib)

Put the jar into `mods/`. The mod is client-side only — it does not need to be on the server, and the server does
not need to know about it.

<a id="usage"></a>

## Usage

Hold the open key. The wheel appears under the cursor, the sector you point at is highlighted, and releasing the key
runs it. An entry can be marked **Keep menu open**, which leaves the wheel up so it can be triggered again.

To fill the wheel in, hold the key and <kbd>Shift</kbd> + <kbd>Left click</kbd> a sector — an empty one creates an
entry, a filled one edits it. Shift-clicking the hole in the middle opens the settings of the menu itself: its name,
its layout, the order of its entries and its colours.

#### Layout

A menu is either **fixed** or **dynamic**. Fixed keeps every entry at the same angle whatever its neighbours do,
which is what makes muscle memory work; empty positions stay as gaps. Dynamic divides the ring by however many
entries there are, so a new entry moves all the others.

#### Commands

```
/radialmenu edit        # open the profile editor
/radialmenu profiles    # list the profiles and say which one is active
/radialmenu profile <name>  # switch to a profile
/radialmenu reload      # re-read the profiles from disk
```

<a id="profiles"></a>

## Profiles

Menus live in `<game folder>/RadialMenu/`:

```
RadialMenu/
├── profiles/
│   └── Default.json
├── icons/
└── settings.json
```

One file per profile, so a profile can be copied between installations or handed to someone else. A broken file
never stops the client from starting — anything unreadable is repaired or skipped.

Each profile carries its own **auto-bind rules**, which switch to it on their own when you join a world: by server
address, by single-player world folder, or any single-player world at all. The rules live inside the profile, so
copying the file carries them with it.

The format is documented for hand-editing in [docs/PROFILE_FORMAT.md](docs/PROFILE_FORMAT.md) — every action type,
its parameters, and which of them are required.

<a id="configuration"></a>

## Configuration

Settings live in `config/RadialMenu/general.cfg` and are editable in game from **Mods → RadialMenu → Config**, in
five categories:

| Category | What it holds |
|---|---|
| General | Release or click to select, choosing by scrolling, how the editor is opened, whether the game keeps taking input while the wheel is up |
| Wheel | Radii, the gap between sectors, line thickness, which lines are drawn, the soft edge, the plate behind each icon |
| Colors | Ring fill and outline, highlight fill and outline, the wash behind the screen, sprite tint |
| Accent | How far each of those colours lands from an accent colour, and how opaque it is |
| Animation | How the wheel arrives — none, fade, zoom, or one of three staggered orders — and how far the sector under the cursor leans out |

#### Colours

Colours inherit down a chain: **menu → profile → mod config**. A submenu that sets nothing looks like its profile,
and a profile that sets nothing looks like the mod's settings — so a menu of destructive actions can be red without
repainting everything else.

Rather than picking six colours by hand, pick one **accent**: the editor fills the rest in from it, using the
proportions in the Accent category. What it writes are ordinary colours, yours to adjust one at a time afterwards.

<a id="development"></a>

## Development

The build needs JDK 25 and uses the GTNewHorizons
[ExampleMod](https://github.com/GTNewHorizons/ExampleMod1.7.10) build script.

```shell
./gradlew build        # compile, test, format check, jar
./gradlew runClient    # development client
./gradlew test         # unit tests
./gradlew spotlessApply  # format; the build fails on violations
```

The jar for the game is `build/libs/radialmenu-<version>.jar` — not the `-dev` one, which is built against
development mappings.

`src/main/java/com/navatusein/radialmenu/core/` holds the menu tree, the profiles, the action data, the JSON codec
and the angle maths, and imports nothing from Minecraft. It is unit tested on its own:

```shell
grep -r "net.minecraft" src/main/java/com/navatusein/radialmenu/core/   # must be empty
```
