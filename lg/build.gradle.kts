plugins {
	id("game-plugin")
	alias(libs.plugins.kordex.gradle)
	alias(libs.plugins.kordex.i18n)
}

kordEx {
	kordExVersion.set(libs.versions.kordex.library)
	kordVersion.set(libs.versions.kord.core)
	plugin {
		id = "paralya-lg"
		version = getVersion() as String
		description = "ParalyaBot's Werewolf game plugin"
		pluginClass = "fr.paralya.bot.lg.LgPlugin"
	}
}

i18n {
	bundle("paralyabot-lg.strings", "fr.paralya.bot.lg") {
		className = "I18n"
		publicVisibility = false
	}
}


