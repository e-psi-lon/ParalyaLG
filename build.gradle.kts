val excludedDependencies = listOf(
	"net.fellbaum" to "jemoji",
	"commons-validator" to "commons-validator",
	// Kept until KordEx abstracts it away
	// "io.sentry" to "sentry"
)

plugins {
	id("kotlin-common")
	alias(libs.plugins.kordex.gradle) apply false
	alias(libs.plugins.kordex.i18n) apply false
	alias(libs.plugins.kotlinx.serialization) apply false
	alias(libs.plugins.detekt) apply false
}

val excludedPlugins = listOf("sta")

val libraries = libs
val typesafeProjects = projects
subprojects {
	pluginManager.apply {
		apply("kotlinx-serialization")
		apply("kotlin")
		apply("dev.detekt")
	}

	dependencies {
		if (path != typesafeProjects.deps.path) {
			compileOnly(typesafeProjects.deps)
			testImplementation(testFixtures(typesafeProjects.deps))
			if (path != typesafeProjects.common.path)
				compileOnly(typesafeProjects.common) // The common subproject serves as a base for all other subprojects
		}
	}

	configurations.all {
		excludedDependencies.forEach { (group, module) ->
			exclude(group = group, module = module)
		}
		resolutionStrategy.dependencySubstitution {
			val kordexModules = mapOf(
				"annotations" to libraries.versions.kordex.library,
				"token-parser" to libraries.versions.kordex.library
			)
			kordexModules.forEach { (artifact, version) ->
				substitute(module("dev.kordex:$artifact"))
					.using(module("dev.kordex:$artifact:${version.get()}"))
			}
			substitute(module("dev.kordex.data:api"))
				.using(module("dev.kordex.data:api:${libraries.versions.kordex.data.api.get()}"))

			val kordModules = mapOf(
				"kord-core" to libraries.versions.kord.core,
				"kord-voice" to libraries.versions.kord.voice,
				"kord-common" to libraries.versions.kord.common,
				"kord-gateway" to libraries.versions.kord.gateway,
				"kord-rest" to libraries.versions.kord.rest,
			)

			kordModules.forEach { (artifact, version) ->
				listOf(artifact, "$artifact-jvm").forEach { coordinate ->
					substitute(module("dev.kord:$coordinate"))
						.using(module("dev.kord:$coordinate:${version.get()}"))
				}
			}
		}
	}
}

allprojects {
	if (path != typesafeProjects.deps.path) {
		configurations.runtimeClasspath {
			exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
			exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
			exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
		}
	}

	tasks.withType<Test> {
		useJUnitPlatform()
	}
}

tasks {
	register("cleanPlugins") {
		description = "Wipes the local plugins directory and recreates an empty one to ensure a fresh environment."
		group = "debugging"
		doLast {
			val pluginsDir = file("./container/plugins")
			if (pluginsDir.exists()) {
				pluginsDir.deleteRecursively()
			}
			pluginsDir.mkdirs()
		}
	}

	register<JavaExec>("runFull") {
		description = "Full execution from scratch: Cleans, exports all subproject plugins (excluding a pre-defined list), and runs the application via ShadowJar configuration."
		group = "debugging"
        dependsOn("cleanPlugins")
		dependsOn(subprojects
			.filter { it.name !in excludedPlugins }
			.mapNotNull { it.tasks.findByName("exportToPluginsDir") }
		)
		val runTask = subprojects.first { it.name == "bot" }.tasks.getByName<JavaExec>("run")
		mainClass = runTask.mainClass
		classpath = runTask.classpath
		jvmArgs = runTask.jvmArgs + listOf("--enable-native-access=ALL-UNNAMED")
	}
}