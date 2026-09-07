import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
    id("kotlin-common")
    alias(libs.plugins.kordex.gradle)
    alias(libs.plugins.kordex.i18n)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
}


group = "fr.paralya.bot"
version = property("paralyabot.version")!!

kordEx {
    kordExVersion.set(libs.versions.kordex.library)
    kordVersion.set(libs.versions.kord.core)
    bot {
        voice = false
        dataCollection(DataCollection.None)
        mainClass = "fr.paralya.bot.ParalyaBotKt"
    }
    configurations = listOf("compileOnly")
}


i18n {
    bundle("paralyabot.strings", "fr.paralya.bot") {
        className = "I18n"
        publicVisibility = false
    }
}

dependencies {
    implementation(libs.logback)
    implementation(libs.kotlinx.html)
}

tasks {
    register<Copy>("copyRuntimeClasspath") {
        description = "Copies the runtime dependencies to the build directory"
        group = "distribution"
        val outputDir = layout.buildDirectory.dir("deps")
        from(configurations.runtimeClasspath)
        into(outputDir)

    }
    jar {
        archiveBaseName.set("paralya-bot")
        archiveClassifier.set("")
        archiveVersion.set("")
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        manifest {
            val rawClassPath = System.getenv("RAW_CLASSPATH")
            if (!rawClassPath.isNullOrEmpty()) {
                attributes["Class-Path"] = rawClassPath.split(" ").joinToString(" ") { "file://$it" }
            }
        }
        listOf(
            "META-INF/maven/**",
            "META-INF/proguard/**",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/DEPENDENCIES",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "META-INF/versions/**",
            "**/*.kotlin_builtins",
            "**/*.kotlin_metadata",
            "**/*.md",
            "**/README*",
            "**/CHANGELOG*",
            "**/LICENSE*",
            "**/NOTICE*"
        ).forEach(::exclude)
    }
}