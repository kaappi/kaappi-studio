package com.kaappi.studio.data

import com.kaappi.studio.domain.SchemeFile

/**
 * Thrown by [FileRepository] read/write operations on I/O failure (missing or
 * unreadable file, unwritable location, ...). Invalid *names* are reported as
 * [IllegalArgumentException] instead (see [SchemeFileNames.sanitize]).
 */
class FileRepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Canonical, platform-independent file-name handling for user Scheme files.
 * Both [FileRepository] actuals (and, on iOS, the Swift copy of this rule in
 * `FileBrowserViewModel.swift`) validate through it, so a hostile name behaves
 * identically everywhere.
 */
object SchemeFileNames {
    /** Extension appended to every stored file. */
    const val EXTENSION = ".scm"

    private val VALID_NAME = Regex("[A-Za-z0-9_\\- ]+")

    /**
     * Validates a user-supplied file name and returns its base name without
     * extension. Leading/trailing whitespace is trimmed and a trailing
     * [EXTENSION] is accepted and stripped.
     *
     * @throws IllegalArgumentException when the name is blank or contains
     * characters outside `[A-Za-z0-9_\- ]` — in particular `/`, `\`, "." and
     * "..", so a hostile name can never escape the schemes directory or form a
     * path.
     */
    fun sanitize(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "File name must not be empty" }
        val base = trimmed.removeSuffix(EXTENSION)
        require(VALID_NAME.matches(base)) {
            "File name '$name' may only contain letters, digits, '_', '-' and spaces"
        }
        return base
    }

    /** Appends [EXTENSION] unless the base name already carries it. */
    fun withExtension(base: String): String =
        if (base.endsWith(EXTENSION)) base else "$base$EXTENSION"
}

/**
 * Platform file repository for the user's Scheme files, rooted at a
 * per-platform "schemes" directory (`<filesDir>/schemes` on Android,
 * `Documents/schemes` on iOS).
 *
 * # Contract (every actual must obey — issues #8, #17)
 *
 * - **Names are sanitized centrally:** every name argument goes through
 *   [SchemeFileNames.sanitize]; invalid names throw [IllegalArgumentException]
 *   before any file system access.
 * - **Writes are atomic:** the new content fully lands before it replaces the
 *   target file, so a crash mid-save cannot destroy the previous version.
 * - **[writeFile][FileRepository.writeFile]** intentionally overwrites an
 *   existing file of the same name (callers must guard that, e.g. the new-file
 *   flow) and throws [FileRepositoryException] on failure — it never
 *   fabricates success.
 * - **[readFile][FileRepository.readFile]** throws [FileRepositoryException]
 *   when the file is missing or unreadable; it never silently returns "" (an
 *   empty-editor fallback would let a later save destroy the real content).
 *   Invalid UTF-8 bytes decode lossily (U+FFFD), consistent with
 *   [listFiles][FileRepository.listFiles].
 * - **[renameFile][FileRepository.renameFile]** refuses to overwrite an
 *   existing destination; it returns null on any failure, including a name
 *   collision, and never silently replaces data.
 * - **[listFiles][FileRepository.listFiles]** returns every `.scm` file in the
 *   directory, newest first. Non-UTF-8 content is decoded lossily (U+FFFD)
 *   rather than dropping the file, so it stays visible and deletable.
 */
expect class FileRepository {
    fun listFiles(): List<SchemeFile>
    fun readFile(path: String): String
    fun writeFile(name: String, content: String): SchemeFile
    fun deleteFile(path: String): Boolean
    fun renameFile(oldPath: String, newName: String): SchemeFile?
}
