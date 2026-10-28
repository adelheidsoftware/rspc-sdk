package org.adelheid.rspc.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * What the build checks against the client: the members that {@code checkPlugin} looks for in the Lua that ships,
 * those that need a permission, such as {@code Chat.AddMessage}, and those for development only, such as
 * {@code ExecuteConsoleCommand}; and the client's globals, which a dependency's alias may not take. A build that
 * generates facts of its own, from the API's definitions, writes them to a file; otherwise {@link #builtIn()} gives
 * the ones this module carries.
 *
 * @param permissions the permission each member or table needs, by its Lua path
 * @param devOnly the Lua paths of the members for development only
 * @param globals the names the client's API takes in Lua's global table: its tables, enums and functions
 */
public record ClientFacts(Map<String, String> permissions, Set<String> devOnly, Set<String> globals) {

	/** The file's name, where a build writes facts of its own. */
	public static final String FILE = "client-facts.txt";

	private static final Pattern SPACES = Pattern.compile("\\s+");

	public ClientFacts {
		permissions = Map.copyOf(permissions);
		devOnly = Set.copyOf(devOnly);
		globals = Set.copyOf(globals);
	}

	/**
	 * The facts in a file, one per line: {@code permission <path> <permission>}, {@code dev-only <path>} or
	 * {@code global <name>}.
	 */
	public static ClientFacts read(Path file) throws IOException {
		return parse(Files.readAllLines(file, StandardCharsets.UTF_8), file.toString());
	}

	/** The facts this module carries, from the client's documentation and the API's definitions. */
	public static ClientFacts builtIn() {
		try (InputStream in = Objects.requireNonNull(ClientFacts.class.getResourceAsStream(FILE), FILE)) {
			return parse(List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")), FILE);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static ClientFacts parse(List<String> lines, String source) throws IOException {
		Map<String, String> permissions = new TreeMap<>();
		Set<String> devOnly = new TreeSet<>();
		Set<String> globals = new TreeSet<>();
		for (String line : lines) {
			List<String> words = SPACES.splitAsStream(line.strip()).toList();
			if (words.size() == 3 && words.get(0).equals("permission")) {
				permissions.put(words.get(1), words.get(2));
			} else if (words.size() == 2 && words.get(0).equals("dev-only")) {
				devOnly.add(words.get(1));
			} else if (words.size() == 2 && words.get(0).equals("global")) {
				globals.add(words.get(1));
			} else if (!line.isBlank() && !line.startsWith("#")) {
				throw new IOException(source + " has a line the build does not understand: " + line);
			}
		}
		return new ClientFacts(permissions, devOnly, globals);
	}

	/** Writes the facts to a file that {@link #read} reads, in sorted order. */
	public void write(Path file) throws IOException {
		List<String> lines = new ArrayList<>(List.of("# What the build checks against the client, written from the"
				+ " API's definitions."));
		new TreeMap<>(permissions).forEach((path, permission) -> lines.add("permission " + path + " " + permission));
		new TreeSet<>(devOnly).forEach(path -> lines.add("dev-only " + path));
		new TreeSet<>(globals).forEach(name -> lines.add("global " + name));
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}
}
