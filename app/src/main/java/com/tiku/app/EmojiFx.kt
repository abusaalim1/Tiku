package com.tiku.app

// ================= ROUND 25 — the owner's own emoji-effect design,
// integrated =================
// Source of truth: the design file the owner supplied (EmojiFx demo
// screen: particle engine [Fx], 6 centre-stage kinds for ❤️ 😘 🫣 👀
// 😚 🤭, PNG sprites in res/drawable/ios_*). The ENGINE and the ART
// below are that file, as verbatim as possible — do not "improve"
// them. The integration changes ONLY the shell:
//   * EmojiFxScreen (black background + bottom emoji button row) is
//     replaced by [EmojiFxOverlay]: the same Canvas show, played
//     TRANSPARENTLY over the chat, with no pointer input.
//   * Fx.emit()'s float-up origin moves from the demo's 6-slot row
//     formula to bottom-centre (chat has no slots).
//   * The sprite bitmaps are decoded once per overlay instead of on
//     every recomposition.

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.*
import kotlin.random.Random

// res/drawable/ me ios_*.png (zip se aaye hain)
private val KEYS = listOf("heart", "kiss", "peek", "eyes", "kissing", "giggle")
private val DRAW = mapOf(
    "heart" to R.drawable.ios_heart, "kiss" to R.drawable.ios_kiss, "peek" to R.drawable.ios_peek,
    "eyes" to R.drawable.ios_eyes, "kissing" to R.drawable.ios_kissing, "lips" to R.drawable.ios_lips,
    "giggle" to R.drawable.ios_giggle, "h1" to R.drawable.ios_h1, "h2" to R.drawable.ios_h2, "h3" to R.drawable.ios_h3, "spark" to R.drawable.ios_spark
)
private val SET = listOf(
    listOf("h1", "h2", "h3", "heart"), listOf("h1", "h3"), listOf("h2", "spark"),
    listOf("spark", "h2"), listOf("h1", "h2"), listOf("h1", "h2")
)
private val RATE = floatArrayOf(14f, 3f, 6f, 5f, 7f, 3f)
private const val HERO = 3.2f
private const val TAU = 6.2831855f
private val PINK = Color(0xFFFF5A9A)
private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
private fun ease(x: Float) = 1f - (1f - x).pow(3)
private fun back(x: Float): Float { val c = 1.70158f; return 1 + (c + 1) * (x - 1).pow(3) + c * (x - 1).pow(2) }

private val HP: List<Pair<Float, Float>> = List(121) { i ->
    val a = i / 120f * TAU; val s = sin(a)
    16f * s * s * s / 17f to -(13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)) / 17f
}

private enum class K { IMG, VHEART, STAR, RING, TRAIL, STAMP, AR, WV, TK }

private class P(
    val k: K, var x: Float = 0f, var y: Float = 0f, var vx: Float = 0f, var vy: Float = 0f,
    val s: Float = 0f, val max: Float = 1f, var r: Float = 0f, val vr: Float = 0f,
    val sw: Float = 0f, val ph: Float = 0f, val im: String = "", val b: Boolean = false,
    val r0: Float = 0f, val r1: Float = 0f, val col: Color = PINK, val side: Float = 0f, var age: Float = 0f
)

private class Fx {
    var w = 1f; var h = 1f; var d = 1f
    var kind = -1; var t = 99f; var acc = 0f; var n1 = 0; var burst = false; var st2 = 0f; var lk = 0f
    val parts = ArrayList<P>()
    val cx get() = w / 2; val cy get() = h * .3f; val S get() = w * .42f

    fun trigger(i: Int) { kind = i; t = 0f; acc = 0f; n1 = 0; burst = false; st2 = 0f; lk = 0f }

    private fun emit() {
        val v = (kind == 3 && t > 1.4f) || (kind == 4 && Random.nextBoolean())
        // ROUND 25 adaptation (the ONLY engine change): the demo fed
        // its float-up stream from the tapped slot of its 6-button
        // row — w * (kind + .5f) / 6f. Chat has no slots, so the
        // stream rises from bottom-centre with the same ±.1w jitter.
        parts += P(if (v) K.VHEART else K.IMG, w / 2f + rnd(-.1f, .1f) * w, h - 70f * d,
            rnd(-25f, 25f) * d, -rnd(300f, 560f) * d, rnd(18f, 42f) * d, rnd(1.8f, 3f),
            rnd(-20f, 20f), rnd(-30f, 30f), rnd(10f, 30f) * d, rnd(0f, 6.28f), SET[kind].random())
    }

    private fun boom(x: Float, y: Float) = repeat(16) {
        val a = rnd(0f, TAU); val s = rnd(350f, 800f) * d
        parts += P(K.IMG, x, y, cos(a) * s, sin(a) * s, rnd(26f, 50f) * d, rnd(1.2f, 2f), 0f,
            rnd(-90f, 90f), im = listOf("h1", "h3", "lips", "h2").random(), b = true)
    }

    private fun ring(x: Float, y: Float, r0: Float, r1: Float, max: Float, c: Color) =
        parts.add(P(K.RING, x, y, max = max, r0 = r0, r1 = r1, col = c))

    fun step(dt: Float) {
        if (kind >= 0 && t < HERO) {
            t += dt; acc += dt
            while (acc > 1f / RATE[kind]) { acc -= 1f / RATE[kind]; emit() }
            if (kind == 0 && n1 < 5 && t >= .157f + n1 * .63f) { n1++; ring(cx, cy, S * .4f, S * 1.25f, 1.1f, PINK) }
            if (kind == 4 && n1 < 3 && t >= .3f + n1 * 1f) {
                val lx = cx + S * .02f; val ly = cy + S * .22f; n1++
                parts += P(K.AR, lx, ly, max = .9f)
                parts += P(K.WV, lx, ly, max = 1.8f, side = 1f); parts += P(K.WV, lx, ly, max = 1.8f, side = -1f)
                ring(cx, cy, S * .35f, S * 1.1f, 1.2f, Color(0xFFFFC2DC))
            }
            if (kind == 5 && n1 < 5 && t >= .4f + n1 * .55f) {
                n1++; parts += P(K.TK, max = .55f)
                repeat(3) {
                    parts += P(K.IMG, cx + rnd(-.35f, .35f) * S, cy - S * .45f, rnd(-60f, 60f) * d, -rnd(260f, 460f) * d, rnd(26f, 40f) * d,
                        1.3f, rnd(-20f, 20f), rnd(-60f, 60f), rnd(6f, 14f) * d, rnd(0f, 6.28f), listOf("h1", "h2", "spark").random())
                }
            }
            if (kind == 2 && t > .6f) {
                lk += dt
                if (lk > .3f) {
                    lk = 0f
                    parts += P(K.IMG, cx + rnd(-.25f, .25f) * S, cy - S * .45f, rnd(-30f, 30f) * d, -rnd(120f, 220f) * d, rnd(22f, 34f) * d,
                        1.7f, rnd(-15f, 15f), rnd(-30f, 30f), rnd(8f, 16f) * d, rnd(0f, 6.28f), "h2")
                }
            }
            if (kind == 1) {
                while (n1 < 7 && t >= .35f + n1 * .12f) { n1++; parts += P(K.TRAIL, max = 1f) }
                if (!burst && t >= 1.4f) {
                    burst = true; val x = w / 2; val y = h * .58f
                    parts += P(K.STAMP, x, y, s = w * .3f, max = 1.5f)
                    ring(x, y, w * .1f, w * .4f, 1f, PINK); boom(x, y)
                }
            }
            if (kind == 2 || kind == 3) {
                st2 += dt
                if (st2 > .1f) { st2 = 0f; parts += P(K.STAR, cx + rnd(-.6f, .6f) * S, cy + rnd(-.6f, .5f) * S, s = rnd(10f, 24f) * d, max = .8f) }
            }
        }
        for (p in parts) {
            p.age += dt; p.x += p.vx * dt; p.y += p.vy * dt; p.r += p.vr * dt
            if (p.b) { val k = 1f - 1.6f * dt; p.vx *= k; p.vy *= k; p.vy -= 60f * d * dt }
        }
        parts.removeAll { it.age > it.max }
    }
}

/** dx, dy, scale, rot, alpha */
private fun hero(k: Int, t: Float): FloatArray {
    val pop = back(min(t / .45f, 1f)); var dx = 0f; var dy = 0f; var s = pop; var r = 0f
    when (k) {
        0 -> s = pop * (1f + .18f * max(0f, sin(t * 10f)).pow(2))
        1 -> { r = -10f * sin(min(t, 1f) * 3f); s = pop * (1f + .22f * exp(-abs(t - .35f) * 10f)) }
        2 -> { dy = (1f - min(t / .5f, 1f)).pow(3) * 140f; if (t > 1.4f && t < 2.3f) dy += sin((t - 1.4f) / .9f * PI.toFloat()) * 110f; r = 6f * sin(t * 9f); if (t > .6f) dx = 8f * sin(t * 14f) }
        3 -> dx = 55f * sin(t * 2.4f)
        4 -> { val p = if (t > .3f) (t - .3f) % 1f else 9f; s = pop * (1f + .16f * exp(-p * 6f)); dy = -8f * sin(t * 3f); r = -6f * sin(t * 3f) }
        5 -> { val pp = (t - .4f) / .55f; if (pp >= 0f && pp < 5f) { val ph = pp % 1f; dy = -30f * sin(ph * PI.toFloat()) * exp(-ph * .6f); r = 9f * sin(ph * TAU * 2f) * (1f - ph) } }
    }
    return floatArrayOf(dx, dy, s, r, if (t > HERO - .5f) max(0f, (HERO - t) / .5f) else 1f)
}

// ---------- drawing helpers ----------
private fun DrawScope.sprite(im: ImageBitmap, x: Float, y: Float, s: Float, rot: Float, a: Float) {
    if (s <= 1f) return
    rotate(rot, Offset(x, y)) {
        drawImage(im, dstOffset = IntOffset((x - s / 2).roundToInt(), (y - s / 2).roundToInt()),
            dstSize = IntSize(s.roundToInt(), s.roundToInt()), alpha = a.coerceIn(0f, 1f), filterQuality = FilterQuality.High)
    }
}

private fun hpath(cx: Float, cy: Float, r: Float, n: Int) = Path().apply {
    for (i in 0..n) { val (x, y) = HP[i]; if (i == 0) moveTo(cx + x * r, cy + y * r) else lineTo(cx + x * r, cy + y * r) }
}

private fun DrawScope.neon(cx: Float, cy: Float, r: Float, pr: Float, a: Float, d: Float) {
    val path = hpath(cx, cy, r, (pr * 120).toInt())
    for ((w, al) in listOf(16f to .1f, 9f to .22f, 3.5f to 1f))
        drawPath(path, PINK, alpha = (a * al).coerceIn(0f, 1f), style = Stroke(w * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.vheart(cx: Float, cy: Float, r: Float, a: Float, fill: Boolean, d: Float, col: Color = Color(0xFFFF4D8D)) {
    val al = a.coerceIn(0f, 1f)
    if (fill) {
        drawPath(hpath(cx, cy, r, 120), col, alpha = al)
        drawPath(hpath(cx - r * .35f, cy - r * .35f, r * .12f, 120), Color(0xFFFFD0E4), alpha = al * .8f)
    } else drawPath(hpath(cx, cy, r, 120), Color(0xFFFF9EC4), alpha = al, style = Stroke(2.5f * d))
}

private fun DrawScope.star(cx: Float, cy: Float, r: Float, a: Float) {
    val p = Path()
    for (i in 0 until 8) {
        val q = i * TAU / 8; val rr = if (i % 2 == 1) r * .22f else r
        if (i == 0) p.moveTo(cx + cos(q) * rr, cy + sin(q) * rr) else p.lineTo(cx + cos(q) * rr, cy + sin(q) * rr)
    }
    p.close(); drawPath(p, Color(0xFFFFF3D6), alpha = a.coerceIn(0f, 1f))
}

private fun DrawScope.hring(cx: Float, cy: Float, r: Float, a: Float, rot: Float, sz: Float, col: Color, d: Float) {
    for (i in 0 until 14) {
        val q = i * TAU / 14 + rot; val x = cx + cos(q) * r; val y = cy + sin(q) * r
        if (i % 2 == 1) star(x, y, sz * .8f, a) else vheart(x, y, sz, a, true, d, col)
    }
}

private fun DrawScope.blush(cx: Float, cy: Float, r: Float, a: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF508C).copy(alpha = a.coerceIn(0f, 1f)), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
}

private fun qb(u: Float, p0: Offset, p1: Offset, p2: Offset): Offset {
    val v = 1 - u
    return Offset(v * v * p0.x + 2 * v * u * p1.x + u * u * p2.x, v * v * p0.y + 2 * v * u * p1.y + u * u * p2.y)
}

/**
 * ROUND 25 — the design's centre-stage show as a chat overlay.
 * Same Canvas rendering as the demo screen, but the shell is a
 * plain fillMaxSize Box with NO background (the chat shows through)
 * and NO pointer input (taps fall through to the chat beneath,
 * exactly like RdFxOverlay). The frame loop is the demo's
 * withFrameNanos loop; it triggers [kind] on launch and calls
 * [onDone] exactly once — when the hero window is over AND every
 * particle has aged out, with a 6.5 s hard cap (the design's
 * longest-lived particle, a 3 s floater emitted at the 3.2 s hero
 * end, can legitimately live to ~6.2 s; a flatter cap would pop
 * still-visible hearts off the screen).
 */
@Composable
fun EmojiFxOverlay(kind: Int, onDone: () -> Unit) {
    val fx = remember { Fx() }
    var tick by remember { mutableLongStateOf(0L) }
    val d = LocalDensity.current.density
    val ctx = LocalContext.current
    // The demo rebuilt this map on every recomposition. Decoded ONCE
    // per overlay here — via BitmapFactory, because the demo's
    // ImageBitmap.imageResource is a @Composable read and cannot
    // live inside remember{}.
    val imgs = remember(ctx) {
        DRAW.mapValues { (_, resId) -> BitmapFactory.decodeResource(ctx.resources, resId).asImageBitmap() }
    }

    LaunchedEffect(Unit) {
        fx.trigger(kind)
        var last = 0L
        var start = 0L
        var finished = false
        while (!finished) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                val dt = if (last == 0L) .016f else ((now - last) / 1e9f).coerceAtMost(.05f)
                last = now; fx.step(dt); tick = now
                // fx.t crosses HERO once and then freezes (step()'s
                // own guard), so >= is the robust form of "t > HERO".
                if ((fx.t >= HERO && fx.parts.isEmpty()) || (now - start) / 1e9f > 6.5f) finished = true
            }
        }
        onDone()
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            tick
            fx.w = size.width; fx.h = size.height; fx.d = d
            val cx = fx.cx; val cy = fx.cy; val S = fx.S
            val on = fx.kind >= 0 && fx.t < HERO
            val h = if (on) hero(fx.kind, fx.t) else null
            if (h != null) {
                val a = h[4]; val t = fx.t
                if (fx.kind == 4) blush(cx, cy, S * .95f, a * (.3f + .12f * sin(t * 5f)))
                if (fx.kind == 4 || fx.kind == 5)
                    for (sx in listOf(-1f, 1f)) blush(cx + sx * S * .3f, cy + S * .1f + h[1] * d, S * .28f, a * .5f)
                if (fx.kind == 2) {
                    val du = if (t > 1.4f && t < 2.3f) sin((t - 1.4f) / .9f * PI.toFloat()) else 0f
                    val bi = a * (.25f + .55f * min(1f, t / 1.2f) + .2f * du); val hy = cy + S * .12f + h[1] * d
                    val hl = min(1f, max(0f, (t - .5f) / .3f))
                    for (sx in listOf(-1f, 1f)) {
                        blush(cx + sx * S * .3f, cy + S * .1f + h[1] * d, S * .32f, bi)
                        for (j in 0..2) {
                            val x0 = cx + sx * S * .56f + (j - 1) * S * .06f
                            drawLine(Color(0xFFFF3D7F), Offset(x0, hy - S * .05f), Offset(x0 + S * .035f, hy + S * .05f), 3.5f * d, StrokeCap.Round,
                                alpha = (a * hl * (.6f + .4f * sin(t * 12f))).coerceIn(0f, 1f))
                        }
                    }
                    if (t > .6f) {
                        val b = max(0f, sin(t * 14f)); val r = S * .1f * (1f + .4f * b); val bx = cx + S * .62f; val by = cy - S * .5f + h[1] * d
                        vheart(bx, by, r, a, true, d, Color(0xFFFF2E7A))
                        for (i in -1..1) {
                            val q = -.8f + i * .5f
                            drawLine(Color(0xFFFF9EC4), Offset(bx + cos(q) * r * 1.5f, by + sin(q) * r * 1.5f), Offset(bx + cos(q) * r * 2f, by + sin(q) * r * 2f),
                                3f * d, StrokeCap.Round, alpha = (a * b).coerceIn(0f, 1f))
                        }
                    }
                }
                if (fx.kind == 0) neon(cx, cy + S * .05f, S * .8f, min(t, 1f), a * (if (t < 1f) 1f else .75f + .25f * sin(t * 10f)), d)
                if (fx.kind == 3) {
                    val c = cos(t * 2.4f); val dir = if (c > 0) 1f else -1f
                    for (j in 0..2) {
                        val y = cy + (j - 1) * S * .18f; val x = cx + h[0] * d - dir * S * .62f
                        drawLine(Color.White, Offset(x, y), Offset(x - dir * S * .18f * (1f - .25f * abs(j - 1)), y),
                            4f * d, StrokeCap.Round, alpha = (a * abs(c) * .5f).coerceIn(0f, 1f))
                    }
                }
            }
            val p0 = Offset(cx + S * .35f, cy); val p1 = Offset(size.width * .95f, size.height * .25f); val p2 = Offset(size.width / 2, size.height * .58f)
            for (p in fx.parts) {
                val sc = min(1f, p.age / .15f); val al = min(1f, (p.max - p.age) / (p.max * .3f))
                val sx = p.x + p.sw * sin(p.age * 2.2f + p.ph)
                when (p.k) {
                    K.IMG -> sprite(imgs[p.im]!!, sx, p.y, p.s * sc, p.r, al)
                    K.VHEART -> vheart(sx, p.y, p.s * .7f * sc, al, false, d)
                    K.STAR -> star(p.x, p.y, p.s * sin(PI.toFloat() * p.age / p.max), 1f)
                    K.RING -> { val u = p.age / p.max; hring(p.x, p.y, p.r0 + (p.r1 - p.r0) * ease(u), 1f - u, u * 1.5f, S * .07f, p.col, d) }
                    K.AR -> {
                        val u = p.age / p.max
                        for (j in 0..2) {
                            val rr = S * .22f + S * .55f * ease(u) + j * S * .13f
                            val al2 = ((1f - u) * (1f - j * .25f)).coerceIn(0f, 1f)
                            for ((a0, a1) in listOf(-.7f to .7f, (PI.toFloat() - .7f) to (PI.toFloat() + .7f)))
                                drawArc(Color(0xFFFFD0E4), a0 * 57.29578f, (a1 - a0) * 57.29578f, false, Offset(p.x - rr, p.y - rr),
                                    androidx.compose.ui.geometry.Size(rr * 2, rr * 2), al2, Stroke(4f * d, cap = StrokeCap.Round))
                        }
                    }
                    K.WV -> {
                        val u = ease(p.age / p.max); val x = p.x + p.side * u * S * 1.15f + sin(u * 10f) * S * .07f * u; val y = p.y - u * size.height * .2f
                        val al2 = min(1f, (1 - u) * 3 + .1f)
                        vheart(x, y, size.width * (.03f + .04f * u), al2, true, d, Color(0xFFFF8FB8))
                        star(x + rnd(-8f, 8f) * d, y + rnd(-8f, 8f) * d, 5f * d, .7f * al2)
                    }
                    K.TK -> {
                        val u = p.age / p.max
                        for (sd in listOf(1f, -1f)) for (i in 0..2) {
                            val q = if (sd > 0) -.2f - i * .5f else PI.toFloat() + .2f + i * .5f
                            val r0 = S * .55f + S * .25f * u; val r1 = r0 + S * .14f * (1 - u)
                            drawLine(Color(0xFFFFD0E4), Offset(cx + cos(q) * r0, cy + sin(q) * r0), Offset(cx + cos(q) * r1, cy + sin(q) * r1),
                                4f * d, StrokeCap.Round, alpha = (1 - u).coerceIn(0f, 1f))
                        }
                    }
                    K.TRAIL -> {
                        val u = ease(p.age / p.max); val o = qb(u, p0, p1, p2)
                        vheart(o.x, o.y, size.width * (.03f + .05f * u), min(1f, (1 - u) * 4 + .2f), true, d)
                        star(o.x + rnd(-9f, 9f) * d, o.y + rnd(-9f, 9f) * d, 6f * d, .8f)
                    }
                    K.STAMP -> sprite(imgs["lips"]!!, p.x, p.y, p.s * back(min(p.age / .35f, 1f)), -12f, al)
                }
            }
            if (h != null) sprite(imgs[KEYS[fx.kind]]!!, cx + h[0] * d, cy + h[1] * d, S * h[2], h[3], h[4])
        }
    }
}
