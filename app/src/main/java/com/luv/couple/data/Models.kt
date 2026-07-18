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
 * Zeichenfarben + dunkle Leinwand-Hintergründe.
 * 0 = Blau (Host-Wahl), 1 = Lila (Gegenseite), ab 2 weitere gut sichtbare Farben.
 * Keine hellen/weißen Strokes — Menü nutzt [menuAccent], nicht die Zeichenfarbe.
 */
object PeerPalette {
    const val MAX_PEERS = 10
    const val MAX_LOBBIES = 10
    const val MAX_LOBBY_NAME_LENGTH = 16
    const val LOBBY_CREATE_COST = 5
    const val SLOT_COST = 5
    const val GAME_COST = 1
    const val FREE_LOBBY_START_CAPACITY = 2
    const val PAID_LOBBY_START_CAPACITY = 4

    const val COLOR_BLUE = 0
    const val COLOR_PURPLE = 1
    const val COLOR_COUNT = 16

    private val strokeColors = intArrayOf(
        0xFF00B7E4.toInt(), // 0 Blau (Brand)
        0xFFC218A8.toInt(), // 1 Lila (Brand)
        0xFFFF5A6A.toInt(), // Koralle
        0xFFFF8F3D.toInt(), // Orange
        0xFFFFD23F.toInt(), // Gold
        0xFF7CFF6B.toInt(), // Limette
        0xFF2EE6A8.toInt(), // Minze
        0xFF3DD6FF.toInt(), // Cyan
        0xFF6B8CFF.toInt(), // Indigo
        0xFFFF4FC3.toInt(), // Hot Pink
        0xFFFF7A9A.toInt(), // Rosen
        0xFFFFB347.toInt(), // Amber
        0xFFB388FF.toInt(), // Lavendel
        0xFF5CFFEA.toInt(), // Aqua
        0xFFFF6B9D.toInt(), // Pink
        0xFFE8FF4A.toInt()  // Neongelb
    )

    /** Dunkle Tints — Strokes bleiben lesbar */
    private val lockTints = intArrayOf(
        0xFF0E1A24.toInt(),
        0xFF1A0E20.toInt(),
        0xFF1E1214.toInt(),
        0xFF1E1610.toInt(),
        0xFF1A1810.toInt(),
        0xFF101A12.toInt(),
        0xFF0E1A18.toInt(),
        0xFF0E181C.toInt(),
        0xFF10141E.toInt(),
        0xFF1C1018.toInt(),
        0xFF1C1216.toInt(),
        0xFF1A1610.toInt(),
        0xFF16121C.toInt(),
        0xFF0E1A1A.toInt(),
        0xFF1A1016.toInt(),
        0xFF16180E.toInt()
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

data class Lobby(
    val id: String,
    val name: String,
    val role: Role,
    val code: String,
    val token: String,
    val invite: String = "LUV-$code",
    val capacity: Int = PeerPalette.FREE_LOBBY_START_CAPACITY,
    val isFree: Boolean = false,
    val hostNickname: String = "",
    val hostColorSide: String = "blue",
    /** Höchste jemals gesehene Peer-Zahl — für Paar-Modus. */
    val peakPeers: Int = 1
) {
    val joinUrl: String
        get() = "https://reineke.pro/luv/j/$code"

    val coupleMode: Boolean get() = peakPeers <= 2
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
    val hostColorSide: String = "blue"
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
    val departed: Boolean = false
)

/** Ein Eintrag aus peers/welcome memberList */
data class RosterMember(
    val userId: String?,
    val nickname: String,
    val colorIndex: Int = -1,
    val active: Boolean = false,
    val online: Boolean = true
)

data class StrokePoint(val x: Float, val y: Float)

data class Stroke(
    val id: String,
    val points: List<StrokePoint>,
    val width: Float = 18f,
    val isLocal: Boolean = true,
    val nickname: String? = null,
    val colorIndex: Int = 0,
    val authorId: String? = null,
    /** Legacy — ältere Clients */
    val gender: String? = null,
    /** Wenn gesetzt: Emoji-Zeichnung auf der Leinwand (Punkt = Position). */
    val emoji: String? = null
) {
    val isEmoji: Boolean get() = !emoji.isNullOrBlank()
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
