package fr.paralya.bot.common

import dev.kord.common.entity.DiscordUser
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.RoleBehavior
import dev.kord.core.cache.data.UserData
import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kord.rest.Image
import dev.kordex.core.utils.any
import dev.kordex.core.utils.hasRole
import fr.paralya.bot.common.plugins.Plugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext

private object Resource
/**
 * Retrieves an image asset from the specified path.
 *
 * @receiver The plugin instance from which the asset is being retrieved.
 * @param path The path to the asset file (in the `assets` resource directory).
 * @return The image as an [Image] object.
 * @throws IllegalArgumentException if the resource is not found at the specified path.
 */
suspend inline fun <reified T : Plugin> T.getAsset(path: String) = getAsset(path, pluginId, T::class.java)

/**
 * Retrieves an image asset from the specified path.
 *
 * @param path The path to the asset file (in the `assets` resource directory).
 * @param game The game ID associated with the asset (optional).
 * @param clazz The class from which the resource is being loaded (optional).
 * @return The image as an [Image] object.
 * @throws IllegalArgumentException if the resource is not found at the specified path
 */
suspend fun getAsset(path: String, game: String? = null, clazz: Class<*> = Resource.javaClass) =
    Image.raw(getResource("assets/${if (game != null) "$game/" else ""}$path.webp", clazz), Image.Format.WEBP)


suspend inline fun <reified T>getResourceFrom(path: String) = getResource(path, T::class.java)

@Suppress("InjectDispatcher")
suspend fun getResource(path: String, clazz: Class<*> = Resource.javaClass) : ByteArray {
    val resource = clazz.getResourceAsStream("/$path")
        ?: throw IllegalArgumentException("Resource at path /$path not found")
    return withContext(Dispatchers.IO) {
        resource.readAllBytes().also {
            resource.close()
        }
    }
}

/**
 * Converts a [DiscordUser] to a [User] using the provided [Kord] instance.
 */
fun DiscordUser?.asUser(kord: Kord) = this?.let { User(UserData.from(it), kord) }

/** Extension properties to convert various number types to a [Snowflake] */
val Number.snowflake get() = Snowflake(toLong())

/** @see [Number.snowflake] */
val ULong.snowflake get() = Snowflake(this)


/**
 * Filter a [Flow] of member objects to only include those who have a specific role.
 *
 * @param roleId The ID of the role to filter by.
 * @return A flow of [Member] objects who have the specified role.
 */
fun Flow<Member>.filterByRole(roleId: Snowflake): Flow<Member> =
    filter { member -> member.roles.any { it.id == roleId } }

fun Flow<Member>.filterByRole(role: RoleBehavior): Flow<Member> = filter { it.hasRole(role) }

class ParalyaNotFoundException : ParalyaBotException("Paralya guild not found")
