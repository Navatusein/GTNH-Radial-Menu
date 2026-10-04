# Scripting RadialMenu

A **script** is a slot that writes its own menu while the game is running.

Every other action is data the mod interprets: press this binding, send these lines, open this submenu. That covers
what a slot usually wants to do. It does not cover the menu whose entries nobody knows until the server answers — your
homes, the warps, the players online, what is in your bag right now. That is what a script is for.

The language is **Lua 5.2** ([LuaJ](https://github.com/luaj/luaj) 3.0). Three things are worth knowing before the first
line:

- A script **asks**, the game answers. It never touches the world directly, so nothing it does can corrupt a save or
  reach another player.
- A script **waits without freezing anything**. Waiting for a chat reply or for the player to choose suspends the
  script; the game carries on and the script picks up where it left off.
- A script can do **only what you could have done by hand** — type in chat, press a key, open a wheel.

---

## Contents

- [Your first script](#your-first-script)
- [Asking the server something](#asking-the-server-something)
- [Putting a wheel up](#putting-a-wheel-up)
- [Menus with branches](#menus-with-branches)
- [Looking around](#looking-around)
- [Remembering, and asking](#remembering-and-asking)
- [Running the mod's own actions](#running-the-mods-own-actions)
- [Recipes](#recipes)
- [Reference](#reference)
- [How a script runs](#how-a-script-runs)
- [Where a script lives](#where-a-script-lives)

---

## Your first script

Hold the wheel key, hold <kbd>Shift</kbd>, click an empty sector. In the slot editor pick the **Script** tab and press
**Edit script**. Type:

```lua
notify("Hello from " .. player.name)
```

Press **Done**, **Save**, and choose the slot. The line appears in your chat, visible to nobody else.

![The script editor](/docs/assets/script-editor.png)

The editor colours the API as you type, numbers the lines, and lists every call on the right — clicking one writes it
in. If a script fails, the message says which line, and the editor marks it the next time you open it.

For anything longer than a screen, edit the profile JSON in a real editor and run `/radialmenu reload`. The script is
a `"script"` array of lines inside the slot — see [Where a script lives](#where-a-script-lives).

---

## Asking the server something

Two calls, and they are the backbone of most scripts: `chat.send` types a line for you, `chat.await` waits for the
answer and pulls the interesting part out of it with a [Lua pattern](https://www.lua.org/manual/5.2/manual.html#6.4.1).

```lua
chat.send("/home list " .. player.name)

local list = chat.await("^" .. player.name .. ": %d+ / %d+: (.+)$", 60)

if not list then
  notify("no answer from /home list")
  return
end

notify("homes: " .. list)
```

`chat.await` returns the pattern's **captures** — the bits in `( )` — or `nil` if nothing matched before the timeout
ran out. The timeout is in ticks; 20 ticks is a second, and 60 is the default.

Always check for `nil`. A server that is slow, down, or phrases its reply differently is the normal case, not the
exception, and a `nil` reaching `gmatch` is an error the player gets to read.

`chat.awaitAll(pattern, windowTicks)` is the other half: it collects **every** line that matches for a while, which is
how a list spread over several lines is read.

---

## Putting a wheel up

`menu.open` takes a list and suspends until the player picks something. It gives back the **key** of what they chose,
or `nil` if they dismissed it.

```lua
local pick = menu.open({
  {
    key = "day",
    label = "Day",
    icon = "sprite:phosphor:sun"
  },
  {
    key = "night",
    label = "Night",
    icon = "minecraft:torch"
  }
}, { title = "Time" })

if pick then
  chat.send("/time set " .. pick)
end
```

![A wheel a script built](/docs/assets/wheel-script.png)

A plain string is shorthand for an entry that is its own key and its own label, so a list parsed out of chat can go
straight in:

```lua
local pick = menu.open({ "base", "mine", "farm" }, { title = "Homes" })
```

The wheel a script opens says **SCRIPT** in the header. It cannot be edited — it is built for one choice and thrown
away.

### Doing something different per entry

An entry can carry an `onPick` function. It runs **before** `menu.open` returns, and it may itself wait for chat or
open another menu — it is ordinary code in the middle of your script.

```lua
menu.open({
  {
    key = "home",
    label = "Home",
    icon = "minecraft:bed",
    onPick = function()
      chat.send("/home")
    end
  },
  {
    key = "spawn",
    label = "Spawn",
    icon = "sprite:phosphor:house",
    onPick = function()
      chat.send("/spawn")
    end
  }
})
```

Mixing the two styles is fine. Use `onPick` when each entry does something different, and the returned key when they
differ only in a parameter.

---

## Menus with branches

An entry carrying an `items` list of its own is a **submenu**, drawn and navigated exactly like one configured by
hand. `opening = "inline"` unfolds it as a ring around its entry; the default, `"replace"`, drills into it.

```lua
local pick = menu.open({
  {
    key = "spawn",
    label = "Spawn",
    icon = "sprite:phosphor:house"
  },
  {
    label = "Mines",
    icon = "minecraft:iron_pickaxe",
    opening = "inline",
    items = {
      {
        key = "deep",
        label = "Deep",
        icon = "minecraft:diamond"
      },
      {
        key = "quarry",
        label = "Quarry",
        icon = "minecraft:iron_ore"
      }
    }
  }
}, { title = "Warps" })
```

![A script's inline submenu](/docs/assets/wheel-script-inline.png)

`pick` is whatever the player ended on, at whatever depth — `"deep"` is as ordinary an answer as `"spawn"`, and its
`onPick` runs the same way. A submenu entry is never itself the answer: its own key is never returned and its own
`onPick` never runs, because choosing it is how its entries are reached.

Besides `items`, such an entry takes `opening`, `slots` and the colour options (`accent`, `ring`, `highlight`,
`border`, `highlightBorder`, `background`) — the same options a wheel takes, because that is what it is.

### A branch the script does not know in advance

A list nested like that has to exist before the wheel opens, because the player reaches it by pointing rather than by
choosing. When the contents are not known until then, open a **second menu inline**:

```lua
menu.open({
  {
    key = "warps",
    label = "Warps",
    icon = "minecraft:compass",
    onPick = function()
      chat.send("/warp list")

      local line = chat.await("^Warps: (.+)$")

      if not line then
        return
      end

      local warps = {}

      for name in line:gmatch("[^,%s]+") do
        warps[#warps + 1] = name
      end

      local pick = menu.open(warps, { opening = "inline" })

      if pick then
        chat.send("/warp " .. pick)
      end
    end
  }
}, { keepOpen = true })
```

The second wheel unfolds around the entry that was just chosen instead of replacing it. That needs the first wheel to
still be on screen, which is what `keepOpen = true` is doing there. Where it cannot unfold — no wheel left, or
`scrollToSelect` turned on, which has one ring and no way to cross into another — the menu simply opens as a wheel of
its own.

Folding the ring away (a right-click on it) is a cancellation like any other: `menu.open` returns `nil`.

---

## Looking around

Everything the game can tell a script is a field, read at the moment you touch it — so a script that lives across a
teleport sees the new position, not the one it started with.

```lua
notify(("%s at %d %d %d, facing %s"):format(player.name, player.x, player.y, player.z, player.facing))

if world.isDay then
  notify("day " .. world.day)
end

local target = world.lookingAt()

if target then
  notify("looking at " .. target.label)
end

if inventory.has("minecraft:torch") then
  notify("torches: " .. inventory.count("minecraft:torch"))
end
```

`inventory.items()` gives the whole bag as a list, and each stack already carries an `icon` spelled the way an entry
wants it — so a wheel of what you are carrying is six lines:

```lua
local entries = {}

for _, stack in ipairs(inventory.items()) do
  entries[#entries + 1] = {
    key = stack.id,
    label = stack.label .. " x" .. stack.count,
    icon = stack.icon
  }
end

menu.open(entries, { title = "Carrying" })
```

The `chunk` table does the coordinate arithmetic the game means — block `-200` is in chunk `-13`, eight blocks along —
for any coordinate, not only the player's.

---

## Remembering, and asking

`store` is a small file a script can keep things in between runs, shared by every script. Strings both ways; prefix
your keys so unrelated scripts do not collide.

`prompt` asks the player to type one line.

```lua
local last = store.get("home.last")

local name = prompt("Name this home", last or player.name)

if not name then
  notify("cancelled")
  return
end

chat.send("/sethome " .. name)
store.set("home.last", name)
```

Both return `nil` when the player changes their mind, the same nothing a dismissed menu gives — so one test covers it.

---

## Running the mod's own actions

`action.run` performs any of the mod's action types by data, which is how a script reaches a keybinding:

```lua
action.run{
  type = "keybind",
  params = {
    binding = "key.inventory",
    mode = "tap"
  }
}

action.run{
  type = "profileSwitch",
  params = {
    profile = "mining"
  }
}
```

The parameter names are the ones in [PROFILE_FORMAT.md](PROFILE_FORMAT.md); numbers and booleans are converted for you
here, though they are strings everywhere else in the file format. A script may not run `script` or `scriptResume` —
one would let a script re-enter itself without bound, and the other is an answer rather than an action.

---

## Recipes

Complete scripts, ready to paste.

### Homes, from a server that lists them

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

### A wheel of who is online

```lua
chat.send("/list")

local line = chat.await("players online: (.+)$", 60)

if not line then
  notify("no answer from /list")
  return
end

local players = {}

for name in line:gmatch("[^,%s]+") do
  players[#players + 1] = {
    key = name,
    label = name,
    icon = "player:" .. name
  }
end

local pick = menu.open(players, { title = "Online" })

if pick then
  chat.send("/msg " .. pick .. " hello")
end
```

### Drop what you are holding, a stack at a time

```lua
local held = player.held

if not held then
  notify("nothing in hand")
  return
end

local answer = menu.open({
  {
    key = "one",
    label = "Drop one",
    icon = held.icon
  },
  {
    key = "stack",
    label = "Drop the stack",
    icon = held.icon
  }
}, { title = held.label })

if answer == "one" then
  action.run{ type = "keybind", params = { binding = "key.drop", mode = "tap" } }
elseif answer == "stack" then
  action.run{ type = "keybind", params = { binding = "key.drop", mode = "tap", ctrl = true } }
end
```

### A wheel that stays up

```lua
local count = 0

while true do
  count = count + 1

  local pick = menu.open({ "stone", "planks", "glass" }, {
    title = "Give " .. count,
    keepOpen = true
  })

  if not pick then
    break
  end

  chat.send("/give @p minecraft:" .. pick .. " 64")
end

menu.close()
```

With `keepOpen` the wheel stays after a choice, so the loop asks again without reopening anything. `menu.update(items)`
replaces its contents in place; `menu.close()` takes it down.

---

## Reference

### Where you are

| | |
|---|---|
| `player.name` | The player's name. |
| `player.dim` | Dimension id. |
| `player.x`, `player.y`, `player.z` | Block coordinates, floored. |
| `player.chunkX`, `player.chunkZ` | The chunk those coordinates fall in. Negative blocks work the way the game means them: block `-200` is chunk `-13`. |
| `player.xInChunk`, `player.zInChunk` | Where in that chunk, `0`–`15`. |
| `player.facing` | `"north"`, `"south"`, `"east"` or `"west"` — whichever quarter of the compass the player is looking into. |
| `player.yaw` | `0`–`360`. Minecraft's convention, so **`0` is south**, `90` west, `180` north, `270` east. Normalised. |
| `player.pitch` | `-90` (straight up) to `90` (straight down), `0` level. |
| `player.health` | Hearts as the game counts them: `20` is full. |
| `player.food` | Hunger, `0`–`20`. |
| `player.air` | Breath under water, in ticks. `300` when full. |
| `player.held` | What is in your hand as an [item table](#an-item), or `nil`. |

### The world

| | |
|---|---|
| `world.time` | Ticks since this morning, `0`–`23999`. Dawn is `0`, dusk `12000`. |
| `world.day` | How many days the world has seen, the first being `0`. |
| `world.isDay` | `true` while the sun is up. |
| `world.name` | The server's address, or the save's folder in single player. The same string a profile's auto-bind rules match on. |
| `world.lookingAt()` | What the crosshair is on, or `nil` for thin air. |

What `lookingAt` gives back: `kind` (`"block"` or `"entity"`), `id`, `label`, `meta`, `x`, `y`, `z`. Its reach is the
player's own, so a block out of range is thin air.

### Chunks

| Call | Gives |
|---|---|
| `chunk.of(-200)` | `-13` — the chunk that block is in. |
| `chunk.offset(-200)` | `8` — how far into it. |
| `chunk.toWorld(-13, 8)` | `-200` — the two put back together. |
| `chunk.toWorld(-13)` | `-208` — the chunk's own corner. |
| `chunk.size` | `16`. |

These are the only calls that **do not suspend** — they ask the game nothing, so they may be used in a loop without
spending a tick a turn.

### The inventory

| | |
|---|---|
| `inventory.count(id)` | How many you are carrying, every damage value together. |
| `inventory.count(id, meta)` | Only that variant. |
| `inventory.has(id)` / `inventory.has(id, meta)` | The same question as a boolean. |
| `inventory.items()` | A list of [item tables](#an-item), empty slots left out. |

#### An item

| | |
|---|---|
| `id`, `meta`, `count` | Registry name, damage value, stack size. |
| `label` | The name the game prints. |
| `slot` | Where it sits, counted from 1. The hotbar is 1–9. `0` for a held item. |
| `icon` | The same stack spelled as an icon, so a listing can be handed to `menu.open` unchanged. |

The main inventory and the hotbar, in slot order. Not armour, not another container's contents.

### Chat

| | |
|---|---|
| `chat.send(text)` | Sends one line exactly as typing it would. A leading `/` makes it a command; `{player}` `{dim}` `{x}` `{y}` `{z}` still work. |
| `chat.await(pattern, timeoutTicks)` | Suspends until a line matches, then returns its captures. `nil` on timeout. Default `60` ticks. |
| `chat.awaitAll(pattern, windowTicks)` | Collects every line that matches for a while, as a list of capture tables. |

Patterns are Lua patterns, not regular expressions: `%d` is a digit, `.-` is a lazy run, `-` is a literal dash only
when escaped as `%-`. Anchor them with `^` so a line that merely contains the text does not match.

Lines are kept from the moment the script started, so a reply that lands before the script gets round to asking is
still there.

### Menus

| | |
|---|---|
| `menu.open(items, opts)` → `key, item` | Opens a wheel and waits. Returns the chosen entry's key and the entry itself, or `nil` if dismissed. An empty list returns `nil` without opening anything. |
| `menu.update(items)` | Replaces the contents of a wheel left up by `keepOpen`. |
| `menu.close()` | Takes such a wheel down. |
| `menu.maxEntries` | How many entries one wheel holds — 24. Longer lists are cut, with a line in the log. |

#### An entry

| | |
|---|---|
| `key` | What `menu.open` returns. Defaults to `label`; with neither, the entry's position. |
| `label` | Shown in the middle of the wheel while the sector is hovered. Defaults to `key`. |
| `icon` | A string or a table — see below. Omit for no icon. |
| `color` | `#RRGGBB` tint, for `sprite` and `file` icons only. |
| `onPick` | Optional function, called with `(key, item)` when this entry is chosen. |
| `items` | A list of its own, which makes this entry a submenu. |

Anything else you put in the table is yours and comes back untouched as `menu.open`'s second return value — a command
to send, a coordinate, a callback.

Duplicate keys are reported in the log: a key you cannot resolve back to one entry is a silent bug waiting to happen.

#### Icons

| Form | Kind |
|---|---|
| `minecraft:stone` | Item. No prefix means an item. |
| `minecraft:stained_hardened_clay:5` | Item with metadata. The trailing number is the damage value, read from the end. |
| `sprite:phosphor:sword` | One of the bundled monochrome icons. Names are in `assets/radialmenu/icons/phosphor.json` inside the jar. |
| `file:backpack.png` | A PNG from `RadialMenu/icons/`. |
| `effect:potion.moveSpeed` | A status effect, by the potion's unlocalized name. |
| `player:Nortcast` | A player's face, off the skin the game is already drawing them with. |

The table form spells the same thing out, for a name built at run time — `kind` is `"item"`, `"sprite"`, `"file"`,
`"effect"` or `"player"`, and an unknown one is read as an item:
`icon = { kind = "sprite", id = "phosphor:house", color = "#7FD4FF" }`.

An icon that does not resolve draws nothing; it is not an error.

#### Menu options

```lua
menu.open(items, {
  title = "Homes",
  slots = 8,
  accent = "#4A90D9",
  keepOpen = true
})
```

| Option | Meaning |
|---|---|
| `title` | Shown in the header, after the profile name. |
| `slots` | Sector count, **2–24**. A **minimum**, not a cap: eight sectors with three entries keeps the ring's shape, and eleven entries stretch it to eleven. Omitted, the wheel has exactly as many sectors as there are entries. |
| `accent` | One hue, expanded into all the colours below. |
| `ring`, `highlight`, `border`, `highlightBorder`, `background`, `icon` | Individual colours, same meanings as a profile's `style`. Applied over `accent`. |
| `keepOpen` | `true` leaves the wheel up after a choice. |
| `opening` | `"inline"` unfolds this menu as a ring around the entry just chosen. `"replace"` is the default. |

Colours accept `#RRGGBB`, which keeps the opacity of what it replaces, and `0xAARRGGBB`, taken exactly as written.
Whatever you leave out is inherited: the profile first, then the mod config — so a menu that sets no colours looks
like the rest of the profile, which is usually what you want.

### Everything else

| | |
|---|---|
| `notify(text)` | A line in your own chat, visible to nobody else. The right way to report that a parse failed. |
| `log(...)` | A line in the client log, for what the player should not have to read. `print` is the same function. |
| `sleep(ticks)` | Suspends for that many ticks. 20 is a second. |
| `prompt(title [, initial])` | Asks for one line of text. `nil` if cancelled. Never returns a blank string. |
| `store.get(key [, default])` | What was remembered, or the default, or `nil`. |
| `store.set(key, value)` | Remembers it in `RadialMenu/script-store.json`. `nil` forgets the key. Strings only, both ways — a script that wants a number writes one and reads it back with `tonumber`. |
| `action.run(spec)` | Runs one of the mod's action types by data. |

`string`, `table` and `math` are the standard libraries, complete.

---

## How a script runs

- A script starts on the client tick after the wheel closes, like every other action.
- It runs on the client tick and **never blocks it**. Anything that waits suspends the script and the game carries on.
- One copy runs per entry. Choosing the same slot again cancels the run in progress and starts over.
- Leaving the world, disconnecting, or `timeoutTicks` expiring cancels it, and closes any menu it owns.
- An error stops the script, prints the message and the line to chat, and logs it. It never takes the client down.
- Every resume has an instruction and time budget: a script that loops without ever waiting is killed rather than
  freezing the game, so `while true do end` costs you the script, not the session.

### Worth writing for

| Situation | What happens |
|---|---|
| The server never answers | `chat.await` returns `nil` at its timeout. Check for it. |
| The wording differs from the pattern | The same as no answer. Report it with `notify` rather than failing silently. |
| The player cancels a menu | `menu.open` returns `nil`. Escape closes the whole wheel; folding an inline ring away cancels just that menu. |
| Parsing yields nothing | `menu.open` with an empty list returns `nil` without opening a wheel. |
| Another screen is open | A menu or prompt is not forced over it; the call answers `nil` instead. |
| The script outlives `timeoutTicks` | Cancelled, with a line in the log. |
| The entry is triggered again mid-run | The previous run is cancelled and the new one starts. |
| A Lua error | Reported to chat with the line number, logged, and the script stops. |

### What a script cannot do

- No files, no sockets, no `require`, no Java classes. The sandbox has the standard libraries and this API, and
  nothing else.
- No reaching into the world: no placing or breaking blocks, no moving items between slots, no walking the player
  around. Anything that changes the world goes through a command or a keybinding, exactly as it would from your
  keyboard.
- No running another script, and no answering a menu on the player's behalf.

Scripts are not a protocol bypass: everything that leaves the client is a chat line or a key press the server would
have seen anyway.

---

## Where a script lives

In the profile, like any other action — `script` is one more type id:

```json
{
  "title": "Homes",
  "icon": { "kind": "item", "id": "minecraft:bed", "meta": 0 },
  "action": {
    "type": "script",
    "params": {
      "script": [
        "local playerName = player.name",
        "",
        "chat.send(\"/home list \" .. playerName)"
      ],
      "timeoutTicks": "200"
    }
  }
}
```

| Param | Required | Meaning |
|---|---|---|
| `script` | **yes** | The source. A JSON array of lines, or one string with `\n` breaks — the reader takes both, and an array is far easier to edit by hand. |
| `timeoutTicks` | no | How long the whole script may stay alive, waiting included. Default `"600"` — 30 seconds. `"0"` means no limit, which is worth avoiding. |

> **A profile carries its scripts.** Copying someone's profile file runs their code the next time that slot is chosen,
> and a script can send anything to the server under your name. That is the file owner's business, the same as any
> macro file — but it is worth knowing before importing a stranger's profile. `enableScripts` in the mod config turns
> the action type off entirely.
