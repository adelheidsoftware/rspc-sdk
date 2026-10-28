package org.adelheid.rspc.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Builds a small plugin written in Lua with the Gradle plugin, as its author would: the SDK resolved from a Maven
 * repository, the API's definitions from the bindings generator's stubs fixture, and every task from the checks to the
 * zip, the link and the Lua tests. It runs with the configuration cache.
 */
class RspcPluginFunctionalTest {

	@TempDir
	Path project;

	private void write(String path, String text) throws IOException {
		Path file = project.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	private void plugin() throws IOException {
		String repository = Path.of(System.getProperty("rspc.repository")).toUri().toString();
		String stubs = System.getProperty("rspc.stubsFixture").replace('\\', '/');
		write("settings.gradle.kts", """
				pluginManagement {
				    repositories {
				        maven { url = uri("%s") }
				        gradlePluginPortal()
				    }
				}

				rootProject.name = "herb-tracker"
				""".formatted(repository));
		buildFile("local(file(\"" + stubs + "\"))");
		write("main.lua", """
				-- Entry point: counts the server ticks of the session.

				local Tally = require("src/tally")

				local tally = Tally.new()

				Event.ServerLogic.Subscribe("herb-tracker.tick", function()
				    tally:add(1)
				end)

				-- Development-only code lives in dev/, which the package leaves out.
				require("dev/init")

				-- The client requires this hook to be global.
				function PluginShutdown()
				    Event.ServerLogic.Unsubscribe("herb-tracker.tick")
				end
				""");
		write("plugin.json", """
				{
				    "name": "Herb Tracker",
				    "version": "1.2.0",
				    "contactEmailAddress": "plugins@example.org",
				    "requiredDependencies": {},
				    "optionalPermissions": [ "chatAccess" ],
				    "hasSettingsIcon": true
				}
				""");
		write("src/tally.lua", """
				-- src/tally.lua
				-- A running total.

				local Tally = {}
				Tally.__index = Tally

				function Tally.new()
				    return setmetatable({ total = 0 }, Tally)
				end

				function Tally:add(amount)
				    self.total = self.total + amount
				end

				function Tally:sum()
				    return self.total
				end

				return Tally
				""");
		write("dev/init.lua", """
				-- Development only: teleports to the herb patch when the plugin starts.
				ExecuteConsoleCommand("!tele 3058 3311 0")

				return {}
				""");
		write("tests/tally_test.lua", """
				local Tally = require("src/tally")

				local tally = Tally.new()
				tally:add(2)
				tally:add(3)
				assert(tally:sum() == 5, "the total of what was added")
				""");
		write("README.md", "# Herb Tracker\n");
		Files.createDirectories(project.resolve("resources/icons"));
		Files.write(project.resolve("resources/icons/herb.png"), png());
	}

	/** The build file: the plugin from the repository the modules were published to, and the stubs block's lines. */
	private void buildFile(String stubs) throws IOException {
		buildFile(stubs, "");
	}

	/** The build file, with more lines of the {@code rspc { }} block after the stubs block. */
	private void buildFile(String stubs, String more) throws IOException {
		String repository = Path.of(System.getProperty("rspc.repository")).toUri().toString();
		write("build.gradle.kts", """
				plugins {
				    id("org.adelheid.rspc") version "%s"
				}

				repositories {
				    maven { url = uri("%s") }
				    mavenCentral()
				}

				rspc {
				    stubs {
				        %s
				    }
				    %s
				}
				""".formatted(System.getProperty("rspc.version"), repository, stubs, more));
	}

	/** The start of a 16 by 16 PNG: what checkPlugin reads of an image. */
	private static byte[] png() {
		ByteBuffer png = ByteBuffer.allocate(24);
		png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'});
		png.putInt(16).putInt(16);
		return png.array();
	}

	private GradleRunner gradle(String... arguments) {
		List<String> all = new ArrayList<>(List.of(arguments));
		all.add("--configuration-cache");
		all.add("--stacktrace");
		return GradleRunner.create().withProjectDir(project.toFile()).withArguments(all).forwardOutput();
	}

	@Test
	void buildsThePluginAsItShips() throws IOException {
		plugin();
		BuildResult result = gradle("build").build();
		assertEquals(TaskOutcome.SUCCESS, outcome(result, ":checkPlugin"));
		assertEquals(TaskOutcome.SUCCESS, outcome(result, ":testLua"));
		assertTrue(result.getOutput().contains("PASS tally_test.lua"), result.getOutput());

		// The shipped folder holds the four things the client loads, and not the dev folder nor the tests
		Path shipped = project.resolve("build/rspc/plugin");
		assertTrue(Files.isRegularFile(shipped.resolve("src/tally.lua")));
		assertFalse(Files.exists(shipped.resolve("dev")));
		assertFalse(Files.exists(shipped.resolve("tests")));
		assertFalse(Files.exists(shipped.resolve("README.md")));

		Path zip = project.resolve("build/distributions/herb-tracker-1.2.0.zip");
		assertEquals(List.of("main.lua", "plugin.json", "resources/", "resources/icons/", "resources/icons/herb.png",
				"src/", "src/tally.lua"), entries(zip));

		// The same sources give the same bytes
		byte[] first = Files.readAllBytes(zip);
		gradle("clean", "packagePlugin").build();
		Path kept = project.resolve("first.zip");
		Files.write(kept, first);
		assertEquals(-1, Files.mismatch(zip, kept));
	}

	@Test
	void fetchesTheApisDefinitionsForTheEditor() throws IOException {
		plugin();
		BuildResult result = gradle("fetchStubs").build();
		assertEquals(TaskOutcome.SUCCESS, outcome(result, ":fetchStubs"));
		assertTrue(Files.isRegularFile(project.resolve("build/rspc/stubs/library/base.lua")));
		assertTrue(Files.isRegularFile(project.resolve("build/rspc/stubs/constants/NPC.csv")));
		assertTrue(result.getOutput().contains("The RS3 API's definitions are in"), result.getOutput());
	}

	@Test
	void shipsALibraryCopiedInByHand() throws IOException {
		plugin();
		String stubs = "local(file(\"" + System.getProperty("rspc.stubsFixture").replace('\\', '/') + "\"))";
		// A library laid out as its documentation says, a file and a folder of its name, with its licence under
		// licences/ and a README that does not ship. Its Lua may name members of permissions the plugin lacks.
		write("zyklib.lua", "local array = require(\"zyklib/array\")\nreturn { array = array }\n");
		write("zyklib/array.lua", "local function fetch() return HTTPRequest.new(nil, \"/\") end\nreturn {}\n");
		write("zyklib/README.md", "# Zyklib\n");
		write("licences/zyklib/LICENCE.md", "MIT License\n");
		write("main.lua", Files.readString(project.resolve("main.lua")).replace("local Tally = require(\"src/tally\")",
				"local Tally = require(\"src/tally\")\nlocal zyklib = require(\"zyklib\")"));
		buildFile(stubs, "libraries { vendored(\"zyklib\") }");
		BuildResult result = gradle("build").build();
		assertEquals(TaskOutcome.SUCCESS, outcome(result, ":checkPlugin"));
		// The zip's reproducible order puts the root's files before its folders
		assertEquals(List.of("main.lua", "plugin.json", "zyklib.lua", "licences/", "licences/zyklib/",
				"licences/zyklib/LICENCE.md", "resources/", "resources/icons/", "resources/icons/herb.png", "src/",
				"src/tally.lua", "zyklib/", "zyklib/array.lua"),
				entries(project.resolve("build/distributions/herb-tracker-1.2.0.zip")));

		// Without its licence, nothing may pass the library on
		Files.delete(project.resolve("licences/zyklib/LICENCE.md"));
		String output = gradle("checkPlugin").buildAndFail().getOutput();
		assertTrue(output.contains("zyklib has no licence file under licences/zyklib/, such as LICENSE or LICENCE.md"),
				output);

		// A library the plugin never requires would be read by moderators for nothing
		write("licences/zyklib/LICENCE.md", "MIT License\n");
		write("main.lua", Files.readString(project.resolve("main.lua")).replace("local zyklib = require(\"zyklib\")\n",
				""));
		output = gradle("checkPlugin").buildAndFail().getOutput();
		assertTrue(output.contains("zyklib is vendored, but no Lua of the plugin's requires it"), output);
	}

	@Test
	void vendorsALibraryFromACheckout() throws IOException {
		plugin();
		String stubs = "local(file(\"" + System.getProperty("rspc.stubsFixture").replace('\\', '/') + "\"))";
		// A checkout of a library, laid out as libraries are: its Lua under src/, its licence and docs at its root
		write("libs/gauges/src/gauges.lua", "local bar = require(\"gauges/bar\")\nreturn { bar = bar }\n");
		write("libs/gauges/src/gauges/bar.lua", "return {}\n");
		write("libs/gauges/LICENSE", "MIT License\n");
		write("libs/gauges/README.md", "# Gauges\n");
		write("main.lua", Files.readString(project.resolve("main.lua")).replace("local Tally = require(\"src/tally\")",
				"local Tally = require(\"src/tally\")\nlocal gauges = require(\"gauges\")"));
		buildFile(stubs, "libraries { vendored(\"gauges\") { local(file(\"libs/gauges\")); from(\"src\") } }");
		BuildResult result = gradle("build").build();
		assertEquals(TaskOutcome.SUCCESS, outcome(result, ":vendorLibraries"));
		assertEquals(TaskOutcome.SUCCESS, outcome(result, ":checkPlugin"));
		// The library is in the plugin's folder, where the client loads it, and in the zip
		assertTrue(Files.isRegularFile(project.resolve("gauges.lua")));
		assertTrue(Files.isRegularFile(project.resolve("gauges/bar.lua")));
		assertTrue(Files.isRegularFile(project.resolve("licences/gauges/LICENSE")));
		assertFalse(Files.exists(project.resolve("README.md").resolveSibling("gauges/README.md")));
		assertEquals(List.of("gauges.lua", "main.lua", "plugin.json", "gauges/", "gauges/bar.lua", "licences/",
				"licences/gauges/", "licences/gauges/LICENSE", "resources/", "resources/icons/",
				"resources/icons/herb.png", "src/", "src/tally.lua"),
				entries(project.resolve("build/distributions/herb-tracker-1.2.0.zip")));

		// A file the library no longer has goes from the plugin's folder too
		Files.delete(project.resolve("libs/gauges/src/gauges/bar.lua"));
		write("libs/gauges/src/gauges.lua", "return {}\n");
		gradle("vendorLibraries").build();
		assertFalse(Files.exists(project.resolve("gauges/bar.lua")));
		assertFalse(Files.exists(project.resolve("gauges")));

		// A source without a licence cannot be vendored
		Files.delete(project.resolve("libs/gauges/LICENSE"));
		String output = gradle("vendorLibraries").buildAndFail().getOutput();
		assertTrue(output.contains("gauges has no licence at its root; name its file with licence(\"...\")"), output);

		// A library taken out of the build leaves the plugin's folder with every file it brought
		write("libs/gauges/LICENSE", "MIT License\n");
		gradle("vendorLibraries").build();
		assertTrue(Files.isRegularFile(project.resolve("gauges.lua")));
		buildFile(stubs);
		gradle("vendorLibraries").build();
		assertFalse(Files.exists(project.resolve("gauges.lua")));
		assertFalse(Files.exists(project.resolve("licences")));
	}

	@Test
	void takesDefinitionsFromAnyLayout() throws IOException {
		plugin();
		// A repository laid out for LLS-Addons, found by its library folder, and no constants beside it
		write("community/library/base.lua", "---@meta\nui = {}\n");
		write("community/config.json", "{ \"name\": \"RS3\" }\n");
		buildFile("local(file(\"community\"))");
		gradle("fetchStubs").build();
		assertTrue(Files.isRegularFile(project.resolve("build/rspc/stubs/library/base.lua")));
		assertFalse(Files.exists(project.resolve("build/rspc/stubs/constants")));

		// Definitions anywhere else, named with from(...)
		write("elsewhere/types/rs3/api.lua", "---@meta\nEvent = {}\n");
		buildFile("local(file(\"elsewhere\"))\n        from(\"types/rs3\")");
		gradle("fetchStubs").build();
		assertTrue(Files.isRegularFile(project.resolve("build/rspc/stubs/library/api.lua")));
		assertFalse(Files.exists(project.resolve("build/rspc/stubs/library/base.lua")));

		// A folder without definitions is refused, with the way out
		buildFile("local(file(\"elsewhere\"))\n        from(\"types\")");
		String output = gradle("fetchStubs").buildAndFail().getOutput();
		assertTrue(output.contains("has no folder types with LuaCATS definition files; from(...)"), output);
	}

	@Test
	void linksTheProjectIntoTheClient() throws IOException {
		plugin();
		Path devPlugins = Files.createDirectories(project.resolve("client/dev_plugins"));
		Files.writeString(project.resolve("build.gradle.kts"), Files.readString(project.resolve("build.gradle.kts"))
				+ "\nrspc { devPlugins = file(\"client/dev_plugins\") }\n");
		BuildResult result = gradle("linkDevPlugin").build();
		assertEquals(TaskOutcome.SUCCESS, outcome(result, ":linkDevPlugin"));
		Path link = devPlugins.resolve("herb-tracker");
		// The link leads to the project's root itself: an edit needs no build before the client reloads the plugin
		assertTrue(Files.isRegularFile(link.resolve("main.lua")));
		assertTrue(Files.isRegularFile(link.resolve("dev/init.lua")));
		assertTrue(result.getOutput().contains("prf, then pon herb-tracker"), result.getOutput());
	}

	@Test
	void refusesWhatMayNotShip() throws IOException {
		plugin();
		write("plugin.json", """
				{
				    "name": "Herb Tracker",
				    "version": "1.2.0",
				    "contactEmailAddress": "plugins@example.org",
				    "requiredDependencies": []
				}
				""");
		write("src/tally.lua", "local Tally = {}\nreturn Tally\n");
		write("src/notes.txt", "not Lua\n");
		BuildResult result = gradle("checkPlugin").buildAndFail();
		assertEquals(TaskOutcome.FAILED, outcome(result, ":checkPlugin"));
		String output = result.getOutput();
		assertTrue(output.contains("plugin.json's requiredDependencies is a JSON array"), output);
		assertTrue(output.contains("src/tally.lua does not start with its path"), output);
		assertTrue(output.contains("src/notes.txt is not part of a plugin"), output);
	}

	@Test
	void aFailingLuaTestFailsTheBuild() throws IOException {
		plugin();
		write("tests/sum_test.lua",
				"local Tally = require(\"src/tally\")\nassert(Tally.new():sum() == 1, \"empty\")\n");
		BuildResult result = gradle("testLua").buildAndFail();
		assertEquals(TaskOutcome.FAILED, outcome(result, ":testLua"));
		assertTrue(result.getOutput().contains("FAIL sum_test.lua"), result.getOutput());
		assertTrue(result.getOutput().contains("PASS tally_test.lua"), result.getOutput());
	}

	private static TaskOutcome outcome(BuildResult result, String task) {
		BuildTask ran = result.task(task);
		return ran == null ? TaskOutcome.SKIPPED : ran.getOutcome();
	}

	private static List<String> entries(Path zip) throws IOException {
		List<String> names = new ArrayList<>();
		try (ZipFile file = new ZipFile(zip.toFile())) {
			Enumeration<? extends ZipEntry> entries = file.entries();
			while (entries.hasMoreElements()) {
				names.add(entries.nextElement().getName());
			}
		}
		return names;
	}
}
