plugins {
	id("game-plugin")
	alias(libs.plugins.kordex.gradle)
	alias(libs.plugins.kordex.i18n)
}

kordEx {
	kordExVersion.set(libs.versions.kordex.library)
	kordVersion.set(libs.versions.kord.core)
	plugin {
		id = "paralya-ai"
		version = getVersion() as String
		description = "ParalyaBot's AI-based help plugin"
		pluginClass = "fr.paralya.bot.ai.AiPlugin"
	}
}

i18n {
	bundle("paralyabot-ai.strings", "fr.paralya.bot.ai") {
		className = "I18n"
		publicVisibility = false
	}
}


