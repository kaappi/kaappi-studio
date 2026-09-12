package com.kaappi.studio.data

import com.kaappi.studio.contextWithFilesDir
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
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

    /** `assertThrows` for suspend calls: the repository API is suspend-only (issue #12). */
    private inline fun <reified T : Throwable> assertThrowsSuspend(
        message: String? = null,
        crossinline block: suspend () -> Unit,
    ): T = assertThrows(message, T::class.java) { runBlocking { block() } }

    @Test
    fun writeFile_acceptsValidNames_andAppendsScmOnce() = runBlocking<Unit> {
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
            assertThrowsSuspend<IllegalArgumentException>("name '$bad' must be rejected") {
                repo.writeFile(bad, "x")
            }
        }
    }

    @Test
    fun writeFile_neverEscapesTheSchemesDirectory() {
        val (repo, dir) = newRepository()

        for (bad in listOf("foo/bar", "../x", "..")) {
            assertThrowsSuspend<IllegalArgumentException> { repo.writeFile(bad, "x") }
        }

        assertTrue(
            "nothing may be written outside the schemes directory",
            dir.parentFile!!.walkTopDown().filter { it.isFile }.toList().isEmpty(),
        )
    }

    @Test
    fun writeFile_overwritesAndLeavesNoTempFilesBehind() = runBlocking<Unit> {
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

        assertThrowsSuspend<FileRepositoryException> { repo.writeFile("hello", "x") }
    }

    @Test
    fun readFile_throwsOnMissingOrUnreadableFile() {
        val (repo, _) = newRepository()

        assertThrowsSuspend<FileRepositoryException> { repo.readFile("/no/such/file.scm") }
    }

    @Test
    fun readFile_decodesInvalidUtf8Lossily() = runBlocking<Unit> {
        val (repo, dir) = newRepository()
        repo.listFiles() // creates the directory
        val file = File(dir, "bad.scm").apply { writeBytes(byteArrayOf(0x28, 0xFF.toByte(), 0x29)) }

        assertEquals("(\uFFFD)", repo.readFile(file.absolutePath))
    }

    @Test
    fun listFiles_returnsNamesAndMtimesWithoutContents() = runBlocking<Unit> {
        val (repo, dir) = newRepository()
        repo.writeFile("newer", "(display 2)")
        val older = File(dir, "older.scm").apply {
            writeText("(display 1)")
            setLastModified(System.currentTimeMillis() - 60_000)
        }

        val files = repo.listFiles()

        assertEquals(listOf("newer", "older"), files.map { it.name })
        assertEquals(older.absolutePath, files[1].path)
        assertEquals(older.lastModified(), files[1].lastModified)
    }

    @Test
    fun listFiles_keepsNonUtf8FilesVisible() = runBlocking<Unit> {
        val (repo, dir) = newRepository()
        repo.listFiles() // creates the directory
        File(dir, "bad.scm").writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))

        assertEquals(listOf("bad"), repo.listFiles().map { it.name })
    }

    @Test
    fun listFiles_keepsUnreadableFilesVisible_andReadFileReportsThem() = runBlocking<Unit> {
        // Contract (issue #12): listing never reads contents, so an unreadable
        // file stays listed and deletable; opening it is what fails.
        val (repo, dir) = newRepository()
        repo.listFiles() // creates the directory
        val locked = File(dir, "locked.scm").apply {
            writeText("secret")
            setReadable(false)
        }
        // Root (and some CI file systems) ignore permission bits; the case is
        // then untestable here rather than failing.
        assumeFalse("file must be unreadable for this test", locked.canRead())

        assertEquals(listOf("locked"), repo.listFiles().map { it.name })
        assertThrowsSuspend<FileRepositoryException> { repo.readFile(locked.absolutePath) }
    }

    @Test
    fun renameFile_refusesToOverwriteAnExistingDestination() = runBlocking<Unit> {
        val (repo, dir) = newRepository()
        val a = repo.writeFile("a", "AAA")
        repo.writeFile("b", "BBB")

        assertNull(repo.renameFile(a.path, "b"))

        assertEquals("source must survive the refused rename", "AAA", File(dir, "a.scm").readText())
        assertEquals("destination must survive the refused rename", "BBB", File(dir, "b.scm").readText())
    }

    @Test
    fun renameFile_movesWhenTheDestinationIsFree() = runBlocking<Unit> {
        val (repo, dir) = newRepository()
        val a = repo.writeFile("a", "AAA")

        val renamed = repo.renameFile(a.path, "c")

        assertEquals("c", renamed?.name)
        assertFalse(File(dir, "a.scm").exists())
        assertEquals("AAA", File(dir, "c.scm").readText())
    }

    @Test
    fun renameFile_toItsOwnNameIsANoOpSuccess() = runBlocking<Unit> {
        val (repo, dir) = newRepository()
        val a = repo.writeFile("a", "AAA")

        val renamed = repo.renameFile(a.path, "a")

        assertEquals("the file must survive unchanged", "AAA", File(dir, "a.scm").readText())
        assertEquals("a", renamed?.name)
    }

    @Test
    fun firstUse_sweepsStaleTempFilesButKeepsRealFiles() = runBlocking<Unit> {
        val filesDir = tmp.newFolder()
        val dir = File(filesDir, "schemes").apply { mkdirs() }
        File(dir, "crash.scm.tmp").writeText("partial write from a crashed save")
        File(dir, "keep.scm").writeText("real")

        // The sweep is deferred to the first operation so construction (on
        // the main thread, in the ViewModel factory) does no I/O (issue #12).
        val repo = FileRepository(contextWithFilesDir(filesDir))
        assertTrue("construction must not touch the disk", File(dir, "crash.scm.tmp").exists())
        repo.listFiles()

        assertFalse("stale atomic-write temp files must be swept at first use", File(dir, "crash.scm.tmp").exists())
        assertEquals("real files must survive the sweep", "real", File(dir, "keep.scm").readText())
    }

    @Test
    fun renameFile_rejectsInvalidNames() = runBlocking<Unit> {
        val (repo, _) = newRepository()
        val a = repo.writeFile("a", "AAA")

        assertThrowsSuspend<IllegalArgumentException> { repo.renameFile(a.path, "x/y") }
    }
}
