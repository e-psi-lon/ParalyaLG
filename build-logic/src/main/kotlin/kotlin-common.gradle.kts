import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import kotlin.collections.filter

plugins {
	kotlin("jvm")
	id("org.jetbrains.kotlinx.kover")
}
val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")


kotlin {
	jvmToolchain(libs.findVersion("jvm").get().toString().toInt())
	compilerOptions {
		// Kotlin 2.4 will allow to remove these two compiler flags
		freeCompilerArgs.addAll("-Xcontext-parameters", "-Xallow-reified-type-in-catch")
	}
}
dependencies {
	compileOnly(kotlin("stdlib"))
}

tasks.withType<Test> {
	useJUnitPlatform()
}


tasks.register("nixDownloadDepsFixed") {
	notCompatibleWithConfigurationCache("Meant for forced resolving of every dependency, making configuration caching irrelevant/incompatible with the intended behavior")
    description = "A fixed version of the task used by Nixpks to forcefully download dependencies."
	doLast {
		configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
		buildscript.configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
	}
}

repositories {
	mavenCentral()
}