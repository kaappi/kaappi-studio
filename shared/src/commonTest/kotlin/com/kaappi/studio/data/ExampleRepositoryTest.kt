package com.kaappi.studio.data

import com.kaappi.studio.domain.ExampleCategory
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
