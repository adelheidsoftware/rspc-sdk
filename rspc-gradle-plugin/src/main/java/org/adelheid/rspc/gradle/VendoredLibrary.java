package org.adelheid.rspc.gradle;

import javax.inject.Inject;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * A library the plugin vendors: its code, of which the Lua files of {@code from} go to the plugin's root, as the
 * library's documentation lays them out, and its licence.
 */
public abstract class VendoredLibrary extends LuaCode {

	@Inject
	public VendoredLibrary(String name) {
		super(name);
		getDevelopment().convention(false);
	}

	/**
	 * The file of the library's licence, from its root; by default the one file at its root named like a licence,
	 * such as {@code LICENSE} or {@code LICENCE.md}.
	 */
	@Optional
	@Input
	public abstract Property<String> getLicence();

	/** Whether only development uses the library, so that it never ships; set by {@code development(...)}. */
	@Input
	public abstract Property<Boolean> getDevelopment();

	/** The file of the library's licence, which ships under {@code licences/<name>/}. */
	public void licence(String file) {
		getLicence().set(file);
	}
}
