package org.adelheid.rspc.gradle;

import javax.inject.Inject;
import org.adelheid.rspc.plugin.LuaLibraries;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/**
 * The LuaCATS files of a library plugin the plugin depends on, which never ship: definition files written from its
 * docs, or a repository of community types, with {@code from} for a folder of it, such as {@code library} in one laid
 * out for LLS-Addons. They declare the global of its alias, or give the {@code main.lua} that returns it.
 */
public abstract class LibraryTypes extends LuaCode {

	@Inject
	public LibraryTypes(String name) {
		super(name);
	}

	/** Whether the plugin cannot do without the library; its dependency's. */
	@Input
	public abstract Property<LuaLibraries.Use> getUse();

	/** Whether only development uses the library; its dependency's. */
	@Input
	public abstract Property<Boolean> getDevelopment();
}
