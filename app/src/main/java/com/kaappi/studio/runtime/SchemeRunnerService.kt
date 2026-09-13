package com.kaappi.studio.runtime

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts [SchemeRunner] in the dedicated `:runner` process (see the manifest).
 *
 * Nothing but Scheme programs runs in that process, so [IsolatedSchemeRunner]
 * can terminate a runaway program with `Process.killProcess` — the only way to
 * stop Chicory 1.7.5, which has no interrupt or fuel hooks (issue #24). A
 * program that exhausts memory takes this process down instead of the app.
 *
 * Bound, never started: the service lives while a run's binding exists and is
 * destroyed when the client unbinds. The process itself survives as a cached
 * process between runs, keeping the parsed WASM module warm in [moduleCache],
 * until the system or a Stop reclaims it.
 */
class SchemeRunnerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val messenger = Messenger(Handler(Looper.getMainLooper(), Handler.Callback(::handle)))

    override fun onCreate() {
        super.onCreate()
        // Debris from killed runs and crashed clients. Nothing here is still
        // needed by a live run — see sweepStaleRunFiles for the one abandoned
        // case a cached process can still be executing.
        RunnerProtocol.sweepStaleRunFiles(cacheDir)
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handle(msg: Message): Boolean {
        if (msg.what != RunnerProtocol.MSG_RUN) return false
        msg.replyTo?.let { startRun(it, msg.data.getString(RunnerProtocol.KEY_CODE).orEmpty()) }
        return true
    }

    private fun startRun(replyTo: Messenger, code: String) {
        // Acknowledge with our pid before executing so the client can kill us
        // even if Stop arrives before the program prints anything.
        replyTo.trySend(Message.obtain(null, RunnerProtocol.MSG_STARTED, Process.myPid(), 0))

        val runner = SchemeRunner(applicationContext, moduleCache(applicationContext))
        scope.launch {
            // run() reports its own failures through stderr and never throws;
            // writing the result files is the one step left that can, and an
            // uncaught exception here would kill the process with a misleading
            // "exited unexpectedly" on the client side.
            val result = runner.run(code)
            val payload = try {
                RunnerProtocol.encodeResult(result, cacheDir)
            } catch (e: IOException) {
                Log.w(TAG, "Could not write result files", e)
                RunnerProtocol.encodeFailure("Could not write the program's output: ${e.message}")
            }
            replyTo.trySend(Message.obtain(null, RunnerProtocol.MSG_RESULT).apply { data = payload })
        }
    }

    private fun Messenger.trySend(message: Message) {
        try {
            send(message)
        } catch (e: RemoteException) {
            // The client is gone (it unbound, or the app process died); there
            // is nobody left to deliver to.
            Log.w(TAG, "Dropping ${message.what}: client unreachable", e)
        }
    }

    private companion object {
        const val TAG = "SchemeRunnerService"

        /** Parsed once per runner process; service instances come and go with each binding. */
        @Volatile
        private var moduleCache: SchemeRunner.ModuleCache? = null

        fun moduleCache(context: Context): SchemeRunner.ModuleCache = synchronized(this) {
            moduleCache ?: SchemeRunner.ModuleCache {
                context.assets.open("kaappi.wasm").use { it.readBytes() }
            }.also { moduleCache = it }
        }
    }
}
