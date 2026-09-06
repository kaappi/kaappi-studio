package com.kaappi.studio.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchemeFileNamesTest {

    @Test
    fun sanitize_trimsWhitespaceAndStripsTheScmSuffix() {
        assertEquals("hello", SchemeFileNames.sanitize("hello"))
        assertEquals("hello", SchemeFileNames.sanitize("hello.scm"))
        assertEquals("my file", SchemeFileNames.sanitize("  my file  "))
        assertEquals("a-b_c", SchemeFileNames.sanitize("a-b_c"))
    }

    @Test
    fun sanitize_rejectsPathSeparatorsTraversalAndBlankNames() {
        for (bad in listOf(
            "", "   ", "foo/bar", "../x", "..", ".", "a\\b", "foo.bar", "a:b",
            "fo'o", "héllo", "foo\tbar", "foo\nbar",
        )) {
            assertFailsWith<IllegalArgumentException>("name '$bad' must be rejected") {
                SchemeFileNames.sanitize(bad)
            }
        }
    }

    @Test
    fun withExtension_alwaysAppends() {
        assertEquals("hello.scm", SchemeFileNames.withExtension("hello"))
        // Deliberately strict: callers pass sanitized bases (sanitize strips a
        // trailing extension), so withExtension appends unconditionally — same
        // as the Swift mirror in FileBrowserViewModel.swift.
        assertEquals("hello.scm.scm", SchemeFileNames.withExtension("hello.scm"))
    }
}
