package com.kaappi.studio.domain

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemeFileTest {

    @Test
    fun serialization_roundTrips() {
        val file = SchemeFile(
            name = "factorial",
            path = "/data/schemes/factorial.scm",
            lastModified = 1_724_000_000_000,
        )
        val json = Json.encodeToString(SchemeFile.serializer(), file)
        val decoded = Json.decodeFromString(SchemeFile.serializer(), json)
        assertEquals(file, decoded)
    }

    @Test
    fun serialization_usesDeclaredDefaults() {
        val json = """{"name":"hello","path":"/tmp/hello.scm"}"""
        val decoded = Json.decodeFromString(SchemeFile.serializer(), json)
        assertEquals("hello", decoded.name)
        assertEquals(0L, decoded.lastModified)
    }
}
