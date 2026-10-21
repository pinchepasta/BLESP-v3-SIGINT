package com.bruceremote.app.serial

import android.content.Context
import android.os.Environment
import java.io.File

class LocalFileToolsManager(private val context: Context) {

    val storageDir: File
        get() {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val publicDir = File(downloadsDir, "bruce_remote")
            if (!publicDir.exists()) publicDir.mkdirs()
            return if (publicDir.exists()) {
                publicDir
            } else {
                val appExtDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val fallbackDir = File(appExtDir, "bruce_remote")
                if (!fallbackDir.exists()) fallbackDir.mkdirs()
                fallbackDir
            }
        }

    /**
     * Writes or overwrites a file in public local storage (Download/bruce_remote/).
     */
    fun writeFile(filename: String, content: String): String {
        val safeName = sanitizeFilename(filename)
        val file = File(storageDir, safeName)
        file.writeText(content, Charsets.UTF_8)
        return "Saved local file '$safeName' (${file.length()} bytes) to `${file.absolutePath}`"
    }

    /**
     * Reads contents of a file from public local storage (Download/bruce_remote/).
     */
    fun readFile(filename: String): String {
        val safeName = sanitizeFilename(filename)
        val file = File(storageDir, safeName)
        return if (file.exists()) {
            file.readText(Charsets.UTF_8)
        } else {
            "Error: Local file '$safeName' not found at `${file.absolutePath}`."
        }
    }

    /**
     * Appends text to an existing file in public local storage (Download/bruce_remote/).
     */
    fun appendFile(filename: String, content: String): String {
        val safeName = sanitizeFilename(filename)
        val file = File(storageDir, safeName)
        file.appendText(content + "\n", Charsets.UTF_8)
        return "Appended content to '$safeName' (new size: ${file.length()} bytes) at `${file.absolutePath}`"
    }

    /**
     * Lists all saved files in public local storage (Download/bruce_remote/).
     */
    fun listFiles(): String {
        val dir = storageDir
        val files = dir.listFiles() ?: emptyArray()
        if (files.isEmpty()) {
            return "Local storage directory is empty (0 files) at `${dir.absolutePath}`."
        }
        val sb = StringBuilder("📁 **Local Saved Files (${files.size})** in `${dir.absolutePath}`:\n")
        files.sortedByDescending { it.lastModified() }.forEach { f ->
            sb.append("• `${f.name}` (${f.length()} bytes)\n")
        }
        return sb.toString().trim()
    }

    /**
     * Deletes a file from public local storage (Download/bruce_remote/).
     */
    fun deleteFile(filename: String): String {
        val safeName = sanitizeFilename(filename)
        val file = File(storageDir, safeName)
        return if (file.exists() && file.delete()) {
            "Deleted local file '$safeName'"
        } else {
            "Error: Could not delete '$safeName' (file not found)"
        }
    }

    private fun sanitizeFilename(rawName: String): String {
        val cleaned = rawName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (cleaned.isBlank()) "notes.txt" else cleaned
    }
}
