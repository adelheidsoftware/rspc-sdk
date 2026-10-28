# RSPC SDK

The RuneScape Plugin Community SDK: a starting point and a toolchain for writing plugins for the RuneScape 3 client in Lua. It is a community project and is not affiliated with Jagex.

An RS3 plugin is a folder of Lua 5.4 files with a `plugin.json` manifest. You test it on the beta world and publish it through [Mod.io](https://mod.io/g/runescape), where Jagex reviews it. The SDK gives you a template to start from, API type stubs, a test harness, a dev source set, and a build task that can link your plugin into the client, check it against the submission rule, and zip it for final submission.

- [What you get](#what-you-get)
- [Requirements](#requirements)
- [Start a plugin](#start-a-plugin)
- [Day to day](#day-to-day)
- [Inside a plugin](#inside-a-plugin)
- [Development-only code](#development-only-code)
- [Using libraries](#using-libraries)
- [The editor](#the-editor)
- [Checking and releasing](#checking-and-releasing)
- [Support](#support)

## What you get

- **A template plugin** that you copy and rename. It is fully standalone: it needs nothing else from this repository.
- **A Gradle build** with the tasks a plugin needs: fetch API definitions, link the plugin into the client, copy in the Lua libraries your plugin uses, run tests, check what ships, zip it for Mod.io.
- **Editor support** through the Lua Language Server, which reads the API's types from what the build fetched.
- **`AGENTS.md`**, a guide for AI coding agents and people alike: the rules a plugin is reviewed against, its layout, style and workflow. Every plugin made from the template carries a copy.

## Requirements

- The RuneScape beta client and access to the beta world. Jagex's [getting started page](https://developer.runescape.com/rs/getting-started.html) explains how to get both.
- An editor with the [Lua Language Server](https://luals.github.io/): VS Code with the Lua extension by sumneko, or any editor that reads `.luarc.json`.
- A JDK, version 17 or newer, for the build. Gradle itself comes with the template. You never write Java; the build file is a few lines of declaration.

Installing Lua is not required, but is recommended. The build runs your tests on a bundled Lua 5.4.8, which is the version the RuneScape client embeds.

## Start a plugin

You do not develop inside this repository. Copy the template out of it:

1. Copy the `template` folder to wherever you keep your projects, and rename it after your plugin, in lowercase letters, digits and hyphens: e.g. `herb-tracker`. That name is your plugin's identifier: what you type in the client console, and the name of the zip you upload.
2. Open `plugin.json` and set the name, the version and the contact address.
3. Open `src/constants.lua` and set the plugin's name and the prefix of its event names.
4. In the plugin folder, fetch the API definitions and link the plugin into the client:

   ```text
   ./gradlew fetchStubs linkDevPlugin
   ```

   In PowerShell, that is `.\gradlew fetchStubs linkDevPlugin`. The first run downloads Gradle and takes a minute.
5. In the game, open the developer console with `Alt` + `` ` ``. Type `prf`, so that the client finds the new plugin, then `pon herb-tracker` to enable it.

That is the whole setup. The folder can live anywhere, go into any repository, and needs nothing beside it.

## Day to day

Edit a file, type `prl herb-tracker` in the console, and read what the console says. The build is not part of this loop. The client loads your plugin folder itself, through the link that `linkDevPlugin` made, so there is nothing to compile.

`poff herb-tracker` disables the plugin and runs its shutdown. You need `prf` again only after adding a file or changing the manifest.

## Inside a plugin

| Path | What it is |
| --- | --- |
| `main.lua` | The entry point, wiring only: it loads the plugin's modules, subscribes to events and tears everything down in `PluginShutdown`. Its header comment says what the plugin reads, draws, stores and sends, for the reviewer who reads it next. |
| `plugin.json` | The manifest: name, version, contact address, dependencies and permissions. You write it by hand; the build checks it. |
| `src/` | Your code. `src/core/` holds logic that never touches the client, which the tests run. Every file under `src/` starts with a comment line of its own path, because the client's error messages name a file by its first line. |
| `resources/` | PNG sprites and OGG sounds, read as `Plugin.resources.<name>`. Create the folder when you need it. |
| `dev/` | Development-only code, which never ships. See [below](#development-only-code). |
| `tests/` | Standalone Lua 5.4 tests, one `*_test.lua` per core module. `./gradlew testLua` runs them, and so does `lua tests/run_all.lua` with an installed Lua. |
| `AGENTS.md` | The guide. Either keep it or remove it, depending on use-case. |
| `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `gradle/` | The build. `build/` is its output and is not committed. |

What ships to Mod.io is `main.lua`, `plugin.json`, `src/`, `resources/` and the Lua libraries you copied in. Nothing else.

## Development-only code

Cheats, probes and debugging helpers go under `dev/`. The template's `dev/init.lua` shows the shape, with a helper that runs a console cheat where the client offers one.

`main.lua` loads it with one line, `require("dev/init")`. In development, the client finds the file through the link and runs it. In a release, the folder is not in the zip, the client's `require` finds nothing and returns `nil`, and nothing happens. We checked: a release logs nothing for it.

`checkPlugin` keeps the two apart. `dev/` is never among the shipped files, and `ExecuteConsoleCommand` anywhere outside it fails the build.

## Using libraries

There are two kinds of library for RS3 plugins, and they work differently.

**Library plugins** are plugins of their own. The client loads them before yours and hands each to you as a global. [PrettyUI](https://prettyui.j3.gg/), the UI toolkit, is one. Declare it in `plugin.json` by its Mod.io ID, and the client does the rest:

```json
"requiredDependencies": { "prettyui": 6398181 }
```

Players need the library installed too, and Mod.io lists it as a dependency. For the editor, add the global's name to `diagnostics.globals` in `.luarc.json`, as the template already does for `prettyui`.

**Lua you copy into your plugin** ships inside it. [Zyklib](https://zyklib.readthedocs.io/latest/), for example, works this way. There are two ways to import a library.

1. Let the build copy it. Name where the library lives, a repository on GitHub, Codeberg or GitLab at a commit, or a checkout on your machine, and the folder of it that holds the Lua:

```kotlin
rspc {
    libraries {
        vendored("zyklib") {
            remote("https://codeberg.org/ZyklopMarco/runescape-api-zyklib", "<commit>")
            from("src")
        }
    }
}
```

The block alone changes nothing. The copying is a task, `vendorLibraries`, and it runs when you ask for it or for anything that needs the plugin's files: `linkDevPlugin`, `check`, `packagePlugin` and `build` all run it first.

```text
./gradlew vendorLibraries
```

The task downloads that commit of the repository once, into Gradle's cache, and from it copies the Lua under the `from` folder into your plugin folder, laid out as the folder is, so `zyklib.lua` and `zyklib/` land beside your `main.lua`. The licence goes to `licences/zyklib/`. Because the client loads your plugin folder through the link, the library is there as soon as the task finishes; `prl` your plugin and it is loaded.

Change the commit and run the task again, and it copies the new files and removes the ones the library no longer has. Take the block out of the build and run it again, and the files go too. Commit the copied files with your plugin or not, as you like; a fresh clone gets them back from the first run of `linkDevPlugin`. For a checkout on your machine, write `local(file("../runescape-api-zyklib"))` in place of `remote(...)`; the path is relative to the plugin folder.

2. Or copy it in yourself, `zyklib.lua` and `zyklib/` at the root and its licence file under `licences/zyklib/`, and only name it:

```kotlin
rspc {
    libraries { vendored("zyklib") }
}
```

## The editor

The template's `.luarc.json` points the Lua Language Server at `build/rspc/stubs/library`, where `fetchStubs` puts the API's definitions. With them, your editor completes API names, shows each call's documentation and flags mistakes.

The definitions come from [rs3-api-types](https://github.com/adelheidsoftware/rs3-api-types), at the commit `build.gradle.kts` pins, which is the commit the SDK was tested with. Any other repository or checkout of LuaCATS files works too: name it in the `stubs { }` block, and add `from("<folder>")` when its definition files are not in `generated/library`, `library` or its root. `./gradlew updateStubs` tells you the latest commit of the repository you use, so that moving on after a game update is a change you make on purpose.

## Checking and releasing

| Command | What it does |
| --- | --- |
| `./gradlew vendorLibraries` | Copies the Lua libraries the build names into the plugin, with their licences |
| `./gradlew testLua` | Runs `tests/*_test.lua` on the bundled Lua 5.4.8 |
| `./gradlew checkPlugin` | Checks the manifest and the files that would ship |
| `./gradlew check` | Both |
| `./gradlew packagePlugin` | After `check`, writes `build/distributions/<name>-<version>.zip` with `main.lua` and `plugin.json` at the top, as Mod.io wants it. The same sources give the same bytes. |

`checkPlugin` fails the build when the manifest has something the client refuses, such as a dependency list written as an array; when a `require` names a file that is not in the plugin; when a file under `src/` does not start with its path; when a resource is not a PNG or an OGG, or an image is larger than 1024 by 1024; when code uses a member that needs a permission the manifest does not declare; when development-only code is among the shipped files; or when anything else is.

## Support
This library comes with no explicit support. Please see the [official RuneScape plugin documentation](https://developer.runescape.com/rs/index.html), and the RuneScape Plugin Portal Discord linked therein.