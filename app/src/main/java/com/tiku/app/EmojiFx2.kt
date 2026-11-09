package com.tiku.app

// ================= ROUND 66 — the owner's "Chat Emoji Effects" HTML
// demo, ported NATIVELY =================
// Source of truth: ~/workspace/user/files/Chat_Emoji_Effects__3s__0_i3qx.html
// — a data-driven Canvas2D engine: a spec table (SP) of per-emoji
// effects (hero animation h, background bg, foreground fg, emitters
// em built by E(), one-shot hits built from the L library, a water
// system for 'cry'), a generic particle engine (spawn/step/dp), and
// pre-rendered procedural sprites (glass hearts, shards, drops,
// glyph text). This file transcribes that engine mechanically:
//   * Same spec data, same order (SP2 index == the demo's SP index,
//     INCLUDING index 1 'kiss' — transcribed for completeness but
//     NEVER routed: 😘 keeps the Round-25 kind-1 show byte-for-byte;
//     see Chat.kt singleEmojiFx2Kind, which has no 😘 entry).
//   * Same timing: DUR = 4 spec-seconds; the demo's rAF loop steps
//     dt*4/3, so the whole show is 3.0 REAL seconds. The overlay
//     below does exactly the same step scaling.
//   * Same units: the demo works in device pixels (W/H = canvas px,
//     D = min(devicePixelRatio, 2)); this port draws on the Compose
//     Canvas' native android.graphics.Canvas (the closest relative
//     of Canvas2D — same gradients, dashes, shadows, Porter-Duff),
//     with w/h = canvas px and d = min(density, 2).
//   * The demo's tap vibration and bottom button bar are demo chrome
//     and, like Round 25's shell, do not ship.
// The overlay is transparent, full-screen over chat, carries no
// pointer input, and is hosted by Chat.kt's playback shell next to
// the Round-25 overlay (one playback at a time).

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as ACanvas
import android.graphics.Color as AColor
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path as APath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.*
import kotlin.random.Random

private const val DUR2 = 4f
private const val T2 = 6.2831855f

// JS colour constants (engine.js: const P_=…, gl=c=>'gl:'+c)
private const val PC = "#ff2d9b"
private const val RS = "#ff5a7a"
private const val RD = "#ff2a3a"
private const val GD = "#ffb43a"
private const val CY = "#4dd2ff"
private const val VT = "#a56bff"
private fun gl(c: String) = "gl:$c"

private fun ease2(x: Float) = 1f - (1f - x).pow(3)
private fun back2(x: Float): Float {
    val c = 1.70158f; val c3 = c + 1f
    return 1f + c3 * (x - 1f).pow(3) + c * (x - 1f).pow(2)
}
private fun clamp2(x: Float, a: Float, b: Float) = max(a, min(b, x))

/** Parse a JS colour spec — "#rrggbb", "r,g,b" or "rgb(r,g,b)" — to
 *  a packed RGB int (alpha handled separately, exactly like the demo
 *  where colour and globalAlpha are independent). */
private fun rgbOf(spec: String): Int {
    val s = spec.trim()
    if (s.startsWith("#")) {
        val v = s.substring(1).toInt(16)
        return v and 0xFFFFFF
    }
    val inner = if (s.startsWith("rgb")) s.substring(s.indexOf('(') + 1, s.indexOf(')')) else s
    val p = inner.split(",").map { it.trim().toInt() }
    return (p[0] shl 16) or (p[1] shl 8) or p[2]
}
private fun argbOf(spec: String, alpha: Float): Int {
    val rgb = rgbOf(spec)
    val a = (clamp2(alpha, 0f, 1f) * 255f).roundToInt()
    return (a shl 24) or rgb
}

// The heart curve table (JS HPT, 121 points).
private val HPT: List<FloatArray> = List(121) { i ->
    val a = i / 120f * T2; val s = sin(a)
    floatArrayOf(
        16f * s * s * s / 17f,
        -(13f * cos(a) - 5f * cos(2f * a) - 2f * cos(3f * a) - cos(4f * a)) / 17f,
    )
}

private fun heartPath(cx: Float, cy: Float, r: Float, i0: Int = 0, i1: Int = 120): APath {
    val p = APath()
    for (i in i0..i1) {
        val x = cx + HPT[i][0] * r; val y = cy + HPT[i][1] * r
        if (i == i0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    return p
}

/** JS dropPath() in a 200-wide coordinate space. */
private fun dropPath(s: Float): APath {
    val p = APath()
    p.moveTo(s * .5f, s * .05f)
    p.cubicTo(s * .5f, s * .05f, s * .12f, s * .45f, s * .12f, s * .64f)
    val cx = s * .5f; val cy = s * .64f; val r = s * .38f
    p.arcTo(RectF(cx - r, cy - r, cx + r, cy + r), 180f, -180f, false)
    p.cubicTo(s * .88f, s * .45f, s * .5f, s * .05f, s * .5f, s * .05f)
    return p
}

// ---------- sprite store: the demo's IMG bitmaps + procedural art --
private val DRAW2 = mapOf(
    "heart" to R.drawable.ios_heart, "kiss" to R.drawable.ios_kiss,
    "hearteyes" to R.drawable.ios2_hearteyes, "love" to R.drawable.ios2_love,
    "hands" to R.drawable.ios2_hands, "lips2" to R.drawable.ios2_lips2,
    "smirk" to R.drawable.ios2_smirk, "cool" to R.drawable.ios2_cool,
    "peach" to R.drawable.ios2_peach, "fire" to R.drawable.ios2_fire,
    "peek" to R.drawable.ios_peek, "giggle" to R.drawable.ios_giggle,
    "eyes" to R.drawable.ios_eyes, "beg" to R.drawable.ios2_beg,
    "cry" to R.drawable.ios2_cry, "broken" to R.drawable.ios2_broken,
    "rage" to R.drawable.ios2_rage, "mid" to R.drawable.ios2_mid,
    "lol" to R.drawable.ios2_lol, "sleep" to R.drawable.ios2_sleep,
    "roll" to R.drawable.ios2_roll, "kissing" to R.drawable.ios_kissing,
    "anger" to R.drawable.ios2_anger, "boom" to R.drawable.ios2_boom,
    "spark" to R.drawable.ios_spark, "hand" to R.drawable.ios2_hand,
    "h1" to R.drawable.ios_h1, "h2" to R.drawable.ios_h2, "h3" to R.drawable.ios_h3,
    "lips" to R.drawable.ios_lips,
)

private class Fx2Sprites(private val ctx: Context) {
    private val bmpCache = HashMap<String, Bitmap?>()
    private val glassCache = HashMap<String, Bitmap>()
    private val shardCache = HashMap<String, Bitmap>()
    private val glyphCache = HashMap<String, Bitmap>()

    fun bmp(key: String): Bitmap? = bmpCache.getOrPut(key) {
        DRAW2[key]?.let { BitmapFactory.decodeResource(ctx.resources, it) }
    }

    /** JS SPR(): resolve a sprite spec string to a bitmap. */
    fun spr(name: String): Bitmap? = when {
        name.startsWith("gl:") -> glass(name.substring(3))
        name.startsWith("i:") -> bmp(name.substring(2))
        name == "dr" -> dropBmp
        name == "bd" -> bdropBmp
        name.startsWith("sh:") -> shard(name.substring(3))
        name.startsWith("gy:") -> {
            val parts = name.split(":")
            if (parts.size >= 3) glyph(parts[1], parts[2]) else null
        }
        else -> null
    }

    /** JS glass(col): the pre-rendered glass heart, 512×512. */
    fun glass(col: String): Bitmap = glassCache.getOrPut(col) {
        val w = 512; val bmp = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888)
        val c = ACanvas(bmp)
        val cx = w / 2f; val cy = w / 2f + 10f; val r = w * .34f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
        val path = heartPath(cx, cy, r)
        c.save(); c.clipPath(path)
        paint.style = Paint.Style.FILL; paint.strokeWidth = 0f; paint.setShadowLayer(0f, 0f, 0f, 0)
        paint.shader = RadialGradient(
            cx - r * .4f, cy - r * .5f, r * 1.3f,
            intArrayOf(argbOf("#ffffff", .4f), argbOf(col, 0x26 / 255f), argbOf(col, 0x88 / 255f)),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w.toFloat(), w.toFloat(), paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 46f
        paint.color = argbOf(col, .5f); paint.setShadowLayer(40f, 0f, 0f, argbOf(col, 1f))
        c.drawPath(path, paint)
        c.restore()
        paint.alpha = 255; paint.strokeWidth = 16f; paint.color = argbOf(col, 1f)
        paint.setShadowLayer(36f, 0f, 0f, argbOf(col, 1f))
        c.drawPath(path, paint)
        paint.strokeWidth = 6f; paint.color = AColor.WHITE
        paint.setShadowLayer(10f, 0f, 0f, argbOf(col, 1f))
        c.drawPath(path, paint)
        paint.setShadowLayer(0f, 0f, 0f, 0)
        paint.strokeWidth = 8f; paint.color = argbOf("#ffffff", .85f)
        c.drawPath(heartPath(cx, cy, r * .82f, 84, 104), paint)
        paint.strokeWidth = 5f; paint.color = argbOf("#ffffff", .35f)
        c.drawPath(heartPath(cx, cy, r * .84f, 38, 52), paint)
        bmp
    }

    /** JS drop: the glass tear drop, 200×300. */
    val dropBmp: Bitmap by lazy {
        val w = 200; val h = 300
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = ACanvas(bmp)
        val path = dropPath(w.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.save(); c.clipPath(path)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            argbOf("170,215,255", .5f), argbOf("60,130,240", .75f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w.toFloat(), w * 1.5f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 30f
        paint.color = argbOf("#4da3ff", .45f); paint.setShadowLayer(26f, 0f, 0f, argbOf("#4da3ff", 1f))
        c.drawPath(path, paint)
        c.restore()
        paint.strokeWidth = 7f; paint.color = argbOf("#9fd4ff", 1f)
        paint.setShadowLayer(18f, 0f, 0f, argbOf("#8fc8ff", 1f))
        c.drawPath(path, paint)
        paint.strokeWidth = 3f; paint.color = argbOf("#ffffff", .7f)
        paint.setShadowLayer(0f, 0f, 0f, 0)
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL; paint.color = argbOf("#ffffff", .85f)
        c.save(); c.translate(w * .34f, w * .7f); c.rotate(.3f * 180f / PI.toFloat())
        c.drawOval(RectF(-w * .05f, -w * .11f, w * .05f, w * .11f), paint)
        c.restore()
        bmp
    }

    /** JS bdrop: the soft blue drop silhouette, 200×300. */
    val bdropBmp: Bitmap by lazy {
        val bmp = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        val c = ACanvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = argbOf("77,163,255", .45f)
            setShadowLayer(40f, 0f, 0f, argbOf("#4da3ff", 1f))
        }
        c.drawPath(dropPath(200f), paint)
        bmp
    }

    /** JS shard(col): a glass triangle, 160×160. */
    fun shard(col: String): Bitmap = shardCache.getOrPut(col) {
        val bmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val c = ACanvas(bmp)
        val path = APath().apply {
            moveTo(80f, 10f); lineTo(148f, 130f); lineTo(20f, 110f); close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG); paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            20f, 0f, 148f, 0f,
            argbOf("#ffffff", .4f), argbOf(col, 0x88 / 255f), Shader.TileMode.CLAMP,
        )
        c.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f
        paint.color = argbOf(col, 1f); paint.setShadowLayer(18f, 0f, 0f, argbOf(col, 1f))
        c.drawPath(path, paint)
        paint.strokeWidth = 3f; paint.color = AColor.WHITE
        paint.setShadowLayer(4f, 0f, 0f, argbOf(col, 1f))
        c.drawPath(path, paint)
        bmp
    }

    /** JS GY(txt,col): a glowing text glyph bitmap. */
    fun glyph(txt: String, col: String): Bitmap = glyphCache.getOrPut(txt + col) {
        val fs = 170f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(Typeface.SANS_SERIF, 800, false)
        } else {
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        paint.textSize = fs; paint.textAlign = Paint.Align.CENTER; paint.strokeJoin = Paint.Join.ROUND
        val w = ceil(paint.measureText(txt)).toInt() + 60
        val h = (fs + 70f).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = ACanvas(bmp)
        val baseline = h / 2f + 6f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        paint.style = Paint.Style.FILL
        paint.color = argbOf(col, 0x33 / 255f); paint.setShadowLayer(30f, 0f, 0f, argbOf(col, 1f))
        c.drawText(txt, w / 2f, baseline, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 12f; paint.color = argbOf(col, 1f)
        c.drawText(txt, w / 2f, baseline, paint)
        paint.strokeWidth = 4f; paint.color = AColor.WHITE
        paint.setShadowLayer(8f, 0f, 0f, argbOf(col, 1f))
        c.drawText(txt, w / 2f, baseline, paint)
        bmp
    }
}

// ---------- particle / emitter / spec model (JS P, E(), SP) --------
private class P2(
    var k: String,
    var sp: Bitmap? = null,
    var x: Float = 0f, var y: Float = 0f,
    var vx: Float = 0f, var vy: Float = 0f,
    var g: Float = 0f, var drag: Float = 0f,
    var s: Float = 0f, var max: Float = 1f,
    var r: Float = 0f, var vr: Float = 0f,
    var age: Float = 0f, var ph: Float = 0f, var sw: Float = 0f,
    var grow: Boolean = false, var shr: Boolean = false, var add: Boolean = false,
    var col: String? = null,
    var lit: Boolean = false, var spl: Boolean = false,
    var al: Float? = null, var fo: Float? = null,
    var top: Boolean = false,
    var r0: Float = 0f, var r1: Float = 0f, var a: Float = 0f,
    var pts: ArrayList<FloatArray>? = null,
    var p0: FloatArray? = null, var p1: FloatArray? = null, var p2: FloatArray? = null,
    var sHero: Float = 0f,
)

/** JS E(o): emitter spec with the demo's exact defaults. */
private class E2(
    val k: String? = null,
    val sp: List<String>? = null,
    val rate: Float = 5f, val t0: Float = 0f, val t1: Float = 3.3f,
    val o: String = "bot",
    val vx: FloatArray = floatArrayOf(-20f, 20f),
    val vy: FloatArray = floatArrayOf(-120f, -260f),
    val g: Float = 0f,
    val s: FloatArray = floatArrayOf(.1f, .2f),
    val life: FloatArray = floatArrayOf(2.2f, 3.2f),
    val r: FloatArray = floatArrayOf(-25f, 25f),
    val vr: FloatArray = floatArrayOf(-8f, 8f),
    val sw: Float = 0f,
    val grow: Boolean = false, val shr: Boolean = false, val add: Boolean = false,
    val col: String? = null, val cols: List<String>? = null,
    val lit: Boolean = false, val spl: Boolean = false,
    val al: Float? = null, val fo: Float? = null,
    val mir: Boolean = false,
) {
    var acc: Float = 0f
    fun fresh(): E2 = E2(k, sp, rate, t0, t1, o, vx, vy, g, s, life, r, vr, sw,
        grow, shr, add, col, cols, lit, spl, al, fo, mir)
}

/** JS pose() output: dx/dy in px, s scale, r degrees, sy y-scale. */
private class Pose2 {
    var dx: Float = 0f; var dy: Float = 0f
    var s: Float = 1f; var r: Float = 0f; var sy: Float = 1f
}

/** JS hpos() output. */
private class HPos(val x: Float, val y: Float, val s: Float, val o: Pose2)

private typealias HeroFn = Fx2.(Float) -> Pose2
private typealias HitFn = Fx2.(HPos, Float) -> Unit
private fun hitFn(f: HitFn): HitFn = f
private typealias BgFn = Dr.(Float, Float, HPos) -> Unit
private typealias FgFn = Dr.(Float, Float, Float, Float, Pose2) -> Unit

private class Spec2(
    val k: String,
    val col: String,
    val px: Float = .5f,
    val py: Float = .3f,
    val np: Boolean = false,
    val h: HeroFn? = null,
    val sh: ((Float) -> Float)? = null,
    val bg: BgFn? = null,
    val fg: FgFn? = null,
    val em: List<E2> = emptyList(),
    val hit: List<Pair<Float, HitFn>> = emptyList(),
    val water: Boolean = false,
)

// ---------- the engine (JS state + trig/spawn/step + L) ------------
private class Fx2 {
    var w: Float = 1f; var h: Float = 1f; var d: Float = 1f
    var kind: Int = -1
    var t: Float = 99f
    var rng: Random = Random
    lateinit var sprites: Fx2Sprites
    val parts = ArrayList<P2>()
    private var ems: List<E2> = emptyList()
    private class HitState(val t: Float, val f: HitFn, var done: Boolean = false)
    private var hits: List<HitState> = emptyList()
    // JS shk = {a, u, d} (null when shkU < 0)
    var shkA: Float = 0f; var shkU: Float = -1f; var shkD: Float = 1f

    fun hs(): Float = w * .4f
    fun rnd(a: Float, b: Float): Float = a + rng.nextFloat() * (b - a)
    fun <T> pick(list: List<T>): T = list[rng.nextInt(list.size)]

    fun trig(i: Int, sp: Fx2Sprites) {
        kind = i; t = 0f; parts.clear(); shkU = -1f; sprites = sp
        val s = SP2[i]
        ems = s.em.map { it.fresh() }
        hits = s.hit.map { HitState(it.first, it.second) }
        sprites.bmp(s.k) // warm the hero decode
    }

    fun pose(sp: Spec2, tt: Float): Pose2 {
        val o = sp.h?.invoke(this, tt) ?: Pose2()
        if (!sp.np) o.s *= back2(min(tt / .4f, 1f))
        return o
    }

    fun hpos(tt: Float): HPos {
        val sp = SP2[kind]; val o = pose(sp, tt)
        return HPos(w * sp.px + o.dx, h * sp.py + o.dy, hs() * o.s, o)
    }

    /** JS beamF / blinkF (the 'eyes' spec helpers). */
    fun beamF(tt: Float): Float {
        val x = sin(min(tt, 2.4f) * 1.9f)
        return w / 2f + (if (tt < 2.4f) x else x * max(0f, 1f - (tt - 2.4f) * 3f)) * w * .35f
    }
    fun blinkF(tt: Float): Float {
        var b = 0f
        for (x in floatArrayOf(1.1f, 1.9f, 2.9f)) b = max(b, 1f - abs(tt - x) / .09f)
        return max(0f, b)
    }

    // ----- the L library -----
    fun burst(x: Float, y: Float, n: Int, sps: List<String>, v: FloatArray,
              s: FloatArray, life: FloatArray, gOpt: Float? = null, top: Boolean = false) {
        repeat(n) {
            val a = rnd(0f, T2); val sp = rnd(v[0], v[1]) * d
            parts += P2(
                k = "gh", sp = sprites.spr(pick(sps)), x = x, y = y,
                vx = cos(a) * sp, vy = sin(a) * sp,
                g = (gOpt ?: 0f) * d, drag = if (gOpt != null && gOpt != 0f) 0f else 1.6f,
                s = rnd(s[0], s[1]) * w, max = rnd(life[0], life[1]),
                r = 0f, vr = rnd(-90f, 90f), ph = 0f, sw = 0f, top = top,
            )
        }
    }
    fun ringP(x: Float, y: Float, r0: Float, r1: Float, col: String, max: Float = .8f) {
        parts += P2(k = "rg", x = x, y = y, r0 = r0, r1 = r1, col = col, max = max)
    }
    fun hring(x: Float, y: Float, r0: Float, r1: Float, spName: String, max: Float = 1.1f) {
        parts += P2(k = "hr", x = x, y = y, r0 = r0, r1 = r1, sp = sprites.spr(spName), max = max)
    }
    fun flash(col: String, a: Float, max: Float = .15f) {
        parts += P2(k = "fs", col = col, a = a, max = max, top = true)
    }
    fun shake(a: Float, dur: Float) { shkA = a; shkU = t + dur; shkD = dur }
    fun crack(x: Float, y: Float, col: String) {
        var a = rnd(0f, T2)
        val pts = ArrayList<FloatArray>(); pts += floatArrayOf(x, y)
        repeat(9) {
            a += rnd(-.6f, .6f)
            val l = rnd(.03f, .07f) * w; val q = pts[pts.size - 1]
            pts += floatArrayOf(q[0] + cos(a) * l, q[1] + sin(a) * l)
        }
        parts += P2(k = "ck", pts = pts, col = col, max = 1.4f)
    }
    fun trail(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, spName: String) {
        parts += P2(k = "tr", max = 1f, p0 = floatArrayOf(x0, y0),
            p1 = floatArrayOf(cx, cy), p2 = floatArrayOf(x1, y1), sp = sprites.spr(spName))
    }
    fun puff(x: Float, y: Float, col: String, n: Int) {
        repeat(n) {
            parts += P2(
                k = "fl", x = x + rnd(-20f, 20f) * d, y = y,
                vx = rnd(-60f, 60f) * d, vy = -rnd(40f, 120f) * d,
                s = rnd(.06f, .12f) * w, max = rnd(1f, 1.6f), col = col,
            )
        }
    }
    fun pop(spName: String, x: Float, y: Float, s: Float, max: Float,
            rO: Float? = null, vyO: Float? = null) {
        parts += P2(
            k = "gh", sp = sprites.spr(spName), x = x, y = y,
            vx = 0f, vy = vyO ?: (-20f * d), s = s, max = max,
            r = rO ?: 0f, vr = 0f, ph = 0f, sw = 0f, grow = true,
        )
    }

    // ----- spawn / splash / step -----
    private fun spawn(e: E2, hp: HPos, S: Float) {
        val m = if (rng.nextBoolean()) -1f else 1f
        var x = 0f; var y = 0f
        when (e.o) {
            "bot" -> { x = rnd(.08f, .92f) * w; y = h - 100f * d }
            "top" -> { x = rnd(0f, w); y = -60f * d }
            "hero" -> { x = hp.x + rnd(-.15f, .15f) * S; y = hp.y + rnd(-.15f, .15f) * S }
            "eyes" -> { x = hp.x + m * .2f * S; y = hp.y - .02f * S }
            "hTop" -> { x = hp.x + rnd(-.15f, .15f) * S; y = hp.y - .45f * S }
            "hs" -> { x = hp.x + m * .5f * S; y = hp.y + rnd(-.2f, .3f) * S }
            "any" -> { x = rnd(.08f, .92f) * w; y = rnd(.08f, .7f) * h }
            "low" -> { x = rnd(.1f, .9f) * w; y = rnd(.45f, 1f) * h }
            "halo" -> {
                val a = rnd(0f, T2); val r = rnd(.4f, .7f) * S
                x = hp.x + cos(a) * r; y = hp.y + sin(a) * r
            }
            "mouthR" -> { x = hp.x + .3f * S; y = hp.y + .12f * S }
            else -> { x = w / 2f; y = h / 2f }
        }
        val mir = if (e.mir) m else 1f
        parts += P2(
            k = e.k ?: "gh", sp = e.sp?.let { sprites.spr(pick(it)) },
            x = x, y = y,
            vx = rnd(e.vx[0], e.vx[1]) * d * mir, vy = rnd(e.vy[0], e.vy[1]) * d,
            g = e.g * d, s = rnd(e.s[0], e.s[1]) * w, max = rnd(e.life[0], e.life[1]),
            r = rnd(e.r[0], e.r[1]), vr = rnd(e.vr[0], e.vr[1]),
            ph = rnd(0f, 6f), sw = e.sw * w,
            grow = e.grow, shr = e.shr, add = e.add,
            col = if (e.cols != null) pick(e.cols) else e.col,
            lit = e.lit, spl = e.spl, al = e.al, fo = e.fo,
        )
    }

    private fun splash(x: Float, lv: Float) {
        parts += P2(k = "rg", x = x, y = h - lv, r0 = 10f * d, r1 = w * .1f,
            col = "200,230,255", max = .8f)
        repeat(4) {
            parts += P2(
                k = "gh", sp = sprites.dropBmp, x = x, y = h - lv - 4f * d,
                vx = rnd(-120f, 120f) * d, vy = -rnd(150f, 380f) * d, g = 1500f * d,
                s = rnd(.02f, .035f) * w, max = 1f, r = 0f, vr = 0f, ph = 0f, sw = 0f,
            )
        }
    }

    fun step(dt: Float) {
        if (kind < 0) return
        t += dt
        if (t >= DUR2) { kind = -1; parts.clear(); return }
        val hpz = hpos(t); val sHS = hs(); val sp = SP2[kind]
        for (e in ems) {
            if (t >= e.t0 && t < min(e.t1, DUR2 - .7f)) {
                e.acc += dt
                while (e.acc > 1f / e.rate) { e.acc -= 1f / e.rate; spawn(e, hpz, hpz.s) }
            }
        }
        for (hh in hits) {
            if (!hh.done && t >= hh.t) { hh.done = true; hh.f.invoke(this, hpz, sHS) }
        }
        val lv = if (sp.water) min(1f, t / .4f) * min(1f, (DUR2 - t) / .5f) * h * .05f else 0f
        var i = 0
        while (i < parts.size) {
            val p = parts[i]
            p.age += dt
            p.x += p.vx * dt; p.y += p.vy * dt
            if (p.g != 0f) p.vy += p.g * dt
            p.r += p.vr * dt
            if (p.drag != 0f) {
                val kk = max(0f, 1f - p.drag * dt); p.vx *= kk; p.vy *= kk
            }
            if (p.spl && p.vy > 0f && p.y > h - lv - 8f * d && lv > 2f) {
                p.age = 999f; splash(p.x, lv)
            }
            i++
        }
        parts.removeAll { it.age >= it.max }
    }
}

// ---------- drawing (JS spr/star/glow/vig/neon/ecg/hform/orbit/shine/dp/draw)
private class Dr(val fx: Fx2, val c: ACanvas) {
    val W: Float get() = fx.w
    val H: Float get() = fx.h
    val D: Float get() = fx.d
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var shineBmp: Bitmap? = null
    private var shineCanvas: ACanvas? = null

    private fun basePaint(): Paint {
        paint.reset(); paint.isAntiAlias = true
        paint.shader = null; paint.pathEffect = null; paint.xfermode = null
        paint.setShadowLayer(0f, 0f, 0f, 0)
        return paint
    }

    /** JS spr(): draw a sprite bitmap centred at (x,y), width w. */
    fun spr(bmp: Bitmap?, x: Float, y: Float, w: Float, r: Float, a: Float) {
        if (bmp == null || w <= 0f) return
        val hh = w * bmp.height / bmp.width
        val p = basePaint(); p.alpha = (clamp2(a, 0f, 1f) * 255f).roundToInt()
        c.save(); c.translate(x, y); c.rotate(r)
        rect.set(-w / 2f, -hh / 2f, w / 2f, hh / 2f)
        c.drawBitmap(bmp, null, rect, p)
        c.restore()
    }

    /** JS star(): the 8-point sparkle polygon with a soft shadow. */
    fun star(cx: Float, cy: Float, r: Float, a: Float, col: String?) {
        if (r <= 0f) return
        val colSpec = col ?: "#fff6e0"
        val path = APath()
        for (i in 0 until 8) {
            val q = i * T2 / 8f; val rr = if (i % 2 == 1) r * .2f else r
            val x = cx + cos(q) * rr; val y = cy + sin(q) * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val p = basePaint(); p.style = Paint.Style.FILL
        p.color = argbOf(colSpec, clamp2(a, 0f, 1f))
        p.setShadowLayer(10f * D, 0f, 0f, argbOf(col ?: "#ffffff", clamp2(a, 0f, 1f)))
        c.drawPath(path, p)
    }

    /** JS glow(): a radial-gradient disc (optionally y-squashed). */
    fun glow(cx: Float, cy: Float, r: Float, col: String, a: Float, sy: Float = 1f, add: Boolean = false) {
        if (r <= 0f) return
        val p = basePaint(); p.style = Paint.Style.FILL
        if (add) p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        c.save(); c.translate(cx, cy); c.scale(1f, sy)
        p.shader = RadialGradient(
            0f, 0f, r,
            intArrayOf(argbOf(col, a), argbOf(col, 0f)), floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawCircle(0f, 0f, r, p)
        c.restore()
    }

    /** JS vig(): the edge vignette in an "r,g,b" colour. */
    fun vig(col: String, a: Float) {
        val p = basePaint(); p.style = Paint.Style.FILL
        val cx = W / 2f; val cy = H / 2f; val r1 = H * .75f; val r0 = H * .25f
        p.shader = RadialGradient(
            cx, cy, r1,
            intArrayOf(argbOf(col, 0f), argbOf(col, a)), floatArrayOf(r0 / r1, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, W, H, p)
    }

    /** JS neon(): a stroke paint with colour, width and shadow glow. */
    fun neonPaint(col: String, lw: Float, blur: Float, alpha: Float): Paint {
        val p = basePaint()
        p.style = Paint.Style.STROKE; p.strokeWidth = lw
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.color = argbOf(col, alpha)
        if (blur != 0f) p.setShadowLayer(blur * D, 0f, 0f, argbOf(col, alpha))
        return p
    }

    /** JS ecg(): the heartbeat trace for the 'heart' spec. */
    fun ecg(tt: Float, col: String) {
        val pts = arrayOf(
            floatArrayOf(0f, 0f), floatArrayOf(.28f, 0f), floatArrayOf(.36f, -.03f),
            floatArrayOf(.4f, .05f), floatArrayOf(.45f, -.3f), floatArrayOf(.5f, .4f),
            floatArrayOf(.55f, -.1f), floatArrayOf(.6f, 0f), floatArrayOf(.72f, 0f),
            floatArrayOf(.77f, -.05f), floatArrayOf(.83f, 0f), floatArrayOf(1f, 0f),
        )
        val pr = clamp2((tt - .1f) / 1.2f, 0f, 1f)
        val n = pr * (pts.size - 1); val y0 = H * .64f; val amp = H * .25f
        val path = APath()
        val fi = floor(n).toInt()
        for (i in 0..fi) {
            val q = pts[i]
            if (i == 0) path.moveTo(q[0] * W, y0 + q[1] * amp) else path.lineTo(q[0] * W, y0 + q[1] * amp)
        }
        if (fi < pts.size - 1) {
            val a = pts[fi]; val b = pts[fi + 1]; val f = n - fi
            path.lineTo((a[0] + (b[0] - a[0]) * f) * W, y0 + (a[1] + (b[1] - a[1]) * f) * amp)
        }
        c.drawPath(path, neonPaint("rgb($col)", 4f * D, 18f, 1f))
    }

    /** JS hform(): the heart-outline formation for the 'hands' spec. */
    fun hform(tt: Float, hx: Float, hy: Float) {
        val S = fx.hs(); val n = 22; val r0 = S * 1.15f
        for (i in 0 until n) {
            val st = .3f + i * .05f
            if (tt < st) continue
            val u = min(1f, (tt - st) / .35f)
            val q = HPT[floor(i.toFloat() / n * 120f).toInt()]
            val rel = if (tt > 3f) ease2(clamp2((tt - 3f) / .7f, 0f, 1f)) else 0f
            val beat = if (tt > 1.8f && tt < 3f) 1f + .18f * max(0f, sin((tt - 1.8f) * 9f)) else 1f
            val bx = hx + q[0] * r0; val by = hy + q[1] * r0 - S * .05f
            spr(
                fx.sprites.glass(if (i % 2 == 1) GD else "#ff5a9a"),
                bx + (bx - hx) * rel * .8f, by + (by - hy) * rel * .8f - rel * H * .08f,
                W * .1f * back2(u) * beat, i * 20f, 1f - rel,
            )
        }
    }

    /** JS orbit(): the orbiting hearts for the 'love' spec. */
    fun orbit(tt: Float, hx: Float, hy: Float, front: Boolean) {
        val S = fx.hs()
        for (i in 0 until 6) {
            val a = tt * 1.8f + i * T2 / 6f; val sn = sin(a)
            if ((sn >= 0f) != front) continue
            val sc = .8f + .25f * sn
            val bmp = if (i % 2 == 1) fx.sprites.bmp("h1")
                else if (i % 3 != 0) fx.sprites.bmp("h3")
                else fx.sprites.glass(PC)
            spr(bmp, hx + cos(a) * S * .78f, hy + sn * S * .3f, W * .12f * sc, 0f, clamp2(tt / .6f, 0f, 1f))
        }
    }

    /** JS shine(): a light sweep clipped to the hero face. */
    fun shine(key: String, x: Float, y: Float, S: Float, o: Pose2, pos: Float) {
        val hero = fx.sprites.bmp(key) ?: return
        val n = ceil(S * 1.5f).toInt()
        if (n <= 0) return
        if (shineBmp == null || shineBmp!!.width != n) {
            shineBmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            shineCanvas = ACanvas(shineBmp!!)
        }
        val tg = shineCanvas!!
        tg.drawColor(AColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val p = basePaint()
        tg.save(); tg.translate(n / 2f, n / 2f); tg.rotate(o.r); tg.scale(o.s, o.s * o.sy)
        rect.set(-S / 2f, -S / 2f, S / 2f, S / 2f)
        tg.drawBitmap(hero, null, rect, p)
        tg.restore()
        val bx = -n * .2f + pos * n * 1.4f
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        p.shader = LinearGradient(
            bx - n * .14f, 0f, bx + n * .14f, n * .1f,
            intArrayOf(argbOf("#ffffff", 0f), argbOf("#ffffff", .9f), argbOf("#ffffff", 0f)),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP,
        )
        tg.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
        val p2 = basePaint()
        c.drawBitmap(shineBmp!!, x - n / 2f, y - n / 2f, p2)
    }

    /** JS dp(): draw one particle. */
    fun dp(p: P2) {
        val u = if (p.max > 0f) p.age / p.max else 1f
        val fo = min(1f, (p.max - p.age) / (p.fo ?: .6f))
        val fi = min(1f, p.age / .25f)
        when (p.k) {
            "gh" -> {
                var al = fi * fo * (p.al ?: 1f)
                if (p.lit) al *= .3f + .7f * max(0f, 1f - abs(p.x - fx.beamF(fx.t)) / (W * .22f))
                val gs = if (p.grow) .3f + .7f * ease2(min(p.age / .6f, 1f)) else 1f
                spr(p.sp, p.x + (if (p.sw != 0f) sin(p.age * 2.2f + p.ph) * p.sw else 0f), p.y, p.s * gs, p.r, al)
            }
            "st" -> star(p.x, p.y, p.s * sin(PI.toFloat() * u), 1f, p.col)
            "mt" -> {
                val pp = basePaint(); pp.style = Paint.Style.FILL
                pp.color = argbOf("#cfe6ff", fi * fo * (.5f + .5f * sin(p.age * 6f)))
                c.drawCircle(p.x, p.y, p.s, pp)
            }
            "fl" -> glow(p.x, p.y, p.s * (if (p.shr) 1f - .7f * u else .6f + .5f * u), p.col ?: "255,255,255", .55f * fi * fo, 1f, p.add)
            "rg" -> {
                val r = p.r0 + (p.r1 - p.r0) * ease2(u)
                val pp = neonPaint("rgb(${p.col})", 4f * D * (1f - u * .5f), 14f, 1f - u)
                pp.pathEffect = DashPathEffect(floatArrayOf(16f * D, 12f * D), -p.age * 200f * D)
                c.drawCircle(p.x, p.y, r, pp)
            }
            "hr" -> {
                val r = p.r0 + (p.r1 - p.r0) * ease2(u)
                for (i in 0 until 12) {
                    val q = i * T2 / 12f + u * 1.5f
                    val x = p.x + cos(q) * r; val y = p.y + sin(q) * r
                    if (i % 2 == 1) star(x, y, W * .025f, 1f - u, null)
                    else spr(p.sp, x, y, W * .08f * (1f - u * .3f), 0f, 1f - u)
                }
            }
            "ip" -> {
                val pp = neonPaint("#ffd0e4", 4f * D, 10f, 1f - u)
                for (i in 0 until 9) {
                    val q = i * T2 / 9f
                    val r0 = p.sHero * .15f + p.sHero * .2f * u
                    val r1 = r0 + p.sHero * .12f * (1f - u)
                    c.drawLine(p.x + cos(q) * r0, p.y + sin(q) * r0, p.x + cos(q) * r1, p.y + sin(q) * r1, pp)
                }
            }
            "fs" -> {
                val pp = basePaint(); pp.style = Paint.Style.FILL
                pp.color = argbOf("rgb(${p.col})", p.a * (1f - u))
                c.drawRect(0f, 0f, W, H, pp)
            }
            "ck" -> {
                val pts = p.pts ?: return
                val n = floor(pts.size * min(1f, p.age / .18f)).toInt()
                val path = APath()
                for (i in 0..min(n, pts.size - 1)) {
                    if (i == 0) path.moveTo(pts[i][0], pts[i][1]) else path.lineTo(pts[i][0], pts[i][1])
                }
                c.drawPath(path, neonPaint(p.col ?: "#ffffff", 3f * D, 12f, fo))
            }
            "tk" -> {
                val pp = neonPaint("#ffd0e4", 4f * D, 0f, 1f - u)
                for (sd in floatArrayOf(1f, -1f)) for (i in 0 until 3) {
                    val q = if (sd > 0f) -.2f - i * .5f else PI.toFloat() + .2f + i * .5f
                    val r0 = p.sHero * .55f + p.sHero * .25f * u
                    val r1 = r0 + p.sHero * .14f * (1f - u)
                    c.drawLine(p.x + cos(q) * r0, p.y + sin(q) * r0, p.x + cos(q) * r1, p.y + sin(q) * r1, pp)
                }
            }
            "tr" -> {
                val e = ease2(u); val v = 1f - e
                val p0 = p.p0!!; val p1 = p.p1!!; val p2 = p.p2!!
                val x = v * v * p0[0] + 2f * v * e * p1[0] + e * e * p2[0]
                val y = v * v * p0[1] + 2f * v * e * p1[1] + e * e * p2[1]
                spr(p.sp, x, y, W * (.07f + .08f * e), e * 60f, min(1f, (1f - e) * 4f + .2f))
                star(x + fx.rnd(-9f, 9f) * D, y + fx.rnd(-9f, 9f) * D, 6f * D, .8f, null)
            }
            "sl" -> {
                val dir = if (p.vx < 0f) 1f else -1f
                val pp = basePaint()
                pp.style = Paint.Style.STROKE; pp.strokeWidth = 3f * D; pp.strokeCap = Paint.Cap.ROUND
                pp.alpha = (clamp2(fo, 0f, 1f) * 255f).roundToInt()
                pp.shader = LinearGradient(
                    p.x, 0f, p.x + p.s * dir, 0f,
                    intArrayOf(argbOf("160,230,255", .9f), argbOf("160,230,255", 0f)),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
                )
                c.drawLine(p.x, p.y, p.x + p.s * dir, p.y, pp)
            }
        }
    }

    /** JS draw(): background, back particles, hero, foreground, top particles. */
    fun drawAll() {
        if (fx.kind < 0) return
        val sp = SP2[fx.kind]; val t = fx.t
        val env = min(1f, t / .4f) * min(1f, (DUR2 - t) / .5f)
        val hp = fx.hpos(t)
        var sa = sp.sh?.invoke(t) ?: 0f
        if (fx.shkU >= 0f && t < fx.shkU) sa += fx.shkA * (fx.shkU - t) / fx.shkD
        c.save()
        if (sa != 0f) c.translate(fx.rnd(-sa, sa) * D, fx.rnd(-sa, sa) * D)
        sp.bg?.invoke(this, t, env, hp)
        for (p in fx.parts) if (!p.top) dp(p)
        val S = fx.hs(); val o = hp.o
        glow(hp.x, hp.y, S * .85f, sp.col, .26f * min(1f, t / .4f))
        val hero = fx.sprites.bmp(sp.k)
        if (hero != null) {
            val p = basePaint()
            c.save(); c.translate(hp.x, hp.y); c.rotate(o.r); c.scale(o.s, o.s * o.sy)
            rect.set(-S / 2f, -S / 2f, S / 2f, S / 2f)
            c.drawBitmap(hero, null, rect, p)
            c.restore()
        }
        sp.fg?.invoke(this, t, hp.x, hp.y, S, o)
        for (p in fx.parts) if (p.top) dp(p)
        c.restore()
    }
}

// ---------- the specs (JS SP, same order; index 1 'kiss' is
// transcribed but NEVER routed — 😘 stays on Round-25 kind 1) --------
private val SP2: List<Spec2> = listOf(
    // 0 — heart ❤️
    Spec2(
        k = "heart", col = "255,40,90",
        h = { t ->
            val ph = if (t < .3f) 9f else (t - .3f) % .7f
            Pose2().apply {
                s = 1f + .2f * exp(-ph * 8f) + (if (ph > .22f) .12f * exp(-(ph - .22f) * 10f) else 0f)
            }
        },
        bg = { t, env, _ ->
            vig("255,30,70", .05f + .08f * env); ecg(t, "255,60,100")
        },
        em = listOf(E2(sp = listOf(gl(RD), gl(PC)), rate = 3f, s = floatArrayOf(.08f, .18f))),
        hit = listOf(.3f, 1f, 1.7f, 2.4f, 3.1f).map { b ->
            val f: HitFn = { hp, s ->
                hring(hp.x, hp.y, s * .5f, s * 1.3f, gl(RD), 1.1f)
                burst(hp.x, hp.y, 6, listOf(gl(RD), gl(PC), "i:heart"),
                    floatArrayOf(250f, 500f), floatArrayOf(.05f, .1f), floatArrayOf(1f, 1.5f))
            }
            b to f
        },
    ),
    // 1 — kiss 😘 (TRANSCRIBED ONLY — never routed, see header)
    Spec2(
        k = "kiss", col = "255,60,150", px = .3f, py = .2f,
        h = { t ->
            Pose2().apply {
                r = -10f * sin(min(t, 1f) * 3f) + (if (t > .4f) 6f * exp(-(t - .4f) * 3f) else 0f)
                s = 1f + .25f * exp(-abs(t - .4f) * 10f)
            }
        },
        em = listOf(
            E2(sp = listOf(gl(PC)), rate = 3f, s = floatArrayOf(.1f, .2f), grow = true),
            E2(k = "st", o = "any", rate = 9f, life = floatArrayOf(.6f, .9f),
                s = floatArrayOf(.02f, .05f), vx = floatArrayOf(0f, 0f), vy = floatArrayOf(0f, 0f)),
        ),
        hit = (0 until 8).map { i ->
            val f: HitFn = { hp, s ->
                trail(hp.x + s * .14f, hp.y + s * .05f, w * .65f, h * .08f, w * .66f, h * .42f,
                    if (i % 2 == 1) "i:h1" else gl(PC))
            }
            (.4f + i * .12f) to f
        } + listOf(1.5f to hitFn { _: HPos, _: Float ->
            val x = w * .66f; val y = h * .42f
            pop("i:lips", x, y, w * .3f, 1.6f, rO = -12f)
            pop(gl(PC), x, y, w * .5f, 2.2f)
            burst(x, y, 14, listOf(gl(PC), "i:h1", "i:h3", "i:heart", "i:lips"),
                floatArrayOf(300f, 750f), floatArrayOf(.06f, .14f), floatArrayOf(1.2f, 2f))
        }),
    ),
    // 2 — hearteyes 😍
    Spec2(
        k = "hearteyes", col = "255,70,150",
        h = { t -> Pose2().apply { s = 1f + .07f * sin(t * 9f); r = 3f * sin(t * 6f) } },
        bg = { t, env, _ -> vig("255,40,140", .05f + .07f * sin(t * 5f) * env) },
        em = listOf(
            E2(o = "eyes", mir = true, sp = listOf(gl(PC), "i:h1", "i:h3", "i:heart"),
                rate = 12f, t0 = .3f, vx = floatArrayOf(120f, 320f), vy = floatArrayOf(-100f, -380f),
                s = floatArrayOf(.05f, .1f), life = floatArrayOf(1f, 1.6f),
                r = floatArrayOf(-30f, 30f), vr = floatArrayOf(-40f, 40f)),
            E2(sp = listOf(gl(PC)), rate = 2f, s = floatArrayOf(.1f, .2f)),
        ),
        hit = listOf(.35f to hitFn { hp: HPos, s: Float ->
            burst(hp.x, hp.y - s * .02f, 12, listOf("i:h1", "i:heart", gl(PC)),
                floatArrayOf(250f, 600f), floatArrayOf(.06f, .11f), floatArrayOf(1f, 1.5f))
        }),
    ),
    // 3 — love 🥰
    Spec2(
        k = "love", col = "255,120,170",
        h = { t ->
            Pose2().apply {
                s = 1f + .04f * sin(t * 5f); dy = -6f * d * sin(t * 3f); r = 4f * sin(t * 2.5f)
            }
        },
        bg = { t, env, hp -> vig("255,90,150", .05f + .05f * env); orbit(t, hp.x, hp.y, false) },
        fg = { t, x, y, _, _ -> orbit(t, x, y, true) },
        em = listOf(
            E2(o = "hero", sp = listOf(gl(PC), "i:h1"), rate = 3f, s = floatArrayOf(.07f, .14f),
                vy = floatArrayOf(-70f, -150f), life = floatArrayOf(1.6f, 2.4f)),
            E2(k = "st", o = "any", rate = 7f, life = floatArrayOf(.6f, .9f),
                s = floatArrayOf(.02f, .045f), vx = floatArrayOf(0f, 0f), vy = floatArrayOf(0f, 0f)),
        ),
        hit = listOf(1f, 2.2f).map { b ->
            val f: HitFn = { hp, _ ->
                burst(hp.x, hp.y, 10, listOf("i:h1", "i:h3", gl(PC)),
                    floatArrayOf(200f, 450f), floatArrayOf(.06f, .11f), floatArrayOf(1f, 1.5f))
            }
            b to f
        },
    ),
    // 4 — hands 🫶
    Spec2(
        k = "hands", col = "255,160,120", py = .32f,
        h = { t -> Pose2().apply { s = 1f + .05f * sin(t * 4f); dy = -4f * d * sin(t * 3f) } },
        bg = { t, env, hp -> vig("255,140,100", .04f + .05f * env); hform(t, hp.x, hp.y) },
        em = listOf(
            E2(sp = listOf(gl(GD), gl("#ff5a9a")), rate = 2.5f, s = floatArrayOf(.07f, .14f)),
            E2(k = "st", o = "any", rate = 8f, life = floatArrayOf(.6f, .9f),
                s = floatArrayOf(.02f, .045f), vx = floatArrayOf(0f, 0f), vy = floatArrayOf(0f, 0f)),
        ),
    ),
    // 5 — lips2 👄
    Spec2(
        k = "lips2", col = "220,30,70",
        h = { t -> Pose2().apply { s = 1f + .05f * sin(t * 3f); r = -4f * sin(t * 2f) } },
        bg = { t, env, _ -> vig("190,15,55", .1f + .08f * sin(t * 2.5f) * env) },
        fg = { t, x, y, s, o ->
            for (tt in floatArrayOf(.8f, 1.9f, 3f)) {
                if (t >= tt && t < tt + .7f) shine("lips2", x, y, s, o, (t - tt) / .7f)
            }
        },
        em = listOf(
            E2(sp = listOf(gl(RD)), rate = 2f, s = floatArrayOf(.08f, .16f), vy = floatArrayOf(-60f, -140f)),
            E2(k = "fl", o = "bot", col = "220,30,70", rate = 6f, s = floatArrayOf(.15f, .3f),
                vy = floatArrayOf(-40f, -90f), life = floatArrayOf(1.6f, 2.4f), shr = true),
            E2(k = "st", o = "any", rate = 5f, life = floatArrayOf(.6f, .9f),
                s = floatArrayOf(.02f, .045f), vx = floatArrayOf(0f, 0f), vy = floatArrayOf(0f, 0f)),
        ),
    ),
    // 6 — smirk 😏
    Spec2(
        k = "smirk", col = "165,107,255",
        h = { t ->
            Pose2().apply {
                dx = w * .4f * (1f - ease2(min(t / .7f, 1f)))
                r = -10f * ease2(min(t / .9f, 1f)) + (if (t > 1f) 3f * sin(t * 2f) else 0f)
            }
        },
        bg = { t, env, _ ->
            vig("140,60,255", .05f * env)
            for (j in 0 until 3) {
                val path = APath()
                var x = 0f
                while (x <= W) {
                    val y = H * (.52f + .07f * j) + sin(x / W * 6f + t * (1.5f + .4f * j) + j) * H * .03f
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += 16f * D
                }
                c.drawPath(path, neonPaint(if (j % 2 == 1) "#ff5a9a" else "#a56bff", 3f * D, 16f, .7f * env))
            }
        },
        fg = { t, x, y, s, o ->
            for (tt in floatArrayOf(1.2f, 2.6f)) {
                if (t >= tt && t < tt + .7f) shine("smirk", x, y, s, o, (t - tt) / .7f)
            }
        },
        em = listOf(
            E2(sp = listOf(gl(VT), gl(PC)), rate = 1.8f, s = floatArrayOf(.08f, .15f)),
            E2(k = "st", o = "any", rate = 6f, life = floatArrayOf(.6f, .9f),
                s = floatArrayOf(.02f, .045f), vx = floatArrayOf(0f, 0f), vy = floatArrayOf(0f, 0f)),
        ),
        hit = listOf(1.5f to hitFn { hp: HPos, s: Float ->
            pop(gl(PC), hp.x + s * .3f, hp.y - s * .25f, w * .1f, 1.2f)
        }),
    ),
    // 7 — cool 😎
    Spec2(
        k = "cool", col = "77,210,255", np = true,
        h = { t ->
            Pose2().apply {
                dx = -w * .7f * (1f - back2(min(t / .5f, 1f)))
                r = -8f * (1f - ease2(min(t / .5f, 1f)))
            }
        },
        bg = { t, env, _ ->
            vig("40,190,255", .04f + .18f * max(0f, sin(t * 7.85f)).pow(4) * env)
        },
        em = listOf(
            E2(k = "sl", o = "any", rate = 18f, t1 = 3f, vx = floatArrayOf(-1700f, -2500f),
                vy = floatArrayOf(0f, 0f), s = floatArrayOf(.2f, .45f), life = floatArrayOf(.3f, .5f)),
            E2(sp = listOf(gl(CY)), rate = 1.5f, s = floatArrayOf(.07f, .14f)),
        ),
        hit = listOf(
            .9f to hitFn { hp: HPos, s: Float ->
                parts += P2(k = "st", x = hp.x - s * .13f, y = hp.y - s * .03f, s = w * .2f, max = .6f, top = true)
                burst(hp.x - s * .13f, hp.y - s * .03f, 6, listOf("i:spark"),
                    floatArrayOf(100f, 300f), floatArrayOf(.04f, .07f), floatArrayOf(.5f, .9f))
            },
            2.2f to hitFn { hp: HPos, s: Float ->
                parts += P2(k = "st", x = hp.x + s * .13f, y = hp.y - s * .03f, s = w * .2f, max = .6f, top = true)
                burst(hp.x + s * .13f, hp.y - s * .03f, 6, listOf("i:spark"),
                    floatArrayOf(100f, 300f), floatArrayOf(.04f, .07f), floatArrayOf(.5f, .9f))
            },
        ),
    ),
    // 8 — peach 🍑
    Spec2(
        k = "peach", col = "255,160,120",
        h = { t ->
            val S = hs()
            var dx = 0f; var r = 0f; var s = 1f; var sy = 1f
            for (a in floatArrayOf(.45f, 1.45f, 2.45f)) {
                val dd = t - (a + .35f)
                if (dd >= 0f) {
                    val e = exp(-dd * 7f)
                    dx += S * .2f * e * cos(dd * 20f)
                    r += 10f * e * cos(dd * 20f)
                    sy *= 1f + .18f * exp(-dd * 9f) * cos(dd * 28f)
                    s *= 1f - .06f * exp(-dd * 9f) * cos(dd * 28f)
                }
            }
            Pose2().apply { this.dx = dx; this.r = r; this.s = s; this.sy = sy }
        },
        bg = { _, env, _ -> vig("255,140,110", .05f * env) },
        fg = { t, _, _, s, _ ->
            var px = -1.3f; var py = .2f; var r = 0f
            for (a in floatArrayOf(.45f, 1.45f, 2.45f)) {
                val u = t - a
                if (u < 0f || u >= .9f) continue
                if (u < .3f) {
                    val kk = ease2(u / .3f); px = -1.3f + .15f * kk; py = .2f - .45f * kk; r = -30f * kk
                } else if (u < .4f) {
                    val kk = ((u - .3f) / .1f).pow(2); px = -1.15f + .81f * kk; py = -.25f + .25f * kk; r = -30f + 38f * kk
                } else if (u < .5f) {
                    px = -.34f; py = 0f; r = 8f
                } else {
                    val kk = ease2((u - .5f) / .4f); px = -.34f - .96f * kk; py = .2f * kk; r = 8f - 8f * kk
                }
            }
            val al = clamp2((t - .2f) / .25f, 0f, 1f) * clamp2((3.5f - t) / .3f, 0f, 1f)
            spr(fx.sprites.bmp("hand"), W / 2f + px * s, H * .3f + py * s, W * .38f, r, al)
        },
        em = listOf(
            E2(sp = listOf(gl("#ff9a7a"), gl(PC)), rate = 2.5f, s = floatArrayOf(.08f, .16f)),
            E2(k = "st", o = "any", rate = 6f, life = floatArrayOf(.6f, .9f),
                s = floatArrayOf(.02f, .045f), vx = floatArrayOf(0f, 0f), vy = floatArrayOf(0f, 0f)),
        ),
        hit = listOf(.45f, 1.45f, 2.45f).mapIndexed { i, a ->
            val f: HitFn = { _, s ->
                val cx = w / 2f - s * .28f; val cy = h * .3f
                burst(cx, cy, 10, listOf(gl("#ff7aa8"), "i:h1", "i:h3", gl("#ff9a7a")),
                    floatArrayOf(200f, 480f), floatArrayOf(.05f, .1f), floatArrayOf(.9f, 1.4f))
                parts += P2(k = "ip", x = cx, y = cy, sHero = s, max = .35f, top = true)
                pop("gy:" + listOf("smack!", "pat!", "oof~")[i] + ":#ff9ee0",
                    cx - s * .1f, cy - s * .65f, w * .24f, .9f,
                    rO = rnd(-12f, 12f), vyO = -60f * d)
                hring(w / 2f, cy, s * .45f, s * 1.15f, gl("#ff9a7a"), .8f)
                shake(4f, .2f)
            }
            (a + .35f) to f
        },
    ),
    // 9 — fire 🔥
    Spec2(
        k = "fire", col = "255,120,30",
        h = { t ->
            Pose2().apply {
                s = 1f + .05f * sin(t * 23f) + .04f * sin(t * 31f)
                sy = 1f + .05f * sin(t * 19f)
            }
        },
        bg = { _, env, _ ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(
                0f, H, 0f, H * .45f,
                intArrayOf(argbOf("255,110,20", .3f * env), argbOf("255,110,20", 0f)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, W, H, p)
        },
        em = listOf(
            E2(k = "fl", o = "bot", cols = listOf("255,140,30", "255,200,60", "255,80,20"),
                rate = 55f, s = floatArrayOf(.08f, .2f), vx = floatArrayOf(-60f, 60f),
                vy = floatArrayOf(-350f, -700f), life = floatArrayOf(.7f, 1.2f), shr = true, add = true),
            E2(k = "st", o = "bot", col = "#ffb43a", rate = 20f, s = floatArrayOf(.01f, .025f),
                vx = floatArrayOf(-80f, 80f), vy = floatArrayOf(-300f, -620f), life = floatArrayOf(.8f, 1.4f)),
        ),
    ),
    // 10 — peek 🫣
    Spec2(
        k = "peek", col = "255,100,140",
        h = { t ->
            val S = hs()
            var dx = 0f; var r = 0f; var s = 1f
            if (t >= .4f && t < 1.4f) {
                dx = S * .04f * sin((t - .4f) * 4.5f); r = 5f * sin((t - .4f) * 4.5f)
            } else if (t >= 1.4f && t < 2f) {
                val u = ease2((t - 1.4f) / .6f); dx = -S * .3f * u; r = -14f * u; s = 1f - .2f * u
            } else if (t >= 2f && t < 2.6f) {
                val u = ease2((t - 2f) / .6f); dx = -S * .3f * (1f - u); r = -14f * (1f - u); s = .8f + .3f * u
            } else if (t >= 2.6f) {
                s = 1.1f + .03f * sin((t - 2.6f) * 8f); r = 4f * sin((t - 2.6f) * 5f)
            }
            Pose2().apply { this.dx = dx; this.r = r; this.s = s }
        },
        bg = { t, env, _ ->
            vig("255,70,120", .04f + (if (t > 1.4f && t < 2.6f) .12f else .05f) *
                max(0f, sin(t * 14f)).pow(2) * env)
        },
        fg = { t, x, y, s, _ ->
            val cu = if (t >= 1.4f && t < 2.6f) 1 else 0
            val bi = .22f + .4f * clamp2(t / 1.4f, 0f, 1f) + .25f * cu
            val hl = clamp2((t - .5f) / .3f, 0f, 1f)
            val bb = max(0f, sin(t * (if (cu == 1) 20f else 14f)))
            for (sd in floatArrayOf(-1f, 1f)) glow(x + sd * s * .3f, y + s * .12f, s * .34f, "255,90,130", bi)
            val p1 = neonPaint("#ff3d7f", 3.5f * D, 8f, hl * (.6f + .4f * sin(t * 12f)))
            for (sd in floatArrayOf(-1f, 1f)) for (j in 0 until 3) {
                val x0 = x + sd * s * .62f + (j - 1) * W * .022f * sd
                c.drawLine(x0, y - s * .1f, x0 + W * .014f, y - s * .1f + W * .05f, p1)
            }
            spr(fx.sprites.glass(RS), x + s * .62f, y - s * .58f, s * .34f * (1f + .4f * bb), 0f, clamp2(t / .5f, 0f, 1f))
            val p2 = neonPaint("#ffd0e4", 3f * D, 0f, bb)
            for (i in -1..1) {
                val q = -.8f + i * .5f
                c.drawLine(
                    x + s * .62f + cos(q) * s * .2f, y - s * .58f + sin(q) * s * .2f,
                    x + s * .62f + cos(q) * s * .3f, y - s * .58f + sin(q) * s * .3f, p2,
                )
            }
        },
        em = listOf(
            E2(o = "hTop", sp = listOf(gl(RS)), rate = 3f, t0 = .6f, s = floatArrayOf(.05f, .09f),
                vx = floatArrayOf(-30f, 30f), vy = floatArrayOf(-100f, -200f),
                life = floatArrayOf(1.6f, 2f), grow = true),
            E2(k = "st", o = "any", rate = 6f, life = floatArrayOf(.6f, .9f),
                s = floatArrayOf(.02f, .045f), vx = floatArrayOf(0f, 0f), vy = floatArrayOf(0f, 0f)),
        ),
    ),
    // 11 — giggle 🤭
    Spec2(
        k = "giggle", col = "230,140,255", np = true,
        h = { t ->
            val pp = (t - .4f) / .5f
            var dy = 0f; var sy = 1f; var r = 0f
            if (pp >= 0f && pp < 6f) {
                val ph = pp % 1f
                dy = -hs() * .2f * sin(ph * PI.toFloat()) * exp(-ph * .4f)
                sy = 1f - .12f * exp(-ph * 10f)
                r = 8f * sin(ph * T2 * 2f) * (1f - ph)
            }
            Pose2().apply { this.dy = dy; this.sy = sy; this.r = r; s = back2(min(t / .4f, 1f)) }
        },
        em = listOf(
            E2(k = "st", o = "any", rate = 8f, life = floatArrayOf(.6f, .9f),
                s = floatArrayOf(.02f, .045f), vx = floatArrayOf(0f, 0f), vy = floatArrayOf(0f, 0f)),
            E2(sp = listOf(gl("#ff6ec7")), rate = 1.5f, s = floatArrayOf(.07f, .13f)),
        ),
        hit = (0 until 6).map { i ->
            val f: HitFn = { hp, s ->
                parts += P2(k = "tk", x = hp.x, y = hp.y, sHero = hs(), max = .5f)
                burst(hp.x, hp.y - s * .4f, 3, listOf(gl("#ff6ec7"), "i:h1", "i:spark"),
                    floatArrayOf(200f, 420f), floatArrayOf(.05f, .09f), floatArrayOf(1f, 1.4f))
                pop("gy:" + pick(listOf("hehe", "hihi")) + ":#ff9ee0",
                    hp.x + rnd(-.35f, .35f) * s, hp.y - s * .7f, w * rnd(.2f, .3f), 1f,
                    rO = rnd(-15f, 15f), vyO = -rnd(40f, 80f) * d)
            }
            (.4f + i * .5f) to f
        },
    ),

    // 12 — eyes 👀
    Spec2(
        k = "eyes", col = "120,200,255", py = .26f,
        h = { t ->
            Pose2().apply {
                dx = (beamF(t) - w / 2f) * .25f
                sy = 1f - blinkF(t) * .9f
                s = if (t > 2.4f) 1f + .2f * min(1f, (t - 2.4f) * 5f) else 1f
            }
        },
        bg = { t, env, _ ->
            val bx = fx.beamF(t); val fy = H * .45f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(
                0f, 0f, 0f, fy,
                intArrayOf(
                    argbOf("190,230,255", 0f), argbOf("190,230,255", .14f * env),
                    argbOf("190,230,255", .24f * env),
                ),
                floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP,
            )
            val ax = W / 2f + (bx - W / 2f) * .3f
            val path = APath().apply {
                moveTo(ax - W * .03f, 0f); lineTo(ax + W * .03f, 0f)
                lineTo(bx + W * .28f, fy); lineTo(bx - W * .28f, fy); close()
            }
            c.drawPath(path, p)
            glow(bx, fy, W * .38f, "170,220,255", .4f * env, .3f)
            if (t > 2.4f) {
                val f = exp(-(t - 2.4f) * 2.2f)
                val p2 = Paint(Paint.ANTI_ALIAS_FLAG)
                p2.shader = LinearGradient(
                    0f, 0f, W, 0f,
                    intArrayOf(argbOf("#ffffff", 0f), argbOf("#ffffff", f), argbOf("#ffffff", 0f)),
                    floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP,
                )
                c.drawRect(0f, fy - 3f * D, W, fy + 3f * D, p2)
                star(W / 2f, fy, W * .28f * exp(-(t - 2.4f) * 1.4f), 1f, null)
            }
        },
        fg = { t, _, _, _, _ ->
            if (t >= 2.4f) {
                val fy = H * .45f
                val u = min(1f, (t - 2.4f) / .45f)
                val hs2 = W * (.26f - .14f * ease2(u)); val cl = hs2 * .45f
                val p = neonPaint("#8fe0ff", 4f * D, 16f, 1f)
                for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                    val path = APath().apply {
                        moveTo(W / 2f + sx * hs2, fy + sy * (hs2 - cl))
                        lineTo(W / 2f + sx * hs2, fy + sy * hs2)
                        lineTo(W / 2f + sx * (hs2 - cl), fy + sy * hs2)
                    }
                    c.drawPath(path, p)
                }
                spr(fx.sprites.glyph("!", "#6fd0ff"), W / 2f, fy, W * .2f * back2(u), 0f, 1f)
            }
        },
        em = listOf(
            E2(o = "low", sp = listOf("gy:?:#6fd0ff"), rate = 3f, t1 = 2.4f,
                vx = floatArrayOf(-8f, 8f), vy = floatArrayOf(-15f, -40f),
                s = floatArrayOf(.08f, .16f), life = floatArrayOf(2.2f, 3f), lit = true),
            E2(k = "mt", o = "any", rate = 5f, t1 = 2.4f,
                vx = floatArrayOf(-8f, 8f), vy = floatArrayOf(-5f, -20f),
                s = floatArrayOf(.0035f, .009f), life = floatArrayOf(1.5f, 2.8f)),
        ),
        hit = listOf(2.4f to hitFn { _: HPos, _: Float ->
            for (p in parts) if (p.lit) p.max = min(p.max, p.age + .4f)
            repeat(7) {
                parts += P2(k = "st", x = w / 2f + rnd(-.35f, .35f) * w,
                    y = h * .45f + rnd(-.1f, .1f) * h, s = rnd(14f, 30f) * d, max = .9f)
            }
        }),
    ),
    // 13 — beg 🥺
    Spec2(
        k = "beg", col = "200,220,255",
        h = { t ->
            Pose2().apply {
                s = 1f + .1f * ease2(min(t / 3f, 1f))
                r = 5f * sin(t * 2.2f); dy = -hs() * .02f * sin(t * 3f)
            }
        },
        bg = { _, env, _ -> vig("120,160,255", .05f * env) },
        em = listOf(
            E2(k = "st", o = "halo", rate = 16f, s = floatArrayOf(.015f, .04f),
                vx = floatArrayOf(0f, 0f), vy = floatArrayOf(0f, 0f), life = floatArrayOf(.5f, .9f)),
            E2(sp = listOf(gl("#8fb8ff")), rate = 1.4f, s = floatArrayOf(.07f, .13f),
                vy = floatArrayOf(-60f, -120f)),
        ),
        hit = listOf(1.5f, 2.4f).mapIndexed { i, tt ->
            val f: HitFn = { hp, s ->
                parts += P2(
                    k = "gh", sp = sprites.dropBmp,
                    x = hp.x + (if (i == 1) 1f else -1f) * s * .2f, y = hp.y + s * .1f,
                    vx = 0f, vy = 50f * d, g = 420f * d, s = w * .05f, max = 2f,
                    r = 0f, vr = 0f, ph = 0f, sw = 0f, fo = .4f,
                )
            }
            tt to f
        },
    ),
    // 14 — cry 😭
    Spec2(
        k = "cry", col = "80,150,255",
        sh = { 0f },
        h = { t ->
            val sob = max(0f, sin(t * 7.85f)).pow(3)
            val e = min(1f, t * 3f)
            Pose2().apply {
                dx = 3f * d * sin(t * 40f) * sob
                dy = 2f * d * sin(t * 37f) * sob
                s = 1f + .05f * sob; r = 2f * sin(t * 11f) * e
            }
        },
        water = true,
        bg = { t, env, hp ->
            val S = fx.hs()
            val sob = max(0f, sin(t * 7.85f)).pow(3)
            c.save()
            c.translate(3f * D * sin(t * 40f) * sob, 2f * D * sin(t * 37f) * sob)
            for (m in floatArrayOf(-1f, 1f)) {
                val x0 = hp.x + m * S * .22f; val y0 = hp.y + S * .14f
                val path = APath()
                var u = 0f
                while (u <= 1f) {
                    val x = x0 + m * (W * .05f * u + sin(u * 6f + t * 4f) * W * .012f)
                    val y = y0 + u * H * .45f
                    if (u == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    u += .04f
                }
                val p = neonPaint("#7fc4ff", (5f + 3f * sob) * D, 18f, 1f)
                p.pathEffect = DashPathEffect(floatArrayOf(W * .07f, W * .04f), -t * 420f * D)
                c.drawPath(path, p)
            }
            c.restore()
            val lv = env * H * .05f
            fun wl(x: Float) = H - lv + sin(x / W * 9f + t * 3f) * 8f * D
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            fill.style = Paint.Style.FILL; fill.color = argbOf("60,120,255", .12f * env)
            val wpath = APath(); wpath.moveTo(0f, H)
            var x = 0f
            while (x <= W + 20f * D) { wpath.lineTo(x, wl(x)); x += 20f * D }
            wpath.lineTo(W, H); wpath.close()
            c.drawPath(wpath, fill)
            val lpath = APath()
            x = 0f
            while (x <= W + 20f * D) {
                if (x == 0f) lpath.moveTo(x, wl(x)) else lpath.lineTo(x, wl(x))
                x += 20f * D
            }
            c.drawPath(lpath, neonPaint("#8fd0ff", 3f * D, 18f, env))
        },
        em = listOf(
            E2(o = "eyes", mir = true, sp = listOf("dr"), rate = 20f,
                vx = floatArrayOf(10f, 50f), vy = floatArrayOf(80f, 200f), g = 1300f,
                s = floatArrayOf(.03f, .055f), life = floatArrayOf(2f, 3f), spl = true,
                r = floatArrayOf(0f, 0f), vr = floatArrayOf(0f, 0f)),
            E2(o = "top", sp = listOf("bd"), rate = 2f,
                vx = floatArrayOf(0f, 0f), vy = floatArrayOf(90f, 170f),
                s = floatArrayOf(.2f, .34f), life = floatArrayOf(3f, 4f), al = .5f,
                r = floatArrayOf(0f, 0f), vr = floatArrayOf(0f, 0f)),
        ),
    ),
    // 15 — broken 💔
    Spec2(
        k = "broken", col = "160,120,255",
        h = { t ->
            Pose2().apply {
                dy = if (t > 1f) hs() * .05f * ease2(clamp2(t - 1f, 0f, 1f)) else 0f
                r = if (t > 1f) -3f * ease2(clamp2(t - 1f, 0f, 1f)) else 0f
            }
        },
        bg = { t, env, hp ->
            val x = hp.x; val y = hp.y; val sz = W * .58f
            vig("90,60,200", .07f * env)
            if (t < 1f) {
                spr(fx.sprites.glass(PC), x, y, sz * back2(clamp2(t / .4f, 0f, 1f)), 0f, .9f)
                if (t > .5f) {
                    val pr = clamp2((t - .5f) / .45f, 0f, 1f)
                    val pts = arrayOf(
                        floatArrayOf(0f, -.5f), floatArrayOf(.06f, -.3f), floatArrayOf(-.05f, -.12f),
                        floatArrayOf(.07f, .08f), floatArrayOf(-.04f, .25f), floatArrayOf(0f, .45f),
                    )
                    val path = APath()
                    for (i in pts.indices) {
                        if (i.toFloat() / (pts.size - 1) > pr) break
                        if (i == 0) path.moveTo(x + pts[i][0] * sz, y + pts[i][1] * sz)
                        else path.lineTo(x + pts[i][0] * sz, y + pts[i][1] * sz)
                    }
                    c.drawPath(path, neonPaint("#ffffff", 4f * D, 14f, 1f))
                }
            } else {
                val u = t - 1f; val al = clamp2(1f - u / 2.4f, 0f, 1f)
                for (sd in floatArrayOf(-1f, 1f)) {
                    c.save()
                    val rr = RectF(if (sd < 0f) 0f else x, 0f, if (sd < 0f) x else W, H)
                    c.clipRect(rr)
                    c.translate(sd * u * W * .14f, u * u * H * .12f + u * 20f * D)
                    c.translate(x, y); c.rotate(sd * u * .25f * 180f / PI.toFloat()); c.translate(-x, -y)
                    spr(fx.sprites.glass(PC), x, y, sz, 0f, .9f * al)
                    c.restore()
                }
            }
        },
        em = listOf(
            E2(o = "eyes", mir = true, sp = listOf("dr"), rate = 4f, t0 = 1f,
                vx = floatArrayOf(10f, 40f), vy = floatArrayOf(60f, 140f), g = 900f,
                s = floatArrayOf(.025f, .045f), life = floatArrayOf(2f, 2.6f),
                r = floatArrayOf(0f, 0f), vr = floatArrayOf(0f, 0f)),
        ),
        hit = listOf(1f to hitFn { hp: HPos, _: Float ->
            shake(8f, .4f)
            burst(hp.x, hp.y, 16, listOf("sh:$PC", "sh:#8fb8ff", gl(RS)),
                floatArrayOf(200f, 600f), floatArrayOf(.04f, .1f), floatArrayOf(1.4f, 2.2f), gOpt = 900f)
        }),
    ),
    // 16 — rage 🤬
    Spec2(
        k = "rage", col = "255,40,40",
        h = { t ->
            val S = hs()
            Pose2().apply {
                dx = S * .025f * sin(t * 50f); dy = S * .02f * sin(t * 43f)
                s = 1f + .2f * min(1f, t / 2f); r = 3f * sin(t * 37f)
            }
        },
        sh = { 5f },
        bg = { t, env, _ -> vig("255,30,30", .14f + .12f * sin(t * 14f) * env) },
        em = listOf(
            E2(k = "fl", o = "hs", col = "255,150,130", rate = 14f, s = floatArrayOf(.1f, .2f),
                vx = floatArrayOf(-40f, 40f), vy = floatArrayOf(-120f, -240f),
                life = floatArrayOf(.8f, 1.3f), shr = true),
            E2(o = "hero", sp = listOf("i:anger"), rate = 2.4f, t0 = .2f, s = floatArrayOf(.1f, .16f),
                vx = floatArrayOf(-80f, 80f), vy = floatArrayOf(-60f, -120f),
                life = floatArrayOf(.6f, .9f), grow = true, r = floatArrayOf(-30f, 30f)),
            E2(k = "st", o = "hero", col = "#ff8a3a", rate = 14f, s = floatArrayOf(.012f, .03f),
                vx = floatArrayOf(-300f, 300f), vy = floatArrayOf(-300f, 100f), life = floatArrayOf(.4f, .8f)),
        ),
        hit = listOf(
            0f to hitFn { _: HPos, _: Float -> flash("255,40,40", .45f, .2f) },
            .2f to hitFn { hp: HPos, s: Float ->
                crack(hp.x, hp.y, "#ff4a4a"); crack(hp.x, hp.y, "#ff4a4a")
                ringP(hp.x, hp.y, s * .4f, s * 1.3f, "255,60,60", .7f)
            },
            1.4f to hitFn { hp: HPos, s: Float ->
                crack(hp.x, hp.y, "#ff4a4a")
                ringP(hp.x, hp.y, s * .4f, s * 1.3f, "255,60,60", .7f)
                burst(hp.x, hp.y, 8, listOf("i:boom"),
                    floatArrayOf(150f, 350f), floatArrayOf(.08f, .14f), floatArrayOf(.6f, .9f))
            },
            2.6f to hitFn { hp: HPos, s: Float ->
                crack(hp.x, hp.y, "#ff4a4a")
                ringP(hp.x, hp.y, s * .4f, s * 1.3f, "255,60,60", .7f)
            },
        ),
    ),
    // 17 — mid 🖕
    Spec2(
        k = "mid", col = "255,90,140", np = true, py = .34f,
        h = { t ->
            val S = hs()
            val dd = if (t < .5f) 0f
                else if (t < 1.1f) ease2((t - .5f) / .6f) * .6f
                else .6f + .4f * (.5f + .5f * sin((t - 1.1f) * 7f))
            Pose2().apply { dy = (1f - dd) * S * .95f; s = .82f * clamp2(t / .5f, 0f, 1f) }
        },
        bg = { t, env, _ ->
            val dd = if (t < 1.1f) 0f else .5f + .5f * sin((t - 1.1f) * 7f)
            vig("255,60,120", .05f + .08f * dd * env)
        },
        fg = { t, _, _, _, _ ->
            val cx = W / 2f; val cy = H * .34f
            val hg = W * .2f; val rr2 = W * .27f
            val pr = clamp2(t / .5f, 0f, 1f)
            val dd = if (t < 1.1f) 0f else .5f + .5f * sin((t - 1.1f) * 7f)
            glow(cx, cy, W * .4f, "255,70,130", .1f + .2f * dd)
            for (sd in floatArrayOf(-1f, 1f)) {
                val c0 = cx + sd * (hg - rr2)
                val a0 = if (sd < 0f) PI.toFloat() - .95f * pr else -.95f * pr
                val a1 = if (sd < 0f) PI.toFloat() + .95f * pr else .95f * pr
                val arc = RectF(c0 - rr2, cy - rr2, c0 + rr2, cy + rr2)
                c.drawArc(arc, a0 * 180f / PI.toFloat(), (a1 - a0) * 180f / PI.toFloat(), false,
                    neonPaint("#ff5a9a", 9f * D, 22f, 1f))
                c.drawArc(arc, a0 * 180f / PI.toFloat(), (a1 - a0) * 180f / PI.toFloat(), false,
                    neonPaint("#ffffff", 3f * D, 6f, .9f))
            }
        },
        em = listOf(
            E2(o = "hero", sp = listOf(gl(PC), "i:h1"), rate = 3f, t0 = 1f, s = floatArrayOf(.06f, .12f),
                vy = floatArrayOf(-70f, -150f), life = floatArrayOf(1.4f, 2f)),
            E2(k = "st", o = "any", rate = 7f, t0 = .6f, life = floatArrayOf(.6f, .9f),
                s = floatArrayOf(.02f, .045f), vx = floatArrayOf(0f, 0f), vy = floatArrayOf(0f, 0f)),
        ),
        hit = listOf(1.32f, 2.22f, 3.12f).mapIndexed { i, tt ->
            val f: HitFn = { _, s ->
                val x = w / 2f; val y = h * .34f
                burst(x, y, 8, listOf(gl(PC), "i:h1", "i:h3", "i:spark"),
                    floatArrayOf(200f, 450f), floatArrayOf(.05f, .1f), floatArrayOf(.9f, 1.3f))
                hring(x, y, s * .35f, s * 1.05f, gl(PC), .8f)
                pop("gy:" + listOf("oh~", "mm~", "ahh~")[i] + ":#ff9ee0",
                    x + rnd(-.2f, .2f) * w, y - w * .34f, w * .22f, .9f,
                    rO = rnd(-12f, 12f), vyO = -60f * d)
            }
            tt to f
        },
    ),
    // 18 — lol 🤣
    Spec2(
        k = "lol", col = "255,200,60",
        h = { t ->
            Pose2().apply {
                r = 22f * sin(t * 28f) * min(1f, t * 4f)
                dy = -10f * d * abs(sin(t * 14f))
                s = 1f + .05f * sin(t * 14f)
            }
        },
        sh = { 3f },
        em = listOf(
            E2(o = "eyes", mir = true, sp = listOf("dr"), rate = 30f, t1 = 3.1f,
                vx = floatArrayOf(160f, 420f), vy = floatArrayOf(-300f, -620f), g = 1500f,
                s = floatArrayOf(.03f, .06f), life = floatArrayOf(1f, 1.6f),
                r = floatArrayOf(0f, 0f), vr = floatArrayOf(0f, 0f)),
            E2(o = "hero", sp = listOf("gy:HA:#ffd23a", "gy:HAHA:#ffb43a"), rate = 3f, t0 = .3f,
                s = floatArrayOf(.14f, .22f), vx = floatArrayOf(-60f, 60f), vy = floatArrayOf(-120f, -200f),
                life = floatArrayOf(.9f, 1.3f), grow = true, r = floatArrayOf(-25f, 25f)),
            E2(k = "st", o = "any", rate = 8f, col = "#ffe27a", life = floatArrayOf(.6f, .9f),
                s = floatArrayOf(.02f, .045f), vx = floatArrayOf(0f, 0f), vy = floatArrayOf(0f, 0f)),
        ),
    ),
    // 19 — sleep 😴
    Spec2(
        k = "sleep", col = "120,150,255",
        h = { t ->
            Pose2().apply {
                r = 6f * sin(t * 1.4f); s = 1f + .04f * sin(t * 2f); dy = -5f * d * sin(t * 1.4f)
            }
        },
        bg = { t, env, _ -> vig("30,50,160", .12f + .06f * sin(t * 1.5f) * env) },
        em = listOf(
            E2(o = "mouthR", sp = listOf("gy:Z:#9fb4ff", "gy:z:#7f98ff"), rate = 1.6f,
                vx = floatArrayOf(30f, 70f), vy = floatArrayOf(-70f, -130f),
                s = floatArrayOf(.08f, .18f), life = floatArrayOf(2f, 2.8f),
                grow = true, sw = .03f, r = floatArrayOf(-15f, 15f)),
            E2(k = "st", o = "any", col = "#bcd0ff", rate = 6f, life = floatArrayOf(1f, 1.6f),
                s = floatArrayOf(.012f, .03f), vx = floatArrayOf(0f, 0f), vy = floatArrayOf(0f, 0f)),
        ),
    ),
    // 20 — roll 🙄
    Spec2(
        k = "roll", col = "160,176,200",
        h = { t ->
            var r = 0f; var s = 1f; var dy = 0f
            if (t >= .6f && t < 1.7f) r = 360f * ease2((t - .6f) / 1.1f)
            if (t >= 1.7f) {
                val u = clamp2((t - 1.7f) / .6f, 0f, 1f)
                s = 1f - .1f * sin(u * PI.toFloat()); dy = hs() * .04f * sin(u * PI.toFloat())
            }
            Pose2().apply { this.r = r; this.s = s; this.dy = dy }
        },
        bg = { t, env, hp ->
            vig("90,110,140", .1f * env)
            if (t >= .6f && t < 1.8f) {
                val u = ease2(clamp2((t - .6f) / 1.1f, 0f, 1f))
                val arc = RectF(hp.x - fx.hs() * .62f, hp.y - fx.hs() * .62f, hp.x + fx.hs() * .62f, hp.y + fx.hs() * .62f)
                val p = neonPaint("#9fb0c8", 4f * D, 12f, 1f)
                p.pathEffect = DashPathEffect(floatArrayOf(W * .04f, W * .03f), 0f)
                c.drawArc(arc, (u * T2 * 1.5f) * 180f / PI.toFloat(), (T2 * .6f) * 180f / PI.toFloat(), false, p)
            }
        },
        em = listOf(
            E2(k = "mt", o = "any", rate = 6f, vx = floatArrayOf(-8f, 8f), vy = floatArrayOf(-5f, -20f),
                s = floatArrayOf(.0035f, .009f), life = floatArrayOf(1.5f, 2.8f)),
        ),
        hit = listOf(1.75f to hitFn { hp: HPos, s: Float ->
            puff(hp.x, hp.y + s * .25f, "180,195,215", 8)
            pop("gy:...:#9fb0c8", hp.x, hp.y - s * .6f, w * .26f, 1.4f)
        }),
    ),
)

// ---------- the overlay (the demo's draw loop as a chat overlay) ----
/**
 * ROUND 66 — one demo show, played transparently over the chat.
 * Production playback ([stageT] == null): the demo's rAF loop with
 * step(dt * 4/3) — 4 spec-seconds in 3.0 real seconds — ending the
 * moment the engine ends the show (t reaches DUR, particles cleared
 * by the engine itself), with a 4.5 s hard cap. The canvas opacity
 * fade over the last .5 spec-seconds mirrors the demo's
 * cv.style.opacity ramp. [stageT] is a TEST-ONLY fixed-clock staging
 * hook (Roborazzi): it seeds the RNG and steps the engine in fixed
 * 1/60 spec-second increments to exactly [stageT], then holds that
 * one frame; production callers never pass it.
 */
@Composable
fun EmojiFx2Overlay(specIdx: Int, stageT: Float? = null, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val sprites = remember(ctx) { Fx2Sprites(ctx) }
    val fx = remember { Fx2() }
    var tick by remember { mutableLongStateOf(0L) }
    var dispT by remember { mutableFloatStateOf(0f) }
    var staged by remember { mutableStateOf(false) }

    LaunchedEffect(specIdx, stageT) {
        if (stageT != null) return@LaunchedEffect
        fx.d = min(density, 2f)
        fx.trig(specIdx, sprites)
        var last = 0L
        var start = 0L
        var finished = false
        while (!finished) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                val dt = if (last == 0L) .016f else ((now - last) / 1e9f).coerceAtMost(.05f)
                last = now
                fx.step(dt * 4f / 3f)
                dispT = fx.t; tick = now
                if (fx.kind < 0 || (now - start) / 1e9f > 4.5f) finished = true
            }
        }
        onDone()
    }

    val fade = if (stageT == null && dispT > DUR2 - .5f) {
        max(0f, (DUR2 - dispT) / .5f)
    } else {
        1f
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade }) {
        Canvas(Modifier.fillMaxSize()) {
            if (stageT != null) {
                fx.w = size.width; fx.h = size.height; fx.d = min(density, 2f)
                if (!staged) {
                    fx.trig(specIdx, sprites)
                    fx.rng = Random(66)
                    var acc = 0f
                    while (acc < stageT) { fx.step(1f / 60f); acc += 1f / 60f }
                    staged = true
                }
            } else {
                tick
                fx.w = size.width; fx.h = size.height; fx.d = min(density, 2f)
            }
            Dr(fx, drawContext.canvas.nativeCanvas).drawAll()
        }
    }
}
