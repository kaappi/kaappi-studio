package com.kaappi.studio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows

/**
 * `assertThrows` for suspend calls. `FileRepository` and `FileBrowserViewModel`
 * expose suspend-only APIs (issue #12), and JUnit's `ThrowingRunnable` is not a
 * suspend lambda, so the call is bridged through `runBlocking`.
 */
inline fun <reified T : Throwable> assertThrowsSuspend(
    message: String? = null,
    crossinline block: suspend () -> Unit,
): T = assertThrows(message, T::class.java) { runBlocking { block() } }
