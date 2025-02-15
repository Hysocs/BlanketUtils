package com.blanketutils.utils

import kotlinx.coroutines.runBlocking

class LogDebugTester {
    companion object {
        private const val TEST_MOD_ID = "test_mod"

        private fun testInitialization(): Boolean = runBlocking {
            try {
                // Test basic initialization
                LogDebug.init(TEST_MOD_ID, true)
                val isEnabled = LogDebug.isDebugEnabledForMod(TEST_MOD_ID)
                if (!isEnabled) {
                    //println("[TEST] Debug should be enabled but isn't")
                    return@runBlocking false
                }
                true
            } catch (e: Exception) {
                //println("[TEST] Initialization test failed: ${e.message}")
                false
            }
        }

        private fun testDebugToggle(): Boolean = runBlocking {
            try {
                // Test enabling/disabling debug
                LogDebug.setDebugEnabledForMod(TEST_MOD_ID, false)
                if (LogDebug.isDebugEnabledForMod(TEST_MOD_ID)) {
                    //println("[TEST] Debug should be disabled but isn't")
                    return@runBlocking false
                }

                LogDebug.setDebugEnabledForMod(TEST_MOD_ID, true)
                if (!LogDebug.isDebugEnabledForMod(TEST_MOD_ID)) {
                    //println("[TEST] Debug should be enabled but isn't")
                    return@runBlocking false
                }
                true
            } catch (e: Exception) {
                //println("[TEST] Debug toggle test failed: ${e.message}")
                false
            }
        }

        private fun testDebugOutput(): Boolean = runBlocking {
            try {
                // Test debug output
                LogDebug.init(TEST_MOD_ID, true)
                //LogDebug.debug("Test debug message", TEST_MOD_ID)
                true
            } catch (e: Exception) {
                //println("[TEST] Debug output test failed: ${e.message}")
                false
            }
        }

        private fun testMultipleMods(): Boolean = runBlocking {
            try {
                // Test debug with multiple mods
                val secondModId = "second_test_mod"

                LogDebug.init(TEST_MOD_ID, true)
                LogDebug.init(secondModId, false)

                if (!LogDebug.isDebugEnabledForMod(TEST_MOD_ID)) {
                    //println("[TEST] Debug should be enabled for first mod but isn't")
                    return@runBlocking false
                }

                if (LogDebug.isDebugEnabledForMod(secondModId)) {
                    //println("[TEST] Debug should be disabled for second mod but isn't")
                    return@runBlocking false
                }

                true
            } catch (e: Exception) {
                //println("[TEST] Multiple mods test failed: ${e.message}")
                false
            }
        }

        fun runAllTests(): Map<String, Boolean> = mapOf(
            "initialization" to testInitialization(),
            "debug_toggle" to testDebugToggle(),
            "debug_output" to testDebugOutput(),
            "multiple_mods" to testMultipleMods()
        )
    }
}