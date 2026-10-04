# RadialMenu scripting

Reference for the `script` action — for someone writing one by hand, or for an assistant asked to generate one.

Everything here is taken from the mod's own reader and host (`core/script/`, `client/script/`), not from a design
document, so it describes what the mod actually runs.

## What it is for

Every other action type is data the mod interprets: press this binding, send these lines, open this submenu. That
covers what a slot usually wants to do, and it deliberately covers nothing else.

A script is for the case where the menu's contents are not known until the game is running. The example this was
designed around: a server's `/home list` answers with a line that has to be read, split, and turned into a wheel —

```
Nortcast: 5 / 10: dungeon, gradka, home, mine, sand
```

— after which choosing a sector sends `/home gradka Nortcast`. No amount of stored parameters expresses that, because
the entries come out of the server's reply.

The language is **Lua 5.2** (LuaJ 3.0). A script is a coroutine: it runs, asks the game for something, and is
suspended until the answer exists. That is what lets it wait for a chat reply or for the player's choice without
freezing the client.

## The whole example

```lua
local me = player.name

chat.send("/home list " .. me)
local list = chat.await("^" .. me .. ": %d+ / %d+: (.+)$", 60)
if not list then
  notify("No answer from /home list")
  return
end

local homes = {}
for name in list:gmatch("[^,%s]+") do
  homes[#homes + 1] = name
end

local pick = menu.open(homes, { title = "Homes", slots = 8 })
if pick then
  chat.send("/home " .. pick .. " " .. me)
end
```

## Where the script lives

In the profile, like any other action. Nothing about the file format changes — `script` is one more type id:

```json
{
  "title": "Homes",
  "icon": { "kind": "item", "id": "minecraft:bed", "meta": 0 },
  "action": {
    "type": "script",
    "params": {
      "script": "local me = player.name\nchat.send(\"/home list \" .. me)",
      "timeoutTicks": "200"
    }
  }
}
```

| Param | Required | Meaning |
|---|---|---|
| `script` | **yes** | The source. One string with `\n` line breaks, or a JSON array of lines — the reader accepts both, and an array is far easier to edit by hand. |
| `timeoutTicks` | no | How long the whole script may stay alive, waiting included. Default `"600"` — 30 seconds. `"0"` means no limit, which is worth avoiding. |

Editing a long script through the in-game multiline editor is a column of single-line fields; it is fine for ten lines
and unpleasant for fifty. The practical loop is an external editor on the profile JSON plus `/radialmenu reload`.

> **A profile carries its scripts.** Copying someone's profile file runs their code the next time that slot is chosen,
> and a script can send anything to the server under your name. That is the file owner's business, the same as any
> macro file — but it is worth knowing before importing a stranger's profile. `enableScripts` in the mod config turns
> the action type off entirely.

## Execution model

- A script starts on the client tick after the wheel closes, like every other action.
- It runs on the client tick and **never blocks it**. Anything that waits — a chat reply, the player's choice,
  `sleep` — suspends the script; the game carries on and the script is resumed on a later tick.
- One copy of a script runs per entry. Choosing the same slot again cancels the run in progress and starts over,
  rather than interleaving two of them.
- Leaving the world, disconnecting, or `timeoutTicks` expiring cancels it.
- An error stops the script, prints the message and line to chat, and logs it. It never takes the client down.
- Every resume has an instruction and time budget. A script that loops without ever waiting is killed rather than
  freezing the game, so `while true do end` costs you the script, not the session.

## API

### `player`

| Field | Value |
|---|---|
| `player.name` | The player's name. |
| `player.dim` | Dimension id. |
| `player.x`, `player.y`, `player.z` | Block coordinates, floored. |
| `player.chunkX`, `player.chunkZ` | The chunk those coordinates fall in. Negative blocks work the way the game means them: block `-200` is chunk `-13`. |
| `player.xInChunk`, `player.zInChunk` | Where in that chunk, `0`–`15`. |
| `player.facing` | `"north"`, `"south"`, `"east"` or `"west"` — whichever quarter of the compass the player is looking into. |
| `player.yaw` | `0`–`360`. Minecraft's convention, so **`0` is south**, `90` west, `180` north, `270` east. Normalised, so turning around a few times does not change the number you compare against. |
| `player.pitch` | `-90` (straight up) to `90` (straight down), `0` level. |

Read when touched, so after a teleport the next read is the new position — and the next read of `yaw` is wherever
the mouse has got to since.

```lua
notify(("%s at %d,%d,%d in chunk %d,%d, facing %s")
  :format(player.name, player.x, player.y, player.z, player.chunkX, player.chunkZ, player.facing))
```

### `chunk.of(world)`, `chunk.offset(world)`, `chunk.toWorld(chunk, offset)`

The same arithmetic for *any* coordinate, not only the player's — for a position a script read out of a chat line,
say. `chunk.size` is 16.

| Call | Gives |
|---|---|
| `chunk.of(-200)` | `-13` — the chunk that block is in. |
| `chunk.offset(-200)` | `8` — how far into it. |
| `chunk.toWorld(-13, 8)` | `-200` — the two put back together. |
| `chunk.toWorld(-13)` | `-208` — the chunk's own corner; the offset defaults to 0. |

The offset is not clamped: `chunk.toWorld(5, 19)` is the block four past that chunk's edge, which is what a script
doing its own arithmetic means by it.

These are the only calls in the API that **do not suspend** — they ask the game nothing, so they return there and
then and may be used in a loop without spending a tick a turn.

```lua
-- The middle of the chunk the player is standing in
local x = chunk.toWorld(player.chunkX, 8)
local z = chunk.toWorld(player.chunkZ, 8)
chat.send(("/tp %d %d %d"):format(x, player.y, z))
```

### `player.health`, `player.food`, `player.air`, `player.held`

| Field | Value |
|---|---|
| `player.health` | Hearts as the game counts them: `20` is full. |
| `player.food` | Hunger, `0`–`20`. |
| `player.air` | Breath under water, in ticks. `300` when full. |
| `player.held` | What is in your hand as an item table (below), or `nil` for an empty one. |

### `world`

| Field | Value |
|---|---|
| `world.time` | Ticks since this morning, `0`–`23999`. Dawn is `0`, dusk `12000`. |
| `world.day` | How many days the world has seen, the first being `0`. |
| `world.isDay` | `true` while the sun is up — `world.time < 12000`. |
| `world.name` | The server's address, or the save's folder in single player. The same string a profile's auto-bind rules match on. |

### `world.lookingAt()`

What the crosshair is on, or `nil` for thin air. A block and an entity come back as the same shape, with `kind`
saying which:

| Field | Value |
|---|---|
| `kind` | `"block"` or `"entity"` |
| `id` | Registry name — `minecraft:furnace`, `gregtech:machine` — or the entity's type. |
| `label` | What the game calls it, translated. |
| `meta` | The block's damage value. `0` for an entity. |
| `x`, `y`, `z` | Block position, or the entity's own, floored. |

```lua
local t = world.lookingAt()
if t and t.kind == "block" then
  notify(("%s at %d %d %d"):format(t.label, t.x, t.y, t.z))
end
```

### `inventory`

Your own inventory, hotbar included, as the client already knows it — nothing is asked of the server.

| Call | Gives |
|---|---|
| `inventory.count(id)` | How many you are carrying, every damage value together. |
| `inventory.count(id, meta)` | Only that variant. |
| `inventory.has(id)` / `inventory.has(id, meta)` | The same question as a boolean. |
| `inventory.items()` | A list of item tables, empty slots left out. |

The damage value is compared **only when you name one**: a pickaxe half worn through is still a pickaxe. Name it
where the value is the difference — wool, dye, a GregTech meta item.

An item table is:

| Field | Value |
|---|---|
| `id`, `meta`, `count` | Registry name, damage value, stack size. |
| `label` | The name the game prints. |
| `slot` | Where it sits, counted from 1. The hotbar is 1–9. `0` for a held item. |
| `icon` | The same stack spelled as an icon, so a listing can be opened as a wheel unchanged. |

```lua
local items = {}
for _, item in ipairs(inventory.items()) do
  if item.count >= 16 then
    items[#items + 1] = { key = item.id, label = item.label, icon = item.icon }
  end
end
menu.open(items, { title = "Stacks" })
```

### `store.get(key [, default])`, `store.set(key, value)`

What a script remembers between runs, in `RadialMenu/script-store.json`.

**Strings both ways.** A script that wants a number writes one and reads it back with `tonumber`. `store.set(key,
nil)` forgets the key. `store.get` answers the second argument when nothing is stored, and `nil` when there is no
second argument — so a missing key is still testable.

**One store, shared by every script.** Two entries running the same script — a "go home" and a "set home" — want the
same value, which is the whole point. Prefix your keys (`home.last`, not `last`) so unrelated scripts do not collide.

```lua
local last = store.get("home.last")
if last then chat.send("/home " .. last) end
```

### `prompt(title [, initial])`

Asks the player to type one line, and returns it — or `nil` if they cancelled, which is the same nothing a dismissed
menu gives. The title is shown as written.

Empty text is refused by the box itself, so a returned string is never blank. The prompt will not open over somebody
else's screen; it answers `nil` instead of taking it away mid-use.

```lua
local name = prompt("Name this home", player.name)
if name then chat.send("/sethome " .. name) end
```

### `chat.send(text)`

Sends one line exactly as typing it would: a leading `/` makes it a command, client-side commands are offered it
first, and the server sees nothing it could not have seen from the keyboard. `{player}` `{dim}` `{x}` `{y}` `{z}`
still work, though in a script plain concatenation is usually clearer.

### `chat.await(pattern, timeoutTicks)`

Suspends until a chat line matches, then returns its **captures** — the first one, or all of them if the pattern has
several. Returns `nil` on timeout. `timeoutTicks` defaults to `60`, three seconds.

```lua
local list        = chat.await("^Warps: (.+)$")
local who, amount = chat.await("^(%S+) sent you (%d+) coins$", 100)
```

- **These are Lua patterns, not regular expressions.** `%d` not `\d`, `%s` not `\s`, no alternation, no lazy
  quantifiers. A pattern written as a regex quietly matches nothing.
- Formatting codes are stripped before matching, so a coloured reply is matched on its text alone.
- Send the command first, then await. A reply that arrives in the same tick is not lost — the host keeps a short
  backlog — but a line that came before the script started is not seen.

### `chat.awaitAll(pattern, windowTicks)`

Collects every line matching the pattern for `windowTicks` and returns a list of capture tables. For a reply that
arrives as several lines — a paginated list, a table with a header.

```lua
local rows = chat.awaitAll("^ %- (%S+) %((%d+)%)$", 40)
for _, row in ipairs(rows) do
  notify(row[1] .. " = " .. row[2])
end
```

### `menu.open(items, opts)` → `key, item`

Opens a wheel built from `items` and suspends until the player chooses. Returns the chosen item's **key**, and the
item table itself as a second value. Returns `nil` if the player cancelled with Escape or the menu was closed.

```lua
local key       = menu.open(items)
local key, item = menu.open(items, { title = "Homes" })
```

An empty `items` returns `nil` immediately rather than opening an empty wheel.

A choice can come from a submenu as well, in which case the key and the item are that nested entry's — see
[Submenus](#submenus).

### `menu.close()` / `menu.update(items)`

Only meaningful for a menu opened with `keepOpen` — see below. `close` shuts it, `update` replaces its contents in
place.

### `sleep(ticks)`

Suspends for that many ticks. 20 ticks is one second.

### `action.run(spec)`

Runs any of the mod's own action types by data, which is how a script reaches everything the editor can do:

```lua
action.run{ type = "keybind", params = { binding = "key.inventory", mode = "tap" } }
action.run{ type = "profileSwitch", params = { profile = "mining" } }
```

Sugar for the two common ones: `key.press(binding, mode, holdTicks)` and `profile.switch(name)`.

`params` values are strings everywhere else in the file format; here numbers and booleans are converted for you.

### `notify(text)` / `log(...)`

`notify` prints a line in your own chat, visible to nobody else — the right way to report that a parse failed. `log`
writes to the client log, for anything the player should not have to read.

## Items

An item is a table. A plain string is shorthand for `{ key = s, label = s }`.

| Field | Meaning |
|---|---|
| `key` | What `menu.open` returns. Defaults to `label`; with neither, the item's index as a number. |
| `label` | Shown in the middle of the wheel while the sector is hovered. Defaults to `key`. |
| `icon` | A string or a table — see below. Omit for no icon. |
| `color` | `#RRGGBB` tint, for `sprite` and `file` icons only. |
| `onPick` | Optional function, called when this item is chosen. |
| `items` | A list of its own, which makes this entry a **submenu** rather than something to pick — see below. |

Anything else you put in the table is yours and comes back untouched in the second return value: a command to send, a
coordinate, a nested list.

```lua
menu.open({
  "dungeon", "gradka", "home",
  { key = "mine",  label = "Mine",  icon = "minecraft:iron_pickaxe" },
  { key = "clay",  label = "Clay",  icon = "minecraft:stained_hardened_clay:5" },
  { key = "base",  label = "Base",  icon = "sprite:phosphor:house", color = "#7FD4FF" },
  { key = "sand",  label = "Sands", icon = "file:sand.png" },
  { key = "speed", label = "Buff",  icon = "effect:potion.moveSpeed" },
})
```

Duplicate keys are reported in the log: a key you cannot resolve back to one item is a silent bug waiting to happen.

### Icon strings

| Form | Kind |
|---|---|
| `minecraft:stone` | Item. No prefix means an item. |
| `minecraft:stained_hardened_clay:5` | Item with metadata. The trailing number is the damage value, read from the end — a registry name already contains a colon. |
| `sprite:phosphor:sword` | One of the bundled monochrome icons. Names are listed in `assets/radialmenu/icons/phosphor.json` inside the jar. |
| `file:backpack.png` | A PNG the player put in `RadialMenu/icons/`. |
| `effect:potion.moveSpeed` | A status effect, by the potion's unlocalized name. |

The table form spells the same thing out and is the one to use when a name is built at runtime:
`icon = { kind = "sprite", id = "phosphor:house", color = "#7FD4FF" }`.

An icon that does not resolve draws nothing; it is not an error. If you are unsure a sprite name exists, use an item
icon — a slot with no icon is harder to recognise than one showing the wrong block.

## Submenus

An entry carrying an `items` list of its own is a submenu: pointing at it opens its entries instead of picking it. The
wheel draws it exactly as it draws a submenu configured in a profile, which means `opening` decides how it arrives —
`"replace"` (the default) drills in and the parent comes back on a right-click, `"inline"` unfolds it as a ring around
the entry while the parent stays on screen.

```lua
local pick = menu.open({
  { key = "home", label = "Home", icon = "minecraft:bed" },
  { label = "Warps", icon = "minecraft:compass", opening = "inline",
    items = {
      { key = "spawn", label = "Spawn" },
      { key = "mine",  label = "Mine"  },
    } },
  { key = "tp", label = "Accept tp", icon = "minecraft:ender_pearl" },
})
```

`pick` is whatever the player ended on, at whatever depth — `"spawn"` here is a perfectly ordinary answer, and so is its
`onPick`. A submenu entry is never itself the answer: its own `key` is never returned and its own `onPick` is never
called, because choosing it is how its entries are reached.

Besides `items`, a submenu entry may carry `opening`, `slots` and the colour options below (`accent`, `ring`,
`highlight`, `border`, `highlightBorder`, `background`) — the same options `menu.open` takes for a wheel, because that
is what it is. `title` is not among them: the entry's `label` is its name. Neither is the `icon` colour option, since
on an entry `icon` is already the entry's own picture; a submenu's icon tint inherits.

A list nested this way has to be built **before** the wheel opens, because the player reaches it by pointing rather
than by choosing: there is no moment in between for the script to go and ask the server. For a list that is not known
until then, use the next section.

### Opening one menu inside another

`menu.open` itself takes `opening = "inline"`, and then the menu does not replace the wheel: it unfolds as a ring
around the entry the player just chose. That is the dynamic half of the same picture — the list may come from anything
the script did in between, chat included.

It needs the wheel to still be on screen, so the entry it hangs off has to be one the wheel stayed up for: open the
parent menu with `keepOpen = true`.

```lua
local pick = menu.open({
  { key = "home",  label = "Home" },
  { key = "warps", label = "Warps", icon = "minecraft:compass",
    onPick = function()
      chat.send("/warp list")
      local line = chat.await("^Warps: (.+)$")
      if not line then return end

      local warps = {}
      for w in line:gmatch("[^,%s]+") do warps[#warps + 1] = w end

      -- A ring around the "Warps" entry, with the wheel it came from still there behind it.
      local w = menu.open(warps, { opening = "inline" })
      if w then chat.send("/warp " .. w) end
    end },
}, { keepOpen = true })
```

Folding that ring away — a right-click on it, or on the entry it came from — is a cancellation like any other, and
`menu.open` returns `nil`. Escape closes the whole wheel, which is also a cancellation.

Where it cannot unfold — no wheel left on screen, or `scrollToSelect` on in the mod config, which has one ring and one
index and no way to cross into another — the menu opens as a wheel of its own instead. A worse menu than the one asked
for, but a working one. `title` is ignored for an inline menu: the entry it unfolds from is its name.

### `onPick`

Optional, and independent of the return value. If the chosen item has one, it is called with `(key, item)` **before**
`menu.open` returns, and its own return value is ignored — so `menu.open` always gives you the key, whether or not any
item carries a callback.

A callback runs inside the script, so it may itself wait: nesting `chat.await` or another `menu.open` in it is
ordinary code.

```lua
local pick = menu.open({
  { key = "home", label = "Home", icon = "minecraft:bed" },
  { key = "warps", label = "Warps", icon = "minecraft:compass",
    onPick = function()
      chat.send("/warp list")
      local line = chat.await("^Warps: (.+)$")
      if not line then return end

      local warps = {}
      for w in line:gmatch("[^,%s]+") do warps[#warps + 1] = w end

      local w = menu.open(warps, { title = "Warps" })
      if w then chat.send("/warp " .. w) end
    end },
})

if pick == "home" then chat.send("/home " .. player.name) end
```

Mixing the two styles in one menu is fine. Use `onPick` when each entry does something different, and the return value
when they differ only in a parameter.

## Menu options

```lua
menu.open(items, {
  title = "Homes",
  slots = 8,
  accent = "#4A90D9",
  ring = "#202830",
  highlight = "#4A90D9",
  keepOpen = true,
})
```

| Option | Meaning |
|---|---|
| `title` | Shown in the header at the top of the screen, after the profile name. The hovered item's `label` is what appears in the middle of the wheel. |
| `slots` | Sector count, **2–24**. Omitted, the wheel has exactly as many sectors as there are items. |
| `accent` | One hue, expanded into all six colours below. |
| `ring`, `highlight`, `border`, `highlightBorder`, `background`, `icon` | Individual colours, with the same meanings as a profile's `style`. |
| `keepOpen` | `true` leaves the wheel up after a choice. |
| `opening` | `"inline"` unfolds this menu as a ring around the entry just chosen instead of replacing the wheel — see [Opening one menu inside another](#opening-one-menu-inside-another). `"replace"` is the default. |

`slots` is a **minimum**: eight sectors with three items keeps the ring's shape, which is the point of a fixed wheel,
and eleven items stretch it to eleven rather than losing three. More than 24 items are cut, with a warning — page them
yourself with a "more" entry instead.

Colours accept `#RRGGBB`, which keeps the opacity of the value it replaces, and `0xAARRGGBB`, taken exactly as
written. `accent` is applied first and the individual colours override it. Whatever you leave out is inherited:
the profile first, then the mod config. A menu that sets no colours looks like the rest of the profile, which is
usually what you want.

`background` is only drawn when `dimBackground` is on in the mod config.

### `keepOpen`

A choice does not close the wheel, and the script keeps running while it is up:

```lua
while true do
  local pick = menu.open(homes, { title = "Homes", keepOpen = true })
  if not pick then break end
  chat.send("/home " .. pick .. " " .. player.name)
end
menu.close()
```

Reopening reuses the wheel already on screen and only swaps its contents, so a loop like this does not re-animate it.
Escape returns `nil`, which is the loop's way out.

## What a script cannot do

The mod is client-only and a script does not change that. It can send what you could type and press what you could
press; it cannot ask the server anything the server does not already tell you, and a script that reads a reply depends
on that server's exact wording.

Not available, and not by omission: file and process access (`io`, `os.execute`, `require`, `package` and `debug` are
not in the environment), reading the world or your inventory, and anything that would outlive the run — a script
starts fresh every time, with no state kept between activations.

## Failure modes worth writing for

| Situation | What happens |
|---|---|
| The server never answers | `chat.await` returns `nil` at its timeout. Check for it — a `nil` reaching `gmatch` is an error. |
| The wording differs from the pattern | The same as no answer. Report it with `notify` rather than failing silently. |
| The player cancels the menu | `menu.open` returns `nil`. Escape closes the whole wheel; folding an inline ring away cancels just the menu it was showing. |
| An inline menu has nothing to unfold from | It opens as a wheel of its own instead, and is answered the same way. |
| Parsing yields nothing | `menu.open` with an empty list returns `nil` without opening a wheel. |
| The script outlives `timeoutTicks` | Cancelled, with a line in the log, and any menu it owns is closed. |
| The entry is triggered again mid-run | The previous run is cancelled and the new one starts. |
| A Lua error | Reported to chat with the line number, logged, and the script stops. |
