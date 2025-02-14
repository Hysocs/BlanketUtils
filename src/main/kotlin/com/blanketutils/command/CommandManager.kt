package com.blanketutils.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.LoggerFactory
import java.util.function.Supplier

/**
 * Enhanced utility class for command registration in Fabric mods with full Brigadier support
 */
class CommandManager(
    private val modId: String,
    private val defaultPermissionLevel: Int = 2,
    private val defaultOpLevel: Int = 2
) {
    private val logger = LoggerFactory.getLogger("$modId-Commands")
    private val commands = mutableListOf<CommandData>()
    private val allPermissions = mutableSetOf<String>()

    data class CommandData(
        val name: String,
        val permission: String,
        val permissionLevel: Int,
        val opLevel: Int,
        val aliases: List<String>,
        val executor: ((CommandContext<ServerCommandSource>) -> Int)?,
        val argumentBuilder: (ArgumentBuilder<ServerCommandSource, *>.() -> Unit)?,
        val subcommands: List<SubcommandData>
    )

    data class SubcommandData(
        val name: String,
        val permission: String?,
        val permissionLevel: Int?,
        val opLevel: Int?,
        val executor: ((CommandContext<ServerCommandSource>) -> Int)?,
        val argumentBuilder: (ArgumentBuilder<ServerCommandSource, *>.() -> Unit)?,
        val subcommands: List<SubcommandData>
    )

    inner class CommandConfig {
        internal var executor: ((CommandContext<ServerCommandSource>) -> Int)? = null
        internal var argumentBuilder: (ArgumentBuilder<ServerCommandSource, *>.() -> Unit)? = null
        internal val subcommands = mutableListOf<SubcommandData>()

        fun executes(executor: (CommandContext<ServerCommandSource>) -> Int) {
            this.executor = executor
        }

        fun then(argument: ArgumentBuilder<ServerCommandSource, *>) {
            val currentBuilder = argumentBuilder
            argumentBuilder = {
                if (currentBuilder != null) {
                    currentBuilder.invoke(this)
                }
                then(argument)
            }
        }

        fun suggests(provider: SuggestionProvider<ServerCommandSource>) {
            val currentBuilder = argumentBuilder
            argumentBuilder = {
                if (currentBuilder != null) {
                    currentBuilder.invoke(this)
                }
                suggests(provider)
            }
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
                    argumentBuilder = config.argumentBuilder,
                    subcommands = config.subcommands
                )
            )
        }
    }

    inner class SubcommandConfig {
        internal var executor: ((CommandContext<ServerCommandSource>) -> Int)? = null
        internal var argumentBuilder: (ArgumentBuilder<ServerCommandSource, *>.() -> Unit)? = null
        internal val subcommands = mutableListOf<SubcommandData>()

        fun executes(executor: (CommandContext<ServerCommandSource>) -> Int) {
            this.executor = executor
        }

        fun then(argument: ArgumentBuilder<ServerCommandSource, *>) {
            val currentBuilder = argumentBuilder
            argumentBuilder = {
                if (currentBuilder != null) {
                    currentBuilder.invoke(this)
                }
                then(argument)
            }
        }

        fun suggests(provider: SuggestionProvider<ServerCommandSource>) {
            val currentBuilder = argumentBuilder
            argumentBuilder = {
                if (currentBuilder != null) {
                    currentBuilder.invoke(this)
                }
                suggests(provider)
            }
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
                    argumentBuilder = config.argumentBuilder,
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
                argumentBuilder = config.argumentBuilder,
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

        commandData.argumentBuilder?.let { builder ->
            builder.invoke(mainCommand)
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

            subcommand.argumentBuilder?.let { builder ->
                builder.invoke(subNode)
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

    // Keep track of all permissions
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

    fun simulatePermissionChecks(server: MinecraftServer) {
        logger.info("Simulating permission checks for $modId commands...")

        val players = server.playerManager.playerList
        if (players.isEmpty()) {
            logger.info("No players online. Permission simulation will only check console permissions.")
        }

        allPermissions.forEach { permission ->
            logger.debug("Simulating checks for permission: $permission")

            try {
                server.commandSource.hasPermissionLevel(defaultOpLevel)
                logger.debug("Console permission check successful for: $permission")
            } catch (e: Exception) {
                logger.warn("Failed to check console permission for: $permission", e)
            }

            players.forEach { player ->
                simulatePlayerPermissionCheck(player, permission)
            }
        }

        logger.info("Permission simulation completed for ${allPermissions.size} permissions.")
    }

    private fun simulatePlayerPermissionCheck(player: ServerPlayerEntity, permission: String) {
        try {
            try {
                Permissions.check(player, permission, defaultPermissionLevel)
                logger.debug("Permission API check successful for player ${player.name.string}: $permission")
            } catch (e: NoClassDefFoundError) {
                player.hasPermissionLevel(defaultOpLevel)
                logger.debug("Vanilla permission check successful for player ${player.name.string}: $permission")
            }
        } catch (e: Exception) {
            logger.warn("Failed to check permission for player ${player.name.string}: $permission", e)
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

        fun hasPermissionOrOp(
            source: ServerCommandSource,
            permission: String,
            permissionLevel: Int,
            opLevel: Int
        ): Boolean {
            val player = source.player

            return if (player != null) {
                try {
                    Permissions.check(player, permission, permissionLevel)
                } catch (e: NoClassDefFoundError) {
                    player.hasPermissionLevel(opLevel)
                }
            } else {
                source.hasPermissionLevel(opLevel)
            }
        }
    }
}