package com.blanketutils.command

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

class CommandTester(private val modId: String = "test_mod") {
    private var commandManager: CommandManager? = null

    private fun setupCommandManager(): CommandManager {
        return CommandManager(
            modId = modId,
            logger = LoggerFactory.getLogger("BlanketUtils-CommandTester")
        )
    }

    // Test that we can initialize the command manager
    fun testInitialization(): Boolean = runBlocking {
        try {
            commandManager = setupCommandManager()
            true
        } catch (e: Exception) {
            false
        }
    }

    // Test the class availability checker
    fun testClassAvailability(): Boolean = runBlocking {
        try {
            commandManager = setupCommandManager()

            // Test with a class we know exists (java.lang.String)
            val realClass = commandManager?.checkClassAvailable("java.lang.String") ?: false

            // Test with a class we know doesn't exist
            val fakeClass = commandManager?.checkClassAvailable("com.fake.NonExistentClass") ?: true

            // Both checks should work correctly
            realClass && !fakeClass
        } catch (e: Exception) {
            false
        }
    }

    // Test command registration callback
    fun testCommandRegistration(): Boolean = runBlocking {
        try {
            commandManager = setupCommandManager()

            // Attempt to register a command - if no exception is thrown, registration worked
            commandManager?.registerCommand { dispatcher ->
                // Just a dummy command for testing
                with(dispatcher) { }
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    private fun cleanup() {
        commandManager = null
    }

    companion object {
        fun runAllTests(): Map<String, Boolean> {
            return mapOf(
                "initialization" to CommandTester().testInitialization(),
                "classAvailability" to CommandTester().testClassAvailability(),
                "registration" to CommandTester().testCommandRegistration()
            )
        }
    }
}