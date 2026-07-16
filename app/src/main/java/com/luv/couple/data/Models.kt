package com.luv.couple.data

enum class Gender {
    MALE,
    FEMALE;

    val lockColor: Int
        get() = when (this) {
            MALE -> 0xFF00B7E4.toInt()
            FEMALE -> 0xFFC218A8.toInt()
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
    val width: Float = 18f
)
