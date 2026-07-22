package com.luv.couple.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.luv.couple.LuvApp
import com.luv.couple.MainActivity
import com.luv.couple.R
import com.luv.couple.data.QuietHoursGate
import com.luv.couple.lock.CanvasStore
import com.luv.couple.lock.LockDrawActivity
import com.luv.couple.lock.LockScreenWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Nutzer-Benachrichtigungen: Beitritt, Zeichnen, Tagesbonus, Live-Nähe.
 * (Die stille Foreground-Service-Notification ist davon getrennt.)
 */
object LuvAlertNotifier {
    const val CHANNEL_ID = "luv_alerts_v2"
    /** v2: leiser als der alte HIGH-Kanal (Importance wird nach Erstellung nicht mehr geändert). */
    const val LIVE_CHANNEL_ID = "luv_live_near_v2"
    const val MOOD_CHANNEL_ID = "luv_mood_v2"
    private const val DRAW_MIN_INTERVAL_MS = 90_000L
    private const val DRAW_RICH_INTERVAL_MS = 60_000L
    private const val DRAW_WAKE_INTERVAL_MS = 180_000L
    /** Vorschau kurz nach dem letzten Strich — mind. ~1,2 s Mal-Burst. */
    private const val PREVIEW_SETTLE_MS = 2_000L
    private const val PREVIEW_MIN_PAINT_MS = 1_200L
    private const val NOTIFY_DRAW_BASE = 770
    private const val NOTIFY_JOIN_BASE = 880
    private const val NOTIFY_CLEAR_BASE = 900
    private const val NOTIFY_PUBLIC_BASE = 920
    private const val NOTIFY_DAILY = 990
    private const val NOTIFY_MOOD = 995
    private const val NOTIFY_MEMORY_BASE = 1100
    private const val NOTIFY_MARKET_SALE = 996
    private const val NOTIFY_APP_BADGE = 997
    private const val NOTIFY_FRIEND = 998
    private const val NOTIFY_MARRIAGE = 1002
    private const val NOTIFY_LOBBY_INVITE = 1003
    private const val NOTIFY_ACHIEVEMENT = 999
    private const val NOTIFY_INVENTORY = 1001

    private data class PaintBurst(
        var startedAt: Long,
        var lastStrokeAt: Long,
        var nickname: String,
        var lobbyName: String
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastDrawByLobby = ConcurrentHashMap<String, Long>()
    private val lastWakeByLobby = ConcurrentHashMap<String, Long>()
    private val dailyNotifiedDays = ConcurrentHashMap.newKeySet<String>()
    private val paintBursts = ConcurrentHashMap<String, PaintBurst>()
    private val previewJobs = ConcurrentHashMap<String, Job>()

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        val alerts = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alerts_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.alerts_channel_desc)
            enableVibration(true)
            setShowBadge(true)
        }
        val live = NotificationChannel(
            LIVE_CHANNEL_ID,
            context.getString(R.string.live_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.live_channel_desc)
            enableVibration(false)
            setShowBadge(false)
        }
        val mood = NotificationChannel(
            MOOD_CHANNEL_ID,
            context.getString(R.string.mood_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.mood_channel_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        mgr.createNotificationChannel(alerts)
        mgr.createNotificationChannel(live)
        mgr.createNotificationChannel(mood)
    }

    /** Sanfter Tages-Impuls — leise, kurz, emotional. */
    fun showMoodNudge(
        context: Context,
        text: String,
        subtitle: String? = null,
        deepTarget: String? = null
    ) {
        if (QuietHoursGate.isQuietNow()) return
        val line = text.ifBlank { MoodLines.pickText() }
        val sub = subtitle?.trim()?.takeIf { it.isNotBlank() }
            ?: "Tippen — und kurz vorbeischauen"
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            NOTIFY_MOOD,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_FROM_NOTIFICATION, true)
                val target = deepTarget?.trim()?.lowercase().orEmpty()
                if (target.isNotBlank() && target != "none") {
                    putExtra(MainActivity.EXTRA_DEEP_TARGET, target)
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, MOOD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(line)
            .setContentText(sub)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$line\n$sub")
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFY_MOOD, notification)
    }

    fun onPartnerStroke(
        context: Context,
        lobbyName: String,
        nickname: String,
        lobbyId: String
    ) {
        scope.launch {
            LiveProximity.onRemoteStroke(lobbyId, nickname)
            if (QuietHoursGate.isQuietNow()) return@launch
            val app = context.applicationContext
            // Pro Lobby: Nähe-Impulse standardmäßig aus (Glocke auf Home)
            val lobbyNear = runCatching {
                LuvApp.instance.prefs.isLobbyProximityEnabled(lobbyId)
            }.getOrDefault(false)
            if (!lobbyNear) return@launch

            val rich = runCatching { LuvApp.instance.prefs.isLiveProximityRichEnabled() }.getOrDefault(true)
            val wake = runCatching { LuvApp.instance.prefs.isLiveProximityWakeEnabled() }.getOrDefault(false)
            val notifyOn = prefsEnabled()
            val hapticOn = runCatching { LuvApp.instance.prefs.isPartnerHapticEnabled() }.getOrDefault(true)

            if (rich) {
                LockScreenWidgetProvider.requestUpdate(app)
                LiveProximity.scheduleWidgetPulse(app, lobbyId)
            }
            if (hapticOn && rich) {
                softPulse(app)
            }

            if (!notifyOn) return@launch

            val who = nickname.ifBlank { "Jemand" }
            if (rich) {
                // Vorschau + Push nach kurzem Debounce, sobald mind. kurz gemalt wurde.
                scheduleRichPreviewNotify(
                    app = app,
                    lobbyId = lobbyId,
                    lobbyName = lobbyName.ifBlank { "Lobby" },
                    nickname = who,
                    wake = wake
                )
                return@launch
            }

            val now = SystemClock.elapsedRealtime()
            val last = lastDrawByLobby[lobbyId] ?: 0L
            if (last > 0L && now - last < DRAW_MIN_INTERVAL_MS) return@launch
            lastDrawByLobby[lobbyId] = now
            showLiveStroke(
                context = app,
                lobbyName = lobbyName,
                nickname = who,
                lobbyId = lobbyId,
                preview = null,
                rich = false,
                wake = wake
            )
        }
    }

    private fun scheduleRichPreviewNotify(
        app: Context,
        lobbyId: String,
        lobbyName: String,
        nickname: String,
        wake: Boolean
    ) {
        val now = SystemClock.elapsedRealtime()
        val burst = paintBursts.getOrPut(lobbyId) {
            PaintBurst(startedAt = now, lastStrokeAt = now, nickname = nickname, lobbyName = lobbyName)
        }
        // Neuer Burst, wenn die Pause schon länger als das Settle-Fenster war
        if (now - burst.lastStrokeAt > PREVIEW_SETTLE_MS * 2) {
            burst.startedAt = now
        }
        burst.lastStrokeAt = now
        burst.nickname = nickname
        burst.lobbyName = lobbyName

        previewJobs.remove(lobbyId)?.cancel()
        previewJobs[lobbyId] = scope.launch {
            val expectedLast = burst.lastStrokeAt
            delay(PREVIEW_SETTLE_MS)
            val current = paintBursts[lobbyId] ?: return@launch
            // Inzwischen weiter gemalt → ein neuer Job hat uns ersetzt / Zustand veraltet
            if (current.lastStrokeAt != expectedLast) return@launch
            val paintedFor = current.lastStrokeAt - current.startedAt
            if (paintedFor < PREVIEW_MIN_PAINT_MS) {
                paintBursts.remove(lobbyId)
                return@launch
            }
            val rateNow = SystemClock.elapsedRealtime()
            val lastNotify = lastDrawByLobby[lobbyId] ?: 0L
            if (lastNotify > 0L && rateNow - lastNotify < DRAW_RICH_INTERVAL_MS) {
                paintBursts.remove(lobbyId)
                return@launch
            }
            lastDrawByLobby[lobbyId] = rateNow
            paintBursts.remove(lobbyId)
            previewJobs.remove(lobbyId)

            val preview = runCatching {
                val bg = CanvasStore.backgroundFor(CanvasStore.cachedColorIndex)
                CanvasStore.renderBitmap(512, 720, bg, lobbyId)
            }.getOrNull()
            showLiveStroke(
                context = app,
                lobbyName = current.lobbyName,
                nickname = current.nickname,
                lobbyId = lobbyId,
                preview = preview,
                rich = true,
                wake = wake
            )
        }
    }

    fun onPeerJoined(
        context: Context,
        lobbyName: String,
        nickname: String,
        lobbyId: String,
        userId: String? = null,
        firstJoin: Boolean = true
    ) {
        scope.launch {
            // Reconnect / App-Wiederöffnen ist kein neuer Beitritt
            if (!firstJoin) return@launch
            if (QuietHoursGate.isQuietNow()) return@launch
            if (!prefsEnabled()) return@launch
            val who = nickname.ifBlank { "Jemand" }
            val peerKey = userId?.takeIf { it.isNotBlank() } ?: who.lowercase()
            val claimed = runCatching {
                LuvApp.instance.prefs.claimJoinAnnouncement(lobbyId, peerKey)
            }.getOrDefault(false)
            if (!claimed) return@launch
            show(
                context = context.applicationContext,
                title = "Lobby",
                text = context.getString(R.string.peer_join_text_fmt, lobbyName, who),
                notificationId = NOTIFY_JOIN_BASE + (lobbyId.hashCode() and 0xffff),
                lobbyId = lobbyId
            )
        }
    }

    /** Jemand fragt nach Leinwand leeren — Peer ist nicht in der Leinwand. */
    fun onClearAsk(
        context: Context,
        lobbyName: String,
        nickname: String,
        lobbyId: String
    ) {
        scope.launch {
            if (QuietHoursGate.isQuietNow()) return@launch
            if (LockDrawActivity.isCanvasForeground(lobbyId)) return@launch
            val who = nickname.ifBlank { "Jemand" }
            val lobby = lobbyName.ifBlank { "Lobby" }
            show(
                context = context.applicationContext,
                title = "Leinwand",
                text = context.getString(R.string.clear_ask_notify_fmt, who, lobby),
                notificationId = NOTIFY_CLEAR_BASE + (lobbyId.hashCode() and 0xffff),
                lobbyId = lobbyId
            )
        }
    }

    /** Jemand fragt nach öffentlichem Teilen — Peer ist nicht in der Leinwand. */
    fun onPublicShareAsk(
        context: Context,
        lobbyName: String,
        nickname: String,
        lobbyId: String
    ) {
        scope.launch {
            if (QuietHoursGate.isQuietNow()) return@launch
            if (LockDrawActivity.isCanvasForeground(lobbyId)) return@launch
            val who = nickname.ifBlank { "Jemand" }
            val lobby = lobbyName.ifBlank { "Lobby" }
            show(
                context = context.applicationContext,
                title = "Teilen",
                text = context.getString(R.string.public_ask_notify_fmt, who, lobby),
                notificationId = NOTIFY_PUBLIC_BASE + (lobbyId.hashCode() and 0xffff),
                lobbyId = lobbyId
            )
        }
    }

    /** 24h-Leinwand-Erinnerung — tippen öffnet Vollbild. */
    fun onCanvasMemory(
        context: Context,
        lobbyName: String,
        lobbyCode: String,
        imageUrl: String
    ) {
        scope.launch {
            if (QuietHoursGate.isQuietNow()) return@launch
            if (!prefsEnabled()) return@launch
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context,
                NOTIFY_MEMORY_BASE + (lobbyCode.hashCode() and 0xffff),
                Intent(context, com.luv.couple.lock.MemoryActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(com.luv.couple.lock.MemoryActivity.EXTRA_LOBBY_CODE, lobbyCode)
                    putExtra(com.luv.couple.lock.MemoryActivity.EXTRA_LOBBY_NAME, lobbyName)
                    putExtra(com.luv.couple.lock.MemoryActivity.EXTRA_IMAGE_URL, imageUrl)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Erinnerung")
                .setContentText("Eure Leinwand in „$lobbyName“ wartet als Erinnerung")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Lange niemand auf der Leinwand — hier ist ein Abbild zum Anschauen und Teilen. Nur 24 Stunden.")
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIFY_MEMORY_BASE + (lobbyCode.hashCode() and 0xffff), notification)
        }
    }

    /** Launcher-Badge-Zahl (Android zeigt .setNumber auf dem App-Icon). */
    fun updateAppBadge(context: Context, count: Int) {
        ensureChannel(context)
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (count <= 0) {
            mgr.cancel(NOTIFY_APP_BADGE)
            return
        }
        val open = PendingIntent.getActivity(
            context,
            NOTIFY_APP_BADGE,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_FROM_NOTIFICATION, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hinweise")
            .setContentText(
                if (count == 1) "1 neuer Hinweis" else "$count neue Hinweise"
            )
            .setNumber(count)
            .setContentIntent(open)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        mgr.notify(NOTIFY_APP_BADGE, notification)
    }

    /** Marktplatz: eigenes Angebot verkauft (außerhalb Ruhezeiten, wenn Benachrichtigungen an). */
    fun onMarketSale(context: Context, itemCount: Int, totalCoins: Int) {
        scope.launch {
            if (itemCount <= 0 || totalCoins <= 0) return@launch
            if (QuietHoursGate.isQuietNow()) return@launch
            if (!prefsEnabled()) return@launch
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context,
                NOTIFY_MARKET_SALE,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_FROM_NOTIFICATION, true)
                    putExtra(MainActivity.EXTRA_OPEN_MARKETPLACE, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = if (itemCount == 1) {
                "Dein Angebot wurde verkauft · $totalCoins Coins abholen"
            } else {
                "$itemCount Angebote verkauft · $totalCoins Coins abholen"
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Marktplatz")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setNumber(itemCount)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIFY_MARKET_SALE, notification)
        }
    }

    fun onFriendRequest(context: Context, count: Int) {
        scope.launch {
            if (count <= 0) return@launch
            if (QuietHoursGate.isQuietNow()) return@launch
            if (!prefsEnabled()) return@launch
            ensureChannel(context)
            val text = if (count == 1) {
                "Neue Freundschaftsanfrage"
            } else {
                "$count neue Freundschaftsanfragen"
            }
            notifyOpenMain(
                context = context.applicationContext,
                notificationId = NOTIFY_FRIEND,
                title = "Sozial · Freunde",
                text = text,
                extras = {
                    putExtra(MainActivity.EXTRA_OPEN_SOZIAL, true)
                    putExtra(MainActivity.EXTRA_SOZIAL_TAB, 0)
                }
            )
        }
    }

    fun onMarriageProposal(context: Context, count: Int) {
        scope.launch {
            if (count <= 0) return@launch
            if (QuietHoursGate.isQuietNow()) return@launch
            if (!prefsEnabled()) return@launch
            ensureChannel(context)
            val text = if (count == 1) {
                "Neuer Heiratsantrag"
            } else {
                "$count neue Heiratsanträge"
            }
            notifyOpenMain(
                context = context.applicationContext,
                notificationId = NOTIFY_MARRIAGE,
                title = "Sozial · Heirat",
                text = text,
                extras = {
                    putExtra(MainActivity.EXTRA_OPEN_SOZIAL, true)
                    putExtra(MainActivity.EXTRA_SOZIAL_TAB, 0)
                }
            )
        }
    }

    fun onLobbyInvite(context: Context, count: Int) {
        scope.launch {
            if (count <= 0) return@launch
            if (QuietHoursGate.isQuietNow()) return@launch
            if (!prefsEnabled()) return@launch
            ensureChannel(context)
            val text = if (count == 1) {
                "Neue Lobby-Einladung"
            } else {
                "$count neue Lobby-Einladungen"
            }
            notifyOpenMain(
                context = context.applicationContext,
                notificationId = NOTIFY_LOBBY_INVITE,
                title = "Sozial · Freunde",
                text = text,
                extras = {
                    putExtra(MainActivity.EXTRA_OPEN_SOZIAL, true)
                    putExtra(MainActivity.EXTRA_SOZIAL_TAB, 0)
                }
            )
        }
    }

    fun onAchievementsReady(context: Context) {
        scope.launch {
            if (QuietHoursGate.isQuietNow()) return@launch
            if (!prefsEnabled()) return@launch
            ensureChannel(context)
            notifyOpenMain(
                context = context.applicationContext,
                notificationId = NOTIFY_ACHIEVEMENT,
                title = "Sozial · Erfolge",
                text = "Du kannst Belohnungen abholen",
                extras = {
                    putExtra(MainActivity.EXTRA_OPEN_SOZIAL, true)
                    putExtra(MainActivity.EXTRA_SOZIAL_TAB, 2)
                }
            )
        }
    }

    fun onInventoryNew(context: Context, count: Int) {
        scope.launch {
            if (count <= 0) return@launch
            if (QuietHoursGate.isQuietNow()) return@launch
            if (!prefsEnabled()) return@launch
            ensureChannel(context)
            notifyOpenMain(
                context = context.applicationContext,
                notificationId = NOTIFY_INVENTORY,
                title = "Inventar",
                text = if (count == 1) "Neues Item im Inventar" else "$count neue Items im Inventar",
                extras = {
                    putExtra(MainActivity.EXTRA_OPEN_INVENTAR, true)
                }
            )
        }
    }

    /** Einmal pro Kalendertag, wenn der Tagesbonus frisch gutgeschrieben wurde. */
    fun onDailyCoins(context: Context, amount: Int, dayKey: String) {
        scope.launch {
            if (amount <= 0) return@launch
            if (QuietHoursGate.isQuietNow()) return@launch
            if (!dailyNotifiedDays.add(dayKey)) return@launch
            ensureChannel(context)
            notifyOpenMain(
                context = context.applicationContext,
                notificationId = NOTIFY_DAILY,
                title = "Tagesbonus",
                text = context.getString(R.string.daily_coins_text_fmt, amount),
                extras = {
                    putExtra(MainActivity.EXTRA_OPEN_HOME, true)
                }
            )
        }
    }

    private fun notifyOpenMain(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        extras: Intent.() -> Unit = {}
    ) {
        val open = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_FROM_NOTIFICATION, true)
                extras()
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification)
    }

    private suspend fun prefsEnabled(): Boolean =
        runCatching { LuvApp.instance.prefs.isPartnerDrawNotifyEnabled() }.getOrDefault(true)

    private fun softPulse(context: Context) {
        if (QuietHoursGate.isQuietNow()) return
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 22, 40, 28), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 22, 40, 28), -1)
            }
        }
    }

    private fun showLiveStroke(
        context: Context,
        lobbyName: String,
        nickname: String,
        lobbyId: String,
        preview: Bitmap?,
        rich: Boolean,
        wake: Boolean
    ) {
        ensureChannel(context)
        val notificationId = NOTIFY_DRAW_BASE + (lobbyId.hashCode() and 0xffff)
        val openIntent = Intent(context, LockDrawActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobbyId)
            if (wake) putExtra(LockDrawActivity.EXTRA_WAKE_NEAR, true)
        }
        val open = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (rich) "$nickname malt gerade" else context.getString(R.string.partner_draw_title)
        val text = if (rich) {
            "in „$lobbyName“ — tippen und dabei sein"
        } else {
            context.getString(R.string.partner_draw_text_fmt, lobbyName, nickname)
        }
        val channel = if (rich) LIVE_CHANNEL_ID else CHANNEL_ID
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(!rich)
            .setColor(0xFFFF6B8A.toInt())

        if (preview != null) {
            builder.setLargeIcon(preview)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(preview)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(text)
                    .setBigContentTitle(title)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }

        if (wake) {
            builder.setFullScreenIntent(open, true)
            val nowWake = SystemClock.elapsedRealtime()
            val lastWake = lastWakeByLobby[lobbyId] ?: 0L
            if (lastWake <= 0L || nowWake - lastWake >= DRAW_WAKE_INTERVAL_MS) {
                lastWakeByLobby[lobbyId] = nowWake
                // Sofort wecken — LockDrawActivity hat turnScreenOn
                runCatching { context.startActivity(openIntent) }
            }
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, builder.build())
    }

    private fun show(
        context: Context,
        title: String,
        text: String,
        notificationId: Int,
        lobbyId: String?
    ) {
        ensureChannel(context)
        val open = if (lobbyId != null) {
            PendingIntent.getActivity(
                context,
                notificationId,
                Intent(context, LockDrawActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobbyId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                context,
                notificationId,
                Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_FROM_NOTIFICATION, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification)
    }
}
