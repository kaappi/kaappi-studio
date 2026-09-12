package com.kaappi.studio.data

import android.content.Context
import com.kaappi.studio.domain.SchemeFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class FileRepository(context: Context) {
    // Lazy so the mkdirs and the temp-file sweep run on Dispatchers.IO at
    // first use, not on the main thread when the ViewModel is built (issue #12).
    private val dir: File by lazy {
        File(context.filesDir, "schemes").also {
            it.mkdirs()
            // A crash between writeText and renameTo leaves <name>.scm.tmp behind:
            // invisible to listFiles (the .scm filter) but accumulating forever.
            // Sweep stale temp files instead of letting them pile up.
            it.listFiles { _, name -> name.endsWith(".tmp") }?.forEach { f -> f.delete() }
        }
    }

    actual suspend fun listFiles(): List<SchemeFile> = withContext(Dispatchers.IO) {
        dir.listFiles()
            ?.filter { it.extension == "scm" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.toSchemeFile() }
            ?: emptyList()
    }

    actual suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        // Contract: throw on missing/unreadable files — never return "" (see expect KDoc).
        try {
            File(path).readText()
        } catch (e: IOException) {
            throw FileRepositoryException("Cannot read file at $path", e)
        }
    }

    actual suspend fun writeFile(name: String, content: String): SchemeFile = withContext(Dispatchers.IO) {
        val file = File(dir, SchemeFileNames.withExtension(SchemeFileNames.sanitize(name)))
        // Contract: atomic write — the temp file is fully written before it
        // replaces the target, so a crash mid-save keeps the previous version.
        val tmp = File(dir, "${file.name}.tmp")
        try {
            tmp.writeText(content)
            if (!tmp.renameTo(file)) throw IOException("rename(${tmp.name}) failed")
        } catch (e: IOException) {
            tmp.delete()
            throw FileRepositoryException("Cannot save ${file.name}", e)
        }
        file.toSchemeFile()
    }

    actual suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).delete()
    }

    actual suspend fun renameFile(oldPath: String, newName: String): SchemeFile? = withContext(Dispatchers.IO) {
        val old = File(oldPath)
        val new = File(old.parentFile, SchemeFileNames.withExtension(SchemeFileNames.sanitize(newName)))
        // Contract: POSIX rename(2) silently replaces an existing destination;
        // report the collision as a failure instead. Renaming a file to its own
        // current name is a no-op success, not a collision.
        if (new.exists() && new.canonicalPath != old.canonicalPath) return@withContext null
        if (old.renameTo(new)) new.toSchemeFile() else null
    }

    // Contract: no readText() here — listing never touches file contents (issue #12).
    private fun File.toSchemeFile() = SchemeFile(
        name = nameWithoutExtension,
        path = absolutePath,
        lastModified = lastModified(),
    )
}
