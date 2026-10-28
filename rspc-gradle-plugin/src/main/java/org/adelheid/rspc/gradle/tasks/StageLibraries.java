package org.adelheid.rspc.gradle.tasks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import org.adelheid.rspc.gradle.LibraryTypes;
import org.adelheid.rspc.gradle.LuaCode;
import org.adelheid.rspc.gradle.VendoredLibraries;
import org.adelheid.rspc.gradle.VendoredLibrary;
import org.adelheid.rspc.gradle.remote.RemoteRepository;
import org.adelheid.rspc.plugin.LuaLibraries;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.jspecify.annotations.Nullable;

/**
 * Stages the Lua the plugin uses that is not its own, each from a checkout on this machine or a repository fetched at
 * a commit ({@link LuaLibraries}): the libraries it vendors as they ship, the Lua files of the folder the build names
 * laid out in the plugin's root as the library documents, and its licence under {@code licences/<name>/}; and the
 * types of the library plugins it depends on, which never ship. A library without a licence fails, since nobody may
 * pass on its code without one, and so does a file where the plugin's own go, or that another library has too.
 */
@CacheableTask
public abstract class StageLibraries extends DefaultTask {

	private final List<VendoredLibrary> libraries = new ArrayList<>();
	private final List<LibraryTypes> types = new ArrayList<>();

	/** The libraries the build vendors. */
	@Nested
	public List<VendoredLibrary> getLibraries() {
		return libraries;
	}

	/** The types the build names for the library plugins it depends on. */
	@Nested
	public List<LibraryTypes> getTypes() {
		return types;
	}

	/** The files read from checkouts on this machine: the Lua of each, and the files at a library's root. */
	@InputFiles
	@IgnoreEmptyDirectories
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getLocalFiles();

	/** Where fetched repositories are kept, one folder per commit, in Gradle's user home. */
	@Internal
	public abstract DirectoryProperty getCache();

	@OutputDirectory
	public abstract DirectoryProperty getOutputFolder();

	@TaskAction
	public void stage() {
		try {
			Path output = getOutputFolder().get().getAsFile().toPath();
			clean(output);
			List<String> problems = new ArrayList<>();
			// What each staged file copies
			Map<Path, Path> copies = new LinkedHashMap<>();
			List<LuaLibraries.Library> listed = new ArrayList<>();
			vendor(output, copies, listed, problems);
			for (LibraryTypes dependency : types) {
				stageTypes(dependency, output, copies, listed, problems);
			}
			if (!problems.isEmpty()) {
				throw new GradleException("The plugin's libraries cannot be staged:\n  - "
						+ String.join("\n  - ", problems));
			}
			for (Map.Entry<Path, Path> copy : copies.entrySet()) {
				Files.createDirectories(copy.getKey().getParent());
				Files.copy(copy.getValue(), copy.getKey(), StandardCopyOption.REPLACE_EXISTING);
			}
			listed.sort(Comparator.comparing(LuaLibraries.Library::name));
			new LuaLibraries(listed).write(output.resolve(LuaLibraries.FILE));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Stages the vendored libraries: their Lua as it ships and again as their types, and their licences. */
	private void vendor(Path output, Map<Path, Path> copies, List<LuaLibraries.Library> listed, List<String> problems)
			throws IOException {
		// Each file's path in the plugin, by the library that has it
		Map<String, String> taken = new HashMap<>();
		List<VendoredLibrary> sorted = libraries.stream()
				.sorted(Comparator.comparing(VendoredLibrary::getName))
				.toList();
		for (VendoredLibrary library : sorted) {
			String name = library.getName();
			boolean development = library.getDevelopment().get();
			Path root = root(library, name);
			Path from = from(library, root, name, problems);
			if (from == null) {
				continue;
			}
			List<String> lua = VendoredLibraries.luaFiles(from);
			if (lua.isEmpty()) {
				problems.add(name + " has no Lua files in " + (library.getFrom().get().isEmpty() ? "its root"
						: library.getFrom().get()) + "; from(...) names the folder whose Lua goes to the plugin.");
			}
			Path shipped = LuaLibraries.shippedFolder(output, development);
			List<String> files = new ArrayList<>();
			for (String path : lua) {
				String reserved = VendoredLibraries.reserved(path);
				String other = taken.putIfAbsent(path, name);
				if (reserved != null) {
					problems.add(name + "'s " + path + " would ship " + reserved + "; from(...) names the folder of"
							+ " the library whose Lua goes to the plugin's root, as its documentation lays it out.");
				} else if (other != null) {
					problems.add(name + " and " + other + " both have " + path + ", which only one file of the"
							+ " plugin can be.");
				} else {
					files.add(path);
					copies.put(shipped.resolve(path), from.resolve(path));
					copies.put(LuaLibraries.typesFolder(output, name).resolve(path), from.resolve(path));
				}
			}
			Path licence = VendoredLibraries.licence(library, root, problems);
			if (licence != null) {
				String path = VendoredLibraries.LICENCES + name + "/" + licence.getFileName();
				files.add(path);
				copies.put(shipped.resolve(path), licence);
			}
			overrides(library, output, copies);
			listed.add(new LuaLibraries.Library(name, LuaLibraries.Use.VENDORED, development, files));
		}
	}

	/**
	 * Stages the types of a library plugin, which go by its alias as a vendored library goes by its name, so that the
	 * two cannot share one.
	 */
	private void stageTypes(LibraryTypes dependency, Path output, Map<Path, Path> copies,
			List<LuaLibraries.Library> listed, List<String> problems) throws IOException {
		String alias = dependency.getName();
		String described = alias + "'s types { }";
		if (libraries.stream().anyMatch(library -> library.getName().equals(alias))) {
			problems.add(alias + " is both a vendored library and a dependency's alias, which would share one name;"
					+ " rename the library.");
			return;
		}
		if (SourceVersion.isKeyword(alias)) {
			problems.add("The dependency's alias " + alias + " is a word Java reserves, which the bindings a build"
					+ " generates from its types cannot take as a package; choose another alias, or leave out its"
					+ " types.");
			return;
		}
		Path root = root(dependency, described);
		Path from = from(dependency, root, described, problems);
		if (from == null) {
			return;
		}
		List<String> lua = VendoredLibraries.luaFiles(from);
		if (lua.isEmpty()) {
			problems.add(described + " has no Lua files in " + (dependency.getFrom().get().isEmpty()
					? "its root" : dependency.getFrom().get()) + "; from(...) names the folder of its LuaCATS files.");
		}
		for (String path : lua) {
			copies.put(LuaLibraries.typesFolder(output, alias).resolve(path), from.resolve(path));
		}
		overrides(dependency, output, copies);
		listed.add(new LuaLibraries.Library(alias, dependency.getUse().get(), dependency.getDevelopment().get(),
				List.of()));
	}

	/**
	 * The root of some Lua: its checkout on this machine, or its repository's commit, fetched once.
	 *
	 * @param described what the messages call it: a library's name, or the types of a dependency
	 */
	private Path root(LuaCode code, String described) throws IOException {
		boolean local = code.getLocal().isPresent();
		boolean remote = code.getRemote().isPresent() && code.getCommit().isPresent();
		if (local == remote) {
			throw new GradleException(described + (local ? " names its code twice" : " names no code")
					+ "; give one of local(file(\"...\")), a checkout on this machine, or remote(\"<url>\","
					+ " \"<commit>\"), a repository on GitHub, Codeberg or GitLab at a full commit.");
		}
		if (local) {
			return code.getLocal().get().getAsFile().toPath().toAbsolutePath().normalize();
		}
		RemoteRepository repository = RemoteRepository.parse(code.getRemote().get());
		String commit = code.getCommit().get();
		getLogger().info("Staging {} from {} at commit {}.", described, repository.url(), commit);
		return repository.checkout(commit, getCache().get().getAsFile().toPath()).toAbsolutePath().normalize();
	}

	/** The folder {@code from} names, from the root; null, with a problem, where there is none. */
	private static @Nullable Path from(LuaCode code, Path root, String described, List<String> problems) {
		Path from = root.resolve(code.getFrom().get()).normalize();
		if (!from.startsWith(root) || !Files.isDirectory(from)) {
			problems.add(described + " has no folder " + code.getFrom().get() + "; from(...) names a folder"
					+ " of it, from its root.");
			return null;
		}
		return from;
	}

	private static void overrides(LuaCode code, Path output, Map<Path, Path> copies) {
		if (code.getOverrides().isPresent()) {
			copies.put(LuaLibraries.overridesFile(output, code.getName()),
					code.getOverrides().get().getAsFile().toPath());
		}
	}

	private static void clean(Path folder) throws IOException {
		if (!Files.exists(folder)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(folder)) {
			for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
				if (!path.equals(folder)) {
					Files.delete(path);
				}
			}
		}
	}
}
