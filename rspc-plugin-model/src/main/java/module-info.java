/**
 * An RS3 plugin apart from its code: its manifest, the checks of the folder that ships, and the Lua libraries it
 * uses.
 */
module org.adelheid.rspc.plugin {
	// JSpecify's nullness annotations, which the API carries for the code that compiles against it
	requires static transitive org.jspecify;

	exports org.adelheid.rspc.plugin;
}
