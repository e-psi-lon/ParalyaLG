package fr.paralya.bot.common.plugins

import fr.paralya.bot.common.getExceptionOrNull
import fr.paralya.bot.common.orNoMessage
import fr.paralya.bot.common.runCatchingException
import io.github.oshai.kotlinlogging.KLogger
import org.pf4j.PluginRuntimeException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.OnErrorResult
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

private sealed interface TeardownResult {
    @JvmInline
    value class OldPluginFailedToDelete(val exception: PluginRuntimeException? = null) : TeardownResult
    data object OldPluginSuccessfulTeardown : TeardownResult
}


// Not thread-safe, but reload operations aren't concurrent anyway
@Suppress("VarCouldBeVal") // No it can't, the setter is just used internally
private var tempDirectory: Path = Files.createTempDirectory("ParalyaBot-Reload")
    get() {
        if (!field.exists()) {
            field = Files.createTempDirectory("ParalyaBot-Reload")
        }
        return field
    }


@OptIn(ExperimentalPathApi::class)
internal class PluginReloadStrategy(
    private val pluginManager: PluginManager,
    private val pluginId: String,
    private val oldPluginPath: Path,
    val newPluginZipPath: Path,
    private val logger: KLogger,
    private val workDirectory: Path
) {

    fun reload(): PluginReloadResult {
        val safeCopy = saveCopy()
        val safeNewPlugin = workDirectory.resolve(newPluginZipPath.fileName)
        try {
            newPluginZipPath.copyTo(safeNewPlugin, overwrite = true)
            val teardownResult = teardown()
            if (teardownResult is TeardownResult.OldPluginFailedToDelete) {
                return OldPluginFailedToDelete(teardownResult.exception)
            }
            if (!newPluginZipPath.exists()) safeNewPlugin.copyTo(newPluginZipPath, overwrite = true)
            val result = loadNewPlugin()
            // This has to be safe
            // We only catch Exception, if the cast fails, what the hell is happening to the JVM????
            val loadedPluginId = result.getOrElse { return fallback(safeCopy, it as Exception) }
            val exception = tryStartPlugin(loadedPluginId)
            if (exception != null) {
                return fallback(safeCopy, exception)
            }
            successCleanup()
            return SuccessfulPluginReload
        } finally {
            safeCopy.deleteRecursively()
            safeNewPlugin.deleteIfExists()
        }
    }

    private fun saveCopy(): Path {
        val path = workDirectory.resolve(oldPluginPath.fileName)
        logger.info { "Saving a copy of the old plugin at $path" }
        return oldPluginPath.copyToRecursively(
            path,
            overwrite = true,
            onError = { source, target, exception ->
                logger.error(exception) { "Failed to copy $source to $target" }
                OnErrorResult.TERMINATE
            },
            followLinks = false
        )
    }


    private fun teardown() = try {
        val isDeleted = pluginManager.deletePlugin(pluginId)
        if (isDeleted) {
            logger.info { "Plugin $pluginId successfully unloaded." }
            TeardownResult.OldPluginSuccessfulTeardown
        } else {
            logger.error { "Plugin $pluginId failed to delete without exception." }
            TeardownResult.OldPluginFailedToDelete()
        }
    } catch (e: PluginRuntimeException) {
        logger.error(e) { "Plugin $pluginId failed to delete." }
        TeardownResult.OldPluginFailedToDelete(e)
    }

    private fun logAndFail(e: Exception, message: String): Result<String> {
        logger.error(e) { message }
        return Result.failure(e)
    }
    private fun loadNewPlugin(): Result<String> = try {
        val loadedPluginId = pluginManager.loadPlugin(newPluginZipPath)
        if (pluginId != loadedPluginId) {
            logger.warn {
                "Plugin ${loadedPluginId ?: "<null plugin>"} was successfully loaded, " +
                        "but its ID doesn't match the old $pluginId."
            }
        }
        if (loadedPluginId != null) {
            logger.info { "Plugin $loadedPluginId successfully loaded from path $newPluginZipPath." }
            Result.success(loadedPluginId)
        } else {
            logAndFail(
                Exception("Unknown error during plugin loading, no exception was thrown but the result is null"),
                "Failed to load the new plugin from path $newPluginZipPath for an unknown reason."
            )
        }
    } catch (e: PluginRuntimeException) {
        logAndFail(e, "An error happened when loading: from path $newPluginZipPath: ${e.message.orNoMessage()}")
    } catch (e: PluginValidationException) {
        logAndFail(e, "Failed to load invalid plugin at path $newPluginZipPath: ${e.message.orNoMessage()}")
    } catch (e: IllegalArgumentException) {
        logAndFail(e, "Failed to load the new plugin at path $newPluginZipPath: ${e.message.orNoMessage()}")
    }

    private fun tryStartPlugin(pluginToStartId: String): Exception? = runCatchingException {
        pluginManager.startPlugin(pluginToStartId)
    }.onFailure { e -> logger.error(e) { "Failed to start the new plugin $pluginToStartId." } }
    .getExceptionOrNull()

    @Suppress("ReturnCount")
    private fun fallback(safeCopy: Path, originalException: Exception) : PluginReloadResult {
        safeCopy.copyToRecursively(
            oldPluginPath,
            overwrite = true,
            onError = { source, target, exception ->
                logger.error(exception) { "Failed to copy $source to $target" }
                OnErrorResult.TERMINATE
            },
            followLinks = false
        )
        try {
            pluginManager.loadPlugin(oldPluginPath)
            val exception = tryStartPlugin(pluginId)
            if (exception == null) {
                logger.info { "Successfully reloaded the old plugin $pluginId." }
            } else {
                logger.error(exception) { "Failed to start the old plugin $pluginId." }
                return OldPluginFallbackFailedToLoad(originalException, fallbackException = exception)
            }
            return OldPluginReusedAsFallback(originalException)
        } catch (e: PluginRuntimeException) {
            logger.error(e) { "Failed to load the old plugin $pluginId." }
            return OldPluginFallbackFailedToLoad(originalException, fallbackException = e)
        } catch (e: PluginValidationException) {
            logger.error(e) { "Failed to load the old plugin $pluginId: invalid plugin." }
            return OldPluginFallbackFailedToLoad(originalException, fallbackException = e)
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "Failed to load the old plugin $pluginId: invalid plugin path." }
            return OldPluginFallbackFailedToLoad(originalException, fallbackException = e)
        }
    }

    private fun successCleanup() {
        oldPluginPath
            .resolveSibling("${oldPluginPath.fileName}.zip")
            .deleteIfExists()
    }
}

/**
 * Internal helper factory to create a [PluginReloadStrategy] instance based on the current [PluginManager] instance.
 * Avoids explicit `this` at call site.
 */
internal fun PluginManager.createReloadStrategy(
    pluginId: String,
    oldPluginPath: Path,
    newPluginZipPath: Path,
    logger: KLogger
) = PluginReloadStrategy(this, pluginId, oldPluginPath, newPluginZipPath, logger, tempDirectory)
