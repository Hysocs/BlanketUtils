package com.blanketutils

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import com.blanketutils.config.ConfigData
import com.blanketutils.config.ConfigManager
import com.blanketutils.config.ConfigTester
import java.nio.file.Paths

object BlanketUtils : ModInitializer {
    private val logger = LoggerFactory.getLogger("blanketutils")
    const val MOD_ID = "blanketutils"
    const val VERSION = "1.0.0"

    // ANSI color and format codes
    private object Colors {
        const val RESET = "\u001B[0m"
        const val BOLD = "\u001B[1m"

        // Regular colors
        const val YELLOW = "\u001B[33m"
        const val GREEN = "\u001B[32m"
        const val RED = "\u001B[31m"
        const val PURPLE = "\u001B[35m"

        // Bright colors
        const val BRIGHT_BLACK = "\u001B[90m"
        const val BRIGHT_YELLOW = "\u001B[93m"
        const val BRIGHT_GREEN = "\u001B[92m"
        const val BRIGHT_PURPLE = "\u001B[95m"

        // Compound styles
        const val BOLD_YELLOW = "\u001B[1;33m"
        const val BOLD_GREEN = "\u001B[1;32m"
        const val BOLD_RED = "\u001B[1;31m"
        const val BOLD_PURPLE = "\u001B[1;35m"
        const val BOLD_BRIGHT_PINK = "\u001B[1;95m"   // Bright bold pink
    }

    private fun getModName(): String {
        return "${Colors.BOLD_BRIGHT_PINK}blanket${Colors.BOLD_PURPLE}utils"
    }

    override fun onInitialize() {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val prefix = "${Colors.BOLD_PURPLE}[${getModName()}${Colors.BOLD_PURPLE}]${Colors.RESET}"

        logger.info("")
        logger.info("$prefix ${Colors.BOLD_PURPLE}============================")
        logger.info("$prefix ${Colors.BOLD}${Colors.BRIGHT_PURPLE}${getModName()} v$VERSION${Colors.RESET}")
        logger.info("$prefix ${Colors.BRIGHT_BLACK}Loading at: $timestamp")

        // Run config tests
        val results = ConfigTester.runAllTests()
        logger.info("$prefix ${Colors.BOLD_YELLOW}Running Configuration Tests:")
        results.forEach { (testName, passed) ->
            val status = if (passed) "${Colors.BOLD_GREEN}✓" else "${Colors.BOLD_RED}✗"
            logger.info("$prefix ${Colors.BRIGHT_BLACK}- Test ${testName.capitalize()}: $status${Colors.RESET}")
        }

        logger.info("$prefix ${Colors.BOLD_GREEN}Successfully initialized!${Colors.RESET}")
        logger.info("$prefix ${Colors.BRIGHT_BLACK}Runtime: ${System.getProperty("java.version")}")
        logger.info("$prefix ${Colors.BRIGHT_BLACK}OS: ${System.getProperty("os.name")}")
        logger.info("$prefix ${Colors.BOLD_PURPLE}============================")
        logger.info("")
    }
}