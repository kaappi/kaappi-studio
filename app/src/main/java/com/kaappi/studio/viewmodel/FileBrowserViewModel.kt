package com.kaappi.studio.viewmodel

import androidx.lifecycle.ViewModel
import com.kaappi.studio.data.FileRepository
import com.kaappi.studio.data.FileRepositoryException
import com.kaappi.studio.data.SchemeFileNames
import com.kaappi.studio.domain.SchemeFile
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every operation is a `suspend` function: the repository does its disk I/O
 * on `Dispatchers.IO`, so UI callers launch these from the main thread
 * without ever blocking it (issue #12). Failures propagate as exceptions to
 * the caller, which owns the snackbar.
 */
class FileBrowserViewModel(private val fileRepository: FileRepository) : ViewModel() {

    private val _files = MutableStateFlow<List<SchemeFile>>(emptyList())
    val files: StateFlow<List<SchemeFile>> = _files.asStateFlow()

    suspend fun refresh() {
        _files.value = fileRepository.listFiles()
    }

    suspend fun saveFile(name: String, content: String): SchemeFile =
        fileRepository.writeFile(name, content).also { refresh() }

    /**
     * True when a file with this name (sanitized, base name) already exists on
     * disk. Used by the new-file flow so it can confirm before overwriting
     * (issue #10). Throws [IllegalArgumentException] for invalid names.
     */
    suspend fun fileExists(name: String): Boolean {
        val base = SchemeFileNames.sanitize(name)
        return fileRepository.listFiles().any { it.name == base }
    }

    /**
     * Deletes the file at [path]. The list is refreshed either way so it shows
     * what is actually on disk. Throws [FileRepositoryException] when nothing
     * was deleted (missing file, permission failure), matching the
     * repository's never-fabricate-success contract: callers must not reset
     * editor state for a file that still exists (issue #15).
     */
    suspend fun deleteFile(path: String) {
        val deleted = fileRepository.deleteFile(path)
        refresh()
        if (!deleted) throw FileRepositoryException("Cannot delete ${File(path).name}")
    }

    suspend fun renameFile(oldPath: String, newName: String): SchemeFile? =
        fileRepository.renameFile(oldPath, newName).also { refresh() }

    /** The file's contents; [SchemeFile] itself carries none (issue #12). */
    suspend fun readFile(path: String): String = fileRepository.readFile(path)
}
