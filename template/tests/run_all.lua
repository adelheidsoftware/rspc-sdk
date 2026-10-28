-- Runs every *_test.lua in this folder against the plugin under a standalone Lua 5.4 interpreter.
-- Usage, from anywhere:   lua tests/run_all.lua [name ...]
-- The tests require the plugin's modules by the plugin's own paths ("src/core/..."), so package.path gets the
-- plugin root as well as this folder.

local here = arg[0]:match("^(.*)[/\\]run_all%.lua$") or "."
local plugin = here .. "/.."
package.path = plugin .. "/?.lua;" .. here .. "/?.lua;" .. package.path

local Harness = require("harness")
Harness.PLUGIN_ROOT = plugin

-- Every *_test.lua in this folder, by module name, in alphabetical order. Standard Lua cannot list a directory, so
-- this asks the shell: dir on Windows, ls elsewhere.
local function findTests()
    local windows = package.config:sub(1, 1) == "\\"
    local command = windows and ('dir /b "' .. here:gsub("/", "\\") .. '"') or ('ls -1 "' .. here .. '"')
    local listing = assert(io.popen(command), "cannot list " .. here)
    local names = {}
    for file in listing:lines() do
        local name = file:match("^(.+_test)%.lua$")
        if name ~= nil then
            names[#names + 1] = name
        end
    end
    listing:close()
    table.sort(names)
    return names
end

local TESTS = findTests()
if #TESTS == 0 then
    print("no *_test.lua files in " .. here .. "; add one for each module under src/core/")
    os.exit(0)
end

local selected = {}
for i = 1, #arg do
    selected[arg[i]] = true
end
for name in pairs(selected) do
    local found = false
    for _, test in ipairs(TESTS) do
        found = found or test == name
    end
    assert(found, "no test named " .. name)
end

local clock = os.clock
for _, name in ipairs(TESTS) do
    if next(selected) == nil or selected[name] then
        local started = clock()
        local failedBefore = Harness.failed
        require(name)
        print(string.format("%-20s %s  (%.2f s)", name, Harness.failed == failedBefore and "ok" or "FAILED",
            clock() - started))
    end
end

local ok = Harness.summary()
os.exit(ok and 0 or 1)
