package com.luv.couple.lock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.Log
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.Stroke
import com.luv.couple.data.StrokePoint
import com.luv.couple.net.AccountSession
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairMessage
import com.luv.couple.net.PairProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

data class CanvasSticker(
    val id: String,
    val emoji: String,
    val x: Float,
    val y: Float,
    val isLocal: Boolean = false
)

object CanvasStore {
    private sealed class LocalUndo {
        data class Stroke(val id: String) : LocalUndo()
        data class Sticker(val id: String) : LocalUndo()
    }

    private data class LobbyCanvas(
        val strokes: CopyOnWriteArrayList<Stroke> = CopyOnWriteArrayList(),
        val stickers: CopyOnWriteArrayList<CanvasSticker> = CopyOnWriteArrayList(),
        val localStrokeIds: CopyOnWriteArrayList<String> = CopyOnWriteArrayList(),
        val localUndo: CopyOnWriteArrayList<LocalUndo> = CopyOnWriteArrayList(),
        val revision: MutableStateFlow<Long> = MutableStateFlow(0L)
    )

    private val canvases = ConcurrentHashMap<String, LobbyCanvas>()
    private val lastStrokeAt = ConcurrentHashMap<String, MutableStateFlow<Long>>()
    private val lastStrokeBy = ConcurrentHashMap<String, MutableStateFlow<String?>>()
    private val historyBackup = ConcurrentHashMap<String, List<Stroke>>()
    private val persistJobs = ConcurrentHashMap<String, Job>()
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    /**
     * Eigene Striche auf [colorIndex] bringen.
     * [broadcast]=false nur lokal (z. B. beim Öffnen der Leinwand), sonst auch an Peers.
     */
    fun recolorOwnStrokes(colorIndex: Int, lobbyId: String? = null, broadcast: Boolean = true) {
        val id = resolveLobbyId(lobbyId) ?: return
        val safe = colorIndex.coerceIn(0, PeerPalette.COLOR_COUNT - 1)
        cachedColorIndex = safe
        val nick = cachedNickname
        val c = canvas(id)
        var changed = false
        val updated = c.strokes.map { stroke ->
            if (stroke.isLocal || stroke.nickname.equals(nick, ignoreCase = true)) {
                if (stroke.colorIndex != safe) changed = true
                stroke.copy(colorIndex = safe, isLocal = stroke.isLocal)
            } else {
                stroke
            }
        }
        if (changed) {
            c.strokes.clear()
            c.strokes.addAll(updated)
            bump(id)
        }
        if (broadcast && ::appContext.isInitialized) {
            PairConnectionService.sendRecolor(appContext, nick, safe, id)
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
        canvases.getOrPut(lobbyId) {
            LobbyCanvas().also { loadDiskInto(lobbyId, it) }
        }

    fun snapshot(lobbyId: String? = null): List<Stroke> {
        val id = resolveLobbyId(lobbyId) ?: return emptyList()
        return canvas(id).strokes.toList()
    }

    /**
     * Server-History nach Connect. Bei leerem Server und lokaler Sicherung
     * werden eigene Striche wiederhergestellt und hochgeladen.
     */
    fun applyServerHistory(
        lobbyId: String,
        incoming: List<Stroke>,
        replace: Boolean,
        done: Boolean
    ) {
        val c = canvas(lobbyId)
        val nick = cachedNickname
        if (replace) {
            if (incoming.isEmpty()) {
                historyBackup[lobbyId] = c.strokes.toList()
            } else {
                historyBackup.remove(lobbyId)
            }
            c.strokes.clear()
            c.localStrokeIds.clear()
        }
        for (raw in incoming) {
            if (c.strokes.any { it.id == raw.id }) continue
            val mine = !nick.isNullOrBlank() && raw.nickname.equals(nick, ignoreCase = true)
            val stroke = raw.copy(isLocal = mine)
            c.strokes.add(stroke)
            if (mine) c.localStrokeIds.add(stroke.id)
            touchStroke(lobbyId, stroke.nickname)
        }
        if (done) {
            val backup = historyBackup.remove(lobbyId)
            if (c.strokes.isEmpty() && !backup.isNullOrEmpty()) {
                for (s in backup) {
                    if (c.strokes.any { it.id == s.id }) continue
                    val mine = s.isLocal ||
                        (!nick.isNullOrBlank() && s.nickname.equals(nick, ignoreCase = true))
                    val stroke = s.copy(isLocal = mine)
                    c.strokes.add(stroke)
                    if (mine) c.localStrokeIds.add(stroke.id)
                    if (::appContext.isInitialized) {
                        PairConnectionService.sendStroke(
                            appContext,
                            PairProtocol.encode(PairMessage.StrokeMsg(stroke)),
                            lobbyId
                        )
                    }
                }
            }
        }
        bump(lobbyId)
    }

    fun lastStrokeAt(lobbyId: String): StateFlow<Long> =
        lastStrokeAt.getOrPut(lobbyId) { MutableStateFlow(0L) }.asStateFlow()

    fun lastStrokeBy(lobbyId: String): StateFlow<String?> =
        lastStrokeBy.getOrPut(lobbyId) { MutableStateFlow(null) }.asStateFlow()

    fun lastStrokeAtValue(lobbyId: String?): Long {
        val id = resolveLobbyId(lobbyId) ?: return 0L
        return lastStrokeAt[id]?.value ?: 0L
    }

    fun lastStrokeByValue(lobbyId: String?): String? {
        val id = resolveLobbyId(lobbyId) ?: return null
        return lastStrokeBy[id]?.value
    }

    private fun touchStroke(lobbyId: String, nickname: String?) {
        lastStrokeAt.getOrPut(lobbyId) { MutableStateFlow(0L) }.value = System.currentTimeMillis()
        if (!nickname.isNullOrBlank()) {
            lastStrokeBy.getOrPut(lobbyId) { MutableStateFlow(null) }.value = nickname.trim()
        }
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
            authorId = AccountSession.account.value?.id?.takeIf { it.isNotBlank() }
        )
        val c = canvas(id)
        c.strokes.add(stroke)
        c.localStrokeIds.add(stroke.id)
        pushUndo(c, LocalUndo.Stroke(stroke.id))
        touchStroke(id, cachedNickname)
        bump(id)
        if (::appContext.isInitialized) {
            PairConnectionService.sendStroke(
                appContext,
                PairProtocol.encode(PairMessage.StrokeMsg(stroke)),
                id
            )
            // Widget-Update läuft debounced über bump() — nicht synchron auf dem UI-Thread
        }
    }

    fun snapshotStickers(lobbyId: String? = null): List<CanvasSticker> {
        val id = resolveLobbyId(lobbyId) ?: return emptyList()
        return canvas(id).stickers.toList()
    }

    fun addLocalSticker(
        emoji: String,
        x: Float,
        y: Float,
        lobbyId: String? = null,
        id: String = UUID.randomUUID().toString()
    ): CanvasSticker? {
        val lobby = resolveLobbyId(lobbyId) ?: return null
        val sticker = CanvasSticker(
            id = id,
            emoji = emoji.take(8),
            x = x.coerceIn(0.05f, 0.95f),
            y = y.coerceIn(0.05f, 0.95f),
            isLocal = true
        )
        val c = canvas(lobby)
        c.stickers.removeAll { it.id == sticker.id }
        c.stickers.add(sticker)
        pushUndo(c, LocalUndo.Sticker(sticker.id))
        bump(lobby)
        if (::appContext.isInitialized) {
            PairConnectionService.sendStickerPlace(
                appContext,
                sticker.id,
                sticker.emoji,
                sticker.x,
                sticker.y,
                lobby
            )
        }
        return sticker
    }

    fun upsertRemoteSticker(sticker: CanvasSticker, lobbyId: String) {
        val c = canvas(lobbyId)
        c.stickers.removeAll { it.id == sticker.id }
        c.stickers.add(sticker.copy(isLocal = false))
        bump(lobbyId)
    }

    fun replaceStickers(lobbyId: String, stickers: List<CanvasSticker>) {
        val c = canvas(lobbyId)
        c.stickers.clear()
        c.stickers.addAll(stickers)
        c.localUndo.removeAll { it is LocalUndo.Sticker }
        bump(lobbyId)
    }

    fun removeStickerById(stickerId: String, lobbyId: String, broadcast: Boolean = false) {
        val c = canvas(lobbyId)
        val removed = c.stickers.removeAll { it.id == stickerId }
        c.localUndo.removeAll { it is LocalUndo.Sticker && it.id == stickerId }
        if (!removed) return
        bump(lobbyId)
        if (broadcast && ::appContext.isInitialized) {
            PairConnectionService.sendStickerRemove(appContext, stickerId, lobbyId)
        }
    }

    /** Schwamm: Emojis im Pinselradius entfernen. */
    fun eraseStickersAlong(
        brush: List<StrokePoint>,
        radius: Float = 0.05f,
        lobbyId: String? = null
    ): List<String> {
        if (brush.isEmpty()) return emptyList()
        val id = resolveLobbyId(lobbyId) ?: return emptyList()
        val c = canvas(id)
        val r2 = radius * radius
        val hit = c.stickers.filter { s ->
            brush.any { b ->
                val dx = s.x - b.x
                val dy = s.y - b.y
                dx * dx + dy * dy <= r2
            }
        }
        if (hit.isEmpty()) return emptyList()
        val ids = hit.map { it.id }
        c.stickers.removeAll { it.id in ids }
        c.localUndo.removeAll { it is LocalUndo.Sticker && it.id in ids }
        bump(id)
        if (::appContext.isInitialized) {
            ids.forEach { sid ->
                PairConnectionService.sendStickerRemove(appContext, sid, id)
            }
        }
        return ids
    }

    private fun pushUndo(c: LobbyCanvas, action: LocalUndo) {
        c.localUndo.add(action)
        while (c.localUndo.size > 200) c.localUndo.removeAt(0)
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
        touchStroke(lobbyId, stroke.nickname)
        bump(lobbyId)
    }

    /** Doppeltipp: letzten eigenen Strich ODER eigenes Emoji rückgängig. */
    fun undoLastLocalStroke(lobbyId: String? = null): Boolean {
        val id = resolveLobbyId(lobbyId) ?: return false
        val c = canvas(id)
        val action = if (c.localUndo.isNotEmpty()) {
            c.localUndo.removeAt(c.localUndo.lastIndex)
        } else {
            val strokeId = c.localStrokeIds.lastOrNull() ?: return false
            LocalUndo.Stroke(strokeId)
        }
        return when (action) {
            is LocalUndo.Stroke -> {
                c.localStrokeIds.remove(action.id)
                val removed = c.strokes.removeAll { it.id == action.id }
                if (!removed) return false
                bump(id)
                if (::appContext.isInitialized) {
                    PairConnectionService.sendUndo(appContext, action.id, id)
                }
                true
            }
            is LocalUndo.Sticker -> {
                val removed = c.stickers.removeAll { it.id == action.id }
                if (!removed) return false
                bump(id)
                if (::appContext.isInitialized) {
                    PairConnectionService.sendStickerRemove(appContext, action.id, id)
                }
                true
            }
        }
    }

    /**
     * Radiert nur Stellen entlang des Brush-Pfads (normalisierte 0..1).
     * Eigene Striche werden an getroffenen Stellen geteilt — nicht komplett gelöscht.
     */
    fun eraseLocalAlong(
        brush: List<StrokePoint>,
        radius: Float = 0.028f,
        lobbyId: String? = null
    ): Boolean {
        if (brush.isEmpty()) return false
        val id = resolveLobbyId(lobbyId) ?: return false
        val c = canvas(id)
        val nick = cachedNickname?.trim().orEmpty()
        val mine = c.strokes.filter { stroke ->
            stroke.isLocal ||
                stroke.id in c.localStrokeIds ||
                (nick.isNotBlank() && stroke.nickname.equals(nick, ignoreCase = true))
        }
        if (mine.isEmpty()) return false
        var changed = false
        for (stroke in mine) {
            val strokeRadius = radius + (stroke.width / 1100f).coerceIn(0.008f, 0.03f)
            val fragments = splitStrokeAwayFromBrush(stroke.points, brush, strokeRadius)
            val unchanged = fragments.size == 1 && fragments[0].size == stroke.points.size &&
                fragments[0].zip(stroke.points).all { (a, b) -> a.x == b.x && a.y == b.y }
            if (unchanged) continue
            c.strokes.removeAll { it.id == stroke.id }
            c.localStrokeIds.remove(stroke.id)
            if (::appContext.isInitialized) {
                PairConnectionService.sendUndo(appContext, stroke.id, id)
            }
            for (frag in fragments) {
                if (frag.isEmpty()) continue
                val points = if (frag.size == 1) microDotAround(frag[0]) else frag
                val neu = Stroke(
                    id = UUID.randomUUID().toString(),
                    points = points,
                    width = stroke.width,
                    isLocal = true,
                    nickname = stroke.nickname ?: cachedNickname,
                    colorIndex = stroke.colorIndex,
                    authorId = stroke.authorId
                        ?: AccountSession.account.value?.id?.takeIf { it.isNotBlank() },
                    gender = stroke.gender
                )
                c.strokes.add(neu)
                c.localStrokeIds.add(neu.id)
                if (::appContext.isInitialized) {
                    PairConnectionService.sendStroke(
                        appContext,
                        PairProtocol.encode(PairMessage.StrokeMsg(neu)),
                        id
                    )
                }
            }
            changed = true
        }
        if (changed) bump(id)
        return changed
    }

    private fun microDotAround(center: StrokePoint): List<StrokePoint> {
        val r = 0.005f
        return buildList {
            for (i in 0..10) {
                val a = (Math.PI * 2.0 * i) / 10.0
                add(
                    StrokePoint(
                        x = (center.x + r * kotlin.math.cos(a).toFloat()).coerceIn(0f, 1f),
                        y = (center.y + r * kotlin.math.sin(a).toFloat()).coerceIn(0f, 1f)
                    )
                )
            }
        }
    }

    private fun splitStrokeAwayFromBrush(
        points: List<StrokePoint>,
        brush: List<StrokePoint>,
        radius: Float
    ): List<List<StrokePoint>> {
        if (points.isEmpty()) return emptyList()
        val r2 = radius * radius
        val keep = BooleanArray(points.size) { i ->
            val p = points[i]
            brush.none { b ->
                val dx = p.x - b.x
                val dy = p.y - b.y
                dx * dx + dy * dy <= r2
            }
        }
        val breakAfter = BooleanArray(points.size)
        for (i in 0 until points.lastIndex) {
            if (!keep[i] || !keep[i + 1]) continue
            val a = points[i]
            val b = points[i + 1]
            if (brush.any { distPointToSegment2(it, a, b) <= r2 }) {
                breakAfter[i] = true
            }
        }
        val fragments = mutableListOf<List<StrokePoint>>()
        var current = mutableListOf<StrokePoint>()
        for (i in points.indices) {
            if (keep[i]) {
                current.add(points[i])
                if (breakAfter[i]) {
                    if (current.isNotEmpty()) {
                        fragments.add(current.toList())
                        current = mutableListOf()
                    }
                }
            } else if (current.isNotEmpty()) {
                fragments.add(current.toList())
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) fragments.add(current.toList())
        return fragments.filter { it.isNotEmpty() }
    }

    private fun distPointToSegment2(p: StrokePoint, a: StrokePoint, b: StrokePoint): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val len2 = abx * abx + aby * aby
        if (len2 <= 1e-12f) {
            val dx = p.x - a.x
            val dy = p.y - a.y
            return dx * dx + dy * dy
        }
        val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / len2).coerceIn(0f, 1f)
        val cx = a.x + abx * t
        val cy = a.y + aby * t
        val dx = p.x - cx
        val dy = p.y - cy
        return dx * dx + dy * dy
    }

    fun removeStrokeById(strokeId: String, lobbyId: String) {
        val c = canvas(lobbyId)
        val removed = c.strokes.removeAll { it.id == strokeId }
        c.localStrokeIds.remove(strokeId)
        if (removed) bump(lobbyId)
    }

    fun clear(
        localOnly: Boolean = false,
        notifyPeer: Boolean = false,
        lobbyId: String? = null
    ) {
        val id = resolveLobbyId(lobbyId) ?: return
        val c = canvas(id)
        c.strokes.clear()
        c.stickers.clear()
        c.localStrokeIds.clear()
        c.localUndo.clear()
        historyBackup.remove(id)
        bump(id)
        if (notifyPeer && !localOnly && ::appContext.isInitialized) {
            PairConnectionService.sendClear(appContext, id)
        }
    }

    fun clearLobby(lobbyId: String) {
        canvases.remove(lobbyId)
        historyBackup.remove(lobbyId)
        persistJobs.remove(lobbyId)?.cancel()
        runCatching { diskFile(lobbyId).delete() }
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
        val room = id?.let { canvas(it) }
        val strokes = room?.strokes?.toList().orEmpty()
        val stickers = room?.stickers?.toList().orEmpty()
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
        if (stickers.isNotEmpty()) {
            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = min(width, height) * 0.075f
                typeface = Typeface.DEFAULT
            }
            val fm = emojiPaint.fontMetrics
            val baselinePad = (fm.bottom + fm.top) / 2f
            stickers.forEach { s ->
                canvas.drawText(
                    s.emoji,
                    s.x * width,
                    s.y * height - baselinePad,
                    emojiPaint
                )
            }
        }
        return bitmap
    }

    fun backgroundFor(colorIndex: Int): Int = PeerPalette.lockBackground(colorIndex)

    private fun bump(lobbyId: String) {
        val c = canvas(lobbyId)
        c.revision.value = c.revision.value + 1
        bumpGlobal()
        schedulePersist(lobbyId)
        if (::appContext.isInitialized) {
            LockScreenWidgetProvider.requestUpdate(appContext)
        }
    }

    private fun bumpGlobal() {
        _revision.value = _revision.value + 1
    }

    private fun diskDir(): File {
        val dir = File(appContext.filesDir, "canvas_strokes")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun diskFile(lobbyId: String): File {
        val safe = lobbyId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(diskDir(), "$safe.json")
    }

    private fun schedulePersist(lobbyId: String) {
        if (!::appContext.isInitialized) return
        persistJobs[lobbyId]?.cancel()
        persistJobs[lobbyId] = persistScope.launch {
            delay(450)
            persistToDisk(lobbyId)
        }
    }

    private fun persistToDisk(lobbyId: String) {
        if (!::appContext.isInitialized) return
        val strokes = canvases[lobbyId]?.strokes?.toList() ?: emptyList()
        runCatching {
            val arr = JSONArray()
            strokes.forEach { stroke ->
                val points = JSONArray()
                stroke.points.forEach { p ->
                    points.put(JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble()))
                }
                arr.put(
                    JSONObject()
                        .put("id", stroke.id)
                        .put("width", stroke.width.toDouble())
                        .put("nickname", stroke.nickname)
                        .put("colorIndex", stroke.colorIndex)
                        .put("authorId", stroke.authorId)
                        .put("isLocal", stroke.isLocal)
                        .put("points", points)
                )
            }
            val file = diskFile(lobbyId)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure {
            Log.w("CanvasStore", "persist failed lobby=$lobbyId: ${it.message}")
        }
    }

    private fun loadDiskInto(lobbyId: String, target: LobbyCanvas) {
        if (!::appContext.isInitialized) return
        if (target.strokes.isNotEmpty()) return
        val file = diskFile(lobbyId)
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            val nick = cachedNickname
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pointsJson = o.getJSONArray("points")
                val points = buildList {
                    for (pi in 0 until pointsJson.length()) {
                        val p = pointsJson.getJSONObject(pi)
                        add(
                            StrokePoint(
                                x = p.getDouble("x").toFloat(),
                                y = p.getDouble("y").toFloat()
                            )
                        )
                    }
                }
                if (points.size < 2) continue
                val nickname = o.optString("nickname").takeIf { it.isNotBlank() && it != "null" }
                val mine = o.optBoolean("isLocal", false) ||
                    (!nick.isNullOrBlank() && nickname.equals(nick, ignoreCase = true))
                val stroke = Stroke(
                    id = o.getString("id"),
                    points = points,
                    width = o.optDouble("width", 18.0).toFloat(),
                    isLocal = mine,
                    nickname = nickname,
                    colorIndex = o.optInt("colorIndex", 0),
                    authorId = o.optString("authorId").takeIf { it.isNotBlank() && it != "null" }
                )
                if (target.strokes.any { it.id == stroke.id }) continue
                target.strokes.add(stroke)
                if (mine) target.localStrokeIds.add(stroke.id)
            }
        }.onFailure {
            Log.w("CanvasStore", "load failed lobby=$lobbyId: ${it.message}")
        }
    }
}
