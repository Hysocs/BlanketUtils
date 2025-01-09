package com.blanketutils.config

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path


data class TestConfig(
    override var version: String = "1.0",
    override var configId: String = "test",
    var testSetting: String = "default",
    var numericSetting: Int = 42
) : ConfigData

class ConfigTester(private val testDir: Path = Files.createTempDirectory("config_test")) {
    private var configManager: ConfigManager<TestConfig>? = null

    private fun setupConfigManager(): ConfigManager<TestConfig> {
        val defaultConfig = TestConfig()
        return ConfigManager(
            currentVersion = "1.0",
            defaultConfig = defaultConfig,
            configClass = TestConfig::class,
            configDir = testDir,
            metadata = ConfigMetadata(
                headerComments = listOf("Test Configuration"),
                includeTimestamp = false
            )
        )
    }

    fun testReload(): Boolean = runBlocking {
        try {
            configManager = setupConfigManager()
            val configFile = testDir.resolve("test/config.jsonc")

            val modifiedContent = """
                /* CONFIG_SECTION
                 * Test Configuration
                 * Version: 1.0
                 */
                {
                  "version": "1.0",
                  "configId": "test",
                  "testSetting": "modified",
                  "numericSetting": 100
                }
                /* END_CONFIG_SECTION */
            """.trimIndent()
            Files.writeString(configFile, modifiedContent)

            configManager?.reloadConfig()
            val reloadedConfig = configManager?.getCurrentConfig()
            val success = reloadedConfig?.testSetting == "modified" &&
                    reloadedConfig.numericSetting == 100

            success
        } catch (e: Exception) {
            false
        } finally {
            cleanup()
        }
    }

    fun testSelfHeal(): Boolean = runBlocking {
        try {
            configManager = setupConfigManager()
            val configFile = testDir.resolve("test/config.jsonc")
            Files.writeString(configFile, "{ invalid json }")

            configManager?.reloadConfig()
            val healedConfig = configManager?.getCurrentConfig()
            val success = healedConfig?.testSetting == "default" &&
                    healedConfig.numericSetting == 42

            success
        } catch (e: Exception) {
            false
        } finally {
            cleanup()
        }
    }

    fun testVersionMigration(): Boolean = runBlocking {
        try {
            // Set up the configuration manager with a test directory
            configManager = setupConfigManager()
            val configFile = testDir.resolve("test/config.jsonc")

            // Write old version configuration
            val oldVersionContent = """
            {
              "version": "0.9",
              "configId": "test",
              "testSetting": "old_value",
              "numericSetting": 99
            }
        """.trimIndent()
            Files.createDirectories(configFile.parent)
            Files.writeString(configFile, oldVersionContent)

            // Reload the configuration to trigger migration
            configManager?.reloadConfig()

            // Retrieve the migrated configuration
            val migratedConfig = configManager?.getCurrentConfig()

            // Check the migration results
            val success = migratedConfig?.version == "1.0" &&
                    migratedConfig.testSetting == "old_value" &&
                    migratedConfig.numericSetting == 99

            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            cleanup()
        }
    }


    fun testBackupCreation(): Boolean = runBlocking {
        try {
            configManager = setupConfigManager()
            val backupDir = testDir.resolve("test/backups")

            val configFile = testDir.resolve("test/config.jsonc")
            Files.writeString(configFile, "{ corrupt json }")
            configManager?.reloadConfig()

            val hasBackup = Files.list(backupDir).use { files ->
                files.anyMatch { it.toString().contains("json_error") }
            }

            hasBackup
        } catch (e: Exception) {
            false
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        try {
            configManager?.cleanup()
            Files.walk(testDir)
                .sorted(Comparator.reverseOrder())
                .forEach { path ->
                    try {
                        Files.deleteIfExists(path)
                    } catch (_: Exception) {}
                }
        } catch (_: Exception) {}
    }

    companion object {
        fun runAllTests(): Map<String, Boolean> {
            return mapOf(
                "reload" to ConfigTester().testReload(),
                "selfHeal" to ConfigTester().testSelfHeal(),
                "versionMigration" to ConfigTester().testVersionMigration(),
                "backupCreation" to ConfigTester().testBackupCreation()
            )
        }
    }
}