package com.tiku.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Call screen — the designed dark-gradient UI. WebRTC engine is OUT OF SCOPE this
 * round: this screen shows the "Calling…" state, writes a real call-log entry, and
 * shows the design's toast after ~1.2s ("Calls are coming in the next round").
 */
@Composable
fun CallScreen(threadId: String, video: Boolean) {
    androidx.activity.compose.BackHandler { AppState.go(Screen.Home) }
    val thread = AppState.threads.value.firstOrNull { it.id == threadId }
    val person = thread?.person ?: Person(name = "Unknown")
    var secs by remember { mutableIntStateOf(-3) }
    var toggled by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(threadId) {
        AppState.repo.logCall(threadId, video)
        delay(1200)
        AppState.toast("Calls are coming in the next round")
        delay(1600)
        AppState.go(Screen.Chat(threadId))
    }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); secs++ }
    }

    val status = if (secs < 0) "Calling…" else "%d:%02d".format(secs / 60, secs % 60)
    val inf = rememberInfiniteTransition(label = "callfloat")
    val fy by inf.animateFloat(0f, -8f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "fy")

    Box(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF5A1F1A), Color(0xFF1F0E0D))))
            .then(if (video) Modifier.background(
                Brush.radialGradient(listOf(Color(0x66F0885B), Color.Transparent)),
            ) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (video) {
            Box(Modifier.fillMaxSize().padding(top = 60.dp, end = 20.dp)) {
                Box(
                    Modifier.size(92.dp, 130.dp).align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0x1FFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) { Text("You", fontSize = 12.sp, color = Color.White) }
            }
        }
        Column(
            Modifier.fillMaxSize().padding(top = 70.dp, bottom = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.offset(y = fy.dp)) {
                    AvatarBox(person, 120.dp, 44.dp, 48f)
                }
                Spacer(Modifier.height(18.dp))
                SerifTitle(person.name, 30.sp, Color.White)
                Spacer(Modifier.height(6.dp))
                Text(status, fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f), fontFamily = Manrope)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TikuLockIcon(Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.width(5.dp))
                    Text("End-to-end encrypted", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), fontFamily = Manrope)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(Icons.Filled.Mic to "m", Icons.Filled.VolumeUp to "s", Icons.Filled.Videocam to "c").forEach { (icon, key) ->
                        val on = toggled.contains(key)
                        Box(
                            Modifier.size(62.dp).clip(CircleShape)
                                .background(if (on) Color.White else Color(0x22FFFFFF))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    toggled = if (on) toggled - key else toggled + key
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, null, Modifier.size(22.dp), tint = if (on) Color(0xFF1F0E0D) else Color.White)
                        }
                    }
                    Box(
                        Modifier.size(62.dp).clip(CircleShape).background(TikuTokens.Red)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                AppState.go(Screen.Chat(threadId))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", fontSize = 22.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Story viewer — progress segments, gradient backgrounds, serif quote text,
 * tap left/right to navigate. Fed by the Moments data model.
 */
@Composable
fun StoryScreen() {
    androidx.activity.compose.BackHandler { AppState.go(Screen.Home) }
    val content = AppState.story
    if (content == null) { LaunchedEffect(Unit) { AppState.go(Screen.Home) }; return }

    var sl by remember { mutableIntStateOf(0) }
    val segments = minOf(3, content.quotes.size.coerceAtLeast(1))
    val grad = content.grads[sl % content.grads.size]
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun next() { if (sl >= segments - 1) AppState.go(Screen.Home) else sl++ }
    fun prev() { if (sl > 0) sl-- }

    // auto-advance ~3.6s per segment (1.4% per 50ms like the prototype)
    LaunchedEffect(sl) {
        delay(3570)
        if (sl < segments - 1) sl++ else AppState.go(Screen.Home)
    }

    val quote = content.quotes[(content.personIndex + sl) % content.quotes.size]

    Box(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(grad.first, grad.second)))
            .onGloballyPositioned { size = it.size },
    ) {
        // tap zones
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() },
                indication = null) { prev() })
            Box(Modifier.weight(2f).fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() },
                indication = null) { next() })
        }
        Column(Modifier.fillMaxSize().padding(top = 56.dp, bottom = 40.dp, start = 24.dp, end = 24.dp)) {
            // progress segments
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(segments) { i ->
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(3.dp))
                            .background(Color(0x55FFFFFF)),
                    ) {
                        Box(
                            Modifier.fillMaxHeight()
                                .fillMaxWidth(if (i < sl) 1f else if (i == sl) segmentProgress(sl) else 0f)
                                .background(Color.White),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBox(content.person, 42.dp, 15.dp, 18f)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(content.person.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 16.sp, color = Color.White, fontFamily = Manrope)
                    Text("2h ago", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), fontFamily = Manrope)
                }
            }
            Spacer(Modifier.weight(1f))
            SerifTitle(quote, 52.sp, Color.White)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Tap to continue · ", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), fontFamily = Manrope)
                TikuLockIcon(Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.8f))
                Text(" Encrypted", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), fontFamily = Manrope)
            }
        }
    }
}

@Composable
private fun segmentProgress(sl: Int): Float {
    val inf = rememberInfiniteTransition(label = "seg")
    val w by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3570), RepeatMode.Restart), label = "segw")
    @Suppress("UNUSED_PARAMETER") val unused = sl
    return w
}
