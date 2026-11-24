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
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
 *  [focusPoint], when set, draws the tap-to-focus ring at that point. */
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
    preview: @Composable () -> Unit,
) {
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
            if (hasFlash) {
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
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 34.dp),
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
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CameraChip(tag = "camera_flip", onClick = onFlip) {
                    Icon(
                        Icons.Filled.FlipCameraAndroid, null,
                        tint = Color.White, modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** ROUND 83: the capture confirm screen — the staged photo, Retake
 *  (ghost pill) / Use photo (rose-gradient pill). Pure UI. */
@Composable
internal fun CameraConfirmView(
    bmp: Bitmap,
    onRetake: () -> Unit,
    onUse: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("camera_confirm")) {
        Image(
            bmp.asImageBitmap(), null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().padding(20.dp),
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
                    .clickable { onUse() }
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(
                    "Use photo", color = Color.White, fontSize = 15.sp,
                    fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** ROUND 83: the live in-app camera overlay. Binds Preview +
 *  ImageCapture only, captures to app cache, decodes off the main
 *  thread, and hands the CONFIRMED photo to [onUsePhoto] as
 *  (cache file, upright bitmap) — the caller sends it through the
 *  existing pipeline and deletes the file. Any bind failure routes
 *  to [onBindFailed] (the caller's graceful toast + gallery surface);
 *  no exception escapes composition. */
@Composable
fun CameraCaptureOverlay(
    onCancel: () -> Unit,
    onUsePhoto: (file: File, bmp: Bitmap) -> Unit,
    onBindFailed: (Throwable) -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var frontLens by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var hasFlash by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<File, Bitmap>?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // `live` gates every async callback: once this overlay leaves
    // composition, a late capture result deletes its file and stops.
    var live by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        live = true
        onDispose { live = false }
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

    // (Re)bind on provider / preview surface / lens change.
    LaunchedEffect(provider, previewView, frontLens) {
        val p = provider ?: return@LaunchedEffect
        val pv = previewView ?: return@LaunchedEffect
        runCatching {
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(pv.surfaceProvider)
            val selector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1440),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                    ),
                )
                .build()
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(selector)
                .setFlashMode(flashMode)
                .build()
            p.unbindAll()
            val cam = p.bindToLifecycle(
                lifecycleOwner,
                if (frontLens) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA,
                preview, capture,
            )
            imageCapture = capture
            camera = cam
            hasFlash = runCatching { cam.cameraInfo.hasFlashUnit() }.getOrDefault(false)
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

    BackHandler {
        val p = pending
        if (p != null) {
            runCatching { p.first.delete() }
            pending = null
        } else {
            onCancel()
        }
    }

    val staged = pending
    if (staged != null) {
        CameraConfirmView(
            bmp = staged.second,
            onRetake = {
                runCatching { staged.first.delete() }
                pending = null
            },
            onUse = { onUsePhoto(staged.first, staged.second) },
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
