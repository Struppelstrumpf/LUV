package com.luv.couple.shop

/**
 * Clientseitige Shop-Rotation für Always-on-Items (ohne Server-Fenster):
 * - mind. 50 % der rotierenden Items aktiv
 * - ~40 % der Items im 3-Tage-Takt, ~60 % im 7-Tage-Takt
 * - Server-Timer-Items ([remainingMs] > 0) immer sichtbar
 * - Gratis/Starter nie ausblenden
 * - Kein Fake-Countdown — Restzeit nur vom Server
 * - Event-IDs sind bereits in [LiveShopCatalog] ausgefiltert
 */
object ShopRotation {
    const val CYCLE_DAYS_SHORT = 3
    const val CYCLE_DAYS_LONG = 7
    const val MIN_ACTIVE_RATIO = 0.5
    const val PRICE_CHEAP_MAX = 24
    /** Featured-Badge nutzt den kurzen Zyklus. */
    const val CYCLE_DAYS = CYCLE_DAYS_SHORT
    /** Horizont für „Demnächst“-Vorschau. */
    const val PREVIEW_HORIZON_DAYS = 21

    data class CycleInfo(
        val epoch: Long,
        /** 1–3 Tage bis zum nächsten 3-Tage-Slot (Featured). */
        val daysUntilNext: Int,
    )

    data class RotateResult<T>(
        val grid: List<T>,
        val preview: List<T>,
        /** Tage bis die Preview-Items kommen; null wenn keine Preview. */
        val daysUntilPreview: Int?,
    )

    fun cycleInfo(nowMs: Long = System.currentTimeMillis()): CycleInfo {
        val day = nowMs / 86_400_000L
        val epoch = day / CYCLE_DAYS_SHORT
        val dayInCycle = (day % CYCLE_DAYS_SHORT).toInt()
        val daysUntilNext = CYCLE_DAYS_SHORT - dayInCycle
        return CycleInfo(epoch = epoch, daysUntilNext = daysUntilNext.coerceIn(1, CYCLE_DAYS_SHORT))
    }

    fun daysUntilLabel(days: Int): String = when {
        days <= 1 -> "in 1 Tag"
        else -> "in $days Tagen"
    }

    private fun stableHash(id: String): Int {
        var h = 0
        for (c in id) {
            h = 31 * h + c.code
        }
        return h and 0x7fff_ffff
    }

    /** 3-Tage-Pool wenn hash%5 < 2 (~40 %), sonst 7-Tage. */
    private fun cycleLenFor(id: String): Int =
        if (stableHash(id) % 5 < 2) CYCLE_DAYS_SHORT else CYCLE_DAYS_LONG

    private fun isActiveInRotation(id: String, day: Long): Boolean {
        val cycle = cycleLenFor(id)
        val epoch = day / cycle
        val group = stableHash(id) % 2
        return group == (epoch % 2).toInt()
    }

    private fun daysUntilActive(id: String, day: Long): Int {
        for (offset in 1..PREVIEW_HORIZON_DAYS) {
            if (isActiveInRotation(id, day + offset)) return offset
        }
        return cycleLenFor(id).coerceIn(1, PREVIEW_HORIZON_DAYS)
    }

    private fun isPinned(id: String, price: Int): Boolean =
        price <= 0 || id == "meadow" || id == "🐣"

    /**
     * @return grid (aktive Items) + preview (2 Items, die als Nächstes reinkommen)
     * @param withRemaining optional: setzt Anzeige-Restzeit nur aus Server-[remainingOf]
     * @param skipRotation bei Suche: alle Treffer zeigen
     */
    fun <T> rotateCatalog(
        all: List<T>,
        epoch: Long,
        idOf: (T) -> String,
        priceOf: (T) -> Int,
        remainingOf: (T) -> Long?,
        bucketSize: Int = 0, // unused — API-Kompatibilität
        previewCount: Int = 2,
        nowMs: Long = System.currentTimeMillis(),
        withRemaining: ((T, Long?) -> T)? = null,
        skipRotation: Boolean = false,
    ): Pair<List<T>, List<T>> {
        val r = rotateCatalogDetailed(
            all = all,
            epoch = epoch,
            idOf = idOf,
            priceOf = priceOf,
            remainingOf = remainingOf,
            bucketSize = bucketSize,
            previewCount = previewCount,
            nowMs = nowMs,
            withRemaining = withRemaining,
            skipRotation = skipRotation,
        )
        return r.grid to r.preview
    }

    fun <T> rotateCatalogDetailed(
        all: List<T>,
        epoch: Long,
        idOf: (T) -> String,
        priceOf: (T) -> Int,
        remainingOf: (T) -> Long?,
        bucketSize: Int = 0,
        previewCount: Int = 2,
        nowMs: Long = System.currentTimeMillis(),
        withRemaining: ((T, Long?) -> T)? = null,
        skipRotation: Boolean = false,
    ): RotateResult<T> {
        @Suppress("UNUSED_PARAMETER")
        val _bucket = bucketSize
        @Suppress("UNUSED_PARAMETER")
        val _epoch = epoch
        val shopable = all
        if (skipRotation || shopable.isEmpty()) {
            val grid = if (withRemaining != null) {
                shopable.map { item ->
                    val rem = remainingOf(item)?.takeIf { it > 0L }
                    withRemaining(item, rem)
                }
            } else shopable
            return RotateResult(grid, emptyList(), null)
        }

        val timed = shopable.filter { (remainingOf(it) ?: 0L) > 0L }
        val pinned = shopable.filter {
            (remainingOf(it) ?: 0L) <= 0L && isPinned(idOf(it), priceOf(it))
        }
        val rotating = shopable.filter {
            (remainingOf(it) ?: 0L) <= 0L && !isPinned(idOf(it), priceOf(it))
        }
        if (rotating.isEmpty()) {
            val gridRaw = timed + pinned
            val grid = if (withRemaining != null) {
                gridRaw.map { item ->
                    withRemaining(item, remainingOf(item)?.takeIf { it > 0L })
                }
            } else gridRaw
            return RotateResult(grid, emptyList(), null)
        }

        val day = nowMs / 86_400_000L
        var activeRotating = rotating.filter { isActiveInRotation(idOf(it), day) }

        val minActive = kotlin.math.ceil(rotating.size * MIN_ACTIVE_RATIO).toInt().coerceAtLeast(1)
        if (activeRotating.size < minActive) {
            val need = minActive - activeRotating.size
            val inactive = rotating
                .filter { it !in activeRotating }
                .sortedBy { stableHash(idOf(it)) }
            activeRotating = activeRotating + inactive.take(need)
        }

        val cheap = activeRotating.filter { priceOf(it) <= PRICE_CHEAP_MAX }
            .sortedWith(compareBy({ priceOf(it) }, { idOf(it) }))
        val expensive = activeRotating.filter { priceOf(it) > PRICE_CHEAP_MAX }
            .sortedWith(compareBy({ priceOf(it) }, { idOf(it) }))
        val mixed = ArrayList<T>(activeRotating.size)
        var i = 0
        var j = 0
        var takeCheap = true
        while (i < cheap.size || j < expensive.size) {
            when {
                takeCheap && i < cheap.size -> mixed.add(cheap[i++])
                !takeCheap && j < expensive.size -> mixed.add(expensive[j++])
                i < cheap.size -> mixed.add(cheap[i++])
                j < expensive.size -> mixed.add(expensive[j++])
            }
            takeCheap = !takeCheap
        }

        val gridIds = mixed.map { idOf(it) }.toHashSet()
        val picked = ArrayList<T>(previewCount)
        val pickedIds = LinkedHashSet<String>()
        var farthestOffset = 0

        fun tryAdd(item: T, offset: Int): Boolean {
            if (picked.size >= previewCount) return false
            val id = idOf(item)
            if (id in gridIds || id in pickedIds) return false
            picked.add(item)
            pickedIds.add(id)
            farthestOffset = maxOf(farthestOffset, offset)
            return true
        }

        // Über den Horizont akkumulieren, bis previewCount erreicht
        for (offset in 1..PREVIEW_HORIZON_DAYS) {
            if (picked.size >= previewCount) break
            val pool = rotating.filter {
                val id = idOf(it)
                id !in gridIds &&
                    id !in pickedIds &&
                    !isActiveInRotation(id, day) &&
                    isActiveInRotation(id, day + offset)
            }.sortedWith(compareBy({ priceOf(it) }, { idOf(it) }))
            val cheapNext = pool.firstOrNull { priceOf(it) <= PRICE_CHEAP_MAX }
            val expNext = pool.firstOrNull { priceOf(it) > PRICE_CHEAP_MAX }
            cheapNext?.let { tryAdd(it, offset) }
            expNext?.let { tryAdd(it, offset) }
            for (item in pool) {
                if (picked.size >= previewCount) break
                tryAdd(item, offset)
            }
        }

        // Fallback: alles nicht im Grid, bald aktiv
        if (picked.size < previewCount) {
            val rest = rotating
                .filter { idOf(it) !in gridIds && idOf(it) !in pickedIds }
                .sortedWith(
                    compareBy(
                        { daysUntilActive(idOf(it), day) },
                        { priceOf(it) },
                        { idOf(it) }
                    )
                )
            for (item in rest) {
                if (picked.size >= previewCount) break
                tryAdd(item, daysUntilActive(idOf(item), day))
            }
        }

        // Letzter Fallback bei winzigem Katalog: auch Grid-Items mit Offset zeigen
        if (picked.size < previewCount && rotating.size >= 1) {
            val rest = rotating
                .filter { idOf(it) !in pickedIds }
                .sortedWith(compareBy({ priceOf(it) }, { idOf(it) }))
            for (item in rest) {
                if (picked.size >= previewCount) break
                val off = if (isActiveInRotation(idOf(item), day)) {
                    cycleLenFor(idOf(item))
                } else {
                    daysUntilActive(idOf(item), day)
                }
                tryAdd(item, off)
            }
        }

        val seen = LinkedHashSet<String>()
        val gridRaw = ArrayList<T>(timed.size + pinned.size + mixed.size)
        for (item in timed + pinned + mixed) {
            if (seen.add(idOf(item))) gridRaw.add(item)
        }

        val grid = if (withRemaining != null) {
            gridRaw.map { item ->
                withRemaining(item, remainingOf(item)?.takeIf { it > 0L })
            }
        } else {
            gridRaw
        }

        val days = if (picked.isEmpty()) {
            null
        } else {
            farthestOffset.coerceIn(1, PREVIEW_HORIZON_DAYS)
        }
        return RotateResult(grid, picked.take(previewCount), days)
    }
}
