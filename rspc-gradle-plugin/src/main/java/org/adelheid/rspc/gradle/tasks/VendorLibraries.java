package org.adelheid.rspc.gradle.tasks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.adelheid.rspc.gradle.VendoredLibraries;
import org.adelheid.rspc.gradle.VendoredLibrary;
import org.adelheid.rspc.gradle.remote.RemoteRepository;
import org.adelheid.rspc.plugin.LuaLibraries;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.jspecify.annotations.Nullable;

/**
 * {@code vendorLibraries}: puts the Lua libraries the build names into the plugin's folder, where the client loads
 * them with the plugin's own files, and lists them for the checks and the zip. A library with a source, a checkout on
 * this machine or a repository at a commit, is copied in from it: the Lua of the folder the build names, laid out at
 * the plugin's root as the library documents, and its licence under {@code licences/<name>/}; files it copied in
 * before and the source no longer has are removed, as are all the files of a library the build no longer names, as
 * far as the list from the last run remembers them. A library without a source was copied in by hand, and is only
 * checked: its Lua where its name says, and its licence under {@code licences/<name>/}.
 */
@UntrackedTask(because = "It writes the libraries' files into the plugin's folder, beside the plugin's own.")
public abstract class VendorLibraries extends DefaultTask {

	private final List<VendoredLibrary> libraries = new ArrayList<>();

	/** The libraries the build names. */
	@Nested
	public List<VendoredLibrary> getLibraries() {
		return libraries;
	}

	/** The files read from checkouts on this machine, for the record. */
	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getLocalFiles();

	/** The plugin's folder, the libraries' destination. */
	@Internal
	public abstract DirectoryProperty getRoot();

	/** Where fetched repositories are kept, one folder per commit, in Gradle's user home. */
	@Internal
	public abstract DirectoryProperty getCache();

	/** The list of the libraries and their files, which {@code checkPlugin} and the zip read. */
	@OutputFile
	public abstract RegularFileProperty getList();

	@TaskAction
	public void vendor() {
		Path root = getRoot().get().getAsFile().toPath();
		Path list = getList().get().getAsFile().toPath();
		try {
			Map<String, List<String>> before = new HashMap<>();
			if (Files.isRegularFile(list)) {
				LuaLibraries.read(list).libraries().forEach(library -> before.put(library.name(), library.files()));
			}
			List<String> problems = new ArrayList<>();
			// Each file's path in the plugin, by the library that has it
			Map<String, String> taken = new HashMap<>();
			// What each copied file comes from
			Map<Path, Path> copies = new LinkedHashMap<>();
			List<LuaLibraries.Library> listed = new ArrayList<>();
			List<VendoredLibrary> sorted = libraries.stream()
					.sorted(Comparator.comparing(VendoredLibrary::getName))
					.toList();
			for (VendoredLibrary library : sorted) {
				List<String> files = hasSource(library)
						? fromSource(library, root, taken, copies, problems)
						: VendoredLibraries.inPlace(root, library.getName(), problems);
				listed.add(new LuaLibraries.Library(library.getName(), LuaLibraries.Use.VENDORED,
						library.getDevelopment().get(), files));
			}
			if (!problems.isEmpty()) {
				throw new GradleException("The plugin's libraries cannot be vendored:\n  - "
						+ String.join("\n  - ", problems));
			}
			for (LuaLibraries.Library library : listed) {
				removeStale(root, before.getOrDefault(library.name(), List.of()), new HashSet<>(library.files()));
			}
			// A library the build no longer names leaves with every file it had
			Set<String> names = new HashSet<>();
			listed.forEach(library -> names.add(library.name()));
			for (Map.Entry<String, List<String>> gone : before.entrySet()) {
				if (!names.contains(gone.getKey())) {
					removeStale(root, gone.getValue(), Set.of());
				}
			}
			for (Map.Entry<Path, Path> copy : copies.entrySet()) {
				Files.createDirectories(copy.getKey().getParent());
				Files.copy(copy.getValue(), copy.getKey(), StandardCopyOption.REPLACE_EXISTING);
			}
			new LuaLibraries(listed).write(list);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean hasSource(VendoredLibrary library) {
		return library.getLocal().isPresent() || library.getRemote().isPresent();
	}

	/**
	 * The files a library with a source puts into the plugin, and the copies that put them there: the Lua of the
	 * named folder at the plugin's root, and the licence under {@code licences/<name>/}.
	 */
	private List<String> fromSource(VendoredLibrary library, Path root, Map<String, String> taken,
			Map<Path, Path> copies, List<String> problems) throws IOException {
		String name = library.getName();
		Path source = source(library);
		Path from = source.resolve(library.getFrom().get()).normalize();
		if (!from.startsWith(source) || !Files.isDirectory(from)) {
			problems.add(name + " has no folder " + library.getFrom().get() + "; from(...) names a folder of it, from"
					+ " its root.");
			return List.of();
		}
		List<String> lua = VendoredLibraries.luaFiles(from);
		if (lua.isEmpty()) {
			problems.add(name + " has no Lua files in " + (library.getFrom().get().isEmpty() ? "its root"
					: library.getFrom().get()) + "; from(...) names the folder whose Lua goes to the plugin.");
		}
		List<String> files = new ArrayList<>();
		for (String path : lua) {
			String reserved = VendoredLibraries.reserved(path);
			String other = taken.putIfAbsent(path, name);
			if (reserved != null) {
				problems.add(name + "'s " + path + " would go " + reserved + "; from(...) names the folder of the"
						+ " library whose Lua goes to the plugin's root, as its documentation lays it out.");
			} else if (other != null) {
				problems.add(name + " and " + other + " both have " + path + ", which only one file of the plugin"
						+ " can be.");
			} else {
				files.add(path);
				copies.put(root.resolve(path), from.resolve(path));
			}
		}
		Path licence = VendoredLibraries.licence(library, source, problems);
		if (licence != null) {
			String path = VendoredLibraries.LICENCES + name + "/" + licence.getFileName();
			files.add(path);
			copies.put(root.resolve(path), licence);
		}
		return files;
	}

	/** A library's source: its checkout on this machine, or its repository's commit, fetched once. */
	private Path source(VendoredLibrary library) throws IOException {
		if (library.getLocal().isPresent() && library.getRemote().isPresent()) {
			throw new GradleException(library.getName() + " names its source twice; give one of local(file(\"...\")),"
					+ " a checkout on this machine, or remote(\"<url>\", \"<commit>\"), a repository on GitHub,"
					+ " Codeberg or GitLab at a full commit.");
		}
		if (library.getLocal().isPresent()) {
			return library.getLocal().get().getAsFile().toPath().toAbsolutePath().normalize();
		}
		String commit = library.getCommit().getOrNull();
		if (commit == null) {
			throw new GradleException(library.getName() + " names its repository but no commit of it:"
					+ " remote(\"<url>\", \"<commit>\"), a full 40-character commit hash, so that every machine"
					+ " vendors the same files.");
		}
		RemoteRepository repository = RemoteRepository.parse(library.getRemote().get());
		getLogger().lifecycle("Vendoring {} from {} at commit {}.", library.getName(), repository.url(), commit);
		return repository.checkout(commit, getCache().get().getAsFile().toPath()).toAbsolutePath().normalize();
	}

	/** Removes the files a library had in the plugin that it no longer has, and the folders that empties. */
	private static void removeStale(Path root, List<String> before, Set<String> now) throws IOException {
		for (String path : before) {
			if (!now.contains(path)) {
				Path file = root.resolve(path);
				Files.deleteIfExists(file);
				removeEmptyFolders(root, file.getParent());
			}
		}
	}

	private static void removeEmptyFolders(Path root, @Nullable Path folder) throws IOException {
		while (folder != null && !folder.equals(root) && Files.isDirectory(folder) && isEmpty(folder)) {
			Files.delete(folder);
			folder = folder.getParent();
		}
	}

	private static boolean isEmpty(Path folder) throws IOException {
		try (var entries = Files.list(folder)) {
			return entries.findAny().isEmpty();
		}
	}
}
