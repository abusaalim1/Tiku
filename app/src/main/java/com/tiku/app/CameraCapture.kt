package com.tiku.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ================= ROUND 83 — in-app camera (CameraX) =================
// The owner's order: Tiku's OWN camera, never the system camera app —
// no OEM filters/effects/state can leak into a capture, the photo goes
// DIRECTLY into the chat (cache file only, deleted once the send
// pipeline owns the bitmap; no gallery insertion anywhere here),
// and the whole bind/capture path is guarded so a weak or camera-less
// device degrades to the caller's graceful failure surface instead of
// a crash (the build-112 lesson that created the old intent path).
// Low-end discipline: Preview + ImageCapture ONLY (no analysis),
// capture capped at 1920x1440 (the send pipeline compresses to 1280px
// anyway), camera released the moment this overlay leaves composition.

// Design tokens — file-private in Chat.kt, so the same hexes live here.
private val CamRose = Color(0xFFFF5C8A)
private val CamRoseDeep = Color(0xFFE23D74)
private val CamCard = Color(0xFF1B1B1F)
@Suppress("unused")
private val CamSheet = Color(0xFF16161B)
@Suppress("unused")
private val CamSub = Color(0xFF9A99A6)

/** ROUND 83: the rotation (degrees, clockwise) + horizontal-mirror a
 *  stored EXIF orientation demands to display upright. */
internal data class ExifTransform(val degrees: Int, val mirror: Boolean)

/** The androidx EXIF orientation table (ExifInterface constants):
 *  1 NORMAL -> (0,false) · 2 FLIP_HORIZONTAL -> (0,true) ·
 *  3 ROTATE_180 -> (180,false) · 4 FLIP_VERTICAL -> (180,true) ·
 *  5 TRANSPOSE -> (270,true) · 6 ROTATE_90 -> (90,false) ·
 *  7 TRANSVERSE -> (90,true) · 8 ROTATE_270 -> (270,false). */
internal fun exifTransform(orientation: Int): ExifTransform = when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90 -> ExifTransform(90, false)
    ExifInterface.ORIENTATION_TRANSVERSE -> ExifTransform(90, true)
    ExifInterface.ORIENTATION_ROTATE_180 -> ExifTransform(180, false)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifTransform(180, true)
    ExifInterface.ORIENTATION_ROTATE_270 -> ExifTransform(270, false)
    ExifInterface.ORIENTATION_TRANSPOSE -> ExifTransform(270, true)
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifTransform(0, true)
    else -> ExifTransform(0, false)
}

/** ROUND 85 (WS-D): the in-app camera's two modes. Photo keeps the
 *  Round-83 Preview + ImageCapture binding; Video binds Preview +
 *  VideoCapture (still NO ImageAnalysis in either). */
enum class CamMode { PHOTO, VIDEO }

/** ROUND 85 (WS-D) — the recording timer policy: the timer is a MAX
 *  RECORDING LENGTH, chosen before recording (30 s, 60 s, or a custom
 *  number of seconds); when the elapsed time reaches it, recording
 *  stops automatically and lands on the confirm screen. No choice =
 *  no auto-stop (the send pipeline's own caps then govern: a 64 MB
 *  file cap in deliverMedia's caller, sendMediaFile — stated in the
 *  round report). Pure so the harness can pin the decision. */
object VideoTimerPolicy {
    const val PRESET_SHORT_SEC = 30
    const val PRESET_LONG_SEC = 60
    /** Custom limits are whole seconds in [MIN_CUSTOM_SEC]..[MAX_CUSTOM_SEC]. */
    const val MIN_CUSTOM_SEC = 5
    const val MAX_CUSTOM_SEC = 3600

    /** True when a recording at [elapsedSec] has reached its limit. */
    fun shouldAutoStop(elapsedSec: Int, limitSec: Int?): Boolean =
        limitSec != null && limitSec > 0 && elapsedSec >= limitSec

    /** Parse a custom-seconds entry; null when it isn't a usable
     *  limit (blank, non-numeric, out of range). */
    fun sanitizeCustom(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in MIN_CUSTOM_SEC..MAX_CUSTOM_SEC }

    /** "0:07" / "1:30" clock for the recording pill + timer chips. */
    fun formatClock(totalSec: Int): String {
        val s = totalSec.coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }
}

/** ROUND 83: decode a CameraX capture the way the old system-camera
 *  path decoded its result (bounds-sampled so the long edge lands at
 *  or under ~2560 px BEFORE the full decode — OOM discipline), PLUS
 *  the EXIF fix the old path never had: apply the stored orientation
 *  (rotate + mirror) so the photo is never sideways, and mirror
 *  front-camera captures back to what the user saw in the preview
 *  when [mirrorHorizontally] is set. Null on any failure. */
internal fun decodeCameraCapture(f: File, mirrorHorizontally: Boolean): Bitmap? = runCatching {
    if (!f.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / sample > 2560) sample *= 2
    val src = BitmapFactory.decodeFile(
        f.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    val orientation = runCatching {
        ExifInterface(f.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val transform = exifTransform(orientation)
    // The stored mirror and the preview mirror combine: exactly one
    // of them asking for a flip means the pixels get flipped once.
    val flip = transform.mirror != mirrorHorizontally
    val matrix = Matrix()
    if (transform.degrees != 0) matrix.postRotate(transform.degrees.toFloat())
    if (flip) matrix.postScale(-1f, 1f)
    if (matrix.isIdentity) {
        src
    } else {
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (out !== src) src.recycle()
        out
    }
}.getOrNull()

@Composable
private fun CameraChip(
    tag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(CamCard)
            .testTag(tag).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** ROUND 83: the in-app camera's chrome — pure UI (no CameraX runtime
 *  calls; only the FLASH_MODE_* int constants pick the flash icon).
 *  Black full-size stage: [preview] fills it, close + flash ride the
 *  top, and the bottom row is spacer / rose-gradient shutter / flip.
 *  [focusPoint], when set, draws the tap-to-focus ring at that point.
 *  ROUND 85 (WS-D): video mode adds the Photo/Video toggle, the
 *  max-length timer chips (30s / 60s / Custom — see VideoTimerPolicy)
 *  and the recording state (elapsed pill up top, red stop square in
 *  the shutter). All video props default to the photo chrome, so the
 *  Round-83 staging compiles and renders unchanged. */
@Composable
internal fun CameraChrome(
    flashMode: Int,
    hasFlash: Boolean,
    capturing: Boolean,
    onClose: () -> Unit,
    onFlash: () -> Unit,
    onShutter: () -> Unit,
    onFlip: () -> Unit,
    focusPoint: Offset? = null,
    videoMode: Boolean = false,
    recording: Boolean = false,
    recElapsedSec: Int = 0,
    recLimitSec: Int? = null,
    onModeToggle: (Boolean) -> Unit = {},
    onLimitPick: (Int?) -> Unit = {},
    preview: @Composable () -> Unit,
) {
    // ROUND 85: the custom-seconds editor's transient state (pure UI —
    // the parsed limit leaves through onLimitPick only when Set is
    // tapped with a valid value; an invalid entry simply doesn't
    // close the editor).
    var customEditing by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        preview()
        // Tap-to-focus ring (drawn over the preview, under the bars).
        focusPoint?.let { fp ->
            Box(
                Modifier.offset {
                    IntOffset(
                        (fp.x - 32.dp.toPx()).roundToInt(),
                        (fp.y - 32.dp.toPx()).roundToInt(),
                    )
                }.size(64.dp).clip(CircleShape).border(2.dp, CamRose, CircleShape),
            )
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraChip(tag = "camera_close", onClick = onClose) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            // ROUND 85 (WS-D): while recording, the elapsed clock rides
            // top-centre (with the chosen limit when one is set).
            if (recording) {
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(CamCard)
                        .testTag("camera_rec_pill")
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(CamRoseDeep))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (recLimitSec != null) {
                            "${VideoTimerPolicy.formatClock(recElapsedSec)} / ${VideoTimerPolicy.formatClock(recLimitSec)}"
                        } else {
                            VideoTimerPolicy.formatClock(recElapsedSec)
                        },
                        color = Color.White, fontSize = 13.5.sp,
                        fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (hasFlash && !recording) {
                val flashIcon = when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                    else -> Icons.Filled.FlashOff
                }
                CameraChip(tag = "camera_flash", onClick = onFlash) {
                    Icon(
                        flashIcon, null,
                        tint = if (flashMode == ImageCapture.FLASH_MODE_OFF) Color.White else CamRose,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(44.dp))
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!recording) {
                // ROUND 85 (WS-D): the timer chips — video mode only.
                // A set limit selects its chip (a custom value selects
                // Custom and names the seconds); tapping the selected
                // preset again clears back to no auto-stop.
                if (videoMode) {
                    if (customEditing) {
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(CamCard)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = customText,
                                onValueChange = { v ->
                                    customText = v.filter { it.isDigit() }.take(4)
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.White, fontSize = 14.sp,
                                    fontFamily = PoppinsFamily,
                                ),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                ),
                                modifier = Modifier.width(64.dp).testTag("camera_timer_custom_field")
                                    .padding(horizontal = 8.dp),
                                decorationBox = { inner ->
                                    if (customText.isEmpty()) {
                                        Text("Seconds", color = CamSub, fontSize = 13.sp, fontFamily = PoppinsFamily)
                                    }
                                    inner()
                                },
                            )
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp))
                                    .background(Brush.linearGradient(listOf(CamRose, CamRoseDeep)))
                                    .testTag("camera_timer_custom_set")
                                    .clickable {
                                        val parsed = VideoTimerPolicy.sanitizeCustom(customText)
                                        if (parsed != null) {
                                            onLimitPick(parsed)
                                            customEditing = false
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            ) {
                                Text("Set", color = Color.White, fontSize = 13.sp, fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.size(6.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(0.10f))
                                    .clickable { customEditing = false }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text("Cancel", color = Color.White, fontSize = 13.sp, fontFamily = PoppinsFamily)
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CamTimerChip(
                                tag = "camera_timer_30",
                                label = "30s",
                                selected = recLimitSec == VideoTimerPolicy.PRESET_SHORT_SEC,
                            ) {
                                onLimitPick(
                                    if (recLimitSec == VideoTimerPolicy.PRESET_SHORT_SEC) null
                                    else VideoTimerPolicy.PRESET_SHORT_SEC,
                                )
                            }
                            CamTimerChip(
                                tag = "camera_timer_60",
                                label = "60s",
                                selected = recLimitSec == VideoTimerPolicy.PRESET_LONG_SEC,
                            ) {
                                onLimitPick(
                                    if (recLimitSec == VideoTimerPolicy.PRESET_LONG_SEC) null
                                    else VideoTimerPolicy.PRESET_LONG_SEC,
                                )
                            }
                            CamTimerChip(
                                tag = "camera_timer_custom",
                                label = if (recLimitSec != null && recLimitSec != VideoTimerPolicy.PRESET_SHORT_SEC && recLimitSec != VideoTimerPolicy.PRESET_LONG_SEC) {
                                    "Custom · ${recLimitSec}s"
                                } else "Custom",
                                selected = recLimitSec != null && recLimitSec != VideoTimerPolicy.PRESET_SHORT_SEC && recLimitSec != VideoTimerPolicy.PRESET_LONG_SEC,
                            ) { customEditing = true }
                        }
                    }
                    Spacer(Modifier.size(14.dp))
                }
                // ROUND 85 (WS-D): the Photo / Video mode toggle.
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(CamCard)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CamModeChip(tag = "camera_mode_photo", label = "Photo", selected = !videoMode) {
                        onModeToggle(false)
                    }
                    CamModeChip(tag = "camera_mode_video", label = "Video", selected = videoMode) {
                        onModeToggle(true)
                    }
                }
                Spacer(Modifier.size(18.dp))
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(80.dp).alpha(if (capturing) 0.5f else 1f)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(CamRose, CamRoseDeep)))
                        .testTag("camera_shutter")
                        .clickable(enabled = !capturing) { onShutter() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (recording) {
                        // ROUND 85 (WS-D): the stop square.
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.White))
                    } else {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White))
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (!recording) {
                        CameraChip(tag = "camera_flip", onClick = onFlip) {
                            Icon(
                                Icons.Filled.FlipCameraAndroid, null,
                                tint = Color.White, modifier = Modifier.size(22.dp),
                            )
                        }
                    } else {
                        Spacer(Modifier.size(44.dp))
                    }
                }
            }
        }
    }
}

/** ROUND 85 (WS-D): one mode-toggle chip in the camera chrome. */
@Composable
private fun CamModeChip(tag: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) Brush.linearGradient(listOf(CamRose, CamRoseDeep)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .testTag(tag)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            label, color = Color.White, fontSize = 13.5.sp,
            fontFamily = PoppinsFamily,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** ROUND 85 (WS-D): one timer chip in the camera chrome. */
@Composable
private fun CamTimerChip(tag: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(CamCard)
            .border(1.dp, if (selected) CamRose else Color.White.copy(0.14f), RoundedCornerShape(999.dp))
            .testTag(tag)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label, color = if (selected) CamRose else Color.White, fontSize = 12.5.sp,
            fontFamily = PoppinsFamily,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** ROUND 83: the capture confirm screen — the staged photo, Retake
 *  (ghost pill) / Use photo (rose-gradient pill). Pure UI.
 *  ROUND 85 (WS-E): the LOOKS selector rides above the pills when
 *  [showLooks] — Keep (normal) · One look · Two looks, the choice the
 *  caller holds (seeded from the composer's arm) and reads back at
 *  Use time through the overlay's callbacks; Retake never touches it.
 *  [useLabel] is "Use photo" / "Use video" by capture kind. */
@Composable
internal fun CameraConfirmView(
    bmp: Bitmap,
    onRetake: () -> Unit,
    onUse: () -> Unit,
    useLabel: String = "Use photo",
    showLooks: Boolean = false,
    looks: Int? = null,
    onLooks: (Int?) -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("camera_confirm")) {
        Image(
            bmp.asImageBitmap(), null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showLooks) {
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(CamCard)
                        .testTag("looks_selector")
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LooksChip(tag = "looks_keep", label = "Keep", selected = looks == null) { onLooks(null) }
                    LooksChip(tag = "looks_one", label = "One look", selected = looks == 1) { onLooks(1) }
                    LooksChip(tag = "looks_two", label = "Two looks", selected = looks == 2) { onLooks(2) }
                }
                Spacer(Modifier.size(14.dp))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(CamCard)
                        .clickable { onRetake() }
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text("Retake", color = Color.White, fontSize = 15.sp, fontFamily = PoppinsFamily)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(Brush.linearGradient(listOf(CamRose, CamRoseDeep)))
                        .testTag("camera_use")
                        .clickable { onUse() }
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text(
                        useLabel, color = Color.White, fontSize = 15.sp,
                        fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** ROUND 85 (WS-E): one looks-selector chip on the confirm screen. */
@Composable
private fun LooksChip(tag: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) Brush.linearGradient(listOf(CamRose, CamRoseDeep)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .testTag(tag)
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 8.dp),
    ) {
        Text(
            label, color = Color.White, fontSize = 13.sp,
            fontFamily = PoppinsFamily,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** ROUND 85 (WS-D): duration (seconds) of a recorded video file, 0
 *  when unreadable — the composer-side mediaDurationSec, mirrored
 *  for the camera (the value rides the staged item into the exact
 *  existing sendVideoVo pipeline). */
internal fun recordedVideoDurationSec(f: File): Int = runCatching {
    val r = android.media.MediaMetadataRetriever()
    try {
        r.setDataSource(f.absolutePath)
        ((r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0").toLong() / 1000).toInt()
    } finally {
        runCatching { r.release() }
    }
}.getOrDefault(0)

/** ROUND 85 (WS-D): one poster frame from a recorded video file for
 *  the confirm screen; null when the file won't read (the confirm
 *  then falls back to a plain dark frame). */
internal fun recordedVideoPoster(f: File): Bitmap? = runCatching {
    val r = android.media.MediaMetadataRetriever()
    try {
        r.setDataSource(f.absolutePath)
        r.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } finally {
        runCatching { r.release() }
    }
}.getOrNull()

/** ROUND 83: the live in-app camera overlay. Binds Preview +
 *  ImageCapture only, captures to app cache, decodes off the main
 *  thread, and hands the CONFIRMED photo to [onUsePhoto] as
 *  (cache file, upright bitmap) — the caller sends it through the
 *  existing pipeline and deletes the file. Any bind failure routes
 *  to [onBindFailed] (the caller's graceful toast + gallery surface);
 *  no exception escapes composition.
 *
 *  ROUND 85 (WS-D/E/F): the overlay now opens in PHOTO or VIDEO mode
 *  ([initialMode]; the toggle inside can switch before a capture).
 *  Video binds Preview + VideoCapture (Recorder capped at HD — the
 *  send pipeline ships the file as-is under its 64 MB cap, so
 *  recording 4K would be pure waste) and records to app-private cache
 *  ONLY (no MediaStore anywhere in this file). The timer chips pick
 *  a MAX LENGTH (30s / 60s / custom seconds — VideoTimerPolicy);
 *  reaching it auto-stops into the confirm screen. The confirm
 *  screen (photo AND video) carries the looks selector (WS-E),
 *  seeded from [initialVo] (the composer's arm snapshot); its final
 *  choice rides the Use callback. "Use" no longer sends — the caller
 *  STAGES the capture in the composer (WS-F). Audio rides only when
 *  the microphone permission is already granted (asked once from the
 *  record tap otherwise; a denial records silently rather than
 *  blocking the video). */
@Composable
fun CameraCaptureOverlay(
    initialMode: CamMode = CamMode.PHOTO,
    initialVo: Int? = null,
    onCancel: () -> Unit,
    onUsePhoto: (file: File, bmp: Bitmap, vo: Int?) -> Unit,
    onUseVideo: (file: File, durSec: Int, vo: Int?) -> Unit,
    onBindFailed: (Throwable) -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(initialMode) }
    var frontLens by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var hasFlash by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<File, Bitmap>?>(null) }
    /** A finished recording awaiting confirm: file + poster (poster
     *  null = unrecoverable frame; the confirm falls back). */
    var pendingVideo by remember { mutableStateOf<Pair<File, Bitmap?>?>(null) }
    /** ROUND 85 (WS-E): the confirm screen's looks choice — seeded
     *  from the composer's arm, kept across Retake, read at Use. */
    var looks by remember { mutableStateOf(initialVo?.takeIf { it in 1..2 }) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // ROUND 85 (WS-D): recording state. `activeRecording` is the
    // CameraX handle; the clock state drives the chrome pill and the
    // auto-stop decision (VideoTimerPolicy).
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var recActive by remember { mutableStateOf(false) }
    var recStartMs by remember { mutableLongStateOf(0L) }
    var recElapsedSec by remember { mutableIntStateOf(0) }
    var recLimitSec by remember { mutableStateOf<Int?>(null) }
    var micAsked by remember { mutableStateOf(false) }

    // `live` gates every async callback: once this overlay leaves
    // composition, a late capture result deletes its file and stops.
    var live by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        live = true
        onDispose {
            live = false
            // Leaving mid-recording: stop it and drop the partial
            // file — a recording nobody confirmed is never kept.
            runCatching { activeRecording?.stop() }
        }
    }
    // Release the camera the moment the screen closes.
    DisposableEffect(provider) {
        val p = provider
        onDispose { runCatching { p?.unbindAll() } }
    }

    // Obtain the camera provider (async future, main-executor listener).
    LaunchedEffect(Unit) {
        runCatching {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener(
                {
                    runCatching { provider = future.get() }
                        .onFailure { if (live) onBindFailed(it) }
                },
                ContextCompat.getMainExecutor(ctx),
            )
        }.onFailure { if (live) onBindFailed(it) }
    }

    // (Re)bind on provider / preview surface / lens / mode change.
    // Per mode: Preview + ImageCapture (photo) or Preview +
    // VideoCapture (video) — never ImageAnalysis (Round-83 law).
    LaunchedEffect(provider, previewView, frontLens, mode) {
        val p = provider ?: return@LaunchedEffect
        val pv = previewView ?: return@LaunchedEffect
        runCatching {
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(pv.surfaceProvider)
            val selector = if (frontLens) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
            p.unbindAll()
            if (mode == CamMode.VIDEO) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(Quality.HD, Quality.SD),
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                        ),
                    ).build()
                val vc = VideoCapture.withOutput(recorder)
                val cam = p.bindToLifecycle(lifecycleOwner, selector, preview, vc)
                videoCapture = vc
                imageCapture = null
                camera = cam
                hasFlash = runCatching { cam.cameraInfo.hasFlashUnit() }.getOrDefault(false)
            } else {
                val resSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1920, 1440),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setResolutionSelector(resSelector)
                    .setFlashMode(flashMode)
                    .build()
                val cam = p.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                imageCapture = capture
                videoCapture = null
                camera = cam
                hasFlash = runCatching { cam.cameraInfo.hasFlashUnit() }.getOrDefault(false)
            }
        }.onFailure { if (live) onBindFailed(it) }
    }

    // Flash changes apply to the bound use-case — no rebind.
    LaunchedEffect(flashMode, imageCapture) {
        runCatching { imageCapture?.flashMode = flashMode }
    }

    // The focus ring fades shortly after the tap.
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(900)
            focusPoint = null
        }
    }

    // ROUND 85 (WS-D): the recording clock + the auto-stop decision.
    // The decision itself is VideoTimerPolicy.shouldAutoStop (unit-
    // pinned); this loop only feeds it the elapsed seconds and pulls
    // the stop lever — reaching the limit lands on the confirm screen
    // through the same Finalize path as a manual stop.
    LaunchedEffect(recActive) {
        if (!recActive) return@LaunchedEffect
        while (live && recActive) {
            recElapsedSec = ((System.currentTimeMillis() - recStartMs) / 1000).toInt()
            if (VideoTimerPolicy.shouldAutoStop(recElapsedSec, recLimitSec)) {
                runCatching { activeRecording?.stop() }
                break
            }
            delay(200)
        }
    }

    fun startRecording() {
        val vc = videoCapture ?: return
        if (recActive) return
        runCatching {
            val file = File(ctx.cacheDir, "capvid_inapp_${System.currentTimeMillis()}.mp4")
            val pendingRec = vc.output.prepareRecording(
                ctx, FileOutputOptions.Builder(file).build(),
            )
            val withAudio = ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (withAudio) pendingRec.withAudioEnabled()
            recStartMs = System.currentTimeMillis()
            recElapsedSec = 0
            recActive = true
            activeRecording = pendingRec.start(ContextCompat.getMainExecutor(ctx)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    recActive = false
                    activeRecording = null
                    when {
                        !live -> runCatching { file.delete() }
                        event.hasError() -> {
                            runCatching { file.delete() }
                            Log.e("TikuCamera", "in-app recording failed: ${event.error}")
                            AppState.toast("Couldn't record that video — please try again")
                        }
                        else -> {
                            scope.launch(Dispatchers.IO) {
                                val poster = recordedVideoPoster(file)
                                withContext(Dispatchers.Main) {
                                    if (live) pendingVideo = file to poster
                                    else runCatching { file.delete() }
                                }
                            }
                        }
                    }
                }
            }
        }.onFailure {
            recActive = false
            activeRecording = null
            Log.e("TikuCamera", "in-app recording start failed", it)
            if (live) AppState.toast("Couldn't record that video — please try again")
        }
    }

    // The one-time microphone ask from the record tap: a grant starts
    // the recording WITH sound; a denial starts it silently — the
    // video is never blocked on the mic.
    val askMicForVideo = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { startRecording() }

    BackHandler {
        when {
            recActive -> runCatching { activeRecording?.stop() }
            pending != null -> {
                runCatching { pending?.first?.delete() }
                pending = null
            }
            pendingVideo != null -> {
                runCatching { pendingVideo?.first?.delete() }
                pendingVideo = null
            }
            else -> onCancel()
        }
    }

    val stagedVideo = pendingVideo
    if (stagedVideo != null) {
        CameraConfirmView(
            bmp = stagedVideo.second
                ?: Bitmap.createBitmap(720, 960, Bitmap.Config.ARGB_8888),
            useLabel = "Use video",
            showLooks = true,
            looks = looks,
            onLooks = { looks = it },
            onRetake = {
                runCatching { stagedVideo.first.delete() }
                pendingVideo = null
            },
            onUse = {
                onUseVideo(
                    stagedVideo.first,
                    recordedVideoDurationSec(stagedVideo.first),
                    looks,
                )
            },
        )
        return
    }

    val staged = pending
    if (staged != null) {
        CameraConfirmView(
            bmp = staged.second,
            useLabel = "Use photo",
            showLooks = true,
            looks = looks,
            onLooks = { looks = it },
            onRetake = {
                runCatching { staged.first.delete() }
                pending = null
            },
            onUse = { onUsePhoto(staged.first, staged.second, looks) },
        )
        return
    }

    CameraChrome(
        flashMode = flashMode,
        hasFlash = hasFlash,
        capturing = capturing,
        onClose = onCancel,
        onFlash = {
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }
        },
        onShutter = {
            if (mode == CamMode.VIDEO) {
                if (recActive) {
                    runCatching { activeRecording?.stop() }
                } else if (ContextCompat.checkSelfPermission(
                        ctx, android.Manifest.permission.RECORD_AUDIO,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED && !micAsked
                ) {
                    micAsked = true
                    runCatching {
                        askMicForVideo.launch(android.Manifest.permission.RECORD_AUDIO)
                    }.onFailure { startRecording() }
                } else {
                    startRecording()
                }
                return@CameraChrome
            }
            val capture = imageCapture
            if (!capturing && capture != null) {
                capturing = true
                runCatching {
                    val file = File(ctx.cacheDir, "cap_inapp_${System.currentTimeMillis()}.jpg")
                    capture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(ctx),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                scope.launch(Dispatchers.IO) {
                                    val bmp = decodeCameraCapture(file, mirrorHorizontally = frontLens)
                                    withContext(Dispatchers.Main) {
                                        capturing = false
                                        when {
                                            !live -> runCatching { file.delete() }
                                            bmp != null -> pending = file to bmp
                                            else -> {
                                                runCatching { file.delete() }
                                                AppState.toast("Couldn't send that photo — please try again")
                                            }
                                        }
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                capturing = false
                                runCatching { file.delete() }
                                Log.e("TikuCamera", "in-app capture failed", exception)
                                if (live) AppState.toast("Couldn't take that photo — please try again")
                            }
                        },
                    )
                }.onFailure {
                    capturing = false
                    Log.e("TikuCamera", "in-app capture start failed", it)
                    if (live) AppState.toast("Couldn't take that photo — please try again")
                }
            }
        },
        onFlip = { frontLens = !frontLens },
        focusPoint = focusPoint,
        videoMode = mode == CamMode.VIDEO,
        recording = recActive,
        recElapsedSec = recElapsedSec,
        recLimitSec = recLimitSec,
        onModeToggle = { video -> mode = if (video) CamMode.VIDEO else CamMode.PHOTO },
        onLimitPick = { recLimitSec = it },
        preview = {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { c ->
                        PreviewView(c).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    update = { view -> previewView = view },
                    onRelease = { view -> if (previewView === view) previewView = null },
                    modifier = Modifier.fillMaxSize(),
                )
                // Tap-to-focus layer — sits above the preview surface
                // but UNDER the chrome bars (they compose after the
                // preview slot), so it never eats a control tap.
                Box(
                    Modifier.fillMaxSize().pointerInput(previewView, camera) {
                        detectTapGestures { offset ->
                            val pv = previewView ?: return@detectTapGestures
                            val cam = camera ?: return@detectTapGestures
                            runCatching {
                                val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                                val action = FocusMeteringAction.Builder(
                                    point, FocusMeteringAction.FLAG_AF,
                                ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                cam.cameraControl.startFocusAndMetering(action)
                                focusPoint = offset
                            }.onFailure {
                                Log.w("TikuCamera", "tap-to-focus failed", it)
                            }
                        }
                    },
                )
            }
        },
    )
}
