package org.adelheid.rspc.gradle.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The repositories the build fetches, by their web addresses: where each host advertises a repository's refs, where it
 * serves a commit's archive, the addresses it does not take, and the origin of a checkout on this machine. Nothing
 * here goes to the network.
 */
class RemoteRepositoryTest {

	@TempDir
	Path checkout;

	@Test
	void aCheckoutsOriginIsReadAsAWebAddress() throws IOException {
		Files.createDirectories(checkout.resolve(".git"));
		Files.writeString(checkout.resolve(".git/config"), """
				[core]
				\trepositoryformatversion = 0
				[remote "upstream"]
				\turl = https://github.com/herbs/upstream.git
				[remote "origin"]
				\turl = git@github.com:herbs/types.git
				\tfetch = +refs/heads/*:refs/remotes/origin/*
				""");
		assertEquals("https://github.com/herbs/types", RemoteRepository.localOrigin(checkout));
		Files.writeString(checkout.resolve(".git/config"), "[remote \"origin\"]\n\turl = https://codeberg.org/a/b\n");
		assertEquals("https://codeberg.org/a/b", RemoteRepository.localOrigin(checkout));
		Files.writeString(checkout.resolve(".git/config"), "[core]\n\tbare = false\n");
		assertNull(RemoteRepository.localOrigin(checkout));
		assertNull(RemoteRepository.localOrigin(checkout.resolve("missing")));
	}

	@Test
	void aGitHubRepository() {
		RemoteRepository repository = RemoteRepository.parse("https://github.com/adelheidsoftware/rs3-api-types");
		assertEquals(new RemoteRepository(RemoteRepository.Host.GITHUB, "adelheidsoftware/rs3-api-types"),
				repository);
		assertEquals("https://github.com/adelheidsoftware/rs3-api-types.git/info/refs?service=git-upload-pack",
				repository.refs().toString());
		assertEquals("https://codeload.github.com/adelheidsoftware/rs3-api-types/zip/" + commit(),
				repository.archive(commit()).toString());
		assertEquals(Path.of("cache", "github.com", "adelheidsoftware", "rs3-api-types"),
				repository.folder(Path.of("cache")));
	}

	@Test
	void aCodebergRepository() {
		RemoteRepository repository =
				RemoteRepository.parse("https://codeberg.org/ZyklopMarco/runescape-api-zyklib.git");
		assertEquals(new RemoteRepository(RemoteRepository.Host.CODEBERG, "ZyklopMarco/runescape-api-zyklib"),
				repository);
		assertEquals("https://codeberg.org/ZyklopMarco/runescape-api-zyklib.git/info/refs?service=git-upload-pack",
				repository.refs().toString());
		assertEquals("https://codeberg.org/ZyklopMarco/runescape-api-zyklib/archive/" + commit() + ".zip",
				repository.archive(commit()).toString());
	}

	/** GitLab's groups nest, so a project's path has two names or more. */
	@Test
	void aGitLabProjectInASubgroup() {
		RemoteRepository repository = RemoteRepository.parse("https://gitlab.com/herbs/lua/types/");
		assertEquals(new RemoteRepository(RemoteRepository.Host.GITLAB, "herbs/lua/types"), repository);
		assertEquals("https://gitlab.com/herbs/lua/types.git/info/refs?service=git-upload-pack",
				repository.refs().toString());
		assertEquals("https://gitlab.com/herbs/lua/types/-/archive/" + commit() + "/types-" + commit() + ".zip",
				repository.archive(commit()).toString());
	}

	@Test
	void anotherHostIsRefused() {
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
				() -> RemoteRepository.parse("https://bitbucket.org/herbs/types"));
		String message = String.valueOf(refused.getMessage());
		assertTrue(message.startsWith("https://bitbucket.org/herbs/types is not the address of a repository the build"
				+ " can fetch: it fetches from GitHub, https://github.com/<owner>/<repository>, Codeberg,"), message);
		assertThrows(IllegalArgumentException.class, () -> RemoteRepository.parse("http://github.com/herbs/types"));
	}

	/** A page of a repository, rather than the repository's own address, is not taken for another repository. */
	@Test
	void aPageOfARepositoryIsRefused() {
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
				() -> RemoteRepository.parse("https://github.com/herbs/types/tree/main"));
		assertEquals("https://github.com/herbs/types/tree/main is not the address of a repository on GitHub: give the"
				+ " repository's own page, https://github.com/<owner>/<repository>.", refused.getMessage());
		assertThrows(IllegalArgumentException.class,
				() -> RemoteRepository.parse("https://gitlab.com/herbs/types/-/tree"));
		assertThrows(IllegalArgumentException.class, () -> RemoteRepository.parse("https://codeberg.org/herbs"));
	}

	private static String commit() {
		return "22dcf36a36db1879798d96eec1a5528cc996b168";
	}
}
