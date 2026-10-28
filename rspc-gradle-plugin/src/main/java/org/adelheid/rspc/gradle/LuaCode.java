package org.adelheid.rspc.gradle;

import java.io.File;
import org.gradle.api.Named;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/**
 * Lua the build names: a checkout on this machine or a repository at a full commit, the folder of it that counts, and
 * an overrides file for a build that generates bindings from it. As a task's input, its local checkout counts by the
 * files that are read from it.
 */
public abstract class LuaCode implements Named {

	private final String name;

	protected LuaCode(String name) {
		this.name = name;
		getFrom().convention("");
	}

	@Input
	@Override
	public String getName() {
		return name;
	}

	/** A checkout on this machine. */
	@Internal
	public abstract DirectoryProperty getLocal();

	/** A repository on GitHub, Codeberg or GitLab, fetched at {@link #getCommit()}. */
	@Optional
	@Input
	public abstract Property<String> getRemote();

	/** The full commit of the remote. */
	@Optional
	@Input
	public abstract Property<String> getCommit();

	/** The folder of it, from its root, whose Lua files count; its root by default. */
	@Input
	public abstract Property<String> getFrom();

	/** An overrides file for the bindings a build generates from the code, for what its annotations do not say. */
	@Optional
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getOverrides();

	/** The code from a checkout on this machine. */
	public void local(File directory) {
		getLocal().set(directory);
	}

	/** The code from a repository on GitHub, Codeberg or GitLab, at a full commit. */
	public void remote(String url, String commit) {
		getRemote().set(url);
		getCommit().set(commit);
	}

	/** The folder of it whose Lua files count, from its root. */
	public void from(String folder) {
		getFrom().set(folder);
	}

	/** An overrides file for the bindings, for what the annotations do not say. */
	public void overrides(File file) {
		getOverrides().set(file);
	}
}
