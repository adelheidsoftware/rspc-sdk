-- Entry point. Wiring only: this is where the plugin loads its modules, subscribes to the client's events and tears
-- everything down in PluginShutdown. Logic that can run without the client belongs under src/core/, where tests/ can
-- run it under a standalone Lua 5.4; the files that talk to the client stay thin.
--
-- What this plugin touches, for review:
--   reads        nothing from the game
--   draws        nothing
--   stores       nothing
--   sends        nothing: no urls in the manifest, no HTTPRequest
--   permissions  none requested, none checked
--   events       none

local Constants = require("src/constants")

-- Development-only code lives in dev/, which the package leaves out, so in a release this finds no file and loads
-- nothing: the client's require returns nil for a file that does not exist.
require("dev/init")

log(formatStr("{} enabled", Constants.NAME))

-- The client requires this hook to be global. Every subscription is unsubscribed and every component destroyed here.
function PluginShutdown()
end
