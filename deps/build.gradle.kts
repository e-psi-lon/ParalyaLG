
plugins {
    id("kotlin-common")
    alias(libs.plugins.kordex.gradle)
    alias(libs.plugins.kordex.i18n)
    `java-test-fixtures`
    alias(libs.plugins.shadow)
}

group = "fr.paralya.bot"
version = property("paralyabot.version")!!

kordEx {
    kordExVersion.set(libs.versions.kordex.library)
    kordVersion.set(libs.versions.kord.core)
}

repositories {
    mavenCentral()
}

dependencies {
    // Test dependencies (exposed to test fixtures)
    testFixturesApi(kotlin("test"))
    testFixturesApi(libs.koin.test) // For tests that involve Koin
    testFixturesApi(libs.mockk) // Allow mocking in tests
    testFixturesApi(libs.junit)
    testFixturesApi(libs.junit.launcher)

    // Exposed dependencies, for use in plugins
    api(libs.konform)
    api(libs.kord.cache.redis)
    api(libs.kordex.i18n.runtime)
}

kotlin {
    jvmToolchain(25)
}

tasks {
    shadowJar {
        archiveBaseName.set("paralya-bot-deps")
        archiveClassifier.set("")
        archiveVersion.set("")
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        exclude("fr/paralya/")

        listOf("brkitr", "translit", "rbnf").forEach { category ->
            exclude("com/ibm/icu/impl/data/icudata/$category/**")
        }
        listOf(
            "aix-*", "sunos-*", "darwin-*", "freebsd-*", "openbsd-*", "dragonflybsd-*",
            "linux-arm*", "linux-mips*", "linux-ppc*", "linux-s390x", "linux-loongarch*",
            "linux-riscv*", "linux-aarch64", "linux-x86",
            "win32-x86", "win32-aarch64"
        ).forEach { platform ->
            exclude("com/sun/jna/$platform/**")
        }


        exclude { file ->
            val path = file.relativePath.pathString
            val isTranslationDir = path.startsWith("translations/kordex/") ||
                    path.startsWith("kordex/")

            if (isTranslationDir) {
                val name = file.relativePath.lastName
                val isTargetLocale = name.contains("fr", ignoreCase = true) ||
                        name.contains("en", ignoreCase = true) ||
                        name.endsWith(".properties") && !name.contains("_")

                return@exclude !isTargetLocale
            }

            false
        }


        exclude { file ->
            val path = file.relativePath.pathString
            if (!path.startsWith("com/ibm/icu/impl/data/icudata/")) return@exclude false

            val lastName = file.relativePath.lastName
            // Keep all non-res/icu binary data files (.cfu, .nrm, .spp, .brk, .dict, etc.)
            if (!lastName.endsWith(".res") && !lastName.endsWith(".icu")) return@exclude false

            val name = lastName.removeSuffix(".res").removeSuffix(".icu")

            val keepFiles = setOf(
                "root", "pool", "res_index", "metadata", "supplementalData",
                "langInfo", "zoneinfo64", "metaZones", "timezoneTypes",
                "windowsZones", "tzdbNames", "numberingSystems", "plurals",
                "pluralRanges", "currencyNumericCodes", "keyTypeData",
                "grammaticalFeatures", "genderList", "dayPeriods",
                "icustd", "icuver", "units", "unames", "uprops",
                "confusables", "pnames", "ucase"
            )
            if (name in keepFiles) return@exclude false
            if (name.startsWith("en") || name.startsWith("fr")) return@exclude false

            name.first().isLetter()
        }

        listOf("osx", "linux_aarch_64").forEach { platform ->
            exclude("META-INF/native/libnetty_*_${platform}*.*")
        }
        exclude("META-INF/native/netty_*_x86_32.dll")
        listOf("x86", "arm64").forEach { arch ->
            exclude("org/fusesource/jansi/internal/native/Windows/$arch/**")
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
            "META-INF/com.android.tools/**",
            "**/*.kotlin_builtins",
            "**/*.kotlin_metadata",
            "**/*.md",
            "**/README*",
            "**/CHANGELOG*",
            "**/LICENSE*",
            "**/NOTICE*",
            "**/DebugProbesKt.bin",
            "oshi.macos.versions.properties",
            "oshi.vmmacaddr.properties"
        ).forEach(::exclude)

        mergeServiceFiles()
    }
}