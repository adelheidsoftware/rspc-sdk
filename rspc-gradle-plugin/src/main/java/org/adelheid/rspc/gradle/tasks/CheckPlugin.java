package org.adelheid.rspc.gradle.tasks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.adelheid.rspc.plugin.ClientFacts;
import org.adelheid.rspc.plugin.LuaLibraries;
import org.adelheid.rspc.plugin.PackageChecks;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * {@code checkPlugin}: checks the folder that ships as {@link PackageChecks} describes: its manifest, what it holds,
 * each Lua file's first line and its requires, and that no Lua in it uses a member of the client's that is for
 * development only, nor, but in a library, one that needs a permission the manifest lacks. It runs as part of
 * {@code check}, and before the zip is made.
 */
@CacheableTask
public abstract class CheckPlugin extends DefaultTask {

	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getPluginFolder();

	/** The plugin's identifier, which the manifest is checked for. */
	@Input
	public abstract Property<String> getPluginId();

	/**
	 * The client's members that need a permission or are for development only, and its globals, as a build generates
	 * them from the API's definitions; without the file, the built-in facts.
	 */
	@Optional
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getClientFacts();

	/** The list of the libraries the plugin uses and their files, as the build stages them; without it, none. */
	@Optional
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getLibraries();

	/** The outcome, kept so that the check is up to date while the folder does not change. */
	@OutputFile
	public abstract RegularFileProperty getReport();

	@TaskAction
	public void check() {
		try {
			ClientFacts facts = getClientFacts().isPresent()
					? ClientFacts.read(getClientFacts().get().getAsFile().toPath())
					: ClientFacts.builtIn();
			List<LuaLibraries.Library> libraries = getLibraries().isPresent()
					? LuaLibraries.read(getLibraries().get().getAsFile().toPath()).shipped()
					: List.of();
			List<String> problems = PackageChecks.check(getPluginFolder().get().getAsFile().toPath(),
					getPluginId().get(), facts, libraries);
			Path report = getReport().get().getAsFile().toPath();
			Files.createDirectories(report.getParent());
			String outcome = problems.isEmpty() ? "The plugin may ship.\n" : String.join("\n", problems) + "\n";
			Files.writeString(report, outcome, StandardCharsets.UTF_8);
			if (!problems.isEmpty()) {
				throw new GradleException("The plugin folder " + getPluginFolder().get().getAsFile()
						+ " may not ship:\n  - " + String.join("\n  - ", problems));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
