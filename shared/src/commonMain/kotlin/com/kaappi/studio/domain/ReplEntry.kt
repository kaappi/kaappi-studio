package com.kaappi.studio.domain

data class ReplEntry(
    val input: String,
    val output: String,
    val error: String = "",
    val elapsedMs: Double = 0.0,
)
