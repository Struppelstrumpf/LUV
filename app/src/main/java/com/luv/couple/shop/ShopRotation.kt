package com.luv.couple.shop

/**
 * Clientseitige Shop-Rotation für Always-on-Items (ohne Server-Fenster):
 * - mind. 50 % der rotierenden Items aktiv
 * - ~40 % der Items im 3-Tage-Takt, ~60 % im 7-Tage-Takt
 * - Server-Timer-Items ([remainingMs] > 0) immer sichtbar
 * - Gratis/Starter nie ausblenden
 * - „Demnächst“: immer bis zu 2 Vorschau-Items (auch wenn alles Timer/Pinned ist)
 */
object ShopRotation {
    const val CYCLE_DAYS_SHORT = 3
    const val CYCLE_DAYS_LONG = 7
    const val MIN_ACTIVE_RATIO = 0.5
    const val PRICE_CHEAP_MAX = 24
    const val CYCLE_DAYS = CYCLE_DAYS_SHORT
    const val PREVIEW_HORIZON_DAYS = 21

    data class CycleInfo(
        val epoch: Long,
        val daysUntilNext: Int,
    )

    data class RotateResult<T>(
        val grid: List<T>,
        val preview: List<T>,
        /** Baldigster Eintritt der Vorschau-Items (für Tab-Badge). */
        val daysUntilPreview: Int?,
        /** Tage bis Aktivierung je Vorschau-Item (gleiche Reihenfolge wie [preview]). */
        val previewDays: List<Int> = emptyList(),
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

    fun <T> rotateCatalog(
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
        previewPool: List<T>? = null,
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
            previewPool = previewPool,
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
        /** Vollständiger Katalog für „Demnächst“, falls [all] nur aktive Server-Items enthält. */
        previewPool: List<T>? = null,
    ): RotateResult<T> {
        @Suppress("UNUSED_PARAMETER")
        val _bucket = bucketSize
        @Suppress("UNUSED_PARAMETER")
        val _epoch = epoch
        val shopable = all
        val day = nowMs / 86_400_000L

        if (skipRotation || shopable.isEmpty()) {
            val grid = if (withRemaining != null) {
                shopable.map { item ->
                    withRemaining(item, remainingOf(item)?.takeIf { it > 0L })
                }
            } else shopable
            val gridIds = grid.map { idOf(it) }.toHashSet()
            val (preview, days, perItem) = pickPreview(
                pool = previewPool ?: shopable,
                gridIds = gridIds,
                day = day,
                previewCount = previewCount,
                idOf = idOf,
                priceOf = priceOf,
            )
            return RotateResult(grid, preview, days, perItem)
        }

        val timed = shopable.filter { (remainingOf(it) ?: 0L) > 0L }
        val pinned = shopable.filter {
            (remainingOf(it) ?: 0L) <= 0L && isPinned(idOf(it), priceOf(it))
        }
        val rotating = shopable.filter {
            (remainingOf(it) ?: 0L) <= 0L && !isPinned(idOf(it), priceOf(it))
        }

        val mixed: List<T> = if (rotating.isEmpty()) {
            emptyList()
        } else {
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
            val out = ArrayList<T>(activeRotating.size)
            var i = 0
            var j = 0
            var takeCheap = true
            while (i < cheap.size || j < expensive.size) {
                when {
                    takeCheap && i < cheap.size -> out.add(cheap[i++])
                    !takeCheap && j < expensive.size -> out.add(expensive[j++])
                    i < cheap.size -> out.add(cheap[i++])
                    j < expensive.size -> out.add(expensive[j++])
                }
                takeCheap = !takeCheap
            }
            out
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

        val gridIds = grid.map { idOf(it) }.toHashSet()
        val pool = when {
            !previewPool.isNullOrEmpty() -> previewPool
            rotating.isNotEmpty() -> rotating
            else -> shopable
        }
        val (preview, days, perItem) = pickPreview(
            pool = pool,
            gridIds = gridIds,
            day = day,
            previewCount = previewCount,
            idOf = idOf,
            priceOf = priceOf,
        )
        return RotateResult(grid, preview, days, perItem)
    }

    /**
     * Nächste Shop-Items, die gerade nicht im Grid sind.
     * Tage = echter Offset bis zur nächsten Aktivierung je Item.
     */
    private fun <T> pickPreview(
        pool: List<T>,
        gridIds: Set<String>,
        day: Long,
        previewCount: Int,
        idOf: (T) -> String,
        priceOf: (T) -> Int,
    ): Triple<List<T>, Int?, List<Int>> {
        if (previewCount <= 0 || pool.isEmpty()) {
            return Triple(emptyList(), null, emptyList())
        }

        val picked = ArrayList<T>(previewCount)
        val pickedDays = ArrayList<Int>(previewCount)
        val pickedIds = LinkedHashSet<String>()

        fun tryAdd(item: T, offset: Int): Boolean {
            if (picked.size >= previewCount) return false
            val id = idOf(item)
            if (id in gridIds || id in pickedIds) return false
            val days = offset.coerceIn(1, PREVIEW_HORIZON_DAYS)
            picked.add(item)
            pickedDays.add(days)
            pickedIds.add(id)
            return true
        }

        // 1) Items die heute nicht aktiv sind, bald schon (frühste zuerst)
        for (offset in 1..PREVIEW_HORIZON_DAYS) {
            if (picked.size >= previewCount) break
            val batch = pool.filter {
                val id = idOf(it)
                id !in gridIds &&
                    id !in pickedIds &&
                    !isActiveInRotation(id, day) &&
                    isActiveInRotation(id, day + offset)
            }.sortedWith(compareBy({ priceOf(it) }, { idOf(it) }))
            val cheap = batch.firstOrNull { priceOf(it) <= PRICE_CHEAP_MAX }
            val exp = batch.firstOrNull { priceOf(it) > PRICE_CHEAP_MAX }
            cheap?.let { tryAdd(it, offset) }
            exp?.let { tryAdd(it, offset) }
            for (item in batch) {
                if (picked.size >= previewCount) break
                tryAdd(item, offset)
            }
        }

        // 2) Rest außer Grid, nach baldiger Aktivierung
        if (picked.size < previewCount) {
            val rest = pool
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

        // 3) Fallback: nächster Zyklus außerhalb des Grids
        if (picked.size < previewCount) {
            val rest = pool
                .filter { idOf(it) !in pickedIds && idOf(it) !in gridIds }
                .sortedWith(compareBy({ priceOf(it) }, { idOf(it) }))
            for (item in rest) {
                if (picked.size >= previewCount) break
                val id = idOf(item)
                val off = if (isActiveInRotation(id, day)) {
                    cycleLenFor(id)
                } else {
                    daysUntilActive(id, day)
                }
                tryAdd(item, off)
            }
        }

        // 4) Wirklich nichts außerhalb? Zeige günstigste mit Zyklus-Offset
        if (picked.isEmpty() && pool.isNotEmpty()) {
            val sorted = pool.sortedWith(compareBy({ priceOf(it) }, { idOf(it) }))
            for (item in sorted) {
                if (picked.size >= previewCount) break
                val id = idOf(item)
                if (id in pickedIds) continue
                tryAdd(item, cycleLenFor(id))
            }
        }

        val soonest = pickedDays.minOrNull()
        return Triple(picked.take(previewCount), soonest, pickedDays.take(previewCount))
    }
}
