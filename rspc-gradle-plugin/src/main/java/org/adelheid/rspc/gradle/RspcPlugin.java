package org.adelheid.rspc.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.adelheid.rspc.gradle.tasks.FetchStubs;
import org.adelheid.rspc.gradle.tasks.UpdateStubs;
import org.adelheid.rspc.gradle.tasks.VendorLibraries;
import org.adelheid.rspc.plugin.LuaLibraries;
import org.adelheid.rspc.plugin.Manifest;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * The Gradle plugin {@code org.adelheid.rspc}, for a plugin written in Lua whose files sit at the project's root as
 * the client loads them. It adds:
 *
 * <ul>
 *   <li>{@code fetchStubs}, the RS3 API's definitions at the commit the build names, staged under
 *       {@code build/rspc/stubs/} for the editor, and {@code updateStubs}, which names the latest commit;
 *   <li>{@code vendorLibraries}, which copies the Lua libraries the build names into the plugin's folder, from a
 *       checkout or a repository at a commit, with their licences, and lists them with those copied in by hand;
 *   <li>{@code assemblePlugin}, the folder that ships: {@code main.lua}, {@code plugin.json}, {@code src/},
 *       {@code resources/} and the libraries' Lua and licences, and nothing else, among it not {@code dev/};
 *   <li>{@code checkPlugin}, {@code packagePlugin}, {@code testLua} and {@code linkDevPlugin}, the tasks every RS3
 *       plugin build shares ({@link PluginTasks}): the checks of that folder, its zip, the tests under {@code tests/},
 *       and the link of the project's root into the client's {@code dev_plugins}, so that an edit needs no build
 *       before the client reloads the plugin.
 * </ul>
 */
public final class RspcPlugin implements Plugin<Project> {

	/** The rs3-api-types commit this release was tested against; another commit of it builds with a warning. */
	static final String TESTED_COMMIT = "9f151d069fec71348ec19d7f87722d8ff7178e9d";

	private static final String GROUP = "rspc";
	private static final String BLOCK = "rspc";
	private static final String WINDOWS_DEV_PLUGINS = "C:\\ProgramData\\Jagex\\RuneScape-BETA\\dev_plugins";

	@Override
	public void apply(Project project) {
		project.getPluginManager().apply(BasePlugin.class);
		RspcExtension extension = project.getExtensions().create(BLOCK, RspcExtension.class);
		extension.getPluginId().convention(project.getName().toLowerCase(Locale.ROOT));
		File windows = new File(WINDOWS_DEV_PLUGINS);
		if (windows.isDirectory()) {
			extension.getDevPlugins()
					.convention(project.getLayout().getProjectDirectory().dir(windows.getAbsolutePath()));
		}
		ProjectLayout layout = project.getLayout();
		TaskContainer tasks = project.getTasks();
		Provider<Directory> buildFolder = layout.getBuildDirectory().dir("rspc");
		Directory root = layout.getProjectDirectory();

		File cache = StubsSource.cache(project);
		Stubs stubs = extension.getStubs();
		Provider<String> latest = StubsSource.latestCommit(project, stubs, cache, "fetchStubs");
		tasks.register("fetchStubs", FetchStubs.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("Stages the RS3 API's definitions at the commit the build names, for the editor.");
			task.getLocalStubs().set(stubs.getLocal());
			task.getRemote().set(stubs.getRemote());
			task.getCommit().set(stubs.getRemote().flatMap(remote -> stubs.getCommit().orElse(latest)));
			task.getFrom().set(stubs.getFrom());
			task.getCache().set(cache);
			task.getBlock().set(BLOCK);
			task.getTestedCommit().set(TESTED_COMMIT);
			task.getOutputFolder().set(buildFolder.map(folder -> folder.dir("stubs")));
		});
		tasks.register("updateStubs", UpdateStubs.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("Names the latest commit of the RS3 API's definitions, to pin in the build.");
			task.getRemote().set(stubs.getRemote());
			task.getCommit().set(stubs.getCommit());
			task.getBlock().set(BLOCK);
		});

		TaskProvider<VendorLibraries> libraries = tasks.register("vendorLibraries", VendorLibraries.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("Copies the Lua libraries the build names into the plugin, with their licences, and"
					+ " lists them for the checks and the zip.");
			for (VendoredLibrary library : extension.getLibraries().declared()) {
				task.getLibraries().add(library);
				if (library.getLocal().isPresent()) {
					task.getLocalFiles().from(library.getLocal().get().getAsFileTree());
				}
			}
			task.getRoot().set(root);
			task.getCache().set(cache);
			task.getList().set(buildFolder.map(folder -> folder.file(LuaLibraries.FILE)));
		});
		// The libraries' files that ship, read from the list once it is written
		Provider<Set<String>> vendoredFiles = libraries.flatMap(VendorLibraries::getList).map(file -> {
			try {
				return LuaLibraries.read(file.getAsFile().toPath()).shipped().stream()
						.flatMap(library -> library.files().stream())
						.collect(Collectors.toSet());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});

		TaskProvider<Sync> assemblePlugin = tasks.register("assemblePlugin", Sync.class, sync -> {
			sync.setGroup(GROUP);
			sync.setDescription("Assembles the plugin as it ships: main.lua, plugin.json, src/, resources/, and the"
					+ " Lua and licences of the libraries it vendors.");
			sync.dependsOn(libraries);
			sync.from(root, spec -> spec.include(element -> ships(element.getRelativePath().getPathString(),
					element.isDirectory(), vendoredFiles.get())));
			sync.into(buildFolder.map(folder -> folder.dir("plugin")));
		});

		// The version is the manifest's, which names the zip; a manifest that cannot be read leaves it to checkPlugin
		Provider<String> version = project.getProviders().fileContents(root.file("plugin.json")).getAsText()
				.map(json -> {
					Manifest manifest = Manifest.parse("plugin", json).manifest();
					return manifest == null || manifest.version().isBlank() ? "unversioned" : manifest.version();
				}).orElse("unversioned");
		Directory tests = root.dir("tests");
		PluginTasks.Inputs inputs = new PluginTasks.Inputs(GROUP, BLOCK, extension.getPluginId(), version,
				layout.dir(assemblePlugin.map(Sync::getDestinationDir)), project.provider(() -> root),
				extension.getDevPlugins(), tests, tests.getAsFileTree(), extension.getLua().getExecutable(),
				buildFolder)
				.libraries(libraries.flatMap(VendorLibraries::getList));
		PluginTasks.Registered registered = PluginTasks.register(project, inputs);
		// The client loads the libraries from the plugin's folder, so linking it puts them there first
		registered.linkDevPlugin().configure(task -> task.dependsOn(libraries));
	}

	/**
	 * Whether a path of the project's root ships: the entry point and the manifest, everything under {@code src/}
	 * and {@code resources/}, and the files of the libraries the plugin vendors. A folder ships when files under it
	 * can, so that no empty folder is made for the others.
	 */
	private static boolean ships(String path, boolean directory, Set<String> vendoredFiles) {
		int slash = path.indexOf('/');
		String first = slash < 0 ? path : path.substring(0, slash);
		if (directory) {
			String prefix = path + "/";
			return first.equals("src") || first.equals("resources")
					|| vendoredFiles.stream().anyMatch(file -> file.startsWith(prefix));
		}
		return path.equals("main.lua") || path.equals("plugin.json") || first.equals("src")
				|| first.equals("resources") || vendoredFiles.contains(path);
	}
}
