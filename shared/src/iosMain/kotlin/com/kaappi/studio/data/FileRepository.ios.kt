package com.kaappi.studio.data

import com.kaappi.studio.domain.SchemeFile
import platform.Foundation.*

actual class FileRepository {
    private val dir: String by lazy {
        val docs = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: ""
        val schemes = "$docs/schemes"
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(schemes)) {
            fm.createDirectoryAtPath(schemes, withIntermediateDirectories = true, attributes = null, error = null)
        }
        schemes
    }

    actual fun listFiles(): List<SchemeFile> {
        val fm = NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(dir, error = null) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (contents as List<String>)
            .filter { it.hasSuffix(".scm") }
            .mapNotNull { name ->
                val path = "$dir/$name"
                val content = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null) ?: return@mapNotNull null
                val attrs = fm.attributesOfItemAtPath(path, error = null)
                val modified = (attrs?.get(NSFileModificationDate) as? NSDate)
                    ?.timeIntervalSince1970?.toLong()?.times(1000) ?: 0L
                SchemeFile(
                    name = name.removeSuffix(".scm"),
                    path = path,
                    content = content,
                    lastModified = modified,
                )
            }
            .sortedByDescending { it.lastModified }
    }

    actual fun readFile(path: String): String =
        NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null) ?: ""

    actual fun writeFile(name: String, content: String): SchemeFile {
        val safeName = if (name.endsWith(".scm")) name else "$name.scm"
        val path = "$dir/$safeName"
        (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        return SchemeFile(
            name = safeName.removeSuffix(".scm"),
            path = path,
            content = content,
            lastModified = NSDate().timeIntervalSince1970.toLong() * 1000,
        )
    }

    actual fun deleteFile(path: String): Boolean =
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)

    actual fun renameFile(oldPath: String, newName: String): SchemeFile? {
        val safeName = if (newName.endsWith(".scm")) newName else "$newName.scm"
        val newPath = "$dir/$safeName"
        val success = NSFileManager.defaultManager.moveItemAtPath(oldPath, toPath = newPath, error = null)
        if (!success) return null
        val content = readFile(newPath)
        return SchemeFile(
            name = safeName.removeSuffix(".scm"),
            path = newPath,
            content = content,
            lastModified = NSDate().timeIntervalSince1970.toLong() * 1000,
        )
    }
}
