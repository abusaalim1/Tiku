package com.tiku.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Scenes = listOf(
    Triple("Physics MCQs, fresh every day", "Class 11 • NCERT — daily practice, notes and formulas in one quiet place.", 0),
    Triple("Chemistry important questions", "Chapter-wise key points, written for quick revision.", 1),
    Triple("Notes • Formulas • Diagrams", "Physics • Chemistry • Biology • Maths — your Class 11 companion.", 2),
)
private const val PLAIN_LINE = "Physics • Chapter 4 — key points"
private const val RND_CHARS = "▓▒░#@%&*ø§¤"

@Composable
fun WelcomeScreen(onGoogleClick: () -> Unit) {
    androidx.activity.compose.BackHandler { AppState.go(Screen.Facade) }
    val settings = tikuSettings().value
    var sceneIdx by remember { mutableStateOf(0) }
    var visible by remember { mutableStateOf(true) }
    var cipherText by remember { mutableStateOf(PLAIN_LINE) }

    // 3 scenes x 3.333s loop with the design's .4s fade-out/in between
    LaunchedEffect(Unit) {
        while (true) {
            delay(2933)
            visible = false
            delay(400)
            sceneIdx = (sceneIdx + 1) % 3
            visible = true
        }
    }
    // cipher scramble for scene 2 (kind=1), 60ms ticks like the prototype
    LaunchedEffect(sceneIdx) {
        if (sceneIdx != 1) return@LaunchedEffect
        var n = 0
        while (true) {
            cipherText = PLAIN_LINE.mapIndexed { i, c ->
                if (i < n) c else RND_CHARS[Random.nextInt(RND_CHARS.length)]
            }.joinToString("")
            n++
            if (n > PLAIN_LINE.length + 14) {
                cipherText = PLAIN_LINE.map { RND_CHARS[Random.nextInt(RND_CHARS.length)] }.joinToString("")
                break
            }
            delay(60)
        }
    }

    val txtAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(400, easing = TikuEase), label = "txtA")
    val txtOff by animateFloatAsState(if (visible) 0f else 8f, tween(400, easing = TikuEase), label = "txtO")

    // pulsing rings + floating lock (orb)
    val inf = rememberInfiniteTransition(label = "orb")
    val floatT by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3300), RepeatMode.Restart), label = "f")
    // CSS float: 50% translateY(-8) rotate(-3), 3.3s ease-in-out infinite
    val floatTrans = rememberInfiniteTransition(label = "lockfloat")
    val fy by floatTrans.animateFloat(0f, -8f, infiniteRepeatable(tween(1650, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse), label = "fy")
    val fr by floatTrans.animateFloat(0f, -3f, infiniteRepeatable(tween(1650, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse), label = "fr")
    @Suppress("UNUSED_VARIABLE") val unused = floatT

    Box(Modifier.fillMaxSize()) {
        // radial glow (hero:before)
        Box(
            Modifier.size(320.dp).offset((-90).dp, 40.dp)
                .background(Brush.radialGradient(listOf(Color(0x77F5A27D), Color.Transparent)), CircleShape)
        )
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                // OWNER ADDITION (sanctioned): final logo artwork above the orb
                Image(
                    painterResource(R.drawable.tiku_logo), contentDescription = "Tikuu",
                    modifier = Modifier.size(92.dp).clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(26.dp))
                // orb: 3 pulsing rings + floating gradient lock tile
                Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    for (i in 0..2) {
                        val t = rememberInfiniteTransition(label = "ring$i")
                        val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3300, easing = TikuEase), RepeatMode.Restart,
                            initialStartOffset = androidx.compose.animation.core.StartOffset(i * 1100)), label = "rp$i")
                        Box(
                            Modifier.size(150.dp).scale(0.45f + phase * 1.05f).alpha(0.7f * (1f - phase))
                                .border(1.5.dp, settings.accent, CircleShape)
                        )
                    }
                    Box(
                        Modifier.size(84.dp).offset(y = fy.dp).rotate(fr)
                            .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = settings.gradBottom)
                            .clip(RoundedCornerShape(28.dp))
                            .background(settings.gradBrush),
                        contentAlignment = Alignment.Center,
                    ) {
                        TikuBookIcon(Modifier.size(38.dp), tint = Color.White)
                    }
                }
                Spacer(Modifier.height(26.dp))
                // cipher / sample chip
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(TikuTokens.Bubble)
                        .shadow(8.dp, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .alpha(txtAlpha).offset(y = txtOff.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (Scenes[sceneIdx].third == 2) {
                            EmojiArt("📘", 20.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            when (Scenes[sceneIdx].third) {
                                0 -> PLAIN_LINE
                                1 -> cipherText
                                else -> "notes · ∫ formulas · △ diagrams"
                            },
                            fontFamily = SerifItalic, fontSize = 20.sp, color = TikuTokens.Ink,
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                Column(Modifier.alpha(txtAlpha).offset(y = txtOff.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SerifTitle(Scenes[sceneIdx].first, 40.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(Scenes[sceneIdx].second, color = TikuTokens.Muted, fontSize = 15.sp,
                        fontFamily = Manrope, textAlign = TextAlign.Center,
                        modifier = Modifier.width(270.dp).height(42.dp))
                }
                Spacer(Modifier.height(14.dp))
                Row {
                    Scenes.indices.forEach { i ->
                        Box(
                            Modifier.padding(end = 6.dp).height(6.dp)
                                .width(if (i == sceneIdx) 22.dp else 6.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (i == sceneIdx) settings.accent else TikuTokens.Line.copy(alpha = 0.7f))
                        )
                    }
                }
            }
            // bottom block: logo + Google button + legal
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SerifTitle("Tikuu", 36.sp)
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().height(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(TikuTokens.Card)
                        .border(1.dp, TikuTokens.Line, RoundedCornerShape(18.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!AppState.authBusy) { AppState.authBusy = true; onGoogleClick() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (AppState.authBusy) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = settings.accent, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_google), null, Modifier.size(20.dp), tint = Color.Unspecified)
                            Spacer(Modifier.width(10.dp))
                            Text("Continue with Google", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                                fontFamily = Manrope, color = TikuTokens.Ink)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Continue with Google to sync your notes & progress on every device.", fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope)
            }
        }
    }
}
