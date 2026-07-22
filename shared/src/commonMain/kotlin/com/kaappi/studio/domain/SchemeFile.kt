package com.kaappi.studio.domain

import kotlinx.serialization.Serializable

@Serializable
data class SchemeFile(
    val name: String,
    val path: String,
    val content: String = "",
    val lastModified: Long = 0L,
)
