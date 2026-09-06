package com.kaappi.studio.data

import com.kaappi.studio.contextWithFilesDir
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** A repository rooted at a fresh temp `<filesDir>/schemes`. */
    private fun newRepository(): Pair<FileRepository, File> {
        val filesDir = tmp.newFolder()
        return FileRepository(contextWithFilesDir(filesDir)) to File(filesDir, "schemes")
    }

    @Test
    fun writeFile_acceptsValidNames_andAppendsScmOnce() {
        val (repo, dir) = newRepository()

        assertEquals("hello", repo.writeFile("hello", "v1").name)
        assertEquals("hello", repo.writeFile("hello.scm", "v2").name)
        assertEquals("my file", repo.writeFile("  my file  ", "v1").name)

        assertEquals(
            listOf("hello.scm", "my file.scm").sorted(),
            dir.listFiles()!!.map { it.name }.sorted(),
        )
        assertEquals("v2", File(dir, "hello.scm").readText())
    }

    @Test
    fun writeFile_rejectsPathSeparatorsTraversalAndBlankNames() {
        val (repo, _) = newRepository()

        for (bad in listOf("", "   ", "foo/bar", "../x", "..", ".", "a\\b", "foo.bar", "a:b")) {
            assertThrows("name '$bad' must be rejected", IllegalArgumentException::class.java) {
                repo.writeFile(bad, "x")
            }
        }
    }

    @Test
    fun writeFile_neverEscapesTheSchemesDirectory() {
        val (repo, dir) = newRepository()

        for (bad in listOf("foo/bar", "../x", "..")) {
            assertThrows(IllegalArgumentException::class.java) { repo.writeFile(bad, "x") }
        }

        assertTrue(
            "nothing may be written outside the schemes directory",
            dir.parentFile!!.walkTopDown().filter { it.isFile }.toList().isEmpty(),
        )    }

    @Test
    fun writeFile_overwritesAndLeavesNoTempFilesBehind() {
        val (repo, dir) = newRepository()

        repo.writeFile("hello", "v1")
        repo.writeFile("hello", "v2")

        assertEquals("v2", File(dir, "hello.scm").readText())
        assertTrue(
            "atomic-write temp files must be cleaned up",
            dir.listFiles()!!.none { it.name.endsWith(".tmp") },
        )
    }

    @Test
    fun writeFile_wrapsIoFailuresInsteadOfFabricatingSuccess() {
        // filesDir is a regular FILE, so <filesDir>/schemes can never exist and
        // every write must fail with an IOException, wrapped by the repository.
        val repo = FileRepository(contextWithFilesDir(tmp.newFile("blocker")))

        assertThrows(FileRepositoryException::class.java) { repo.writeFile("hello", "x") }
    }

    @Test
    fun readFile_throwsOnMissingOrUnreadableFile() {
        val (repo, _) = newRepository()

        assertThrows(FileRepositoryException::class.java) { repo.readFile("/no/such/file.scm") }
    }

    @Test
    fun readFile_decodesInvalidUtf8Lossily() {
        val (repo, dir) = newRepository()
        val file = File(dir, "bad.scm").apply { writeBytes(byteArrayOf(0x28, 0xFF.toByte(), 0x29)) }

        assertEquals("(\uFFFD)", repo.readFile(file.absolutePath))
    }

    @Test
    fun listFiles_keepsNonUtf8FilesVisibleWithLossyContent() {
        val (repo, dir) = newRepository()
        File(dir, "bad.scm").writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))

        val files = repo.listFiles()

        assertEquals(1, files.size)
        assertEquals("\uFFFD\uFFFD", files[0].content)
    }

    @Test
    fun renameFile_refusesToOverwriteAnExistingDestination() {
        val (repo, dir) = newRepository()
        val a = repo.writeFile("a", "AAA")
        repo.writeFile("b", "BBB")

        assertNull(repo.renameFile(a.path, "b"))

        assertEquals("source must survive the refused rename", "AAA", File(dir, "a.scm").readText())
        assertEquals("destination must survive the refused rename", "BBB", File(dir, "b.scm").readText())
    }

    @Test
    fun renameFile_movesWhenTheDestinationIsFree() {
        val (repo, dir) = newRepository()
        val a = repo.writeFile("a", "AAA")

        val renamed = repo.renameFile(a.path, "c")

        assertEquals("c", renamed?.name)
        assertFalse(File(dir, "a.scm").exists())
        assertEquals("AAA", File(dir, "c.scm").readText())
    }

    @Test
    fun renameFile_rejectsInvalidNames() {
        val (repo, _) = newRepository()
        val a = repo.writeFile("a", "AAA")

        assertThrows(IllegalArgumentException::class.java) { repo.renameFile(a.path, "x/y") }
    }
}
