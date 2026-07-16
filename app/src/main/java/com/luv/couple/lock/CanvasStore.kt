package com.luv.couple.lock

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.Stroke
import com.luv.couple.data.StrokePoint
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairMessage
import com.luv.couple.net.PairProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object CanvasStore {
    private data class LobbyCanvas(
        val strokes: CopyOnWriteArrayList<Stroke> = CopyOnWriteArrayList(),
        val localStrokeIds: CopyOnWriteArrayList<String> = CopyOnWriteArrayList(),
        val revision: MutableStateFlow<Long> = MutableStateFlow(0L)
    )

    private val canvases = ConcurrentHashMap<String, LobbyCanvas>()
    private val _activeLobbyId = MutableStateFlow<String?>(null)
    val activeLobbyId: StateFlow<String?> = _activeLobbyId.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    @Volatile var cachedNickname: String? = null
        private set
    @Volatile var cachedColorIndex: Int = 0
        private set
    @Volatile private var knownLobbyIds: Set<String> = emptySet()

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun updateProfile(nickname: String?, colorIndex: Int) {
        cachedNickname = nickname
        cachedColorIndex = colorIndex.coerceIn(0, PeerPalette.COLOR_COUNT - 1)
    }

    /** Eigene Striche lokal umfärben und Sync an Peers senden. */
    fun recolorOwnStrokes(colorIndex: Int, lobbyId: String? = null) {
        val id = resolveLobbyId(lobbyId) ?: return
        val safe = colorIndex.coerceIn(0, PeerPalette.COLOR_COUNT - 1)
        cachedColorIndex = safe
        val nick = cachedNickname
        val c = canvas(id)
        val updated = c.strokes.map { stroke ->
            if (stroke.isLocal || stroke.nickname.equals(nick, ignoreCase = true)) {
                stroke.copy(colorIndex = safe, isLocal = stroke.isLocal)
            } else {
                stroke
            }
        }
        c.strokes.clear()
        c.strokes.addAll(updated)
        bump(id)
        if (::appContext.isInitialized) {
            PairConnectionService.sendRecolor(appContext, nick, safe, id)
            LockScreenWidgetProvider.requestUpdate(appContext)
        }
    }

    /** Fremde Umfärbung anwenden (nach Nickname). */
    fun recolorByNickname(nickname: String?, colorIndex: Int, lobbyId: String) {
        if (nickname.isNullOrBlank()) return
        val safe = colorIndex.coerceIn(0, PeerPalette.COLOR_COUNT - 1)
        val c = canvas(lobbyId)
        var changed = false
        val updated = c.strokes.map { stroke ->
            if (!stroke.isLocal && stroke.nickname.equals(nickname, ignoreCase = true)) {
                changed = true
                stroke.copy(colorIndex = safe)
            } else {
                stroke
            }
        }
        if (!changed) return
        c.strokes.clear()
        c.strokes.addAll(updated)
        bump(lobbyId)
        if (::appContext.isInitialized) {
            LockScreenWidgetProvider.requestUpdate(appContext)
        }
    }

    fun updateKnownLobbies(ids: Collection<String>) {
        knownLobbyIds = ids.toSet()
    }

    fun setActiveLobby(lobbyId: String?) {
        if (_activeLobbyId.value == lobbyId) return
        _activeLobbyId.value = lobbyId
    }

    fun resolveLobbyId(lobbyId: String? = null): String? =
        lobbyId ?: _activeLobbyId.value

    private fun canvas(lobbyId: String): LobbyCanvas =
        canvases.getOrPut(lobbyId) { LobbyCanvas() }

    fun snapshot(lobbyId: String? = null): List<Stroke> {
        val id = resolveLobbyId(lobbyId) ?: return emptyList()
        return canvas(id).strokes.toList()
    }

    fun addLocalStroke(
        points: List<StrokePoint>,
        width: Float = 18f,
        lobbyId: String? = null
    ) {
        if (points.size < 2) return
        val id = resolveLobbyId(lobbyId) ?: return
        val stroke = Stroke(
            id = UUID.randomUUID().toString(),
            points = points,
            width = width,
            isLocal = true,
            nickname = cachedNickname,
            colorIndex = cachedColorIndex,
            authorId = "local"
        )
        val c = canvas(id)
        c.strokes.add(stroke)
        c.localStrokeIds.add(stroke.id)
        bump(id)
        if (::appContext.isInitialized) {
            PairConnectionService.sendStroke(
                appContext,
                PairProtocol.encode(PairMessage.StrokeMsg(stroke)),
                id
            )
            LockScreenWidgetProvider.requestUpdate(appContext)
        }
    }

    fun addLocalDot(x: Float, y: Float, lobbyId: String? = null) {
        val r = 0.006f
        val points = buildList {
            for (i in 0..10) {
                val a = (Math.PI * 2.0 * i) / 10.0
                add(
                    StrokePoint(
                        x = (x + r * kotlin.math.cos(a)).toFloat().coerceIn(0f, 1f),
                        y = (y + r * kotlin.math.sin(a)).toFloat().coerceIn(0f, 1f)
                    )
                )
            }
        }
        addLocalStroke(points, width = 16f, lobbyId = lobbyId)
    }

    fun addRemoteStroke(stroke: Stroke, lobbyId: String) {
        val c = canvas(lobbyId)
        if (c.strokes.any { it.id == stroke.id }) return
        c.strokes.add(stroke.copy(isLocal = false))
        bump(lobbyId)
        if (::appContext.isInitialized) {
            LockScreenWidgetProvider.requestUpdate(appContext)
        }
    }

    fun undoLastLocalStroke(lobbyId: String? = null): Boolean {
        val id = resolveLobbyId(lobbyId) ?: return false
        val c = canvas(id)
        val strokeId = c.localStrokeIds.lastOrNull() ?: return false
        c.localStrokeIds.remove(strokeId)
        c.strokes.removeAll { it.id == strokeId }
        bump(id)
        if (::appContext.isInitialized) {
            PairConnectionService.sendUndo(appContext, strokeId, id)
            LockScreenWidgetProvider.requestUpdate(appContext)
        }
        return true
    }

    fun removeStrokeById(strokeId: String, lobbyId: String) {
        val c = canvas(lobbyId)
        val removed = c.strokes.removeAll { it.id == strokeId }
        c.localStrokeIds.remove(strokeId)
        if (removed) {
            bump(lobbyId)
            if (::appContext.isInitialized) {
                LockScreenWidgetProvider.requestUpdate(appContext)
            }
        }
    }

    fun clear(
        localOnly: Boolean = false,
        notifyPeer: Boolean = false,
        lobbyId: String? = null
    ) {
        val id = resolveLobbyId(lobbyId) ?: return
        val c = canvas(id)
        c.strokes.clear()
        c.localStrokeIds.clear()
        bump(id)
        if (notifyPeer && !localOnly && ::appContext.isInitialized) {
            PairConnectionService.sendClear(appContext, id)
        }
        if (::appContext.isInitialized) {
            LockScreenWidgetProvider.requestUpdate(appContext)
        }
    }

    fun clearLobby(lobbyId: String) {
        canvases.remove(lobbyId)
        bumpGlobal()
    }

    fun clearAll(notifyPeer: Boolean = false) {
        val ids = knownLobbyIds.ifEmpty { canvases.keys.toSet() }
        ids.forEach { clear(notifyPeer = notifyPeer, lobbyId = it) }
    }

    fun strokeColor(stroke: Stroke): Int {
        if (stroke.gender != null && stroke.nickname == null) {
            return when (stroke.gender) {
                "MALE" -> 0xFFFFE8F6.toInt()
                "FEMALE" -> 0xFFE8F9FF.toInt()
                else -> PeerPalette.strokeColor(stroke.colorIndex)
            }
        }
        return PeerPalette.strokeColor(stroke.colorIndex)
    }

    fun renderBitmap(width: Int, height: Int, background: Int, lobbyId: String? = null): Bitmap {
        val id = resolveLobbyId(lobbyId)
        val strokes = if (id == null) emptyList() else canvas(id).strokes.toList()
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            paint.color = strokeColor(stroke)
            paint.strokeWidth = stroke.width
            val path = Path()
            val first = stroke.points.first()
            path.moveTo(first.x * width, first.y * height)
            stroke.points.drop(1).forEach { point ->
                path.lineTo(point.x * width, point.y * height)
            }
            canvas.drawPath(path, paint)
        }
        return bitmap
    }

    fun backgroundFor(colorIndex: Int): Int = PeerPalette.lockBackground(colorIndex)

    private fun bump(lobbyId: String) {
        val c = canvas(lobbyId)
        c.revision.value = c.revision.value + 1
        bumpGlobal()
        if (::appContext.isInitialized) {
            appContext.sendBroadcast(
                Intent(LockScreenWidgetProvider.ACTION_WIDGET_REFRESH)
                    .setPackage(appContext.packageName)
                    .putExtra(LockScreenWidgetProvider.EXTRA_LOBBY_ID, lobbyId)
            )
        }
    }

    private fun bumpGlobal() {
        _revision.value = _revision.value + 1
    }
}
