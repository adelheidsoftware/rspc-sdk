package org.adelheid.rspc.gradle;

import org.gradle.api.file.RegularFileProperty;

/** The Lua 5.4 that runs the plugin's Lua tests: the bundled Lua 5.4.8 unless an installed one is named. */
public abstract class Lua {

	/** An installed Lua 5.4 interpreter; unset, the tests run on the bundled one. */
	public abstract RegularFileProperty getExecutable();
}
