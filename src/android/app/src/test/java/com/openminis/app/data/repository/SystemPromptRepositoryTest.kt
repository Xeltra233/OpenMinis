package com.openminis.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SystemPromptRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun loadAndSave_singleCanonicalFile_neverWritesLegacyDotNotation() {
        val promptsDir = tempFolder.newFolder("prompts")
        val legacyDir = tempFolder.newFolder("legacy_memory")

        val repo = SystemPromptRepository(promptsDir, legacyDir)

        assertEquals("", repo.loadSystemPrompt())
        assertEquals("", repo.loadAppendPrompt())

        // Save new prompts
        repo.saveSystemPromptFiles("Custom System Prompt", "My Append Instructions")

        assertEquals("Custom System Prompt", repo.loadSystemPrompt())
        assertEquals("My Append Instructions", repo.loadAppendPrompt())

        // Assert files on disk
        assertTrue(File(promptsDir, "SYSTEM.md").exists())
        assertTrue(File(promptsDir, "APPEND_SYSTEM.md").exists())

        // Critical check: APPEND.SYSTEM.md must NOT exist
        assertFalse(File(promptsDir, "APPEND.SYSTEM.md").exists())

        // And legacyDir must NOT contain prompt files
        assertFalse(File(legacyDir, "SYSTEM.md").exists())
        assertFalse(File(legacyDir, "APPEND_SYSTEM.md").exists())
        assertFalse(File(legacyDir, "APPEND.SYSTEM.md").exists())
    }

    @Test
    fun blankValues_deleteFiles() {
        val promptsDir = tempFolder.newFolder("prompts")
        val repo = SystemPromptRepository(promptsDir)

        repo.saveSystemPromptFiles("Custom System Prompt", "My Append Instructions")
        assertTrue(File(promptsDir, "SYSTEM.md").exists())
        assertTrue(File(promptsDir, "APPEND_SYSTEM.md").exists())

        // Save blank to delete
        repo.saveSystemPromptFiles("", "")
        assertFalse(File(promptsDir, "SYSTEM.md").exists())
        assertFalse(File(promptsDir, "APPEND_SYSTEM.md").exists())
        assertEquals("", repo.loadSystemPrompt())
        assertEquals("", repo.loadAppendPrompt())
    }

    @Test
    fun legacyMigration_movesFilesAndCleansUpLegacyDir() {
        val promptsDir = tempFolder.newFolder("prompts")
        val legacyDir = tempFolder.newFolder("legacy_memory")

        // Seed legacy memoryDir with old files (including duplicate APPEND.SYSTEM.md)
        File(legacyDir, "SYSTEM.md").writeText("Legacy System Prompt")
        File(legacyDir, "APPEND_SYSTEM.md").writeText("Legacy Append Prompt")
        File(legacyDir, "APPEND.SYSTEM.md").writeText("Legacy Append Prompt Duplicate")

        // Also keep a real memory file in legacyDir to ensure it is NOT deleted
        val realGlobalFile = File(legacyDir, "GLOBAL.md")
        realGlobalFile.writeText("Keep this global memory")
        val realDailyLog = File(legacyDir, "2026-09-09.md")
        realDailyLog.writeText("Keep this daily log")

        val repo = SystemPromptRepository(promptsDir, legacyDir)

        // Contents must be migrated to promptsDir
        assertEquals("Legacy System Prompt", repo.loadSystemPrompt())
        assertEquals("Legacy Append Prompt", repo.loadAppendPrompt())

        assertTrue(File(promptsDir, "SYSTEM.md").exists())
        assertTrue(File(promptsDir, "APPEND_SYSTEM.md").exists())
        assertFalse(File(promptsDir, "APPEND.SYSTEM.md").exists())

        // Prompt files must be deleted from legacyDir
        assertFalse(File(legacyDir, "SYSTEM.md").exists())
        assertFalse(File(legacyDir, "APPEND_SYSTEM.md").exists())
        assertFalse(File(legacyDir, "APPEND.SYSTEM.md").exists())

        // Real memory files must remain untouched
        assertTrue(realGlobalFile.exists())
        assertTrue(realDailyLog.exists())
    }
}
