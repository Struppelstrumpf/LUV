package com.luv.couple.net

import android.content.Context
import android.util.Log
import com.luv.couple.notify.LuvAlertNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Account-level WebSocket (/v1/ws/account) — Freunde/Hochzeit/Markt/Erfolge live,
 * solange der Prozess (oft FGS) lebt. FCM deckt tote App ab.
 */
object AccountPushConnection {
    private const val TAG = "LuvAccountPush"
    private const val PING_MS = 20_000L
    private const val MIN_BACKOFF_MS = 1_500L
    private const val MAX_BACKOFF_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val socketRef = AtomicReference<WebSocket?>(null)
    private var loopJob: Job? = null

    private val wsClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun start(context: Context) {
        val app = context.applicationContext
        if (!running.compareAndSet(false, true)) {
            if (loopJob?.isActive != true) {
                loopJob = scope.launch { runLoop(app) }
            }
            return
        }
        loopJob?.cancel()
        loopJob = scope.launch { runLoop(app) }
    }

    fun stop() {
        running.set(false)
        loopJob?.cancel()
        loopJob = null
        socketRef.getAndSet(null)?.close(1000, "bye")
    }

    fun ensureStarted(context: Context) {
        if (LuvApiClient.sessionToken.isNullOrBlank()) {
            stop()
            return
        }
        start(context)
    }

    private suspend fun runLoop(app: Context) {
        var backoff = MIN_BACKOFF_MS
        while (scope.isActive && running.get()) {
            val session = LuvApiClient.sessionToken
            if (session.isNullOrBlank()) {
                delay(2_000L)
                continue
            }
            val opened = connectOnce(app, session)
            if (!running.get()) break
            if (opened) {
                backoff = MIN_BACKOFF_MS
            } else {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    private suspend fun connectOnce(app: Context, session: String): Boolean {
        val url = LuvApiClient.accountWsUrl(session)
        val request = Request.Builder().url(url).build()
        return suspendCancellableCoroutine { cont ->
            val settled = AtomicBoolean(false)
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    socketRef.set(webSocket)
                    // Nach Connect/Reconnect einmal synchronisieren (Events in der Lücke)
                    AccountEventRouter.syncAfterReconnect(app)
                    scope.launch {
                        while (running.get() && socketRef.get() === webSocket) {
                            delay(PING_MS)
                            runCatching {
                                webSocket.send(JSONObject().put("type", "ping").toString())
                            }
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(app, text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socketRef.compareAndSet(webSocket, null)
                    if (settled.compareAndSet(false, true) && cont.isActive) {
                        cont.resume(true)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "account ws fail: ${t.message}")
                    socketRef.compareAndSet(webSocket, null)
                    if (settled.compareAndSet(false, true) && cont.isActive) {
                        cont.resume(false)
                    }
                }
            }
            val ws = wsClient.newWebSocket(request, listener)
            cont.invokeOnCancellation {
                ws.cancel()
                socketRef.compareAndSet(ws, null)
            }
        }
    }

    private fun handleMessage(app: Context, text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "pong", "account_hello" -> Unit
            "account_event" -> {
                val event = json.optString("event").trim()
                val data = json.optJSONObject("data") ?: JSONObject()
                AccountEventRouter.onEvent(app, event, data)
            }
        }
    }
}

/** Shared by Account-WS and FCM data payloads. */
object AccountEventRouter {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Einmal nach WS-Open — ersetzt periodischen HTTP-Poll. */
    fun syncAfterReconnect(context: Context) {
        val app = context.applicationContext
        ioScope.launch {
            runCatching { NotificationBadges.refreshFriends(app, force = true) }
            NotificationBadges.syncAppBadge(app)
        }
    }

    fun onEvent(context: Context, event: String, data: JSONObject) {
        val app = context.applicationContext
        when (event) {
            "friend_request", "friend_accepted", "friend_removed",
            "marriage_proposal", "marriage_update", "lobby_invite" -> {
                LuvApiClient.invalidateFriendsCache()
                CeremonyRefreshBus.bump()
                if (event == "marriage_update") {
                    val status = data.optString("status").trim().lowercase()
                    val removed = data.optString("removedCeremonyLobbyCode")
                        .trim()
                        .uppercase()
                        .removePrefix("LUV-")
                    val ceremonyGone =
                        status == "cancelled" ||
                            status == "rejected" ||
                            removed.isNotBlank() ||
                            (status.isNotBlank() &&
                                data.optString("ceremonyLobbyCode").isBlank() &&
                                data.has("removedCeremonyLobbyCode"))
                    if (ceremonyGone) {
                        ioScope.launch {
                            runCatching {
                                val prefs = com.luv.couple.LuvApp.instance.prefs
                                prefs.removeWeddingCeremonyLobbies(
                                    removed.takeIf { it.length >= 3 }
                                )
                                com.luv.couple.lock.CanvasStore.updateKnownLobbies(
                                    prefs.snapshot().lobbies.map { it.id }
                                )
                            }
                            CeremonyLobbyGoneBus.bump()
                        }
                    }
                }
                ioScope.launch {
                    runCatching { NotificationBadges.refreshFriends(app, force = true) }
                    NotificationBadges.syncAppBadge(app)
                }
            }
            "market_sold" -> {
                val coins = data.optInt("priceCoins", 0).coerceAtLeast(1)
                runCatching { LuvAlertNotifier.onMarketSale(app, 1, coins) }
                ioScope.launch {
                    runCatching { NotificationBadges.refreshPendingSales(app) }
                }
            }
            "achievement_unlocked", "achievement_claimable" -> {
                val id = data.optString("achievementId").trim()
                val fp = when {
                    id.isNotBlank() -> id
                    data.optBoolean("daily", false) -> "daily:${System.currentTimeMillis() / 86_400_000L}"
                    else -> "claimable"
                }
                NotificationBadges.onAchievementsClaimable(true, fp)
                NotificationBadges.syncAppBadge(app)
            }
            "home_feed" -> HomeFeedRefreshBus.bump()
            "live_notice" -> {
                val id = data.optString("id").trim()
                val msg = data.optString("message").trim()
                if (id.isNotBlank() && msg.isNotBlank()) {
                    LiveNoticeBus.offer(
                        LiveNotice(
                            id = id,
                            message = msg,
                            authorNickname = data.optString("authorNickname").ifBlank { "LUV" },
                            createdAt = data.optLong("createdAt", System.currentTimeMillis()),
                            kind = data.optString("kind").ifBlank { "team" },
                            subtitle = data.optString("subtitle").takeIf { it.isNotBlank() },
                            targetUserId = data.optString("targetUserId").takeIf { it.isNotBlank() },
                        )
                    )
                }
            }
            "staff_warning" -> {
                val id = data.optString("id").trim()
                val msg = data.optString("message").trim()
                if (id.isNotBlank() && msg.isNotBlank()) {
                    StaffWarningBus.offerFromPush(
                        LuvApiClient.StaffWarning(
                            id = id,
                            message = msg,
                            severity = data.optString("severity", "warn").ifBlank { "warn" },
                            at = data.optLong("at", System.currentTimeMillis()),
                            byNick = "Team",
                            seen = false,
                        )
                    )
                }
            }
            "maintenance_start" -> MaintenancePushBus.bump()
            "shop_rotated" -> {
                ShopRotatedBus.bump()
                ioScope.launch {
                    runCatching {
                        com.luv.couple.ui.screens.MarketHubCache.warm(force = true)
                    }
                }
            }
            "inventory_new" -> {
                InventoryRefreshBus.bump()
                NotificationBadges.syncAppBadge(app)
            }
            "lootbox_ready" -> {
                LootboxRefreshBus.bump()
                NotificationBadges.syncAppBadge(app)
            }
            else -> Unit
        }
    }

    /** FCM data map (all string values). */
    fun onFcmData(context: Context, data: Map<String, String>) {
        val type = data["type"]?.trim().orEmpty()
        if (type.isBlank()) return
        val json = JSONObject()
        data.forEach { (k, v) -> if (k != "type") json.put(k, v) }
        onEvent(context, type, json)
    }
}
