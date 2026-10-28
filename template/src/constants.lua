-- src/constants.lua
-- Every name the plugin uses in one place: its own name, the reference names of its event subscriptions, and the IDs
-- it looks up. The version lives in plugin.json alone.

local Constants = {}

Constants.NAME = "My Plugin"

-- Reference names are "<plugin>.<purpose>", so that the console and other plugins can tell whose subscription is
-- whose: tick = "my-plugin.tick"
Constants.EVENTS = {}

return Constants
