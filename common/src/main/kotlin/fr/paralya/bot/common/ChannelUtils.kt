package fr.paralya.bot.common

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.TopGuildChannelBehavior
import dev.kord.core.behavior.channel.editMemberPermission
import dev.kord.core.behavior.channel.editRolePermission
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.TextChannel
import dev.kordex.core.utils.permissionsForMember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/**
 * Adds a permission to a role in a channel.
 *
 * @param id The ID of the role to which the permission will be added.
 * @param permission The permission to be added.
 * @param reason An optional reason for the permission change.
 */
suspend fun TopGuildChannelBehavior.addRolePermission(
    id: Snowflake,
    permission: Permission,
    reason: String? = null,
) {
    editRolePermission(id) {
        allowed = allowed.plus(permission)
        denied = denied.minus(permission)
        this.reason = reason
    }
}

/**
 * Adds a permission to a member in a channel.
 *
 * @param id The ID of the member to which the permission will be added.
 * @param permission The permission to be added.
 * @param reason An optional reason for the permission change.
 */
suspend fun TopGuildChannelBehavior.addMemberPermission(
    id: Snowflake,
    permission: Permission,
    reason: String? = null,
) {
    editMemberPermission(id) {
        allowed = allowed.plus(permission)
        denied = denied.minus(permission)
        this.reason = reason
    }
}

/**
 * Adds multiple permissions to a role in a channel.
 *
 * @param id The ID of the role to which the permissions will be added.
 * @param permissions The permissions to be added.
 * @param reason An optional reason for the permission change.
 */
suspend fun TopGuildChannelBehavior.addRolePermissions(
    id: Snowflake,
    vararg permissions: Permission,
    reason: String? = null
) {
    editRolePermission(id) {
        allowed = allowed.plus(Permissions(permissions.toSet()))
        denied = denied.minus(Permissions(permissions.toSet()))
        this.reason = reason
    }
}

/**
 * Adds multiple permissions to a member in a channel.
 *
 * @param id The ID of the member to which the permissions will be added.
 * @param permissions The permissions to be added.
 * @param reason An optional reason for the permission change.
 */
suspend fun TopGuildChannelBehavior.addMemberPermissions(
    id: Snowflake,
    vararg permissions: Permission,
    reason: String? = null
) {
    editMemberPermission(id) {
        allowed = allowed.plus(Permissions(permissions.toSet()))
        denied = denied.minus(Permissions(permissions.toSet()))
        this.reason = reason
    }
}

/**
 * Remove a permission from a role in a channel.
 *
 * @param id The ID of the role to which the permission will be removed.
 * @param permission The permission to be removed.
 * @param reason An optional reason for the permission change.
 */
suspend fun TopGuildChannelBehavior.removeRolePermission(
    id: Snowflake,
    permission: Permission,
    reason: String? = null,
) {
    editRolePermission(id) {
        allowed = allowed.minus(permission)
        denied = denied.plus(permission)
        this.reason = reason
    }
}

/**
 * Remove a permission from a member in a channel.
 *
 * @param id The ID of the member to which the permission will be removed.
 * @param permission The permission to be removed.
 * @param reason An optional reason for the permission change.
 */
suspend fun TopGuildChannelBehavior.removeMemberPermission(
    id: Snowflake,
    permission: Permission,
    reason: String? = null,
) {
    editMemberPermission(id) {
        allowed = allowed.minus(permission)
        denied = denied.plus(permission)
        this.reason = reason
    }
}

/**
 * Remove multiple permissions from a role in a channel.
 *
 * @param id The ID of the role to which the permissions will be removed.
 * @param permissions The permissions to be removed.
 * @param reason An optional reason for the permission change.
 */
suspend fun TopGuildChannelBehavior.removeRolePermissions(
    id: Snowflake,
    vararg permissions: Permission,
    reason: String? = null
) {
    editRolePermission(id) {
        allowed = allowed.minus(Permissions(permissions.toSet()))
        denied = denied.plus(Permissions(permissions.toSet()))
        this.reason = reason
    }
}

/**
 * Remove multiple permissions from a member in a channel.
 *
 * @param id The ID of the member to which the permissions will be removed.
 * @param permissions The permissions to be removed.
 * @param reason An optional reason for the permission change.
 */
suspend fun TopGuildChannelBehavior.removeMemberPermissions(
    id: Snowflake,
    vararg permissions: Permission,
    reason: String? = null
) {
    editMemberPermission(id) {
        allowed = allowed.minus(Permissions(permissions.toSet()))
        denied = denied.plus(Permissions(permissions.toSet()))
        this.reason = reason
    }
}

/**
 * Retrieves a flow of members who have access to the current text channel.
 *
 * @return A flow of [Member] objects representing the members with access to the channel.
 */
fun TextChannel.getMembersWithAccess(): Flow<Member> {
    return guild.members.filter { member ->
        permissionsForMember(member).contains(Permission.ViewChannel)
    }
}
