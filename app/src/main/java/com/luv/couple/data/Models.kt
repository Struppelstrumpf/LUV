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

/** Feste Palette — gut unterscheidbar auf dunklem Sperrbildschirm, max. 4 Personen. */
object PeerPalette {
    const val MAX_PEERS = 4
    const val MAX_LOBBIES = 3

    private val strokeColors = intArrayOf(
        0xFFFFB4A8.toInt(), // Koralle
        0xFFA8E6CF.toInt(), // Minze
        0xFFC9B6FF.toInt(), // Lavendel
        0xFFFFE29A.toInt()  // Amber
    )

    private val lockTints = intArrayOf(
        0xFF2A1E24.toInt(),
        0xFF1A2624.toInt(),
        0xFF221E2C.toInt(),
        0xFF2A2418.toInt()
    )

    fun strokeColor(index: Int): Int =
        strokeColors[index.coerceAtLeast(0) % strokeColors.size]

    fun lockBackground(index: Int): Int =
        lockTints[index.coerceAtLeast(0) % lockTints.size]

    fun indexFor(seed: String): Int {
        if (seed.isBlank()) return 0
        return seed.hashCode().and(0x7fffffff) % strokeColors.size
    }

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
        get() = "https://reineke.pro/love/j/$code"
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
