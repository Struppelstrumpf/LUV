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
import com.luv.couple.data.Lobby
import com.luv.couple.lock.CanvasStore
import com.luv.couple.notify.PartnerStrokeNotifier
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class PairConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val foregroundStarted = AtomicBoolean(false)
    private val sessions = ConcurrentHashMap<String, LobbySession>()

    private val wsClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private data class LobbySession(
        val lobby: Lobby,
        val running: AtomicBoolean = AtomicBoolean(true),
        val webSocket: AtomicReference<WebSocket?> = AtomicReference(null),
        var job: Job? = null,
        val forceReconnect: AtomicBoolean = AtomicBoolean(false),
        @Volatile var backoffMs: Long = MIN_BACKOFF_MS,
        @Volatile var attempt: Int = 0,
        @Volatile var nextRetryAtMs: Long = 0L
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground(getString(R.string.notification_running))

        when (intent?.action) {
            ACTION_STOP -> {
                val lobbyId = intent.getStringExtra(EXTRA_LOBBY_ID)
                if (lobbyId != null) stopLobby(lobbyId)
                else stopAll()
                return START_NOT_STICKY
            }
            ACTION_SEND_STROKE -> {
                val lobbyId = intent.getStringExtra(EXTRA_LOBBY_ID)
                val payload = intent.getStringExtra(EXTRA_PAYLOAD)
                if (payload != null) sendRaw(lobbyId, payload)
                return START_STICKY
            }
            ACTION_SEND_CLEAR -> {
                sendRaw(intent.getStringExtra(EXTRA_LOBBY_ID), PairProtocol.encode(PairMessage.Clear))
                return START_STICKY
            }
            ACTION_SEND_UNDO -> {
                val strokeId = intent.getStringExtra(EXTRA_STROKE_ID)
                if (!strokeId.isNullOrBlank()) {
                    sendRaw(
                        intent.getStringExtra(EXTRA_LOBBY_ID),
                        PairProtocol.encode(PairMessage.UndoMsg(strokeId))
                    )
                }
                return START_STICKY
            }
            ACTION_RECONNECT -> {
                val lobbyId = intent.getStringExtra(EXTRA_LOBBY_ID)
                scope.launch { forceReconnect(lobbyId) }
            }
            ACTION_START_ALL, ACTION_START, null -> {
                scope.launch { syncSessionsFromPrefs() }
            }
        }
        return START_STICKY
    }

    private fun forceReconnect(lobbyId: String?) {
        val targets = if (lobbyId == null) sessions.values else listOfNotNull(sessions[lobbyId])
        targets.forEach { session ->
            session.backoffMs = MIN_BACKOFF_MS
            session.attempt = 0
            session.nextRetryAtMs = 0L
            session.forceReconnect.set(true)
            session.webSocket.get()?.cancel()
            publishReconnect(session)
            if (session.job?.isActive != true) {
                session.running.set(true)
                session.job = scope.launch { runLobbyLoop(session) }
            }
        }
        if (targets.isEmpty()) {
            scope.launch { syncSessionsFromPrefs() }
        }
    }

    private suspend fun syncSessionsFromPrefs() {
        val lobbies = runCatching { LuvApp.instance.prefs.snapshot().lobbies }.getOrDefault(emptyList())
        if (lobbies.isEmpty()) {
            stopAll()
            return
        }
        CanvasStore.updateKnownLobbies(lobbies.map { it.id })
        val wanted = lobbies.associateBy { it.id }
        sessions.keys.filter { it !in wanted }.forEach { stopLobby(it) }
        wanted.values.forEach { ensureLobbySession(it) }
        refreshAggregateState()
    }

    private fun ensureLobbySession(lobby: Lobby) {
        val existing = sessions[lobby.id]
        if (existing != null) {
            if (existing.job?.isActive == true) return
            existing.running.set(true)
            existing.job = scope.launch { runLobbyLoop(existing) }
            return
        }
        val session = LobbySession(lobby = lobby)
        sessions[lobby.id] = session
        session.job = scope.launch { runLobbyLoop(session) }
    }

    private suspend fun runLobbyLoop(session: LobbySession) {
        val lobby = session.lobby
        try {
            while (scope.isActive && session.running.get()) {
                session.forceReconnect.set(false)
                updateLobbyState(lobby.id, ConnectionState.CONNECTING)
                publishReconnect(session, waiting = false)
                ensureForeground(statusText())

                val openedAt = System.currentTimeMillis()
                val connected = connectOnce(session)
                val livedMs = System.currentTimeMillis() - openedAt
                session.webSocket.getAndSet(null)?.close(1000, "bye")
                if (!session.running.get()) break

                val stable = connected && livedMs >= STABLE_CONNECTION_MS
                if (stable) {
                    session.backoffMs = MIN_BACKOFF_MS
                    session.attempt = 0
                    clearReconnect(lobby.id)
                }

                updateLobbyState(lobby.id, ConnectionState.RECONNECTING)
                // Still reconnect — Notification bleibt ruhig (kein „wiederherstellen“)
                ensureForeground(statusText())

                val waitMs = if (stable) 800L else session.backoffMs
                session.nextRetryAtMs = System.currentTimeMillis() + waitMs
                if (!stable) {
                    session.attempt += 1
                    publishReconnect(session, waiting = true)
                }

                var forced = false
                var waited = 0L
                while (waited < waitMs && session.running.get()) {
                    if (session.forceReconnect.getAndSet(false)) {
                        session.backoffMs = MIN_BACKOFF_MS
                        session.attempt = 0
                        forced = true
                        break
                    }
                    delay(250)
                    waited += 250
                    if (!stable && waited % 1000L < 250L) publishReconnect(session, waiting = true)
                }

                if (!stable && !forced) {
                    session.backoffMs = (session.backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
            }
        } finally {
            session.running.set(false)
            clearReconnect(lobby.id)
        }
    }

    private suspend fun connectOnce(session: LobbySession): Boolean {
        val lobby = session.lobby
        val opened = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        val request = Request.Builder()
            .url(
                LuvApiClient.wsUrl(
                    lobby.code,
                    lobby.token,
                    lobby.role.name.lowercase(),
                    LuvApiClient.sessionToken
                )
            )
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.set(true)
                Log.i(TAG, "ws open lobby=${lobby.name}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(lobby, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                closed.set(true)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.set(true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "ws failure lobby=${lobby.name}", t)
                closed.set(true)
            }
        }

        val socket = wsClient.newWebSocket(request, listener)
        session.webSocket.set(socket)

        while (session.running.get() && !closed.get() && !session.forceReconnect.get()) {
            delay(300)
        }
        return opened.get()
    }

    private fun publishReconnect(session: LobbySession, waiting: Boolean = true) {
        val remaining = if (waiting) {
            ((session.nextRetryAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
        } else {
            0L
        }
        _reconnectUi.value = _reconnectUi.value + (session.lobby.id to LobbyReconnectUi(
            lobbyId = session.lobby.id,
            attempt = session.attempt,
            nextRetryInSec = remaining.toInt(),
            backoffSec = (session.backoffMs / 1000L).toInt(),
            waiting = waiting && remaining > 0
        ))
    }

    private fun clearReconnect(lobbyId: String) {
        _reconnectUi.value = _reconnectUi.value - lobbyId
    }

    private fun handleIncoming(lobby: Lobby, text: String) {
        runCatching {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "welcome", "peers" -> {
                    val peers = json.optInt("peers", json.optInt("count", 0))
                    PairSessionState.onPeers(lobby.id, peers)
                    if (peers >= 2) {
                        sessions[lobby.id]?.let {
                            it.backoffMs = MIN_BACKOFF_MS
                            it.attempt = 0
                        }
                        clearReconnect(lobby.id)
                    }
                    updateLobbyState(
                        lobby.id,
                        if (peers >= 2) ConnectionState.CONNECTED else ConnectionState.HOSTING
                    )
                    ensureForeground(statusText())
                    return
                }
                "clear_vote_open" -> {
                    AccountSession.emitClearVote(
                        ClearVoteEvent.Open(
                            lobbyId = lobby.id,
                            proposalId = json.getString("proposalId"),
                            by = json.optString("by", "Jemand"),
                            endsAt = json.optLong("endsAt"),
                            yes = json.optInt("yes", 1),
                            total = json.optInt("total", 1)
                        )
                    )
                    return
                }
                "clear_vote_update" -> {
                    AccountSession.emitClearVote(
                        ClearVoteEvent.Update(
                            lobbyId = lobby.id,
                            proposalId = json.getString("proposalId"),
                            yes = json.optInt("yes"),
                            no = json.optInt("no"),
                            total = json.optInt("total")
                        )
                    )
                    return
                }
                "clear_result" -> {
                    AccountSession.emitClearVote(
                        ClearVoteEvent.Result(
                            lobbyId = lobby.id,
                            proposalId = json.getString("proposalId"),
                            approved = json.optBoolean("approved"),
                            yes = json.optInt("yes"),
                            total = json.optInt("total")
                        )
                    )
                    return
                }
                "economy_block" -> {
                    AccountSession.emitEconomyBlock(
                        json.optString("message", "Keine Coins mehr — Zuschauen geht weiter.")
                    )
                    return
                }
                "economy_ok" -> {
                    val userJson = json.optJSONObject("user")
                    if (userJson != null) {
                        AccountSession.setAccount(
                            com.luv.couple.data.AccountInfo.fromApi(userJson)
                        )
                    }
                    return
                }
            }
        }

        when (val message = PairProtocol.decode(text)) {
            is PairMessage.StrokeMsg -> {
                CanvasStore.addRemoteStroke(message.stroke, lobby.id)
                PartnerStrokeNotifier.onPartnerStroke(
                    this,
                    lobbyName = lobby.name,
                    nickname = message.stroke.nickname ?: "Jemand",
                    lobbyId = lobby.id
                )
                scope.launch { _events.emit(PairEvent.StrokeReceived(lobby.id, message.stroke)) }
            }
            is PairMessage.UndoMsg -> {
                CanvasStore.removeStrokeById(message.strokeId, lobby.id)
                scope.launch { _events.emit(PairEvent.StrokeUndone(lobby.id, message.strokeId)) }
            }
            is PairMessage.Presence -> {
                val key = message.peerKey ?: message.nickname ?: "peer"
                PairSessionState.onPresence(
                    lobbyId = lobby.id,
                    peerKey = key,
                    nickname = message.nickname ?: "Jemand",
                    colorIndex = message.colorIndex,
                    active = message.active
                )
            }
            is PairMessage.Note -> PairSessionState.emitNote(message.text)
            is PairMessage.Recolor -> {
                CanvasStore.recolorByNickname(message.nickname, message.colorIndex, lobby.id)
                PairSessionState.updatePeerColor(lobby.id, message.nickname, message.colorIndex)
                scope.launch {
                    _events.emit(
                        PairEvent.RecolorReceived(lobby.id, message.nickname, message.colorIndex)
                    )
                }
            }
            is PairMessage.Reaction -> {
                scope.launch {
                    _events.emit(PairEvent.ReactionReceived(lobby.id, message.emoji, message.nickname))
                }
            }
            is PairMessage.GameBoard -> {
                scope.launch {
                    _events.emit(
                        PairEvent.GameBoardReceived(lobby.id, message.game, message.visible)
                    )
                }
            }
            PairMessage.Clear -> {
                CanvasStore.clear(localOnly = true, lobbyId = lobby.id)
                scope.launch { _events.emit(PairEvent.Cleared(lobby.id)) }
            }
            else -> Unit
        }
    }

    private fun sendRaw(lobbyId: String?, line: String) {
        val id = lobbyId ?: CanvasStore.activeLobbyId.value ?: return
        sessions[id]?.webSocket?.get()?.send(line)
    }

    private fun stopLobby(lobbyId: String) {
        sessions.remove(lobbyId)?.let { session ->
            session.running.set(false)
            session.job?.cancel()
            session.webSocket.getAndSet(null)?.close(1000, "bye")
            PairSessionState.resetLobby(lobbyId)
            _lobbyStates.updateAndRemove(lobbyId)
        }
        refreshAggregateState()
        if (sessions.isEmpty()) {
            stopSelfSafely()
        } else {
            ensureForeground(statusText())
        }
    }

    private fun stopAll() {
        sessions.keys.toList().forEach { stopLobby(it) }
        PairSessionState.reset()
        updateAggregate(ConnectionState.IDLE)
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        foregroundStarted.set(false)
        stopSelf()
    }

    private fun stopSelfSafely() {
        updateAggregate(ConnectionState.IDLE)
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        foregroundStarted.set(false)
        stopSelf()
    }

    private fun updateLobbyState(lobbyId: String, state: ConnectionState) {
        _lobbyStates.value = _lobbyStates.value + (lobbyId to state)
        refreshAggregateState()
    }

    private fun refreshAggregateState() {
        val states = _lobbyStates.value.values
        val aggregate = when {
            states.any { it == ConnectionState.CONNECTED } -> ConnectionState.CONNECTED
            states.any { it == ConnectionState.HOSTING } -> ConnectionState.HOSTING
            states.any { it == ConnectionState.RECONNECTING } -> ConnectionState.RECONNECTING
            states.any { it == ConnectionState.CONNECTING } -> ConnectionState.CONNECTING
            else -> ConnectionState.IDLE
        }
        updateAggregate(aggregate)
    }

    private fun statusText(): String {
        // Keine Reconnect-/Stör-Meldungen in der System-Notification
        val connected = _lobbyStates.value.count { it.value == ConnectionState.CONNECTED }
        val hosting = _lobbyStates.value.count { it.value == ConnectionState.HOSTING }
        return when {
            connected > 0 -> getString(R.string.notification_title)
            hosting > 0 -> getString(R.string.notification_hosting)
            else -> getString(R.string.notification_running)
        }
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
        sessions.values.forEach {
            it.running.set(false)
            it.job?.cancel()
            it.webSocket.getAndSet(null)?.close(1000, "bye")
        }
        sessions.clear()
        scope.cancel()
        updateAggregate(ConnectionState.IDLE)
        foregroundStarted.set(false)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LuvPairService"
        const val ACTION_START = "com.luv.couple.ACTION_START"
        const val ACTION_START_ALL = "com.luv.couple.ACTION_START_ALL"
        const val ACTION_STOP = "com.luv.couple.ACTION_STOP"
        const val ACTION_SEND_STROKE = "com.luv.couple.ACTION_SEND_STROKE"
        const val ACTION_SEND_CLEAR = "com.luv.couple.ACTION_SEND_CLEAR"
        const val ACTION_SEND_UNDO = "com.luv.couple.ACTION_SEND_UNDO"
        const val ACTION_RECONNECT = "com.luv.couple.ACTION_RECONNECT"
        const val EXTRA_PAYLOAD = "payload"
        const val EXTRA_STROKE_ID = "stroke_id"
        const val EXTRA_LOBBY_ID = "lobby_id"
        private const val NOTIFICATION_ID = 42
        private const val MIN_BACKOFF_MS = 5_000L
        private const val MAX_BACKOFF_MS = 120_000L
        private const val STABLE_CONNECTION_MS = 8_000L

        private val _state = MutableStateFlow(ConnectionState.IDLE)
        val state: StateFlow<ConnectionState> = _state.asStateFlow()

        private val _lobbyStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
        val lobbyStates: StateFlow<Map<String, ConnectionState>> = _lobbyStates.asStateFlow()

        private val _reconnectUi = MutableStateFlow<Map<String, LobbyReconnectUi>>(emptyMap())
        val reconnectUi: StateFlow<Map<String, LobbyReconnectUi>> = _reconnectUi.asStateFlow()

        private val _events = MutableSharedFlow<PairEvent>(extraBufferCapacity = 64)
        val events: SharedFlow<PairEvent> = _events.asSharedFlow()

        private fun updateAggregate(value: ConnectionState) {
            _state.value = value
        }

        private fun MutableStateFlow<Map<String, ConnectionState>>.updateAndRemove(lobbyId: String) {
            value = value - lobbyId
        }

        fun startAll(context: Context): Boolean {
            return try {
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_START_ALL)
                context.startForegroundService(intent)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to start pair service", t)
                false
            }
        }

        /** @deprecated use startAll */
        fun start(
            context: Context,
            code: String? = null,
            token: String? = null,
            role: com.luv.couple.data.Role? = null
        ): Boolean = startAll(context)

        fun stop(context: Context, lobbyId: String? = null) {
            try {
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to stop pair service", t)
            }
        }

        fun sendStroke(context: Context, strokeJson: String, lobbyId: String? = null) {
            try {
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_STROKE)
                    .putExtra(EXTRA_PAYLOAD, strokeJson)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to send stroke", t)
            }
        }

        fun sendClear(context: Context, lobbyId: String? = null) {
            // Clear = Abstimmung starten (Server)
            try {
                val nickname = CanvasStore.cachedNickname
                    ?: AccountSession.account.value?.nickname
                val payload = PairProtocol.encode(PairMessage.ClearPropose(nickname))
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_STROKE)
                    .putExtra(EXTRA_PAYLOAD, payload)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to propose clear", t)
            }
        }

        fun sendClearVote(context: Context, proposalId: String, yes: Boolean, lobbyId: String? = null) {
            try {
                val payload = PairProtocol.encode(PairMessage.ClearVote(proposalId, yes))
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_STROKE)
                    .putExtra(EXTRA_PAYLOAD, payload)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to send clear vote", t)
            }
        }

        fun sendUndo(context: Context, strokeId: String, lobbyId: String? = null) {
            try {
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_UNDO)
                    .putExtra(EXTRA_STROKE_ID, strokeId)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to send undo", t)
            }
        }

        fun sendPresence(context: Context, active: Boolean, lobbyId: String? = null) {
            try {
                val nickname = CanvasStore.cachedNickname
                    ?: AccountSession.account.value?.nickname
                val colorIndex = CanvasStore.cachedColorIndex
                val payload = PairProtocol.encode(
                    PairMessage.Presence(
                        active = active,
                        nickname = nickname,
                        colorIndex = colorIndex,
                        peerKey = nickname
                    )
                )
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_STROKE)
                    .putExtra(EXTRA_PAYLOAD, payload)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId ?: CanvasStore.activeLobbyId.value)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to send presence", t)
            }
        }

        fun sendNote(context: Context, text: String, lobbyId: String? = null) {
            try {
                val payload = PairProtocol.encode(PairMessage.Note(text))
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_STROKE)
                    .putExtra(EXTRA_PAYLOAD, payload)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId ?: CanvasStore.activeLobbyId.value)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to send note", t)
            }
        }

        fun sendRecolor(context: Context, nickname: String?, colorIndex: Int, lobbyId: String? = null) {
            try {
                val payload = PairProtocol.encode(PairMessage.Recolor(nickname, colorIndex))
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_STROKE)
                    .putExtra(EXTRA_PAYLOAD, payload)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId ?: CanvasStore.activeLobbyId.value)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to send recolor", t)
            }
        }

        fun sendReaction(context: Context, emoji: String, lobbyId: String? = null) {
            try {
                val nickname = CanvasStore.cachedNickname
                    ?: AccountSession.account.value?.nickname
                val payload = PairProtocol.encode(PairMessage.Reaction(emoji, nickname))
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_STROKE)
                    .putExtra(EXTRA_PAYLOAD, payload)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId ?: CanvasStore.activeLobbyId.value)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to send reaction", t)
            }
        }

        fun sendGameBoard(
            context: Context,
            game: String,
            visible: Boolean,
            lobbyId: String? = null
        ) {
            try {
                val payload = PairProtocol.encode(PairMessage.GameBoard(game, visible))
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_STROKE)
                    .putExtra(EXTRA_PAYLOAD, payload)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId ?: CanvasStore.activeLobbyId.value)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to send game board", t)
            }
        }

        fun lobbyState(lobbyId: String): ConnectionState =
            _lobbyStates.value[lobbyId] ?: ConnectionState.IDLE

        fun reconnectNow(context: Context, lobbyId: String? = null) {
            try {
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_RECONNECT)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to force reconnect", t)
            }
        }
    }
}

data class LobbyReconnectUi(
    val lobbyId: String,
    val attempt: Int,
    val nextRetryInSec: Int,
    val backoffSec: Int,
    val waiting: Boolean
)

sealed class PairEvent {
    data class StrokeReceived(val lobbyId: String, val stroke: com.luv.couple.data.Stroke) : PairEvent()
    data class StrokeUndone(val lobbyId: String, val strokeId: String) : PairEvent()
    data class Cleared(val lobbyId: String) : PairEvent()
    data class RecolorReceived(val lobbyId: String, val nickname: String?, val colorIndex: Int) : PairEvent()
    data class ReactionReceived(val lobbyId: String, val emoji: String, val nickname: String?) : PairEvent()
    data class GameBoardReceived(val lobbyId: String, val game: String, val visible: Boolean) : PairEvent()
}
