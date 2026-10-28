package org.adelheid.rspc.gradle;

import java.util.List;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

/**
 * The Lua libraries the plugin vendors: copied into it, as their documentation lays them out, with their licences,
 * by hand or by the build from a checkout or a repository at a commit. Each has a name, a word of lowercase letters
 * and digits, which names the folder of its licence, {@code licences/<name>/}, and, in a build that generates
 * bindings, the package of them.
 */
public abstract class Libraries {

	/** A library's name: a word of lowercase letters and digits, which a package name can take. */
	private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9]*");

	private final NamedDomainObjectContainer<VendoredLibrary> declared;

	@Inject
	public Libraries(ObjectFactory objects) {
		this.declared = objects.domainObjectContainer(VendoredLibrary.class);
	}

	/**
	 * A library already copied into the plugin by hand, as its documentation lays it out: {@code <name>.lua} and
	 * {@code <name>/} at the plugin's root, with its licence under {@code licences/<name>/}.
	 */
	public void vendored(String name) {
		declared.create(checked(name));
	}

	/**
	 * A library that ships with the plugin, its Lua in the plugin's root, copied in from the source the block names:
	 * a checkout on this machine, or a repository at a commit.
	 */
	public void vendored(String name, Action<? super VendoredLibrary> action) {
		declared.create(checked(name), action);
	}

	/** A library that only development uses, which goes into the development folder alone and never ships. */
	public void development(String name, Action<? super VendoredLibrary> action) {
		declared.create(checked(name), library -> {
			library.getDevelopment().set(true);
			action.execute(library);
		});
	}

	/** The libraries the build declares. */
	public List<VendoredLibrary> declared() {
		return List.copyOf(declared);
	}

	private static String checked(String name) {
		if (!NAME.matcher(name).matches() || SourceVersion.isKeyword(name)) {
			throw new GradleException("A library's name, " + name + ", must be a word of lowercase letters and digits,"
					+ " starting with a letter, that is no word Java reserves, such as widgets; it names the folder"
					+ " of the library's licence, and the package of its bindings where a build generates them.");
		}
		return name;
	}
}
