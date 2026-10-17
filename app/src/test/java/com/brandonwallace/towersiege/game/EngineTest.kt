package com.brandonwallace.towersiege.game

import org.junit.Assert.*
import org.junit.Test

class EngineTest {
    private fun eng(i: Int = 0) = Engine(Maps.all[i], i, seed = 42)
    private fun freeTile(g: Engine): Pair<Int, Int> {
        // a buildable tile adjacent to the path
        for (r in 0 until g.map.rows) for (c in 0 until g.map.cols) if (g.canPlace(c, r) && g.map.pathTiles.any { (pc, pr) -> kotlin.math.abs(pc - c) + kotlin.math.abs(pr - r) == 1 }) return c to r
        error("no tile")
    }
    private fun run(g: Engine, secs: Float) { var t = 0f; while (t < secs && !g.finished) { g.step(0.05f); t += 0.05f / g.speed * g.speed } }

    @Test fun mapsArePlayable() {
        for (m in Maps.all) {
            assertTrue(m.length > 10f)
            assertTrue(m.pathTiles.size > 10)
            assertTrue((0 until m.cols).sumOf { c -> (0 until m.rows).count { r -> m.buildable(c, r) } } > 40)
            // path segments are axis aligned
            for (i in 0 until m.path.size - 1) assertTrue(m.path[i].first == m.path[i + 1].first || m.path[i].second == m.path[i + 1].second)
        }
    }

    @Test fun posAtFollowsPath() {
        val m = Maps.all[0]
        assertEquals(m.points.first(), m.posAt(0f))
        assertEquals(m.points.last(), m.posAt(m.length + 5f))
    }

    @Test fun placeCostsGoldAndBlocksPath() {
        val g = eng(); val (c, r) = freeTile(g)
        val before = g.gold
        assertNotNull(g.place(TowerKind.ARROW, c, r))
        assertEquals(before - TowerKind.ARROW.cost, g.gold)
        assertNull(g.place(TowerKind.ARROW, c, r))
        val (pc, pr) = g.map.pathTiles.first { it.first >= 0 && it.second >= 0 && it.first < g.map.cols && it.second < g.map.rows }
        assertNull(g.place(TowerKind.ARROW, pc, pr))
    }

    @Test fun cannotAffordTower() {
        val g = eng(); g.gold = 10; val (c, r) = freeTile(g)
        assertNull(g.place(TowerKind.CANNON, c, r)); assertEquals(10, g.gold)
    }

    @Test fun upgradeRequiresBranchAtLevel3() {
        val g = eng(); g.gold = 100000; val (c, r) = freeTile(g)
        val t = g.place(TowerKind.ARROW, c, r)!!
        val d1 = t.damage()
        assertTrue(g.upgrade(t)); assertTrue(g.upgrade(t)); assertEquals(3, t.level)
        assertTrue(t.damage() > d1)
        assertFalse(g.upgrade(t))
        assertFalse(g.upgrade(t, Branch.CANNON_BIG))
        assertTrue(g.upgrade(t, Branch.ARROW_SNIPER)); assertEquals(Branch.ARROW_SNIPER, t.branch)
        assertTrue(g.upgrade(t)); assertEquals(Engine.MAX_LEVEL, t.level)
        assertNull(t.upgradeCost()); assertFalse(g.upgrade(t))
    }

    @Test fun sellRefunds70Percent() {
        val g = eng(); val (c, r) = freeTile(g)
        val t = g.place(TowerKind.ARROW, c, r)!!; val gold = g.gold
        g.sell(t)
        assertEquals(gold + (TowerKind.ARROW.cost * 0.7f).toInt(), g.gold)
        assertTrue(g.towers.isEmpty())
    }

    @Test fun targetingPriorities() {
        val g = eng(); val t = Tower(99, TowerKind.ARROW, 4, 3).also { g.towers += it }
        // place enemies along path near tower
        val a = g.spawn(EnemyKind.GRUNT, 5.5f); val b = g.spawn(EnemyKind.TANK, 6.5f); val c = g.spawn(EnemyKind.GRUNT, 7.5f)
        c.hp = 5f
        val inRange = listOf(a, b, c).filter { it.pos.dist(t.pos) <= t.range() + it.kind.radius }
        assumeAll(inRange.size == 3)
        t.targeting = Targeting.FIRST; assertSame(c, g.pickTarget(t))
        t.targeting = Targeting.LAST; assertSame(a, g.pickTarget(t))
        t.targeting = Targeting.STRONG; assertSame(b, g.pickTarget(t))
        assertEquals(Targeting.FIRST, Targeting.CLOSE.next())
    }
    private fun assumeAll(b: Boolean) = org.junit.Assume.assumeTrue(b)

    @Test fun cannonCannotHitFlyers() {
        val g = eng(); val t = Tower(1, TowerKind.CANNON, 4, 3).also { g.towers += it }
        g.spawn(EnemyKind.FLYER, 6f)
        assertNull(g.pickTarget(t))
    }

    @Test fun speedCyclesAndScalesTime() {
        val g = eng(); assertEquals(1, g.speed)
        g.cycleSpeed(); assertEquals(2, g.speed); g.cycleSpeed(); assertEquals(3, g.speed); g.cycleSpeed(); assertEquals(1, g.speed)
        val a = eng(); a.callWave(); a.step(0.05f)
        val b = eng(); b.speed = 3; b.callWave(); b.step(0.05f)
        assertEquals(a.time * 3, b.time, 1e-4f)
    }

    @Test fun earlyCallPaysBonus() {
        val g = eng()
        assertEquals(0, g.callWave()); assertEquals(1, g.wave)
        g.cheats.oneHitKill = true
        // let it clear with no towers: kill all via cheat skip
        g.spawnQueue.clear(); g.enemies.clear(); g.step(0.02f)
        assertTrue(g.nextWaveTimer > 0f)
        val gold = g.gold
        val bonus = g.callWave()
        assertTrue(bonus > 0); assertEquals(gold + bonus, g.gold); assertEquals(2, g.wave)
    }

    @Test fun leaksCostLivesAndCanLose() {
        val g = eng(); g.lives = 1
        g.spawn(EnemyKind.GRUNT, g.map.length - 0.01f); g.wave = 1
        g.step(0.05f)
        assertEquals(0, g.lives); assertTrue(g.lost)
    }

    @Test fun godModePreventsLifeLoss() {
        val g = eng(); g.cheats.godMode = true; val l = g.lives
        g.spawn(EnemyKind.BOSS, g.map.length - 0.01f); g.step(0.05f)
        assertEquals(l, g.lives)
    }

    @Test fun towersKillEnemiesAndEarnGold() {
        val g = eng(); g.gold = 100000
        var placed = 0
        for (r in 0 until g.map.rows) for (c in 0 until g.map.cols) if (placed < 10 && g.canPlace(c, r) && g.map.pathTiles.any { (pc, pr) -> kotlin.math.abs(pc - c) + kotlin.math.abs(pr - r) == 1 }) { g.place(TowerKind.ARROW, c, r); placed++ }
        val gold = g.gold
        g.callWave(); run(g, 40f)
        assertTrue(g.totalKills > 0); assertTrue(g.gold > gold)
    }

    @Test fun fullGameWithCheatsWins() {
        val g = eng(); g.cheats.oneHitKill = true; g.gold = 100000
        for (r in 0 until g.map.rows) for (c in 0 until g.map.cols) if (g.canPlace(c, r)) g.place(TowerKind.TESLA, c, r)
        g.speed = 3
        g.callWave()
        var guard = 0
        while (!g.finished && guard++ < 200000) { g.step(0.05f); if (g.canCallEarly()) g.callWave() }
        assertTrue(g.won)
    }

    @Test fun bossStunsTowersUnlessInvincible() {
        val g = eng(); val t = Tower(1, TowerKind.ARROW, 4, 3).also { g.towers += it }
        val b = g.spawn(EnemyKind.BOSS, 6f); b.abilityTimer = 0f
        g.step(0.02f); assertTrue(t.stunned > 0)
        val g2 = eng(); g2.cheats.invincibleTowers = true; val t2 = Tower(1, TowerKind.ARROW, 4, 3).also { g2.towers += it }
        val b2 = g2.spawn(EnemyKind.BOSS, 6f); b2.abilityTimer = 0f
        g2.step(0.02f); assertEquals(0f, t2.stunned, 0f)
    }

    @Test fun splitterSpawnsChildren() {
        val g = eng(); val s = g.spawn(EnemyKind.SPLITTER, 3f)
        g.damage(s, 1e6f, null)
        assertEquals(3, g.enemies.count { it.kind == EnemyKind.SWARM })
    }

    @Test fun wavesGrowAndHaveBosses() {
        val m = Maps.all[0]
        assertTrue(Waves.build(m, 10).size > Waves.build(m, 1).size)
        assertTrue(Waves.build(m, 5).any { it.kind == EnemyKind.BOSS })
        assertTrue(Waves.hpMultiplier(m, 10) > Waves.hpMultiplier(m, 1))
        assertEquals(Waves.build(m, 7), Waves.build(m, 7))
    }

    @Test fun jumpToWave() {
        val g = eng(); g.jumpToWave(12); assertEquals(12, g.wave)
        g.skipWave(); assertEquals(13, g.wave)
    }
}

class SaveTest {
    @Test fun roundTrip() {
        val p = Profile(unlockedMaps = 3, musicOn = false, sfxOn = true, totalKills = 1234, cheatsUnlocked = true)
        p.stars[0] = 3; p.stars[1] = 2
        p.run = RunSnapshot(1, 7, 555, 12, listOf(TowerSnap(TowerKind.TESLA, 3, 4, 4, Branch.TESLA_STORM, Targeting.STRONG, 600), TowerSnap(TowerKind.ARROW, 1, 1, 1, null, Targeting.FIRST, 60)))
        val q = Profile.decode(p.encode())
        assertEquals(p.encode(), q.encode())
        assertEquals(2, q.run!!.towers.size)
        assertFalse(q.musicOn)
    }

    @Test fun corruptAndEmptyInputIsSafe() {
        assertEquals(1, Profile.decode(null).unlockedMaps)
        val q = Profile.decode("maps=99\nstars=x,2\nt=BOGUS,1\nmusic=false")
        assertEquals(Maps.all.size, q.unlockedMaps); assertFalse(q.musicOn)
    }

    @Test fun snapshotRestore() {
        val g = Engine(Maps.all[0], 0, 1); g.gold = 5000
        g.place(TowerKind.FROST, 0, 0); g.upgrade(g.towers[0])
        g.towers[0].targeting = Targeting.LAST
        val s = g.snapshot()
        val h = Engine(Maps.all[0], 0, 2); h.restore(s)
        assertEquals(g.gold, h.gold); assertEquals(2, h.towers[0].level); assertEquals(Targeting.LAST, h.towers[0].targeting)
    }

    @Test fun starsAndUnlocks() {
        assertEquals(3, Profile.starsFor(20, 20)); assertEquals(2, Profile.starsFor(12, 20)); assertEquals(1, Profile.starsFor(1, 20))
        val p = Profile()
        assertTrue(p.towerUnlocked(TowerKind.ARROW)); assertFalse(p.towerUnlocked(TowerKind.TESLA))
        p.unlockedMaps = 2; assertTrue(p.towerUnlocked(TowerKind.TESLA))
        assertEquals("001429", ADMIN_CODE)
    }
}
