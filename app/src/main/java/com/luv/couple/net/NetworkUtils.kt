package com.luv.couple.net

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    const val DEFAULT_PORT = 18765

    fun localIpv4(): String? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        for (network in interfaces) {
            if (!network.isUp || network.isLoopback) continue
            for (address in network.inetAddresses) {
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    val host = address.hostAddress ?: continue
                    if (host.startsWith("192.") || host.startsWith("10.") || host.startsWith("172.")) {
                        return@runCatching host
                    }
                }
            }
        }
        for (network in interfaces) {
            if (!network.isUp || network.isLoopback) continue
            for (address in network.inetAddresses) {
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return@runCatching address.hostAddress
                }
            }
        }
        null
    }.getOrNull()

    fun randomToken(length: Int = 8): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..length).map { alphabet.random() }.joinToString("")
    }
}
