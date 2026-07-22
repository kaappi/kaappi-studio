package com.kaappi.studio.domain

data class RunResult(
    val stdout: String,
    val stderr: String,
    val elapsedMs: Double,
)
