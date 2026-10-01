-- RadialMenu script prelude: loaded into a script's globals, in the same environment, just before the script itself.
--
-- Everything here could have been written in Java, and is not for one reason: these functions handle Lua values the host
-- has no business holding. A pattern that only string.match should interpret. An item table carrying the script
-- author's own fields and callbacks. So the contract across the Java line stays narrow - a chat line, a list of labels,
-- an index - and keys, patterns and callbacks live on this side of it.

local host_line = chat.line
local host_open = menu.open
local host_update = menu.update

-- Taken away once captured. Not a security boundary - the script's author wrote the script - but calling the host half
-- of menu.open by hand would skip every rule below it, and the symptom would be an entry's onPick never running.
chat.line = nil
menu.open = nil
menu.update = nil

local DEFAULT_WAIT = 60

--- Waits for a chat line matching a Lua pattern, and returns its captures.
--
-- The loop is here rather than in the host because the matching is: the host hands over lines as they arrive and says
-- how long each one took, and this decides whether it was the line being waited for.
function chat.await(pattern, timeoutTicks)
  if type(pattern) ~= "string" then
    error("chat.await expects a pattern string", 2)
  end

  local remaining = timeoutTicks or DEFAULT_WAIT
  while remaining > 0 do
    local line, waited = host_line(remaining)
    if line == nil then
      return nil
    end
    remaining = remaining - (waited or 0)

    local captures = { line:match(pattern) }
    if captures[1] ~= nil then
      return table.unpack(captures)
    end
  end
  return nil
end

--- Collects every line matching a pattern for a while, as a list of capture tables.
function chat.awaitAll(pattern, windowTicks)
  if type(pattern) ~= "string" then
    error("chat.awaitAll expects a pattern string", 2)
  end

  local remaining = windowTicks or DEFAULT_WAIT
  local found = {}
  while remaining > 0 do
    local line, waited = host_line(remaining)
    if line == nil then
      break
    end
    remaining = remaining - (waited or 0)

    local captures = { line:match(pattern) }
    if captures[1] ~= nil then
      found[#found + 1] = captures
    end
  end
  return found
end

-- The list the player is looking at. A choice resolves against this rather than against whatever the script has moved
-- on to, because the player chose from what was on screen.
local shown = nil

local function normalize(items, level)
  local list = {}
  local seen = {}

  for i = 1, #items do
    local raw = items[i]
    local item
    if type(raw) == "table" then
      item = raw
    elseif type(raw) == "string" or type(raw) == "number" then
      -- A plain string is the common case: a name parsed out of a server's reply, which is its own key and its own
      -- label.
      item = { key = raw, label = tostring(raw) }
    else
      error("menu entry " .. i .. " is a " .. type(raw) .. ", expected a string or a table", level)
    end

    local key = item.key
    if key == nil then key = item.label end
    if key == nil then key = i end

    local label = item.label
    if label == nil then label = tostring(key) end

    if seen[key] ~= nil then
      log("menu has two entries keyed '" .. tostring(key) .. "'; a choice cannot say which of them it was")
    end
    seen[key] = true

    list[i] = { item = item, key = key, label = label, icon = item.icon, color = item.color }
  end

  return list
end

-- What the host is allowed to see: what to draw, and nothing else. Extra fields, functions and nested tables stay here.
local function project(list)
  local projected = {}
  for i = 1, #list do
    projected[i] = { label = list[i].label, icon = list[i].icon, color = list[i].color }
  end
  return projected
end

--- Opens a wheel and waits for a choice. Returns the chosen entry's key, and the entry itself.
--
-- An entry's onPick, if it has one, runs before this returns - so a callback that waits for chat or opens a menu of its
-- own is ordinary code - and its return value is ignored. The key is what comes back either way: what menu.open returns
-- must not depend on whether some entry happened to carry a callback.
function menu.open(items, opts)
  if type(items) ~= "table" then
    error("menu.open expects a table of entries", 2)
  end

  local list = normalize(items, 3)
  if #list == 0 then
    return nil
  end
  if #list > menu.maxEntries then
    log("menu was given " .. #list .. " entries and a wheel holds " .. menu.maxEntries
      .. "; the rest are not shown - page them with an entry of your own")
  end

  shown = list
  local index = host_open(project(list), opts)
  if index == nil then
    return nil
  end

  local chosen = shown[index]
  if chosen == nil then
    return nil
  end

  local item = chosen.item
  if type(item.onPick) == "function" then
    item.onPick(chosen.key, item)
  elseif item.onPick ~= nil then
    log("entry '" .. tostring(chosen.key) .. "' has an onPick that is a " .. type(item.onPick) .. ", not a function")
  end

  return chosen.key, item
end

--- Replaces the contents of a wheel left up by keepOpen, without waiting for a choice.
function menu.update(items)
  if type(items) ~= "table" then
    error("menu.update expects a table of entries", 2)
  end

  shown = normalize(items, 3)
  host_update(project(shown))
end
