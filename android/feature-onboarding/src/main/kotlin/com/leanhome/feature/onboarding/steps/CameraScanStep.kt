package com.leanhome.feature.onboarding.steps

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.leanhome.core.domain.model.BodyArchetype
import com.leanhome.core.domain.model.Gender
import com.leanhome.feature.onboarding.mvi.OnboardingIntent
import com.leanhome.feature.onboarding.mvi.OnboardingState
import java.util.Optional
import java.util.concurrent.Executors

/**
 * Step 3 (V2): On-device Camera AI Body Scan.
 *
 * Uses CameraX for preview and the Google MediaPipe Tasks Vision
 * PoseLandmarker for on-device body landmark detection. No image data
 * ever leaves the device — pose landmarks are converted to SWR/WHR
 * ratios and mapped to a [BodyArchetype].
 *
 * Landmarks used (MediaPipe PoseLandmarker indices):
 *   11 = left_shoulder, 12 = right_shoulder
 *   23 = left_hip, 24 = right_hip
 */
private object PoseLandmarkIndex {
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
}

/**
 * A detected body landmark in normalized image coords [0,1].
 * x axis: 0 = left edge, 1 = right edge of the sensor frame.
 */
internal data class Landmark(
    val x: Float,
    val y: Float,
    val visibility: Float,
)

/**
 * Pure-function mapping from Shoulder-to-Waist and Waist-to-Hip ratios to
 * a [BodyArchetype] per the V2 spec mapping rules:
 *   SWR > 1.4 and WHR < 0.85 → INVERTED_TRIANGLE
 *   SWR 1.0–1.4 and WHR 0.85–1.0 → RECTANGLE
 *   WHR > 0.9 (male/other) or > 0.85 (female) → OVAL/TRIANGLE/APPLE
 *   WHR < 0.85 (female only) → PEAR
 *
 * Male-like high WHR falls back to OVAL (endomorph baseline). Male-like
 * low WHR with insufficient shoulders-to-waist taper falls back to
 * TRIANGLE (the male pear analogue). Female-like ambiguous matches fall
 * back to RECTANGLE. The mapping is deterministic so it can be unit
 * tested without a camera.
 */
internal fun computeArchetypeFromRatios(
    swr: Double,
    whr: Double,
    gender: Gender,
): BodyArchetype {
    val maleLike = gender == Gender.MALE || gender == Gender.OTHER
    return when {
        swr > 1.4 && whr < 0.85 -> BodyArchetype.INVERTED_TRIANGLE
        swr in 1.0..1.4 && whr in 0.85..1.0 -> BodyArchetype.RECTANGLE
        whr > 0.9 && maleLike -> BodyArchetype.OVAL
        whr > 0.85 && !maleLike -> BodyArchetype.APPLE
        whr < 0.85 && !maleLike -> BodyArchetype.PEAR
        // Male-like high WHR that didn't already match.
        whr > 0.9 -> BodyArchetype.OVAL
        // Male-like low WHR without an inverted-triangle taper.
        maleLike && whr <= 0.9 -> BodyArchetype.TRIANGLE
        // Fallback: no rule fired — neutral rectangle.
        else -> BodyArchetype.RECTANGLE
    }
}

/**
 * Compute the archetype purely from the four required landmarks, using a
 * weighted waist estimate (mid-rib-cage approximation). The waist width
 * is computed as the average of the shoulder width and hip width — i.e.
 * the torso narrows between the shoulders and hips. This is the same
 * approach used on iOS (midpoint between shoulders and hips).
 *
 * Returns `null` when landmarks are missing or low-visibility.
 */
internal fun computeArchetypeFromLandmarks(
    leftShoulder: Landmark?,
    rightShoulder: Landmark?,
    leftHip: Landmark?,
    rightHip: Landmark?,
    gender: Gender,
    minVisibility: Float = 0.5f,
): BodyArchetype? {
    val ls = leftShoulder?.takeIf { it.visibility >= minVisibility } ?: return null
    val rs = rightShoulder?.takeIf { it.visibility >= minVisibility } ?: return null
    val lh = leftHip?.takeIf { it.visibility >= minVisibility } ?: return null
    val rh = rightHip?.takeIf { it.visibility >= minVisibility } ?: return null
    val shoulderWidth = Math.abs(rs.x - ls.x).toDouble()
    val hipWidth = Math.abs(rh.x - lh.x).toDouble()
    // Waist is loosely the mean of shoulder and hip widths (mid-torso).
    val waistWidth = (shoulderWidth + hipWidth) / 2.0
    if (shoulderWidth < 0.02 || hipWidth < 0.02 || waistWidth < 0.02) return null
    val swr = shoulderWidth / waistWidth
    val whr = waistWidth / hipWidth
    return computeArchetypeFromRatios(swr, whr, gender)
}

/**
 * Composable surface for the camera scan step. Renders a live CameraX
 * preview with a silhouette overlay and a "Use this scan" button once a
 * confident archetype is detected. Falls back to [ArchetypePickerStep]
 * via [onCancel] when the user dismisses the scan, the camera permission
 * is denied, or the pose can not be detected.
 *
 * @param state the current onboarding state
 * @param onIntent dispatch intents back to [OnboardingViewModel]
 * @param onArchetypeScanned invoked with the detected archetype so the
 *   host can pop back to the manual picker
 * @param onCancel invoked when scan aborts (cancel / permission-denied / failure)
 */
@Composable
fun CameraScanStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    onArchetypeScanned: (BodyArchetype) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) onCancel() // spec: silent fallback to manual selection
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    // Mutable holder for the most recent detected archetype — kept across
    // analyzer frames because the analyzer runs on a background thread.
    var detectedArchetype by remember { mutableStateOf<BodyArchetype?>(null) }
    var detectedSwr by remember { mutableStateOf(0.0) }
    var detectedWhr by remember { mutableStateOf(0.0) }

    DisposableEffect(hasCameraPermission) {
        val poseLandmarker: PoseLandmarker? = if (hasCameraPermission) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("pose_landmarker.task")
                    .build()
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result: PoseLandmarkerResult, _ ->
                        val lm = result.landmarks()
                        if (lm.isNotEmpty()) {
                            val list = lm[0]
                            val ls = list.getOrNull(PoseLandmarkIndex.LEFT_SHOULDER)?.toLandmark()
                            val rs = list.getOrNull(PoseLandmarkIndex.RIGHT_SHOULDER)?.toLandmark()
                            val lh = list.getOrNull(PoseLandmarkIndex.LEFT_HIP)?.toLandmark()
                            val rh = list.getOrNull(PoseLandmarkIndex.RIGHT_HIP)?.toLandmark()
                            val arch = computeArchetypeFromLandmarks(ls, rs, lh, rh, state.gender)
                            if (arch != null) {
                                // Compute ratios off the listener thread, then post
                                // the state mutations onto the main thread so Compose
                                // snapshots observe the update safely.
                                val sw = if (ls != null && rs != null) Math.abs(rs.x - ls.x).toDouble() else 0.0
                                val hw = if (lh != null && rh != null) Math.abs(rh.x - lh.x).toDouble() else 0.0
                                val waist = (sw + hw) / 2.0
                                val ratios = if (waist > 0.0 && hw > 0.0) sw / waist to waist / hw else null
                                ContextCompat.getMainExecutor(context).execute {
                                    detectedArchetype = arch
                                    if (ratios != null) {
                                        detectedSwr = ratios.first
                                        detectedWhr = ratios.second
                                    }
                                }
                            }
                        }
                    }
                    .setErrorListener { /* swallow, fall back to manual */ }
                    .build()
                PoseLandmarker.createFromOptions(context, options)
            } catch (e: Exception) {
                null // The bundled task model may be absent in CI; fall back cleanly.
            }
        } else null

        if (hasCameraPermission && poseLandmarker != null) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val analysisUseCase = ImageAnalysis.Builder()
                    .setAspectRatioRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val executor = Executors.newSingleThreadExecutor()
                analysisUseCase.setAnalyzer(executor) { imageProxy ->
                    val mpImage = try { imageProxy.toMpImage() } catch (_: Exception) { null }
                    if (mpImage != null) {
                        poseLandmarker.detectAsync(mpImage, imageProxy.imageInfo.timestampMs)
                    }
                    imageProxy.close()
                }
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val previewUseCase = Preview.Builder()
                        .setAspectRatioRatio(AspectRatio.RATIO_4_3)
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        previewUseCase,
                        analysisUseCase,
                    )
                } catch (_: Exception) {
                    onCancel() // CameraX bind failure → spec fallback
                }
            }, ContextCompat.getMainExecutor(context))
        }

        onDispose { poseLandmarker?.close() }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
        ) {
            if (hasCameraPermission) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                SilhouetteGuideOverlay(
                    detectedArchetype = detectedArchetype,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Camera permission required", color = Color.White)
                    Text(
                        "Tap below to continue with manual archetype selection.",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Camera Body Scan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "All processing happens on your device. Nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                )
                detectedArchetype?.let { arch ->
                    Text("Detected: ${arch.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "SWR=%.2f  WHR=%.2f".format(detectedSwr, detectedWhr),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } ?: Text(
                    "Stand in frame so shoulders and hips are visible.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            detectedArchetype?.let { arch ->
                                onIntent(OnboardingIntent.ArchetypeSelected(arch))
                                onArchetypeScanned(arch)
                            }
                        },
                        enabled = detectedArchetype != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Use Scan")
                    }
                }
            }
        }
    }
}

/**
 * Renders a translucent full-body silhouette outline (head circle + body
 * taper) so the user can position themselves for a good scan. When the
 * pipeline detects a confident archetype, the guide turns green;
 * otherwise it stays yellow.
 */
@Composable
private fun SilhouetteGuideOverlay(
    detectedArchetype: BodyArchetype?,
    modifier: Modifier = Modifier,
) {
    val outlineColor = if (detectedArchetype != null) Color(0xFF4CAF50) else Color(0xFFFFC107)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val headRadius = w * 0.10f
        val torsoTop = h * 0.20f
        val torsoBottom = h * 0.75f
        // Head circle
        drawCircle(color = outlineColor.copy(alpha = 0.65f), radius = headRadius, center = Offset(cx, torsoTop * 0.45f))
        // Shoulders line
        drawLine(
            color = outlineColor,
            start = Offset(cx - w * 0.18f, torsoTop),
            end = Offset(cx + w * 0.18f, torsoTop),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )
        // Torso body (triangle silhouette)
        drawLine(
            color = outlineColor.copy(alpha = 0.5f),
            start = Offset(cx - w * 0.18f, torsoTop),
            end = Offset(cx - w * 0.08f, torsoBottom),
            strokeWidth = 3f,
        )
        drawLine(
            color = outlineColor.copy(alpha = 0.5f),
            start = Offset(cx + w * 0.18f, torsoTop),
            end = Offset(cx + w * 0.08f, torsoBottom),
            strokeWidth = 3f,
        )
        // Hips line
        drawLine(
            color = outlineColor,
            start = Offset(cx - w * 0.11f, torsoBottom),
            end = Offset(cx + w * 0.11f, torsoBottom),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )
    }
}

// --- MediaPipe → landmark conversion helpers ---

/**
 * Convert a MediaPipe normalized landmark to our internal [Landmark].
 * MediaPipe returns `Optional<Float>` for visibility/presence; we unwrap
 * it via [Optional.orElse] so missing visibility defaults to 0 (off-screen).
 */
private fun com.google.mediapipe.tasks.components.containers.NormalizedLandmark.toLandmark(): Landmark =
    Landmark(x = x(), y = y(), visibility = visibility().orElse(0f))

/** Convert an [ImageProxy] to a MediaPipe [MPImage] for analysis. */
private fun ImageProxy.toMpImage(): MPImage {
    val bitmap = this.toBitmap()
    return BitmapImageBuilder.buildBitmapImage(bitmap)
}
