package org.adelheid.rspc.gradle.tasks;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.adelheid.rspc.gradle.StubsSource;
import org.adelheid.rspc.gradle.remote.RemoteRepository;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * {@code updateStubs}: looks up the latest commit of the stubs' repository and says how to pin it in the build, so that
 * moving the API's definitions on after a game update is a deliberate, visible change.
 */
@UntrackedTask(because = "It asks the repository's host each time, and changes nothing in the build.")
public abstract class UpdateStubs extends DefaultTask {

	@Internal
	public abstract Property<String> getRemote();

	@Internal
	public abstract Property<String> getCommit();

	/** The name of the build's block, for the messages. */
	@Internal
	public abstract Property<String> getBlock();

	@TaskAction
	public void update() {
		String remote = getRemote().getOrElse(StubsSource.DEFAULT_REMOTE);
		String latest;
		try {
			latest = RemoteRepository.parse(remote).latestCommit();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		String pinned = getCommit().getOrNull();
		if (latest.equals(pinned)) {
			getLogger().lifecycle("The build pins the latest commit of {} already, {}.", remote, latest);
			return;
		}
		getLogger().lifecycle("The latest commit of {} is {}{}. To move to it, pin it in the build, then run"
				+ " fetchStubs:\n\n    {} {\n        stubs { remote(\"{}\", \"{}\") }\n    }", remote, latest,
				pinned == null ? "" : ", and the build pins " + pinned, getBlock().get(), remote, latest);
	}
}
