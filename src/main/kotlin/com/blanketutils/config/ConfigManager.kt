package com.blanketutils.config

import com.google.gson.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors
import kotlin.io.path.exists
import kotlin.reflect.KClass
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.full.memberProperties

interface ConfigData {
    val version: String
    val configId: String
}

data class ConfigMetadata(
    val headerComments: List<String> = emptyList(),
    val footerComments: List<String> = emptyList(),
    val sectionComments: Map<String, String> = emptyMap(),
    val includeTimestamp: Boolean = true,
    val includeVersion: Boolean = true,
    val watcherSettings: WatcherSettings = WatcherSettings()
) {
    companion object {
        fun default(configId: String) = ConfigMetadata(
            headerComments = listOf(
                "Configuration file for $configId",
                "This file is automatically managed - custom comments will be preserved"
            )
        )
    }
}

class ConfigMigrationResult<T : ConfigData>(
    val migratedConfig: T,
    val migratedFields: Set<String>,
    val skippedFields: Set<String>
)

class JsoncParser {
    companion object {
        private val SINGLE_LINE_COMMENT = """//[^\n]*""".toRegex()
        private val MULTI_LINE_COMMENT = """/\*[\s\S]*?\*/""".toRegex()
        private val CONFIG_SECTION = """\/\*\s*CONFIG_SECTION\s*\*\/([\s\S]*?)(?:\/\*\s*END_CONFIG_SECTION\s*\*\/|$)""".toRegex()
        private val TRAILING_COMMA = """,(\s*[}\]])""".toRegex()
    }

    fun parseWithComments(content: String): Pair<String, Map<String, String>> {
        val comments = mutableMapOf<String, String>()
        var processedContent = content

        // Remove trailing commas before JSON parsing
        processedContent = processedContent.replace(TRAILING_COMMA, "$1")

        content.split("\n").forEach { line ->
            SINGLE_LINE_COMMENT.find(line)?.let { match ->
                val propertyName = line.substringBefore("//").trim()
                if (propertyName.isNotBlank()) {
                    comments[propertyName] = match.value.substring(2).trim()
                }
            }
        }

        processedContent = processedContent.replace(SINGLE_LINE_COMMENT, "")
        processedContent = processedContent.replace(MULTI_LINE_COMMENT, "")

        return processedContent.trim() to comments
    }

    fun extractConfigSection(content: String): String? {
        return CONFIG_SECTION.find(content)?.groupValues?.get(1)
    }
}

data class WatcherSettings(
    val enabled: Boolean = false,            // Whether the file watcher is enabled
    val debounceMs: Long = 1000,            // Debounce time in milliseconds
    val autoSaveEnabled: Boolean = false,    // Whether auto-save is enabled - now false by default
    val autoSaveIntervalMs: Long = 30_000   // Auto-save interval in milliseconds
)

class ConfigManager<T : ConfigData>(
    private val currentVersion: String,
    private val defaultConfig: T,
    private val configClass: KClass<T>,
    private val configDir: Path = Paths.get("config"),
    private val metadata: ConfigMetadata = ConfigMetadata.default(defaultConfig.configId)
) {
    private val logger = LoggerFactory.getLogger("ConfigManager-${defaultConfig.configId}")
    private val configFile = configDir.resolve("${defaultConfig.configId}/config.jsonc")
    private val backupDir = configDir.resolve("${defaultConfig.configId}/backups")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Initialize all state-related properties
    private val configData = AtomicReference(defaultConfig)
    private val lastValidConfig = AtomicReference(defaultConfig)
    private val lastSavedHash = AtomicInteger(defaultConfig.hashCode())
    private val currentComments = ConcurrentHashMap<String, String>()
    private val lastModifiedTime = AtomicLong(0)
    private val lastFileSize = AtomicLong(0)

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .setLenient()
        .serializeNulls()
        .create()

    private val parser = JsoncParser()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    private var watcherJob: Job? = null
    private var autoSaveJob: Job? = null

    init {
        runBlocking {
            initializeConfig()
            if (metadata.watcherSettings.enabled) {
                setupWatcher()
            }
            if (metadata.watcherSettings.autoSaveEnabled) {
                startAutoSave()
            }
        }
    }

    private suspend fun initializeConfig() = withContext(Dispatchers.IO) {
        Files.createDirectories(configFile.parent)
        Files.createDirectories(backupDir)

        if (!configFile.exists()) {
            saveConfig(defaultConfig)
        } else {
            reloadConfig()
        }
    }

    private fun hasFileChanged(): Boolean {
        return try {
            val attrs = Files.readAttributes(configFile, BasicFileAttributes::class.java)
            val currentModTime = attrs.lastModifiedTime().toMillis()
            val currentSize = attrs.size()

            val changed = currentModTime > lastModifiedTime.get() ||
                    currentSize != lastFileSize.get()

            if (changed) {
                lastModifiedTime.set(currentModTime)
                lastFileSize.set(currentSize)
            }

            changed
        } catch (e: Exception) {
            logger.debug("Error checking file changes: ${e.message}")
            false
        }
    }

    private fun setupWatcher() {
        watcherJob?.cancel()
        watcherJob = scope.launch {
            try {
                val watcher = FileSystems.getDefault().newWatchService()
                configFile.parent.register(watcher, ENTRY_MODIFY)

                while (isActive) {
                    val key = withContext(Dispatchers.IO) { watcher.take() }
                    for (event in key.pollEvents()) {
                        if (event.context() == configFile.fileName) {
                            delay(metadata.watcherSettings.debounceMs)
                            if (hasFileChanged()) {
                                reloadConfig()
                            }
                        }
                    }
                    key.reset()
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    logger.debug("Config watcher stopped: ${e.message}")
                }
            }
        }
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            while (isActive) {
                delay(metadata.watcherSettings.autoSaveIntervalMs)
                if (hasChanges()) {
                    saveConfig(configData.get())
                }
            }
        }
    }

    private suspend fun readConfigFile(): String = withContext(Dispatchers.IO) {
        Files.newBufferedReader(configFile, Charsets.UTF_8).use { reader ->
            reader.readText()
        }
    }

    private suspend fun writeConfigFile(content: String) = withContext(Dispatchers.IO) {
        Files.newBufferedWriter(configFile, Charsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { writer ->
            writer.write(content)
            writer.flush()
        }
    }

    private fun hasChanges(): Boolean =
        configData.get().hashCode() != lastSavedHash.get()

    suspend fun reloadConfig() = withContext(Dispatchers.IO) {
        if (!configFile.exists()) {
            saveConfig(defaultConfig)
            return@withContext
        }

        if (!hasFileChanged()) {
            return@withContext
        }

        try {
            val content = readConfigFile()
            if (content.isBlank()) {
                handleConfigError("empty_file")
                return@withContext
            }

            val (jsonContent, comments) = parser.parseWithComments(content)
            if (jsonContent.isBlank()) {
                handleConfigError("parse_error")
                return@withContext
            }

            try {
                val parsedConfig = gson.fromJson(jsonContent, configClass.java)
                currentComments.clear()
                currentComments.putAll(comments)

                if (parsedConfig.version != currentVersion) {
                    handleVersionMismatch(parsedConfig)
                } else {
                    configData.set(parsedConfig)
                    lastValidConfig.set(parsedConfig)
                    lastSavedHash.set(parsedConfig.hashCode())
                }
            } catch (e: JsonSyntaxException) {
                handleConfigError("json_error")
            }
        } catch (e: Exception) {
            if (configFile.exists()) {
                handleConfigError("reload_error")
            }
        }
    }

    private suspend fun handleConfigError(reason: String) {
        createBackup(reason)

        // First try to restore from backup
        restoreFromBackup()?.let { restoredConfig ->
            configData.set(restoredConfig)
            lastValidConfig.set(restoredConfig)
            saveConfig(restoredConfig)
            logger.debug("Restored configuration from backup after $reason")
            return
        }

        // If no backup, use last valid config instead of default
        val lastKnownGood = lastValidConfig.get()
        if (lastKnownGood != defaultConfig) {
            configData.set(lastKnownGood)
            saveConfig(lastKnownGood)
            logger.debug("Restored last valid configuration after $reason")
        } else {
            // Only use default config if we have no other option
            configData.set(defaultConfig)
            saveConfig(defaultConfig)
            logger.debug("Reset to default configuration after $reason")
        }
    }

    private suspend fun handleVersionMismatch(oldConfig: T) {
        createBackup("pre_migration")
        val currentConfig = configData.get()

        // Merge in this order: oldConfig -> currentConfig -> defaultConfig
        val mergedConfig = mergeConfigs(
            mergeConfigs(oldConfig, currentConfig),
            defaultConfig
        )

        configData.set(mergedConfig)
        lastValidConfig.set(mergedConfig)
        saveConfig(mergedConfig)
    }

    private fun mergeConfigs(oldConfig: T, newConfig: T): T {
        val oldConfigJson = gson.toJsonTree(oldConfig).asJsonObject
        val newConfigJson = gson.toJsonTree(newConfig).asJsonObject

        oldConfigJson.entrySet().forEach { (key, oldValue) ->
            if (key != "version" && newConfigJson.has(key)) {
                newConfigJson.add(key, oldValue)
            }
        }

        newConfigJson.addProperty("version", currentVersion)
        return gson.fromJson(newConfigJson, configClass.java)
    }

    private suspend fun createBackup(reason: String) = withContext(Dispatchers.IO) {
        try {
            val timestamp = LocalDateTime.now().format(dateFormatter)
            val backupFile = backupDir.resolve("${defaultConfig.configId}_${reason}_$timestamp.jsonc")
            Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING)

            Files.list(backupDir).use { stream ->
                stream.filter { it.toString().endsWith(".jsonc") }
                    .sorted(Comparator.reverseOrder())
                    .skip(5)
                    .forEach { Files.delete(it) }
            }
        } catch (e: Exception) {
            logger.debug("Backup failed: ${e.message}")
        }
    }

    private suspend fun restoreFromBackup(): T? = withContext(Dispatchers.IO) {
        try {
            Files.list(backupDir).use { stream ->
                val latestBackup = stream
                    .filter { it.toString().endsWith(".jsonc") }
                    .max { a, b ->
                        a.fileName.toString().compareTo(b.fileName.toString())
                    }
                    .orElse(null)

                latestBackup?.let {
                    val content = Files.newBufferedReader(it, Charsets.UTF_8).use { reader ->
                        reader.readText()
                    }
                    val (jsonContent, _) = parser.parseWithComments(content)
                    gson.fromJson(jsonContent, configClass.java)
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to restore from backup: ${e.message}")
            null
        }
    }

    private suspend fun saveConfig(config: T) = withContext(Dispatchers.IO) {
        try {
            val content = buildConfigContent(config)
            writeConfigFile(content)
            lastSavedHash.set(config.hashCode())
        } catch (e: Exception) {
            logger.debug("Save failed: ${e.message}")
        }
    }

    private fun buildConfigContent(config: T): String = buildString {
        append(buildHeaderComment())
        append("\n{")

        val jsonContent = gson.toJson(config)
        val jsonObject = JsonParser.parseString(jsonContent).asJsonObject

        jsonObject.entrySet().toList().forEachIndexed { index, (key, value) ->
            val isLast = index == jsonObject.size() - 1
            append("\n")

            metadata.sectionComments[key]?.let { sectionComment ->
                sectionComment.lines().forEach { line ->
                    append("  // $line\n")
                }
            }

            currentComments[key]?.let { comment ->
                append("  // $comment\n")
            }

            append("  \"$key\": ${gson.toJson(value)}")
            if (!isLast) append(",")
        }

        append("\n}")
        append(buildFooterComment())
    }

    private fun buildHeaderComment(): String = buildString {
        append("/* CONFIG_SECTION\n")
        metadata.headerComments.forEach { append(" * $it\n") }
        if (metadata.includeVersion) append(" * Version: $currentVersion\n")
        if (metadata.includeTimestamp) append(" * Last updated: ${LocalDateTime.now()}\n")
        append(" */\n")
    }

    private fun buildFooterComment(): String = buildString {
        append("\n/*\n")
        metadata.footerComments.forEach { append(" * $it\n") }
        append(" * END_CONFIG_SECTION\n */")
    }

    fun getCurrentConfig(): T = configData.get()

    suspend fun reloadManually() {
        reloadConfig()
    }

    fun enableWatcher() {
        if (!metadata.watcherSettings.enabled) {
            metadata.copy(watcherSettings = metadata.watcherSettings.copy(enabled = true))
            setupWatcher()
        }
    }

    fun disableWatcher() {
        watcherJob?.cancel()
        watcherJob = null
    }

    fun enableAutoSave() {
        if (!metadata.watcherSettings.autoSaveEnabled) {
            metadata.copy(watcherSettings = metadata.watcherSettings.copy(autoSaveEnabled = true))
            startAutoSave()
        }
    }

    fun disableAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    fun cleanup() {
        watcherJob?.cancel()
        autoSaveJob?.cancel()
        scope.cancel()
    }
}
