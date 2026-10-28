import net.ltgt.gradle.errorprone.errorprone

// The Gradle plugin of the RSPC SDK, org.adelheid.rspc: the rspc { } block and the tasks a plugin written in Lua needs
// around its files. It links the plugin into the client, fetches the API's definitions for the editor, runs the Lua
// tests, checks what ships and zips it for Mod.io; the shared tasks are offered to builds that extend it. Compiled
// for Java 17, the oldest JVM Gradle 9 runs on, so that a JDK is the only install a plugin's developer needs.

plugins {
	`java-gradle-plugin`
	alias(libs.plugins.plugin.publish)
}

description = "The Gradle plugin of the RSPC SDK, which builds RuneScape 3 client plugins written in Lua: links " +
	"them into the client, fetches the API's definitions, runs their tests, checks what ships and zips it for Mod.io."

dependencies {
	// The manifest, the package checks and the libraries list, which the tasks and the blocks hand out
	api(project(":rspc-plugin-model"))
	// The bundled Lua 5.4.8 the tests run on, in a worker process
	implementation(project(":rspc-lua-runner"))
	testImplementation(gradleTestKit())
}

gradlePlugin {
	website = "https://github.com/adelheidsoftware/rspc-sdk"
	vcsUrl = "https://github.com/adelheidsoftware/rspc-sdk.git"
	plugins {
		create("rspc") {
			id = "org.adelheid.rspc"
			implementationClass = "org.adelheid.rspc.gradle.RspcPlugin"
			displayName = "RSPC SDK"
			description = "Builds RuneScape 3 client plugins written in Lua: links them into the client, fetches " +
				"the API's definitions for the editor, runs their tests, checks what ships and zips it for Mod.io."
			tags = listOf("runescape", "rs3", "lua")
		}
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 17
	options.compilerArgs.add("-Xlint:deprecation")
	options.errorprone {
		// Gradle injects its services into abstract getters and abstract classes' constructors, which these checks
		// assume no injector does
		disable("JavaxInjectOnAbstractMethod", "InjectOnConstructorOfAbstractClass")
	}
}

tasks.test {
	// The functional test builds a plugin as its author would, resolving the SDK from the repository the published
	// modules go to, with the API's definitions from the bindings generator's stubs fixture
	val publishedModules = listOf("plugin-model", "lua-runner", "gradle-plugin").map { ":rspc-$it" }
	dependsOn(publishedModules.map { "$it:publishAllPublicationsToFunctionalTestRepository" })
	// The published modules are what the test builds with, so a change to any of them must rerun it
	inputs.files(publishedModules.map { project(it).tasks.named("jar") }).withPropertyName("publishedJars")
	systemProperty("rspc.repository",
		rootProject.layout.buildDirectory.dir("functional-test-repository").get().asFile.absolutePath)
	systemProperty("rspc.version", project.version.toString())
	val stubsFixture = file("src/test/stubs")
	systemProperty("rspc.stubsFixture", stubsFixture.absolutePath)
	inputs.dir(stubsFixture).withPropertyName("stubsFixture")
}
