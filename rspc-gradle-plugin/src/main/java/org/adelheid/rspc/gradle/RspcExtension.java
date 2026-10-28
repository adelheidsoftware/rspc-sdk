package org.adelheid.rspc.gradle;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * The {@code rspc { }} block of a plugin's build:
 *
 * <pre>{@code
 * rspc {
 *     stubs {
 *         remote("https://github.com/adelheidsoftware/rs3-api-types", "<commit>")
 *     }
 *     libraries {
 *         vendored("zyklib")
 *         vendored("gauges") {
 *             remote("https://codeberg.org/<owner>/<repository>", "<commit>")
 *             from("src")
 *         }
 *     }
 * }
 * }</pre>
 *
 * The plugin's files sit at the project's root as the client loads them: {@code main.lua}, {@code plugin.json},
 * {@code src/} and {@code resources/}, with {@code dev/} for development-only code and {@code tests/} for the Lua
 * tests, and the Lua libraries it vendors beside them, copied in by hand or by {@code vendorLibraries} from the source
 * each names, their licences under {@code licences/<name>/}. The manifest is written by hand, and {@code checkPlugin}
 * reads it.
 */
public abstract class RspcExtension {

	private final Stubs stubs;
	private final Libraries libraries;
	private final Lua lua;

	@Inject
	public RspcExtension(ObjectFactory objects) {
		this.stubs = objects.newInstance(Stubs.class);
		this.libraries = objects.newInstance(Libraries.class);
		this.lua = objects.newInstance(Lua.class);
	}

	/**
	 * The plugin's identifier, lowercase words joined by hyphens: the name of its link in {@code dev_plugins} and of
	 * its zip. By default the project's name, which is the folder's.
	 */
	public abstract Property<String> getPluginId();

	/**
	 * The client's {@code dev_plugins} folder, where {@code linkDevPlugin} links the plugin; by default the beta
	 * client's on Windows, {@code C:\ProgramData\Jagex\RuneScape-BETA\dev_plugins}.
	 */
	public abstract DirectoryProperty getDevPlugins();

	/** The Lua libraries the plugin vendors, whose Lua and licences ship with it. */
	public Libraries getLibraries() {
		return libraries;
	}

	public void libraries(Action<? super Libraries> action) {
		action.execute(libraries);
	}

	/** Where the RS3 API's definitions come from, which {@code fetchStubs} stages for the editor. */
	public Stubs getStubs() {
		return stubs;
	}

	public void stubs(Action<? super Stubs> action) {
		action.execute(stubs);
	}

	/** The Lua that runs the plugin's Lua tests. */
	public Lua getLua() {
		return lua;
	}

	public void lua(Action<? super Lua> action) {
		action.execute(lua);
	}
}
