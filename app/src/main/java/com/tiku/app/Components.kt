package com.tiku.app

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PressAnim = androidx.compose.animation.core.tween<Float>(250)

@Composable
fun AvatarBox(
    person: Person,
    size: Dp,
    corner: Dp = size * 0.36f,
    fontSizeSp: Float = size.value * 0.42f,
    photo: Bitmap? = null,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = Modifier.size(size).clip(shape)
            .background(Color(person.colorArgb)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = photo ?: person.photoBitmap
        when {
            bmp != null -> {
                Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
            person.photoUrl.isNotEmpty() -> {
                coil.compose.AsyncImage(person.photoUrl, null, Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
            else -> {
                if (EmojiArt.assetName(person.emoji) != null) {
                    EmojiArt(person.emoji, (fontSizeSp * 1.2f).dp)
                } else {
                    Text(person.name.take(1).uppercase(), fontSize = fontSizeSp.sp, fontFamily = Bricolage, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/** .ib icon button: 42dp circle, card bg, hairline border. */
@Composable
fun GlassIconButton(icon: ImageVector, size: Dp = 42.dp, iconSize: Dp = 20.dp, label: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.size(size)
            .clip(CircleShape)
            .background(TikuTokens.Card)
            .border(1.dp, TikuTokens.Line, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize), tint = TikuTokens.Ink)
    }
}

/** .pri primary button: 54dp tall, radius 18, ink bg, 600 weight. */
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (enabled) 1f else 1f, tween(300, easing = TikuEase), label = "pri")
    Box(
        modifier = modifier
            .height(54.dp).scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) TikuTokens.Button else TikuTokens.Button.copy(alpha = 0.35f))
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TikuTokens.ButtonText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Manrope)
    }
}

/** .tg toggle: 50x30dp pill. */
@Composable
fun ToggleSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.width(50.dp).height(30.dp)
            .clip(CircleShape)
            .background(if (on) SettingsStore.state.collectAsState().value.accent else TikuTokens.Line)
            .clickable(interactionSource = interaction, indication = null) { onChange(!on) },
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier.size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(0.dp, Color.Transparent),
        )
    }
}

/** Serif italic display title (covers .top h2, .form h2, .hero h1, .logo, .sht, .gh, .sq, .cph, #kn). */
@Composable
fun SerifTitle(text: String, fontSize: androidx.compose.ui.unit.TextUnit, color: Color = TikuTokens.Ink,
               modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text, fontSize = fontSize, fontFamily = SerifItalic, color = color,
        letterSpacing = (-0.5).sp, modifier = modifier, textAlign = textAlign)
}

/** Design toast: dark pill sliding down from top. */
@Composable
fun BoxScope.DesignToast() {
    val text = AppState.toastText
    AnimatedVisibility(
        visible = text != null,
        enter = slideInVertically(tween(600, easing = TikuEase)) { -80 } + fadeIn(),
        exit = slideOutVertically(tween(600, easing = TikuEase)) { -80 } + fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(TikuTokens.ToastBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(text ?: "", color = TikuTokens.ToastText, fontSize = 14.sp, fontFamily = Manrope,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}

/** #sh bottom sheet: dim + blur scrim, sheet slides up with grabber. */
@Composable
fun BoxScope.BottomSheetHost() {
    val content = AppState.sheetContent
    AnimatedVisibility(
        visible = content != null,
        enter = fadeIn(tween(400, easing = TikuEase)),
        exit = fadeOut(tween(400, easing = TikuEase)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color(0x59320F0A))
                .clickable(interactionSource = remember { MutableInteractionSource() },
                    indication = null) { AppState.closeSheet() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = content != null,
                enter = slideInVertically(tween(550, easing = TikuEase)) { it },
                exit = slideOutVertically(tween(400, easing = TikuEase)) { it },
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                        .background(TikuTokens.Card)
                        .clickable(interactionSource = remember { MutableInteractionSource() },
                            indication = null) { },
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier.width(38.dp).height(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(TikuTokens.Muted.copy(alpha = 0.4f))
                                .align(Alignment.CenterHorizontally)
                        )
                        content?.invoke()
                    }
                }
            }
        }
    }
}

