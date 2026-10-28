# My Plugin

One paragraph on what the plugin shows the player, and from what. The template's plugin does nothing yet: it logs that it is enabled, and it shuts down cleanly.

## For review

What this plugin touches, in the words of the header comment of `main.lua`:

- **Reads**: nothing from the game.
- **Draws**: nothing.
- **Stores**: nothing.
- **Sends**: nothing; no URLs in the manifest, no HTTP requests.
- **Permissions**: none requested.
- **Events**: none.

## Developing

You need the beta client, an editor with the Lua Language Server, and a JDK 17 or newer for the build. The build's wrapper fetches Gradle itself.

1. Fetch the API definitions the editor reads, and link the plugin into the client:

   ```text
   ./gradlew fetchStubs linkDevPlugin
   ```

2. In the client console (`Alt` + `` ` ``): `prf`, then `pon my-plugin`, where `my-plugin` is this folder's name.
3. Edit, then `prl my-plugin` in the console, and read what it says. The build is not part of this loop.

Without the build, the same works by hand: clone [rs3-api-types](https://github.com/adelheidsoftware/rs3-api-types) and point `.luarc.json` at its `generated/library/` folder, link the plugin with `mklink /J "C:\ProgramData\Jagex\RuneScape-BETA\dev_plugins\my-plugin" "<this folder>"` in a Command Prompt, and run the tests with an installed Lua 5.4, `lua tests/run_all.lua`.

## Layout

| Path | What it is |
| --- | --- |
| `main.lua` | The entry point: wiring only, with a header that says what the plugin touches |
| `plugin.json` | The manifest: name, version, contact address, dependencies and permissions |
| `AGENTS.md` | Guidance for AI coding agents and people working on the plugin: the rules, layout, style and workflow |
| `src/` | Your code. `src/constants.lua` keeps every name in one place; logic that never touches the client goes under `src/core/`, where the tests run it |
| `resources/` | PNG sprites and OGG sounds, read as `Plugin.resources.<name>`; make the folder when the plugin needs one |
| `dev/` | Development-only code, never shipped: cheats, probes, debugging helpers |
| `tests/` | Standalone Lua 5.4 tests: the runner and the harness; add one `*_test.lua` per core module |
| `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `gradle/` | The build, where its plugin comes from, and its wrapper; `build/` is its output and is not committed |

## Checking and releasing

```text
./gradlew check            runs the tests and checks the manifest and the shipped files
./gradlew packagePlugin    writes build/distributions/my-plugin-<version>.zip for Mod.io
```

Before the first submission, read Jagex's [plugin guidelines](https://developer.runescape.com/rs/submission/guidelines.html): what is approved is decided by what the plugin does, not by which API it calls.
