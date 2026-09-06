package com.kaappi.studio.data

import android.content.Context
import com.kaappi.studio.domain.SchemeFile
import java.io.File
import java.io.IOException

actual class FileRepository(context: Context) {
    private val dir: File = File(context.filesDir, "schemes").also {
        it.mkdirs()
        // A crash between writeText and renameTo leaves <name>.scm.tmp behind:
        // invisible to listFiles (the .scm filter) but accumulating forever.
        // Sweep stale temp files instead of letting them pile up.
        it.listFiles { _, name -> name.endsWith(".tmp") }?.forEach { f -> f.delete() }
    }

    actual fun listFiles(): List<SchemeFile> =
        dir.listFiles()
            ?.filter { it.extension == "scm" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.toSchemeFile() }
            ?: emptyList()

    actual fun readFile(path: String): String =
        // Contract: throw on missing/unreadable files — never return "" (see expect KDoc).
        try {
            File(path).readText()
        } catch (e: IOException) {
            throw FileRepositoryException("Cannot read file at $path", e)
        }

    actual fun writeFile(name: String, content: String): SchemeFile {
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
        return file.toSchemeFile()
    }

    actual fun deleteFile(path: String): Boolean = File(path).delete()

    actual fun renameFile(oldPath: String, newName: String): SchemeFile? {
        val old = File(oldPath)
        val new = File(old.parentFile, SchemeFileNames.withExtension(SchemeFileNames.sanitize(newName)))
        // Contract: POSIX rename(2) silently replaces an existing destination;
        // report the collision as a failure instead. Renaming a file to its own
        // current name is a no-op success, not a collision.
        if (new.exists() && new.canonicalPath != old.canonicalPath) return null
        return if (old.renameTo(new)) new.toSchemeFile() else null
    }

    private fun File.toSchemeFile() = SchemeFile(
        name = nameWithoutExtension,
        path = absolutePath,
        // Contract: an unreadable file makes listFiles throw — a listed entry
        // must never carry fabricated empty content (see expect KDoc).
        content = readText(),
        lastModified = lastModified(),
    )
}
