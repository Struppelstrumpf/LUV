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
    const val PREVIEW_HORIZON_DAYS = 14

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

        var previewOffset: Int? = null
        var previewPool = emptyList<T>()
        for (offset in 1..PREVIEW_HORIZON_DAYS) {
            val pool = rotating.filter {
                val id = idOf(it)
                !isActiveInRotation(id, day) && isActiveInRotation(id, day + offset)
            }.sortedWith(compareBy({ priceOf(it) }, { idOf(it) }))
            if (pool.isNotEmpty()) {
                previewOffset = offset
                previewPool = pool
                break
            }
        }
        if (previewPool.isEmpty()) {
            previewPool = rotating
                .filter { it !in mixed }
                .sortedWith(compareBy({ priceOf(it) }, { idOf(it) }))
            if (previewPool.isNotEmpty()) {
                previewOffset = previewOffset ?: CYCLE_DAYS_LONG
            }
        }

        val nextCheap = previewPool.firstOrNull { priceOf(it) <= PRICE_CHEAP_MAX }
        val nextExp = previewPool.firstOrNull { priceOf(it) > PRICE_CHEAP_MAX }
        val preview = buildList {
            nextCheap?.let { add(it) }
            nextExp?.let { add(it) }
            for (item in previewPool) {
                if (size >= previewCount) break
                if (item !in this) add(item)
            }
        }.take(previewCount)

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

        val days = if (preview.isEmpty()) null else previewOffset?.coerceIn(1, PREVIEW_HORIZON_DAYS)
        return RotateResult(grid, preview, days)
    }
}
