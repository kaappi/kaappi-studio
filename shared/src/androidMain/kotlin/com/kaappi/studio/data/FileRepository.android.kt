package com.kaappi.studio.data

import android.content.Context
import com.kaappi.studio.domain.SchemeFile
import java.io.File

actual class FileRepository(context: Context) {
    private val dir: File = File(context.filesDir, "schemes").also { it.mkdirs() }

    actual fun listFiles(): List<SchemeFile> =
        dir.listFiles()
            ?.filter { it.extension == "scm" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.toSchemeFile() }
            ?: emptyList()

    actual fun readFile(path: String): String = File(path).readText()

    actual fun writeFile(name: String, content: String): SchemeFile {
        val safeName = if (name.endsWith(".scm")) name else "$name.scm"
        val file = File(dir, safeName)
        file.writeText(content)
        return file.toSchemeFile()
    }

    actual fun deleteFile(path: String): Boolean = File(path).delete()

    actual fun renameFile(oldPath: String, newName: String): SchemeFile? {
        val old = File(oldPath)
        val safeName = if (newName.endsWith(".scm")) newName else "$newName.scm"
        val new = File(old.parentFile, safeName)
        return if (old.renameTo(new)) new.toSchemeFile() else null
    }

    private fun File.toSchemeFile() = SchemeFile(
        name = nameWithoutExtension,
        path = absolutePath,
        content = readText(),
        lastModified = lastModified(),
    )
}
