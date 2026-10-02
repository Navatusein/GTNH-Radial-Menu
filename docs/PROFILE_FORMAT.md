# RadialMenu profile format

Reference for writing a `RadialMenu` profile by hand — or for an assistant asked to generate one. Everything here is
taken from the mod's own reader (`core/json/ConfigCodec`, `core/model/`, `core/action/`), not from a design document,
so it describes what the mod actually accepts.

RadialMenu is a **client-side** Minecraft 1.7.10 mod. A profile decides what the radial menu contains: which entries
sit in which sectors, what each one does, and what the wheel looks like.

## Where the file goes

```
<minecraft folder>/RadialMenu/
├── settings.json          which profile is active
├── profiles/
│   ├── default.json       one file per profile
│   └── mining.json
└── icons/                 PNG files for kind "file" icons
```

Not in `config/`. The file name is the profile name, with `\ / : * ? " < > |` replaced by `_`. Keep the `name` field
inside the file equal to the file name — the mod matches profiles by file name, and a mismatch means the name shown
in the wheel is not the name you switch to.

The mod reads profiles at startup and when switching. A malformed file is repaired rather than rejected: unknown
fields are ignored, missing ones get defaults, and out-of-range numbers are clamped. **A broken profile never stops
the client from starting**, which also means a mistake tends to show up as an entry that quietly does nothing rather
than as an error.

## Smallest working profile

```json
{
  "formatVersion": 1,
  "name": "default",
  "bindings": [],
  "root": {
    "title": "default",
    "layout": { "mode": "fixed", "slots": 8 },
    "children": [
      {
        "title": "Inventory",
        "icon": { "kind": "item", "id": "minecraft:chest", "meta": 0 },
        "action": {
          "type": "keybind",
          "params": { "binding": "key.inventory", "mode": "tap" }
        }
      }
    ]
  }
}
```

## Top level

| Field | Type | Meaning |
|---|---|---|
| `formatVersion` | int | Always `1`. Omitted or `<= 0` becomes `1`. |
| `name` | string | Profile name. Match the file name. |
| `bindings` | array | Auto-activation rules. `[]` or omitted means manual switching only. |
| `style` | object | Colours for every menu in this profile. Omit to inherit the mod config. |
| `root` | object | The top-level wheel, as a node with `children`. |

## Node

One object type covers both an entry and a submenu.

| Field | Type | Meaning |
|---|---|---|
| `title` | string | Shown in the middle of the wheel while the entry is hovered, and in the header when you are inside a submenu. |
| `icon` | object | See [Icons](#icons). Omit for no icon. |
| `keepOpen` | bool | `true` leaves the wheel open after the entry fires, so it can be triggered repeatedly. Default `false`. |
| `action` | object | What the entry does. Makes the node an **entry**. |
| `children` | array | Makes the node a **submenu** — its own wheel. |
| `layout` | object | Sector layout of this node's children wheel. Submenus only. |
| `style` | object | Colour overrides for this node's children wheel. Submenus only. |

**A node is one or the other.** `children` present ⇒ submenu, and any `action` on it is dropped. `action` present and
no `children` ⇒ entry, and its `layout` and `style` are dropped. Never write both.

There is no `"type": "submenu"` action. The editor presents "Submenu" as a choice of what a slot does, but in the file
a submenu is simply a node that has `children`.

## Layout and where entries land

```json
"layout": { "mode": "fixed", "slots": 8 }
```

| `mode` | Behaviour |
|---|---|
| `"fixed"` | The wheel always has `slots` sectors. Child index *is* sector index. A `null` child is an empty sector, drawn dimmed. |
| `"dynamic"` | The wheel has as many sectors as there are non-null children. `null` entries are skipped, not drawn. |

`slots` is clamped to **2–24**, default `8`, and only matters for `fixed`. A node with `children` but no `layout`
gets `dynamic`.

**Sector 0 is at the top** (12 o'clock) and they run **clockwise**. Sectors are centred on their angle, so the first
one straddles the top rather than starting there.

Prefer `fixed` for anything the player will learn by muscle memory: an entry keeps its angle no matter what is added
around it. With `dynamic`, adding one entry moves every other one.

Under `fixed` you do not need to pad the list with trailing `null`s — the mod pads it. You **do** need interior
`null`s to push later entries into the sectors you want:

```json
"children": [ {"title": "Top"}, null, null, {"title": "Bottom"} ]
```

That puts "Top" at 12 o'clock and, on an 8-slot wheel, "Bottom" at 4:30. The `null`s are two empty sectors.

## Icons

```json
"icon": { "kind": "item", "id": "minecraft:torch", "meta": 0 }
"icon": { "kind": "sprite", "id": "phosphor:feather", "color": "#7FD4FF" }
"icon": { "kind": "file", "id": "backpack.png" }
"icon": { "kind": "effect", "id": "potion.moveSpeed" }
```

| `kind` | `id` | Notes |
|---|---|---|
| `"item"` | Registry name, e.g. `minecraft:diamond_pickaxe`, `gregtech:gt.metaitem.01` | `meta` selects the damage/metadata variant, default `0`. Items keep their own colours; `color` is ignored. |
| `"sprite"` | `phosphor:<name>` | 1512 bundled monochrome icons. Names are listed in `assets/radialmenu/icons/phosphor.json` inside the jar — e.g. `phosphor:sword`, `phosphor:axe`, `phosphor:gear`, `phosphor:house`. The `phosphor:` prefix is required. |
| `"file"` | File name inside `RadialMenu/icons/` | e.g. `backpack.png`. The player has to put the file there. |
| `"effect"` | A potion's unlocalized name, e.g. `potion.moveSpeed`, `potion.nightVision` | Drawn from the sheet vanilla uses for the inventory's effect list, so modded effects work too. Identified by name rather than id, which shifts between packs. Carries its own colours; `color` is ignored. |

`color` applies to `sprite` and `file` only, as `#RRGGBB`:

- **omitted / `null`** — keep the artwork's own colours. Right for a PNG.
- **`""`** — inherit: the menu's tint, then the profile's, then the mod config's. Right for a sprite.
- **`"#RRGGBB"`** — that colour.

Those two blanks mean different things; do not collapse them.

If you are not sure a sprite name exists, use an `item` icon instead. An unknown sprite draws nothing, and a slot
with no icon is harder to recognise than one showing the wrong block.

## Actions

`action` is `{ "type": ..., "params": { ... }, "steps": [ ... ] }`.

> **Every value in `params` is a string**, numbers and booleans included: `"20"`, `"true"`. A real JSON `20` or `true`
> is read as its text rather than refused — it used to throw hard enough to take the whole profile with it — but write
> the quoted form, which is what the editor saves.

A value may also be written as an **array of lines**, which is the readable way to put a chain of commands or a Lua
script in a file:

```json
"params": { "command": ["/home", "/time day"] }
```

The array becomes one string with `\n` between the lines — exactly the same thing the quoted form means. A value with
newlines in it is written back out as an array, so a file written this way still looks like this after the editor saves
it.

### `keybind` — press a keybinding

The reason the mod exists: this works on a keybinding that has **no key assigned** in the controls screen.

| Param | Required | Meaning |
|---|---|---|
| `binding` | **yes** | The keybinding id, e.g. `key.inventory`. |
| `category` | no | Its category, e.g. `key.categories.inventory`. Only needed to tell apart two mods that registered the same description. |
| `mode` | no | `"tap"` (default), `"toggle"`, `"hold"`. |
| `holdTicks` | no | How long `hold` keeps the key down. Default `"20"` — 20 ticks is one second. |
| `shift`, `ctrl`, `alt` | no | `"true"` holds that modifier down for as long as the binding is pressed. Default `"false"`. |
| `sneak` | no | `"true"` starts sneaking, then presses the binding a tick later. Default `"false"`. |

```json
{ "type": "keybind", "params": { "binding": "key.sneak", "mode": "toggle" } }
```

`tap` presses once. `toggle` holds the key down until the entry is chosen again. `hold` holds it for `holdTicks`.

The modifiers are for mods that read the keyboard rather than a keybinding — `GuiScreen.isShiftKeyDown()` asks LWJGL
directly, so no binding, injected or otherwise, can tell it Shift is down.

`sneak` is the other half of that, and a different thing entirely: it holds the sneak binding and delays the press by a
tick, because `isSneaking()` reads a copy the player updates once per tick. Use it for mods that ask whether you are
sneaking - Backpack puts the backpack on your back that way - and `shift` for mods that ask about the key.

**Vanilla keybinding ids** (1.7.10, read from the game's own code):

```
key.attack   key.use      key.forward  key.left     key.back     key.right
key.jump     key.sneak    key.sprint   key.drop     key.inventory
key.chat     key.command  key.playerlist  key.pickItem
key.screenshot  key.fullscreen  key.togglePerspective  key.smoothCamera
key.hotbar.1 … key.hotbar.9
```

Categories: `key.categories.movement`, `key.categories.gameplay`, `key.categories.inventory`,
`key.categories.multiplayer`, `key.categories.misc`, `key.categories.stream`.

**For modded keybindings, do not guess the id.** Open the slot editor in game and use the keybinding picker: it lists
every registered binding and writes the exact id. An id no mod registered logs a warning and does nothing.

Two limits worth knowing: a mod that reads the physical key with `Keyboard.isKeyDown` instead of its own binding
object cannot be driven this way at all, and this is unfixable. Everything that reads its own `KeyBinding` — which is
the normal way, and what AE2, Draconic Evolution and AdventureBackpack2 all do — works.

### `command` — send chat lines or slash commands

| Param | Required | Meaning |
|---|---|---|
| `command` | **yes** | One or more lines, separated by `\n`. Blank lines are dropped. |
| `delayTicks` | no | Ticks between lines. `"0"` (default) sends them all at once. |
| `cycle` | no | `"true"` sends one line per activation, stepping through the list. Default `"false"`. |

```json
{ "type": "command", "params": { "command": "/home\n/time day", "delayTicks": "20" } }
```

A line starting with `/` is a command; anything else is chat. It goes through the ordinary chat path, so the server
sees exactly what it would have seen had the player typed it — nothing works that the player could not do by hand.

Placeholders in the text, substituted when it is sent:

`{player}` `{dim}` `{x}` `{y}` `{z}`

An unknown placeholder is left as written rather than blanked, so a typo is visible instead of silently turning into
an empty argument.

Pair `cycle` with `keepOpen` for something like a set of waypoints on one slot.

### `profileSwitch` — change the active profile

| Param | Required | Meaning |
|---|---|---|
| `profile` | no | Profile name. **Blank or omitted cycles to the next profile**, which is a deliberate choice rather than an omission. |

```json
{ "type": "profileSwitch", "params": { "profile": "mining" } }
```

### `sequence` — a chain of actions

Runs `steps` in order.

| Param | Required | Meaning |
|---|---|---|
| `delayTicks` | no | Ticks between steps. `"0"` (default) runs them all at once. |

```json
{
  "type": "sequence",
  "params": { "delayTicks": "10" },
  "steps": [
    { "type": "keybind", "params": { "binding": "key.inventory", "mode": "tap" } },
    { "type": "command", "params": { "command": "/home" } }
  ]
}
```

Rules that matter when generating one:

- A step is itself an action object. A step may be another `sequence`; nesting is capped at 8 deep.
- A step with no `type` is dropped when the file is read.
- **A step missing a required parameter is skipped at run time** — a `keybind` step with no `binding`, a `command`
  step with no text. The chain still runs its other steps.
- A step cannot be a submenu. There is no such action.
- Triggering the entry again restarts the chain rather than overlapping a second copy.

### `script` — run a Lua script

For the one thing stored parameters cannot express: a menu whose entries are not known until the game is running,
because they came out of a server's reply.

| Param | Required | Meaning |
|---|---|---|
| `script` | **yes** | The source. A plain string with `\n`, or an array of lines. |
| `timeoutTicks` | no | How long the script may stay alive, waiting included. Default `"600"`. `"0"` means no limit. |

```json
{
  "type": "script",
  "params": {
    "script": [
      "chat.send('/home list ' .. player.name)",
      "local list = chat.await('^' .. player.name .. ': %d+ / %d+: (.+)$')",
      "if not list then return end",
      "local homes = {}",
      "for name in list:gmatch('[^,%s]+') do homes[#homes + 1] = name end",
      "local pick = menu.open(homes, { title = 'Homes', slots = 8 })",
      "if pick then chat.send('/home ' .. pick .. ' ' .. player.name) end"
    ]
  }
}
```

**`docs/SCRIPTING.md` is the reference for what a script can call** — this section is only the shape it is stored in.
Two things worth knowing before writing one into a file:

- The script travels with the profile. A profile copied from somebody else runs their code, and a script can send
  anything to the server that the player could type. `enableScripts` in the mod config refuses to run them at all.
- Patterns are **Lua patterns**, not regular expressions: `%d` rather than `\d`, and no alternation. A pattern written
  as a regex quietly matches nothing.

## Colours

`style` on the profile, and `style` on any submenu node:

```json
"style": {
  "ringColor": "0x99101010",
  "highlightColor": "#4A90D9",
  "iconColor": "#FFFFFF",
  "borderColor": "0x60FFFFFF",
  "highlightBorderColor": "0xCCFFFFFF",
  "backgroundColor": "0x80101010"
}
```

| Field | What it colours |
|---|---|
| `ringColor` | The ring itself. |
| `highlightColor` | The sector under the cursor. |
| `iconColor` | Tint for `sprite` icons that have no colour of their own. Items and PNGs are unaffected. |
| `borderColor` | The lines: sector dividers and the ring's inner and outer edges. |
| `highlightBorderColor` | The outline around the sector under the cursor. |
| `backgroundColor` | A wash over the screen behind the wheel. Only drawn when `dimBackground` is on in the mod config. |

Omit a field, or the whole `style`, to inherit. **Colours inherit down a chain: menu → profile → mod config**, so a
submenu that sets nothing looks like its profile, and a profile that sets nothing looks like the mod's settings.

There is no accent in this file, and that is deliberate. The editor offers one as a way of filling these colours
in from a single hue - how far each lands from it comes from the `accent*` settings in the mod's config - but what
it writes is the colours themselves. A file always says outright what it is drawn with, and a colour written by an
accent is afterwards an ordinary colour to adjust. `iconColor` is never among them: an icon that changes hue with
the ring stops saying what it is.

Two accepted forms, and the difference matters:

- **`"0xAARRGGBB"`** — eight digits, taken exactly as written, opacity included.
- **`"#RRGGBB"`** — six digits: the colour you chose, keeping the **opacity of the value it replaces**.

That second rule exists because a ring is translucent by nature. Writing `"#920A0A"` where an eight-digit value was
expected would otherwise mean alpha `0x00` and the ring would simply vanish. `iconColor` ignores alpha either way —
a tint multiplies a texture, so transparency would only dim it.

## Auto-bind rules

Rules live inside the profile, so copying the file carries them.

```json
"bindings": [
  { "type": "server", "value": "gtnh.example.com" },
  { "type": "world",  "value": "New World" },
  { "type": "singleplayer" }
]
```

| `type` | Matches |
|---|---|
| `"server"` | The server address the client connected to. Case-insensitive, exact — not a pattern. |
| `"world"` | The single-player world's **folder** name, which is not always the name shown in the world list. |
| `"singleplayer"` | Any single-player world. `value` is ignored. |

The reliable way to fill in a server address is the "Use current world" button in the rules editor, which writes it in
exactly the form the client will later compare against.

## settings.json

```json
{ "formatVersion": 1, "activeProfile": "default" }
```

Only which profile is active. The mod rewrites this on every switch, so edit it while the game is closed.

## Things that are easy to get wrong

- **`params` values are strings.** `"delayTicks": 20` does not work; `"delayTicks": "20"` does.
- **A node is an entry or a submenu, never both.** `children` wins, and the `action` is dropped.
- **There is no `submenu` action type** in the file — a submenu is a node with `children`.
- **`null` in `children` is meaningful** under `fixed`: it is an empty sector holding a position.
- **`"#RRGGBB"` keeps the inherited opacity; `"0xAARRGGBB"` does not.**
- **An icon `color` of `null` and of `""` are different** — own colours versus inherit.
- **Do not invent keybinding ids.** An unregistered one silently does nothing but log a warning.
- **A profile name is its file name.** Rename both together.
- **If a binding is not in the picker, no `keybind` entry will work for it.** The picker lists every binding
  registered with Forge, which is everything the injector can reach; a mod that keeps its keys to itself and polls
  the keyboard directly has no binding to press. Writing such an id in by hand does nothing.

## Worked example

An 8-slot fixed wheel: two direct actions, a chain, a submenu, and a profile switch.

```json
{
  "formatVersion": 1,
  "name": "mining",
  "bindings": [
    { "type": "server", "value": "gtnh.example.com" }
  ],
  "style": {
    "ringColor": "0xAA1A1410",
    "highlightColor": "#D98B4A"
  },
  "root": {
    "title": "mining",
    "layout": { "mode": "fixed", "slots": 8 },
    "children": [
      {
        "title": "Inventory",
        "icon": { "kind": "item", "id": "minecraft:chest", "meta": 0 },
        "action": {
          "type": "keybind",
          "params": { "binding": "key.inventory", "mode": "tap" }
        }
      },
      {
        "title": "Sneak",
        "icon": { "kind": "sprite", "id": "phosphor:arrow-down", "color": "" },
        "keepOpen": true,
        "action": {
          "type": "keybind",
          "params": { "binding": "key.sneak", "mode": "toggle" }
        }
      },
      {
        "title": "Go home",
        "icon": { "kind": "item", "id": "minecraft:bed", "meta": 0 },
        "action": {
          "type": "sequence",
          "params": { "delayTicks": "10" },
          "steps": [
            { "type": "command", "params": { "command": "/home" } },
            { "type": "keybind", "params": { "binding": "key.inventory", "mode": "tap" } }
          ]
        }
      },
      null,
      {
        "title": "Waypoints",
        "icon": { "kind": "item", "id": "minecraft:map", "meta": 0 },
        "layout": { "mode": "dynamic" },
        "style": { "highlightColor": "#4A90D9" },
        "children": [
          {
            "title": "Base",
            "icon": { "kind": "item", "id": "minecraft:crafting_table", "meta": 0 },
            "action": { "type": "command", "params": { "command": "/warp base" } }
          },
          {
            "title": "Mine",
            "icon": { "kind": "item", "id": "minecraft:iron_pickaxe", "meta": 0 },
            "action": { "type": "command", "params": { "command": "/warp mine" } }
          }
        ]
      },
      null,
      null,
      {
        "title": "Next profile",
        "icon": { "kind": "sprite", "id": "phosphor:arrows-clockwise", "color": "" },
        "action": { "type": "profileSwitch", "params": { "profile": "" } }
      }
    ]
  }
}
```

Sector map for that wheel, clockwise from the top: Inventory, Sneak, Go home, *(empty)*, Waypoints, *(empty)*,
*(empty)*, Next profile.

## Checking the result

Put the file in `RadialMenu/profiles/`, then in game:

| Command | Does |
|---|---|
| `/radialmenu profiles` | Lists the profiles it found and which is active. |
| `/radialmenu profile <name>` | Switches to one. |
| `/radialmenu reload` | Re-reads the files from disk, so you can edit without restarting. |
| `/radialmenu edit` | Opens the profile manager. |

If an entry does nothing, the log usually says why: an unregistered keybinding and an action type with no executor
both log a warning naming what failed.
