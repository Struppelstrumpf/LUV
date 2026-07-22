package com.luv.couple.ui.space

import com.luv.couple.net.LuvApiClient
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val GRID_W = 56
private const val GRID_H = 72

fun zoneContains(z: LuvApiClient.RoomZone, x: Float, y: Float, pad: Float = 0f): Boolean {
    return when (z.shape) {
        "circle" -> hypot(x - z.cx, y - z.cy) <= z.r + pad
        "poly" -> {
            if (pointInPoly(z.points, x, y)) true
            else if (pad > 0f) {
                z.points.any { (px, py) -> hypot(x - px, y - py) <= pad }
            } else false
        }
        else ->
            x >= z.x - pad && x <= z.x + z.w + pad &&
                y >= z.y - pad && y <= z.y + z.h + pad
    }
}

private fun pointInPoly(points: List<Pair<Float, Float>>, x: Float, y: Float): Boolean {
    if (points.size < 3) return false
    var inside = false
    var j = points.lastIndex
    for (i in points.indices) {
        val (xi, yi) = points[i]
        val (xj, yj) = points[j]
        val intersect = (yi > y) != (yj > y) &&
            x < (xj - xi) * (y - yi) / (yj - yi + 1e-12f) + xi
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

private fun pointInGreen(zones: List<LuvApiClient.RoomZone>, x: Float, y: Float): Boolean {
    return zones.any { it.isWalk && zoneContains(it, x, y, 0f) }
}

/** Laufbar nur auf Grün; Rot darin = Sperre (Loch). */
fun walkableAt(
    zones: List<LuvApiClient.RoomZone>,
    x: Float,
    y: Float,
    avatarR: Float,
): Boolean {
    if (!pointInGreen(zones, x, y)) return false
    // Pad: auch schmale Rots blockieren (Grid-Zentren sonst daneben)
    val blockPad = (avatarR * 0.45f).coerceIn(0.008f, 0.022f)
    if (zones.any { it.isBlock && zoneContains(it, x, y, blockPad) }) return false
    return true
}

/** Zelle trifft Rot (AABB), damit dünne Sperren nicht zwischen Zellmitten verschwinden. */
private fun cellOverlapsRed(zones: List<LuvApiClient.RoomZone>, ix: Int, iy: Int): Boolean {
    val x0 = ix / GRID_W.toFloat()
    val x1 = (ix + 1) / GRID_W.toFloat()
    val y0 = iy / GRID_H.toFloat()
    val y1 = (iy + 1) / GRID_H.toFloat()
    val cx = (x0 + x1) * 0.5f
    val cy = (y0 + y1) * 0.5f
    return zones.any { z ->
        if (!z.isBlock) return@any false
        when (z.shape) {
            "rect" -> !(z.x + z.w <= x0 || z.x >= x1 || z.y + z.h <= y0 || z.y >= y1)
            else -> zoneContains(z, cx, cy, 0.012f)
        }
    }
}

/** Nächster laufbarer Punkt in der Nähe von [x],[y] (für Sitze auf Rot). */
fun nearestWalkablePoint(
    zones: List<LuvApiClient.RoomZone>,
    x: Float,
    y: Float,
    avatarR: Float,
): Pair<Float, Float>? {
    val pathR = (avatarR * 0.75f).coerceAtLeast(0.008f)
    val walk = buildWalkGrid(zones, pathR)
    val cell = nearestWalkable(walk, x, y) ?: return null
    return cellCenter(cell.first, cell.second)
}

private fun cellCenter(ix: Int, iy: Int): Pair<Float, Float> =
    ((ix + 0.5f) / GRID_W) to ((iy + 0.5f) / GRID_H)

private fun toCell(x: Float, y: Float): Pair<Int, Int> =
    (x * GRID_W).toInt().coerceIn(0, GRID_W - 1) to
        (y * GRID_H).toInt().coerceIn(0, GRID_H - 1)

private fun buildWalkGrid(zones: List<LuvApiClient.RoomZone>, avatarR: Float): BooleanArray {
    val walk = BooleanArray(GRID_W * GRID_H)
    for (iy in 0 until GRID_H) {
        for (ix in 0 until GRID_W) {
            val (cx, cy) = cellCenter(ix, iy)
            walk[iy * GRID_W + ix] =
                walkableAt(zones, cx, cy, avatarR) && !cellOverlapsRed(zones, ix, iy)
        }
    }
    return walk
}

private fun nearestWalkable(
    walk: BooleanArray,
    x: Float,
    y: Float,
): Pair<Int, Int>? {
    val (sx, sy) = toCell(x, y)
    if (walk[sy * GRID_W + sx]) return sx to sy
    var best: Pair<Int, Int>? = null
    var bestD = Float.MAX_VALUE
    for (iy in 0 until GRID_H) {
        for (ix in 0 until GRID_W) {
            if (!walk[iy * GRID_W + ix]) continue
            val (cx, cy) = cellCenter(ix, iy)
            val d = hypot(cx - x, cy - y)
            if (d < bestD) {
                bestD = d
                best = ix to iy
            }
        }
    }
    return best
}

private fun lineClear(
    zones: List<LuvApiClient.RoomZone>,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    avatarR: Float,
): Boolean {
    val dist = hypot(x1 - x0, y1 - y0)
    val steps = (dist / 0.012f).toInt().coerceIn(1, 80)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val x = x0 + (x1 - x0) * t
        val y = y0 + (y1 - y0) * t
        if (!walkableAt(zones, x, y, avatarR)) return false
    }
    return true
}

/** String-Pull: entfernt Zickzack-Zwischenpunkte. */
private fun smoothPath(
    raw: List<Pair<Float, Float>>,
    zones: List<LuvApiClient.RoomZone>,
    avatarR: Float,
): List<Pair<Float, Float>> {
    if (raw.size <= 2) return raw
    val out = ArrayList<Pair<Float, Float>>()
    out += raw.first()
    var i = 0
    while (i < raw.lastIndex) {
        var best = i + 1
        var j = raw.lastIndex
        while (j > i + 1) {
            val a = raw[i]
            val b = raw[j]
            if (lineClear(zones, a.first, a.second, b.first, b.second, avatarR)) {
                best = j
                break
            }
            j--
        }
        out += raw[best]
        i = best
    }
    return out
}

/** A* nur in Grün, um Rot herum — Ergebnis geglättet. */
fun findPath(
    zones: List<LuvApiClient.RoomZone>,
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    avatarR: Float,
): List<Pair<Float, Float>> {
    val pathR = (avatarR * 0.75f).coerceAtLeast(0.008f)
    val walk = buildWalkGrid(zones, pathR)
    val start = nearestWalkable(walk, fromX, fromY) ?: return emptyList()
    val goal = nearestWalkable(walk, toX, toY) ?: return emptyList()
    if (start.first == goal.first && start.second == goal.second) {
        val end = if (walkableAt(zones, toX, toY, pathR)) toX to toY
        else cellCenter(goal.first, goal.second)
        return listOf(end)
    }
    data class Node(val ix: Int, val iy: Int, val g: Float, val f: Float)
    fun key(ix: Int, iy: Int) = iy * GRID_W + ix
    val open = ArrayList<Node>()
    open += Node(start.first, start.second, 0f, 0f)
    val came = HashMap<Int, Int>()
    val gScore = HashMap<Int, Float>()
    gScore[key(start.first, start.second)] = 0f
    val closed = HashSet<Int>()
    val dirs = arrayOf(
        1 to 0, -1 to 0, 0 to 1, 0 to -1,
        1 to 1, 1 to -1, -1 to 1, -1 to -1
    )
    var found: Int? = null
    while (open.isNotEmpty()) {
        open.sortBy { it.f }
        val cur = open.removeAt(0)
        val ck = key(cur.ix, cur.iy)
        if (!closed.add(ck)) continue
        if (cur.ix == goal.first && cur.iy == goal.second) {
            found = ck
            break
        }
        for ((dx, dy) in dirs) {
            val nix = cur.ix + dx
            val niy = cur.iy + dy
            if (nix !in 0 until GRID_W || niy !in 0 until GRID_H) continue
            val nk = key(nix, niy)
            if (!walk[nk] || closed.contains(nk)) continue
            if (dx != 0 && dy != 0) {
                if (!walk[key(cur.ix + dx, cur.iy)] || !walk[key(cur.ix, cur.iy + dy)]) continue
            }
            val step = if (dx != 0 && dy != 0) 1.414f else 1f
            val ng = (gScore[ck] ?: 0f) + step
            if (ng >= (gScore[nk] ?: Float.MAX_VALUE)) continue
            came[nk] = ck
            gScore[nk] = ng
            val h = hypot((nix - goal.first).toFloat(), (niy - goal.second).toFloat())
            open += Node(nix, niy, ng, ng + h)
        }
    }
    val endKey = found ?: return emptyList()
    val raw = ArrayList<Pair<Float, Float>>()
    var k: Int = endKey
    while (came.containsKey(k)) {
        val ix = k % GRID_W
        val iy = k / GRID_W
        raw += cellCenter(ix, iy)
        k = came[k]!!
    }
    raw.reverse()
    if (raw.isEmpty()) {
        raw += cellCenter(goal.first, goal.second)
    }
    if (walkableAt(zones, toX, toY, pathR)) {
        val last = raw.last()
        if (hypot(last.first - toX, last.second - toY) > 0.003f) {
            raw += toX to toY
        }
    }
    return smoothPath(raw, zones, pathR)
}

/**
 * Gleichmäßiges Laufen entlang geglätteter Wegpunkte.
 */
suspend fun walkAlongPath(
    path: List<Pair<Float, Float>>,
    setPos: (Float, Float) -> Unit,
    getX: () -> Float,
    getY: () -> Float,
    onStep: (() -> Unit)? = null,
    speedPerSec: Float = 0.48f,
) {
    if (path.isEmpty()) return
    val frameMs = 16L
    val dt = frameMs / 1000f
    for ((tx, ty) in path) {
        var guard = 0
        while (guard < 500) {
            val dx = tx - getX()
            val dy = ty - getY()
            val dist = hypot(dx, dy)
            if (dist <= 0.004f) break
            val step = (speedPerSec * dt).coerceAtMost(dist)
            setPos(getX() + dx / dist * step, getY() + dy / dist * step)
            onStep?.invoke()
            delay(frameMs)
            guard++
        }
        setPos(tx, ty)
        onStep?.invoke()
    }
}
