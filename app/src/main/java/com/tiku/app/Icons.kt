package com.tiku.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Study-journal motif for the re-skinned Welcome orb: an open book in strokes. */
@Composable
fun TikuBookIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        val stroke = 1.8f * sx
        fun X(v: Float) = v * sx
        fun Y(v: Float) = v * sy
        // cover: rounded rect x4 y4 w16 h16 rx2
        drawRoundRect(
            color = tint,
            topLeft = Offset(X(4f), Y(4f)), size = Size(X(16f), Y(16f)),
            cornerRadius = CornerRadius(X(2f), Y(2f)),
            style = Stroke(width = stroke),
        )
        // spine
        drawLine(tint, Offset(X(12f), Y(4f) + stroke / 2), Offset(X(12f), Y(20f) - stroke / 2), stroke)
        // page lines
        drawLine(tint, Offset(X(6.8f), Y(8.5f)), Offset(X(10.2f), Y(8.5f)), stroke * 0.8f)
        drawLine(tint, Offset(X(6.8f), Y(11.5f)), Offset(X(10.2f), Y(11.5f)), stroke * 0.8f)
        drawLine(tint, Offset(X(13.8f), Y(8.5f)), Offset(X(17.2f), Y(8.5f)), stroke * 0.8f)
        drawLine(tint, Offset(X(13.8f), Y(11.5f)), Offset(X(17.2f), Y(11.5f)), stroke * 0.8f)
    }
}

/** One four-point sparkle (concave star) as a filled path — the building
 *  block of [TikuSparklesIcon]. (cx, cy, r) in the 24×24 icon grid. */
private fun DrawScope.sparklePath(cx: Float, cy: Float, r: Float, sx: Float, sy: Float): Path {
    fun X(v: Float) = v * sx
    fun Y(v: Float) = v * sy
    return Path().apply {
        moveTo(X(cx), Y(cy - r))
        quadraticBezierTo(X(cx), Y(cy), X(cx + r), Y(cy))
        quadraticBezierTo(X(cx), Y(cy), X(cx), Y(cy + r))
        quadraticBezierTo(X(cx), Y(cy), X(cx - r), Y(cy))
        quadraticBezierTo(X(cx), Y(cy), X(cx), Y(cy - r))
        close()
    }
}

/** ROUND 22 — the message-effects affordance glyph: a cluster of
 *  sparkles, drawn as vector paths (never an emoji — the owner's icon
 *  law). Sits in the composer's left slot whenever text is present,
 *  Instagram-style: tap it to arm a send effect for the next message. */
@Composable
fun TikuSparklesIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        // main sparkle, upper-left
        drawPath(sparklePath(9f, 9.5f, 6.2f, sx, sy), tint)
        // small sparkle, upper-right
        drawPath(sparklePath(18f, 5.5f, 3.1f, sx, sy), tint)
        // small sparkle, lower-right
        drawPath(sparklePath(17f, 16.5f, 2.6f, sx, sy), tint)
        // dot, lower-left
        drawCircle(tint, radius = 1.15f * sx, center = Offset(5.5f * sx, 18f * sy))
    }
}

/** The design's lock glyph, drawn 1:1 (port of the welcome screen's inline SVG). */
@Composable
fun TikuLockIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        val stroke = 1.8f * sx
        fun X(v: Float) = v * sx
        fun Y(v: Float) = v * sy
        // shackle: M8 11 V8 a4 4 0 0 1 8 0 v3
        drawArc(
            color = tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(X(8f), Y(7f)), size = Size(X(8f), Y(8f)),
            style = Stroke(width = stroke),
        )
        drawLine(tint, Offset(X(8f), Y(11f)), Offset(X(8f), Y(8f) + stroke / 2), stroke)
        drawLine(tint, Offset(X(16f), Y(11f)), Offset(X(16f), Y(8f) + stroke / 2), stroke)
        // body: rounded rect x5 y11 w14 h9 rx3
        drawRoundRect(
            color = tint,
            topLeft = Offset(X(5f), Y(11f)), size = Size(X(14f), Y(9f)),
            cornerRadius = CornerRadius(X(3f), Y(3f)),
            style = Stroke(width = stroke),
        )
        // keyhole dot
        drawCircle(tint, radius = X(1.2f), center = Offset(X(12f), Y(15.5f)))
    }
}
