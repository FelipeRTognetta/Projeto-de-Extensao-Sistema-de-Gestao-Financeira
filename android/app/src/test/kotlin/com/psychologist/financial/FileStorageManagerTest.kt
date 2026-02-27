package com.psychologist.financial

import android.content.Context
import com.psychologist.financial.services.FileStorageManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for FileStorageManager
 *
 * Coverage:
 * - getExportDirectory() creates and returns export dir
 * - getExportDirectory() falls back to cache dir when external unavailable
 * - getFilePath() generates timestamped filename
 * - getFilePath() generates non-timestamped filename
 * - deleteFile() deletes existing file
 * - deleteFile() returns false for nonexistent file
 * - deleteDirectory() recursively deletes contents
 * - deleteDirectory() returns false for non-directory
 * - getDirectorySizeMB() returns zero for empty dir
 * - getDirectorySizeMB() returns zero for nonexistent dir
 * - cleanupOldExports() deletes old directories
 * - cleanupOldExports() keeps recent directories
 * - getExportedFiles() returns files in export dir
 * - getExportDirectories() returns subdirectories sorted by modified
 *
 * Total: 16 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class FileStorageManagerTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var tempDir: File
    private lateinit var manager: FileStorageManager

    @Before
    fun setUp() {
        tempDir = createTempDir("test_exports")
        whenever(mockContext.getExternalFilesDir("exports")).thenReturn(tempDir)
        whenever(mockContext.cacheDir).thenReturn(tempDir.parentFile)
        manager = FileStorageManager(context = mockContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ========================================
    // getExportDirectory() Tests
    // ========================================

    @Test
    fun `getExportDirectory returns external files directory when available`() {
        val dir = manager.getExportDirectory()

        assertNotNull(dir)
        assertTrue(dir.exists())
        assertEquals(tempDir, dir)
    }

    @Test
    fun `getExportDirectory falls back to cache dir when external unavailable`() {
        val cacheDir = createTempDir("test_cache")
        try {
            whenever(mockContext.getExternalFilesDir("exports")).thenReturn(null)
            whenever(mockContext.cacheDir).thenReturn(cacheDir)
            // Cache dir fallback creates "exports" subdirectory under cacheDir
            val fallbackManager = FileStorageManager(context = mockContext)

            val dir = fallbackManager.getExportDirectory()

            assertNotNull(dir)
            assertTrue(dir.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    // ========================================
    // getFilePath() Tests
    // ========================================

    @Test
    fun `getFilePath with timestamp generates timestamped filename`() {
        val path = manager.getFilePath("export", addTimestamp = true)

        assertTrue(path.contains("export"))
        assertTrue(path.endsWith(".csv"))
        // Timestamp pattern: YYYY-MM-DD-HHmmss
        assertTrue(path.contains("-"))
    }

    @Test
    fun `getFilePath without timestamp uses plain filename`() {
        val path = manager.getFilePath("export", addTimestamp = false)

        assertTrue(path.endsWith("export.csv"))
        assertFalse(path.contains("null"))
    }

    // ========================================
    // deleteFile() Tests
    // ========================================

    @Test
    fun `deleteFile returns true when file exists and is deleted`() {
        val testFile = File(tempDir, "test.txt").also { it.createNewFile() }

        val result = manager.deleteFile(testFile)

        assertTrue(result)
        assertFalse(testFile.exists())
    }

    @Test
    fun `deleteFile returns false when file does not exist`() {
        val nonExistentFile = File(tempDir, "nonexistent.txt")

        val result = manager.deleteFile(nonExistentFile)

        assertFalse(result)
    }

    // ========================================
    // deleteDirectory() Tests
    // ========================================

    @Test
    fun `deleteDirectory recursively removes all contents`() {
        val dir = File(tempDir, "to_delete").also { it.mkdirs() }
        File(dir, "file1.csv").createNewFile()
        File(dir, "file2.csv").createNewFile()
        val subDir = File(dir, "sub").also { it.mkdirs() }
        File(subDir, "file3.csv").createNewFile()

        val result = manager.deleteDirectory(dir)

        assertTrue(result)
        assertFalse(dir.exists())
    }

    @Test
    fun `deleteDirectory returns false for non-directory`() {
        val testFile = File(tempDir, "file.txt").also { it.createNewFile() }

        val result = manager.deleteDirectory(testFile)

        assertFalse(result)
    }

    // ========================================
    // getDirectorySizeMB() Tests
    // ========================================

    @Test
    fun `getDirectorySizeMB returns zero for empty directory`() {
        val emptyDir = File(tempDir, "empty").also { it.mkdirs() }

        val size = manager.getDirectorySizeMB(emptyDir)

        assertEquals(0L, size)
    }

    @Test
    fun `getDirectorySizeMB returns zero for nonexistent directory`() {
        val nonExistent = File(tempDir, "nonexistent")

        val size = manager.getDirectorySizeMB(nonExistent)

        assertEquals(0L, size)
    }

    // ========================================
    // cleanupOldExports() Tests
    // ========================================

    @Test
    fun `cleanupOldExports deletes directories older than cutoff`() {
        val oldDir = File(tempDir, "old_export").also { it.mkdirs() }
        // Set modification time to 8 days ago
        oldDir.setLastModified(System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L))

        val deleted = manager.cleanupOldExports(daysOld = 7)

        assertEquals(1, deleted)
        assertFalse(oldDir.exists())
    }

    @Test
    fun `cleanupOldExports keeps recent directories`() {
        val recentDir = File(tempDir, "recent_export").also { it.mkdirs() }
        // Recent dir has current modification time

        val deleted = manager.cleanupOldExports(daysOld = 7)

        assertEquals(0, deleted)
        assertTrue(recentDir.exists())
    }

    // ========================================
    // getExportedFiles() Tests
    // ========================================

    @Test
    fun `getExportedFiles returns all files in export directory`() {
        File(tempDir, "patients.csv").createNewFile()
        File(tempDir, "appointments.csv").createNewFile()

        val files = manager.getExportedFiles()

        assertEquals(2, files.size)
    }

    @Test
    fun `getExportedFiles returns empty list when no files`() {
        val files = manager.getExportedFiles()

        assertTrue(files.isEmpty())
    }

    // ========================================
    // getExportDirectories() Tests
    // ========================================

    @Test
    fun `getExportDirectories returns only subdirectories`() {
        File(tempDir, "2026-01-01-100000").also { it.mkdirs() }
        File(tempDir, "2026-01-02-100000").also { it.mkdirs() }
        File(tempDir, "patients.csv").createNewFile() // Should not be included

        val dirs = manager.getExportDirectories()

        assertEquals(2, dirs.size)
        assertTrue(dirs.all { it.isDirectory })
    }

    @Test
    fun `getExportDirectories returns sorted by modified time descending`() {
        val older = File(tempDir, "older").also { it.mkdirs() }
        val newer = File(tempDir, "newer").also { it.mkdirs() }
        older.setLastModified(System.currentTimeMillis() - 60000)
        newer.setLastModified(System.currentTimeMillis())

        val dirs = manager.getExportDirectories()

        assertEquals("newer", dirs[0].name)
        assertEquals("older", dirs[1].name)
    }
}
