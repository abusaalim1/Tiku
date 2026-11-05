package com.tiku.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Scenes = listOf(
    Triple("Queen of Tears, now in Hindi", "Korean love stories dubbed in Hindi — every episode in one quiet place.", 0),
    Triple("Love stories to read", "Short romantic stories from Seoul — perfect between episodes.", 1),
    Triple("Photos • Moments • Butterflies", "Cute, romantic stills and scenes from the dramas you love.", 2),
)
private const val PLAIN_LINE = "Queen of Tears • Ep 2 — Hindi Dubbed"
private const val RND_CHARS = "▓▒░#@%&*ø§¤"

@Composable
fun WelcomeScreen(onGoogleClick: () -> Unit) {
    androidx.activity.compose.BackHandler { AppState.go(Screen.Facade) }
    val settings = tikuSettings().value
    // ROUND 36: premium-dark stage + liquid glass, the current app's
    // language (palette + InterTight) — scenes, timing and the sign-in
    // contract below are unchanged; visuals only.
    val pal = LocalPalette.current
    val density = LocalDensity.current
    var sceneIdx by remember { mutableStateOf(0) }
    var visible by remember { mutableStateOf(true) }
    var cipherText by remember { mutableStateOf(PLAIN_LINE) }

    // 3 scenes x 3.333s loop with smooth fade-out/in
    LaunchedEffect(Unit) {
        while (true) {
            delay(2933)
            visible = false
            delay(400)
            sceneIdx = (sceneIdx + 1) % Scenes.size
            visible = true
        }
    }

    // Cipher scramble for scene 2 (kind=1)
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

    // Pulsing hero animations
    val floatTrans = rememberInfiniteTransition(label = "lockfloat")
    val fy by floatTrans.animateFloat(
        0f, -6f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fy"
    )
    val fr by floatTrans.animateFloat(
        0f, -2.5f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fr"
    )

    val scrollState = rememberScrollState()

    Box(
        Modifier.fillMaxSize().background(pal.bg)
            .drawBehind {
                with(density) { drawThemePattern(pal.pat, pal.ac, pal.isDark, 160.dp.toPx()) }
            },
    ) {
        // Soft ambient radial background glow
        Box(
            Modifier
                .size(340.dp)
                .offset((-80).dp, 20.dp)
                .background(
                    Brush.radialGradient(
                        listOf(pal.ac.copy(alpha = 0.26f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        // Main vertically scrollable content container (prevents clipping & breaking on small/tall screens)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ---- TOP & CENTER HERO SECTION ----
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                // Unified Hero Badge: Pulsing glowing aura rings + floating logo tile
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    for (i in 0..2) {
                        val t = rememberInfiniteTransition(label = "ring$i")
                        val phase by t.animateFloat(
                            0f, 1f,
                            infiniteRepeatable(
                                tween(3200, easing = TikuEase),
                                RepeatMode.Restart,
                                initialStartOffset = StartOffset(i * 1050)
                            ),
                            label = "rp$i"
                        )
                        Box(
                            Modifier
                                .size(140.dp)
                                .scale(0.55f + phase * 0.95f)
                                .alpha(0.6f * (1f - phase))
                                .border(1.5.dp, pal.ac.copy(alpha = 0.5f), CircleShape)
                        )
                    }

                    // Floating App Logo Badge
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .offset(y = fy.dp)
                            .rotate(fr)
                            .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = pal.ac2)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.tiku_logo),
                            contentDescription = "Tiku Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Feature Chip / Cipher capsule
                Box(
                    modifier = Modifier
                        .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = Color(0x4D000000))
                        .glassSurface(RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .alpha(txtAlpha)
                        .offset(y = txtOff.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (Scenes[sceneIdx].third == 2) {
                            EmojiArt("❤️", 18.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = when (Scenes[sceneIdx].third) {
                                0 -> PLAIN_LINE
                                1 -> cipherText
                                else -> "love · fate · first snow"
                            },
                            fontFamily = InterTight,
                            fontSize = 17.sp,
                            color = pal.tx
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Dynamic Scene Title & Subtitle with responsive typography
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .alpha(txtAlpha)
                        .offset(y = txtOff.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = Scenes[sceneIdx].first,
                        fontSize = 30.sp,
                        fontFamily = InterTight,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                        color = pal.tx,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = Scenes[sceneIdx].second,
                        color = pal.sub,
                        fontSize = 13.sp,
                        fontFamily = PoppinsFamily,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(horizontal = 4.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Smooth Dot Indicators
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Scenes.indices.forEach { i ->
                        val isCurrent = i == sceneIdx
                        val dotWidth by animateFloatAsState(
                            if (isCurrent) 22f else 6f,
                            tween(300, easing = TikuEase),
                            label = "dw$i"
                        )
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .height(6.dp)
                                .width(dotWidth.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isCurrent) pal.ac
                                    else Color.White.copy(alpha = 0.14f)
                                )
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ---- BOTTOM AUTH & GOOGLE SIGN-IN SECTION ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Tiku", fontSize = 32.sp, fontFamily = InterTight,
                    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = pal.tx)
                Spacer(Modifier.height(12.dp))

                // Modern Instagram-styled Google Sign-In button with elevation & click feedback
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = Color(0x4D000000))
                        .glassSurface(RoundedCornerShape(16.dp))
                        .clickable(enabled = !AppState.authBusy) {
                            if (!AppState.authBusy) {
                                AppState.authBusy = true
                                onGoogleClick()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (AppState.authBusy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = pal.ac,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Connecting with Google...",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                fontFamily = PoppinsFamily,
                                color = pal.sub
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_google),
                                contentDescription = "Google Icon",
                                modifier = Modifier.size(22.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Continue with Google",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                fontFamily = PoppinsFamily,
                                color = pal.tx
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Continue with Google to sync your journal, favourites & progress securely.",
                    fontSize = 12.sp,
                    color = pal.sub,
                    fontFamily = PoppinsFamily,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
        }
    }
}
