package org.adelheid.rspc.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.adelheid.rspc.plugin.LuaLibraries;
import org.jspecify.annotations.Nullable;

/**
 * What the Lua libraries a plugin vendors have in common, however they arrive: copied in by hand, or by the build from
 * a checkout or a repository at a commit. A library's Lua sits at the plugin's root as the library documents its
 * layout, and its licence under {@code licences/<name>/}; nothing else of it ships. These are the rules of where its
 * files may go, which files of a folder count, and which file is its licence.
 */
public final class VendoredLibraries {

	/** Where the libraries' licences go in the plugin, one folder per library. */
	public static final String LICENCES = "licences/";

	private VendoredLibraries() {
	}

	/** The paths of the Lua files under a folder, from it, in order. */
	public static List<String> luaFiles(Path folder) throws IOException {
		try (Stream<Path> walk = Files.walk(folder)) {
			return walk.filter(Files::isRegularFile)
					.filter(file -> file.getFileName().toString().endsWith(".lua"))
					.map(file -> folder.relativize(file).toString().replace('\\', '/'))
					.sorted()
					.toList();
		}
	}

	/** Where in the plugin a path is the plugin's own, as a phrase; null for a path a library may take. */
	public static @Nullable String reserved(String path) {
		if (path.equals("main.lua")) {
			return "in place of the plugin's main.lua";
		}
		for (Map.Entry<String, String> folder : Map.of(
				"src/", "in the plugin's src/ folder, which holds its own Lua",
				"dev/", "in the plugin's dev/ folder, which holds its development-only Lua",
				"tests/", "in the plugin's tests/ folder, which never ships",
				"resources/", "in the plugin's resources/ folder",
				"utils/", "in the plugin's utils/ folder, which holds its helpers",
				LICENCES, "in the plugin's licences/ folder, which holds the libraries' licences").entrySet()) {
			if (path.startsWith(folder.getKey())) {
				return folder.getValue();
			}
		}
		return null;
	}

	/**
	 * A library's licence in its source: the file the build names, or the one file at the source's root named like a
	 * licence. A library without one is reported, as is one with several the build does not choose between.
	 */
	public static @Nullable Path licence(VendoredLibrary library, Path root, List<String> problems)
			throws IOException {
		String name = library.getName();
		if (library.getLicence().isPresent()) {
			Path named = root.resolve(library.getLicence().get()).normalize();
			if (!named.startsWith(root) || !Files.isRegularFile(named)) {
				problems.add(name + " has no file " + library.getLicence().get() + "; licence(...) names the file of"
						+ " its licence, from the library's root.");
				return null;
			}
			return named;
		}
		List<Path> found;
		try (Stream<Path> files = Files.list(root)) {
			found = files.filter(Files::isRegularFile)
					.filter(file -> LuaLibraries.isLicenceFile(file.getFileName().toString()))
					.sorted()
					.toList();
		}
		if (found.size() == 1) {
			return found.get(0);
		}
		problems.add(found.isEmpty()
				? name + " has no licence at its root; name its file with licence(\"...\"). A library without a licence"
						+ " cannot be vendored: nobody may pass on its code without one."
				: name + " has several files that may be its licence, " + found.stream()
						.map(file -> file.getFileName().toString())
						.collect(Collectors.joining(" and ")) + "; name the one that ships with licence(\"...\").");
		return null;
	}

	/**
	 * The files of a library that was copied into the plugin by hand, from the plugin's root: {@code <name>.lua}, the
	 * Lua under {@code <name>/}, and the licence under {@code licences/<name>/}; with the problems, no Lua where the
	 * name says, or no licence.
	 */
	public static List<String> inPlace(Path root, String name, List<String> problems) throws IOException {
		List<String> files = new ArrayList<>();
		if (Files.isRegularFile(root.resolve(name + ".lua"))) {
			files.add(name + ".lua");
		}
		Path folder = root.resolve(name);
		if (Files.isDirectory(folder)) {
			for (String path : luaFiles(folder)) {
				files.add(name + "/" + path);
			}
		}
		if (files.isEmpty()) {
			problems.add(name + " is named as a library copied into the plugin, but there is no " + name
					+ ".lua and no Lua under " + name + "/ at the plugin's root; copy the library there as its"
					+ " documentation lays it out, or name where to copy it from, local(file(\"...\")) or"
					+ " remote(\"<url>\", \"<commit>\").");
			return files;
		}
		Path licences = root.resolve(LICENCES + name);
		List<Path> found = List.of();
		if (Files.isDirectory(licences)) {
			try (Stream<Path> candidates = Files.list(licences)) {
				found = candidates.filter(Files::isRegularFile)
						.filter(file -> LuaLibraries.isLicenceFile(file.getFileName().toString()))
						.sorted()
						.toList();
			}
		}
		if (found.isEmpty()) {
			problems.add(name + " has no licence file under " + LICENCES + name + "/, such as LICENSE or LICENCE.md;"
					+ " copy the library's licence there. Nobody may pass on its code without one, and moderators"
					+ " look for it.");
		}
		for (Path licence : found) {
			files.add(LICENCES + name + "/" + licence.getFileName());
		}
		return files;
	}
}
