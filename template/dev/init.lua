-- Development-only code: cheats, probes and anything else that must never ship. The package leaves the dev folder
-- out, so main.lua's require of this file finds nothing in a release. Cheats work on the beta world's development
-- server only.

local Dev = {}

-- Runs a console cheat, such as "!tele 3200 3200", where the client offers the command.
function Dev.cheat(command)
    if type(ExecuteConsoleCommand) ~= "function" then
        log("cheats are not available on this client")
        return
    end
    ExecuteConsoleCommand(command)
end

log("development build: dev/init.lua loaded")

return Dev
