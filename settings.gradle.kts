pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	// Downloads the JDK that the modules compile with on machines that have none installed
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}
}

rootProject.name = "rspc-sdk"

// The Gradle plugin's modules, each folder named after its artifact, org.adelheid:<folder>, so that a build that
// includes this one resolves them from it. The template and the docs are not Gradle projects.
include(
	"rspc-plugin-model",
	"rspc-lua-runner",
	"rspc-gradle-plugin",
)
