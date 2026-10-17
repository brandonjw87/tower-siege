package com.brandonwallace.towersiege.game

/** Static game data: towers, enemies, maps. Pure Kotlin, no Android deps. */

enum class Targeting(val label: String) {
    FIRST("First"), LAST("Last"), STRONG("Strong"), CLOSE("Close");
    fun next(): Targeting = entries[(ordinal + 1) % entries.size]
}

enum class TowerKind(
    val label: String,
    val cost: Int,
    val baseDamage: Float,
    val baseRange: Float,
    val baseRate: Float, // shots per second
    val unlockMap: Int,   // map index you must have reached to unlock
    val blurb: String,
) {
    ARROW("Archer", 60, 9f, 2.6f, 2.2f, 0, "Fast single shots. Hits air."),
    CANNON("Cannon", 100, 22f, 2.3f, 0.75f, 0, "Explosive splash damage."),
    FROST("Frost", 90, 4f, 2.2f, 1.2f, 0, "Slows enemies in an area."),
    TESLA("Tesla", 150, 16f, 2.4f, 1.0f, 1, "Lightning chains between foes."),
    LASER("Laser", 220, 40f, 3.0f, 8f, 2, "Ramping beam melts armor."),
    MORTAR("Mortar", 180, 45f, 4.2f, 0.4f, 3, "Huge range, big shells.");
}

/** Branch upgrades unlocked at level 3. Each kind has two. */
enum class Branch(val kind: TowerKind, val label: String, val desc: String) {
    ARROW_SNIPER(TowerKind.ARROW, "Longbow", "+80% range, +60% damage, crits"),
    ARROW_RAPID(TowerKind.ARROW, "Volley", "Triple rate, shoots 2 targets"),
    CANNON_BIG(TowerKind.CANNON, "Siege Bomb", "Bigger blast, x2 damage"),
    CANNON_CLUSTER(TowerKind.CANNON, "Cluster", "Fast fire, burning ground"),
    FROST_DEEP(TowerKind.FROST, "Deep Freeze", "Chance to freeze solid"),
    FROST_SHARD(TowerKind.FROST, "Ice Shards", "Strong damage + slow"),
    TESLA_STORM(TowerKind.TESLA, "Storm", "Chains to 7 targets"),
    TESLA_OVER(TowerKind.TESLA, "Overload", "x2.5 damage, stuns"),
    LASER_PRISM(TowerKind.LASER, "Prism", "Beam splits to 3 foes"),
    LASER_SOLAR(TowerKind.LASER, "Solar Lance", "Ramp x3 faster, huge DPS"),
    MORTAR_NUKE(TowerKind.MORTAR, "Big Bertha", "Massive blast radius"),
    MORTAR_NAPALM(TowerKind.MORTAR, "Napalm", "Leaves fire that burns");

    companion object { fun of(k: TowerKind) = entries.filter { it.kind == k } }
}

enum class EnemyKind(
    val label: String, val hp: Float, val speed: Float, val reward: Int,
    val armor: Float, val flying: Boolean = false, val leak: Int = 1, val radius: Float = 0.28f,
) {
    GRUNT("Grunt", 40f, 1.0f, 5, 0f),
    RUNNER("Runner", 28f, 1.9f, 5, 0f, radius = 0.22f),
    TANK("Armored", 160f, 0.6f, 12, 6f, leak = 2, radius = 0.36f),
    SWARM("Swarmling", 14f, 1.4f, 2, 0f, radius = 0.17f),
    HEALER("Medic", 70f, 0.9f, 10, 1f),
    FLYER("Wasp", 45f, 1.3f, 8, 0f, flying = true, radius = 0.24f),
    SPLITTER("Splitter", 90f, 0.85f, 8, 2f, radius = 0.32f),
    BOSS("Warlord", 1400f, 0.45f, 120, 10f, leak = 10, radius = 0.5f);
}

data class P(val x: Float, val y: Float) {
    operator fun plus(o: P) = P(x + o.x, y + o.y)
    operator fun minus(o: P) = P(x - o.x, y - o.y)
    operator fun times(s: Float) = P(x * s, y * s)
    fun len() = kotlin.math.sqrt(x * x + y * y)
    fun dist(o: P) = (this - o).len()
}

class MapDef(
    val name: String,
    val theme: Int, // 0 grass, 1 desert, 2 snow, 3 lava
    val cols: Int, val rows: Int,
    /** Waypoints in tile coordinates (centres). Consecutive points share a row or column. */
    val path: List<Pair<Int, Int>>,
    val waves: Int,
    val startGold: Int,
    val startLives: Int,
    val hpScale: Float,
) {
    /** All tiles covered by the path. */
    val pathTiles: Set<Pair<Int, Int>> by lazy {
        val s = LinkedHashSet<Pair<Int, Int>>()
        for (i in 0 until path.size - 1) {
            val (ax, ay) = path[i]; val (bx, by) = path[i + 1]
            val dx = Integer.signum(bx - ax); val dy = Integer.signum(by - ay)
            var x = ax; var y = ay
            s += x to y
            while (x != bx || y != by) { x += dx; y += dy; s += x to y }
        }
        s
    }
    val points: List<P> by lazy { path.map { P(it.first + 0.5f, it.second + 0.5f) } }
    val length: Float by lazy { (0 until points.size - 1).sumOf { points[it].dist(points[it + 1]).toDouble() }.toFloat() }

    fun buildable(c: Int, r: Int) = c in 0 until cols && r in 0 until rows && (c to r) !in pathTiles

    /** Position at distance d along the path. */
    fun posAt(d: Float): P {
        var rem = d
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]; val seg = a.dist(b)
            if (rem <= seg) return a + (b - a) * (rem / seg)
            rem -= seg
        }
        return points.last()
    }
}

object Maps {
    val all = listOf(
        MapDef("Green Meadow", 0, 16, 9,
            listOf(-1 to 2, 4 to 2, 4 to 6, 9 to 6, 9 to 1, 13 to 1, 13 to 5, 16 to 5), 15, 220, 20, 1.0f),
        MapDef("Dune Pass", 1, 16, 9,
            listOf(-1 to 7, 2 to 7, 2 to 1, 6 to 1, 6 to 7, 10 to 7, 10 to 2, 14 to 2, 14 to 6, 16 to 6), 20, 260, 20, 1.15f),
        MapDef("Frostfang", 2, 16, 9,
            listOf(8 to -1, 8 to 2, 2 to 2, 2 to 6, 13 to 6, 13 to 3, 16 to 3), 25, 300, 15, 1.35f),
        MapDef("Ember Keep", 3, 16, 9,
            listOf(-1 to 1, 14 to 1, 14 to 4, 1 to 4, 1 to 7, 16 to 7), 30, 340, 15, 1.6f),
    )
}
