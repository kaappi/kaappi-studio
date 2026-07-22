package com.kaappi.studio.data

import com.kaappi.studio.domain.SchemeFile

expect class FileRepository {
    fun listFiles(): List<SchemeFile>
    fun readFile(path: String): String
    fun writeFile(name: String, content: String): SchemeFile
    fun deleteFile(path: String): Boolean
    fun renameFile(oldPath: String, newName: String): SchemeFile?
}
