package com.brandonwallace.towersiege.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.brandonwallace.towersiege.game.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Layout of the board inside the canvas. */
data class Board(val ox: Float, val oy: Float, val s: Float) {
    fun x(t: Float) = ox + t * s
    fun y(t: Float) = oy + t * s
    fun o(p: P) = Offset(x(p.x), y(p.y))
}

fun boardFor(w: Float, h: Float, m: MapDef): Board {
    val s = minOf(w / m.cols, h / m.rows)
    return Board((w - s * m.cols) / 2f, (h - s * m.rows) / 2f, s)
}

private class Theme(val g1: Color, val g2: Color, val path: Color, val pathEdge: Color, val deco: List<Color>, val bg: Color)
private val themes = listOf(
    Theme(Color(0xFF4E9A3C), Color(0xFF5CAB47), Color(0xFFD9B77E), Color(0xFF9C7B4A), listOf(Color(0xFF2E7D32), Color(0xFF388E3C), Color(0xFF8D6E63)), Color(0xFF1B3A12)),
    Theme(Color(0xFFE0B865), Color(0xFFE8C474), Color(0xFFB98A4C), Color(0xFF8A5F2E), listOf(Color(0xFF7CB342), Color(0xFFA1887F), Color(0xFFBF8F4A)), Color(0xFF4A3412)),
    Theme(Color(0xFFDDE8F0), Color(0xFFE9F1F7), Color(0xFF9FB4C7), Color(0xFF6B8199), listOf(Color(0xFF2E5E4E), Color(0xFF90A4AE), Color(0xFFB0BEC5)), Color(0xFF20303D)),
    Theme(Color(0xFF3A2A2A), Color(0xFF453232), Color(0xFF7A5040), Color(0xFFFF6D00), listOf(Color(0xFF5D4037), Color(0xFF212121), Color(0xFFBF360C)), Color(0xFF150A0A)),
)

class Deco(val x: Float, val y: Float, val kind: Int, val size: Float, val c: Int)

fun decoFor(m: MapDef): List<Deco> {
    val r = Random(m.name.hashCode())
    val out = ArrayList<Deco>()
    repeat(m.cols * m.rows / 3) {
        val x = r.nextFloat() * m.cols; val y = r.nextFloat() * m.rows
        val near = m.pathTiles.any { (c, rr) -> kotlin.math.abs(c + 0.5f - x) < 0.8f && kotlin.math.abs(rr + 0.5f - y) < 0.8f }
        if (!near) out += Deco(x, y, r.nextInt(3), 0.12f + r.nextFloat() * 0.16f, r.nextInt(3))
    }
    return out.sortedBy { it.y }
}

fun Long.c() = Color(this.toInt())

fun DrawScope.drawMap(m: MapDef, b: Board, deco: List<Deco>, time: Float) {
    drawMapStatic(m, b, deco); drawMapAnim(m, b, time)
}

/** Renders the static layer once into a bitmap so each frame is a single blit. */
fun renderMapBitmap(m: MapDef, w: Int, h: Int, deco: List<Deco>, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.ImageBitmap {
    val img = androidx.compose.ui.graphics.ImageBitmap(w, h)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(density, androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(w.toFloat(), h.toFloat())) {
        drawMapStatic(m, boardFor(w.toFloat(), h.toFloat(), m), deco)
    }
    return img
}

fun DrawScope.drawMapStatic(m: MapDef, b: Board, deco: List<Deco>) {
    val th = themes[m.theme]
    drawRect(th.bg)
    for (c in 0 until m.cols) for (r in 0 until m.rows) {
        drawRect(if ((c + r) % 2 == 0) th.g1 else th.g2, Offset(b.x(c.toFloat()), b.y(r.toFloat())), Size(b.s + 1, b.s + 1))
    }
    // soft vignette
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x55000000)), center = Offset(size.width / 2, size.height / 2), radius = size.maxDimension * 0.7f))
    // path
    val path = Path()
    m.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(b.x(p.x), b.y(p.y)) else path.lineTo(b.x(p.x), b.y(p.y)) }
    drawPath(path, Color(0x40000000), style = Stroke(b.s * 0.98f, cap = StrokeCap.Square, join = StrokeJoin.Round), )
    drawPath(path, th.pathEdge, style = Stroke(b.s * 0.9f, cap = StrokeCap.Square, join = StrokeJoin.Round))
    drawPath(path, th.path, style = Stroke(b.s * 0.76f, cap = StrokeCap.Square, join = StrokeJoin.Round))
    for (dd in deco) drawDeco(dd, b, th)
}

private fun DrawScope.drawDeco(dd: Deco, b: Board, th: Theme) {
        val o = Offset(b.x(dd.x), b.y(dd.y)); val rr = dd.size * b.s
        val col = th.deco[dd.c]
        when (dd.kind) {
            0 -> {
                drawCircle(Color(0x44000000), rr * 1.1f, o + Offset(rr * 0.3f, rr * 0.4f))
                drawCircle(col, rr, o)
                drawCircle(Color.White.copy(alpha = 0.15f), rr * 0.5f, o - Offset(rr * 0.3f, rr * 0.3f))
            }
            1 -> {
                drawOval(Color(0x44000000), o + Offset(-rr * 0.6f, -rr * 0.1f), Size(rr * 1.6f, rr))
                drawOval(th.deco[1], o - Offset(rr * 0.8f, rr * 0.6f), Size(rr * 1.6f, rr * 1.1f))
            }
            else -> drawCircle(col.copy(alpha = 0.5f), rr * 0.35f, o)
        }
}

fun DrawScope.drawMapAnim(m: MapDef, b: Board, time: Float) {
    val th = themes[m.theme]
    // animated flow dots along the path
    val step = 0.9f
    var d = (time * 0.6f) % step
    while (d < m.length) {
        val p = m.posAt(d)
        drawCircle(th.pathEdge.copy(alpha = 0.35f), b.s * 0.05f, b.o(p))
        d += step
    }
    if (m.theme == 3) { // lava glow on path edge
        val path = Path()
        m.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(b.x(p.x), b.y(p.y)) else path.lineTo(b.x(p.x), b.y(p.y)) }
        val pulse = 0.25f + 0.15f * sin(time * 2f)
        drawPath(path, Color(0xFFFF6D00).copy(alpha = pulse), style = Stroke(b.s * 0.95f, cap = StrokeCap.Square, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
    }
    // entry / exit markers
    val start = m.posAt(1f); val end = m.posAt(m.length - 1f)
    drawCircle(Color(0x66FF5252), b.s * (0.45f + 0.05f * sin(time * 4)), b.o(end), style = Stroke(b.s * 0.08f))
    drawCircle(Color(0x6669F0AE), b.s * (0.45f + 0.05f * sin(time * 4)), b.o(start), style = Stroke(b.s * 0.08f))
}

fun towerColor(k: TowerKind): Color = when (k) {
    TowerKind.ARROW -> Color(0xFF66BB6A)
    TowerKind.CANNON -> Color(0xFF8D6E63)
    TowerKind.FROST -> Color(0xFF4FC3F7)
    TowerKind.TESLA -> Color(0xFF9575CD)
    TowerKind.LASER -> Color(0xFFF06292)
    TowerKind.MORTAR -> Color(0xFFFFA726)
}

fun DrawScope.drawTowerIcon(k: TowerKind, center: Offset, s: Float, level: Int = 1, angle: Float = -PI.toFloat() / 2, recoil: Float = 0f, branch: Branch? = null, time: Float = 0f, stunned: Boolean = false) {
    val col = towerColor(k)
    // base
    drawRoundRect(Color(0x55000000), center - Offset(s * 0.40f, s * 0.34f), Size(s * 0.86f, s * 0.86f), CornerRadius(s * 0.2f))
    drawRoundRect(Color(0xFF5D5D6A), center - Offset(s * 0.42f, s * 0.42f), Size(s * 0.84f, s * 0.84f), CornerRadius(s * 0.2f))
    drawRoundRect(Color(0xFF7E7E8C), center - Offset(s * 0.36f, s * 0.38f), Size(s * 0.72f, s * 0.7f), CornerRadius(s * 0.16f))
    if (branch != null) drawCircle(col.copy(alpha = 0.35f + 0.15f * sin(time * 3f)), s * 0.5f, center, style = Stroke(s * 0.06f), blendMode = BlendMode.Plus)
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 1f), col.copy(red = col.red * 0.6f, green = col.green * 0.6f, blue = col.blue * 0.6f)), center = center - Offset(s * 0.08f, s * 0.08f), radius = s * 0.35f), s * 0.28f, center)
    val deg = angle * 180f / PI.toFloat() + 90f
    rotate(deg, center) {
        val back = recoil * s * 0.08f
        when (k) {
            TowerKind.ARROW -> {
                drawLine(Color(0xFF5D4037), center + Offset(-s * 0.22f, back), center + Offset(s * 0.22f, back), s * 0.06f, StrokeCap.Round)
                drawLine(Color(0xFFEEEEEE), center + Offset(0f, back), center + Offset(0f, -s * 0.38f + back), s * 0.05f, StrokeCap.Round)
                if (branch == Branch.ARROW_RAPID) drawLine(Color(0xFFEEEEEE), center + Offset(s * 0.1f, back), center + Offset(s * 0.1f, -s * 0.3f + back), s * 0.04f, StrokeCap.Round)
            }
            TowerKind.CANNON, TowerKind.MORTAR -> {
                val w = if (k == TowerKind.MORTAR) 0.24f else 0.16f
                val len = if (k == TowerKind.MORTAR) 0.26f else 0.42f
                drawRoundRect(Color(0xFF37474F), center + Offset(-s * w / 2, -s * len + back), Size(s * w, s * len), CornerRadius(s * 0.04f))
                drawCircle(Color(0xFF212121), s * w * 0.35f, center + Offset(0f, -s * len + back))
            }
            TowerKind.FROST -> {
                for (i in 0 until 6) rotate(i * 60f + time * 30f, center) {
                    drawLine(Color(0xFFE1F5FE), center, center + Offset(0f, -s * 0.24f), s * 0.04f, StrokeCap.Round)
                }
            }
            TowerKind.TESLA -> {
                drawCircle(Color(0xFFD1C4E9), s * 0.14f, center)
                drawCircle(Color(0xFFB388FF).copy(alpha = 0.5f + 0.5f * sin(time * 10)), s * 0.2f, center, style = Stroke(s * 0.03f), blendMode = BlendMode.Plus)
            }
            TowerKind.LASER -> {
                drawRoundRect(Color(0xFF263238), center + Offset(-s * 0.07f, -s * 0.4f), Size(s * 0.14f, s * 0.4f), CornerRadius(s * 0.04f))
                drawCircle(Color(0xFFFF80AB), s * 0.06f, center + Offset(0f, -s * 0.4f), blendMode = BlendMode.Plus)
            }
        }
    }
    // level pips
    for (i in 0 until level) {
        val px = center.x - s * 0.32f + i * s * 0.16f
        drawCircle(if (i >= 3) Color(0xFFFFD54F) else Color.White, s * 0.05f, Offset(px, center.y + s * 0.36f))
    }
    if (stunned) drawCircle(Color(0x99FFEB3B), s * 0.45f, center, style = Stroke(s * 0.05f))
}

fun DrawScope.drawEnemy(e: Enemy, b: Board, time: Float) {
    val o = b.o(e.pos); val r = e.kind.radius * b.s
    val col = enemyColor(e.kind).c()
    val bob = sin(time * 10f + e.wobble) * r * 0.08f
    if (e.kind.flying) {
        drawOval(Color(0x33000000), o + Offset(-r * 0.8f, r * 0.9f), Size(r * 1.6f, r * 0.6f))
        val flap = sin(time * 30f + e.wobble) * 0.5f + 0.5f
        drawOval(Color(0xAAFFFFFF), o + Offset(-r * 1.6f, -r * 0.5f - flap * r * 0.3f), Size(r * 1.2f, r * 0.7f))
        drawOval(Color(0xAAFFFFFF), o + Offset(r * 0.4f, -r * 0.5f - flap * r * 0.3f), Size(r * 1.2f, r * 0.7f))
    } else drawOval(Color(0x44000000), o + Offset(-r, r * 0.5f), Size(r * 2f, r * 0.8f))
    val c = o + Offset(0f, bob - if (e.kind.flying) r * 0.4f else 0f)
    val body = if (e.hit > 0) Color.White else if (e.frozen > 0) Color(0xFFB3E5FC) else col
    drawCircle(Brush.radialGradient(listOf(body, body.copy(red = body.red * 0.55f, green = body.green * 0.55f, blue = body.blue * 0.55f)), center = c - Offset(r * 0.3f, r * 0.3f), radius = r * 1.3f), r, c)
    if (e.kind.armor > 0) drawCircle(Color(0xFFCFD8DC), r, c, style = Stroke(r * 0.18f))
    // eyes looking along movement
    drawCircle(Color.White, r * 0.22f, c + Offset(-r * 0.3f, -r * 0.15f)); drawCircle(Color.White, r * 0.22f, c + Offset(r * 0.3f, -r * 0.15f))
    drawCircle(Color.Black, r * 0.1f, c + Offset(-r * 0.27f, -r * 0.12f)); drawCircle(Color.Black, r * 0.1f, c + Offset(r * 0.33f, -r * 0.12f))
    if (e.kind == EnemyKind.BOSS) {
        val crown = Path().apply {
            moveTo(c.x - r * 0.5f, c.y - r * 0.8f); lineTo(c.x - r * 0.5f, c.y - r * 1.3f); lineTo(c.x - r * 0.25f, c.y - r * 1.0f)
            lineTo(c.x, c.y - r * 1.4f); lineTo(c.x + r * 0.25f, c.y - r * 1.0f); lineTo(c.x + r * 0.5f, c.y - r * 1.3f); lineTo(c.x + r * 0.5f, c.y - r * 0.8f); close()
        }
        drawPath(crown, Color(0xFFFFD54F))
    }
    if (e.kind == EnemyKind.HEALER) { drawRect(Color.White, c + Offset(-r * 0.08f, r * 0.15f), Size(r * 0.16f, r * 0.5f)); drawRect(Color.White, c + Offset(-r * 0.25f, r * 0.32f), Size(r * 0.5f, r * 0.16f)) }
    if (e.slow > 0) drawCircle(Color(0x6680D8FF), r * 1.2f, c, style = Stroke(r * 0.12f))
    if (e.burn > 0) drawCircle(Color(0x66FF6D00), r * 1.1f, c, blendMode = BlendMode.Plus)
    // hp bar
    if (e.hp < e.maxHp) {
        val w = maxOf(r * 2f, b.s * 0.4f); val top = c.y - r - b.s * 0.14f
        drawRect(Color(0xAA000000), Offset(c.x - w / 2, top), Size(w, b.s * 0.07f))
        val f = (e.hp / e.maxHp).coerceIn(0f, 1f)
        drawRect(if (f > 0.5f) Color(0xFF76FF03) else if (f > 0.25f) Color(0xFFFFEA00) else Color(0xFFFF1744), Offset(c.x - w / 2, top), Size(w * f, b.s * 0.07f))
    }
}

fun DrawScope.drawWorld(g: Engine, b: Board, time: Float, selected: Tower?, ghost: Pair<Int, Int>?, ghostKind: TowerKind?) {
    for (f in g.fires) drawCircle(Brush.radialGradient(listOf(Color(0x88FF6D00), Color.Transparent), center = Offset(b.x(f.x), b.y(f.y)), radius = f.r * b.s), f.r * b.s, Offset(b.x(f.x), b.y(f.y)), blendMode = BlendMode.Plus)
    // range of selected / ghost
    selected?.let {
        drawCircle(Color(0x22FFFFFF), it.range() * b.s, b.o(it.pos)); drawCircle(Color(0x88FFFFFF), it.range() * b.s, b.o(it.pos), style = Stroke(b.s * 0.03f))
    }
    ghost?.let { (c, r) ->
        val o = Offset(b.x(c + 0.5f), b.y(r + 0.5f))
        drawRoundRect(Color(0x55FFFFFF), Offset(b.x(c.toFloat()), b.y(r.toFloat())), Size(b.s, b.s), CornerRadius(b.s * 0.15f), style = Stroke(b.s * 0.05f))
        ghostKind?.let { k -> drawCircle(Color(0x22FFFFFF), k.baseRange * b.s, o); drawCircle(Color(0x66FFFFFF), k.baseRange * b.s, o, style = Stroke(b.s * 0.03f)) }
    }
    for (t in g.towers) {
        drawTowerIcon(t.kind, b.o(t.pos), b.s, t.level, t.angle, t.recoil, t.branch, time, t.stunned > 0)
        if (t.kind == TowerKind.LASER) for (id in t.beamTargets) {
            val e = g.enemies.firstOrNull { it.id == id } ?: continue
            val start = b.o(t.pos) + Offset(cos(t.angle), sin(t.angle)) * (b.s * 0.38f)
            val w = b.s * (0.05f + 0.04f * t.ramp)
            drawLine(Color(0x66FF4081), start, b.o(e.pos), w * 2.2f, StrokeCap.Round, blendMode = BlendMode.Plus)
            drawLine(Color(0xFFFFE0F0), start, b.o(e.pos), w * 0.6f, StrokeCap.Round)
        }
    }
    for (e in g.enemies.sortedBy { if (it.kind.flying) 1 else 0 }) drawEnemy(e, b, time)
    for (p in g.projectiles) {
        val o = b.o(p.pos)
        when (p.style) {
            0 -> { val dir = (p.targetPos - p.pos); val l = dir.len().coerceAtLeast(0.001f)
                drawLine(Color(0xFFFFF8E1), o, o - Offset(dir.x / l, dir.y / l) * (b.s * 0.25f), b.s * 0.04f, StrokeCap.Round) }
            1 -> { drawCircle(Color(0xFF212121), b.s * 0.09f, o); drawCircle(Color(0x88FFAB40), b.s * 0.12f, o, blendMode = BlendMode.Plus) }
            3 -> { val h = sin(p.t.coerceIn(0f, 1f) * PI.toFloat()) * b.s * 1.2f
                drawCircle(Color(0x44000000), b.s * 0.1f, o)
                drawCircle(Color(0xFF3E2723), b.s * (0.12f + h / b.s * 0.06f), o - Offset(0f, h)) }
        }
    }
    for (bo in g.bolts) {
        val a = bo.life / 0.18f
        for (i in 0 until bo.pts.size - 1) {
            val p0 = b.o(bo.pts[i]); val p1 = b.o(bo.pts[i + 1])
            val mid = (p0 + p1) / 2f + Offset((Random.nextFloat() - .5f) * b.s * 0.4f, (Random.nextFloat() - .5f) * b.s * 0.4f)
            drawLine(bo.color.c().copy(alpha = a), p0, mid, b.s * 0.09f, StrokeCap.Round, blendMode = BlendMode.Plus)
            drawLine(bo.color.c().copy(alpha = a), mid, p1, b.s * 0.09f, StrokeCap.Round, blendMode = BlendMode.Plus)
            drawLine(Color.White.copy(alpha = a), p0, mid, b.s * 0.03f); drawLine(Color.White.copy(alpha = a), mid, p1, b.s * 0.03f)
        }
    }
    for (r in g.rings) {
        val f = 1f - r.life / r.maxLife
        drawCircle(r.color.c().copy(alpha = (1f - f) * 0.8f), r.maxR * b.s * (0.3f + 0.7f * f), Offset(b.x(r.x), b.y(r.y)), style = Stroke(b.s * 0.08f * (1f - f) + 1f))
    }
    for (p in g.particles) {
        val a = (p.life / p.maxLife).coerceIn(0f, 1f)
        drawCircle(p.color.c().copy(alpha = a), p.size * b.s * (0.5f + a * 0.5f), Offset(b.x(p.x), b.y(p.y)), blendMode = if (p.glow) BlendMode.Plus else BlendMode.SrcOver)
    }
}
