std = "lua54"
max_line_length = 120

-- The client's globals, as its API documents them, and the library plugins a manifest can name
read_globals = {
    "AABB", "Camera", "Chat", "CoordFine", "CoordGrid", "Event", "GrandExchange", "HTTPRequest", "Inventory",
    "Keyboard", "LocList", "Matrix33", "MiniMenu", "Mouse", "NPCList", "ObjStackList", "PersistentDB", "Player",
    "PlayerList", "Plugin", "PluginPermissions", "Quaternion", "Rect", "ScreenConvert", "ShapeData", "ShapeList",
    "Vector2", "Vector3", "Vector4", "World",
    "audio", "config", "game", "id", "ui", "var",
    "AchievementDescriptionType", "AchievementHiddenType", "AchievementProgressType", "AnimationDuplicateBehaviour",
    "BoxCornerType", "ChatType", "EntityType", "GameKey", "GameState", "GrandExchangeOfferStage",
    "GrandExchangeOfferType", "HeightOffsetType", "LocShapeType", "MapElementHorizontalAlign", "MapElementTextSize",
    "MapElementVerticalAlign", "MiniMenuActionType", "MiniMenuActionVerbType", "MiniMenuEntryType", "ModifierKey",
    "ObjStackType", "PersistentDBType", "PluginResourceLoadResult", "PostAnimMove", "PreAnimMove", "QuestDifficulty",
    "QuestType", "RotationType", "ShapeEntityAlignType", "SkillType", "SystemKey", "ValueType",
    "ExecuteConsoleCommand", "clamp", "formatStr", "lerp", "log", "print", "random", "slerp",
    "prettyui",
}

-- The hooks the client requires to be global
globals = { "PluginShutdown", "PluginDependencyUpdate" }

files["tests"] = { max_line_length = false }
exclude_files = { "build", ".gradle" }
