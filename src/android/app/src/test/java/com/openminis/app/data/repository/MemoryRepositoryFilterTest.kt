package com.openminis.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Guards the boundary between "memory" and "system prompt":
 *
 *  - the Settings list must show GLOBAL.md, SOUL.md and the daily logs;
 *  - it must never show SYSTEM.md / APPEND_SYSTEM.md / APPEND.SYSTEM.md;
 *  - an unreadable file must not be silently reported as empty, because the
 *    editor's Save button overwrites whatever it was given.
 *
 * [T-memory-list-soul-visible] The SOUL.md assertions are the regression guard
 * for the v1.13-1.1 report "更新最新版本后 soul.md 没了": the file was intact on
 * disk but a daily-log-only whitelist had removed it from the list.
 */
class MemoryRepositoryFilterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun listAllFiles_showsGlobalSoulAndDailyLogs_neverPromptFiles() {
        val memDir = tempFolder.newFolder("memory")
        // Memory files
        File(memDir, "GLOBAL.md").writeText("# Global memory preferences")
        File(memDir, "SOUL.md").writeText("---\nname: \"Minis\"\n---\n\nBe direct.\n")
        File(memDir, "2026-09-09.md").writeText("[2026-09-09 10:00:00] user: hello")
        File(memDir, "2026-09-08.md").writeText("[2026-09-08 09:00:00] user: earlier memory")

        // Prompt files / stray files that must NEVER be listed as memory
        File(memDir, "SYSTEM.md").writeText("Custom base prompt")
        File(memDir, "APPEND_SYSTEM.md").writeText("Append instructions 1")
        File(memDir, "APPEND.SYSTEM.md").writeText("Append instructions 2")
        File(memDir, "random_note.md").writeText("random markdown")
        File(memDir, "notes.txt").writeText("plain text file")

        val repo = MemoryRepository(memDir)
        val files = repo.listAllFiles()
        val fileNames = files.map { it.name }

        // GLOBAL.md first, then SOUL.md, then daily logs newest-first.
        assertEquals("GLOBAL.md", files.first().name)
        assertTrue(files.first().isGlobal)
        assertEquals(
            listOf("GLOBAL.md", "SOUL.md", "2026-09-09.md", "2026-09-08.md"),
            fileNames,
        )

        // SOUL.md is listed (user report: it disappeared) and carries its preview.
        val soul = files.first { it.name == "SOUL.md" }
        assertFalse(soul.isGlobal)
        assertTrue(soul.preview.contains("---"))

        // Neither system file can be deleted from the Settings list.
        assertFalse(files.first { it.name == "GLOBAL.md" }.canDelete)
        assertFalse(soul.canDelete)
        // Daily logs stay deletable.
        assertTrue(files.first { it.name == "2026-09-09.md" }.canDelete)

        // Prompt files and unrelated files never appear.
        assertFalse(fileNames.contains("APPEND_SYSTEM.md"))
        assertFalse(fileNames.contains("APPEND.SYSTEM.md"))
        assertFalse(fileNames.contains("SYSTEM.md"))
        assertFalse(fileNames.contains("random_note.md"))
        assertFalse(fileNames.contains("notes.txt"))
    }

    @Test
    fun listAllFiles_showsSoulEvenWhenGlobalIsMissing() {
        val memDir = tempFolder.newFolder("memory")
        File(memDir, "SOUL.md").writeText("---\nname: \"Minis\"\n---\n\nbody\n")

        val fileNames = MemoryRepository(memDir).listAllFiles().map { it.name }
        assertEquals(listOf("GLOBAL.md", "SOUL.md"), fileNames)
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

    // -- Write / read safety ------------------------------------------------

    @Test
    fun saveFile_writesAtomically_andLeavesNoTempFile() {
        val memDir = tempFolder.newFolder("memory")
        val repo = MemoryRepository(memDir)

        repo.saveFile("GLOBAL.md", "first")
        assertEquals("first", File(memDir, "GLOBAL.md").readText())

        repo.saveFile("GLOBAL.md", "second")
        assertEquals("second", File(memDir, "GLOBAL.md").readText())
        assertFalse(File(memDir, "GLOBAL.md.tmp").exists())
        assertFalse(File(memDir, "SOUL.md.tmp").exists())

        repo.saveGlobalMd("third")
        assertEquals("third", File(memDir, "GLOBAL.md").readText())
        assertFalse(File(memDir, "GLOBAL.md.tmp").exists())
    }

    @Test
    fun saveFile_rejectsSystemPromptNames() {
        val memDir = tempFolder.newFolder("memory")
        val repo = MemoryRepository(memDir)

        for (name in listOf("SYSTEM.md", "APPEND_SYSTEM.md", "APPEND.SYSTEM.md")) {
            val failed = try {
                repo.saveFile(name, "pollution")
                false
            } catch (_: IllegalArgumentException) {
                true
            }
            assertTrue("$name must be rejected by saveFile", failed)
            assertFalse(File(memDir, name).exists())
        }
    }

    @Test
    fun readFileOrNull_distinguishesMissingFromUnreadable() {
        val memDir = tempFolder.newFolder("memory")
        val repo = MemoryRepository(memDir)

        // Missing -> empty string: the file legitimately does not exist yet.
        assertEquals("", repo.readFileOrNull("GLOBAL.md"))

        repo.saveFile("GLOBAL.md", "content")
        assertEquals("content", repo.readFileOrNull("GLOBAL.md"))

        // A name that exists but cannot be read as text (a directory) must be
        // reported as null, NOT as empty — otherwise the editor would offer to
        // save "" over content it never managed to read.
        val unreadable = File(memDir, "2026-09-09.md")
        unreadable.mkdirs()
        assertNull(repo.readFileOrNull("2026-09-09.md"))
        // Legacy accessor keeps its "unreadable reads as empty" contract.
        assertEquals("", repo.readFile("2026-09-09.md"))
    }

    @Test
    fun deleteFile_refusesGlobalAndSoul_butDeletesDailyLogs() {
        val memDir = tempFolder.newFolder("memory")
        File(memDir, "GLOBAL.md").writeText("global")
        File(memDir, "SOUL.md").writeText("soul")
        File(memDir, "2026-09-09.md").writeText("log")
        val repo = MemoryRepository(memDir)

        assertFalse(repo.deleteFile("GLOBAL.md"))
        assertFalse(repo.deleteFile("SOUL.md"))
        assertTrue(File(memDir, "GLOBAL.md").exists())
        assertTrue(File(memDir, "SOUL.md").exists())

        assertTrue(repo.deleteFile("2026-09-09.md"))
        assertFalse(File(memDir, "2026-09-09.md").exists())
    }
}
