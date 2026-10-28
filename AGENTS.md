# AGENTS.md

Guidance for AI coding agents, and for anyone else, working in the RSPC SDK or on a plugin built with it.

## This repository

The RuneScape Plugin Community SDK: `template/`, a complete plugin to copy, and the Gradle plugin the template applies, `org.adelheid.rspc`, in the modules `rspc-gradle-plugin`, `rspc-plugin-model` and `rspc-lua-runner`. The SDK is not a plugin itself, and it vendors nothing: no copy of the API definitions or of any library. It points at them and pins the versions tested together. The template is a real plugin and follows everything below; `template/AGENTS.md` is the copy of this guidance that a plugin carries, and the two change together. The SDK is written for Lua developers and assumes no other language of them. Markdown has no line limit; the 120-column rule is for code.

The template's tests run with an installed Lua 5.4 from any folder: `lua template/tests/run_all.lua`. The modules build with `./gradlew build` at the root, on a JDK 25 that Gradle downloads when none is installed.

The modules are Java, compiled for Java 17, the oldest JVM Gradle 9 runs on, and they follow their own rules: tabs, 120 columns, a Javadoc comment on every class saying what it does and intent comments where the logic is not obvious; packages marked `@NullMarked`, with NullAway, Error Prone and Checkstyle failing the build on a violation; a test for every behaviour, and the plugin's functional test for every task; and messages that say "the build" and name the `rspc { }` block, never a tool, a company or another project. Nothing in them, or anywhere else in the repository, names any other project unless the SDK depends on it.

## What a plugin is

A plugin for the RuneScape (RS3) Client Plugin API: Lua 5.4 scripts that run inside the game client, are tested on a dedicated beta world, and are distributed through Mod.io after review by Jagex. The official documentation is at <https://developer.runescape.com/rs/index.html>, and the rules a plugin is reviewed against are the [plugin guidelines](https://developer.runescape.com/rs/submission/guidelines.html). The API is in development and its pages change: when a signature or a rule matters, read the official page and test in the client rather than relying on memory.

## Rules that cannot be broken

These come from the Jagex guidelines and apply to every line.

1. **No automation, prediction or hidden information.** A plugin may display, organise, track and notify from visible or API-exposed data. It must never act for the player, recommend actions (prayers, abilities, positions), predict mechanics beyond what the game already shows, reveal hidden state, alter click zones, add server-bound menu entries, or export data about other players. Moderation judges what a plugin does, not which API it calls: a feature is not allowed just because the API permits it.
2. **A person reviews every line.** Jagex accepts AI-assisted code only when the developer has personally read and understood it, and rejects code that is unvetted or hard to follow. Treat everything you write as a draft for the developer to read line by line, and write for the moderator who reads it next: a header comment per file saying what it does and what it touches, intent comments where the logic is not obvious, and no cleverness.
3. **Permissions are minimal and justified.** Request `httpAccess`, `chatAccess` or `trackingAccess` only when a feature needs them, prefer `optionalPermissions` with `Plugin.HasPermission` checks, and keep the data-protection story Mod.io asks for in mind from the first design. Never add a permission on your own.
4. **Development-only code never ships.** Cheats, `ExecuteConsoleCommand`, probes and debugging helpers live under `dev/`, which the package leaves out. Nothing under `src/` or in `main.lua` may require a file from `dev/`, except the one guarded line in `main.lua` that loads `dev/init`.
5. **Cross-platform.** A plugin must work on Windows, Linux, Android and iOS. Guard `Mouse`, `Keyboard`, `Player`, `World` and `Camera` with `IsAvailable()`, and never depend on the console at runtime.
6. **Other people's code stays theirs.** Do not copy code from other plugins. A library plugin is a dependency declared in the manifest by its Mod.io ID. A Lua library the plugin vendors sits at the root as its documentation lays it out, with its licence under `licences/<name>/`, and the build knows it from `libraries { vendored("<name>") { ... } }`: with a repository at a commit or a checkout named, `vendorLibraries` copies it in; without one, it was copied in by hand. A licence that forbids distribution or changes rules a library out.

## Layout of a plugin

| Path | What it is |
| --- | --- |
| `main.lua` | The entry point: wiring only, with a header that says what the plugin reads, draws, stores and sends, which permissions it uses and which events it subscribes to |
| `plugin.json` | The manifest. The dependency fields are objects, `{}` when empty; the version is digits and dots; the contact address is required and is not the developer's Mod.io or RuneScape login |
| `src/constants.lua` | Every name in one place: the plugin's name, the reference names of its events, the IDs it uses |
| `src/settings.lua` | Where the settings go when the plugin has any: their defaults, and loading and saving them through the typed PersistentDB accessors |
| `src/core/` | Where logic with no client in it goes: plain Lua functions and tables that the tests run |
| `src/`, elsewhere | The adapters: files that read the game, draw, or build UI, kept thin |
| `resources/` | PNG sprites and OGG sounds, read as `Plugin.resources.<name>`; images are at most 1024 by 1024 |
| `dev/` | Development-only code, never shipped |
| `tests/` | Standalone Lua 5.4 tests: `run_all.lua`, `harness.lua`, and one `*_test.lua` per core module |
| `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `gradle/` | The build, where its plugin comes from, and its wrapper; `build/` is its output |

What ships: `main.lua`, `plugin.json`, `src/` and `resources/`, and nothing else.

## Engineering principles

- **Pure cores, thin glue.** Decisions live in plain Lua functions under `src/core/` that know nothing about the client; event callbacks and UI are adapters. Pure modules are testable with a standalone Lua and stub tables.
- **Single source of truth.** IDs, event reference names and the settings schema each live in one module.
- **Abstract on the second use, not the first.** Design for the next feature, not every feature.
- **Comments document the present.** No changelog comments; names carry the what, comments carry the why.
- **Delete dead code.**

## Code style

- Lua 5.4. Four-space indent, 120-column soft limit, no semicolons.
- Every Lua file under `src/` starts with a comment line of its own path, `-- src/core/session.lua`, because the client names a file in its errors by its first line; the header comment follows it, and the build's check requires the line.
- `local` everything. The only globals are `PluginShutdown` and `PluginDependencyUpdate`, each with a comment saying the client requires them global. A `PluginShutdown` declared `local` never runs.
- `camelCase` locals and functions, `PascalCase` modules and constructors, `UPPER_SNAKE_CASE` constants. Event reference names and hook IDs are `"<plugin>.<purpose>"`.
- Bind `require` results and `config.*` aliases at the top of the file; do not reach into globals mid-function.
- `ipairs` for API arrays, `pairs` for maps. Check `nil` and `isValid` before indexing, and re-check `isValid` on an entity held across ticks.
- Typed `PersistentDB` accessors, reading with the same type as writing. `formatStr` and `tostring` for strings. `pcall` only around genuinely risky dynamic work, never as a habit.
- Every `Subscribe` has its `Unsubscribe` in `PluginShutdown`, every created component is destroyed there, and every changed game option is restored there.

## Workflow

1. Edit with the Lua Language Server active: `.luarc.json` loads the API definitions, and its diagnostics are fixed before a reload.
2. In the client console (`Alt` + `` ` ``): `prf` after adding files or changing the manifest, then `prl <name>` after every Lua change. Read the errors there.
3. Find IDs offline first, in the API definitions' constants where the build's source has them (`build/rspc/stubs/constants/<Type>.csv` after `fetchStubs`, from rs3-api-types), then confirm in the client with `pan NPC`, `pan Obj`, `pan Loc` or `pan VarPlayer`.
4. An agent cannot run the game client. Before claiming a change works, say what ran in the client and what the console showed; when nothing could run, say so and do not infer success. To verify behaviour, write the smallest `log()` instrumentation or test plugin for the developer to run, and ask for the console output.
5. Before a submission: `./gradlew check`, then `./gradlew packagePlugin`, then the guidelines, read in full.

## Commands in a plugin

| Command | What it does |
| --- | --- |
| `./gradlew fetchStubs linkDevPlugin` | Fetches the API definitions for the editor and links the plugin into the client's development folder |
| `./gradlew testLua` | Runs the standalone tests; `lua tests/run_all.lua` does the same with an installed Lua 5.4 |
| `./gradlew check` | The tests, and the checks of the manifest and the shipped files |
| `./gradlew packagePlugin` | Writes the zip for Mod.io, after `check` |

## API quick reference

| Task | Call |
| --- | --- |
| Console output | `log(...)`, prefixed with the plugin's name; `print(...)` |
| Periodic work | `Event.ServerLogic.Subscribe(name, fn)` (about 600 ms), `Event.Logic` (50 Hz), `Event.Draw` (per frame) |
| Clean up | global `function PluginShutdown()` |
| Settings | `PersistentDB:GetBool/GetInt/GetFloat/GetString/GetStructuredData(key)` and `Set*` |
| Permission check | `Plugin.HasPermission(PluginPermissions.httpAccess)` |
| Local player | `Player.IsAvailable()`, `Player.GetEntity()`, `Player.GetLevelGameplay(SkillType.X)` |
| Game variables | `var.VarPlayer.NAME.value`, `var.VarClient.NAME.value`, `Event.VarTransmit` |
| Static data | `config.NPC.NAME`, `config.Obj.NAME`, `config.Loc.NAME`, `id.Obj.NAME`, `config.X.FromID(n)` |
| Nearby entities | `NPCList.Filter({ config = ..., coordGrid = ..., range = n })`, `LocList`, `ObjStackList`, `PlayerList`; check `isValid` |
| Inventory | `Inventory.Find(config.Inv.INV)`, `inv:GetTotal(objType)`, `Event.InventoryUpdated` |
| Coordinates | `CoordGrid._0_50_50_32_32`, `coord:ToCoordFine(true).position`, `World.GetGroundHeight(level, x, z)` |
| Screen position | `ScreenConvert.Vector3ToScreen(v3)`, `ScreenConvert.ScreenToCoordGrid(Mouse.GetPosition())` |
| 3D markers | `ShapeData.new()` then `ShapeList.CreateEntity(name)` (fixed), `CreateInstance(name)` (moving), or `ShapeList.Draw{...}` in `Event.Draw` (one frame) |
| UI root | `ui.Interfaces:GetComponent(id.Component.TOPLEVEL_V2__PLUGIN_BUILD_LAYER_TOP)`, or the layers from `Event.SettingsLayerReady` and `ContentLayerReady` |
| UI components | `ui.GroupBox.new(parent)`, `ui.Text.new`, `ui.Button.new`, `ui.Check.new`; `c:SetPos`, `c:SetSize`, `c.stylesheetID = id.StyleSheet.BUTTON_DEFAULT`, `c:Subscribe(ui.Hook.ONBUTTONPRESSED, fn)` |
| Right-click menu | `Event.MiniMenuEntrySelected` (read), `Event.MiniMenuReady` with `miniMenu:Swap` or `miniMenu:Add` |
| Chat (needs chatAccess) | `Chat.AddMessage(text)`, `Event.ChatMessage` |
| HTTP (needs httpAccess) | `HTTPRequest.new(Plugin.urls.alias, path):Get(onDone)` |
| Audio | `audio.Play(Plugin.resources.name, params)` |

## Pitfalls

- `"requiredDependencies": []` in the manifest: the client wants an object, `{}`, and rejects the whole file otherwise.
- Forgetting `prl` after an edit, or `prf` after a manifest change.
- Waiting for a permission prompt: the development client has none. Put permissions in `optionalPermissions` and grant them in the console.
- Indexing an optional payload table, such as `entry.npcData`, without a nil check.
- Holding an entity across ticks without re-checking `isValid`; storing client objects in `PersistentDB`.
- A `Text` without `font`, a `GroupBox` without `stylesheetID`, or a component without a size: all invisible. An `InputField` without `stylesheetID` reads its content back as empty.
- Querying `NPCList.All()` or building strings inside `Event.Draw`: compute on `ServerLogic`, draw from the result.
- Mixing an untyped `PersistentDB:Set` with a typed `Get`: an int and float mismatch returns nil.
- Changing `game.options` without restoring them on shutdown.
- Keyboard queries returning `false` because a hidden-text `InputField` has focus.
- Assuming a feature is allowed because the API permits it; moderation judges the effect.
