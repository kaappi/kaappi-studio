package com.kaappi.studio.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.util.Log
import com.kaappi.studio.domain.RunResult

/**
 * Runs one Scheme program in the `:runner` process through
 * [SchemeRunnerService], so that [stop] can actually terminate it.
 *
 * Each instance binds the service for its single run, sends the program, and
 * awaits the result. [stop] — pressed by the user, triggered by cancellation
 * of [run], or by ViewModel teardown — unbinds first (so the system does not
 * restart the service) and then kills the runner process by the pid it
 * reported. The next run binds again and Android spawns a fresh `:runner`
 * process. See [IsolatedRunSession] for the ordering guarantees.
 */
class IsolatedSchemeRunner(private val context: Context) : SchemeExecutor {

    private val session = IsolatedRunSession(::killRunnerProcess)
    private val lock = Any()
    private var connection: ServiceConnection? = null
    private var replyThread: HandlerThread? = null
    private var started = false

    override suspend fun run(code: String): RunResult {
        synchronized(lock) {
            check(!started) { "IsolatedSchemeRunner runs exactly one program" }
            started = true
        }
        // Stopped before it even began: nothing to bind.
        if (session.isFinished) return session.await()

        bind(code)
        try {
            return session.await()
        } finally {
            // Normal completion: releases the binding (a no-op kill).
            // Cancellation: the program must not outlive its awaiter.
            stop()
        }
    }

    override fun stop() {
        unbind()
        session.stop()
    }

    private fun bind(code: String) {
        // Replies (the pid ack and the result, whose output files are read on
        // arrival) are handled off the main thread.
        val thread = HandlerThread("kaappi-runner-reply").apply { start() }
        val replyMessenger = Messenger(Handler(thread.looper, Handler.Callback(::onReply)))

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val request = Message.obtain(null, RunnerProtocol.MSG_RUN).apply {
                    data.putString(RunnerProtocol.KEY_CODE, code)
                    replyTo = replyMessenger
                }
                try {
                    Messenger(service).send(request)
                } catch (e: RemoteException) {
                    Log.w(TAG, "Runner process unreachable", e)
                    processDied()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) = processDied()

            override fun onBindingDied(name: ComponentName) = processDied()

            override fun onNullBinding(name: ComponentName) {
                unbind()
                session.onStartFailed("The Scheme runner service refused the connection.")
            }
        }

        synchronized(lock) {
            connection = conn
            replyThread = thread
        }
        val intent = Intent(context, SchemeRunnerService::class.java)
        if (!context.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
            unbind()
            session.onStartFailed("Could not start the Scheme runner service.")
        }
    }

    private fun onReply(msg: Message): Boolean = when (msg.what) {
        RunnerProtocol.MSG_STARTED -> {
            session.onStarted(msg.arg1)
            true
        }
        RunnerProtocol.MSG_RESULT -> {
            session.onResult(RunnerProtocol.decodeResult(msg.data))
            true
        }
        else -> false
    }

    /** Unbind before the process disappears for good, or Android would restart the service. */
    private fun processDied() {
        unbind()
        session.onProcessDied()
    }

    private fun killRunnerProcess(pid: Int) {
        unbind()
        Process.killProcess(pid)
    }

    private fun unbind() {
        val (conn, thread) = synchronized(lock) {
            val pair = connection to replyThread
            connection = null
            replyThread = null
            pair
        }
        // bindService() documents that unbindService() is due even when
        // binding failed, so the connection is released unconditionally.
        conn?.let(context::unbindService)
        thread?.quitSafely()
    }

    private companion object {
        const val TAG = "IsolatedSchemeRunner"
    }
}
