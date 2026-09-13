package com.kaappi.studio.runtime

import com.kaappi.studio.domain.RunResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsolatedRunSessionTest {

    private val kills = mutableListOf<Int>()
    private val session = IsolatedRunSession { kills += it }
    private val ok = RunResult(stdout = "hi", stderr = "", elapsedMs = 1.0)

    @Test
    fun result_isDeliveredAndNothingIsKilled() = runBlocking {
        session.onStarted(42)
        session.onResult(ok)

        assertEquals(ok, session.await())
        assertTrue(session.isFinished)
        assertTrue("a finished run must not be killed, killed: $kills", kills.isEmpty())
    }

    @Test
    fun stop_killsTheProcessWhosePidIsKnown() = runBlocking {
        session.onStarted(42)

        session.stop()

        assertEquals(listOf(42), kills)
        assertEquals(IsolatedRunSession.STOPPED_MESSAGE, session.await().stderr)
    }

    @Test
    fun stop_beforeThePidIsKnown_killsAsSoonAsItArrives() = runBlocking {
        session.stop()
        assertTrue("stop must resolve the run immediately", session.isFinished)
        assertTrue(kills.isEmpty())

        session.onStarted(7)

        assertEquals(listOf(7), kills)
        assertEquals(IsolatedRunSession.STOPPED_MESSAGE, session.await().stderr)
    }

    @Test
    fun stop_afterANormalResult_killsNothing() = runBlocking {
        session.onStarted(42)
        session.onResult(ok)

        session.stop()

        assertTrue("an idle runner process must be left alone, killed: $kills", kills.isEmpty())
        assertEquals(ok, session.await())
    }

    @Test
    fun stop_isIdempotent_andKillsOnce() {
        session.onStarted(42)

        session.stop()
        session.stop()
        session.onStarted(42)

        assertEquals(listOf(42), kills)
    }

    @Test
    fun lateResultOrDeath_doesNotOverrideAStop() = runBlocking {
        session.onStarted(42)
        session.stop()

        session.onResult(ok)
        session.onProcessDied()

        assertEquals(IsolatedRunSession.STOPPED_MESSAGE, session.await().stderr)
    }

    @Test
    fun processDeath_surfacesAsAnErrorResult() = runBlocking {
        session.onStarted(42)

        session.onProcessDied()

        val result = session.await()
        assertEquals(IsolatedRunSession.PROCESS_DIED_MESSAGE, result.stderr)
        assertEquals("", result.stdout)
        assertTrue(kills.isEmpty())
    }

    @Test
    fun startFailure_surfacesItsReason() = runBlocking {
        session.onStartFailed("no service")

        assertEquals("no service", session.await().stderr)
        assertFalse(kills.isNotEmpty())
    }

    @Test
    fun firstOutcomeWins() = runBlocking {
        session.onResult(ok)
        session.onProcessDied()
        session.onStartFailed("late")

        assertEquals(ok, session.await())
    }
}
