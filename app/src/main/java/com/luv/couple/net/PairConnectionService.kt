package com.luv.couple.net

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.luv.couple.LuvApp
import com.luv.couple.MainActivity
import com.luv.couple.R
import com.luv.couple.data.ConnectionState
import com.luv.couple.data.Role
import com.luv.couple.lock.CanvasStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class PairConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null
    private val running = AtomicBoolean(false)
    private val foregroundStarted = AtomicBoolean(false)
    private val webSocket = AtomicReference<WebSocket?>(null)

    private val wsClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground(getString(R.string.notification_connecting))

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_SEND_STROKE -> {
                val payload = intent.getStringExtra(EXTRA_PAYLOAD)
                if (payload != null) sendRaw(payload)
                return START_STICKY
            }
            ACTION_SEND_CLEAR -> {
                sendRaw(PairProtocol.encode(PairMessage.Clear))
                return START_STICKY
            }
            else -> startSession()
        }
        return START_STICKY
    }

    private fun ensureForeground(text: String) {
        if (foregroundStarted.get()) {
            updateNotification(text)
            return
        }
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(text),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
            foregroundStarted.set(true)
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
            stopSelf()
        }
    }

    private fun startSession() {
        if (!running.compareAndSet(false, true)) return
        ensureForeground(getString(R.string.notification_connecting))
        sessionJob = scope.launch {
            while (isActive && running.get()) {
                val snapshot = runCatching { LuvApp.instance.prefs.snapshot() }.getOrNull()
                val code = snapshot?.inviteCode?.let { LuvApiClient.normalizeCode(it) }
                val token = snapshot?.token
                val role = snapshot?.role
                if (snapshot == null || !snapshot.paired || code == null || token.isNullOrBlank() || role == null) {
                    updateState(ConnectionState.IDLE)
                    break
                }

                updateState(
                    if (role == Role.HOST) ConnectionState.HOSTING else ConnectionState.CONNECTING
                )
                ensureForeground(
                    if (role == Role.HOST) {
                        getString(R.string.notification_hosting)
                    } else {
                        getString(R.string.notification_connecting)
                    }
                )

                val connected = connectOnce(code, token, role.name.lowercase())
                closeSocket()
                if (!running.get()) break
                if (!connected) {
                    updateState(ConnectionState.RECONNECTING)
                    ensureForeground(getString(R.string.notification_connecting))
                    delay(1500)
                } else {
                    updateState(ConnectionState.RECONNECTING)
                    delay(800)
                }
            }
        }
    }

    private suspend fun connectOnce(code: String, token: String, role: String): Boolean {
        val opened = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        val request = Request.Builder()
            .url(LuvApiClient.wsUrl(code, token, role))
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.set(true)
                Log.i(TAG, "ws open")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                closed.set(true)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.set(true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "ws failure", t)
                closed.set(true)
            }
        }

        val socket = wsClient.newWebSocket(request, listener)
        webSocket.set(socket)

        // Warten bis Verbindung endet (Reconnect-Schleife außen)
        while (running.get() && !closed.get()) {
            delay(300)
        }
        return opened.get()
    }

    private fun handleIncoming(text: String) {
        // Server-Steuerungsnachrichten
        runCatching {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "welcome", "peers" -> {
                    val peers = json.optInt("peers", json.optInt("count", 0))
                    if (peers >= 2) {
                        updateState(ConnectionState.CONNECTED)
                        ensureForeground(getString(R.string.notification_title))
                    } else {
                        updateState(ConnectionState.HOSTING)
                        ensureForeground(getString(R.string.notification_hosting))
                    }
                    return
                }
            }
        }

        when (val message = PairProtocol.decode(text)) {
            is PairMessage.StrokeMsg -> {
                CanvasStore.addRemoteStroke(message.stroke)
                scope.launch { _events.emit(PairEvent.StrokeReceived(message.stroke)) }
            }
            PairMessage.Clear -> {
                CanvasStore.clear(localOnly = true)
                scope.launch { _events.emit(PairEvent.Cleared) }
            }
            is PairMessage.Hello,
            is PairMessage.HelloOk,
            PairMessage.Ping,
            PairMessage.Pong,
            null -> Unit
        }
    }

    private fun sendRaw(line: String) {
        val socket = webSocket.get() ?: return
        socket.send(line)
    }

    private fun closeSocket() {
        runCatching { webSocket.getAndSet(null)?.close(1000, "bye") }
    }

    private fun stopSelfSafely() {
        running.set(false)
        sessionJob?.cancel()
        closeSocket()
        updateState(ConnectionState.IDLE)
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        foregroundStarted.set(false)
        stopSelf()
    }

    private fun buildNotification(text: String): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, LuvApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pending)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        if (!foregroundStarted.get()) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        running.set(false)
        sessionJob?.cancel()
        closeSocket()
        scope.cancel()
        updateState(ConnectionState.IDLE)
        foregroundStarted.set(false)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LuvPairService"
        const val ACTION_START = "com.luv.couple.ACTION_START"
        const val ACTION_STOP = "com.luv.couple.ACTION_STOP"
        const val ACTION_SEND_STROKE = "com.luv.couple.ACTION_SEND_STROKE"
        const val ACTION_SEND_CLEAR = "com.luv.couple.ACTION_SEND_CLEAR"
        const val EXTRA_PAYLOAD = "payload"
        private const val NOTIFICATION_ID = 42

        private val _state = MutableStateFlow(ConnectionState.IDLE)
        val state: StateFlow<ConnectionState> = _state.asStateFlow()

        private val _events = MutableSharedFlow<PairEvent>(extraBufferCapacity = 64)
        val events: SharedFlow<PairEvent> = _events.asSharedFlow()

        private fun updateState(value: ConnectionState) {
            _state.value = value
        }

        fun start(context: Context): Boolean {
            return try {
                val intent = Intent(context, PairConnectionService::class.java).setAction(ACTION_START)
                context.startForegroundService(intent)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to start pair service", t)
                false
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, PairConnectionService::class.java).setAction(ACTION_STOP)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to stop pair service", t)
            }
        }

        fun sendStroke(context: Context, strokeJson: String) {
            try {
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_STROKE)
                    .putExtra(EXTRA_PAYLOAD, strokeJson)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to send stroke", t)
            }
        }

        fun sendClear(context: Context) {
            try {
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_CLEAR)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to send clear", t)
            }
        }
    }
}

sealed class PairEvent {
    data class StrokeReceived(val stroke: com.luv.couple.data.Stroke) : PairEvent()
    data object Cleared : PairEvent()
}
