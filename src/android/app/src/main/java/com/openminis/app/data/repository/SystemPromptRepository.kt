package com.openminis.app.data.repository

import com.openminis.app.logging.AppLogger
import java.io.File

/**
 * Repository for persistent system prompt files stored in a dedicated directory:
 *   - SYSTEM.md: custom base system prompt (falls back to built-in when absent)
 *   - APPEND_SYSTEM.md: append prompt automatically concatenated to the system prompt
 *
 * Stored under `minis-global/prompts/` to ensure full isolation from `minis-global/memory/`
 * (MemoryRepository), preventing system prompt files from ever appearing in the memory files UI.
 */
class SystemPromptRepository(
    private val promptsDir: File,
    private val legacyMemoryDir: File? = null,
) {
    companion object {
        const val TAG = "SystemPromptRepository"
        const val SYSTEM_PROMPT_FILE = "SYSTEM.md"
        const val APPEND_SYSTEM_PROMPT_FILE = "APPEND_SYSTEM.md"
        const val LEGACY_APPEND_SYSTEM_PROMPT_FILE = "APPEND.SYSTEM.md"
    }

    init {
        try {
            if (!promptsDir.exists()) {
                promptsDir.mkdirs()
            }
            migrateAndCleanLegacyFiles()
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "Failed during init migration: ${t.message}")
        }
    }

    /**
     * Migrate legacy prompt files stored in [legacyMemoryDir] to [promptsDir],
     * and delete legacy files from [legacyMemoryDir] to clean up memory directory pollution.
     */
    fun migrateAndCleanLegacyFiles() {
        val targetSys = File(promptsDir, SYSTEM_PROMPT_FILE)
        val targetApp = File(promptsDir, APPEND_SYSTEM_PROMPT_FILE)

        // Handle promptsDir-local legacy APPEND.SYSTEM.md
        val localLegacyApp = File(promptsDir, LEGACY_APPEND_SYSTEM_PROMPT_FILE)
        if (localLegacyApp.exists()) {
            if (!targetApp.exists()) {
                try {
                    val content = localLegacyApp.readText()
                    targetApp.writeText(content)
                } catch (t: Throwable) {
                    AppLogger.warning(TAG, "Failed migrating local legacy append prompt: ${t.message}")
                }
            }
            try {
                localLegacyApp.delete()
            } catch (_: Throwable) {}
        }

        // Migrate and clean legacyMemoryDir
        if (legacyMemoryDir != null && legacyMemoryDir.exists()) {
            val oldSys = File(legacyMemoryDir, SYSTEM_PROMPT_FILE)
            if (oldSys.exists()) {
                if (!targetSys.exists()) {
                    try {
                        val content = oldSys.readText()
                        targetSys.writeText(content)
                    } catch (t: Throwable) {
                        AppLogger.warning(TAG, "Failed migrating old SYSTEM.md: ${t.message}")
                    }
                }
                try {
                    oldSys.delete()
                } catch (_: Throwable) {}
            }

            val oldApp = File(legacyMemoryDir, APPEND_SYSTEM_PROMPT_FILE)
            val oldLegacyApp = File(legacyMemoryDir, LEGACY_APPEND_SYSTEM_PROMPT_FILE)

            if (!targetApp.exists()) {
                val content = when {
                    oldApp.exists() -> try { oldApp.readText() } catch (_: Throwable) { null }
                    oldLegacyApp.exists() -> try { oldLegacyApp.readText() } catch (_: Throwable) { null }
                    else -> null
                }
                if (!content.isNullOrBlank()) {
                    try {
                        targetApp.writeText(content)
                    } catch (t: Throwable) {
                        AppLogger.warning(TAG, "Failed migrating old append prompt: ${t.message}")
                    }
                }
            }

            try { oldApp.delete() } catch (_: Throwable) {}
            try { oldLegacyApp.delete() } catch (_: Throwable) {}
        }
    }

    /**
     * Load the custom SYSTEM.md content, or empty string if not set.
     */
    fun loadSystemPrompt(): String {
        val file = File(promptsDir, SYSTEM_PROMPT_FILE)
        return if (file.exists()) {
            try { file.readText() } catch (_: Throwable) { "" }
        } else ""
    }

    /**
     * Load the APPEND_SYSTEM.md content, or empty string if not set.
     */
    fun loadAppendPrompt(): String {
        val file = File(promptsDir, APPEND_SYSTEM_PROMPT_FILE)
        if (file.exists()) {
            return try { file.readText() } catch (_: Throwable) { "" }
        }
        val legacyFile = File(promptsDir, LEGACY_APPEND_SYSTEM_PROMPT_FILE)
        if (legacyFile.exists()) {
            val content = try { legacyFile.readText() } catch (_: Throwable) { "" }
            if (content.isNotBlank()) {
                try {
                    file.writeText(content)
                    legacyFile.delete()
                } catch (_: Throwable) {}
                return content
            }
        }
        return ""
    }

    /**
     * Load both system prompt and append prompt.
     */
    fun loadSystemPromptFiles(): Pair<String, String> {
        return loadSystemPrompt() to loadAppendPrompt()
    }

    /**
     * Save both system prompt and append prompt files.
     * Empty or blank strings delete the corresponding file.
     * Only [APPEND_SYSTEM_PROMPT_FILE] is written for append prompt (never duplicates).
     */
    fun saveSystemPromptFiles(systemMd: String, appendMd: String) {
        val sysFile = File(promptsDir, SYSTEM_PROMPT_FILE)
        if (systemMd.isBlank()) {
            try { sysFile.delete() } catch (_: Throwable) {}
        } else {
            try { sysFile.writeText(systemMd) } catch (t: Throwable) {
                AppLogger.warning(TAG, "Failed writing SYSTEM.md: ${t.message}")
            }
        }

        val appFile = File(promptsDir, APPEND_SYSTEM_PROMPT_FILE)
        val legacyAppFile = File(promptsDir, LEGACY_APPEND_SYSTEM_PROMPT_FILE)
        try { legacyAppFile.delete() } catch (_: Throwable) {}

        if (appendMd.isBlank()) {
            try { appFile.delete() } catch (_: Throwable) {}
        } else {
            try { appFile.writeText(appendMd) } catch (t: Throwable) {
                AppLogger.warning(TAG, "Failed writing APPEND_SYSTEM.md: ${t.message}")
            }
        }

        // Clean up legacyMemoryDir if present
        if (legacyMemoryDir != null && legacyMemoryDir.exists()) {
            try { File(legacyMemoryDir, SYSTEM_PROMPT_FILE).delete() } catch (_: Throwable) {}
            try { File(legacyMemoryDir, APPEND_SYSTEM_PROMPT_FILE).delete() } catch (_: Throwable) {}
            try { File(legacyMemoryDir, LEGACY_APPEND_SYSTEM_PROMPT_FILE).delete() } catch (_: Throwable) {}
        }
    }

    fun deleteSystemPrompt() {
        try { File(promptsDir, SYSTEM_PROMPT_FILE).delete() } catch (_: Throwable) {}
    }

    fun deleteAppendPrompt() {
        try { File(promptsDir, APPEND_SYSTEM_PROMPT_FILE).delete() } catch (_: Throwable) {}
        try { File(promptsDir, LEGACY_APPEND_SYSTEM_PROMPT_FILE).delete() } catch (_: Throwable) {}
    }
}
