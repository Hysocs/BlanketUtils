package com.blanketutils.command

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import net.minecraft.text.Text

class CommandTester {
    companion object {
        private val logger = LoggerFactory.getLogger("CommandTester")
        private val commandManager = CommandManager("test_mod")

        private fun testCommandRegistration(): Boolean = runBlocking {
            try {
                // Test basic command registration
                commandManager.command("test") {
                    executes { context ->
                        1
                    }
                }
                true
            } catch (e: Exception) {
                logger.error("Command registration test failed", e)
                false
            }
        }

        private fun testSubcommandStructure(): Boolean = runBlocking {
            try {
                // Test nested command structure
                commandManager.command("parent") {
                    subcommand("child1") {
                        executes { context -> 1 }
                    }
                    subcommand("child2") {
                        subcommand("grandchild") {
                            executes { context -> 1 }
                        }
                    }
                }
                true
            } catch (e: Exception) {
                logger.error("Subcommand structure test failed", e)
                false
            }
        }

        private fun testPermissionConfiguration(): Boolean = runBlocking {
            try {
                // Test permission configuration
                commandManager.command("secured",
                    permission = "test.command.secured",
                    permissionLevel = 2,
                    opLevel = 2
                ) {
                    executes { context -> 1 }
                }
                true
            } catch (e: Exception) {
                logger.error("Permission configuration test failed", e)
                false
            }
        }

        private fun testCommandAliases(): Boolean = runBlocking {
            try {
                // Test command aliases
                commandManager.command("main",
                    aliases = listOf("alt1", "alt2")
                ) {
                    executes { context -> 1 }
                }
                true
            } catch (e: Exception) {
                logger.error("Command aliases test failed", e)
                false
            }
        }

        fun runAllTests(): Map<String, Boolean> = mapOf(
            "registration" to testCommandRegistration(),
            "subcommands" to testSubcommandStructure(),
            "permissions" to testPermissionConfiguration(),
            "aliases" to testCommandAliases()
        )
    }
}