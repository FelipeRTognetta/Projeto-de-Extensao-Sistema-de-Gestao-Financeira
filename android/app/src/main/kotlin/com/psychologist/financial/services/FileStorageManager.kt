package com.psychologist.financial.services

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File Storage Manager
 *
 * Handles file storage operations for exported data.
 * Manages app-specific storage directories and file permissions.
 *
 * Features:
 * - App-specific export directory management
 * - Timestamp-based folder creation for organized exports
 * - File existence checking and deletion
 * - Storage space validation
 * - File path generation with timestamps
 * - Directory creation and cleanup
 *
 * Storage locations:
 * - Primary: App's external files directory (app-specific)
 * - Fallback: App's cache directory
 * - Cleanup: Old export folders (optional)
 *
 * Usage:
 * ```kotlin
 * val manager = FileStorageManager(context)
 * val exportDir = manager.getExportDirectory()
 * val filePath = manager.getFilePath("export")
 * val hasSpace = manager.hasStorageSpace(minimumMB)
 * manager.deleteFile(file)
 * ```
 */
class FileStorageManager(private val context: Context) {

    companion object {
        private const val TAG = "FileStorageManager"
        private const val EXPORT_DIR_NAME = "exports"
        private const val MIN_STORAGE_MB = 10  // Minimum 10MB required
    }

    /**
     * Get or create export directory
     *
     * Creates app-specific export directory in external files.
     * Falls back to cache directory if external storage unavailable.
     *
     * @return Export directory File
     * @throws IllegalStateException if directory cannot be created
     */
    fun getExportDirectory(): File {
        // Try external files directory first (app-specific, survives app uninstall)
        val externalFilesDir = context.getExternalFilesDir(EXPORT_DIR_NAME)
        if (externalFilesDir != null && (externalFilesDir.exists() || externalFilesDir.mkdirs())) {
            return externalFilesDir
        }

        // Fallback to cache directory
        val cacheDir = File(context.cacheDir, EXPORT_DIR_NAME)
        if (cacheDir.exists() || cacheDir.mkdirs()) {
            return cacheDir
        }

        throw IllegalStateException("Cannot create export directory")
    }

    /**
     * Get timestamped export subdirectory
     *
     * Creates a subdirectory with timestamp for organizing exports.
     * Format: exports/2026-02-26-143022/
     *
     * @return Timestamped export directory
     */
    fun getTimestampedExportDirectory(): File {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
        val baseDir = getExportDirectory()
        val timestampDir = File(baseDir, timestamp)

        if (!timestampDir.exists() && !timestampDir.mkdirs()) {
            throw IllegalStateException("Cannot create timestamped directory")
        }

        return timestampDir
    }

    /**
     * Generate file path with optional timestamp
     *
     * @param filename Base filename without extension
     * @param addTimestamp Whether to include timestamp in filename
     * @return Full file path
     */
    fun getFilePath(filename: String, addTimestamp: Boolean = true): String {
        val timestamp = if (addTimestamp) {
            SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
        } else {
            ""
        }

        val finalFilename = if (addTimestamp) {
            "$filename-$timestamp.csv"
        } else {
            "$filename.csv"
        }

        return File(getExportDirectory(), finalFilename).absolutePath
    }

    /**
     * Check if sufficient storage space available
     *
     * Verifies that device has minimum required free space.
     *
     * @param minimumMB Minimum required space in MB (default 10MB)
     * @return true if sufficient space available
     */
    fun hasStorageSpace(minimumMB: Int = MIN_STORAGE_MB): Boolean {
        val dir = getExportDirectory()
        val freeSpace = dir.freeSpace

        // Convert MB to bytes: 1MB = 1024*1024 bytes
        val minimumBytes = minimumMB * 1024L * 1024L

        return freeSpace > minimumBytes
    }

    /**
     * Get available storage space in MB
     *
     * @return Available space in megabytes
     */
    fun getAvailableStorageMB(): Long {
        val dir = getExportDirectory()
        return dir.freeSpace / (1024 * 1024)
    }

    /**
     * Delete file
     *
     * Safely deletes a file if it exists.
     *
     * @param file File to delete
     * @return true if deleted successfully, false if file doesn't exist or deletion failed
     */
    fun deleteFile(file: File): Boolean {
        return file.exists() && file.delete()
    }

    /**
     * Delete directory and all contents
     *
     * Recursively deletes directory and all files within.
     *
     * @param dir Directory to delete
     * @return true if successful
     */
    fun deleteDirectory(dir: File): Boolean {
        if (!dir.isDirectory) return false

        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteDirectory(file)
            } else {
                file.delete()
            }
        }

        return dir.delete()
    }

    /**
     * Get list of exported files
     *
     * @return List of files in export directory
     */
    fun getExportedFiles(): List<File> {
        val dir = getExportDirectory()
        return dir.listFiles()?.toList() ?: emptyList()
    }

    /**
     * Get list of exported directories (by timestamp)
     *
     * @return List of timestamped export directories
     */
    fun getExportDirectories(): List<File> {
        val dir = getExportDirectory()
        return dir.listFiles { file ->
            file.isDirectory
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Get size of directory in MB
     *
     * Recursively calculates total size of directory and contents.
     *
     * @param dir Directory to measure
     * @return Size in megabytes
     */
    fun getDirectorySizeMB(dir: File): Long {
        if (!dir.exists()) return 0

        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                getDirectorySizeMB(file) * 1024 * 1024  // Convert back to bytes
            } else {
                file.length()
            }
        }

        return size / (1024 * 1024)
    }

    /**
     * Clean up old exports
     *
     * Removes export directories older than specified days.
     *
     * @param daysOld Minimum age of directories to delete (default 7 days)
     * @return Number of directories deleted
     */
    fun cleanupOldExports(daysOld: Int = 7): Int {
        val dirs = getExportDirectories()
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)

        var deletedCount = 0
        dirs.forEach { dir ->
            if (dir.lastModified() < cutoffTime) {
                if (deleteDirectory(dir)) {
                    deletedCount++
                }
            }
        }

        return deletedCount
    }

    /**
     * Get human-readable export status
     *
     * @return Status string describing export storage
     */
    fun getStorageStatus(): String {
        return try {
            val availableMB = getAvailableStorageMB()
            val files = getExportedFiles().size
            "Storage: ${availableMB}MB available, $files files in exports"
        } catch (e: Exception) {
            "Storage: Unable to determine"
        }
    }
}
