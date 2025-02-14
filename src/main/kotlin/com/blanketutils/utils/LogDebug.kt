package com.blanketutils.utils

import java.util.concurrent.ConcurrentHashMap

object LogDebug {
    private val debugStates = ConcurrentHashMap<String, Boolean>()

    /**
     * Initialize debug state for a specific mod
     * @param modId The mod identifier
     * @param debugEnabled Whether debug is enabled for this mod
     */
    fun init(modId: String, debugEnabled: Boolean = false) {
        debugStates[modId] = debugEnabled
        println("[DEBUG-$modId] Initialized debug state: ${if (debugEnabled) "enabled" else "disabled"}")
    }

    /**
     * Print a debug message if debug is enabled for the source mod
     * @param message The message to print
     * @param source The source mod ID
     */
    fun debug(message: String, source: String) {
        if (isDebugEnabledForMod(source)) {
            println("[DEBUG-$source] $message")
        }
    }

    /**
     * Set the debug state for a specific mod
     * @param modId The mod identifier
     * @param enabled Whether debug should be enabled
     */
    fun setDebugEnabledForMod(modId: String, enabled: Boolean) {
        val previousState = debugStates[modId]
        debugStates[modId] = enabled

        // Only print if the state actually changed
        if (previousState != enabled) {
            println("[DEBUG-$modId] Debug mode ${if (enabled) "enabled" else "disabled"}")
        }
    }

    /**
     * Check if debug is enabled for a specific mod
     * @param modId The mod identifier
     * @return Whether debug is enabled for the mod
     */
    fun isDebugEnabledForMod(modId: String): Boolean {
        return debugStates[modId] ?: false
    }
}

// Extension function for easier use
fun logDebug(message: String, source: String) {
    LogDebug.debug(message, source)
}