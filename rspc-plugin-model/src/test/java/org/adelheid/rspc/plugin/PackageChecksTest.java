package org.adelheid.rspc.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The checks of the folder that ships. */
class PackageChecksTest {

	private static final ClientFacts FACTS = new ClientFacts(
			Map.of("Chat.AddMessage", "chatAccess", "HTTPRequest", "httpAccess"), Set.of("ExecuteConsoleCommand"),
			Set.of("Chat", "ExecuteConsoleCommand", "HTTPRequest"));

	@TempDir
	Path folder;

	private void write(String path, String text) throws IOException {
		Path file = folder.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, text);
	}

	private void writePng(String path, int width, int height) throws IOException {
		ByteBuffer png = ByteBuffer.allocate(24);
		png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'});
		png.putInt(width).putInt(height);
		Path file = folder.resolve(path);
		Files.createDirectories(file.getParent());
		Files.write(file, png.array());
	}

	@Test
	void aPluginAsItShipsPasses() throws IOException {
		write("main.lua", """
				local Tally = require("src/tally")
				local Levels = require('src/core/levels')
				local zyklib = require("zyklib")
				local trim = require("utils/trim").trim
				Chat.AddMessage("hello")
				-- The development-only folder never ships, so this finds nothing in a release
				require("dev/init")
				-- What this plugin touches: no HTTPRequest, and it never calls ExecuteConsoleCommand
				--[[ Nor here: HTTPRequest.new(Plugin.urls.prices, "/") ]]
				local note = "HTTPRequest is a member of the client's; so is ExecuteConsoleCommand"
				local more = [==[ ExecuteConsoleCommand("!tele 3200 3200") ]==]
				local quoted = 'an escaped quote \\' and HTTPRequest after it'
				""");
		write("plugin.json", """
				{
				    "name": "Herb Tracker",
				    "version": "1.2.0",
				    "contactEmailAddress": "plugins@example.org",
				    "requiredDependencies": {},
				    "optionalPermissions": [ "chatAccess" ]
				}
				""");
		write("src/tally.lua", "-- src/tally.lua\nreturn {}\n");
		write("utils/trim.lua", "-- utils/trim.lua\n-- A helper.\n\nreturn { trim = string.match }\n");
		write("src/core/levels.lua", "-- src/core/levels.lua\r\n-- With Windows line ends.\r\n");
		writePng("resources/icons/herb.png", 32, 32);
		Files.write(folder.resolve("resources/ding.ogg"), new byte[] {'O', 'g', 'g', 'S', 0});
		// A library's files need not start with their paths, and may name members of permissions the plugin lacks
		write("zyklib.lua", "local array = require \"zyklib/array\"\nreturn { array = array }\n");
		write("zyklib/array.lua", "local function fetch() return HTTPRequest.new(nil, \"/\") end\nreturn {}\n");
		write("licences/zyklib/LICENCE.md", "MIT License\n");
		LuaLibraries.Library zyklib = new LuaLibraries.Library("zyklib", LuaLibraries.Use.VENDORED, false,
				List.of("zyklib.lua", "zyklib/array.lua", "licences/zyklib/LICENCE.md"));
		assertEquals(List.of(), PackageChecks.check(folder, "herb-tracker", FACTS, List.of(zyklib)));
	}

	@Test
	void everyProblemIsNamed() throws IOException {
		write("main.lua", "local Missing = require(\"src/missing\")\nExecuteConsoleCommand(\"!tele 3200 3200\")\n");
		write("src/fetch.lua", "local Gone = require('src/gone')\nHTTPRequest.new(Plugin.urls.prices, \"/\")\n");
		write("README.md", "notes\n");
		write("resources/notes.txt", "not an image\n");
		writePng("resources/huge.png", 2048, 16);
		write("utils/nested/round.lua", "-- utils/nested/round.lua\nreturn math.floor\n");
		write("utils/split.lua", "return string.gmatch\n");
		write("json.lua", "ExecuteConsoleCommand(\"!give 995 1000\")\nreturn {}\n");
		write("licences/json/LICENSE", "MIT License\n");
		LuaLibraries.Library json = new LuaLibraries.Library("json", LuaLibraries.Use.VENDORED, false,
				List.of("json.lua", "licences/json/LICENSE"));
		assertEquals(List.of(
				"The plugin has no plugin.json, which the client needs at the top of the folder.",
				"README.md is not part of a plugin: only main.lua, plugin.json, Lua files under src/, Lua files in"
						+ " utils/, PNG or Ogg files under resources/, and the files of the libraries the build"
						+ " vendors ship.",
				"resources/huge.png is 2048 by 16 pixels; the client loads images up to 1024 by 1024.",
				"resources/notes.txt is not a PNG image or an Ogg Vorbis sound, the only resources the client loads.",
				"utils/nested/round.lua is not part of a plugin: only main.lua, plugin.json, Lua files under src/, Lua"
						+ " files in utils/, PNG or Ogg files under resources/, and the files of the libraries the"
						+ " build vendors ship.",
				"json.lua uses ExecuteConsoleCommand, which is for development only and must never ship.",
				"main.lua requires \"src/missing\", which is not in the plugin.",
				"main.lua uses ExecuteConsoleCommand, which is for development only and must never ship.",
				"src/fetch.lua does not start with its path, -- src/fetch.lua, by which the client's errors name it.",
				"src/fetch.lua requires \"src/gone\", which is not in the plugin.",
				"src/fetch.lua uses HTTPRequest, which needs the httpAccess permission the manifest does not declare;"
						+ " declare it, preferably in optionalPermissions.",
				"utils/split.lua does not start with its path, -- utils/split.lua, by which the client's errors name"
						+ " it.",
				"json is vendored, but no Lua of the plugin's requires it, so moderators would read its code for"
						+ " nothing; use it, or remove it from the build."),
				PackageChecks.check(folder, "herb-tracker", FACTS, List.of(json)));
	}

	@Test
	void onlyCodeIsSearchedForMembers() {
		// A long string and a block comment each take their line breaks with them
		assertEquals("local x =    \nlocal y =    \nHTTPRequest.new()\nlocal z =  \n", PackageChecks.code("""
				local x = "HTTPRequest" -- HTTPRequest
				local y = [[HTTPRequest
				]] --[==[ HTTPRequest
				]==]
				HTTPRequest.new()
				local z = 'it\\'s HTTPRequest'
				"""));
	}

	@Test
	void theManifestIsChecked() throws IOException {
		write("main.lua", "log(\"hello\")\n");
		write("plugin.json", """
				{
				    "name": "Herb Tracker",
				    "version": "1.2.0",
				    "requiredDependencies": [],
				    "optionalDependencies": { "rsprite": "rsprite", "Chat": 12 }
				}
				""");
		assertEquals(List.of(
				"plugin.json's requiredDependencies is a JSON array, [ ], which the client refuses with \"unexpected"
						+ " json Array, expected a json Object\"; write an object, {} when there are none, with each"
						+ " dependency's alias and Mod.io ID: { \"prettyui\": 6398181 }.",
				"The plugin id \"Herb Tracker\" must be lowercase letters and digits, with words joined by hyphens,"
						+ " such as herb-tracker; it names the dev link and the zip.",
				"The plugin needs a contact address, which the legal sections require, in the manifest's"
						+ " contactEmailAddress; use one that is not a Mod.io or RuneScape login.",
				"The optional dependency's alias Chat is a global of the client's already, and the client exposes the"
						+ " dependency as the global of its alias; choose another, such as the library's name.",
				"The optional dependency rsprite names a folder on this machine rather than a Mod.io ID, so the plugin"
						+ " cannot ship: players' clients find a dependency by its Mod.io ID. Once the library is"
						+ " published, name its ID; the development folder's manifest may still name the folder."),
				PackageChecks.check(folder, "Herb Tracker", FACTS, List.of()));
	}
}
