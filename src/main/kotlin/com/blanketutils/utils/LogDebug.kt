package com.blanketutils.utils

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object LogDebug {
    private val loggers = ConcurrentHashMap<String, org.slf4j.Logger>()
    private var isDebugEnabled = false
    private var defaultLogLevel = LogLevel.ERROR

    enum class LogLevel {
        ERROR,
        WARN,
        INFO,
        DEBUG,
        TRACE
    }

    /**
     * Initialize the logging system
     * @param debugEnabled Whether debug logging is enabled
     * @param level Default log level to use
     */
    fun init(debugEnabled: Boolean = false, level: LogLevel = LogLevel.ERROR) {
        isDebugEnabled = debugEnabled
        defaultLogLevel = level
    }

    /**
     * Log a debug message if debug is enabled
     * @param message The message to log
     * @param source The source of the log message (defaults to "blanketutils")
     */
    fun debug(message: String, source: String = "blanketutils") {
        if (isDebugEnabled) {
            getLogger(source).debug(message)
        }
    }

    /**
     * Log an error message
     * @param message The message to log
     * @param source The source of the log message (defaults to "blanketutils")
     */
    fun error(message: String, source: String = "blanketutils") {
        getLogger(source).error(message)
    }

    /**
     * Log a warning message
     * @param message The message to log
     * @param source The source of the log message (defaults to "blanketutils")
     */
    fun warn(message: String, source: String = "blanketutils") {
        getLogger(source).warn(message)
    }

    /**
     * Log an info message
     * @param message The message to log
     * @param source The source of the log message (defaults to "blanketutils")
     */
    fun info(message: String, source: String = "blanketutils") {
        getLogger(source).info(message)
    }

    /**
     * Log a message at the current default level
     * @param message The message to log
     * @param source The source of the log message (defaults to "blanketutils")
     */
    operator fun invoke(message: String, source: String = "blanketutils") {
        when (defaultLogLevel) {
            LogLevel.ERROR -> error(message, source)
            LogLevel.WARN -> warn(message, source)
            LogLevel.INFO -> info(message, source)
            LogLevel.DEBUG -> debug(message, source)
            LogLevel.TRACE -> getLogger(source).trace(message)
        }
    }

    private fun getLogger(source: String): org.slf4j.Logger {
        return loggers.computeIfAbsent(source) { LoggerFactory.getLogger(it) }
    }

    /**
     * Set the debug state
     * @param enabled Whether debug logging should be enabled
     */
    fun setDebugEnabled(enabled: Boolean) {
        isDebugEnabled = enabled
    }

    /**
     * Set the default log level
     * @param level The log level to use by default
     */
    fun setDefaultLogLevel(level: LogLevel) {
        defaultLogLevel = level
    }
}

// Extension function to make it easier to use in other classes
fun logDebug(message: String, source: String = "blanketutils") = LogDebug.debug(message, source)