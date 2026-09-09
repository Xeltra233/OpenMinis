package com.openminis.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MemoryRepositoryFilterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun listAllFiles_onlyIncludesGlobalAndDailyLogs_ignoresSystemAndSoulPrompts() {
        val memDir = tempFolder.newFolder("memory")
        // Memory files
        File(memDir, "GLOBAL.md").writeText("# Global memory preferences")
        File(memDir, "2026-09-09.md").writeText("[2026-09-09 10:00:00] user: hello")
        File(memDir, "2026-09-08.md").writeText("[2026-09-08 09:00:00] user: earlier memory")

        // Pollution files that must NEVER be listed in memory files list
        File(memDir, "SYSTEM.md").writeText("Custom base prompt")
        File(memDir, "APPEND_SYSTEM.md").writeText("Append instructions 1")
        File(memDir, "APPEND.SYSTEM.md").writeText("Append instructions 2")
        File(memDir, "SOUL.md").writeText("name: Minis\npersonality: test")
        File(memDir, "random_note.md").writeText("random markdown")
        File(memDir, "notes.txt").writeText("plain text file")

        val repo = MemoryRepository(memDir)
        val files = repo.listAllFiles()
        val fileNames = files.map { it.name }

        // Must contain GLOBAL.md as first item
        assertEquals("GLOBAL.md", files.first().name)
        assertTrue(files.first().isGlobal)

        // Must contain only GLOBAL.md and valid daily logs
        assertEquals(listOf("GLOBAL.md", "2026-09-09.md", "2026-09-08.md"), fileNames)

        // Specifically verify bugged prompt files are NOT in the list
        assertFalse(fileNames.contains("APPEND_SYSTEM.md"))
        assertFalse(fileNames.contains("APPEND.SYSTEM.md"))
        assertFalse(fileNames.contains("SYSTEM.md"))
        assertFalse(fileNames.contains("SOUL.md"))
        assertFalse(fileNames.contains("random_note.md"))
        assertFalse(fileNames.contains("notes.txt"))
    }

    @Test
    fun searchMemory_onlySearchesGlobalAndDailyLogs() {
        val memDir = tempFolder.newFolder("memory")
        File(memDir, "GLOBAL.md").writeText("SpecialKeyword in global")
        File(memDir, "2026-09-09.md").writeText("SpecialKeyword in daily log")
        File(memDir, "APPEND_SYSTEM.md").writeText("SpecialKeyword in append prompt")
        File(memDir, "APPEND.SYSTEM.md").writeText("SpecialKeyword in legacy append prompt")
        File(memDir, "SYSTEM.md").writeText("SpecialKeyword in system prompt")
        File(memDir, "SOUL.md").writeText("SpecialKeyword in soul")

        val repo = MemoryRepository(memDir)
        val result = repo.getMemory("SpecialKeyword", scope = "all")

        assertTrue(result.contains("GLOBAL.md"))
        assertTrue(result.contains("2026-09-09.md"))
        assertFalse(result.contains("APPEND_SYSTEM.md"))
        assertFalse(result.contains("APPEND.SYSTEM.md"))
        assertFalse(result.contains("SYSTEM.md"))
        assertFalse(result.contains("SOUL.md"))
    }
}
