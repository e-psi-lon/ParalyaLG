package fr.paralya.bot.extensions

import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kord.rest.builder.message.embed
import dev.kordex.core.DISCORD_BLURPLE
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.types.TranslatableContext
import fr.paralya.bot.I18n
import fr.paralya.bot.botDeveloper
import fr.paralya.bot.common.adminOnly
import fr.paralya.bot.common.contextTranslate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import oshi.SystemInfo
import oshi.software.os.OSProcess
import oshi.util.FormatUtil.formatBytes
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private data class CpuState(
    val usage: Double? = null,
    val snapshot: OSProcess? = null,
)

class Monitoring : Extension() {
    override val name: String = "Monitoring"
    private val systemInfo = SystemInfo()
    private val cpuState = MutableStateFlow(CpuState())
    private var cpuUsageJob : Job? = null


    override suspend fun unload() {
        cpuUsageJob?.cancel()
    }

    override suspend fun setup() {
        cpuUsageJob = kord.launch {
            val os = systemInfo.operatingSystem
            val pid = os.processId

            while (loaded) {
                delay(5.seconds)
                val newSnapshot = systemInfo.operatingSystem.getProcess(pid)
                cpuState.update { current ->
                    val load = newSnapshot?.getProcessCpuLoadBetweenTicks(current.snapshot) // 0 when null
                    @Suppress("MagicNumber")
                    CpuState(
                        snapshot = newSnapshot,
                        usage = load?.let { it / 100 } ?: current.usage
                    )
                }
            }
        }
        ephemeralSlashCommand {
            name = I18n.Monitoring.Command.name
            description = I18n.Monitoring.Command.description

            adminOnly(listOf(botDeveloper)) {
                respond {
                    buildMonitoringEmbed()
                }
            }
        }
    }

    context(ctx: TranslatableContext)
    private suspend fun FollowupMessageCreateBuilder.buildMonitoringEmbed(): Unit = embed {
        val hardware = systemInfo.hardware
        val cpu = hardware.processor
        val mem = hardware.memory
        val runtime = Runtime.getRuntime()

        title = I18n.Monitoring.Response.Embed.title.contextTranslate()
        color = DISCORD_BLURPLE

        field {
            name = I18n.Monitoring.Response.Embed.Cpu.name.contextTranslate()
            value = I18n.Monitoring.Response.Embed.Cpu.value.contextTranslate(
                cpu.processorIdentifier.name.trim(),
                runtime.availableProcessors(),
                cpuState.value.usage?.let { "%.1f".format(ctx.getLocale(), it) } ?: "No CPU usage found"
            )
            inline = true
        }

        val totalRam = mem.total
        val availRam = mem.available
        val usedRam = totalRam - availRam
        field {
            name = I18n.Monitoring.Response.Embed.Memory.name.contextTranslate()
            value = I18n.Monitoring.Response.Embed.Memory.value.contextTranslate(
                formatBytes(usedRam),
                formatBytes(availRam),
                formatBytes(totalRam)
            )
            inline = true
        }

        field { name = "\u200B"; value = "\u200B"; inline = false }

        val heapMax = runtime.maxMemory()
        val heapTotal = runtime.totalMemory()
        val heapFree = runtime.freeMemory()
        val heapUsed = heapTotal - heapFree
        @Suppress("MagicNumber")
        val heapPercent = heapUsed.toDouble() / heapMax * 100
        field {
            name = I18n.Monitoring.Response.Embed.Heap.name.contextTranslate()
            value = I18n.Monitoring.Response.Embed.Heap.value.contextTranslate(
                formatBytes(heapUsed),
                formatBytes(heapTotal),
                formatBytes(heapMax),
                "%.1f".format(ctx.getLocale(), heapPercent)
            )
            inline = true
        }

        footer {
            text = I18n.Monitoring.Response.Embed.footer.contextTranslate(
                System.getProperty("java.version"),
                System.getProperty("os.name")
            )
        }

        timestamp = Clock.System.now()
    }

}
