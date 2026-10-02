package com.miniproject.transport.tor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.torproject.jni.TorService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TorServiceManager(private val context: Context) {

    companion object {
        private const val TAG = "TorServiceManager"
    }

    @Volatile
    private var boundService: TorService? = null

    private var pendingContinuation: kotlin.coroutines.Continuation<TorService>? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != TorService.ACTION_STATUS) return
            val status = intent.getStringExtra(TorService.EXTRA_STATUS) ?: return
            Log.i(TAG, "Status: $status")
            if (status == TorService.STATUS_ON) {
                val cont = pendingContinuation
                pendingContinuation = null
                val svc = boundService
                if (cont != null && svc != null) {
                    cont.resume(svc)
                }
            } else if (status == TorService.STATUS_STOPPING) {
                val cont = pendingContinuation
                pendingContinuation = null
                cont?.resumeWithException(IllegalStateException("Tor stopped before reaching STATUS_ON"))
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder is TorService.LocalBinder) {
                boundService = binder.service
                Log.i(TAG, "TorService bound")
                // Do not resume yet - wait for STATUS_ON broadcast
            } else {
                Log.e(TAG, "Unexpected binder type: ${binder?.javaClass?.name}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            Log.w(TAG, "TorService disconnected")
        }
    }

    suspend fun startAndBind(timeoutMillis: Long = 180_000): TorService {
        boundService?.let { return it }

        val torrcFile = TorService.getTorrc(context)
        torrcFile.parentFile?.mkdirs()
        val torDir = context.getDir("TorService", Context.MODE_PRIVATE).absolutePath
        torrcFile.writeText("""
            SOCKSPort 9050
            HTTPTunnelPort 8118
            DataDirectory $torDir/data
            Log notice file $torDir/data/tor.log
        """.trimIndent())
        Log.i(TAG, "Wrote torrc to ${torrcFile.absolutePath}")

        val filter = IntentFilter(TorService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(statusReceiver, filter)
        }

        return try {
            withTimeout(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    val intent = Intent(context, TorService::class.java)
                    pendingContinuation = continuation
                    val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    if (!bound) {
                        pendingContinuation = null
                        continuation.resumeWithException(IllegalStateException("bindService returned false"))
                    }
                    continuation.invokeOnCancellation {
                        pendingContinuation = null
                        try { context.unregisterReceiver(statusReceiver) } catch (_: Exception) {}
                        unbind()
                    }
                }
            }
        } catch (e: Exception) {
            try { context.unregisterReceiver(statusReceiver) } catch (_: Exception) {}
            throw e
        }
    }

    fun unbind() {
        try { context.unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        if (boundService != null) {
            try { context.unbindService(connection) } catch (_: Exception) {}
            boundService = null
        }
    }
}
