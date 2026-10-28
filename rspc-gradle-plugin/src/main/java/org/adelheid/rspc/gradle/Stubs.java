package org.adelheid.rspc.gradle;

import java.io.File;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * Where the RS3 API's definitions come from, LuaCATS files that describe the client's API for the Lua Language Server:
 * rs3-api-types, or any other repository or checkout of such files. A checkout on this machine, or a repository on
 * GitHub, Codeberg or GitLab that the build fetches into Gradle's cache, at a pinned commit or at the latest commit of
 * its default branch, checked for at most once a day. The definition files are found in {@code generated/library}, as
 * rs3-api-types lays them out, in {@code library}, as an LLS-Addons repository does, or at the root; {@link #from}
 * names the folder where they are anywhere else.
 */
public abstract class Stubs {

	public Stubs() {
		getFrom().convention("");
	}

	public abstract DirectoryProperty getLocal();

	public abstract Property<String> getRemote();

	public abstract Property<String> getCommit();

	/** The folder of the repository that holds the definition files, from its root; found by its layout if empty. */
	public abstract Property<String> getFrom();

	/** A checkout of the definitions on this machine. */
	public void local(File directory) {
		getLocal().set(directory);
	}

	/** The folder of the repository whose files are the definitions, from its root, such as {@code types/rs3}. */
	public void from(String folder) {
		getFrom().set(folder);
	}

	/** A repository on GitHub, Codeberg or GitLab, at the latest commit of its default branch. */
	public void remote(String url) {
		getRemote().set(url);
	}

	/** A repository on GitHub, Codeberg or GitLab at a commit, which keeps builds the same on every machine. */
	public void remote(String url, String commit) {
		getRemote().set(url);
		getCommit().set(commit);
	}
}
