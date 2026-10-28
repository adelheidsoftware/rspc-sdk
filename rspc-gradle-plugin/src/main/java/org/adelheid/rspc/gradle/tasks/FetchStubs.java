package org.adelheid.rspc.gradle.tasks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.adelheid.rspc.gradle.StubsSource;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * {@code fetchStubs}: stages the RS3 API's definitions the build names, from any repository or checkout of LuaCATS
 * files, under {@code build/rspc/stubs/}, where the plugin's {@code .luarc.json} points the Lua Language Server:
 * {@code library/} holds the definition files, whatever folder they came from, and {@code constants/} the game's
 * names, where the source has them as rs3-api-types does.
 */
@DisableCachingByDefault(because = "The constants are large, and the cache would hold a copy of a folder the"
		+ " repository cache has already.")
public abstract class FetchStubs extends DefaultTask {

	/** Where repositories keep their definition files, tried in order when the build names no folder. */
	private static final List<String> LAYOUTS = List.of("generated/library", "library", "");

	/** A checkout of the stubs on this machine. */
	@Optional
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getLocalStubs();

	/** A repository of the stubs on GitHub, Codeberg or GitLab, fetched at {@link #getCommit()}. */
	@Optional
	@Input
	public abstract Property<String> getRemote();

	/** The remote's commit: the pinned one, or the latest of its default branch. */
	@Optional
	@Input
	public abstract Property<String> getCommit();

	/** The folder of the definition files, from the repository's root; empty to find it by the layout. */
	@Input
	public abstract Property<String> getFrom();

	/** Where fetched repositories are kept, one folder per commit, in Gradle's user home. */
	@Internal
	public abstract DirectoryProperty getCache();

	/** The name of the build's block, for the messages. */
	@Input
	public abstract Property<String> getBlock();

	/** The commit this build's tooling was tested against. */
	@Optional
	@Input
	public abstract Property<String> getTestedCommit();

	@OutputDirectory
	public abstract DirectoryProperty getOutputFolder();

	@Inject
	protected abstract FileSystemOperations getFiles();

	@TaskAction
	public void fetch() {
		Path root = StubsSource.resolve(
				getLocalStubs().isPresent() ? getLocalStubs().get().getAsFile().toPath() : null,
				getRemote().getOrNull(), getCommit().getOrNull(), getCache().get().getAsFile().toPath(),
				getBlock().get(), getTestedCommit().getOrNull(), getLogger());
		Path definitions = definitions(root);
		// The game's names beside the definitions, as rs3-api-types lays them out
		Path constants = root.resolve("generated/constants");
		boolean withConstants = getFrom().get().isEmpty() && Files.isDirectory(constants);
		Path output = getOutputFolder().get().getAsFile().toPath();
		getFiles().sync(spec -> {
			spec.from(definitions, library -> library.into("library"));
			if (withConstants) {
				spec.from(constants, names -> names.into("constants"));
			}
			spec.into(output);
		});
		getLogger().lifecycle("The RS3 API's definitions are in {}: point the editor's Lua Language Server at it.",
				output.resolve("library"));
	}

	/**
	 * The folder of the definition files: the one the build names, or the first of the usual layouts that holds a
	 * Lua file.
	 */
	private Path definitions(Path root) {
		String from = getFrom().get();
		if (!from.isEmpty()) {
			Path named = root.resolve(from).normalize();
			if (!named.startsWith(root) || !holdsLua(named)) {
				throw new GradleException(root + " has no folder " + from + " with LuaCATS definition files; from(...)"
						+ " in " + getBlock().get() + " { stubs { } } names the folder of the definitions, from the"
						+ " repository's root.");
			}
			return named;
		}
		for (String layout : LAYOUTS) {
			Path folder = root.resolve(layout);
			if (holdsLua(folder)) {
				return folder;
			}
		}
		throw new GradleException(root + " holds no LuaCATS definition files in generated/library, library or its"
				+ " root; name the folder of them with from(...) in " + getBlock().get() + " { stubs { } }.");
	}

	/** Whether a folder holds a Lua file directly: definition files, rather than only folders of them. */
	private static boolean holdsLua(Path folder) {
		if (!Files.isDirectory(folder)) {
			return false;
		}
		try (Stream<Path> files = Files.list(folder)) {
			return files.anyMatch(file -> Files.isRegularFile(file) && file.getFileName().toString().endsWith(".lua"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
