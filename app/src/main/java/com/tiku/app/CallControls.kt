package com.tiku.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ───────────────────────── ICONS ─────────────────────────
 * 24x24 grid, 1.75 stroke, round caps/joins (same as HTML preview).
 * Koi extra library nahi chahiye, sirf compose ui + material3.
 */
object CallIcons {

    private fun icon(
        name: String,
        vararg paths: String,
        filled: Boolean = false,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            val nodes = PathParser().parsePathString(d).toNodes()
            if (filled) {
                addPath(pathData = nodes, fill = SolidColor(Color.Black))
            } else {
                addPath(
                    pathData = nodes,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.75f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
    }.build()

    private const val SLASH = "M3 3L21 21"

    private const val MIC_BODY = "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"
    private const val MIC_ARC = "M19 10v2a7 7 0 0 1-14 0v-2"
    private const val MIC_STAND = "M12 19v3"

    private const val VIDEO_BODY =
        "M4.5 6h9a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 2 15.5v-7A2.5 2.5 0 0 1 4.5 6z"
    private const val VIDEO_LENS = "m16 13 5.2 3.5a.5.5 0 0 0 .8-.4V7.9a.5.5 0 0 0-.8-.4L16 10.5"

    val Mic: ImageVector by lazy { icon("Mic", MIC_BODY, MIC_ARC, MIC_STAND) }
    val MicOff: ImageVector by lazy { icon("MicOff", MIC_BODY, MIC_ARC, MIC_STAND, SLASH) }

    val Video: ImageVector by lazy { icon("Video", VIDEO_BODY, VIDEO_LENS) }
    val VideoOff: ImageVector by lazy { icon("VideoOff", VIDEO_BODY, VIDEO_LENS, SLASH) }

    val Speaker: ImageVector by lazy {
        icon(
            "Speaker",
            "M11 4.7a.7.7 0 0 0-1.2-.5L6.4 7.6A1.4 1.4 0 0 1 5.4 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.4a1.4 1.4 0 0 1 1 .4l3.4 3.4a.7.7 0 0 0 1.2-.5z",
            "M16 9a5 5 0 0 1 0 6",
            "M19.4 18.4a9 9 0 0 0 0-12.8",
        )
    }

    val ScreenShare: ImageVector by lazy {
        icon(
            "ScreenShare",
            "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M8 21h8",
            "M12 17v4",
            "m9 10 3-3 3 3",
            "M12 13V7",
        )
    }

    val FlipCamera: ImageVector by lazy {
        icon(
            "FlipCamera",
            "M11 19H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h5",
            "M13 5h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-5",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
            "m18 22-3-3 3-3",
            "m6 2 3 3-3 3",
        )
    }

    /** Phone handset. UI mein 135° rotate karke hang-up dikhate hain. */
    val Phone: ImageVector by lazy {
        icon(
            "Phone",
            "M13.8 16.6a1 1 0 0 0 1.2-.3l.4-.5A2 2 0 0 1 17 15h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2A18 18 0 0 1 2 4a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-.8 1.6l-.5.4a1 1 0 0 0-.3 1.2 14 14 0 0 0 6.4 6.4",
        )
    }

    val Keypad: ImageVector by lazy {
        val pts = listOf(5, 12, 19)
        val dots = buildList {
            for (y in pts) for (x in pts) {
                add("M${x - 1.6f} ${y}a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0z")
            }
        }
        icon("Keypad", *dots.toTypedArray(), filled = true)
    }
}

/* ───────────────────────── BUTTONS ───────────────────────── */

private val GlassFill = Color.White.copy(alpha = 0.14f)
private val GlassBorder = Color.White.copy(alpha = 0.18f)
private val ActiveFill = Color(0xFFF5F5F7)
private val ActiveIcon = Color(0xFF111111)
private val EndTop = Color(0xFFFF5B6B)
private val EndBottom = Color(0xFFE5283F)

@Composable
fun CallControlButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(stiffness = 600f), label = "scale")
    val bg by animateColorAsState(if (active) ActiveFill else GlassFill, label = "bg")
    val tint by animateColorAsState(if (active) ActiveIcon else Color.White, label = "tint")

    Column(
        modifier = modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(scale)
                .then(if (active) Modifier.shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, if (active) Color.White else GlassBorder, CircleShape)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun EndCallButton(
    modifier: Modifier = Modifier,
    label: String = "End",
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(stiffness = 600f), label = "scale")

    Column(
        modifier = modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(scale)
                .shadow(14.dp, CircleShape, ambientColor = EndBottom, spotColor = EndBottom)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(EndTop, EndBottom)))
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "End call" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CallIcons.Phone,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp).rotate(135f),
            )
        }
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
    }
}
