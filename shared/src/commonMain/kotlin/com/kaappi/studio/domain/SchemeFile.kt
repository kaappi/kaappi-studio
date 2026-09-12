package com.kaappi.studio.domain

import kotlinx.serialization.Serializable

/**
 * A user Scheme file as listed by `FileRepository.listFiles`: base name
 * (without `.scm`), absolute path and modification time. Contents are
 * deliberately not part of the model — listing must never read every file
 * (issue #12); open a file with `FileRepository.readFile(path)`.
 */
@Serializable
data class SchemeFile(
    val name: String,
    val path: String,
    val lastModified: Long = 0L,
)
