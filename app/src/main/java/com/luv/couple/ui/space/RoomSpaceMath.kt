package com.luv.couple.ui.space

import com.luv.couple.net.LuvApiClient
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val GRID_W = 48
private const val GRID_H = 64

fun zoneContains(z: LuvApiClient.RoomZone, x: Float, y: Float, pad: Float = 0f): Boolean {
    return if (z.shape == "circle") {
        hypot(x - z.cx, y - z.cy) <= z.r + pad
    } else {
        x >= z.x - pad && x <= z.x + z.w + pad &&
            y >= z.y - pad && y <= z.y + z.h + pad
    }
}

private fun pointInGreen(zones: List<LuvApiClient.RoomZone>, x: Float, y: Float): Boolean {
    return zones.any { it.isWalk && zoneContains(it, x, y, 0f) }
}

/** Avatar komplett in Grün, ohne Rot zu schneiden. */
fun walkableAt(
    zones: List<LuvApiClient.RoomZone>,
    x: Float,
    y: Float,
    avatarR: Float,
): Boolean {
    if (zones.none { it.isWalk }) return false
    val samples = ArrayList<Pair<Float, Float>>(13)
    samples += x to y
    for (i in 0 until 12) {
        val a = (i / 12f) * (Math.PI.toFloat() * 2f)
        samples += (x + cos(a) * avatarR) to (y + sin(a) * avatarR)
    }
    if (samples.any { !pointInGreen(zones, it.first, it.second) }) return false
    if (zones.any { it.isBlock && zoneContains(it, x, y, avatarR) }) return false
    return true
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
            walk[iy * GRID_W + ix] = walkableAt(zones, cx, cy, avatarR)
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

/** A* nur in Grün, um Rot herum. */
fun findPath(
    zones: List<LuvApiClient.RoomZone>,
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    avatarR: Float,
): List<Pair<Float, Float>> {
    val walk = buildWalkGrid(zones, avatarR)
    val start = nearestWalkable(walk, fromX, fromY) ?: return emptyList()
    val goal = nearestWalkable(walk, toX, toY) ?: return emptyList()
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
    while (open.isNotEmpty()) {
        open.sortBy { it.f }
        val cur = open.removeAt(0)
        val ck = key(cur.ix, cur.iy)
        if (!closed.add(ck)) continue
        if (cur.ix == goal.first && cur.iy == goal.second) {
            val path = ArrayList<Pair<Float, Float>>()
            var k = ck
            while (came.containsKey(k)) {
                val ix = k % GRID_W
                val iy = k / GRID_W
                path += cellCenter(ix, iy)
                k = came[k]!!
            }
            path.reverse()
            return path
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
    return emptyList()
}

suspend fun walkAlongPath(
    path: List<Pair<Float, Float>>,
    setPos: (Float, Float) -> Unit,
    getX: () -> Float,
    getY: () -> Float,
) {
    for ((tx, ty) in path) {
        var guard = 0
        while (hypot(tx - getX(), ty - getY()) > 0.012f && guard < 40) {
            val dist = hypot(tx - getX(), ty - getY()).coerceAtLeast(0.0001f)
            val step = 0.012f.coerceAtMost(dist)
            setPos(getX() + (tx - getX()) / dist * step, getY() + (ty - getY()) / dist * step)
            delay(50)
            guard++
        }
        setPos(tx, ty)
    }
}
