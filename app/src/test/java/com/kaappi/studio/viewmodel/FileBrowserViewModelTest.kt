package com.kaappi.studio.viewmodel

import com.kaappi.studio.contextWithFilesDir
import com.kaappi.studio.data.FileRepository
import com.kaappi.studio.data.FileRepositoryException
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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
    fun fileExists_matchesSanitizedBaseNamesOnDisk() = runTest {
        val (vm, _) = newViewModel()

        assertFalse(vm.fileExists("hello"))

        vm.saveFile("hello", "")

        assertTrue(vm.fileExists("hello"))
        assertTrue(vm.fileExists("hello.scm"))
        assertFalse(vm.fileExists("other"))
    }

    @Test
    fun fileExists_rejectsInvalidNames() = runTest {
        val (vm, _) = newViewModel()

        assertThrows(IllegalArgumentException::class.java) { runBlocking { vm.fileExists("../x") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { vm.fileExists("foo/bar") } }
    }

    @Test
    fun saveFile_returnsTheSanitizedFileAndRefreshesTheList() = runTest {
        val (vm, _) = newViewModel()

        val saved = vm.saveFile("  my file  ", "(display 1)")

        assertEquals("my file", saved.name)
        assertTrue(vm.files.value.any { it.name == "my file" })
    }

    @Test
    fun saveFile_propagatesRepositoryFailuresInsteadOfFabricatingSuccess() = runTest {
        val (vm, _) = newViewModel()

        assertThrows(IllegalArgumentException::class.java) { runBlocking { vm.saveFile("foo/bar", "") } }
        assertTrue(vm.files.value.isEmpty())
    }

    @Test
    fun deleteFile_removesTheFileAndRefreshesTheList() = runTest {
        val (vm, dir) = newViewModel()
        val saved = vm.saveFile("gone", "(display 1)")

        vm.deleteFile(saved.path)

        assertFalse(File(dir, "gone.scm").exists())
        assertFalse(vm.files.value.any { it.name == "gone" })
    }

    @Test
    fun deleteFile_throwsWhenNothingWasDeletedInsteadOfFabricatingSuccess() = runTest {
        // The caller resets editor state after a delete; it must never do so
        // for a file that is still on disk (issue #15).
        val (vm, dir) = newViewModel()
        vm.saveFile("keep", "(display 1)")

        assertThrows(FileRepositoryException::class.java) {
            runBlocking { vm.deleteFile(File(dir, "missing.scm").absolutePath) }
        }

        assertTrue("the list must still reflect the disk", vm.files.value.any { it.name == "keep" })
    }

    @Test
    fun readFile_returnsContentsThatTheListingDoesNotCarry() = runTest {
        val (vm, _) = newViewModel()
        val saved = vm.saveFile("prog", "(display 42)")

        assertEquals("(display 42)", vm.readFile(saved.path))
    }

    @Test
    fun readFile_propagatesMissingFilesInsteadOfReturningEmpty() = runTest {
        val (vm, dir) = newViewModel()

        assertThrows(FileRepositoryException::class.java) {
            runBlocking { vm.readFile(File(dir, "missing.scm").absolutePath) }
        }
    }
}
