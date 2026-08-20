rootProject.name = "ParalyaBot"


pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()

		maven("https://snapshots-repo.kordex.dev")
		maven("https://releases-repo.kordex.dev")
	}
	includeBuild("build-logic")
}

// Enable type-safe project accessor
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")


include("deps")
include("common")
include("bot")
include("lg")
include("ai")
include("sta")
