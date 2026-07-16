package com.luv.couple.data

import android.graphics.Color

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

/** Standardpalette für Zeichenfarben — gut unterscheidbar auf dunklem Sperrbildschirm. */
object PeerPalette {
    const val MAX_PEERS = 4
    const val MAX_LOBBIES = 10
    const val LOBBY_CREATE_COST = 5
    const val COLOR_COUNT = 20

    private val strokeColors = intArrayOf(
        0xFFFF6B6B.toInt(), // Koralle
        0xFFFF8E53.toInt(), // Orange
        0xFFFFC857.toInt(), // Gold
        0xFFFFE29A.toInt(), // Amber
        0xFFB8E986.toInt(), // Limette
        0xFF7DDBA3.toInt(), // Minze
        0xFF4ECDC4.toInt(), // Türkis
        0xFF45B7D1.toInt(), // Azur
        0xFF5B8CFF.toInt(), // Blau
        0xFF7B6CFF.toInt(), // Indigo
        0xFFA78BFA.toInt(), // Violett
        0xFFC9B6FF.toInt(), // Lavendel
        0xFFE879F9.toInt(), // Pink
        0xFFFF7AB8.toInt(), // Rosen
        0xFFFFB4A8.toInt(), // Pfirsich
        0xFFF0A6CA.toInt(), // Blush
        0xFFD4A574.toInt(), // Sand
        0xFFB0BEC5.toInt(), // Silber
        0xFFE8E6E3.toInt(), // Creme
        0xFFFFD6A5.toInt()  // Honig
    )

    private val lockTints = intArrayOf(
        0xFF2A1A1C.toInt(),
        0xFF2A1E18.toInt(),
        0xFF2A2418.toInt(),
        0xFF282418.toInt(),
        0xFF1E261A.toInt(),
        0xFF1A2622.toInt(),
        0xFF182426.toInt(),
        0xFF182228.toInt(),
        0xFF1A1E2A.toInt(),
        0xFF1C1A2A.toInt(),
        0xFF221E2C.toInt(),
        0xFF241E2A.toInt(),
        0xFF2A1A28.toInt(),
        0xFF2A1A24.toInt(),
        0xFF2A1E24.toInt(),
        0xFF2A1E26.toInt(),
        0xFF26201A.toInt(),
        0xFF1E2226.toInt(),
        0xFF222224.toInt(),
        0xFF2A241C.toInt()
    )

    fun strokeColor(index: Int): Int =
        strokeColors[index.coerceAtLeast(0) % strokeColors.size]

    fun lockBackground(index: Int): Int =
        lockTints[index.coerceAtLeast(0) % lockTints.size]

    fun indexFor(seed: String): Int {
        if (seed.isBlank()) return 0
        return seed.hashCode().and(0x7fffffff) % strokeColors.size
    }

    fun allIndices(): IntRange = 0 until strokeColors.size

    fun composeColor(index: Int): androidx.compose.ui.graphics.Color =
        androidx.compose.ui.graphics.Color(strokeColor(index))
}

data class Lobby(
    val id: String,
    val name: String,
    val role: Role,
    val code: String,
    val token: String,
    val invite: String = "LUV-$code"
) {
    val joinUrl: String
        get() = "https://reineke.pro/luv/j/$code"
}

data class PeerInfo(
    val peerKey: String,
    val nickname: String,
    val colorIndex: Int,
    val active: Boolean = false
)

data class StrokePoint(
    val x: Float,
    val y: Float
)

data class Stroke(
    val id: String,
    val points: List<StrokePoint>,
    val width: Float = 18f,
    val isLocal: Boolean = true,
    val nickname: String? = null,
    val colorIndex: Int = 0,
    val authorId: String? = null,
    /** Legacy — ältere Clients */
    val gender: String? = null
)

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
