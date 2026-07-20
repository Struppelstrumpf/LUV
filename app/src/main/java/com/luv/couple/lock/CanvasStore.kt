package com.luv.couple.lock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.Stroke
import com.luv.couple.data.StrokePoint
import com.luv.couple.net.AccountSession
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairMessage
import com.luv.couple.net.PairProtocol
import com.luv.couple.net.PairSessionState
import com.luv.couple.ui.ItemImageCache
import com.luv.couple.ui.clipItemId
import com.luv.couple.ui.isImagePetId
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

object CanvasStore {
    /**
     * Strichdicke wird relativ zur kürzeren Leinwandseite gespeichert
     * (Einheit: Pixel bei Referenzbreite [WIDTH_REF]), damit Sync, Screenshots
     * und Veröffentlichungen auf jedem Display gleich aussehen.
     */
    const val WIDTH_REF = 1000f
    /** Anteil der Leinwand für Vorlagen-Stempel. */
    const val TEMPLATE_PLACE_FRAC = 0.92f

    /** Kanonisches Hochformat für Vorlagen (wie Preview/Editor). */
    const val TEMPLATE_CANVAS_AR = 9f / 16f

    /**
     * Zeichnet eine 9:16- bzw. Quadrat-Fläche zentriert in [boxW]×[boxH] ein
     * (Letterbox — keine Verzerrung).
     * @return Triple(drawW, drawH, Offset(ox, oy))
     */
    fun templateFitRect(
        boxW: Float,
        boxH: Float,
        coordSpace: String
    ): Triple<Float, Float, android.graphics.PointF> {
        val w = boxW.coerceAtLeast(1f)
        val h = boxH.coerceAtLeast(1f)
        return if (coordSpace == "square") {
            val side = min(w, h)
            Triple(side, side, android.graphics.PointF((w - side) / 2f, (h - side) / 2f))
        } else {
            val target = TEMPLATE_CANVAS_AR
            val boxAr = w / h
            if (boxAr > target) {
                val drawH = h
                val drawW = h * target
                Triple(drawW, drawH, android.graphics.PointF((w - drawW) / 2f, 0f))
            } else {
                val drawW = w
                val drawH = w / target
                Triple(drawW, drawH, android.graphics.PointF(0f, (h - drawH) / 2f))
            }
        }
    }

    /** Koordinatenraum einer Vorlage / platzierten Vorlage. */
    fun templateCoordSpace(parts: List<com.luv.couple.data.TemplateStrokePart>, hint: String? = null): String {
        val h = hint?.trim()?.lowercase()
        if (h == "canvas" || h == "square") return h
        val pts = parts.flatMap { it.points }
        if (pts.size < 2) return "canvas"
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val bw = (maxX - minX).coerceAtLeast(0.01f)
        val bh = (maxY - minY).coerceAtLeast(0.01f)
        // Nur klar flache Legacy-Zeichnungen → Quadrat; sonst immer 9:16-Canvas
        return if (bh / bw < 0.85f) "square" else "canvas"
    }

    /**
     * Stempelgröße in der Leinwand — immer gleiche AR für alle Geräte
     * (9:16 letterboxed bzw. Quadrat), dann × Fraktion × Scale.
     */
    fun templateStampSize(
        viewW: Float,
        viewH: Float,
        scale: Float,
        coordSpace: String
    ): Pair<Float, Float> {
        val s = scale.coerceIn(0.2f, 4f)
        val (fitW, fitH, _) = templateFitRect(viewW, viewH, coordSpace)
        return fitW * TEMPLATE_PLACE_FRAC * s to fitH * TEMPLATE_PLACE_FRAC * s
    }

    private sealed class LocalUndo {
        data class Stroke(val id: String) : LocalUndo()
    }

    private data class LobbyCanvas(
        val strokes: CopyOnWriteArrayList<Stroke> = CopyOnWriteArrayList(),
        val localStrokeIds: CopyOnWriteArrayList<String> = CopyOnWriteArrayList(),
        val localUndo: CopyOnWriteArrayList<LocalUndo> = CopyOnWriteArrayList(),
        val revision: MutableStateFlow<Long> = MutableStateFlow(0L)
    )

    private val canvases = ConcurrentHashMap<String, LobbyCanvas>()
    private val lastStrokeAt = ConcurrentHashMap<String, MutableStateFlow<Long>>()
    private val lastStrokeBy = ConcurrentHashMap<String, MutableStateFlow<String?>>()
    private val lastOtherStrokeAt = ConcurrentHashMap<String, MutableStateFlow<Long>>()
    private val lastOtherStrokeBy = ConcurrentHashMap<String, MutableStateFlow<String?>>()
    private val historyBackup = ConcurrentHashMap<String, List<Stroke>>()
    /** Chunks bis done zusammenfügen — kein Zwischen-Clear/Flash. */
    private val pendingHistory = ConcurrentHashMap<String, MutableList<Stroke>>()
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
    @Volatile var cachedBrushWidth: Float = 18f
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

    fun updateBrushWidth(width: Float) {
        cachedBrushWidth = width.coerceIn(6f, 40f)
    }

    /** View-Pixel → speicher-/sync-fähige Referenzdicke. */
    fun toStoredWidth(widthPx: Float, shortSidePx: Float): Float {
        val short = shortSidePx.coerceAtLeast(1f)
        return (widthPx / short * WIDTH_REF).coerceIn(3f, 48f)
    }

    /** Gespeicherte Referenzdicke → Pixel auf aktueller Zeichenfläche. */
    fun strokeWidthPx(stroke: Stroke, shortSidePx: Float): Float {
        if (stroke.isEmoji || stroke.isTemplate) return 0f
        val short = shortSidePx.coerceAtLeast(1f)
        return (stroke.width / WIDTH_REF * short).coerceIn(1f, short * 0.25f)
    }

    /** Allein in der Lobby (≤1 Verbundener) → Mehrfarben ohne Umfärben alter Striche. */
    fun isSoloLobby(lobbyId: String?): Boolean {
        val id = resolveLobbyId(lobbyId) ?: return true
        return PairSessionState.peerCount(id).value <= 1
    }

    /**
     * Eigene Striche auf [colorIndex] bringen — nur im Mehrpersonen-Modus und nur
     * nicht-gesperrte Striche (Solo-Zeichnungen behalten ihre Farben).
     */
    fun recolorOwnStrokes(colorIndex: Int, lobbyId: String? = null, broadcast: Boolean = true) {
        val id = resolveLobbyId(lobbyId) ?: return
        val safe = colorIndex.coerceIn(0, PeerPalette.COLOR_COUNT - 1)
        cachedColorIndex = safe
        val nick = cachedNickname
        // Immer live an Peers senden (Avatar/Legende), auch wenn keine Striche umgefärbt werden
        if (broadcast && ::appContext.isInitialized) {
            PairConnectionService.sendRecolor(appContext, nick, safe, id)
        }
        if (isSoloLobby(id)) return
        val c = canvas(id)
        var changed = false
        val updated = c.strokes.map { stroke ->
            val mine = stroke.isLocal || stroke.nickname.equals(nick, ignoreCase = true)
            if (mine && !stroke.colorLocked && stroke.colorIndex != safe) {
                changed = true
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
    }

    /** Fremde Umfärbung anwenden (nach Nickname) — colorLocked bleibt unangetastet. */
    fun recolorByNickname(nickname: String?, colorIndex: Int, lobbyId: String) {
        if (nickname.isNullOrBlank()) return
        val safe = colorIndex.coerceIn(0, PeerPalette.COLOR_COUNT - 1)
        val c = canvas(lobbyId)
        var changed = false
        val updated = c.strokes.map { stroke ->
            if (
                !stroke.isLocal &&
                !stroke.colorLocked &&
                stroke.nickname.equals(nickname, ignoreCase = true) &&
                stroke.colorIndex != safe
            ) {
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

    /** Beim Wechsel Solo → Mehrpersonen: bisherige eigene Striche farblich einfrieren. */
    fun lockOwnStrokeColors(lobbyId: String?) {
        val id = resolveLobbyId(lobbyId) ?: return
        val nick = cachedNickname
        val c = canvas(id)
        var changed = false
        val updated = c.strokes.map { stroke ->
            val mine = stroke.isLocal || stroke.nickname.equals(nick, ignoreCase = true)
            if (mine && !stroke.colorLocked) {
                changed = true
                stroke.copy(colorLocked = true)
            } else {
                stroke
            }
        }
        if (!changed) return
        c.strokes.clear()
        c.strokes.addAll(updated)
        bump(id)
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
     * Tutorial-Skizze vor dem WS-Connect ablegen.
     * Leere Server-History stellt die Striche wieder her und lädt sie hoch.
     */
    fun seedLocalStrokes(lobbyId: String, strokes: List<Stroke>) {
        if (lobbyId.isBlank() || strokes.isEmpty()) return
        val c = canvas(lobbyId)
        c.strokes.clear()
        c.localStrokeIds.clear()
        for (raw in strokes) {
            if (raw.points.isEmpty()) continue
            val stroke = raw.copy(
                isLocal = true,
                nickname = raw.nickname ?: cachedNickname,
                colorIndex = raw.colorIndex,
                authorId = AccountSession.account.value?.id?.takeIf { it.isNotBlank() },
                colorLocked = true
            )
            c.strokes.add(stroke)
            c.localStrokeIds.add(stroke.id)
            touchStroke(lobbyId, stroke.nickname, authorId = stroke.authorId, isLocal = stroke.isLocal)
        }
        bump(lobbyId)
    }

    /**
     * Server-History nach Connect. Chunks werden bis [done] gepuffert —
     * kein Clear/Redraw dazwischen. Unveränderte History → kein bump (kein Blinken).
     * Bei leerem Server und lokaler Sicherung: eigene Striche wiederherstellen.
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
            if (incoming.isEmpty() && done) {
                historyBackup[lobbyId] = c.strokes.toList()
            } else if (incoming.isNotEmpty()) {
                historyBackup.remove(lobbyId)
            }
            pendingHistory[lobbyId] = mutableListOf()
        }
        val buf = pendingHistory.getOrPut(lobbyId) { mutableListOf() }
        for (raw in incoming) {
            if (buf.any { it.id == raw.id }) continue
            buf.add(raw)
        }
        // Zwischen-Chunks: noch nichts anzeigen — verhindert Flash (Clear → 35 Striche → Rest)
        if (!done) return

        pendingHistory.remove(lobbyId)
        val assembled = buf.map { raw ->
            val mine = !nick.isNullOrBlank() && raw.nickname.equals(nick, ignoreCase = true)
            raw.copy(isLocal = mine)
        }.toMutableList()

        val backup = historyBackup.remove(lobbyId)
        if (assembled.isEmpty() && !backup.isNullOrEmpty()) {
            for (s in backup) {
                if (assembled.any { it.id == s.id }) continue
                val mine = s.isLocal ||
                    (!nick.isNullOrBlank() && s.nickname.equals(nick, ignoreCase = true))
                assembled.add(s.copy(isLocal = mine))
                if (::appContext.isInitialized) {
                    PairConnectionService.sendStroke(
                        appContext,
                        PairProtocol.encode(PairMessage.StrokeMsg(s.copy(isLocal = mine))),
                        lobbyId
                    )
                }
            }
        }

        if (strokeFingerprint(c.strokes) == strokeFingerprint(assembled)) {
            return
        }

        c.strokes.clear()
        c.localStrokeIds.clear()
        for (stroke in assembled) {
            c.strokes.add(stroke)
            if (stroke.isLocal) c.localStrokeIds.add(stroke.id)
            touchStroke(
                lobbyId,
                stroke.nickname,
                authorId = stroke.authorId,
                isLocal = stroke.isLocal,
            )
        }
        val other = assembled.asReversed().firstOrNull { stroke ->
            !stroke.isLocal && !isSelfStrokeAuthor(stroke.nickname, stroke.authorId)
        }
        if (other != null && !other.nickname.isNullOrBlank()) {
            lastOtherStrokeAt.getOrPut(lobbyId) { MutableStateFlow(0L) }.value =
                System.currentTimeMillis()
            lastOtherStrokeBy.getOrPut(lobbyId) { MutableStateFlow(null) }.value =
                other.nickname!!.trim()
        }
        bump(lobbyId)
    }

    private fun strokeFingerprint(strokes: List<Stroke>): String =
        strokes.joinToString("|") { s ->
            val first = s.points.firstOrNull()
            val last = s.points.lastOrNull()
            buildString {
                append(s.id)
                append(':')
                append(s.colorIndex)
                append(':')
                append(s.emoji.orEmpty())
                append(':')
                append(s.points.size)
                append(':')
                append(first?.x)
                append(',')
                append(first?.y)
                append('-')
                append(last?.x)
                append(',')
                append(last?.y)
            }
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

    fun lastOtherStrokeAtValue(lobbyId: String?): Long {
        val id = resolveLobbyId(lobbyId) ?: return 0L
        return lastOtherStrokeAt[id]?.value ?: 0L
    }

    fun lastOtherStrokeByValue(lobbyId: String?): String? {
        val id = resolveLobbyId(lobbyId) ?: return null
        return lastOtherStrokeBy[id]?.value
    }

    private fun isSelfStrokeAuthor(nickname: String?, authorId: String?): Boolean {
        val myId = AccountSession.account.value?.id?.takeIf { it.isNotBlank() }
        if (!authorId.isNullOrBlank() && myId != null && authorId == myId) return true
        val myNick = cachedNickname?.trim().orEmpty()
        if (myNick.isNotBlank() && !nickname.isNullOrBlank() &&
            nickname.trim().equals(myNick, ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    private fun touchStroke(
        lobbyId: String,
        nickname: String?,
        authorId: String? = null,
        isLocal: Boolean = false,
    ) {
        lastStrokeAt.getOrPut(lobbyId) { MutableStateFlow(0L) }.value = System.currentTimeMillis()
        if (!nickname.isNullOrBlank()) {
            lastStrokeBy.getOrPut(lobbyId) { MutableStateFlow(null) }.value = nickname.trim()
        }
        val self = isLocal || isSelfStrokeAuthor(nickname, authorId)
        if (!self && !nickname.isNullOrBlank()) {
            lastOtherStrokeAt.getOrPut(lobbyId) { MutableStateFlow(0L) }.value =
                System.currentTimeMillis()
            lastOtherStrokeBy.getOrPut(lobbyId) { MutableStateFlow(null) }.value = nickname.trim()
        }
    }

    fun addLocalStroke(
        points: List<StrokePoint>,
        width: Float = cachedBrushWidth,
        lobbyId: String? = null,
        /** Wenn gesetzt: [width] ist View-Pixel und wird relativ gespeichert. */
        shortSidePx: Float? = null
    ) {
        if (points.size < 2) return
        val id = resolveLobbyId(lobbyId) ?: return
        val storedWidth = if (shortSidePx != null && shortSidePx > 0f) {
            toStoredWidth(width, shortSidePx)
        } else {
            width.coerceIn(4f, 48f)
        }
        val stroke = Stroke(
            id = UUID.randomUUID().toString(),
            points = points,
            width = storedWidth,
            isLocal = true,
            nickname = cachedNickname,
            colorIndex = cachedColorIndex,
            authorId = AccountSession.account.value?.id?.takeIf { it.isNotBlank() },
            colorLocked = isSoloLobby(id)
        )
        val c = canvas(id)
        c.strokes.add(stroke)
        c.localStrokeIds.add(stroke.id)
        pushUndo(c, LocalUndo.Stroke(stroke.id))
        touchStroke(id, cachedNickname, isLocal = true)
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

    /** Vorlage als transformierter Leinwand-Eintrag (Zentrum + Parts). */
    fun addLocalTemplate(
        parts: List<com.luv.couple.data.TemplateStrokePart>,
        x: Float,
        y: Float,
        scale: Float,
        rotation: Float,
        lobbyId: String? = null,
        id: String = UUID.randomUUID().toString(),
        coordSpace: String? = null
    ): Stroke? {
        val lobby = resolveLobbyId(lobbyId) ?: return null
        if (parts.isEmpty()) return null
        val stroke = Stroke(
            id = id,
            points = listOf(
                StrokePoint(
                    x = x.coerceIn(0.05f, 0.95f),
                    y = y.coerceIn(0.05f, 0.95f)
                )
            ),
            width = 0f,
            isLocal = true,
            nickname = cachedNickname,
            colorIndex = cachedColorIndex,
            authorId = AccountSession.account.value?.id?.takeIf { it.isNotBlank() },
            colorLocked = true,
            templateParts = parts,
            templateScale = scale.coerceIn(0.2f, 4f),
            templateRotation = rotation,
            templateCoordSpace = templateCoordSpace(parts, coordSpace)
        )
        val c = canvas(lobby)
        if (c.strokes.any { it.id == stroke.id }) return null
        c.strokes.add(stroke)
        c.localStrokeIds.add(stroke.id)
        pushUndo(c, LocalUndo.Stroke(stroke.id))
        touchStroke(lobby, cachedNickname, isLocal = true)
        bump(lobby)
        if (::appContext.isInitialized) {
            PairConnectionService.sendStroke(
                appContext,
                PairProtocol.encode(PairMessage.StrokeMsg(stroke)),
                lobby
            )
        }
        return stroke
    }

    /** Emoji als echter Leinwand-Strich (1 Punkt + emoji-Feld). */
    fun addLocalSticker(
        emoji: String,
        x: Float,
        y: Float,
        lobbyId: String? = null,
        id: String = UUID.randomUUID().toString()
    ): Stroke? {
        val lobby = resolveLobbyId(lobbyId) ?: return null
        val emojiText = clipItemId(emoji)
        if (emojiText.isEmpty()) return null
        val stroke = Stroke(
            id = id,
            points = listOf(
                StrokePoint(
                    x = x.coerceIn(0.05f, 0.95f),
                    y = y.coerceIn(0.05f, 0.95f)
                )
            ),
            width = 0f,
            isLocal = true,
            nickname = cachedNickname,
            colorIndex = cachedColorIndex,
            authorId = AccountSession.account.value?.id?.takeIf { it.isNotBlank() },
            emoji = emojiText,
            colorLocked = isSoloLobby(lobby)
        )
        val c = canvas(lobby)
        if (c.strokes.any { it.id == stroke.id }) return null
        c.strokes.add(stroke)
        c.localStrokeIds.add(stroke.id)
        pushUndo(c, LocalUndo.Stroke(stroke.id))
        touchStroke(lobby, cachedNickname, isLocal = true)
        bump(lobby)
        if (::appContext.isInitialized) {
            PairConnectionService.sendStroke(
                appContext,
                PairProtocol.encode(PairMessage.StrokeMsg(stroke)),
                lobby
            )
        }
        return stroke
    }

    /** Legacy sticker_place → als Emoji-Strich einfügen. */
    fun upsertRemoteEmojiStroke(
        id: String,
        emoji: String,
        x: Float,
        y: Float,
        nickname: String?,
        lobbyId: String
    ) {
        val emojiText = clipItemId(emoji)
        if (emojiText.isEmpty()) return
        addRemoteStroke(
            Stroke(
                id = id,
                points = listOf(
                    StrokePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
                ),
                width = 0f,
                isLocal = false,
                nickname = nickname,
                emoji = emojiText
            ),
            lobbyId
        )
    }

    private fun pushUndo(c: LobbyCanvas, action: LocalUndo) {
        c.localUndo.add(action)
        while (c.localUndo.size > 200) c.localUndo.removeAt(0)
    }

    fun addLocalDot(x: Float, y: Float, lobbyId: String? = null, shortSidePx: Float = WIDTH_REF) {
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
        // Punkt etwas feiner als aktueller Pinsel, relativ skaliert
        val tip = (cachedBrushWidth * 0.85f).coerceIn(8f, 22f)
        addLocalStroke(points, width = tip, lobbyId = lobbyId, shortSidePx = shortSidePx)
    }

    fun addRemoteStroke(stroke: Stroke, lobbyId: String) {
        val c = canvas(lobbyId)
        if (c.strokes.any { it.id == stroke.id }) return
        c.strokes.add(stroke.copy(isLocal = false))
        touchStroke(
            lobbyId,
            stroke.nickname,
            authorId = stroke.authorId,
            isLocal = false,
        )
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
        }
    }

    private data class EraseSession(
        val lobbyId: String,
        val baselineIds: Set<String>,
    )

    /** Während Finger-down: nur lokal radiert, Sync erst bei [endEraseSession]. */
    private var eraseSession: EraseSession? = null

    /**
     * Eraser-Halbradius in normalisierten 0..1-Koordinaten —
     * exakt die halbe sichtbare Pinselbreite (WIDTH_REF), kein Extra-Puffer.
     */
    fun eraseRadiusForBrush(brushWidth: Float): Float =
        brushWidth.coerceIn(6f, 40f) / WIDTH_REF / 2f

    fun beginEraseSession(lobbyId: String? = null) {
        val id = resolveLobbyId(lobbyId) ?: return
        val c = canvas(id)
        val nick = cachedNickname?.trim().orEmpty()
        val mineIds = c.strokes
            .filter { stroke ->
                stroke.isLocal ||
                    stroke.id in c.localStrokeIds ||
                    (nick.isNotBlank() && stroke.nickname.equals(nick, ignoreCase = true))
            }
            .map { it.id }
            .toSet()
        eraseSession = EraseSession(lobbyId = id, baselineIds = mineIds)
    }

    /**
     * Nach dem Wischt: ein atomarer erase_commit (remove + Fragmente).
     * Peers sehen nicht erst leeres Board und dann Stück-für-Stück-Rebuild.
     */
    fun endEraseSession(lobbyId: String? = null): Boolean {
        val session = eraseSession ?: return false
        eraseSession = null
        val id = resolveLobbyId(lobbyId) ?: session.lobbyId
        if (id != session.lobbyId) return false
        val c = canvas(id)
        if (!::appContext.isInitialized) return true
        val nick = cachedNickname?.trim().orEmpty()
        val currentIds = c.strokes.map { it.id }.toSet()
        val removeIds = session.baselineIds.filter { it !in currentIds }
        val addStrokes = c.strokes.filter { stroke ->
            val mine =
                stroke.isLocal ||
                    stroke.id in c.localStrokeIds ||
                    (nick.isNotBlank() && stroke.nickname.equals(nick, ignoreCase = true))
            mine && stroke.id !in session.baselineIds
        }
        if (removeIds.isEmpty() && addStrokes.isEmpty()) return true
        PairConnectionService.sendEraseCommit(
            appContext,
            removeIds = removeIds,
            add = addStrokes,
            lobbyId = id
        )
        return true
    }

    /** Atomar entfernen + Fragmente einfügen (Peer-Seite / History). */
    fun applyEraseCommit(
        removeIds: List<String>,
        add: List<com.luv.couple.data.Stroke>,
        lobbyId: String
    ) {
        val c = canvas(lobbyId)
        val removeSet = removeIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (removeSet.isNotEmpty()) {
            c.strokes.removeAll { it.id in removeSet }
            c.localStrokeIds.removeAll(removeSet)
            c.localUndo.removeAll { it is LocalUndo.Stroke && it.id in removeSet }
        }
        for (raw in add) {
            if (raw.id.isBlank()) continue
            if (c.strokes.any { it.id == raw.id }) continue
            c.strokes.add(raw.copy(isLocal = false))
        }
        bump(lobbyId)
    }

    fun cancelEraseSession() {
        eraseSession = null
    }

    /**
     * Radiert nur Stellen entlang des Brush-Pfads (normalisierte 0..1).
     * Eigene Striche werden an getroffenen Stellen geteilt — nicht komplett gelöscht.
     * [broadcast]=false während Erase-Session (Netzwerk erst bei endEraseSession).
     */
    fun eraseLocalAlong(
        brush: List<StrokePoint>,
        radius: Float = 0.028f,
        lobbyId: String? = null,
        broadcast: Boolean = true,
    ): Boolean {
        if (brush.isEmpty()) return false
        val id = resolveLobbyId(lobbyId) ?: return false
        val c = canvas(id)
        val nick = cachedNickname?.trim().orEmpty()
        val inSession = eraseSession?.lobbyId == id
        val doBroadcast = broadcast && !inSession
        val mine = c.strokes.filter { stroke ->
            stroke.isLocal ||
                stroke.id in c.localStrokeIds ||
                (nick.isNotBlank() && stroke.nickname.equals(nick, ignoreCase = true))
        }
        if (mine.isEmpty()) return false
        var changed = false
        // Emoji/Vorlage: nur Treffer innerhalb der Pinselbreite (kein 1.6×/3.5×-Aufschlag)
        val stampR2 = (radius * 1.15f).let { it * it }
        for (stroke in mine) {
            if (stroke.isEmoji || stroke.isTemplate) {
                val p = stroke.points.firstOrNull() ?: continue
                val hit = brush.any { b ->
                    val dx = p.x - b.x
                    val dy = p.y - b.y
                    dx * dx + dy * dy <= stampR2
                }
                if (!hit) continue
                c.strokes.removeAll { it.id == stroke.id }
                c.localStrokeIds.remove(stroke.id)
                c.localUndo.removeAll { it is LocalUndo.Stroke && it.id == stroke.id }
                if (doBroadcast && ::appContext.isInitialized) {
                    PairConnectionService.sendUndo(appContext, stroke.id, id)
                }
                changed = true
                continue
            }
            // Pinsel-Halbradius + Strich-Halbradius = echte visuelle Überlappung
            val strokeHalf = (stroke.width.coerceIn(3f, 48f) / WIDTH_REF / 2f)
            val strokeRadius = radius + strokeHalf
            val fragments = splitStrokeAwayFromBrush(stroke.points, brush, strokeRadius)
            val unchanged = fragments.size == 1 && fragments[0].size == stroke.points.size &&
                fragments[0].zip(stroke.points).all { (a, b) -> a.x == b.x && a.y == b.y }
            if (unchanged) continue
            c.strokes.removeAll { it.id == stroke.id }
            c.localStrokeIds.remove(stroke.id)
            if (doBroadcast && ::appContext.isInitialized) {
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
                    gender = stroke.gender,
                    emoji = stroke.emoji,
                    colorLocked = stroke.colorLocked
                )
                c.strokes.add(neu)
                c.localStrokeIds.add(neu.id)
                if (doBroadcast && ::appContext.isInitialized) {
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
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = min(width, height) * 0.075f
            typeface = Typeface.DEFAULT
        }
        val emojiBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val fm = emojiPaint.fontMetrics
        val baselinePad = (fm.bottom + fm.top) / 2f
        strokes.forEach { stroke ->
            if (stroke.isEmoji) {
                val p = stroke.points.firstOrNull() ?: return@forEach
                val emoji = stroke.emoji.orEmpty()
                val cx = p.x * width
                val cy = p.y * height
                if (isImagePetId(emoji)) {
                    val bmp = ItemImageCache.getOrLoadSync(emoji)
                    if (bmp != null) {
                        val size = min(width, height) * 0.09f
                        canvas.drawBitmap(
                            bmp,
                            null,
                            RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f),
                            emojiBitmapPaint
                        )
                    }
                } else {
                    canvas.drawText(emoji, cx, cy - baselinePad, emojiPaint)
                }
                return@forEach
            }
            if (stroke.isTemplate) {
                val parts = stroke.templateParts ?: return@forEach
                val center = stroke.points.firstOrNull() ?: return@forEach
                val cx = center.x * width
                val cy = center.y * height
                val scale = stroke.templateScale.coerceIn(0.2f, 4f)
                val space = templateCoordSpace(parts, stroke.templateCoordSpace)
                val (stampW, stampH) = templateStampSize(
                    width.toFloat(),
                    height.toFloat(),
                    scale,
                    space
                )
                val strokeRef = min(stampW, stampH)
                val rad = Math.toRadians(stroke.templateRotation.toDouble())
                val cos = kotlin.math.cos(rad).toFloat()
                val sin = kotlin.math.sin(rad).toFloat()
                parts.forEach { part ->
                    if (part.points.isEmpty()) return@forEach
                    paint.color = PeerPalette.strokeColor(part.colorIndex)
                    paint.strokeWidth = (part.width / WIDTH_REF) * strokeRef
                    val path = Path()
                    part.points.forEachIndexed { idx, p ->
                        val lx = (p.x - 0.5f) * stampW
                        val ly = (p.y - 0.5f) * stampH
                        val rx = lx * cos - ly * sin
                        val ry = lx * sin + ly * cos
                        val x = cx + rx
                        val y = cy + ry
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    if (part.points.size == 1) {
                        path.lineTo(cx + 0.01f, cy)
                    }
                    canvas.drawPath(path, paint)
                }
                return@forEach
            }
            if (stroke.points.isEmpty()) return@forEach
            paint.color = strokeColor(stroke)
            paint.strokeWidth = strokeWidthPx(stroke, min(width, height).toFloat())
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
                val o = JSONObject()
                    .put("id", stroke.id)
                    .put("width", stroke.width.toDouble())
                    .put("nickname", stroke.nickname)
                    .put("colorIndex", stroke.colorIndex)
                    .put("authorId", stroke.authorId)
                    .put("isLocal", stroke.isLocal)
                    .put("emoji", stroke.emoji)
                    .put("colorLocked", stroke.colorLocked)
                    .put("points", points)
                val tpl = stroke.templateParts
                if (!tpl.isNullOrEmpty()) {
                    o.put("templateParts", PairProtocol.encodeTemplateParts(tpl))
                    o.put("templateScale", stroke.templateScale.toDouble())
                    o.put("templateRotation", stroke.templateRotation.toDouble())
                    stroke.templateCoordSpace?.takeIf { it.isNotBlank() }?.let {
                        o.put("templateCoordSpace", it)
                    }
                }
                arr.put(o)
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
                val emoji = o.optString("emoji").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { clipItemId(it) }
                    ?.takeIf { it.isNotEmpty() }
                val templateParts = PairProtocol.parseTemplateParts(o.optJSONArray("templateParts"))
                if (emoji == null && templateParts == null && points.size < 2) continue
                if (emoji != null && points.isEmpty()) continue
                if (templateParts != null && points.isEmpty()) continue
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
                    authorId = o.optString("authorId").takeIf { it.isNotBlank() && it != "null" },
                    emoji = emoji,
                    colorLocked = o.optBoolean("colorLocked", false),
                    templateParts = templateParts,
                    templateScale = o.optDouble("templateScale", 1.0).toFloat().coerceIn(0.2f, 4f),
                    templateRotation = o.optDouble("templateRotation", 0.0).toFloat(),
                    templateCoordSpace = o.optString("templateCoordSpace")
                        .takeIf { it.equals("canvas", ignoreCase = true) || it.equals("square", ignoreCase = true) }
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
