package com.blanketutils.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.LoggerFactory
import java.util.function.Supplier

/**
 * Utility class for handling command registration and permissions in BlanketUtils
 * with adaptive permission system support
 */
class CommandManager(
    private val modId: String,
    private val logger: org.slf4j.Logger = LoggerFactory.getLogger("BlanketUtils-CommandManager")
) {
    private var serverInstance: net.minecraft.server.MinecraftServer? = null

    // Cached reflection results for performance
    val fabricPermissionsAvailable = checkClassAvailable("me.lucko.fabric.api.permissions.v0.Permissions")
    val luckPermsAvailable = checkClassAvailable("net.luckperms.api.LuckPerms")
    val cyberPermissionsAvailable = checkClassAvailable("com.github.zly2006.cyberpermission.api.PermissionApi")

    /**
     * Checks if a class is available without throwing exceptions
     */
    fun checkClassAvailable(className: String): Boolean {
        return try {
            Class.forName(className)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Registers a command using the provided builder function
     */
    fun registerCommand(buildCommand: (CommandDispatcher<ServerCommandSource>) -> Unit) {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            logger.info("Registering commands for $modId")
            buildCommand(dispatcher)
        }
    }

    /**
     * Checks if a source has the required permission
     */
    fun hasPermission(source: ServerCommandSource, permission: String, level: Int): Boolean {
        val player = source.player
        return if (player != null) {
            hasPlayerPermission(player, permission, level)
        } else {
            source.hasPermissionLevel(level)
        }
    }

    /**
     * Checks player permissions across different permission systems
     */
    fun hasPlayerPermission(player: ServerPlayerEntity, permission: String, level: Int): Boolean {
        return when {
            luckPermsAvailable && hasLuckPermsPermission(player, permission, level) -> true
            cyberPermissionsAvailable && hasCyberPermission(player, permission) -> true
            fabricPermissionsAvailable && hasFabricPermission(player, permission, level) -> true
            else -> player.hasPermissionLevel(level)
        }
    }

    /**
     * Checks LuckPerms permission using reflection
     */
    private fun hasLuckPermsPermission(player: ServerPlayerEntity, permission: String, level: Int): Boolean {
        if (!luckPermsAvailable) return false

        return try {
            val luckPermsProvider = Class.forName("net.luckperms.api.LuckPermsProvider")
            val api = luckPermsProvider.getMethod("get").invoke(null)
            val user = api.javaClass.getMethod("getPlayerAdapter", Class::class.java)
                .invoke(api, ServerPlayerEntity::class.java)
                .javaClass.getMethod("getUser", ServerPlayerEntity::class.java)
                .invoke(api, player)

            user.javaClass.getMethod("getCachedData").invoke(user)
                .javaClass.getMethod("getPermissionData").invoke(user)
                .javaClass.getMethod("checkPermission", String::class.java)
                .invoke(user, permission)
                .toString().toBoolean()
        } catch (e: Exception) {
            logger.debug("LuckPerms permission check failed: ${e.message}")
            false
        }
    }

    /**
     * Checks CyberPermissions using reflection
     */
    private fun hasCyberPermission(player: ServerPlayerEntity, permission: String): Boolean {
        if (!cyberPermissionsAvailable) return false

        return try {
            val cyberPermClass = Class.forName("com.github.zly2006.cyberpermission.api.PermissionApi")
            val checkMethod = cyberPermClass.getMethod("check", ServerPlayerEntity::class.java, String::class.java)
            checkMethod.invoke(null, player, permission) as Boolean
        } catch (e: Exception) {
            logger.debug("CyberPermissions check failed: ${e.message}")
            false
        }
    }

    /**
     * Checks Fabric Permissions API using reflection
     */
    private fun hasFabricPermission(player: ServerPlayerEntity, permission: String, level: Int): Boolean {
        if (!fabricPermissionsAvailable) return false

        return try {
            val permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions")
            val checkMethod = permissionsClass.getMethod("check", ServerPlayerEntity::class.java, String::class.java, Int::class.java)
            checkMethod.invoke(null, player, permission, level) as Boolean
        } catch (e: Exception) {
            logger.debug("Fabric Permissions check failed: ${e.message}")
            false
        }
    }

    /**
     * Simulates permission checks for all online players
     */
    fun simulatePermissionChecks(permissions: List<String>, level: Int = 2) {
        serverInstance?.playerManager?.playerList?.forEach { player ->
            permissions.forEach { permission ->
                if (luckPermsAvailable) hasLuckPermsPermission(player, permission, level)
                if (cyberPermissionsAvailable) hasCyberPermission(player, permission)
                if (fabricPermissionsAvailable) hasFabricPermission(player, permission, level)
            }
        } ?: logger.warn("Server instance is null. Permission simulation skipped.")
    }

    /**
     * Logs available permission systems on startup
     */
    fun logAvailablePermissionSystems() {
        logger.info("Available permission systems:")
        logger.info("- LuckPerms: $luckPermsAvailable")
        logger.info("- CyberPermissions: $cyberPermissionsAvailable")
        logger.info("- Fabric Permissions API: $fabricPermissionsAvailable")
    }

    /**
     * Sends feedback to command source with color support
     */
    fun sendFeedback(source: ServerCommandSource, message: String, color: Int = 0x55FF55, broadcast: Boolean = false) {
        val coloredMessage = Text.literal(message).styled { it.withColor(color) }
        source.sendFeedback(Supplier { coloredMessage }, broadcast)
    }

    /**
     * Sends error message to command source
     */
    fun sendError(source: ServerCommandSource, message: String) {
        source.sendError(Text.literal("§c$message"))
    }

    companion object {
        fun createLogger(name: String): org.slf4j.Logger {
            return LoggerFactory.getLogger(name)
        }
    }
}