package com.brandonwallace.towersiege.game

import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class Tower(
    val id: Int, val kind: TowerKind, val col: Int, val row: Int,
) {
    var level = 1
    var branch: Branch? = null
    var targeting = Targeting.FIRST
    var cooldown = 0f
    var angle = 0f
    var invested = kind.cost
    var kills = 0
    var damageDealt = 0f
    var stunned = 0f
    var beamTargets: List<Int> = emptyList()
    var ramp = 0f
    var recoil = 0f
    val pos get() = P(col + 0.5f, row + 0.5f)

    fun damage(): Float {
        var d = kind.baseDamage * (1f + 0.5f * (level - 1))
        when (branch) {
            Branch.ARROW_SNIPER -> d *= 1.6f
            Branch.CANNON_BIG -> d *= 2f
            Branch.FROST_SHARD -> d *= 3.5f
            Branch.TESLA_OVER -> d *= 2.5f
            Branch.LASER_SOLAR -> d *= 1.4f
            Branch.MORTAR_NUKE -> d *= 1.3f
            else -> {}
        }
        return d
    }
    fun range(): Float {
        var r = kind.baseRange * (1f + 0.08f * (level - 1))
        if (branch == Branch.ARROW_SNIPER) r *= 1.8f
        return r
    }
    fun rate(): Float {
        var r = kind.baseRate * (1f + 0.12f * (level - 1))
        if (branch == Branch.ARROW_RAPID) r *= 3f
        if (branch == Branch.CANNON_CLUSTER) r *= 1.8f
        return r
    }
    fun splash(): Float = when (kind) {
        TowerKind.CANNON -> if (branch == Branch.CANNON_BIG) 1.6f else 1.0f
        TowerKind.MORTAR -> if (branch == Branch.MORTAR_NUKE) 2.4f else 1.4f
        TowerKind.FROST -> 1.1f
        else -> 0f
    }
    fun chains(): Int = if (kind != TowerKind.TESLA) 0 else if (branch == Branch.TESLA_STORM) 7 else 2 + level / 2
    fun canHitAir() = kind != TowerKind.CANNON && kind != TowerKind.MORTAR

    /** Cost of the next linear upgrade or of a branch purchase, null when maxed. */
    fun upgradeCost(): Int? = if (level >= Engine.MAX_LEVEL) null else (kind.cost * (0.55f + 0.35f * level)).toInt().let { if (level == 3) it * 2 else it }
    fun needsBranch() = level == 3 && branch == null
    fun sellValue() = (invested * 0.7f).toInt()
    fun dps(): Float = damage() * rate()
}

class Enemy(val id: Int, val kind: EnemyKind, val maxHp: Float, var d: Float = 0f) {
    var hp = maxHp
    var slow = 0f; var slowAmt = 0f
    var frozen = 0f
    var stun = 0f
    var burn = 0f; var burnDps = 0f
    var hit = 0f
    var alive = true
    var leaked = false
    var abilityTimer = 2f
    var wobble = Random.nextFloat() * 6f
    var pos = P(0f, 0f)
    fun speedMul(): Float = if (frozen > 0 || stun > 0) 0f else if (slow > 0) 1f - slowAmt else 1f
}

class Projectile(
    var pos: P, val targetId: Int, var targetPos: P, val dmg: Float, val speed: Float,
    val splash: Float, val tower: Tower, val style: Int, val arc: Boolean,
) { var alive = true; val start = pos; var t = 0f }

class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val color: Long, val size: Float, val glow: Boolean = false)
class FloatText(val text: String, var x: Float, var y: Float, var life: Float, val color: Long)
class Bolt(val pts: List<P>, var life: Float, val color: Long)
class Ring(val x: Float, val y: Float, val maxR: Float, var life: Float, val maxLife: Float, val color: Long)
class FireZone(val x: Float, val y: Float, val r: Float, var life: Float, val dps: Float, val tower: Tower)

enum class GameEvent { SHOOT, CANNON, ZAP, FREEZE, HIT, DIE, LEAK, WAVE, BUILD, UPGRADE, SELL, WIN, LOSE, BOSS, COIN }

class Cheats {
    var invincibleTowers = false
    var godMode = false
    var oneHitKill = false
    var freeBuild = false
}

data class SpawnEntry(val kind: EnemyKind, val delay: Float)

object Waves {
    /** Deterministic wave composition. */
    fun build(map: MapDef, wave: Int): List<SpawnEntry> {
        val r = Random(wave * 7919 + map.name.hashCode())
        val out = ArrayList<SpawnEntry>()
        val n = 6 + wave * 2
        val pool = buildList {
            add(EnemyKind.GRUNT)
            if (wave >= 2) add(EnemyKind.RUNNER)
            if (wave >= 4) add(EnemyKind.SWARM)
            if (wave >= 5) add(EnemyKind.FLYER)
            if (wave >= 6) add(EnemyKind.TANK)
            if (wave >= 8) add(EnemyKind.HEALER)
            if (wave >= 10) add(EnemyKind.SPLITTER)
        }
        val themeKind = pool[r.nextInt(pool.size)]
        for (i in 0 until n) {
            val k = if (r.nextFloat() < 0.4f) themeKind else pool[r.nextInt(pool.size)]
            val gap = when (k) { EnemyKind.SWARM -> 0.25f; EnemyKind.TANK -> 1.1f; else -> 0.6f }
            if (k == EnemyKind.SWARM) repeat(2) { out += SpawnEntry(k, 0.2f) }
            out += SpawnEntry(k, gap)
        }
        if (wave % 5 == 0) out += SpawnEntry(EnemyKind.BOSS, 1.5f)
        return out
    }
    fun hpMultiplier(map: MapDef, wave: Int): Float = map.hpScale * (1f + 0.11f * (wave - 1)).pow(1.25f)
}

class Engine(val map: MapDef, val mapIndex: Int, val seed: Long = System.nanoTime()) {
    companion object {
        const val MAX_LEVEL = 5
        const val WAVE_GAP = 18f
        const val SPEED_LEVELS = 3
    }
    val rnd = Random(seed)
    val cheats = Cheats()
    var gold = map.startGold
    var lives = map.startLives
    var wave = 0            // number of waves started
    var spawnQueue = ArrayDeque<SpawnEntry>()
    var spawnTimer = 0f
    var nextWaveTimer = -1f // <0 means waiting for player to start
    var speed = 1
    var paused = false
    var time = 0f
    var won = false
    var lost = false
    var totalKills = 0
    var shake = 0f
    private var nextId = 1

    val towers = ArrayList<Tower>()
    val enemies = ArrayList<Enemy>()
    val projectiles = ArrayList<Projectile>()
    val particles = ArrayList<Particle>()
    val texts = ArrayList<FloatText>()
    val bolts = ArrayList<Bolt>()
    val rings = ArrayList<Ring>()
    val fires = ArrayList<FireZone>()
    val events = ArrayList<GameEvent>()

    val waveInProgress get() = spawnQueue.isNotEmpty() || enemies.isNotEmpty()
    val finished get() = won || lost

    fun towerAt(c: Int, r: Int) = towers.firstOrNull { it.col == c && it.row == r }
    fun canPlace(c: Int, r: Int) = map.buildable(c, r) && towerAt(c, r) == null

    fun place(kind: TowerKind, c: Int, r: Int): Tower? {
        if (!canPlace(c, r)) return null
        val cost = if (cheats.freeBuild) 0 else kind.cost
        if (gold < cost) return null
        gold -= cost
        val t = Tower(nextId++, kind, c, r)
        towers += t
        burst(t.pos.x, t.pos.y, 18, 0xFFFFE08AL, 2.5f)
        rings += Ring(t.pos.x, t.pos.y, 0.9f, 0.4f, 0.4f, 0xFFFFFFFFL)
        events += GameEvent.BUILD
        return t
    }

    fun upgrade(t: Tower, branch: Branch? = null): Boolean {
        val cost = t.upgradeCost() ?: return false
        if (t.needsBranch()) {
            if (branch == null || branch.kind != t.kind) return false
        }
        val pay = if (cheats.freeBuild) 0 else cost
        if (gold < pay) return false
        gold -= pay
        t.invested += cost
        if (t.needsBranch()) t.branch = branch
        t.level++
        burst(t.pos.x, t.pos.y, 26, 0xFF7CF7FFL, 3f)
        rings += Ring(t.pos.x, t.pos.y, 1.2f, 0.5f, 0.5f, 0xFF7CF7FFL)
        texts += FloatText("LV ${t.level}", t.pos.x, t.pos.y - 0.4f, 1.0f, 0xFF7CF7FFL)
        events += GameEvent.UPGRADE
        return true
    }

    fun sell(t: Tower) {
        if (!towers.remove(t)) return
        gold += t.sellValue()
        burst(t.pos.x, t.pos.y, 14, 0xFFFFD54FL, 2f)
        texts += FloatText("+${t.sellValue()}", t.pos.x, t.pos.y, 1f, 0xFFFFD54FL)
        events += GameEvent.SELL
    }

    /** Start the next wave. If called early (during countdown or while a wave runs), pays a bonus. Returns bonus. */
    fun callWave(): Int {
        if (finished || wave >= map.waves) return 0
        var bonus = 0
        if (wave > 0) {
            bonus = if (nextWaveTimer > 0) (nextWaveTimer * 2f).toInt() + 5 else if (waveInProgress) 15 + wave else 0
            if (spawnQueue.isNotEmpty()) return 0 // can't stack while still spawning
        }
        wave++
        gold += bonus
        if (bonus > 0) texts += FloatText("Early bonus +$bonus", map.cols / 2f, 1f, 1.6f, 0xFFFFD54FL)
        spawnQueue.addAll(Waves.build(map, wave))
        spawnTimer = 0f
        nextWaveTimer = -1f
        events += GameEvent.WAVE
        if (wave % 5 == 0) events += GameEvent.BOSS
        return bonus
    }

    fun canCallEarly() = !finished && wave in 1 until map.waves && spawnQueue.isEmpty()

    fun cycleSpeed() { speed = speed % SPEED_LEVELS + 1 }

    fun step(dtReal: Float) {
        if (paused || finished) { decayFx(dtReal); return }
        val total = min(dtReal, 0.05f) * speed
        val sub = max(1, (total / 0.017f).toInt() + 1)
        val dt = total / sub
        repeat(sub) { tick(dt) }
        decayFx(0f)
    }

    private fun tick(dt: Float) {
        time += dt
        // spawning
        if (spawnQueue.isNotEmpty()) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                val e = spawnQueue.removeFirst()
                spawn(e.kind, 0f)
                spawnTimer = e.delay
            }
        } else if (wave in 1 until map.waves && enemies.isEmpty() && nextWaveTimer < 0f) {
            // wave cleared: reward, then countdown
            val reward = 20 + wave * 3
            gold += reward
            texts += FloatText("Wave $wave clear  +$reward", map.cols / 2f, map.rows / 2f, 2f, 0xFF9CFF8AL)
            nextWaveTimer = WAVE_GAP
            events += GameEvent.COIN
        } else if (wave >= map.waves && enemies.isEmpty() && !won) {
            won = true; events += GameEvent.WIN
        }
        if (nextWaveTimer > 0f) {
            nextWaveTimer -= dt
            if (nextWaveTimer <= 0f) { nextWaveTimer = -1f; callWave() }
        }
        moveEnemies(dt)
        towersAct(dt)
        moveProjectiles(dt)
        fireZones(dt)
        enemies.removeAll { !it.alive }
        updateFx(dt)
    }

    fun spawn(kind: EnemyKind, d: Float): Enemy {
        val hp = kind.hp * Waves.hpMultiplier(map, max(1, wave))
        val e = Enemy(nextId++, kind, hp, d)
        e.pos = map.posAt(d)
        enemies += e
        return e
    }

    private fun moveEnemies(dt: Float) {
        for (e in enemies) {
            if (!e.alive) continue
            e.slow -= dt; e.frozen -= dt; e.stun -= dt; e.hit -= dt
            if (e.burn > 0) { e.burn -= dt; damage(e, e.burnDps * dt, null, quiet = true) }
            e.d += e.kind.speed * e.speedMul() * dt
            e.pos = map.posAt(e.d)
            e.abilityTimer -= dt
            if (e.abilityTimer <= 0f) ability(e)
            if (e.d >= map.length && e.alive) {
                e.alive = false; e.leaked = true
                if (!cheats.godMode) lives -= e.kind.leak
                shake = 0.35f
                events += GameEvent.LEAK
                texts += FloatText("-${e.kind.leak}", e.pos.x, e.pos.y, 1f, 0xFFFF5252L)
                if (lives <= 0) { lives = 0; lost = true; events += GameEvent.LOSE }
            }
        }
    }

    private fun ability(e: Enemy) {
        when (e.kind) {
            EnemyKind.HEALER -> {
                e.abilityTimer = 2.5f
                for (o in enemies) if (o.alive && o !== e && o.pos.dist(e.pos) < 1.6f) o.hp = min(o.maxHp, o.hp + o.maxHp * 0.12f)
                rings += Ring(e.pos.x, e.pos.y, 1.6f, 0.6f, 0.6f, 0xFF69F0AEL)
            }
            EnemyKind.BOSS -> {
                e.abilityTimer = 6f
                // stomp: disables nearby towers unless invincible
                rings += Ring(e.pos.x, e.pos.y, 2.2f, 0.7f, 0.7f, 0xFFFF6E40L)
                shake = 0.4f
                if (!cheats.invincibleTowers) for (t in towers) if (t.pos.dist(e.pos) < 2.2f) t.stunned = 2.5f
            }
            else -> e.abilityTimer = 99f
        }
    }

    fun damage(e: Enemy, raw: Float, t: Tower?, quiet: Boolean = false, pierce: Boolean = false) {
        if (!e.alive) return
        val amt = if (cheats.oneHitKill) e.hp + 1f else max(raw * 0.25f, raw - if (pierce) 0f else e.kind.armor)
        e.hp -= amt
        e.hit = 0.08f
        t?.let { it.damageDealt += amt }
        if (e.hp <= 0f) kill(e, t) else if (!quiet) events += GameEvent.HIT
    }

    private fun kill(e: Enemy, t: Tower?) {
        e.alive = false
        gold += e.kind.reward
        totalKills++
        t?.let { it.kills++ }
        texts += FloatText("+${e.kind.reward}", e.pos.x, e.pos.y - 0.2f, 0.9f, 0xFFFFD54FL)
        val col = enemyColor(e.kind)
        burst(e.pos.x, e.pos.y, if (e.kind == EnemyKind.BOSS) 70 else 14, col, if (e.kind == EnemyKind.BOSS) 5f else 2.4f)
        if (e.kind == EnemyKind.BOSS) { shake = 0.6f; rings += Ring(e.pos.x, e.pos.y, 3f, 0.8f, 0.8f, 0xFFFFD54FL) }
        if (e.kind == EnemyKind.SPLITTER) {
            repeat(3) { i ->
                val c = spawn(EnemyKind.SWARM, max(0f, e.d - 0.25f * i))
                c.hp = c.maxHp * 1.5f
            }
        }
        events += GameEvent.DIE
    }

    fun pickTarget(t: Tower, exclude: Collection<Int> = emptyList()): Enemy? {
        val r = t.range(); val p = t.pos
        var best: Enemy? = null; var score = 0f
        for (e in enemies) {
            if (!e.alive || e.id in exclude) continue
            if (e.kind.flying && !t.canHitAir()) continue
            val dist = e.pos.dist(p)
            if (dist > r + e.kind.radius) continue
            val s = when (t.targeting) {
                Targeting.FIRST -> e.d
                Targeting.LAST -> -e.d
                Targeting.STRONG -> e.hp
                Targeting.CLOSE -> -dist
            }
            if (best == null || s > score) { best = e; score = s }
        }
        return best
    }

    private fun towersAct(dt: Float) {
        for (t in towers) {
            t.recoil = max(0f, t.recoil - dt * 4f)
            if (t.stunned > 0) { t.stunned -= dt; t.beamTargets = emptyList(); continue }
            t.cooldown -= dt
            if (t.kind == TowerKind.LASER) { laser(t, dt); continue }
            val target = pickTarget(t) ?: continue
            val dx = target.pos.x - t.pos.x; val dy = target.pos.y - t.pos.y
            t.angle = atan2(dy, dx)
            if (t.cooldown > 0) continue
            t.cooldown = 1f / t.rate()
            t.recoil = 1f
            fire(t, target)
        }
    }

    private fun laser(t: Tower, dt: Float) {
        val n = if (t.branch == Branch.LASER_PRISM) 3 else 1
        val picked = ArrayList<Enemy>()
        for (i in 0 until n) pickTarget(t, picked.map { it.id })?.let { picked += it }
        if (picked.isEmpty()) { t.ramp = 0f; t.beamTargets = emptyList(); return }
        val newIds = picked.map { it.id }
        if (newIds.firstOrNull() != t.beamTargets.firstOrNull()) t.ramp = 0f
        t.beamTargets = newIds
        val first = picked[0]
        t.angle = atan2(first.pos.y - t.pos.y, first.pos.x - t.pos.x)
        val rampSpeed = if (t.branch == Branch.LASER_SOLAR) 1.5f else 0.5f
        t.ramp = min(3f, t.ramp + dt * rampSpeed)
        val per = t.damage() * (0.4f + t.ramp) * dt
        for (e in picked) {
            damage(e, per, t, quiet = true, pierce = true)
            if (rnd.nextFloat() < 0.3f) particles += Particle(e.pos.x, e.pos.y, rnd.nextFloat() * 2 - 1, rnd.nextFloat() * 2 - 1, 0.3f, 0.3f, 0xFFFF80ABL, 0.06f, true)
        }
    }

    private fun fire(t: Tower, target: Enemy) {
        when (t.kind) {
            TowerKind.ARROW -> {
                val targets = mutableListOf(target)
                if (t.branch == Branch.ARROW_RAPID) pickTarget(t, listOf(target.id))?.let { targets += it }
                for (tg in targets) {
                    var dmg = t.damage()
                    if (t.branch == Branch.ARROW_SNIPER && rnd.nextFloat() < 0.2f) dmg *= 3f
                    projectiles += Projectile(t.pos, tg.id, tg.pos, dmg, 11f, 0f, t, 0, false)
                }
                events += GameEvent.SHOOT
            }
            TowerKind.CANNON -> {
                projectiles += Projectile(t.pos, target.id, target.pos, t.damage(), 6.5f, t.splash(), t, 1, false)
                events += GameEvent.CANNON
            }
            TowerKind.MORTAR -> {
                projectiles += Projectile(t.pos, target.id, lead(target, 1.1f), t.damage(), 1f / 1.1f, t.splash(), t, 3, true)
                events += GameEvent.CANNON
            }
            TowerKind.FROST -> {
                rings += Ring(t.pos.x, t.pos.y, t.range(), 0.45f, 0.45f, 0xFF80D8FFL)
                for (e in enemies) if (e.alive && e.pos.dist(t.pos) <= t.range()) {
                    e.slow = 1.8f; e.slowAmt = min(0.7f, 0.35f + 0.05f * t.level)
                    if (t.branch == Branch.FROST_DEEP && rnd.nextFloat() < 0.18f && e.kind != EnemyKind.BOSS) e.frozen = 1.2f
                    damage(e, t.damage(), t, quiet = true)
                }
                events += GameEvent.FREEZE
            }
            TowerKind.TESLA -> {
                val chain = ArrayList<Enemy>(); chain += target
                var cur = target
                repeat(t.chains()) {
                    val nxt = enemies.filter { it.alive && it !in chain && it.pos.dist(cur.pos) < 1.8f }.minByOrNull { it.pos.dist(cur.pos) } ?: return@repeat
                    chain += nxt; cur = nxt
                }
                val pts = ArrayList<P>(); pts += t.pos
                var dmg = t.damage()
                for (e in chain) {
                    pts += e.pos
                    if (t.branch == Branch.TESLA_OVER) e.stun = 0.4f
                    damage(e, dmg, t, pierce = true); dmg *= 0.85f
                }
                bolts += Bolt(pts, 0.18f, 0xFFB388FFL)
                events += GameEvent.ZAP
            }
            TowerKind.LASER -> {}
        }
    }

    private fun lead(e: Enemy, secs: Float) = map.posAt(e.d + e.kind.speed * e.speedMul() * secs)

    private fun moveProjectiles(dt: Float) {
        for (p in projectiles) {
            if (!p.alive) continue
            val target = enemies.firstOrNull { it.id == p.targetId && it.alive }
            if (p.arc) {
                p.t += dt * p.speed
                p.pos = p.start + (p.targetPos - p.start) * min(1f, p.t)
                if (p.t >= 1f) impact(p, null)
                continue
            }
            if (target != null) p.targetPos = target.pos
            val dir = p.targetPos - p.pos; val len = dir.len()
            val stepLen = p.speed * dt
            if (len <= stepLen + 0.05f) impact(p, target) else p.pos = p.pos + dir * (stepLen / len)
            if (p.style == 1 && rnd.nextFloat() < 0.5f) particles += Particle(p.pos.x, p.pos.y, 0f, 0f, 0.3f, 0.3f, 0xFF9E9E9EL, 0.07f)
        }
        projectiles.removeAll { !it.alive }
    }

    private fun impact(p: Projectile, target: Enemy?) {
        p.alive = false
        if (p.splash > 0f) {
            val c = p.targetPos
            for (e in enemies) if (e.alive && !(e.kind.flying) && e.pos.dist(c) <= p.splash) {
                val fall = 1f - 0.5f * (e.pos.dist(c) / p.splash)
                damage(e, p.dmg * fall, p.tower, quiet = true)
            }
            burst(c.x, c.y, 22, 0xFFFF9100L, 3.2f, glow = true)
            rings += Ring(c.x, c.y, p.splash, 0.35f, 0.35f, 0xFFFFAB40L)
            shake = max(shake, if (p.style == 3) 0.25f else 0.12f)
            if (p.tower.branch == Branch.CANNON_CLUSTER || p.tower.branch == Branch.MORTAR_NAPALM)
                fires += FireZone(c.x, c.y, p.splash * 0.8f, 3f, p.dmg * 0.35f, p.tower)
            events += GameEvent.HIT
        } else if (target != null) {
            damage(target, p.dmg, p.tower)
            burst(target.pos.x, target.pos.y, 4, 0xFFFFF59DL, 1.4f)
        }
    }

    private fun fireZones(dt: Float) {
        for (f in fires) {
            f.life -= dt
            for (e in enemies) if (e.alive && !e.kind.flying && P(f.x, f.y).dist(e.pos) < f.r) damage(e, f.dps * dt, f.tower, quiet = true)
            if (rnd.nextFloat() < 0.6f) particles += Particle(f.x + (rnd.nextFloat() - .5f) * f.r * 1.6f, f.y + (rnd.nextFloat() - .5f) * f.r * 1.6f, 0f, -0.8f, 0.5f, 0.5f, 0xFFFF6D00L, 0.08f, true)
        }
        fires.removeAll { it.life <= 0 }
    }

    fun burst(x: Float, y: Float, n: Int, color: Long, spd: Float, glow: Boolean = false) {
        if (particles.size > 900) return
        repeat(n) {
            val a = rnd.nextFloat() * 6.2832f; val s = spd * (0.3f + rnd.nextFloat())
            val life = 0.3f + rnd.nextFloat() * 0.5f
            particles += Particle(x, y, kotlin.math.cos(a) * s, kotlin.math.sin(a) * s, life, life, color, 0.04f + rnd.nextFloat() * 0.06f, glow)
        }
    }

    private fun updateFx(dt: Float) {
        for (p in particles) { p.x += p.vx * dt; p.y += p.vy * dt; p.vx *= 0.94f; p.vy *= 0.94f; p.life -= dt }
        particles.removeAll { it.life <= 0 }
        for (t in texts) { t.y -= 0.6f * dt; t.life -= dt }
        texts.removeAll { it.life <= 0 }
        for (b in bolts) b.life -= dt
        bolts.removeAll { it.life <= 0 }
        for (r in rings) r.life -= dt
        rings.removeAll { it.life <= 0 }
        shake = max(0f, shake - dt)
    }

    private fun decayFx(dt: Float) { if (dt > 0) updateFx(dt * 0f) }

    // ---- cheats ----
    fun skipWave() {
        enemies.forEach { it.alive = false }; enemies.clear(); spawnQueue.clear(); projectiles.clear()
        nextWaveTimer = -1f
        if (wave >= map.waves) { won = true; events += GameEvent.WIN } else callWave()
    }
    fun jumpToWave(w: Int) {
        enemies.clear(); spawnQueue.clear(); projectiles.clear()
        wave = (w - 1).coerceIn(0, map.waves - 1)
        nextWaveTimer = -1f
        callWave()
    }
    fun maxAllTowers() { towers.forEach { t -> while (t.level < MAX_LEVEL) { if (t.needsBranch()) t.branch = Branch.of(t.kind).first(); t.level++ } } }

    // ---- snapshot (saved between waves) ----
    fun snapshot(): RunSnapshot = RunSnapshot(mapIndex, if (waveInProgress) max(0, wave - 1) else wave, gold, lives,
        towers.map { TowerSnap(it.kind, it.col, it.row, it.level, it.branch, it.targeting, it.invested) })

    fun restore(s: RunSnapshot) {
        gold = s.gold; lives = s.lives; wave = s.wave
        towers.clear()
        for (ts in s.towers) {
            val t = Tower(nextId++, ts.kind, ts.col, ts.row)
            t.level = ts.level; t.branch = ts.branch; t.targeting = ts.targeting; t.invested = ts.invested
            towers += t
        }
        nextWaveTimer = if (wave > 0) WAVE_GAP else -1f
    }
}

data class TowerSnap(val kind: TowerKind, val col: Int, val row: Int, val level: Int, val branch: Branch?, val targeting: Targeting, val invested: Int)
data class RunSnapshot(val mapIndex: Int, val wave: Int, val gold: Int, val lives: Int, val towers: List<TowerSnap>)

fun enemyColor(k: EnemyKind): Long = when (k) {
    EnemyKind.GRUNT -> 0xFFE57373L
    EnemyKind.RUNNER -> 0xFFFFB74DL
    EnemyKind.TANK -> 0xFF90A4AEL
    EnemyKind.SWARM -> 0xFFBA68C8L
    EnemyKind.HEALER -> 0xFF69F0AEL
    EnemyKind.FLYER -> 0xFFFFEE58L
    EnemyKind.SPLITTER -> 0xFF4DB6ACL
    EnemyKind.BOSS -> 0xFFD50000L
}
