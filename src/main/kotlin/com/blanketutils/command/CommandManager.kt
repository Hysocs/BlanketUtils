package com.blanketutils.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.LoggerFactory
import java.util.function.Supplier

/**
 * Utility class for simplified command registration in Fabric mods
 */
class CommandManager(
    private val modId: String,
    private val defaultPermissionLevel: Int = 2,
    private val defaultOpLevel: Int = 2
) {
    private val logger = LoggerFactory.getLogger("$modId-Commands")
    private val commands = mutableListOf<CommandData>()
    private val allPermissions = mutableSetOf<String>()

    // Keep track of all permissions for a command and its subcommands
    private fun collectPermissions(command: CommandData) {
        allPermissions.add(command.permission)
        command.subcommands.forEach { subcommand ->
            collectSubcommandPermissions(subcommand, command.permission)
        }
    }

    private fun collectSubcommandPermissions(subcommand: SubcommandData, parentPermission: String) {
        subcommand.permission?.let { permission ->
            allPermissions.add(permission)
        } ?: allPermissions.add(parentPermission)

        subcommand.subcommands.forEach { nestedCommand ->
            collectSubcommandPermissions(nestedCommand, subcommand.permission ?: parentPermission)
        }
    }

    /**
     * Simulates permission checks for all registered commands
     * This helps initialize permission systems and verify permissions are registered
     */
    fun simulatePermissionChecks(server: MinecraftServer) {
        logger.info("Simulating permission checks for $modId commands...")

        val players = server.playerManager.playerList
        if (players.isEmpty()) {
            logger.info("No players online. Permission simulation will only check console permissions.")
        }

        // Simulate for all registered permissions
        allPermissions.forEach { permission ->
            logger.debug("Simulating checks for permission: $permission")

            // Check for console
            try {
                server.commandSource.hasPermissionLevel(defaultOpLevel)
                logger.debug("Console permission check successful for: $permission")
            } catch (e: Exception) {
                logger.warn("Failed to check console permission for: $permission", e)
            }

            // Check for each player
            players.forEach { player ->
                simulatePlayerPermissionCheck(player, permission)
            }
        }

        logger.info("Permission simulation completed for ${allPermissions.size} permissions.")
    }

    private fun simulatePlayerPermissionCheck(player: ServerPlayerEntity, permission: String) {
        try {
            // Try LuckPerms/Permissions API
            try {
                Permissions.check(player, permission, defaultPermissionLevel)
                logger.debug("Permission API check successful for player ${player.name.string}: $permission")
            } catch (e: NoClassDefFoundError) {
                // Fallback to vanilla op
                player.hasPermissionLevel(defaultOpLevel)
                logger.debug("Vanilla permission check successful for player ${player.name.string}: $permission")
            }
        } catch (e: Exception) {
            logger.warn("Failed to check permission for player ${player.name.string}: $permission", e)
        }
    }

    data class CommandData(
        val name: String,
        val permission: String,
        val permissionLevel: Int,
        val opLevel: Int,
        val aliases: List<String>,
        val executor: ((CommandContext<ServerCommandSource>) -> Int)?,
        val subcommands: List<SubcommandData>
    )

    data class SubcommandData(
        val name: String,
        val permission: String?,
        val permissionLevel: Int?,
        val opLevel: Int?,
        val executor: ((CommandContext<ServerCommandSource>) -> Int)?,
        val subcommands: List<SubcommandData>
    )

    inner class CommandConfig {
        internal var executor: ((CommandContext<ServerCommandSource>) -> Int)? = null
        internal val subcommands = mutableListOf<SubcommandData>()

        fun executes(executor: (CommandContext<ServerCommandSource>) -> Int) {
            this.executor = executor
        }

        fun subcommand(
            name: String,
            permission: String? = null,
            permissionLevel: Int? = null,
            opLevel: Int? = null,
            builder: (SubcommandConfig.() -> Unit)? = null
        ) {
            val config = SubcommandConfig().apply { builder?.invoke(this) }
            subcommands.add(
                SubcommandData(
                    name = name,
                    permission = permission,
                    permissionLevel = permissionLevel,
                    opLevel = opLevel,
                    executor = config.executor,
                    subcommands = config.subcommands
                )
            )
        }
    }

    inner class SubcommandConfig {
        internal var executor: ((CommandContext<ServerCommandSource>) -> Int)? = null
        internal val subcommands = mutableListOf<SubcommandData>()

        fun executes(executor: (CommandContext<ServerCommandSource>) -> Int) {
            this.executor = executor
        }

        fun subcommand(
            name: String,
            permission: String? = null,
            permissionLevel: Int? = null,
            opLevel: Int? = null,
            builder: (SubcommandConfig.() -> Unit)? = null
        ) {
            val config = SubcommandConfig().apply { builder?.invoke(this) }
            subcommands.add(
                SubcommandData(
                    name = name,
                    permission = permission,
                    permissionLevel = permissionLevel,
                    opLevel = opLevel,
                    executor = config.executor,
                    subcommands = config.subcommands
                )
            )
        }
    }

    fun command(
        name: String,
        permission: String = "$modId.command.$name",
        permissionLevel: Int = defaultPermissionLevel,
        opLevel: Int = defaultOpLevel,
        aliases: List<String> = listOf(),
        builder: CommandConfig.() -> Unit
    ) {
        val config = CommandConfig().apply(builder)
        commands.add(
            CommandData(
                name = name,
                permission = permission,
                permissionLevel = permissionLevel,
                opLevel = opLevel,
                aliases = aliases,
                executor = config.executor,
                subcommands = config.subcommands
            )
        )
    }

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            logger.info("Registering commands for $modId")
            commands.forEach { command ->
                registerCommand(dispatcher, command)
            }
        }
    }

    private fun registerCommand(
        dispatcher: CommandDispatcher<ServerCommandSource>,
        commandData: CommandData
    ) {
        val mainCommand = literal(commandData.name)
            .requires { source ->
                hasPermissionOrOp(
                    source,
                    commandData.permission,
                    commandData.permissionLevel,
                    commandData.opLevel
                )
            }

        commandData.executor?.let { executor ->
            mainCommand.executes(executor)
        }

        addSubcommands(
            mainCommand,
            commandData.subcommands,
            commandData.permission,
            commandData.permissionLevel,
            commandData.opLevel
        )

        dispatcher.register(mainCommand)

        commandData.aliases.forEach { alias ->
            dispatcher.register(
                literal(alias)
                    .requires { source ->
                        hasPermissionOrOp(
                            source,
                            commandData.permission,
                            commandData.permissionLevel,
                            commandData.opLevel
                        )
                    }
                    .redirect(dispatcher.root.getChild(commandData.name))
            )
        }
    }

    private fun addSubcommands(
        node: LiteralArgumentBuilder<ServerCommandSource>,
        subcommands: List<SubcommandData>,
        parentPermission: String,
        parentPermissionLevel: Int,
        parentOpLevel: Int
    ) {
        subcommands.forEach { subcommand ->
            val subNode = literal(subcommand.name)
                .requires { source ->
                    hasPermissionOrOp(
                        source,
                        subcommand.permission ?: parentPermission,
                        subcommand.permissionLevel ?: parentPermissionLevel,
                        subcommand.opLevel ?: parentOpLevel
                    )
                }

            subcommand.executor?.let { executor ->
                subNode.executes(executor)
            }

            addSubcommands(
                subNode,
                subcommand.subcommands,
                subcommand.permission ?: parentPermission,
                subcommand.permissionLevel ?: parentPermissionLevel,
                subcommand.opLevel ?: parentOpLevel
            )

            node.then(subNode)
        }
    }

    companion object {
        fun sendSuccess(source: ServerCommandSource, message: String, broadcastToOps: Boolean = false) {
            source.sendFeedback({ Text.literal(message) }, broadcastToOps)
        }

        fun sendError(source: ServerCommandSource, message: String) {
            source.sendError(Text.literal(message))
        }

        fun formatColoredMessage(message: String, color: Int): Text {
            return Text.literal(message).styled { it.withColor(color) }
        }

        private fun hasPermissionOrOp(
            source: ServerCommandSource,
            permission: String,
            permissionLevel: Int,
            opLevel: Int
        ): Boolean {
            val player = source.player

            // If it's a player, check both permission and op level
            return if (player != null) {
                try {
                    // Check if player has permission from permission mod
                    Permissions.check(player, permission, permissionLevel)
                } catch (e: NoClassDefFoundError) {
                    // Fallback to op level if no permission mod
                    player.hasPermissionLevel(opLevel)
                }
            } else {
                // For non-players (like console), use op level
                source.hasPermissionLevel(opLevel)
            }
        }
    }
}