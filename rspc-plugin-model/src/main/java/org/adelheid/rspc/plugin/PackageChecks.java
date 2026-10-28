package org.adelheid.rspc.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The checks {@code checkPlugin} runs on the folder that ships, before it is zipped. Its {@code plugin.json} says
 * what the client wants, and every dependency it names has a Mod.io ID, by which players' clients find it. The folder
 * may hold only {@code main.lua}, {@code plugin.json}, Lua files under {@code src/}, Lua files directly in
 * {@code utils/}, PNG or Ogg files under {@code resources/}, and the files of the libraries it vendors, their Lua and
 * their licences. Every Lua file under {@code src/} or in {@code utils/} starts with its path, by which the client's
 * errors name it; every {@code require} names a file in the folder, but for one of a module under {@code dev/}, the
 * development-only folder that never ships, which the client's {@code require} finds nothing for in a release; each
 * library is required by the plugin's own Lua, since moderators would read its code for nothing otherwise; no image
 * is larger than the client loads; and no
 * Lua uses a member of the client's that is for development only, such as {@code ExecuteConsoleCommand}, the client's
 * way into the console's cheats, nor, but in a library, one that needs a permission the manifest lacks. A library's
 * code may name members of every permission, in parts the plugin does not call, so the permissions are the plugin's
 * own Lua's to check.
 */
public final class PackageChecks {

	/** The largest image the client loads, in either dimension. */
	private static final int MAX_IMAGE_SIZE = 1024;

	private static final Pattern REQUIRE = Pattern.compile("\\brequire\\s*\\(?\\s*([\"'])([^\"']*)\\1");
	/** The development-only folder, whose modules a plugin may require and find nothing for in a release. */
	private static final String DEVELOPMENT = "dev/";
	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
	private static final byte[] OGG = {'O', 'g', 'g', 'S'};

	private PackageChecks() {
	}

	/**
	 * What is wrong with the folder, each as a sentence naming the file; empty when it may ship.
	 *
	 * @param pluginId the plugin's identifier, the folder's name in the client and the zip's
	 * @param facts the client's members that need a permission or are for development only, and its globals
	 * @param libraries the libraries the plugin vendors and ships
	 */
	public static List<String> check(Path folder, String pluginId, ClientFacts facts,
			List<LuaLibraries.Library> libraries) throws IOException {
		// Each vendored file by the library it comes from
		Map<String, String> vendored = new HashMap<>();
		libraries.forEach(library -> library.files().forEach(path -> vendored.put(path, library.name())));
		List<String> problems = new ArrayList<>();
		for (String required : List.of("main.lua", "plugin.json")) {
			if (!Files.isRegularFile(folder.resolve(required))) {
				problems.add("The plugin has no " + required + ", which the client needs at the top of the folder.");
			}
		}
		Set<String> permissions = manifestProblems(folder, pluginId, facts, problems);
		List<Path> files;
		try (Stream<Path> walk = Files.walk(folder)) {
			files = walk.filter(Files::isRegularFile).sorted().toList();
		}
		for (Path file : files) {
			String path = folder.relativize(file).toString().replace('\\', '/');
			if (path.equals("main.lua") || path.equals("plugin.json") || vendored.containsKey(path)) {
				continue;
			}
			if ((path.startsWith("src/") && path.endsWith(".lua")) || isHelper(path)) {
				continue;
			}
			if (path.startsWith("resources/")) {
				resourceProblems(file, path, problems);
				continue;
			}
			problems.add(path + " is not part of a plugin: only main.lua, plugin.json, Lua files under src/, Lua"
					+ " files in utils/, PNG or Ogg files under resources/, and the files of the libraries the build"
					+ " vendors ship.");
		}
		// The files the plugin's own Lua requires, which a library must be among
		Set<String> required = new HashSet<>();
		for (Path file : files) {
			String path = folder.relativize(file).toString().replace('\\', '/');
			if (path.endsWith(".lua")) {
				Set<String> requires = luaProblems(folder, file, path, vendored.containsKey(path), facts, permissions,
						problems);
				if (!vendored.containsKey(path)) {
					required.addAll(requires);
				}
			}
		}
		for (LuaLibraries.Library library : libraries) {
			if (library.files().stream().noneMatch(required::contains)) {
				problems.add(library.name() + " is vendored, but no Lua of the plugin's requires it, so moderators"
						+ " would read its code for nothing; use it, or remove it from the build.");
			}
		}
		return problems;
	}

	/**
	 * The problems of the manifest, where the folder has one: its shape, what the client or Mod.io would refuse, and
	 * each dependency that names only a folder on this machine; and the permissions it requests.
	 */
	private static Set<String> manifestProblems(Path folder, String pluginId, ClientFacts facts,
			List<String> problems) throws IOException {
		Path file = folder.resolve("plugin.json");
		if (!Files.isRegularFile(file)) {
			return Set.of();
		}
		Manifest.Parsed parsed = Manifest.parse(pluginId, Files.readString(file, StandardCharsets.UTF_8));
		problems.addAll(parsed.problems());
		Manifest manifest = parsed.manifest();
		if (manifest == null) {
			return Set.of();
		}
		problems.addAll(manifest.problems(facts.globals()));
		manifest.unpublishedDependencies().forEach((alias, kind) -> problems.add("The " + kind + " dependency "
				+ alias + " names a folder on this machine rather than a Mod.io ID, so the plugin cannot ship:"
				+ " players' clients find a dependency by its Mod.io ID. Once the library is published, name its ID;"
				+ " the development folder's manifest may still name the folder."));
		return manifest.permissions();
	}

	/**
	 * The problems of a Lua file, and the files it requires.
	 *
	 * @param vendored whether the file is a library's, which need not start with its path, and may name members of
	 *     permissions the manifest lacks
	 */
	private static Set<String> luaProblems(Path folder, Path file, String path, boolean vendored, ClientFacts facts,
			Set<String> permissions, List<String> problems) throws IOException {
		String text = Files.readString(file, StandardCharsets.UTF_8);
		boolean startsWithPath = text.startsWith("-- " + path + "\n") || text.startsWith("-- " + path + "\r");
		if ((path.startsWith("src/") || isHelper(path)) && !vendored && !startsWithPath) {
			problems.add(path + " does not start with its path, -- " + path
					+ ", by which the client's errors name it.");
		}
		Set<String> requires = new HashSet<>();
		Matcher required = REQUIRE.matcher(text);
		while (required.find()) {
			String module = required.group(2) + ".lua";
			requires.add(module);
			if (!Files.isRegularFile(folder.resolve(module)) && !module.startsWith(DEVELOPMENT)) {
				problems.add(path + " requires \"" + required.group(2) + "\", which is not in the plugin.");
			}
		}
		// A member named in a comment or a string, such as a header that says "no HTTPRequest", is not used
		String code = code(text);
		for (Map.Entry<String, String> needed : facts.permissions().entrySet()) {
			if (!vendored && !permissions.contains(needed.getValue()) && uses(code, needed.getKey())) {
				problems.add(path + " uses " + needed.getKey() + ", which needs the " + needed.getValue()
						+ " permission the manifest does not declare; declare it, preferably in optionalPermissions.");
			}
		}
		for (String devOnly : facts.devOnly()) {
			if (uses(code, devOnly)) {
				problems.add(path + " uses " + devOnly + ", which is for development only and must never ship.");
			}
		}
		return requires;
	}

	/**
	 * The Lua with its comments and string literals blanked out, so that only code is left to search: line comments,
	 * block comments and long strings with their level of equals signs, and quoted strings with their escapes.
	 */
	static String code(String text) {
		StringBuilder code = new StringBuilder(text.length());
		int at = 0;
		while (at < text.length()) {
			char c = text.charAt(at);
			int longEnd;
			if (text.startsWith("--", at)) {
				longEnd = longBracketEnd(text, at + 2);
				int end = longEnd >= 0 ? longEnd : text.indexOf('\n', at);
				at = end < 0 ? text.length() : end;
				code.append(' ');
			} else if ((longEnd = longBracketEnd(text, at)) >= 0) {
				at = longEnd;
				code.append(' ');
			} else if (c == '"' || c == '\'') {
				int end = at + 1;
				while (end < text.length() && text.charAt(end) != c && text.charAt(end) != '\n') {
					end += text.charAt(end) == '\\' ? 2 : 1;
				}
				at = Math.min(end + 1, text.length());
				code.append(' ');
			} else {
				code.append(c);
				at++;
			}
		}
		return code.toString();
	}

	/**
	 * Where a long bracket that opens at {@code at}, {@code [[} or {@code [==[}, closes, past its closing bracket, or
	 * the end of the text when it never closes; -1 when no long bracket opens there.
	 */
	private static int longBracketEnd(String text, int at) {
		if (at >= text.length() || text.charAt(at) != '[') {
			return -1;
		}
		int level = 0;
		while (at + 1 + level < text.length() && text.charAt(at + 1 + level) == '=') {
			level++;
		}
		if (at + 1 + level >= text.length() || text.charAt(at + 1 + level) != '[') {
			return -1;
		}
		String close = "]" + "=".repeat(level) + "]";
		int end = text.indexOf(close, at + 2 + level);
		return end < 0 ? text.length() : end + close.length();
	}

	/** Whether a file is a helper, a Lua file directly in {@code utils/}. */
	private static boolean isHelper(String path) {
		return path.startsWith("utils/") && path.endsWith(".lua") && path.indexOf('/', "utils/".length()) < 0;
	}

	/** Whether Lua code names a member by its path, as a whole: {@code HTTPRequest} in {@code HTTPRequest.new(...)}. */
	private static boolean uses(String text, String path) {
		return Pattern.compile("(?<![\\w.])" + Pattern.quote(path) + "(?!\\w)").matcher(text).find();
	}

	private static void resourceProblems(Path file, String path, List<String> problems) throws IOException {
		byte[] start = new byte[24];
		int read;
		try (InputStream in = Files.newInputStream(file)) {
			read = in.readNBytes(start, 0, start.length);
		}
		byte[] header = Arrays.copyOf(start, read);
		if (path.endsWith(".png") && startsWith(header, PNG)) {
			// The width and height are the first fields of the IHDR chunk, right after the signature
			if (header.length >= 24) {
				long width = unsigned(header, 16);
				long height = unsigned(header, 20);
				if (width > MAX_IMAGE_SIZE || height > MAX_IMAGE_SIZE) {
					problems.add(path + " is " + width + " by " + height + " pixels; the client loads images up to "
							+ MAX_IMAGE_SIZE + " by " + MAX_IMAGE_SIZE + ".");
				}
			}
			return;
		}
		if (path.endsWith(".ogg") && startsWith(header, OGG)) {
			return;
		}
		problems.add(path + " is not a PNG image or an Ogg Vorbis sound, the only resources the client loads.");
	}

	private static boolean startsWith(byte[] bytes, byte[] prefix) {
		return bytes.length >= prefix.length && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
	}

	private static long unsigned(byte[] bytes, int offset) {
		return ((bytes[offset] & 0xFFL) << 24) | ((bytes[offset + 1] & 0xFFL) << 16)
				| ((bytes[offset + 2] & 0xFFL) << 8) | (bytes[offset + 3] & 0xFFL);
	}
}
