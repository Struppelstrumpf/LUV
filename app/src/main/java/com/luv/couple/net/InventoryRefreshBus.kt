package com.luv.couple.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/** Account-Push → Inventar / Lootbox-Badge einmal neu laden. */
object InventoryRefreshBus {
    private val counter = AtomicInteger(0)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun bump() {
        _revision.value = counter.incrementAndGet()
    }
}

object LootboxRefreshBus {
    private val counter = AtomicInteger(0)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun bump() {
        _revision.value = counter.incrementAndGet()
    }
}

object MaintenancePushBus {
    private val _ping = MutableStateFlow(0L)
    val ping: StateFlow<Long> = _ping.asStateFlow()

    fun bump() {
        _ping.value = System.currentTimeMillis()
    }
}

object ShopRotatedBus {
    private val counter = AtomicInteger(0)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun bump() {
        _revision.value = counter.incrementAndGet()
    }
}
