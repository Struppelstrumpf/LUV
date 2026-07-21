package com.luv.couple.data

import android.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor

enum class Role {
    HOST,
    JOIN
}

enum class ConnectionState {
    IDLE,
    HOSTING,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

/**
 * Zeichenfarben + dunkle Leinwand-Hintergründe (Leinwand & Vorlagen gleich).
 * ~36 Farben: Brand + Jahreszeiten-Palette inkl. Weiß/Grau/Schwarz/Braun.
 * Menü nutzt [menuAccent], nicht die Zeichenfarbe.
 */
object PeerPalette {
    const val MAX_PEERS = 10
    const val MAX_LOBBIES = 10
    const val MAX_LOBBY_NAME_LENGTH = 16
    const val LOBBY_CREATE_COST = 4
    const val SLOT_COST = 5
    const val GAME_COST = 1
    const val FREE_LOBBY_START_CAPACITY = 2
    const val PAID_LOBBY_START_CAPACITY = 4

    const val COLOR_BLUE = 0
    const val COLOR_PURPLE = 1
    const val COLOR_COUNT = 36

    private val strokeColors = intArrayOf(
        // Brand / Basics
        0xFF00B7E4.toInt(), // 0 Blau
        0xFFC218A8.toInt(), // 1 Lila
        0xFFFFFFFF.toInt(), // Weiß
        0xFFB0BEC5.toInt(), // Hellgrau
        0xFF78909C.toInt(), // Grau
        0xFF263238.toInt(), // Anthrazit
        0xFF000000.toInt(), // Schwarz
        0xFF6D4C41.toInt(), // Braun
        0xFFA1887F.toInt(), // Hellbraun
        // Frühling
        0xFF7CFF6B.toInt(), // Limette
        0xFF81C784.toInt(), // Blattgrün
        0xFF2EE6A8.toInt(), // Minze
        0xFFFF80AB.toInt(), // Blütenrosa
        0xFFFFF59D.toInt(), // Softgelb
        0xFFCE93D8.toInt(), // Flieder
        // Sommer
        0xFFFF5A6A.toInt(), // Koralle
        0xFFFF8F3D.toInt(), // Orange
        0xFFFFD23F.toInt(), // Gold
        0xFF3DD6FF.toInt(), // Cyan
        0xFFFF4FC3.toInt(), // Hot Pink
        0xFFE8FF4A.toInt(), // Neongelb
        0xFF5CFFEA.toInt(), // Aqua
        // Herbst
        0xFFFFB347.toInt(), // Amber
        0xFFE65100.toInt(), // Rost
        0xFFBF360C.toInt(), // Kupfer
        0xFF8D6E63.toInt(), // Haselnuss
        0xFFD4A574.toInt(), // Laub
        0xFFC62828.toInt(), // Weinrot
        0xFFFF7A9A.toInt(), // Rosen
        // Winter
        0xFF6B8CFF.toInt(), // Indigo
        0xFFB388FF.toInt(), // Lavendel
        0xFF90CAF9.toInt(), // Eisblau
        0xFFE1F5FE.toInt(), // Schneeblau
        0xFF0277BD.toInt(), // Tiefblau
        0xFFFF6B9D.toInt(), // Pink
        0xFF4A148C.toInt(), // Mitternacht
    )

    /** Dunkle Tints — Strokes bleiben lesbar (Index wrappt mit Farben). */
    private val lockTints = intArrayOf(
        0xFF0E1A24.toInt(),
        0xFF1A0E20.toInt(),
        0xFF141418.toInt(),
        0xFF16181A.toInt(),
        0xFF141618.toInt(),
        0xFF101214.toInt(),
        0xFF0A0A0C.toInt(),
        0xFF1A1410.toInt(),
        0xFF1A1614.toInt(),
        0xFF101A12.toInt(),
        0xFF101812.toInt(),
        0xFF0E1A18.toInt(),
        0xFF1C1016.toInt(),
        0xFF1A1810.toInt(),
        0xFF18141C.toInt(),
        0xFF1E1214.toInt(),
        0xFF1E1610.toInt(),
        0xFF1A1810.toInt(),
        0xFF0E181C.toInt(),
        0xFF1C1018.toInt(),
        0xFF16180E.toInt(),
        0xFF0E1A1A.toInt(),
        0xFF1A1610.toInt(),
        0xFF1C120C.toInt(),
        0xFF1A100C.toInt(),
        0xFF181410.toInt(),
        0xFF1A1612.toInt(),
        0xFF1A0E10.toInt(),
        0xFF1C1216.toInt(),
        0xFF10141E.toInt(),
        0xFF16121C.toInt(),
        0xFF0E1620.toInt(),
        0xFF101820.toInt(),
        0xFF0C1420.toInt(),
        0xFF1A1016.toInt(),
        0xFF120E1A.toInt(),
    )

    fun strokeColor(index: Int): Int =
        strokeColors[index.coerceAtLeast(0) % strokeColors.size]

    fun lockBackground(index: Int): Int =
        lockTints[index.coerceAtLeast(0) % lockTints.size]

    fun indexFor(seed: String): Int {
        if (seed.isBlank()) return COLOR_BLUE
        return 2 + seed.hashCode().and(0x7fffffff) % (strokeColors.size - 2)
    }

    fun allIndices(): IntRange = 0 until strokeColors.size

    fun composeColor(index: Int): ComposeColor =
        ComposeColor(strokeColor(index))

    /** UI-Menü: immer Brand-Rosa — nie die Zeichenfarbe. */
    fun menuAccent(): ComposeColor = ComposeColor(0xFFFF6B8A)

    fun menuAccentAlt(): ComposeColor = ComposeColor(0xFF00B7E4)

    fun hostSideColor(side: String): Int =
        if (side.equals("purple", ignoreCase = true) || side.equals("lila", ignoreCase = true)) {
            COLOR_PURPLE
        } else {
            COLOR_BLUE
        }

    fun oppositeHostColor(hostIndex: Int): Int =
        if (hostIndex == COLOR_PURPLE) COLOR_BLUE else COLOR_PURPLE

    fun assignLobbyColor(taken: Set<Int>, hostSideIndex: Int = COLOR_BLUE): Int {
        val host = if (hostSideIndex == COLOR_PURPLE) COLOR_PURPLE else COLOR_BLUE
        val other = oppositeHostColor(host)
        if (host !in taken) return host
        if (other !in taken) return other
        for (i in 2 until COLOR_COUNT) {
            if (i !in taken) return i
        }
        return 2
    }
}

/** Ceremony oben, dann Hochzeitsbild-Mal-Lobby — Rest unveränderte Relative-Order. */
fun pinSpecialLobbies(list: List<Lobby>): List<Lobby> {
    if (list.size <= 1) return list
    val ceremony = list.filter { it.isWeddingCeremony }
    val paint = list.filter { it.isWedding && !it.isWeddingCeremony }
    val rest = list.filter { !it.isWeddingCeremony && !(it.isWedding && !it.isWeddingCeremony) }
    if (ceremony.isEmpty() && paint.isEmpty()) return list
    return ceremony + paint + rest
}

data class Lobby(
    val id: String,
    val name: String,
    val role: Role,
    val code: String,
    val token: String,
    val invite: String = "LUV-$code",
    val capacity: Int = PeerPalette.FREE_LOBBY_START_CAPACITY,
    val isFree: Boolean = false,
    /** Zufalls-Matchmaking-Lobby (blau, keine Einladungen). */
    val isRandom: Boolean = false,
    /** Hochzeitsbild-Mal-Lobby — kein Verlassen, keine Einladungen. */
    val isWedding: Boolean = false,
    /** Nachträgliches Hochzeitsbild (Ehe ohne gespeichertes Bild). */
    val isWeddingRetake: Boolean = false,
    /**
     * Hochzeits-Zeremonie-Lobby (ohne Malen) — Zusatz, ersetzt keine anderen Lobby-Typen.
     * Pin oben im Home; Verlassen nur über Ceremony-Leave.
     */
    val isWeddingCeremony: Boolean = false,
    /** Geplante Zeremonie-Zeit (epoch ms), nur Ceremony. */
    val ceremonyAt: Long = 0L,
    /** Namen Brautpaar für Ceremony-Karte. */
    val coupleNameA: String? = null,
    val coupleNameB: String? = null,
    val hostNickname: String = "",
    val hostColorSide: String = "blue",
    /** Höchste jemals gesehene Peer-Zahl — für Paar-Modus. */
    val peakPeers: Int = 1,
    /** Server: letzte Zeichenaktivität in der Lobby. */
    val lastCanvasAt: Long = 0L,
    /** Wer zuletzt gezeichnet/platziert hat — Glow nur wenn ≠ eigenes Konto. */
    val lastCanvasActorId: String? = null,
    /**
     * Unveränderlicher Ersteller (auch nach Host-Failover).
     * Anzeige „Von dir erstellt“ hängt daran, nicht am aktuellen Live-Host.
     */
    val createdByMe: Boolean = false,
    val eventId: String? = null,
    val eventPrompt: String? = null,
    /** Bis zu 3 Vorschläge — leer nach Wahl / wenn schon gesetzt. */
    val eventPromptChoices: List<String> = emptyList(),
    /** ISO-8601 Ende des Event-Fensters (Server). */
    val eventEndsAt: String? = null,
) {
    val joinUrl: String
        get() = "https://reineke.pro/luv/j/$code"

    val coupleMode: Boolean get() = peakPeers <= 2

    val isEventLobby: Boolean get() = !eventId.isNullOrBlank()
}

data class RoomPreview(
    val code: String,
    val name: String,
    val hostNickname: String,
    val peers: Int,
    val capacity: Int,
    val maxPeers: Int,
    val isFree: Boolean,
    val invite: String,
    val joinUrl: String,
    val hostColorSide: String = "blue",
    /** Relativer oder absoluter Pfad zum Invite-Snapshot (Leinwand). */
    val inviteImageUrl: String? = null,
    val hasDrawing: Boolean = false
)

data class PeerInfo(
    val peerKey: String,
    val nickname: String,
    val colorIndex: Int,
    val active: Boolean = false,
    /** Server-User-ID — stabiler als Nickname für Roster/Anzeige */
    val userId: String? = null,
    /** Noch in der Lobby (auch kurz offline) */
    val online: Boolean = true,
    /** Hat die Lobby verlassen, Avatar nur noch wegen gezeichneter Striche */
    val departed: Boolean = false,
    /** Ausgerüsteter Begleiter am Avatar */
    val petEmoji: String = "🐣"
)

/** Ein Eintrag aus peers/welcome memberList */
data class RosterMember(
    val userId: String?,
    val nickname: String,
    val colorIndex: Int = -1,
    val active: Boolean = false,
    val online: Boolean = true,
    val petEmoji: String = "🐣"
)

data class StrokePoint(val x: Float, val y: Float)

/** Ein Strich innerhalb einer Vorlage (Koordinaten 0…1). */
data class TemplateStrokePart(
    val points: List<StrokePoint>,
    val width: Float = 18f,
    val colorIndex: Int = 0
)

data class DrawTemplate(
    val id: String,
    val strokes: List<TemplateStrokePart>,
    val createdAt: Long = System.currentTimeMillis(),
    /** "canvas" = volle Hochformat-Fläche, "square" = Legacy-Quadrat. */
    val coordSpace: String = "canvas"
)

data class Stroke(
    val id: String,
    val points: List<StrokePoint>,
    /**
     * Strichdicke in Referenz-Pixeln (bezogen auf kurze Seite = 1000).
     * Wird beim Zeichnen/Screenshot proportional skaliert — Sync bleibt konsistent.
     */
    val width: Float = 18f,
    val isLocal: Boolean = true,
    val nickname: String? = null,
    val colorIndex: Int = 0,
    val authorId: String? = null,
    /** Legacy — ältere Clients */
    val gender: String? = null,
    /** Wenn gesetzt: Emoji-Zeichnung auf der Leinwand (Punkt = Position). */
    val emoji: String? = null,
    /**
     * Solo-Zeichnung: Farbe bleibt beim Palettenwechsel / Peer-Recolor.
     * Ab 2 Personen neu gemalte Striche haben false und färben sich mit um.
     */
    val colorLocked: Boolean = false,
    /** Platzierte Vorlage: Teile + Transform (points[0] = Zentrum). */
    val templateParts: List<TemplateStrokePart>? = null,
    val templateScale: Float = 1f,
    val templateRotation: Float = 0f,
    /** "canvas" | "square" — fehlt bei alten Strokes → Heuristik. */
    val templateCoordSpace: String? = null
) {
    val isEmoji: Boolean get() = !emoji.isNullOrBlank()
    val isTemplate: Boolean get() = !templateParts.isNullOrEmpty()
}

/** @deprecated Legacy LAN-Invite */
data class InvitePayload(
    val ip: String,
    val port: Int,
    val token: String,
    val gender: String = "MALE"
)

@Suppress("unused")
enum class Gender {
    MALE,
    FEMALE;

    val lockColor: Int
        get() = when (this) {
            MALE -> 0xFF00B7E4.toInt()
            FEMALE -> 0xFFC218A8.toInt()
        }

    val partnerStrokeColor: Int
        get() = when (this) {
            MALE -> 0xFFFFE8F6.toInt()
            FEMALE -> 0xFFE8F9FF.toInt()
        }
}

fun Int.withAlpha(alpha: Int): Int = Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))
