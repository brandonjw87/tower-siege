package com.brandonwallace.towersiege.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brandonwallace.towersiege.Store
import com.brandonwallace.towersiege.game.*
import kotlin.math.sin

enum class Screen { MENU, MAPS, GAME }

private val Panel = Color(0xE01A1F2E)
private val Accent = Color(0xFFFFC94D)
private val Good = Color(0xFF5BE38C)

@Composable
fun TowerSiegeApp(store: Store, audio: Audio) {
    val ctx = LocalContext.current
    val profile = remember { store.load() }
    var rev by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf(Screen.MENU) }
    var engine by remember { mutableStateOf<Engine?>(null) }
    var cheatSession by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { audio.sfxOn = profile.sfxOn; audio.musicOn = profile.musicOn; audio.init(ctx) }
    fun save() { store.save(profile); rev++ }
    fun startMap(i: Int, resume: Boolean) {
        val e = Engine(Maps.all[i], i)
        if (resume) profile.run?.let { e.restore(it) } else { profile.run = e.snapshot(); save() }
        engine = e; screen = Screen.GAME
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondary = Good)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0E1220))) {
            when (screen) {
                Screen.MENU -> MainMenu(profile, rev,
                    onPlay = { screen = Screen.MAPS },
                    onContinue = { profile.run?.let { startMap(it.mapIndex, true) } },
                    onToggleMusic = { profile.musicOn = !profile.musicOn; audio.musicOn = profile.musicOn; save() },
                    onToggleSfx = { profile.sfxOn = !profile.sfxOn; audio.sfxOn = profile.sfxOn; save() })
                Screen.MAPS -> MapSelect(profile, rev, onBack = { screen = Screen.MENU }, onPick = { startMap(it, false) })
                Screen.GAME -> engine?.let { e ->
                    GameScreen(e, profile, audio, cheatSession,
                        onCheatUnlocked = { cheatSession = true; profile.cheatsUnlocked = true; save() },
                        onSave = { profile.run = if (e.finished) null else e.snapshot(); save() },
                        onProfileChange = { save() },
                        onExit = { if (!e.finished) profile.run = e.snapshot(); save(); engine = null; screen = Screen.MENU },
                        onRetry = { startMap(e.mapIndex, false) },
                        onNext = { startMap((e.mapIndex + 1).coerceAtMost(Maps.all.size - 1), false) })
                }
            }
        }
    }
}

@Composable
fun BigButton(text: String, color: Color = Accent, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier
        .clip(RoundedCornerShape(16.dp))
        .background(if (enabled) Brush.verticalGradient(listOf(color, color.copy(red = color.red * 0.75f, green = color.green * 0.75f, blue = color.blue * 0.75f))) else Brush.verticalGradient(listOf(Color(0xFF444A5A), Color(0xFF333845))))
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) Color(0xFF1A1300) else Color(0xFF9097A8), fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) Good.copy(alpha = 0.9f) else Color(0xFF3A4152)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun animTime(): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val s = withFrameNanos { it }; while (true) withFrameNanos { t = (it - s) / 1e9f } }
    return t
}

@Composable
fun MainMenu(p: Profile, rev: Int, onPlay: () -> Unit, onContinue: () -> Unit, onToggleMusic: () -> Unit, onToggleSfx: () -> Unit) {
    rev
    val t = animTime()
    val demo = remember { Maps.all[0] }
    val deco = remember { decoFor(demo) }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val b = boardFor(size.width, size.height, demo)
            drawMap(demo, b, deco, t)
            val ks = TowerKind.entries
            for (i in 0 until 6) drawTowerIcon(ks[i], Offset(b.x(2f + i * 2.3f), b.y(4.5f + sin(t + i) * 0.1f)), b.s, 1 + i % 5, t + i, 0f, null, t)
            drawRect(Color(0x99000000))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TOWER SIEGE", fontSize = (52 + 2 * sin(t * 2)).sp, fontWeight = FontWeight.Black, color = Accent)
            Text("Tap to build. Tap to upgrade. Hold the line.", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (p.run != null) BigButton("CONTINUE\n${Maps.all[p.run!!.mapIndex].name} · wave ${p.run!!.wave + 1}", Good, onClick = onContinue)
                BigButton("PLAY", onClick = onPlay, modifier = Modifier.widthIn(min = 160.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(if (p.musicOn) "♪ Music ON" else "♪ Music OFF", p.musicOn, onToggleMusic)
                Chip(if (p.sfxOn) "🔊 Sound ON" else "🔇 Sound OFF", p.sfxOn, onToggleSfx)
            }
            Spacer(Modifier.height(8.dp))
            Text("Stars ${p.stars.sum()}/${p.stars.size * 3}  ·  Enemies defeated ${p.totalKills}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
fun MapSelect(p: Profile, rev: Int, onBack: () -> Unit, onPick: (Int) -> Unit) {
    BackHandler(onBack = onBack)
    val t = animTime()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigButton("◀", Color(0xFF8FA3C7), onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Text("Choose a battlefield", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Maps.all.forEachIndexed { i, m ->
                val locked = i >= p.unlockedMaps
                val deco = remember(i) { decoFor(m) }
                Column(Modifier.width(260.dp).clip(RoundedCornerShape(18.dp)).background(Panel).clickable(enabled = !locked) { onPick(i) }.padding(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(146.dp).clip(RoundedCornerShape(12.dp))) {
                        Canvas(Modifier.fillMaxSize()) { drawMap(m, boardFor(size.width, size.height, m), deco, t); if (locked) drawRect(Color(0xAA000000)) }
                        if (locked) Text("🔒", fontSize = 40.sp, modifier = Modifier.align(Alignment.Center))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(m.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Text("${m.waves} waves · ${"★".repeat(p.stars[i])}${"☆".repeat(3 - p.stars[i])}", color = Accent)
                    val newTowers = TowerKind.entries.filter { it.unlockMap == i && i > 0 }
                    if (newTowers.isNotEmpty()) Text("Clear previous map to unlock ${newTowers.joinToString { it.label }}", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun GameScreen(
    g: Engine, profile: Profile, audio: Audio, cheatSession: Boolean,
    onCheatUnlocked: () -> Unit, onSave: () -> Unit, onProfileChange: () -> Unit,
    onExit: () -> Unit, onRetry: () -> Unit, onNext: () -> Unit,
) {
    var frame by remember { mutableIntStateOf(0) }
    var time by remember { mutableFloatStateOf(0f) }
    var selected by remember { mutableStateOf<Tower?>(null) }
    var buildAt by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var codeOpen by remember { mutableStateOf(false) }
    var cheatOpen by remember { mutableStateOf(false) }
    var endHandled by remember { mutableStateOf(false) }
    val deco = remember { decoFor(g.map) }
    var bgCache by remember { mutableStateOf<Pair<Pair<Int, Int>, androidx.compose.ui.graphics.ImageBitmap>?>(null) }
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER } }
    val dialogOpen = menuOpen || codeOpen || cheatOpen

    LaunchedEffect(g) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f); last = now
                time += dt
                g.step(dt)
                for (ev in g.events) {
                    audio.play(ev)
                    if (ev == GameEvent.COIN) onSave() // autosave after every cleared wave
                }
                g.events.clear()
                if (selected != null && selected !in g.towers) selected = null
                if (g.finished && !endHandled) {
                    endHandled = true
                    profile.totalKills += g.totalKills
                    if (g.won) {
                        val st = Profile.starsFor(g.lives, g.map.startLives)
                        profile.stars[g.mapIndex] = maxOf(profile.stars[g.mapIndex], st)
                        profile.unlockedMaps = maxOf(profile.unlockedMaps, (g.mapIndex + 2).coerceAtMost(Maps.all.size))
                    }
                    profile.run = null
                    onProfileChange()
                }
                frame++
            }
        }
    }
    // keep paused flag synced even when the loop reads stale lambda captures
    SideEffect { g.paused = dialogOpen }
    BackHandler {
        when {
            cheatOpen -> cheatOpen = false
            codeOpen -> codeOpen = false
            menuOpen -> menuOpen = false
            selected != null || buildAt != null -> { selected = null; buildAt = null }
            else -> menuOpen = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().pointerInput(g) {
            detectTapGestures { pos ->
                if (g.finished) return@detectTapGestures
                val b = boardFor(size.width.toFloat(), size.height.toFloat(), g.map)
                val c = ((pos.x - b.ox) / b.s).toInt().let { if (pos.x < b.ox) -1 else it }
                val r = ((pos.y - b.oy) / b.s).toInt().let { if (pos.y < b.oy) -1 else it }
                val t = g.towerAt(c, r)
                when {
                    t != null -> { selected = if (selected === t) null else t; buildAt = null }
                    g.canPlace(c, r) -> { buildAt = if (buildAt == (c to r)) null else c to r; selected = null }
                    else -> { selected = null; buildAt = null }
                }
            }
        }) {
            frame // read to invalidate each frame
            val b = boardFor(size.width, size.height, g.map)
            val key = size.width.toInt() to size.height.toInt()
            if (bgCache?.first != key) bgCache = key to renderMapBitmap(g.map, key.first, key.second, deco, this)
            val sh = if (g.shake > 0) g.shake * b.s * 0.25f else 0f
            translate(if (sh > 0) (Math.random().toFloat() - .5f) * sh else 0f, if (sh > 0) (Math.random().toFloat() - .5f) * sh else 0f) {
                bgCache?.second?.let { drawImage(it) }
                drawMapAnim(g.map, b, time)
                drawWorld(g, b, time, selected, buildAt, null)
                for (tx in g.texts) {
                    textPaint.textSize = b.s * 0.32f
                    textPaint.color = tx.color.toInt()
                    textPaint.alpha = (255 * tx.life.coerceIn(0f, 1f)).toInt()
                    textPaint.setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
                    drawContext.canvas.nativeCanvas.drawText(tx.text, b.x(tx.x), b.y(tx.y), textPaint)
                }
            }
        }

        Hud(g, frame, onMenu = { menuOpen = true }, onSpeed = { g.cycleSpeed() }, onWave = { g.callWave() })

        // Build picker
        AnimatedVisibility(buildAt != null && !g.finished, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            BuildBar(g, profile, frame) { k ->
                buildAt?.let { (c, r) -> g.place(k, c, r)?.let { buildAt = null } }
            }
        }
        // Tower panel
        AnimatedVisibility(selected != null && !g.finished, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            selected?.let { TowerPanel(g, it, frame, onClose = { selected = null }) }
        }

        if (menuOpen) PauseMenu(profile, audio,
            onResume = { menuOpen = false },
            onAdmin = { menuOpen = false; if (cheatSession) cheatOpen = true else codeOpen = true },
            onExit = { menuOpen = false; onExit() }, onProfileChange = onProfileChange)
        if (codeOpen) AdminCode(onCancel = { codeOpen = false }, onOk = { codeOpen = false; onCheatUnlocked(); cheatOpen = true })
        if (cheatOpen) CheatMenu(g, profile, onProfileChange, onClose = { cheatOpen = false })
        if (g.finished) EndDialog(g, onRetry, onNext, onExit)
    }
}

@Composable
fun Hud(g: Engine, frame: Int, onMenu: () -> Unit, onSpeed: () -> Unit, onWave: () -> Unit) {
    frame
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Panel).padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🪙 ${g.gold}", color = Accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text("❤ ${g.lives}", color = Color(0xFFFF6B81), fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text("Wave ${g.wave}/${g.map.waves}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (g.cheats.godMode || g.cheats.invincibleTowers || g.cheats.oneHitKill || g.cheats.freeBuild) Text("ADMIN", color = Color(0xFFFF5252), fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        val label = when {
            g.wave == 0 -> "▶ START"
            g.canCallEarly() && g.nextWaveTimer > 0 -> "⏩ NEXT ${g.nextWaveTimer.toInt()}s  +${(g.nextWaveTimer * 2).toInt() + 5}"
            g.canCallEarly() -> "⏩ CALL EARLY +${15 + g.wave}"
            else -> null
        }
        if (label != null && !g.finished) { BigButton(label, Good, onClick = onWave); Spacer(Modifier.width(8.dp)) }
        BigButton("${g.speed}x", Color(0xFF7FC8FF), onClick = onSpeed)
        Spacer(Modifier.width(8.dp))
        BigButton("II", Color(0xFFD0D6E2), onClick = onMenu)
    }
}

@Composable
fun BuildBar(g: Engine, p: Profile, frame: Int, onPick: (TowerKind) -> Unit) {
    frame
    Row(Modifier.padding(10.dp).clip(RoundedCornerShape(18.dp)).background(Panel).padding(8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (k in TowerKind.entries) {
            val unlocked = p.towerUnlocked(k)
            val afford = g.gold >= k.cost || g.cheats.freeBuild
            Column(Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).background(if (unlocked && afford) Color(0xFF2B3347) else Color(0xFF1E2230))
                .border(1.dp, if (unlocked && afford) towerColor(k) else Color.Transparent, RoundedCornerShape(12.dp))
                .clickable(enabled = unlocked && afford) { onPick(k) }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(48.dp)) { drawTowerIcon(k, center, size.minDimension) }
                Text(k.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(if (unlocked) "🪙 ${k.cost}" else "🔒 Map ${k.unlockMap + 1}", color = if (afford) Accent else Color(0xFFFF6B6B), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun TowerPanel(g: Engine, t: Tower, frame: Int, onClose: () -> Unit) {
    frame
    Column(Modifier.padding(10.dp).widthIn(max = 640.dp).clip(RoundedCornerShape(18.dp)).background(Panel).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(44.dp)) { drawTowerIcon(t.kind, center, size.minDimension, t.level, -1.57f, 0f, t.branch) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${t.branch?.label ?: t.kind.label}  ·  Lv ${t.level}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("DMG ${"%.0f".format(t.damage())}  RNG ${"%.1f".format(t.range())}  DPS ${"%.0f".format(t.dps())}  Kills ${t.kills}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Chip("🎯 ${t.targeting.label}", true) { t.targeting = t.targeting.next() }
            Spacer(Modifier.width(6.dp))
            Chip("✕", false, onClose)
        }
        Spacer(Modifier.height(8.dp))
        val cost = t.upgradeCost()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                cost == null -> Text("★ MAX LEVEL ★", color = Accent, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                t.needsBranch() -> for (br in Branch.of(t.kind)) BigButton("${br.label} 🪙$cost\n${br.desc}", Color(0xFFB39DFF), g.gold >= cost || g.cheats.freeBuild, Modifier.weight(1f)) { g.upgrade(t, br) }
                else -> BigButton("UPGRADE  🪙 $cost", Good, g.gold >= cost || g.cheats.freeBuild, Modifier.weight(1f)) { g.upgrade(t) }
            }
            BigButton("SELL +${t.sellValue()}", Color(0xFFFF8A80)) { g.sell(t) }
        }
    }
}

@Composable
fun Overlay(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(22.dp)).background(Panel).border(1.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(22.dp)).padding(20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun PauseMenu(p: Profile, audio: Audio, onResume: () -> Unit, onAdmin: () -> Unit, onExit: () -> Unit, onProfileChange: () -> Unit) {
    var music by remember { mutableStateOf(p.musicOn) }; var sfx by remember { mutableStateOf(p.sfxOn) }
    Overlay {
        Text("Paused", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Accent)
        BigButton("RESUME", Good, modifier = Modifier.fillMaxWidth(), onClick = onResume)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(if (music) "♪ Music ON" else "♪ Music OFF", music) { music = !music; p.musicOn = music; audio.musicOn = music; onProfileChange() }
            Chip(if (sfx) "🔊 Sound ON" else "🔇 Sound OFF", sfx) { sfx = !sfx; p.sfxOn = sfx; audio.sfxOn = sfx; onProfileChange() }
        }
        Text("Tip: tap an empty tile to build, tap a tower to upgrade. 🎯 changes who it shoots. ⏩ calls the next wave early for bonus gold.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center)
        BigButton("ADMIN", Color(0xFF9FA8DA), modifier = Modifier.fillMaxWidth(), onClick = onAdmin)
        BigButton("SAVE & QUIT", Color(0xFFFF8A80), modifier = Modifier.fillMaxWidth(), onClick = onExit)
    }
}

@Composable
fun AdminCode(onCancel: () -> Unit, onOk: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Overlay {
        Text("Admin code", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(code.map { '●' }.joinToString(" ").ifEmpty { "– – – – – –" }, fontSize = 28.sp, color = if (wrong) Color(0xFFFF5252) else Accent)
        for (row in listOf("123", "456", "789", "C0✓")) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (ch in row) BigButton(ch.toString(), if (ch == '✓') Good else Color(0xFFD0D6E2), modifier = Modifier.width(72.dp)) {
                wrong = false
                when (ch) {
                    'C' -> code = ""
                    '✓' -> if (code == ADMIN_CODE) onOk() else { wrong = true; code = "" }
                    else -> if (code.length < 6) code += ch
                }
            }
        }
        if (wrong) Text("Wrong code", color = Color(0xFFFF5252))
        BigButton("CANCEL", Color(0xFF8FA3C7), onClick = onCancel)
    }
}

@Composable
fun CheatMenu(g: Engine, p: Profile, onProfileChange: () -> Unit, onClose: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var waveSel by remember { mutableFloatStateOf(g.wave.coerceAtLeast(1).toFloat()) }
    tick
    Overlay {
        Text("Admin Menu", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF8A80))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("+1000 🪙", Accent) { g.gold += 1000; tick++ }
            BigButton("+10000 🪙", Accent) { g.gold += 10000; tick++ }
            BigButton("+50 ❤", Color(0xFFFF6B81)) { g.lives += 50; tick++ }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("God mode", g.cheats.godMode) { g.cheats.godMode = !g.cheats.godMode; tick++ }
            Chip("Invincible towers", g.cheats.invincibleTowers) { g.cheats.invincibleTowers = !g.cheats.invincibleTowers; tick++ }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("One-hit kill", g.cheats.oneHitKill) { g.cheats.oneHitKill = !g.cheats.oneHitKill; tick++ }
            Chip("Free build", g.cheats.freeBuild) { g.cheats.freeBuild = !g.cheats.freeBuild; tick++ }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Skip wave", Good) { g.skipWave(); tick++ }
            BigButton("Max all towers", Color(0xFFB39DFF)) { g.maxAllTowers(); tick++ }
        }
        Text("Wave select: ${waveSel.toInt()}", color = Color.White)
        Slider(waveSel, { waveSel = it }, valueRange = 1f..g.map.waves.toFloat(), steps = g.map.waves - 2)
        BigButton("Jump to wave ${waveSel.toInt()}", Color(0xFF7FC8FF)) { g.jumpToWave(waveSel.toInt()); tick++ }
        BigButton("Unlock all maps & towers", Accent) { p.unlockedMaps = Maps.all.size; onProfileChange(); tick++ }
        BigButton("CLOSE", Color(0xFFD0D6E2), modifier = Modifier.fillMaxWidth(), onClick = onClose)
    }
}

@Composable
fun EndDialog(g: Engine, onRetry: () -> Unit, onNext: () -> Unit, onExit: () -> Unit) {
    Overlay {
        if (g.won) {
            val st = Profile.starsFor(g.lives, g.map.startLives)
            Text("VICTORY!", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Accent)
            Text("★".repeat(st) + "☆".repeat(3 - st), fontSize = 40.sp, color = Accent)
            Text("${g.map.name} defended · ${g.totalKills} enemies defeated", color = Color.White)
            if (g.mapIndex < Maps.all.size - 1) BigButton("NEXT MAP ▶", Good, modifier = Modifier.fillMaxWidth(), onClick = onNext)
        } else {
            Text("DEFEAT", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF5252))
            Text("You reached wave ${g.wave}. Upgrade towers and try again!", color = Color.White)
        }
        BigButton("RETRY", Accent, modifier = Modifier.fillMaxWidth(), onClick = onRetry)
        BigButton("MENU", Color(0xFFD0D6E2), modifier = Modifier.fillMaxWidth(), onClick = onExit)
    }
}
