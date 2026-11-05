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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/** .pri primary button: 54dp tall.
 *  ROUND 36: restyled to the current Tik action language — the filled
 *  variant of TikPrimaryButton: full pill, palette button gradient,
 *  InterTight, white text dimmed when disabled. Only the auth family
 *  and the journal gate call this (Tik surfaces use TikPrimaryButton),
 *  so the restyle touches no Tik surface. Height and contract unchanged. */
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val scale by animateFloatAsState(if (enabled) 1f else 1f, tween(300, easing = TikuEase), label = "pri")
    Box(
        modifier = modifier
            .height(54.dp).scale(scale)
            .clip(RoundedCornerShape(999.dp))
            .background(pal.buttonGradient)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.SemiBold, fontSize = 15.sp, fontFamily = InterTight)
    }
}

/** Toggle switch — ROUND 13: the owner design's switch (52x31, thumb 27,
 *  spring glide, on = theme accent, off = s2 with hairline). Only Tik
 *  surfaces call this (Chat Details + Me settings) — the facade has no
 *  toggles. */
@Composable
fun ToggleSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    TikuToggleSwitch(checked = on, onCheckedChange = onChange)
}

/** Serif italic display title (covers .top h2, .form h2, .hero h1, .logo, .sht, .gh, .sq, .cph, #kn). */
@Composable
fun SerifTitle(text: String, fontSize: androidx.compose.ui.unit.TextUnit, color: Color = TikuTokens.Ink,
               modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text, fontSize = fontSize, fontFamily = SerifItalic, color = color,
        letterSpacing = (-0.5).sp, modifier = modifier, textAlign = textAlign)
}

/** Design toast. ROUND 13: inside the Tik section the toast is the owner
 *  design's (.toast — floating sheet-coloured pill r999 with glass border,
 *  bottom, above the glass nav); everywhere else (facade / gate) the
 *  original top pill stays. */
@Composable
fun BoxScope.DesignToast() {
    val text = AppState.toastText
    if (AppState.isChatRoot(AppState.screen)) {
        val pal = LocalPalette.current
        AnimatedVisibility(
            visible = text != null,
            enter = slideInVertically(tween(280, easing = TikEase)) { 48 } + fadeIn(tween(280, easing = TikEase)),
            exit = slideOutVertically(tween(280, easing = TikEase)) { 48 } + fadeOut(tween(280, easing = TikEase)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(sheetColor())
                    .border(1.dp, pal.gb, RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text ?: "", color = pal.tx, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
                    fontFamily = InterTight,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp))
            }
        }
        return
    }
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

/** Bottom sheet host. ROUND 14: sheets only ever open inside the Tik
 *  section, where the host takes the redesign's sheet skin — a FLOATING
 *  sheet (8dp margin all round, r28) in solid SheetC (#16161B) over a black
 *  scrim (.55), sliding up .38s on the design easing, grabber 40x5
 *  (white .25), hairline border (white .12). Sheet contents carry their
 *  own design margins (host horizontal padding is 0). The peach host below
 *  is kept verbatim for any non-Tik surface. */
@Composable
fun BoxScope.BottomSheetHost() {
    val content = AppState.sheetContent
    if (AppState.isChatRoot(AppState.screen)) {
        // ROUND 14: the Tik sheet host wears the redesign's sheet skin —
        // solid #16161B, r28 corners, white-alpha hairline + grabber, black
        // scrim. Entrance motion unchanged.
        val sheetShape = RoundedCornerShape(28.dp)
        AnimatedVisibility(
            visible = content != null,
            enter = fadeIn(tween(280, easing = TikEase)),
            exit = fadeOut(tween(280, easing = TikEase)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(interactionSource = remember { MutableInteractionSource() },
                        indication = null) { AppState.closeSheet() },
                contentAlignment = Alignment.BottomCenter,
            ) {
                AnimatedVisibility(
                    visible = content != null,
                    enter = slideInVertically(tween(380, easing = TikEase)) { it },
                    exit = slideOutVertically(tween(300, easing = TikEase)) { it },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clip(sheetShape)
                            .background(SheetC, sheetShape)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), sheetShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() },
                                indication = null) { },
                    ) {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
                        ) {
                            Box(
                                modifier = Modifier.width(40.dp).height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .align(Alignment.CenterHorizontally)
                            )
                            Spacer(Modifier.height(6.dp))
                            content?.invoke()
                        }
                    }
                }
            }
        }
        return
    }
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

// ================= Profile picture presets (Round 11) =================

/**
 * Ready-made profile pictures bundled in assets/avatars (512px WebP,
 * ~36 KB each). A preset is NOT a special reference: picking one decodes
 * its bitmap and hands it to the caller's normal photo path
 * (AppState.myAvatar at signup, FirebaseEngine.updateProfilePhoto in
 * Edit profile), so it flows through MediaStore.compressAvatar →
 * photoData exactly like an uploaded photo and renders on EVERY surface
 * avatars render (chat list, chat header, Chat Details, Me). No render
 * site knows presets exist — that is deliberate (a preset-as-reference
 * once shipped as an empty circle on one screen in another app).
 */
object PresetAvatars {
    val files: List<String> = (1..7).map { "avatars/avatar_$it.webp" }

    fun load(ctx: android.content.Context, index: Int): Bitmap? = runCatching {
        ctx.assets.open(files[index]).use { android.graphics.BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

/** "Pick a picture" — the bundled presets as a row of circular crops.
 *  [dark] = the Tik-section skin (Edit profile sheet); the signup surface
 *  keeps the peach skin. */
@Composable
fun PresetAvatarPicker(dark: Boolean = false, onPick: (Bitmap) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pal = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Pick a picture", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = if (dark) InterTight else Manrope, color = if (dark) pal.sub else TikuTokens.Muted,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PresetAvatars.files.indices.forEach { i ->
                val bmp = remember(ctx, i) { PresetAvatars.load(ctx, i) }
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(if (dark) pal.s1 else TikuTokens.Mint)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { if (bmp != null) onPick(bmp) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp != null) {
                        Image(
                            bmp.asImageBitmap(), null, Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}
