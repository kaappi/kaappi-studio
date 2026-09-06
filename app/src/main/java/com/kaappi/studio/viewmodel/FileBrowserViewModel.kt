package com.kaappi.studio.viewmodel

import androidx.lifecycle.ViewModel
import com.kaappi.studio.data.FileRepository
import com.kaappi.studio.data.SchemeFileNames
import com.kaappi.studio.domain.SchemeFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FileBrowserViewModel(private val fileRepository: FileRepository) : ViewModel() {

    private val _files = MutableStateFlow<List<SchemeFile>>(emptyList())
    val files: StateFlow<List<SchemeFile>> = _files.asStateFlow()

    fun refresh() {
        _files.value = fileRepository.listFiles()
    }

    fun saveFile(name: String, content: String): SchemeFile =
        fileRepository.writeFile(name, content).also { refresh() }

    /**
     * True when a file with this name (sanitized, base name) already exists on
     * disk. Used by the new-file flow so it can confirm before overwriting
     * (issue #10). Throws [IllegalArgumentException] for invalid names.
     */
    fun fileExists(name: String): Boolean {
        val base = SchemeFileNames.sanitize(name)
        return fileRepository.listFiles().any { it.name == base }
    }

    fun deleteFile(path: String) {
        fileRepository.deleteFile(path)
        refresh()
    }

    fun renameFile(oldPath: String, newName: String): SchemeFile? =
        fileRepository.renameFile(oldPath, newName).also { refresh() }

    fun readFile(path: String): String = fileRepository.readFile(path)
}
