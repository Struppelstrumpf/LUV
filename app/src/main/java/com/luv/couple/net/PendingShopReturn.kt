package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PendingShopReturn {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun offer(raw: String?) {
        val value = raw?.lowercase().orEmpty()
        if (
            value.startsWith("luv://shop") ||
            value.contains("reineke.pro/luv/shop/return") ||
            value.contains("reineke.pro/love/shop/return")
        ) {
            _pending.value = true
        }
    }

    fun consume(): Boolean {
        val value = _pending.value
        _pending.value = false
        return value
    }
}
