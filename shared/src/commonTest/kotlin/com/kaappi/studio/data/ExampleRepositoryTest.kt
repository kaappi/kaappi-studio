package com.kaappi.studio.data

import com.kaappi.studio.domain.ExampleCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExampleRepositoryTest {

    @Test
    fun examples_areNonEmpty() {
        assertTrue(ExampleRepository.examples.isNotEmpty())
    }

    @Test
    fun examples_haveUniqueIds() {
        val ids = ExampleRepository.examples.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "example ids must be unique")
    }

    @Test
    fun examples_haveNonBlankMetadata() {
        for (example in ExampleRepository.examples) {
            assertTrue(example.title.isNotBlank(), "${example.id}: title must not be blank")
            assertTrue(example.description.isNotBlank(), "${example.id}: description must not be blank")
            assertTrue(example.code.isNotBlank(), "${example.id}: code must not be blank")
        }
    }

    @Test
    fun examples_coverEveryCategory() {
        val used = ExampleRepository.examples.map { it.category }.toSet()
        assertEquals(ExampleCategory.entries.toSet(), used, "every category should have at least one example")
    }

    @Test
    fun byCategory_groupsAllExamples() {
        val grouped = ExampleRepository.byCategory()
        assertEquals(
            ExampleRepository.examples.size,
            grouped.values.sumOf { it.size },
            "byCategory() must not drop examples",
        )
        for ((category, examples) in grouped) {
            assertTrue(examples.all { it.category == category }, "$category bucket has foreign examples")
        }
    }

    @Test
    fun examples_stayWithinIosWorkloadBounds() {
        // iOS executes Scheme inside the WebView's main thread with no timeout
        // (see AGENTS.md "Execution model"): a heavy example freezes the whole
        // UI for seconds. The old tail-recursion demo (million-iteration loop
        // plus Ackermann ack(3,7), issue #19) was the worst offender. Flag bare
        // integer literals of 7+ digits (million-scale loop bounds like
        // `1000000`), while ignoring digit runs inside a decimal constant
        // (e.g. pi). 100000 stays allowed — that is the shipped tail-recursion
        // demo's bound.
        val giantIntegerLiteral = Regex("(?<![\\d.])\\d{7,}(?![\\d.])")
        for (example in ExampleRepository.examples) {
            assertNull(
                giantIntegerLiteral.find(example.code)?.value,
                "${example.id}: code must not embed million-scale integer literals (e.g. million-iteration loops)",
            )
            assertTrue(
                example.code.length <= MAX_CODE_LENGTH,
                "${example.id}: code is ${example.code.length} chars; keep examples under $MAX_CODE_LENGTH chars",
            )
        }
    }

    private companion object {
        // The largest shipped example (library) is ~630 chars; the cap leaves
        // headroom for formatting while catching runaway additions.
        const val MAX_CODE_LENGTH = 1000
    }
}
