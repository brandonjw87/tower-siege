package com.brandonwallace.towersiege.game

/** Persistent profile. Serialized to a simple line format (pure Kotlin, unit testable). */
data class Profile(
    var unlockedMaps: Int = 1,
    var stars: IntArray = IntArray(Maps.all.size),
    var musicOn: Boolean = true,
    var sfxOn: Boolean = true,
    var totalKills: Long = 0,
    var cheatsUnlocked: Boolean = false,
    var run: RunSnapshot? = null,
) {
    fun towerUnlocked(k: TowerKind) = k.unlockMap < unlockedMaps

    fun encode(): String = buildString {
        appendLine("v=1")
        appendLine("maps=$unlockedMaps")
        appendLine("stars=${stars.joinToString(",")}")
        appendLine("music=$musicOn")
        appendLine("sfx=$sfxOn")
        appendLine("kills=$totalKills")
        appendLine("cheats=$cheatsUnlocked")
        run?.let { r ->
            appendLine("run=${r.mapIndex},${r.wave},${r.gold},${r.lives}")
            for (t in r.towers) appendLine("t=${t.kind.name},${t.col},${t.row},${t.level},${t.branch?.name ?: "-"},${t.targeting.name},${t.invested}")
        }
    }

    companion object {
        fun decode(s: String?): Profile {
            val p = Profile()
            if (s.isNullOrBlank()) return p
            var run: RunSnapshot? = null
            val towers = ArrayList<TowerSnap>()
            for (line in s.lineSequence()) {
                val i = line.indexOf('='); if (i <= 0) continue
                val k = line.substring(0, i); val v = line.substring(i + 1)
                try {
                    when (k) {
                        "maps" -> p.unlockedMaps = v.toInt().coerceIn(1, Maps.all.size)
                        "stars" -> v.split(",").forEachIndexed { idx, x -> if (idx < p.stars.size) p.stars[idx] = x.toInt().coerceIn(0, 3) }
                        "music" -> p.musicOn = v.toBoolean()
                        "sfx" -> p.sfxOn = v.toBoolean()
                        "kills" -> p.totalKills = v.toLong()
                        "cheats" -> p.cheatsUnlocked = v.toBoolean()
                        "run" -> { val a = v.split(","); run = RunSnapshot(a[0].toInt().coerceIn(0, Maps.all.size - 1), a[1].toInt(), a[2].toInt(), a[3].toInt(), emptyList()) }
                        "t" -> { val a = v.split(","); towers += TowerSnap(TowerKind.valueOf(a[0]), a[1].toInt(), a[2].toInt(), a[3].toInt().coerceIn(1, Engine.MAX_LEVEL), if (a[4] == "-") null else Branch.valueOf(a[4]), Targeting.valueOf(a[5]), a[6].toInt()) }
                    }
                } catch (_: Exception) { /* skip corrupt line */ }
            }
            p.run = run?.copy(towers = towers)
            return p
        }

        /** Stars for a victory based on remaining lives. */
        fun starsFor(lives: Int, startLives: Int): Int = when {
            lives >= startLives -> 3
            lives >= startLives / 2 -> 2
            else -> 1
        }
    }

    override fun equals(other: Any?) = other is Profile && encode() == other.encode()
    override fun hashCode() = encode().hashCode()
}

const val SAVE_PREFS = "towersiege_save_v1"
const val ADMIN_CODE = "001429"
