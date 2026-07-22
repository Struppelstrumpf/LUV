package com.luv.couple.net

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.luv.couple.LuvApp
import com.luv.couple.MainActivity
import com.luv.couple.R
import com.luv.couple.data.ConnectionState
import com.luv.couple.data.Lobby
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.Role
import com.luv.couple.data.RosterMember
import com.luv.couple.data.Stroke
import com.luv.couple.data.StrokePoint
import com.luv.couple.lock.CanvasStore
import com.luv.couple.lock.LockDrawActivity
import com.luv.couple.lock.LockScreenWidgetProvider
import com.luv.couple.notify.LuvAlertNotifier
import com.luv.couple.notify.MoodLines
import androidx.core.app.NotificationManagerCompat
// LiveNotice / LiveNoticeBus same package
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class PairConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val foregroundStarted = AtomicBoolean(false)
    @Volatile private var lastServiceMoodAtMs = 0L
    @Volatile private var lastServiceMoodLine: String = ""
    private val sessions = ConcurrentHashMap<String, LobbySession>()

    init {
        instanceRef = this
    }

    // Default maxRequestsPerHost=5 → bei vielen Lobbys bleiben WS hängen
    // (nur 2er-Lobbys „verbunden“, größere nur „Du“ + Plus).
    private val wsClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 32
            }
        )
        // HTTP/2 + Proxy zerlegt WS-Frames (MASK) → alle sehen nur sich selbst
        .protocols(listOf(Protocol.HTTP_1_1))
        // Kein Client-Ping — sonst kaputte Frames hinter Caddy
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private data class LobbySession(
        @Volatile var lobby: Lobby,
        val running: AtomicBoolean = AtomicBoolean(true),
        val webSocket: AtomicReference<WebSocket?> = AtomicReference(null),
        val outbox: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue(),
        var job: Job? = null,
        val forceReconnect: AtomicBoolean = AtomicBoolean(false),
        @Volatile var backoffMs: Long = MIN_BACKOFF_MS,
        @Volatile var attempt: Int = 0,
        @Volatile var nextRetryAtMs: Long = 0L,
        /** Lobby auf dem Server weg (z. B. nach API-Neustart) — nicht endlos reconnecten */
        @Volatile var lobbyGone: Boolean = false,
        /** true nur nach bewusstem Verlassen — 4001 sonst nicht Session killen */
        @Volatile var intentionalLeave: Boolean = false,
        /** Token/Roster einmalig geholt — nicht bei jedem Reconnect hämmern */
        @Volatile var accessHealed: Boolean = false
    )

    private data class ConnectResult(
        val opened: Boolean,
        val closeCode: Int = 0,
        val closeReason: String = ""
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()

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
                if (payload != null) enqueueOrSend(lobbyId, payload)
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
            session.lobbyGone = false
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
        // Ein Session pro Lobby-Code — Doppel-Einträge erzeugen sonst 4002-Flapping
        val byCode = linkedMapOf<String, Lobby>()
        lobbies.forEach { lobby ->
            val key = lobby.code.trim().uppercase()
            if (key.isBlank()) return@forEach
            val prev = byCode[key]
            if (prev == null || (lobby.role == Role.HOST && prev.role != Role.HOST)) {
                byCode[key] = lobby
            }
        }
        val unique = byCode.values.toList()
        CanvasStore.updateKnownLobbies(unique.map { it.id })
        val wanted = unique.associateBy { it.id }
        sessions.keys.filter { it !in wanted }.forEach { stopLobby(it) }
        // Alte Sessions mit gleichem Code aber anderer ID stoppen
        val wantedCodes = unique.map { it.code.trim().uppercase() }.toSet()
        sessions.values
            .filter { it.lobby.code.trim().uppercase() in wantedCodes && it.lobby.id !in wanted }
            .map { it.lobby.id }
            .forEach { stopLobby(it) }
        // Parallel — kein Staffeln; Token liegt lokal, WS kann sofort auf
        val myNick = CanvasStore.cachedNickname
            ?: AccountSession.account.value?.nickname
        unique.forEach { lobby ->
            PairSessionState.seedHomePreview(
                lobbyId = lobby.id,
                hostNickname = lobby.hostNickname,
                myNickname = myNick,
                capacity = lobby.capacity
            )
            scope.launch { ensureLobbySession(lobby) }
        }
        refreshAggregateState()
    }

    private fun ensureLobbySession(lobby: Lobby) {
        val codeKey = lobby.code.trim().uppercase()
        // Bereits eine Session für diesen Code? Nicht zweite aufmachen.
        val sameCode = sessions.values.firstOrNull {
            it.lobby.code.trim().uppercase() == codeKey && it.lobby.id != lobby.id
        }
        if (sameCode != null) {
            val oldId = sameCode.lobby.id
            if (oldId != lobby.id) {
                PairSessionState.migrateLobbyId(oldId, lobby.id)
                val prevState = _lobbyStates.value[oldId]
                sameCode.lobby = lobby
                sessions.remove(oldId)
                sessions[lobby.id] = sameCode
                if (prevState != null) {
                    _lobbyStates.value = _lobbyStates.value - oldId + (lobby.id to prevState)
                }
            } else {
                sameCode.lobby = lobby
            }
            if (sameCode.job?.isActive == true) return
            sameCode.running.set(true)
            sameCode.job = scope.launch { runLobbyLoop(sameCode) }
            return
        }
        val existing = sessions[lobby.id]
        if (existing != null) {
            // Prefs-Sync (z. B. neuer Name vom Partner)
            existing.lobby = lobby
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
        try {
            while (scope.isActive && session.running.get() && !session.lobbyGone) {
                val lobby = session.lobby
                session.forceReconnect.set(false)
                // Nach kurzer Trennung nicht hart auf CONNECTING springen (UI-Flackern)
                val prev = _lobbyStates.value[lobby.id]
                val softRetry =
                    prev == ConnectionState.CONNECTED ||
                        prev == ConnectionState.HOSTING ||
                        prev == ConnectionState.RECONNECTING
                updateLobbyState(
                    lobby.id,
                    if (softRetry) ConnectionState.RECONNECTING else ConnectionState.CONNECTING
                )
                if (!softRetry) clearReconnect(lobby.id)
                ensureForeground()

                val openedAt = System.currentTimeMillis()
                val result = connectOnce(session)
                val livedMs = System.currentTimeMillis() - openedAt
                if (!session.running.get()) break

                // Bewusst verlassen — nur mit Flag (sonst Reconnect nach Server-Close)
                if (
                    session.intentionalLeave &&
                    (result.closeCode == 4001 ||
                        result.closeReason.equals("left", ignoreCase = true))
                ) {
                    Log.i(TAG, "ws left lobby=${lobby.code} — stop session")
                    session.running.set(false)
                    break
                }
                // Durch Reconnect ersetzt — Loop verbindet gleich neu
                if (result.closeCode == 4002 ||
                    result.closeReason.equals("replaced", ignoreCase = true)
                ) {
                    delay(500L)
                    continue
                }
                // Nach Server-Update / Token-Drift: Token heilen, dann reconnecten.
                if (result.closeCode == 4401) {
                    Log.w(TAG, "ws 4401 lobby=${lobby.code} — refresh token & retry")
                    session.attempt += 1
                    session.accessHealed = false
                    updateLobbyState(lobby.id, ConnectionState.RECONNECTING)
                    publishReconnect(session, waiting = true)
                    runCatching { refreshLobbyAccess(session) }
                    session.accessHealed = true
                    delay(1200L)
                    continue
                }
                // Lobby voll — nicht alle 1–2s hämmern (sonst „verbunden → weg“-Flackern)
                if (result.closeCode == 4409) {
                    Log.w(TAG, "ws 4409 room_full lobby=${lobby.code} — backoff retry")
                    session.attempt += 1
                    session.accessHealed = false
                    updateLobbyState(lobby.id, ConnectionState.RECONNECTING)
                    publishReconnect(session, waiting = true)
                    runCatching { refreshLobbyAccess(session) }
                    session.accessHealed = true
                    delay((4_000L + session.attempt * 2_000L).coerceAtMost(20_000L))
                    continue
                }

                val stable = result.opened && livedMs >= STABLE_CONNECTION_MS
                if (stable) {
                    session.attempt = 0
                    clearReconnect(lobby.id)
                } else {
                    session.attempt += 1
                }

                // Tote Mal-Lobby nur löschen wenn API erreichbar und Raum weg
                // (Offline → behalten, Sync stellt sie wieder her falls Server sie noch hat)
                if (!stable && shouldDropDeadLobby(result, lobby)) {
                    Log.i(TAG, "dead lobby code=${lobby.code} — drop local (API ok, room gone)")
                    session.lobbyGone = true
                    session.running.set(false)
                    runCatching {
                        (application as LuvApp).prefs.removeLobby(lobby.id)
                    }
                    sessions.remove(lobby.id)
                    PairSessionState.resetLobby(lobby.id)
                    _lobbyStates.updateAndRemove(lobby.id)
                    clearReconnect(lobby.id)
                    refreshAggregateState()
                    break
                }

                updateLobbyState(lobby.id, ConnectionState.RECONNECTING)
                ensureForeground()

                val waitMs = if (stable) 800L else RECONNECT_DELAY_MS
                session.nextRetryAtMs = System.currentTimeMillis() + waitMs
                if (!stable) {
                    publishReconnect(session, waiting = true)
                }

                var waited = 0L
                while (waited < waitMs && session.running.get()) {
                    if (session.forceReconnect.getAndSet(false)) {
                        session.attempt = 0
                        break
                    }
                    delay(250)
                    waited += 250
                    if (!stable && waited % 1000L < 250L) publishReconnect(session, waiting = true)
                }
            }
        } finally {
            session.running.set(false)
            clearReconnect(session.lobby.id)
        }
    }

    /**
     * Geisterkarte „Verbinde…“:
     * - App/API offline → nicht löschen (nur Verbindungsproblem)
     * - API erreichbar + Raum weg → lokal entfernen
     * - API erreichbar + Raum da → behalten (Reconnect)
     */
    private suspend fun shouldDropDeadLobby(result: ConnectResult, lobby: Lobby): Boolean {
        // Zeremonie / Custom-Räume: gleiches Ghost-Verhalten
        if (result.opened) return false
        val attempt = sessions[lobby.id]?.attempt ?: 0
        if (attempt < 2) return false

        // Zuerst: hat die App unabhängig von dieser Lobby Server-Kontakt?
        if (!apiReachableIndependently()) {
            Log.i(TAG, "keep lobby ${lobby.code} — API nicht erreichbar (offline?)")
            return false
        }

        val reason = result.closeReason.lowercase()
        if (reason.contains("wedding_ended") && lobby.isWedding) {
            return true
        }
        // room_not_found / unauthorized nach erfolgreichem API-Check
        if (!roomStillExists(lobby.code)) return true
        if (result.closeCode == 4401 || reason.contains("unauthorized")) {
            // Token tot, Raum aber da → nicht droppen (Token-Heal läuft separat)
            return false
        }
        return false
    }

    /** Heartbeat / Auth-Config — unabhängig vom Lobby-WebSocket. */
    private suspend fun apiReachableIndependently(): Boolean {
        return runCatching {
            if (!LuvApiClient.sessionToken.isNullOrBlank()) {
                LuvApiClient.heartbeat()
            } else {
                LuvApiClient.authConfig()
                true
            }
        }.getOrDefault(false)
    }

    private suspend fun roomStillExists(code: String): Boolean {
        return try {
            LuvApiClient.roomPreview(code)
            true
        } catch (e: LuvApiException) {
            val missing = e.error == "room_not_found" ||
                e.message?.contains("nicht gefunden", ignoreCase = true) == true
            !missing
        } catch (_: Exception) {
            // Netzwerkfehler beim Preview → als „existiert noch“ behandeln
            true
        }
    }

    private suspend fun connectOnce(session: LobbySession): ConnectResult {
        if (!session.accessHealed) {
            // Schnellpfad wie früher: lokaler Token → WS sofort.
            // ensure/join nur wenn Token fehlt; bei 4401 heilt der Loop nach.
            if (session.lobby.token.isBlank()) {
                runCatching { refreshLobbyAccess(session) }
            }
            session.accessHealed = true
        }
        val lobbyNow = session.lobby
        val opened = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        val closeCode = AtomicReference(0)
        val closeReason = AtomicReference("")
        val request = Request.Builder()
            .url(
                LuvApiClient.wsUrl(
                    lobbyNow.code,
                    lobbyNow.token,
                    lobbyNow.role.name.lowercase(),
                    LuvApiClient.sessionToken
                )
            )
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.set(true)
                Log.i(TAG, "ws open lobby=${lobbyNow.name}")
                // Noch nicht CONNECTED — erst nach welcome (sonst Flackern bei room_full)
                session.lobbyGone = false
                updateLobbyState(lobbyNow.id, ConnectionState.CONNECTING)
                ensureForeground()
                flushOutbox(session, webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(session.lobby, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                closeCode.set(code)
                closeReason.set(reason.orEmpty())
                closed.set(true)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closeCode.set(code)
                closeReason.set(reason.orEmpty())
                closed.set(true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "ws failure lobby=${lobbyNow.name}", t)
                closed.set(true)
            }
        }

        // Alte Socket sauber weg, bevor eine neue aufgeht
        session.webSocket.getAndSet(null)?.let { old ->
            try {
                old.cancel()
            } catch (_: Throwable) {
            }
        }
        val socket = wsClient.newWebSocket(request, listener)
        session.webSocket.set(socket)

        var lastPingAt = 0L
        while (session.running.get() && !closed.get() && !session.forceReconnect.get()) {
            val now = System.currentTimeMillis()
            if (opened.get() && now - lastPingAt >= WS_PING_INTERVAL_MS) {
                lastPingAt = now
                runCatching {
                    socket.send(PairProtocol.encode(PairMessage.Ping))
                }
            }
            delay(300)
        }
        // Nur canceln wenn wir selbst reconnecten — nicht doppelt closen nach Server-Close
        if (session.forceReconnect.get() || session.running.get()) {
            val current = session.webSocket.get()
            if (current === socket && closed.get()) {
                session.webSocket.compareAndSet(socket, null)
            } else if (current === socket && session.forceReconnect.get()) {
                try {
                    socket.cancel()
                } catch (_: Throwable) {
                }
                session.webSocket.compareAndSet(socket, null)
            }
        }
        return ConnectResult(
            opened = opened.get(),
            closeCode = closeCode.get() ?: 0,
            closeReason = closeReason.get().orEmpty()
        )
    }

    private fun publishReconnect(session: LobbySession, waiting: Boolean = true) {
        if (!waiting) {
            clearReconnect(session.lobby.id)
            return
        }
        val remaining =
            ((session.nextRetryAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
        if (remaining <= 0) {
            clearReconnect(session.lobby.id)
            return
        }
        _reconnectUi.value = _reconnectUi.value + (session.lobby.id to LobbyReconnectUi(
            lobbyId = session.lobby.id,
            attempt = session.attempt,
            nextRetryInSec = remaining.toInt(),
            backoffSec = (RECONNECT_DELAY_MS / 1000L).toInt(),
            waiting = true
        ))
    }

    private fun clearReconnect(lobbyId: String) {
        _reconnectUi.value = _reconnectUi.value - lobbyId
    }

    private fun handleIncoming(lobby: Lobby, text: String) {
        runCatching {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "ping" -> {
                    sessions[lobby.id]?.webSocket?.get()?.let { ws ->
                        runCatching { ws.send(PairProtocol.encode(PairMessage.Pong)) }
                    }
                    return
                }
                "pong" -> return
                "canvas_taken" -> {
                    // Nur wenn diese Leinwand wirklich im Vordergrund offen ist
                    if (!LockDrawActivity.isCanvasForeground(lobby.id)) return
                    val message = json.optString("message").trim()
                        .ifBlank { "Ein anderes Gerät hat die Leinwand betreten." }
                    scope.launch {
                        _events.emit(PairEvent.CanvasTaken(lobby.id, message))
                    }
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                    }
                    return
                }
                "live_notice" -> {
                    val id = json.optString("id").trim()
                    val message = json.optString("message").trim()
                    if (id.isNotBlank() && message.isNotBlank()) {
                        val kind = json.optString("kind", "team").trim().ifBlank { "team" }
                        val rawAuthor = json.optString("authorNickname").trim()
                        val author = when {
                            kind.equals("wedding", ignoreCase = true) ->
                                rawAuthor.ifBlank { "LUV" }
                            rawAuthor.isNotBlank() &&
                                !rawAuthor.equals("Luv", ignoreCase = true) -> rawAuthor
                            else -> "Team"
                        }
                        LiveNoticeBus.offer(
                            LiveNotice(
                                id = id,
                                message = message,
                                authorNickname = author,
                                createdAt = json.optLong(
                                    "createdAt",
                                    System.currentTimeMillis()
                                ),
                                kind = kind,
                                subtitle = json.optString("subtitle").trim()
                                    .takeIf { it.isNotBlank() && it != "null" },
                                targetUserId = json.optString("targetUserId").trim()
                                    .takeIf { it.isNotBlank() && it != "null" },
                            )
                        )
                    }
                }
                "wedding_confirm" -> {
                    val confirms = json.optJSONObject("confirms")
                    val map = mutableMapOf<String, Boolean>()
                    if (confirms != null) {
                        val keys = confirms.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            map[k] = confirms.optBoolean(k, false)
                        }
                    }
                    scope.launch {
                        _events.emit(
                            PairEvent.WeddingConfirm(
                                lobbyId = lobby.id,
                                confirms = map,
                                fromUserId = json.optString("userId").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                    return
                }
                "welcome", "peers" -> {
                    val peers = json.optInt("peers", json.optInt("count", 0))
                    val capacity = json.optInt("capacity", 0).takeIf { it > 0 }
                    val roster = parseRosterMembers(json)
                    if (roster.isNotEmpty()) {
                        PairSessionState.onRoster(lobby.id, roster, peers, capacity)
                    } else {
                        PairSessionState.onPeers(lobby.id, peers, capacity)
                    }
                    if (json.optString("type") == "welcome") {
                        sessions[lobby.id]?.attempt = 0
                        clearReconnect(lobby.id)
                        updateLobbyState(lobby.id, connectedStateFor(lobby))
                        ensureForeground()
                    }
                    val peakHint = json.optInt("peakPeers", peers).coerceAtLeast(peers)
                    if (peakHint > 0) {
                        scope.launch {
                            LuvApp.instance.prefs.bumpPeakPeers(lobby.id, peakHint)
                        }
                    }
                    if (json.optString("type") == "welcome") {
                        val serverName = json.optString("name").trim()
                        if (serverName.isNotBlank()) {
                            applyLobbyName(lobby.id, serverName)
                        }
                        val serverToken = json.optString("token").trim()
                            .takeIf { it.isNotBlank() && it != "null" }
                        if (!serverToken.isNullOrBlank()) {
                            applyLobbyToken(lobby.id, serverToken)
                        }
                        val hostNick = json.optString("hostNickname").trim()
                        val hostUserId = json.optString("hostUserId").trim()
                            .takeIf { it.isNotBlank() && it != "null" }
                        if (hostNick.isNotBlank() || hostUserId != null) {
                            scope.launch {
                                LuvApp.instance.prefs.updateLobbyHost(
                                    lobby.id,
                                    hostNick.ifBlank { lobby.hostNickname },
                                    hostUserId
                                )
                            }
                            val myId = AccountSession.account.value?.id
                                ?: json.optString("userId").trim().takeIf { it.isNotBlank() && it != "null" }
                            val amHost = hostUserId != null && myId != null && hostUserId == myId
                            sessions[lobby.id]?.let { s ->
                                s.lobby = s.lobby.copy(
                                    hostNickname = hostNick.ifBlank { s.lobby.hostNickname },
                                    role = when {
                                        amHost -> Role.HOST
                                        // Ohne eigene User-Id nie HOST→JOIN (Race beim Start)
                                        myId == null -> s.lobby.role
                                        s.lobby.role == Role.HOST && hostUserId != null && hostUserId != myId ->
                                            Role.JOIN
                                        else -> s.lobby.role
                                    }
                                )
                            }
                        }
                        if (json.has("suggestedColorIndex")) {
                            val suggested = json.optInt("suggestedColorIndex", -1)
                            val activeId = CanvasStore.activeLobbyId.value
                            val appliesHere = activeId == null || activeId == lobby.id
                            if (suggested in 0 until PeerPalette.COLOR_COUNT) {
                                scope.launch {
                                    val prefs = LuvApp.instance.prefs
                                    val lobbyKey = lobby.code.takeIf { it.isNotBlank() } ?: lobby.id
                                    val saved = prefs.colorIndexForLobby(lobbyKey)
                                    // Lokale Lobby-Farbe hat Vorrang; Server nur beim ersten Mal
                                    val useColor = saved ?: suggested
                                    if (saved == null) {
                                        prefs.setColorIndexForLobby(lobbyKey, suggested)
                                    }
                                    if (appliesHere) {
                                        val colorChanged = CanvasStore.cachedColorIndex != useColor
                                        if (colorChanged) {
                                            CanvasStore.updateProfile(
                                                CanvasStore.cachedNickname,
                                                useColor
                                            )
                                            CanvasStore.recolorOwnStrokes(
                                                useColor,
                                                lobby.id,
                                                broadcast = LockDrawActivity.isCanvasForeground(lobby.id)
                                            )
                                        } else if (saved != null && saved != suggested) {
                                            // Server hat andere Farbe — lokale Präferenz zurückspielen
                                            sendRecolor(
                                                applicationContext,
                                                CanvasStore.cachedNickname,
                                                saved,
                                                lobby.id
                                            )
                                        }
                                        // Immer UI syncen (Live-Pinsel) — SharedFlow ohne Replay
                                        // würde ColorAssigned sonst verlieren, wenn die Canvas
                                        // noch nicht subscribed.
                                        _events.emit(PairEvent.ColorAssigned(lobby.id, useColor))
                                    }
                                    // Nur echte Leinwand-Präsenz — nicht schon bei Lobby-Verbindung
                                    sendPresence(
                                        applicationContext,
                                        active = LockDrawActivity.isCanvasForeground(lobby.id),
                                        lobbyId = lobby.id
                                    )
                                }
                            }
                        }
                    }
                    // CONNECTED nur nach welcome (peers allein reicht nicht / room_full-Flackern)
                    if (json.optString("type") == "welcome") {
                        sessions[lobby.id]?.let {
                            it.backoffMs = RECONNECT_DELAY_MS
                            it.attempt = 0
                        }
                        clearReconnect(lobby.id)
                        updateLobbyState(lobby.id, connectedStateFor(lobby))
                        ensureForeground()
                    } else if (capacity != null) {
                        PairSessionState.setCapacity(lobby.id, capacity)
                    }
                    return
                }
                "lobby_rename" -> {
                    val serverName = json.optString("name").trim()
                    if (serverName.isNotBlank()) {
                        applyLobbyName(lobby.id, serverName)
                    }
                    return
                }
                "host_changed" -> {
                    val hostNick = json.optString("hostNickname").trim()
                    val hostUserId = json.optString("hostUserId").trim()
                        .takeIf { it.isNotBlank() && it != "null" }
                    val myId = AccountSession.account.value?.id
                    val session = sessions[lobby.id]
                    if (session != null) {
                        val amHost = hostUserId != null && myId != null && hostUserId == myId
                        session.lobby = session.lobby.copy(
                            hostNickname = hostNick.ifBlank { session.lobby.hostNickname },
                            role = when {
                                amHost -> Role.HOST
                                myId == null -> session.lobby.role
                                session.lobby.role == Role.HOST && hostUserId != null && hostUserId != myId ->
                                    Role.JOIN
                                else -> session.lobby.role
                            }
                        )
                    }
                    scope.launch {
                        LuvApp.instance.prefs.updateLobbyHost(lobby.id, hostNick, hostUserId)
                        LockScreenWidgetProvider.requestUpdate(applicationContext)
                    }
                    return
                }
                "canvas_history" -> {
                    val replace = json.optBoolean("replace", false)
                    val done = json.optBoolean("done", true)
                    val arr = json.optJSONArray("strokes")
                    val strokes = buildList {
                        if (arr == null) return@buildList
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val encoded = o.put("type", "stroke").toString()
                            val msg = PairProtocol.decode(encoded) as? PairMessage.StrokeMsg ?: continue
                            add(msg.stroke)
                        }
                    }
                    // Legacy stickers[] → Emoji-Striche (gleiche History wie Linien)
                    val stickersArr = json.optJSONArray("stickers")
                    val legacyEmoji = if (stickersArr != null) {
                        buildList {
                            for (i in 0 until stickersArr.length()) {
                                val o = stickersArr.optJSONObject(i) ?: continue
                                val sid = o.optString("id").trim()
                                val rawEmoji = o.optString("emoji").trim()
                                val emoji = if (rawEmoji.startsWith("img_", ignoreCase = true)) {
                                    rawEmoji.take(32)
                                } else {
                                    rawEmoji.take(16)
                                }
                                if (sid.isBlank() || emoji.isBlank()) continue
                                add(
                                    Stroke(
                                        id = sid,
                                        points = listOf(
                                            StrokePoint(
                                                x = o.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                                                y = o.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f)
                                            )
                                        ),
                                        width = 0f,
                                        isLocal = false,
                                        nickname = o.optString("nickname")
                                            .takeIf { it.isNotBlank() && it != "null" },
                                        emoji = emoji
                                    )
                                )
                            }
                        }
                    } else {
                        emptyList()
                    }
                    CanvasStore.applyServerHistory(
                        lobbyId = lobby.id,
                        incoming = strokes + legacyEmoji,
                        replace = replace,
                        done = done
                    )
                    if (done) {
                        scope.launch {
                            _events.emit(PairEvent.HistoryApplied(lobby.id))
                        }
                    }
                    return
                }
                "peer_joined" -> {
                    val nick = json.optString("nickname").ifBlank { "Jemand" }
                    val joinUserId = json.optString("userId").takeIf { it.isNotBlank() && it != "null" }
                    PairSessionState.rememberPeer(lobby.id, nick, userId = joinUserId)
                    LuvAlertNotifier.onPeerJoined(
                        this,
                        lobbyName = lobby.name,
                        nickname = nick,
                        lobbyId = lobby.id,
                        userId = joinUserId,
                        firstJoin = json.optBoolean("firstJoin", true)
                    )
                    val roster = parseRosterMembers(json)
                    val peers = json.optInt("peers", json.optInt("count", 0))
                    val capacity = json.optInt("capacity", 0).takeIf { it > 0 }
                    if (roster.isNotEmpty()) {
                        PairSessionState.onRoster(
                            lobby.id,
                            roster,
                            peers.coerceAtLeast(roster.size),
                            capacity
                        )
                    } else if (peers > 0) {
                        PairSessionState.onPeers(lobby.id, peers, capacity)
                    }
                    return
                }
                "peer_left" -> {
                    val leftUserId = json.optString("userId").takeIf { it.isNotBlank() && it != "null" }
                    val leftNick = json.optString("nickname").trim().takeIf { it.isNotBlank() }
                    PairSessionState.removePeer(lobby.id, leftUserId, leftNick)
                    val roster = parseRosterMembers(json)
                    val peers = json.optInt("peers", json.optInt("count", 0))
                    val capacity = json.optInt("capacity", 0).takeIf { it > 0 }
                    if (roster.isNotEmpty()) {
                        PairSessionState.onRoster(lobby.id, roster, peers, capacity)
                    } else {
                        PairSessionState.onPeers(lobby.id, peers, capacity)
                    }
                    return
                }
                "clear_vote_open" -> {
                    val isInitiator = json.optBoolean("isInitiator", false)
                    val alreadyVoted = json.optBoolean("alreadyVoted", false)
                    val by = json.optString("by", "Jemand")
                    AccountSession.emitClearVote(
                        ClearVoteEvent.Open(
                            lobbyId = lobby.id,
                            proposalId = json.getString("proposalId"),
                            by = by,
                            byPeerId = json.optString("byPeerId").takeIf { it.isNotBlank() },
                            endsAt = json.optLong("endsAt"),
                            yes = json.optInt("yes", 1),
                            total = json.optInt("total", 1),
                            alreadyVoted = alreadyVoted,
                            isInitiator = isInitiator
                        )
                    )
                    if (!isInitiator && !alreadyVoted) {
                        LuvAlertNotifier.onClearAsk(
                            this,
                            lobbyName = lobby.name,
                            nickname = by,
                            lobbyId = lobby.id
                        )
                    }
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
                "clear_blocked", "clear_busy" -> {
                    AccountSession.emitEconomyBlock(
                        json.optString(
                            "message",
                            if (json.optString("type") == "clear_busy") {
                                "Lösch-Abstimmung läuft schon."
                            } else {
                                "Löschen nicht möglich."
                            }
                        )
                    )
                    return
                }
                "public_vote_open" -> {
                    val isInitiator = json.optBoolean("isInitiator", false)
                    val alreadyVoted = json.optBoolean("alreadyVoted", false)
                    val by = json.optString("by", "Jemand")
                    val onCanvas = LockDrawActivity.isCanvasForeground(lobby.id)
                    // Nur fragen, wer wirklich in der Leinwand ist — Lobby allein reicht nicht
                    if (!onCanvas && !isInitiator) return
                    AccountSession.emitPublicVote(
                        PublicVoteEvent.Open(
                            lobbyId = lobby.id,
                            proposalId = json.getString("proposalId"),
                            by = by,
                            byPeerId = json.optString("byPeerId").takeIf { it.isNotBlank() },
                            endsAt = json.optLong("endsAt"),
                            yes = json.optInt("yes", 1),
                            total = json.optInt("total", 1),
                            rewardCoins = json.optInt("rewardCoins", 1),
                            alreadyVoted = alreadyVoted,
                            isInitiator = isInitiator
                        )
                    )
                    return
                }
                "public_vote_update" -> {
                    AccountSession.emitPublicVote(
                        PublicVoteEvent.Update(
                            lobbyId = lobby.id,
                            proposalId = json.getString("proposalId"),
                            yes = json.optInt("yes"),
                            no = json.optInt("no"),
                            total = json.optInt("total"),
                            rewardCoins = json.optInt("rewardCoins", 1)
                        )
                    )
                    return
                }
                "public_result" -> {
                    AccountSession.emitPublicVote(
                        PublicVoteEvent.Result(
                            lobbyId = lobby.id,
                            proposalId = json.getString("proposalId"),
                            approved = json.optBoolean("approved"),
                            yes = json.optInt("yes"),
                            total = json.optInt("total"),
                            rewardCoins = json.optInt("rewardCoins", 0),
                            reason = json.optString("reason", "")
                        )
                    )
                    return
                }
                "public_capture_request" -> {
                    val proposalId = json.optString("proposalId")
                    if (proposalId.isBlank()) return
                    scope.launch { fulfillPublicCapture(lobby.id, proposalId) }
                    return
                }
                "public_blocked" -> {
                    AccountSession.emitEconomyBlock(
                        json.optString("message", "Öffentlich teilen gerade nicht möglich.")
                    )
                    return
                }
                "economy_block" -> {
                    AccountSession.emitEconomyBlock(
                        json.optString("message", "Keine Coins mehr — Zuschauen geht weiter.")
                    )
                    return
                }
                "trial_expired" -> {
                    AccountSession.emitTrialExpired(json.optLong("trialDrawUntil", 0L))
                    AccountSession.emitEconomyBlock(
                        json.optString(
                            "message",
                            "Probezeit vorbei — melde dich mit Google an, um weiterzumalen."
                        )
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
                "game_state" -> {
                    val g = json.optJSONObject("game")
                    scope.launch {
                        _events.emit(
                            PairEvent.GameState(
                                lobbyId = lobby.id,
                                gameType = g?.optString("type").orEmpty(),
                                status = g?.optString("status").orEmpty(),
                                drawerPeerId = g?.optString("drawerPeerId"),
                                drawerNickname = g?.optString("drawerNickname"),
                                overlay = g?.optBoolean("overlay", false) == true,
                                endsAt = g?.optLong("endsAt", 0L) ?: 0L
                            )
                        )
                    }
                    return
                }
                "game_play" -> {
                    val g = json.optJSONObject("game") ?: return
                    scope.launch {
                        _events.emit(PairEvent.GamePlay(lobby.id, g))
                    }
                    return
                }
                "game_stop" -> {
                    scope.launch { _events.emit(PairEvent.GameStopped(lobby.id)) }
                    return
                }
                "game_words_pick" -> {
                    val opts = json.optJSONArray("options")
                    val list = buildList {
                        if (opts != null) {
                            for (i in 0 until opts.length()) {
                                add(opts.optString(i))
                            }
                        }
                    }
                    scope.launch {
                        _events.emit(
                            PairEvent.GameWordsPick(
                                lobbyId = lobby.id,
                                options = list,
                                drawerNickname = json.optString("drawerNickname")
                            )
                        )
                    }
                    return
                }
                "game_words_secret" -> {
                    scope.launch {
                        _events.emit(
                            PairEvent.GameWordsSecret(
                                lobbyId = lobby.id,
                                word = json.optString("word"),
                                endsAt = json.optLong("endsAt", 0L)
                            )
                        )
                    }
                    return
                }
                "game_words_correct" -> {
                    scope.launch {
                        _events.emit(
                            PairEvent.GameWordsCorrect(
                                lobbyId = lobby.id,
                                winner = json.optString("winner"),
                                word = json.optString("word"),
                                drawerNickname = json.optString("drawerNickname")
                            )
                        )
                    }
                    return
                }
                "game_words_timeout" -> {
                    scope.launch {
                        _events.emit(
                            PairEvent.GameWordsTimeout(
                                lobbyId = lobby.id,
                                word = json.optString("word"),
                                drawerNickname = json.optString("drawerNickname")
                            )
                        )
                    }
                    return
                }
                "game_guess_chat" -> {
                    scope.launch {
                        _events.emit(
                            PairEvent.GameGuessChat(
                                lobbyId = lobby.id,
                                nickname = json.optString("nickname"),
                                text = json.optString("text"),
                                correct = json.optBoolean("ok")
                            )
                        )
                    }
                    return
                }
                "game_guess_result" -> {
                    scope.launch {
                        _events.emit(
                            PairEvent.GameGuessResult(
                                lobbyId = lobby.id,
                                ok = json.optBoolean("ok"),
                                message = json.optString("message")
                            )
                        )
                    }
                    return
                }
            }
        }

        when (val message = PairProtocol.decode(text)) {
            is PairMessage.StrokeMsg -> {
                CanvasStore.addRemoteStroke(message.stroke, lobby.id)
                // Avatar-Farbe = Zeichenfarbe
                PairSessionState.updatePeerColor(
                    lobby.id,
                    message.stroke.nickname,
                    message.stroke.colorIndex
                )
                LuvAlertNotifier.onPartnerStroke(
                    this,
                    lobbyName = lobby.name,
                    nickname = message.stroke.nickname ?: "Jemand",
                    lobbyId = lobby.id
                )
                val myId = AccountSession.account.value?.id
                val authorId = message.stroke.authorId?.takeIf { it.isNotBlank() }
                val fromPeer = !message.stroke.isLocal &&
                    (authorId == null || authorId != myId)
                if (fromPeer) {
                    scope.launch {
                        LuvApp.instance.prefs.bumpLobbyLastCanvasAt(
                            lobbyId = lobby.id,
                            actorUserId = authorId ?: "peer"
                        )
                    }
                }
                scope.launch { _events.emit(PairEvent.StrokeReceived(lobby.id, message.stroke)) }
            }
            is PairMessage.UndoMsg -> {
                CanvasStore.removeStrokeById(message.strokeId, lobby.id)
                scope.launch { _events.emit(PairEvent.StrokeUndone(lobby.id, message.strokeId)) }
            }
            is PairMessage.EraseCommit -> {
                CanvasStore.applyEraseCommit(message.removeIds, message.add, lobby.id)
                scope.launch {
                    _events.emit(
                        PairEvent.EraseCommitReceived(
                            lobbyId = lobby.id,
                            removeIds = message.removeIds,
                            added = message.add.size
                        )
                    )
                }
            }
            is PairMessage.Presence -> {
                val key = message.userId
                    ?: message.peerKey
                    ?: message.nickname
                    ?: "peer"
                PairSessionState.onPresence(
                    lobbyId = lobby.id,
                    peerKey = key,
                    nickname = message.nickname ?: "Jemand",
                    colorIndex = message.colorIndex,
                    active = message.active,
                    userId = message.userId
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
            is PairMessage.StickerPlace -> {
                CanvasStore.upsertRemoteEmojiStroke(
                    id = message.id,
                    emoji = message.emoji,
                    x = message.x,
                    y = message.y,
                    nickname = message.nickname,
                    lobbyId = lobby.id
                )
                val myNick = AccountSession.account.value?.nickname?.trim().orEmpty()
                val fromPeer = message.nickname.isNullOrBlank() ||
                    !message.nickname.equals(myNick, ignoreCase = true)
                if (fromPeer) {
                    scope.launch {
                        LuvApp.instance.prefs.bumpLobbyLastCanvasAt(
                            lobbyId = lobby.id,
                            actorUserId = "peer"
                        )
                    }
                }
                scope.launch {
                    _events.emit(
                        PairEvent.StickerPlaced(
                            lobby.id,
                            message.id,
                            message.emoji,
                            message.x,
                            message.y,
                            message.nickname
                        )
                    )
                }
            }
            is PairMessage.StickerRemove -> {
                CanvasStore.removeStrokeById(message.id, lobby.id)
                scope.launch {
                    _events.emit(PairEvent.StickerRemoved(lobby.id, message.id))
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

    private fun enqueueOrSend(lobbyId: String?, line: String) {
        val id = lobbyId ?: CanvasStore.activeLobbyId.value ?: return
        val session = sessions[id] ?: return
        val ws = session.webSocket.get()
        if (ws != null && ws.send(line)) return
        while (session.outbox.size >= MAX_OUTBOX) session.outbox.poll()
        session.outbox.offer(line)
    }

    private fun flushOutbox(session: LobbySession, webSocket: WebSocket) {
        while (true) {
            val line = session.outbox.poll() ?: break
            if (!webSocket.send(line)) {
                session.outbox.offer(line)
                break
            }
        }
    }

    private fun sendRaw(lobbyId: String?, line: String) {
        enqueueOrSend(lobbyId, line)
    }

    private fun stopLobby(lobbyId: String) {
        sessions.remove(lobbyId)?.let { session ->
            session.intentionalLeave = true
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
            ensureForeground()
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

    private fun connectedStateFor(lobby: Lobby): ConnectionState =
        if (lobby.role == com.luv.couple.data.Role.HOST) {
            ConnectionState.HOSTING
        } else {
            ConnectionState.CONNECTED
        }

    private fun applyLobbyName(lobbyId: String, name: String) {
        val clean = name.trim().take(PeerPalette.MAX_LOBBY_NAME_LENGTH)
        if (clean.isBlank()) return
        val session = sessions[lobbyId] ?: return
        if (session.lobby.name == clean) return
        session.lobby = session.lobby.copy(name = clean)
        scope.launch {
            LuvApp.instance.prefs.renameLobby(lobbyId, clean)
            LockScreenWidgetProvider.requestUpdate(applicationContext)
        }
    }

    private fun applyLobbyToken(lobbyId: String, token: String) {
        val clean = token.trim()
        if (clean.isBlank()) return
        val session = sessions[lobbyId] ?: return
        if (session.lobby.token == clean) return
        session.lobby = session.lobby.copy(token = clean)
        scope.launch {
            runCatching {
                val snap = LuvApp.instance.prefs.snapshot()
                val lobby = snap.lobbies.firstOrNull { it.id == lobbyId } ?: return@launch
                LuvApp.instance.prefs.upsertLobby(lobby.copy(token = clean))
            }
        }
    }

    /** Token/Kapazität/Roster vom Server holen — alle wieder im selben Raum. */
    private suspend fun refreshLobbyAccess(session: LobbySession) {
        val lobby = session.lobby
        val refreshed = runCatching {
            if (lobby.role == Role.HOST) {
                LuvApiClient.ensureRoom(lobby.code, lobby.token)
            } else {
                LuvApiClient.joinRoom(lobby.code)
            }
        }.getOrNull() ?: return
        val token = refreshed.token.trim()
        if (token.isBlank()) return
        session.lobby = session.lobby.copy(
            token = token,
            name = refreshed.name.ifBlank { session.lobby.name },
            capacity = refreshed.capacity.takeIf { it > 0 } ?: session.lobby.capacity,
            hostNickname = refreshed.hostNickname.ifBlank { session.lobby.hostNickname }
        )
        if (refreshed.capacity > 0) {
            PairSessionState.setCapacity(lobby.id, refreshed.capacity)
        }
        // Roster inkl. Offline-Mitglieder (falls API sie mitschickt)
        val roster = refreshed.memberList
        if (roster.isNotEmpty()) {
            PairSessionState.onRoster(
                lobby.id,
                roster,
                refreshed.peers.coerceAtLeast(1),
                refreshed.capacity.takeIf { it > 0 }
            )
        }
        runCatching {
            val snap = LuvApp.instance.prefs.snapshot()
            val local = snap.lobbies.firstOrNull { it.id == lobby.id } ?: return@runCatching
            LuvApp.instance.prefs.upsertLobby(
                local.copy(
                    token = token,
                    name = session.lobby.name,
                    capacity = session.lobby.capacity,
                    hostNickname = session.lobby.hostNickname
                )
            )
        }
        Log.i(TAG, "refreshed lobby access code=${lobby.code} cap=${session.lobby.capacity}")
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

    /**
     * Android verlangt eine Foreground-Notification.
     * Statt leerem „LUV“: warmer Impuls-Text (kein „verbunden“).
     */
    private fun ensureForeground() {
        val now = System.currentTimeMillis()
        // Stimmungszeile höchstens alle 12h wechseln — sonst wirkt jedes Update wie eine neue Meldung
        val refreshMood =
            lastServiceMoodLine.isBlank() || now - lastServiceMoodAtMs >= 12 * 60 * 60_000L
        if (refreshMood) {
            lastServiceMoodLine = MoodLines.pickText()
            lastServiceMoodAtMs = now
        }
        val line = lastServiceMoodLine.ifBlank { MoodLines.pickText() }
        if (foregroundStarted.get()) {
            if (refreshMood) {
                NotificationManagerCompat.from(this)
                    .notify(NOTIFICATION_ID, buildQuietServiceNotification(line))
            }
            return
        }
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildQuietServiceNotification(line),
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

    private fun buildQuietServiceNotification(line: String): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val behavior = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            NotificationCompat.FOREGROUND_SERVICE_DEFERRED
        } else {
            NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
        }
        val text = line.ifBlank { getString(R.string.notification_service_text) }
        return NotificationCompat.Builder(this, LuvApp.CHANNEL_ID)
            .setContentTitle(text)
            .setContentText("LUV · tippen zum Öffnen")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(text)
                    .setBigContentTitle("LUV")
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(behavior)
            .build()
    }

    private suspend fun fulfillPublicCapture(lobbyId: String, proposalId: String) {
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bg = CanvasStore.backgroundFor(CanvasStore.cachedColorIndex)
                CanvasStore.renderBitmap(720, 1280, bg, lobbyId)
            }.getOrNull()
        }
        if (bitmap == null) {
            Log.w(TAG, "public capture failed lobby=$lobbyId")
            return
        }
        // Auch in die lokale Galerie — unabhängig vom Konto
        runCatching {
            com.luv.couple.data.LocalMoments.save(applicationContext, bitmap, "LUV_public_")
        }
        val b64 = withContext(Dispatchers.IO) {
            runCatching {
                val stream = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 88, stream)) return@runCatching null
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            }.getOrNull()
        }
        if (b64.isNullOrBlank()) {
            Log.w(TAG, "public capture encode failed lobby=$lobbyId")
            return
        }
        sendRaw(
            lobbyId,
            JSONObject()
                .put("type", "public_capture")
                .put("proposalId", proposalId)
                .put("imageBase64", b64)
                .toString()
        )
    }

    override fun onDestroy() {
        if (instanceRef === this) instanceRef = null
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
        private const val MIN_BACKOFF_MS = 2_000L
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val STABLE_CONNECTION_MS = 8_000L
        /** JSON-Ping (kein OkHttp-Ping) — hält NAT/Proxy wach, verhindert Idle-Reconnect. */
        private const val WS_PING_INTERVAL_MS = 15_000L
        private const val MAX_OUTBOX = 80

        @Volatile
        private var instanceRef: PairConnectionService? = null

        private val _state = MutableStateFlow(ConnectionState.IDLE)
        val state: StateFlow<ConnectionState> = _state.asStateFlow()

        private val _lobbyStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
        val lobbyStates: StateFlow<Map<String, ConnectionState>> = _lobbyStates.asStateFlow()

        private val _reconnectUi = MutableStateFlow<Map<String, LobbyReconnectUi>>(emptyMap())
        val reconnectUi: StateFlow<Map<String, LobbyReconnectUi>> = _reconnectUi.asStateFlow()

        // Großer Buffer — bei schnellem Malen dürfen keine Stroke-Events verloren gehen
        private val _events = MutableSharedFlow<PairEvent>(
            extraBufferCapacity = 512,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
        val events: SharedFlow<PairEvent> = _events.asSharedFlow()

        private fun updateAggregate(value: ConnectionState) {
            _state.value = value
        }

        private fun MutableStateFlow<Map<String, ConnectionState>>.updateAndRemove(lobbyId: String) {
            value = value - lobbyId
        }

        private fun dispatchPayload(context: Context, payload: String, lobbyId: String?) {
            val service = instanceRef
            if (service != null) {
                service.enqueueOrSend(lobbyId, payload)
                return
            }
            try {
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_STROKE)
                    .putExtra(EXTRA_PAYLOAD, payload)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to dispatch payload", t)
            }
        }

        /**
         * Ab erstem startAll: 10s Schonzeit, bevor Home „Nicht verbunden“ zeigt.
         */
        @Volatile
        var connectWatchStartedAtMs: Long = 0L
            private set

        fun noteConnectWatchStart() {
            if (connectWatchStartedAtMs == 0L) {
                connectWatchStartedAtMs = System.currentTimeMillis()
            }
        }

        fun isConnectGraceActive(graceMs: Long = 10_000L): Boolean {
            val started = connectWatchStartedAtMs
            if (started <= 0L) return true
            return System.currentTimeMillis() - started < graceMs
        }

        fun startAll(context: Context): Boolean {
            return try {
                noteConnectWatchStart()
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_START_ALL)
                context.startForegroundService(intent)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to start pair service", t)
                false
            }
        }

        /**
         * Soft-Wait für Splash: standardmäßig reicht **eine** Lobby CONNECTED/HOSTING.
         * Rest verbindet im Hintergrund — Home nicht auf alle Lobbys blockieren.
         */
        suspend fun awaitLobbiesConnected(
            lobbyIds: Collection<String>,
            timeoutMs: Long = 2_500L,
            requireAll: Boolean = false,
        ) {
            val ids = lobbyIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (ids.isEmpty()) return
            fun ready(s: ConnectionState?) =
                s == ConnectionState.CONNECTED || s == ConnectionState.HOSTING
            if (requireAll) {
                if (ids.all { ready(lobbyStates.value[it]) }) return
            } else if (ids.any { ready(lobbyStates.value[it]) }) {
                return
            }
            withTimeoutOrNull(timeoutMs) {
                lobbyStates.first { states ->
                    if (requireAll) ids.all { ready(states[it]) }
                    else ids.any { ready(states[it]) }
                }
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
            dispatchPayload(context, strokeJson, lobbyId)
        }

        fun sendClear(context: Context, lobbyId: String? = null) {
            val nickname = CanvasStore.cachedNickname
                ?: AccountSession.account.value?.nickname
            val payload = PairProtocol.encode(PairMessage.ClearPropose(nickname))
            dispatchPayload(context, payload, lobbyId)
        }

        fun sendClearVote(context: Context, proposalId: String, yes: Boolean, lobbyId: String? = null) {
            val payload = PairProtocol.encode(PairMessage.ClearVote(proposalId, yes))
            dispatchPayload(context, payload, lobbyId)
        }

        fun sendPublicPropose(context: Context, lobbyId: String? = null) {
            val nickname = CanvasStore.cachedNickname
                ?: AccountSession.account.value?.nickname
            val payload = JSONObject()
                .put("type", "public_propose")
                .put("nickname", nickname ?: JSONObject.NULL)
                .toString()
            dispatchPayload(context, payload, lobbyId)
        }

        fun sendPublicVote(context: Context, proposalId: String, yes: Boolean, lobbyId: String? = null) {
            val payload = JSONObject()
                .put("type", "public_vote")
                .put("proposalId", proposalId)
                .put("yes", yes)
                .toString()
            dispatchPayload(context, payload, lobbyId)
        }

        fun sendPublicCapture(context: Context, proposalId: String, imageBase64: String, lobbyId: String? = null) {
            val payload = JSONObject()
                .put("type", "public_capture")
                .put("proposalId", proposalId)
                .put("imageBase64", imageBase64)
                .toString()
            dispatchPayload(context, payload, lobbyId)
        }

        fun sendUndo(context: Context, strokeId: String, lobbyId: String? = null) {
            try {
                val service = instanceRef
                if (service != null) {
                    service.sendRaw(
                        lobbyId,
                        PairProtocol.encode(PairMessage.UndoMsg(strokeId))
                    )
                    return
                }
                val intent = Intent(context, PairConnectionService::class.java)
                    .setAction(ACTION_SEND_UNDO)
                    .putExtra(EXTRA_STROKE_ID, strokeId)
                    .putExtra(EXTRA_LOBBY_ID, lobbyId)
                context.startService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to send undo", t)
            }
        }

        fun sendEraseCommit(
            context: Context,
            removeIds: List<String>,
            add: List<com.luv.couple.data.Stroke>,
            lobbyId: String? = null
        ) {
            val payload = PairProtocol.encode(
                PairMessage.EraseCommit(removeIds = removeIds, add = add)
            )
            dispatchPayload(context, payload, lobbyId)
        }

        fun sendPresence(context: Context, active: Boolean, lobbyId: String? = null) {
            val nickname = CanvasStore.cachedNickname
                ?: AccountSession.account.value?.nickname
            val userId = AccountSession.account.value?.id
            val colorIndex = CanvasStore.cachedColorIndex
            val payload = PairProtocol.encode(
                PairMessage.Presence(
                    active = active,
                    nickname = nickname,
                    colorIndex = colorIndex,
                    peerKey = userId ?: nickname,
                    userId = userId
                )
            )
            dispatchPayload(context, payload, lobbyId ?: CanvasStore.activeLobbyId.value)
        }

        private fun parseRosterMembers(json: JSONObject): List<RosterMember> {
            val rich = json.optJSONArray("memberList")
            if (rich != null && rich.length() > 0) {
                return buildList {
                    for (i in 0 until rich.length()) {
                        val o = rich.optJSONObject(i) ?: continue
                        val nick = o.optString("nickname").trim()
                        if (!PairSessionState.isKnownDisplayNickname(nick)) continue
                        add(
                            RosterMember(
                                userId = o.optString("userId").takeIf { it.isNotBlank() && it != "null" },
                                nickname = nick,
                                colorIndex = o.optInt("colorIndex", -1),
                                active = o.optBoolean("active", false),
                                online = if (o.has("online")) o.optBoolean("online", true) else true,
                                petEmoji = o.optString("petEmoji", "🐣").trim().ifBlank { "🐣" }
                            )
                        )
                    }
                }
            }
            val arr = json.optJSONArray("members") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val nick = arr.optString(i).trim()
                    if (PairSessionState.isKnownDisplayNickname(nick)) {
                        add(RosterMember(userId = null, nickname = nick))
                    }
                }
            }
        }

        fun sendNote(context: Context, text: String, lobbyId: String? = null) {
            val payload = PairProtocol.encode(PairMessage.Note(text))
            dispatchPayload(context, payload, lobbyId ?: CanvasStore.activeLobbyId.value)
        }

        fun sendRecolor(context: Context, nickname: String?, colorIndex: Int, lobbyId: String? = null) {
            val payload = PairProtocol.encode(PairMessage.Recolor(nickname, colorIndex))
            dispatchPayload(context, payload, lobbyId ?: CanvasStore.activeLobbyId.value)
        }

        fun sendReaction(context: Context, emoji: String, lobbyId: String? = null) {
            val nickname = CanvasStore.cachedNickname
                ?: AccountSession.account.value?.nickname
            val payload = PairProtocol.encode(PairMessage.Reaction(emoji, nickname))
            dispatchPayload(context, payload, lobbyId ?: CanvasStore.activeLobbyId.value)
        }

        fun sendStickerPlace(
            context: Context,
            id: String,
            emoji: String,
            x: Float,
            y: Float,
            lobbyId: String? = null
        ) {
            val nickname = CanvasStore.cachedNickname
                ?: AccountSession.account.value?.nickname
            val payload = PairProtocol.encode(
                PairMessage.StickerPlace(id, emoji, x, y, nickname)
            )
            dispatchPayload(context, payload, lobbyId ?: CanvasStore.activeLobbyId.value)
        }

        fun sendStickerRemove(context: Context, id: String, lobbyId: String? = null) {
            val payload = PairProtocol.encode(PairMessage.StickerRemove(id))
            dispatchPayload(context, payload, lobbyId ?: CanvasStore.activeLobbyId.value)
        }

        fun sendGameBoard(
            context: Context,
            game: String,
            visible: Boolean,
            lobbyId: String? = null
        ) {
            val payload = PairProtocol.encode(PairMessage.GameBoard(game, visible))
            dispatchPayload(context, payload, lobbyId ?: CanvasStore.activeLobbyId.value)
        }

        fun sendGameStart(context: Context, game: String, lobbyId: String? = null) {
            val nickname = CanvasStore.cachedNickname
                ?: AccountSession.account.value?.nickname
            val payload = JSONObject()
                .put("type", "game_start")
                .put("game", game)
                .put("nickname", nickname ?: JSONObject.NULL)
                .toString()
            dispatchPayload(context, payload, lobbyId ?: CanvasStore.activeLobbyId.value)
        }

        fun sendGameStop(context: Context, lobbyId: String? = null) {
            dispatchPayload(
                context,
                JSONObject().put("type", "game_stop").toString(),
                lobbyId ?: CanvasStore.activeLobbyId.value
            )
        }

        fun sendGameAction(
            context: Context,
            action: String,
            payload: JSONObject = JSONObject(),
            lobbyId: String? = null
        ) {
            val nickname = CanvasStore.cachedNickname
                ?: AccountSession.account.value?.nickname
            dispatchPayload(
                context,
                JSONObject()
                    .put("type", "game_action")
                    .put("action", action)
                    .put("payload", payload)
                    .put("nickname", nickname ?: JSONObject.NULL)
                    .toString(),
                lobbyId ?: CanvasStore.activeLobbyId.value
            )
        }

        fun sendGamePick(context: Context, word: String, lobbyId: String? = null) {
            dispatchPayload(
                context,
                JSONObject().put("type", "game_pick").put("word", word).toString(),
                lobbyId ?: CanvasStore.activeLobbyId.value
            )
        }

        fun sendGameGuess(context: Context, text: String, lobbyId: String? = null) {
            val nickname = CanvasStore.cachedNickname
                ?: AccountSession.account.value?.nickname
            dispatchPayload(
                context,
                JSONObject()
                    .put("type", "game_guess")
                    .put("text", text)
                    .put("nickname", nickname ?: JSONObject.NULL)
                    .toString(),
                lobbyId ?: CanvasStore.activeLobbyId.value
            )
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
    data class EraseCommitReceived(
        val lobbyId: String,
        val removeIds: List<String>,
        val added: Int
    ) : PairEvent()
    data class HistoryApplied(val lobbyId: String) : PairEvent()
    data class Cleared(val lobbyId: String) : PairEvent()
    data class LobbyGone(val lobbyId: String, val name: String) : PairEvent()
    /** Anderes Gerät desselben Kontos hat die Leinwand betreten — nur Canvas schließen, Lobby bleibt. */
    data class CanvasTaken(val lobbyId: String, val message: String) : PairEvent()
    data class ColorAssigned(val lobbyId: String, val colorIndex: Int) : PairEvent()
    data class RecolorReceived(val lobbyId: String, val nickname: String?, val colorIndex: Int) : PairEvent()
    data class ReactionReceived(val lobbyId: String, val emoji: String, val nickname: String?) : PairEvent()
    data class StickerPlaced(
        val lobbyId: String,
        val id: String,
        val emoji: String,
        val x: Float,
        val y: Float,
        val nickname: String?
    ) : PairEvent()
    data class StickerRemoved(val lobbyId: String, val id: String) : PairEvent()
    data class StickersHistory(
        val lobbyId: String,
        val stickers: List<StickerPlaced>
    ) : PairEvent()
    data class GameBoardReceived(val lobbyId: String, val game: String, val visible: Boolean) : PairEvent()
    data class GameState(
        val lobbyId: String,
        val gameType: String,
        val status: String,
        val drawerPeerId: String?,
        val drawerNickname: String?,
        val overlay: Boolean,
        val endsAt: Long = 0L
    ) : PairEvent()
    data class GameStopped(val lobbyId: String) : PairEvent()
    data class GamePlay(val lobbyId: String, val game: JSONObject) : PairEvent()
    data class GameWordsPick(
        val lobbyId: String,
        val options: List<String>,
        val drawerNickname: String?
    ) : PairEvent()
    data class GameWordsSecret(
        val lobbyId: String,
        val word: String,
        val endsAt: Long = 0L
    ) : PairEvent()
    data class GameWordsCorrect(
        val lobbyId: String,
        val winner: String,
        val word: String,
        val drawerNickname: String?
    ) : PairEvent()
    data class GameWordsTimeout(
        val lobbyId: String,
        val word: String,
        val drawerNickname: String?
    ) : PairEvent()
    data class GameGuessChat(
        val lobbyId: String,
        val nickname: String,
        val text: String,
        val correct: Boolean
    ) : PairEvent()
    data class GameGuessResult(val lobbyId: String, val ok: Boolean, val message: String) : PairEvent()
    data class WeddingConfirm(
        val lobbyId: String,
        val confirms: Map<String, Boolean>,
        val fromUserId: String?
    ) : PairEvent()
}
