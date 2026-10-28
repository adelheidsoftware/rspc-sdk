package org.adelheid.rspc.gradle.workers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.adelheid.rspc.runner.LuaTests;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

/** Runs the plugin's Lua tests in the worker process, through the Lua runner, and writes the report. */
public abstract class TestLuaAction implements WorkAction<TestLuaAction.Parameters> {

	private static final Logger LOGGER = Logging.getLogger(TestLuaAction.class);

	/** The Lua to use, the folders, and the report. */
	public interface Parameters extends WorkParameters {

		RegularFileProperty getLuaExecutable();

		DirectoryProperty getPluginFolder();

		DirectoryProperty getTestsFolder();

		DirectoryProperty getWorkFolder();

		RegularFileProperty getReport();
	}

	@Override
	public void execute() {
		Parameters parameters = getParameters();
		LuaTests.Outcome outcome;
		try {
			outcome = LuaTests.run(
					parameters.getLuaExecutable().isPresent()
							? parameters.getLuaExecutable().get().getAsFile().toPath()
							: null,
					parameters.getPluginFolder().get().getAsFile().toPath(),
					parameters.getTestsFolder().get().getAsFile().toPath(),
					parameters.getWorkFolder().get().getAsFile().toPath());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		String report = String.join("\n", outcome.report()) + "\n";
		Path file = parameters.getReport().get().getAsFile().toPath();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, report, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (!outcome.passed()) {
			throw new GradleException("The plugin's Lua tests failed:\n" + report.stripTrailing());
		}
		LOGGER.lifecycle(report.stripTrailing());
	}
}
