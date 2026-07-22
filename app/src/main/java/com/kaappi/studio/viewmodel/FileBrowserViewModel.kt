package com.kaappi.studio.viewmodel

import androidx.lifecycle.ViewModel
import com.kaappi.studio.data.FileRepository
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

    fun deleteFile(path: String) {
        fileRepository.deleteFile(path)
        refresh()
    }

    fun renameFile(oldPath: String, newName: String): SchemeFile? =
        fileRepository.renameFile(oldPath, newName).also { refresh() }

    fun readFile(path: String): String = fileRepository.readFile(path)
}
