package fr.paralya.bot.extensions.base

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.entity.Embed
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import fr.paralya.bot.common.getResource
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.ImgLoading
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import kotlin.time.Clock

suspend fun FollowupMessageCreateBuilder.addStringExport(channel: MessageChannelBehavior, guild: GuildBehavior?, messages: Flow<Message>, anonymous: Boolean) {
    val export = buildString {
        append("==============================================================\n")
        append("Serveur: ${guild?.asGuildOrNull()?.name ?: "Pas de nom de serveur"}")
        append("\nSalon: ${guild?.getChannel(channel.id)?.name ?: "Pas de nom de salon"}\n")
        append("==============================================================\n\n")
        var counter = 0
        var lastAuthor: String? = null
        var identity = false
        messages.collect { message ->
            val localDateTime = message.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
            val formattedDate = "%02d/%02d/%04d %02d:%02d".format(
                localDateTime.day,
                localDateTime.month.number,
                localDateTime.year,
                localDateTime.hour,
                localDateTime.minute
            )
            val author = if (anonymous) {
                if (identity) "Anonyme 1" else "Anonyme 2"
            } else {
                message.author?.tag ?: "Inconnu"
            }
            if ((message.author?.tag ?: "Inconnu") != lastAuthor) {
                append("[$formattedDate] $author\n")
                lastAuthor = message.author?.tag ?: "Inconnu"
                identity = !identity
            }
            append(message.content)
            appendCollection(message.attachments, "Pièce(s) jointe(s)") { it.url }
            appendCollection(message.embeds, "Embed(s)") { formatEmbed(it) }
            append("\n")
            counter++
        }
        append("==============================================================\n")
        append("$counter message(s) exportés")
        append("\n==============================================================\n")
    }
    addFile("export.txt", ChannelProvider { ByteReadChannel(export) })
}

private fun <T>StringBuilder.appendCollection(collection: Collection<T>, name: String, transformer: (T) -> String = { it.toString() }) {
    if (collection.isNotEmpty()) {
        append("{$name}\n")
        collection.forEach {
            append("${transformer(it)}\n")
        }
    }
}

private fun formatEmbed(embed: Embed): String {
    return buildString {
        append("Embed:\n")
        embed.title?.let { append("Titre: $it\n") }
        embed.description?.let { append("Déscription: $it\n") }
        if (embed.fields.isNotEmpty()) {
            append("Champs:\n")
            embed.fields.forEach { field ->
                append(" - ${field.name}: ${field.value}\n")
            }
        }
        embed.url?.let { append("URL: $it\n") }
        embed.color?.let { append("Couleur: #${it.rgb.toString(16).padStart(6, '0')}\n") }
        embed.footer?.let { footer ->
            append("Footer: ${footer.text}\n")
            footer.iconUrl?.let { append("URL de l'icône du footer: $it\n") }
        }
        embed.image?.let { image ->
            append("URL de l'image: ${image.url}\n")
        }
        embed.thumbnail?.let { thumbnail ->
            append("URL de la miniature: ${thumbnail.url}\n")
        }
        embed.author?.let { author ->
            append("Auteur: ${author.name}\n")
            author.url?.let { append("URL de l'auteur: $it\n") }
            author.iconUrl?.let { append("URL de l'icône de l'auteur: $it\n") }
        }
        append("Fin de l'Embed\n")
    }
}

suspend fun FollowupMessageCreateBuilder.addHtmlExport(
    channel: MessageChannelBehavior,
    guild: GuildBehavior?,
    messages: Flow<Message>,
    arguments: Base.ExportArguments
) {
    val css = getResource("style.css").decodeToString()
    val icons = getResource("icons.svg").decodeToString()
    val guildName = guild?.asGuildOrNull()?.name ?: "Pas de nom de serveur"
    val guildIcon = guild?.asGuildOrNull()?.icon?.cdnUrl ?: ""
    val channelName = guild?.getChannel(channel.id)?.name ?: "Pas de nom de salon"
    val channelTopic = (channel.asChannelOrNull() as? TextChannel)?.topic
    val start = arguments.start

    val lastMessageId = messages.lastOrNull()?.id
    val exportRange = when {
        start != null && lastMessageId != null ->
            "Entre ${formatDate(start)} et ${formatDate(lastMessageId)}"
        start != null ->
            "Après ${formatDate(start)}"
        lastMessageId != null ->
            "Jusqu'à ${formatDate(lastMessageId)}"
        else -> null
    }
    val messageList = messages.toList()
    val mentionedUsers = messageList.map { it.mentionedUsers.toList() }
    var counter = 0
    val html = createHTML(prettyPrint = false).html {
        lang = "en"
        head {
            title("$guildName - $channelName")
            meta("charset", "utf-8")
            meta("viewport", "width=device-width")
            style { unsafe { +css } }
            link(rel="stylesheet", href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.15.6/styles/solarized-dark.min.css")
            script(src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.15.6/highlight.min.js") {}
            unsafeScript(
                """
                document.addEventListener('DOMContentLoaded', () => {
                    document.querySelectorAll('.chatlog__markdown-pre--multiline').forEach(e => hljs.highlightBlock(e));
                });  
                """.trimIndent()
            )
            script(src="https://cdnjs.cloudflare.com/ajax/libs/lottie-web/5.8.1/lottie.min.js") {}
            unsafeScript(
                """
                document.addEventListener('DOMContentLoaded', () => {
                    document.querySelectorAll('.chatlog__sticker--media[data-source]').forEach(e => {
                        const anim = lottie.loadAnimation({
                            container: e,
                            renderer: 'svg',
                            loop: true,
                            autoplay: true,
                            path: e.getAttribute('data-source')
                        });
        
                        anim.addEventListener(
                            'data_failed',
                            () => e.innerHTML = '<strong>[Sticker cannot be rendered]</strong>'
                        );
                    });
                });
                """.trimIndent()
            )
            unsafeScript(
                """
                    function scrollToMessage(event, id) {
                        const element = document.getElementById('chatlog__message-container-' + id);
                        if (!element)
                            return;

                        event.preventDefault();
                        element.classList.add('chatlog__message-container--highlighted');

                        window.scrollTo({
                            top: element.getBoundingClientRect().top - document.body.getBoundingClientRect().top - (window.innerHeight / 2),
                            behavior: 'smooth'
                        });

                        window.setTimeout(
                            () => element.classList.remove('chatlog__message-container--highlighted'),
                            2000
                        );
                    }

                    function showSpoiler(event, element) {
                        if (!element)
                            return;

                        if (element.classList.contains('chatlog__attachment--hidden')) {
                            event.preventDefault();
                            element.classList.remove('chatlog__attachment--hidden');
                        }

                        if (element.classList.contains('chatlog__markdown-spoiler--hidden')) {
                            event.preventDefault();
                            element.classList.remove('chatlog__markdown-spoiler--hidden');
                        }
                    }
                """.trimIndent()
            )
            unsafe { +icons }
        }
        body {
            div("preamble") {
                div("preamble__guild-icon-container") {
                    img(classes = "preamble__guild-icon") {
                        src = guildIcon.toString()
                        alt = guildName
                        loading = ImgLoading.lazy
                    }
                }
                div("preamble__entries-container") {
                    preambleEntry(guildName)
                    preambleEntry(channelName)
                    if (!channelTopic.isNullOrBlank()) {
                        preambleEntry(channelTopic, true)
                    }
                    if (exportRange != null) {
                        preambleEntry(exportRange, true)
                    }
                }
            }
            div("chatlog") {
                div("chatlog__messages-group") {
                    messageList.forEachIndexed { index, message ->
                        discordMessageContainer(message, index, mentionedUsers.getOrElse(index) { emptyList() })
                        counter++
                    }
                }
            }
            div("postamble") {
                postambleEntry("$counter message(s) exportés")
                postambleEntry {
                    val offsetHours = TimeZone.currentSystemDefault().offsetAt(Clock.System.now()).totalSeconds / 3600.0
                    val formattedOffset = when {
                        offsetHours > 0 -> "+${offsetHours}"
                        offsetHours < 0 -> "-${-offsetHours}"
                        else -> "+0"
                    }
                    +"Zone UTC: UTC@$formattedOffset"
                }
            }
        }
    }
    addFile("export.html", ChannelProvider { ByteReadChannel(html) })
}

private fun formatDate(snowflake: Snowflake): String {
    val dateTime = snowflake.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d/%02d/%04d %02d:%02d".format(
        dateTime.day, dateTime.month.number, dateTime.year, dateTime.hour, dateTime.minute
    )
}
