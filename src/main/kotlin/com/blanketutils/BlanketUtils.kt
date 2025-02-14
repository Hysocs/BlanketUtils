package com.blanketutils


import com.blanketutils.command.CommandTester
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import com.blanketutils.config.ConfigData
import com.blanketutils.config.ConfigManager
import com.blanketutils.config.ConfigTester
import com.blanketutils.gui.GuiTester
import com.blanketutils.utils.LogDebugTester
import java.nio.file.Paths
import java.util.*

object BlanketUtils : ModInitializer {
    private val logger = LoggerFactory.getLogger("blanketutils")
    const val MOD_ID = "blanketutils"
    const val VERSION = "1.0.0"

    // ANSI color and format codes
    object Colors {
        private const val ESC = "\u001B"
        private const val BOLD = "${ESC}[1m"

        // Helper function to scope color to specific text
        private fun color(text: String, colorCode: String): String {
            return "$colorCode$text${ESC}[39m${if (colorCode.contains(BOLD)) "${ESC}[22m" else ""}"
        }

        // Color wrapper functions
        fun boldPurple(text: String) = color(text, "${ESC}[1;35m")
        fun boldBrightPink(text: String) = color(text, "${ESC}[1;95m")
        fun brightBlack(text: String) = color(text, "${ESC}[90m")
        fun boldYellow(text: String) = color(text, "${ESC}[1;33m")
        fun boldGreen(text: String) = color(text, "${ESC}[1;32m")
        fun boldRed(text: String) = color(text, "${ESC}[1;31m")
        fun brightPurple(text: String) = color(text, "${ESC}[95m")
    }

    private fun getModName(): String {
        return Colors.boldBrightPink("blanket") + Colors.boldPurple("utils")
    }

    override fun onInitialize() {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val prefix = Colors.boldPurple("[") + getModName() + Colors.boldPurple("]")

        logger.info("")
        logger.info("$prefix ${Colors.boldPurple("============================")}")
        logger.info("$prefix ${Colors.brightPurple(getModName() + " v$VERSION")}")
        logger.info("$prefix ${Colors.boldPurple("A Tiny Tool, With Mighty Results.")}")
        logger.info("$prefix ${Colors.brightBlack("Loading at: $timestamp")}")

        // Run config tests
        val results = ConfigTester.runAllTests()
        logger.info("$prefix ${Colors.boldYellow("Running Configuration Tests:")}")
        results.forEach { (testName, passed) ->
            val status = if (passed) Colors.boldGreen("GOOD") else Colors.boldRed("BAD")
            logger.info("$prefix ${Colors.brightBlack("- Test ${testName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault()
                ) else it.toString()
            }}: ")}$status")
        }

        logger.info("$prefix ${Colors.boldYellow("Running GUI System Tests:")}")
        GuiTester.runAllTests().forEach { (testName, passed) ->
            val status = if (passed) Colors.boldGreen("GOOD") else Colors.boldRed("BAD")
            logger.info("$prefix ${Colors.brightBlack("- Test ${testName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault()
                ) else it.toString()
            }}: ")}$status")
        }

        logger.info("$prefix ${Colors.boldYellow("Running Command System Tests:")}")
        CommandTester.runAllTests().forEach { (testName, passed) ->
            val status = if (passed) Colors.boldGreen("GOOD") else Colors.boldRed("BAD")
            logger.info("$prefix ${Colors.brightBlack("- Test ${testName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault()
                ) else it.toString()
            }}: ")}$status")
        }

        // Add LogDebug tests
        logger.info("$prefix ${Colors.boldYellow("Running LogDebug System Tests:")}")
        LogDebugTester.runAllTests().forEach { (testName, passed) ->
            val status = if (passed) Colors.boldGreen("GOOD") else Colors.boldRed("BAD")
            logger.info("$prefix ${Colors.brightBlack("- Test ${testName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }}: ")}$status")
        }

        logger.info("$prefix ${Colors.boldGreen("Successfully initialized!")}")
        logger.info("$prefix ${Colors.brightBlack("Runtime: ${System.getProperty("java.version")}")}")
        logger.info("$prefix ${Colors.brightBlack("Environment: ${System.getProperty("java.vendor")}")}")
        logger.info("$prefix ${Colors.brightBlack("Cores Available: ${Runtime.getRuntime().availableProcessors()}")}")
        logger.info("$prefix ${Colors.brightBlack("Thread Count: ${Thread.activeCount()}")}")
        logger.info("$prefix ${Colors.brightBlack("Memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB allocated")}")
        logger.info("$prefix ${Colors.brightBlack("Report issues at: https://github.com/Hysocs/BlanketUtils")}")
        logger.info("$prefix ${Colors.brightBlack("Or join our discord at: https://discord.gg/nrENPTmQKt")}")
        logger.info("$prefix ${Colors.boldPurple("============================")}")


        logger.info("")
    }
}