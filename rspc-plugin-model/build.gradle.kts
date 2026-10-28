// An RS3 plugin apart from its code, for the RSPC SDK's Gradle plugin and the builds that extend it: the manifest and
// its plugin.json, the checks of the folder that ships, the list of the Lua libraries a plugin uses, and the client's
// members the checks look for. Compiled for Java 17, because the Gradle plugin uses it inside the Gradle daemon.

plugins {
	`java-library`
}

description = "An RS3 plugin apart from its code: its manifest, the checks of the folder that ships, and the Lua " +
	"libraries it uses."
