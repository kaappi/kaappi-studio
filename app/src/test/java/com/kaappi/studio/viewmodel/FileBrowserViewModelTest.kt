package com.kaappi.studio.viewmodel

import com.kaappi.studio.contextWithFilesDir
import com.kaappi.studio.data.FileRepository
import com.kaappi.studio.data.FileRepositoryException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileBrowserViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newViewModel(): Pair<FileBrowserViewModel, File> {
        val filesDir = tmp.newFolder()
        return FileBrowserViewModel(FileRepository(contextWithFilesDir(filesDir))) to
            File(filesDir, "schemes")
    }

    @Test
    fun fileExists_matchesSanitizedBaseNamesOnDisk() {
        val (vm, _) = newViewModel()

        assertFalse(vm.fileExists("hello"))

        vm.saveFile("hello", "")

        assertTrue(vm.fileExists("hello"))
        assertTrue(vm.fileExists("hello.scm"))
        assertFalse(vm.fileExists("other"))
    }

    @Test
    fun fileExists_rejectsInvalidNames() {
        val (vm, _) = newViewModel()

        assertThrows(IllegalArgumentException::class.java) { vm.fileExists("../x") }
        assertThrows(IllegalArgumentException::class.java) { vm.fileExists("foo/bar") }
    }

    @Test
    fun saveFile_returnsTheSanitizedFileAndRefreshesTheList() {
        val (vm, _) = newViewModel()

        val saved = vm.saveFile("  my file  ", "(display 1)")

        assertEquals("my file", saved.name)
        assertTrue(vm.files.value.any { it.name == "my file" })
    }

    @Test
    fun saveFile_propagatesRepositoryFailuresInsteadOfFabricatingSuccess() {
        val (vm, _) = newViewModel()

        assertThrows(IllegalArgumentException::class.java) { vm.saveFile("foo/bar", "") }
        assertTrue(vm.files.value.isEmpty())
    }

    @Test
    fun deleteFile_removesTheFileAndRefreshesTheList() {
        val (vm, dir) = newViewModel()
        val saved = vm.saveFile("gone", "(display 1)")

        vm.deleteFile(saved.path)

        assertFalse(File(dir, "gone.scm").exists())
        assertFalse(vm.files.value.any { it.name == "gone" })
    }

    @Test
    fun deleteFile_throwsWhenNothingWasDeletedInsteadOfFabricatingSuccess() {
        // The caller resets editor state after a delete; it must never do so
        // for a file that is still on disk (issue #15).
        val (vm, dir) = newViewModel()
        vm.saveFile("keep", "(display 1)")

        assertThrows(FileRepositoryException::class.java) {
            vm.deleteFile(File(dir, "missing.scm").absolutePath)
        }

        assertTrue("the list must still reflect the disk", vm.files.value.any { it.name == "keep" })
    }
}
