package org.adelheid.rspc.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.adelheid.rspc.gradle.remote.LatestStubsCommit;
import org.adelheid.rspc.gradle.remote.RemoteRepository;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;

/**
 * Where the stubs the build names are found: a checkout on this machine, or a remote commit fetched into Gradle's
 * cache. Shared by the tasks that read the stubs, in this plugin and in builds that extend it, each with the commit
 * its release was tested against.
 */
public final class StubsSource {

	/** The repository of the RS3 API's definitions. */
	public static final String DEFAULT_REMOTE = "https://github.com/adelheidsoftware/rs3-api-types";

	private StubsSource() {
	}

	/** Where fetched repositories, the stubs and the libraries, are kept, by commit, in Gradle's user home. */
	public static File cache(Project project) {
		return new File(project.getGradle().getGradleUserHomeDir(), "caches/rspc/repositories");
	}

	/**
	 * The latest commit of the remote's default branch, looked up at most once a day, and now when the build runs a
	 * task whose name ends in {@code refreshTask}; an offline build uses the commit it last looked up.
	 */
	public static Provider<String> latestCommit(Project project, Stubs stubs, File cache, String refreshTask) {
		boolean refresh = project.getGradle().getStartParameter().getTaskNames().stream()
				.anyMatch(name -> name.endsWith(refreshTask));
		boolean offline = project.getGradle().getStartParameter().isOffline();
		return project.getProviders().of(LatestStubsCommit.class, spec -> {
			spec.getParameters().getRemote().set(stubs.getRemote());
			spec.getParameters().getCache().set(cache);
			spec.getParameters().getOffline().set(offline);
			spec.getParameters().getRefresh().set(refresh);
		});
	}

	/**
	 * The stubs folder: the local checkout, or the remote commit's files, fetched into the cache when missing.
	 *
	 * @param local a checkout on this machine, or null
	 * @param remote a repository on GitHub, Codeberg or GitLab, or null
	 * @param commit the remote's commit, pinned or the latest
	 * @param cache where fetched repositories are kept
	 * @param block the name of the build's block, for the messages: {@code rspc}
	 * @param testedCommit the commit of {@link #DEFAULT_REMOTE} the build's tooling was tested against, which another
	 *     commit of that repository is warned about, or null; definitions from anywhere else get no warning
	 * @param logger where to say which stubs are used, or null to say nothing
	 */
	public static Path resolve(@Nullable Path local, @Nullable String remote, @Nullable String commit, Path cache,
			String block, @Nullable String testedCommit, @Nullable Logger logger) {
		if (local != null) {
			String origin = RemoteRepository.localOrigin(local);
			if (logger != null && origin != null && isTestedRepository(origin)) {
				warnIfUntested(logger, RemoteRepository.localCommit(local), local.toString(), testedCommit);
			}
			return local;
		}
		if (remote == null) {
			throw new GradleException("""
					The build reads the RS3 API's definitions from a repository of LuaCATS files, such as \
					rs3-api-types, and fetches nothing it is not told to. Name where they come from:

					    %s {
					        stubs {
					            remote("%s", "%s")
					            // or a checkout on this machine: local(file("../rs3-api-types"))
					        }
					    }""".formatted(block, DEFAULT_REMOTE, testedCommit == null ? "<commit>" : testedCommit));
		}
		if (commit == null) {
			throw new GradleException("The build names the stubs' repository " + remote + " but no commit of it.");
		}
		if (logger != null) {
			logger.lifecycle("The RS3 API's definitions are those of {} at commit {}.", remote, commit);
			if (isTestedRepository(remote)) {
				warnIfUntested(logger, commit, remote, testedCommit);
			}
		}
		try {
			return RemoteRepository.parse(remote).checkout(commit, cache);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Whether an address names the repository the tested commit belongs to, however it is written. */
	private static boolean isTestedRepository(String url) {
		try {
			return RemoteRepository.parse(url).equals(RemoteRepository.parse(DEFAULT_REMOTE));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static void warnIfUntested(Logger logger, @Nullable String commit, String source,
			@Nullable String testedCommit) {
		if (commit != null && testedCommit != null && !commit.equals(testedCommit)) {
			logger.warn("The stubs in {} are at commit {}, but this build's tooling was tested against {}; what it"
					+ " knows of the API may not match them. Pin the tested commit to be sure.", source, commit,
					testedCommit);
		}
	}
}
