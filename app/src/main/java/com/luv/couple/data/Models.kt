package com.luv.couple.data

enum class Gender {
    MALE,
    FEMALE;

    val lockColor: Int
        get() = when (this) {
            MALE -> 0xFF00B7E4.toInt()
            FEMALE -> 0xFFC218A8.toInt()
        }

    /** Etwas hellere Mischfarbe für Partner-Striche auf der eigenen Leinwand */
    val partnerStrokeColor: Int
        get() = when (this) {
            MALE -> 0xFFFFE8F6.toInt()   // zartes Rosa auf blau
            FEMALE -> 0xFFE8F9FF.toInt() // zartes Eisblau auf lila
        }
}

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

data class InvitePayload(
    val ip: String,
    val port: Int,
    val token: String,
    val gender: String
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
    val gender: String? = null
)
