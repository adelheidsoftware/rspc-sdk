// The plugin's build. The SDK's Gradle plugin gives it the tasks: fetchStubs and updateStubs for the API definitions
// the editor reads, linkDevPlugin for the client, testLua, checkPlugin and packagePlugin for a release. Nothing here
// ships: the client loads main.lua, plugin.json, src/ and resources/, and the zip holds those alone.

plugins {
    id("org.adelheid.rspc") version "0.1.0"
}

rspc {
    // The API's definitions the editor reads: LuaCATS files from any repository or checkout, pinned to a commit;
    // updateStubs names the latest. Another source goes the same way, with from("<folder>") when its definition files
    // are neither in generated/library, library nor its root.
    stubs { remote("https://github.com/adelheidsoftware/rs3-api-types", "9f151d069fec71348ec19d7f87722d8ff7178e9d") }

    // The Lua libraries the plugin vendors, which ship inside it with their licences under licences/<name>/: copied
    // in by the build from a repository at a commit or a checkout, or copied in by hand and only named.
    // libraries {
    //     vendored("zyklib") {
    //         remote("https://codeberg.org/ZyklopMarco/runescape-api-zyklib", "<commit>")
    //         from("src")
    //     }
    // }
}
