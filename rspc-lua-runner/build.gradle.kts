// Runs Lua 5.4 for the RSPC SDK's test task and the builds that extend it: the bundled Lua 5.4.8 (LuaJava's native
// builds of the reference implementation, the version the RS3 client embeds), or an installed interpreter the
// developer names instead. Compiled for Java 17, because the Gradle plugin's worker runs it on Gradle's own JVM.

plugins {
	`java-library`
}

description = "Runs Lua 5.4 on the bundled Lua 5.4.8 or an installed interpreter, and an RS3 plugin's Lua tests."

dependencies {
	implementation(libs.luajava.lua54)
	runtimeOnly(variantOf(libs.luajava.lua54.platform) { classifier("natives-desktop") })
}

tasks.test {
	// LuaJava loads its native library through System.load, which newer JDKs report unless native access is granted
	jvmArgs("--enable-native-access=ALL-UNNAMED")
}
