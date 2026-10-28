package org.adelheid.rspc.gradle;

import org.adelheid.rspc.gradle.tasks.CheckPlugin;
import org.adelheid.rspc.gradle.tasks.LinkDevPlugin;
import org.adelheid.rspc.gradle.tasks.TestLua;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.jspecify.annotations.Nullable;

/**
 * The tasks every RS3 plugin build shares, whatever writes the plugin's Lua: {@code checkPlugin},
 * {@code packagePlugin}, {@code testLua} and {@code linkDevPlugin}, registered from the folders a build gives them.
 * This plugin registers them for a plugin written in Lua, and a build that compiles its Lua from another language
 * registers them for the folder it assembles.
 */
public final class PluginTasks {

	private PluginTasks() {
	}

	/** What the shared tasks work on; the build fills it in and {@link #register} reads it. */
	public static final class Inputs {

		final String group;
		final String block;
		final Provider<String> pluginId;
		final Provider<String> version;
		final Provider<Directory> shippedFolder;
		final Provider<Directory> linkTarget;
		final DirectoryProperty devPlugins;
		final Directory testsFolder;
		final FileTree testFiles;
		final RegularFileProperty luaExecutable;
		final Provider<Directory> buildFolder;
		@Nullable Provider<RegularFile> clientFacts;
		@Nullable Provider<RegularFile> libraries;

		/**
		 * @param group the tasks' group, as {@code gradle tasks} lists them
		 * @param block the name of the build's block, for the messages
		 * @param pluginId the plugin's identifier, the link's name and the zip's
		 * @param version the plugin's version, in the zip's name
		 * @param shippedFolder the folder that ships, which carries the task that assembles it
		 * @param linkTarget the folder {@code linkDevPlugin} links into the client
		 * @param devPlugins the client's {@code dev_plugins} folder
		 * @param testsFolder the folder of the Lua tests
		 * @param testFiles the test files in it
		 * @param luaExecutable an installed Lua 5.4 for the tests, in place of the bundled one
		 * @param buildFolder where the tasks write their reports and work files
		 */
		public Inputs(String group, String block, Provider<String> pluginId, Provider<String> version,
				Provider<Directory> shippedFolder, Provider<Directory> linkTarget, DirectoryProperty devPlugins,
				Directory testsFolder, FileTree testFiles, RegularFileProperty luaExecutable,
				Provider<Directory> buildFolder) {
			this.group = group;
			this.block = block;
			this.pluginId = pluginId;
			this.version = version;
			this.shippedFolder = shippedFolder;
			this.linkTarget = linkTarget;
			this.devPlugins = devPlugins;
			this.testsFolder = testsFolder;
			this.testFiles = testFiles;
			this.luaExecutable = luaExecutable;
			this.buildFolder = buildFolder;
		}

		/** The client facts a build generates from the API's definitions; without them, the built-in facts. */
		public Inputs clientFacts(Provider<RegularFile> file) {
			this.clientFacts = file;
			return this;
		}

		/** The list of the libraries the build stages; without it, the plugin vendors none. */
		public Inputs libraries(Provider<RegularFile> file) {
			this.libraries = file;
			return this;
		}
	}

	/** The registered tasks, for the build to wire further. */
	public record Registered(TaskProvider<CheckPlugin> checkPlugin, TaskProvider<Zip> packagePlugin,
			TaskProvider<TestLua> testLua, TaskProvider<LinkDevPlugin> linkDevPlugin) {
	}

	/** Registers the shared tasks, and hangs them on {@code check} and {@code assemble}. */
	public static Registered register(Project project, Inputs inputs) {
		TaskContainer tasks = project.getTasks();
		TaskProvider<CheckPlugin> checkPlugin = tasks.register("checkPlugin", CheckPlugin.class, task -> {
			task.setGroup(inputs.group);
			task.setDescription("Checks that the plugin folder holds only what may ship.");
			task.getPluginFolder().set(inputs.shippedFolder);
			task.getPluginId().set(inputs.pluginId);
			if (inputs.clientFacts != null) {
				task.getClientFacts().set(inputs.clientFacts);
			}
			if (inputs.libraries != null) {
				task.getLibraries().set(inputs.libraries);
			}
			task.getReport().set(inputs.buildFolder.map(folder -> folder.file("check-plugin.txt")));
		});
		tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, check -> check.dependsOn(checkPlugin));

		TaskProvider<Zip> packagePlugin = tasks.register("packagePlugin", Zip.class, zip -> {
			zip.setGroup(inputs.group);
			zip.setDescription("Zips the plugin folder for Mod.io, the same bytes for the same sources.");
			zip.dependsOn(checkPlugin);
			zip.from(inputs.shippedFolder);
			zip.getArchiveFileName()
					.set(inputs.pluginId.zip(inputs.version, (id, number) -> id + "-" + number + ".zip"));
			zip.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("distributions"));
			zip.setPreserveFileTimestamps(false);
			zip.setReproducibleFileOrder(true);
			zip.dirPermissions(permissions -> permissions.unix("rwxr-xr-x"));
			zip.filePermissions(permissions -> permissions.unix("rw-r--r--"));
		});
		tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME, assemble -> assemble.dependsOn(packagePlugin));

		TaskProvider<TestLua> testLua = tasks.register("testLua", TestLua.class, task -> {
			task.setGroup(inputs.group);
			task.setDescription("Runs the plugin's Lua tests against the plugin as it ships.");
			task.getPluginFolder().set(inputs.shippedFolder);
			task.getTestFiles().from(inputs.testFiles);
			task.getTestsFolder().set(inputs.testsFolder);
			task.getLuaExecutable().set(inputs.luaExecutable);
			task.getWorkFolder().set(inputs.buildFolder.map(folder -> folder.dir("test-lua")));
			task.getReport().set(inputs.buildFolder.map(folder -> folder.file("test-lua.txt")));
		});
		tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, check -> check.dependsOn(testLua));

		TaskProvider<LinkDevPlugin> linkDevPlugin = tasks.register("linkDevPlugin", LinkDevPlugin.class, task -> {
			task.setGroup(inputs.group);
			task.setDescription("Links the plugin into the client's dev_plugins folder under its id.");
			task.getDevPlugins().set(inputs.devPlugins);
			task.getPluginId().set(inputs.pluginId);
			task.getBlock().set(inputs.block);
			task.getTarget().set(inputs.linkTarget);
		});
		return new Registered(checkPlugin, packagePlugin, testLua, linkDevPlugin);
	}
}
