@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.kaappi.studio.data

import com.kaappi.studio.domain.SchemeFile
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO // Dispatchers.IO is an extension on Kotlin/Native
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.posix.memcpy

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

    actual suspend fun listFiles(): List<SchemeFile> = withContext(Dispatchers.IO) {
        val fm = NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(dir, error = null) ?: return@withContext emptyList()
        @Suppress("UNCHECKED_CAST")
        (contents as List<String>)
            .filter { it.endsWith(".scm") }
            .map { name ->
                val path = "$dir/$name"
                // Contract: names and mtimes only — never read contents while
                // listing (issue #12); readFile reports unreadable files.
                SchemeFile(
                    name = name.removeSuffix(".scm"),
                    path = path,
                    lastModified = lastModified(path),
                )
            }
            .sortedByDescending { it.lastModified }
    }

    actual suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        // Contract: throw on missing/unreadable files — never return "" (see
        // expect KDoc); invalid UTF-8 content decodes lossily, like Android.
        decodeLossy(readData(path))
    }

    actual suspend fun writeFile(name: String, content: String): SchemeFile = withContext(Dispatchers.IO) {
        val safeName = SchemeFileNames.withExtension(SchemeFileNames.sanitize(name))
        val path = "$dir/$safeName"
        val written = dataFrom(content).writeToFile(path, atomically = true)
        // Contract: never fabricate success.
        if (!written) throw FileRepositoryException("Cannot save $safeName")
        SchemeFile(
            name = safeName.removeSuffix(".scm"),
            path = path,
            lastModified = NSDate().timeIntervalSince1970.toLong() * 1000,
        )
    }

    actual suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    actual suspend fun renameFile(oldPath: String, newName: String): SchemeFile? = withContext(Dispatchers.IO) {
        val safeName = SchemeFileNames.withExtension(SchemeFileNames.sanitize(newName))
        val newPath = "$dir/$safeName"
        // Contract: refuse to overwrite an existing destination. Renaming a
        // file to its own current name is a no-op success, not a collision.
        val samePath = newPath == oldPath
        if (!samePath && NSFileManager.defaultManager.fileExistsAtPath(newPath)) return@withContext null
        val success = samePath ||
            NSFileManager.defaultManager.moveItemAtPath(oldPath, toPath = newPath, error = null)
        if (!success) return@withContext null
        SchemeFile(
            name = safeName.removeSuffix(".scm"),
            path = newPath,
            lastModified = NSDate().timeIntervalSince1970.toLong() * 1000,
        )
    }

    /** The file's bytes; throws [FileRepositoryException] when it cannot be read. */
    private fun readData(path: String): NSData =
        NSData.dataWithContentsOfFile(path)
            ?: throw FileRepositoryException("Cannot read file at $path")

    /** Malformed UTF-8 bytes become U+FFFD, matching java's lossy readText(). */
    private fun decodeLossy(data: NSData): String {
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) {
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
        return bytes.decodeToString()
    }

    /** UTF-8 bytes of [text] as an NSData, without a String-to-NSString cast. */
    private fun dataFrom(text: String): NSData {
        val bytes = text.encodeToByteArray()
        if (bytes.isEmpty()) return NSData.data()
        return bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
    }

    private fun lastModified(path: String): Long =
        (NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
            ?.get(NSFileModificationDate) as? NSDate)
            ?.timeIntervalSince1970?.toLong()?.times(1000) ?: 0L
}
