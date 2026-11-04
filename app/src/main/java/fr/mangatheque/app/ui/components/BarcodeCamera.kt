package fr.mangatheque.app.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.MotionEvent
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import fr.mangatheque.app.data.BookLookup
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Caméra qui lit les codes-barres ISBN situés dans le cadre central.
 * [onFrame] reçoit, pour chaque image analysée, le code lu (ou null).
 */
@OptIn(ExperimentalGetImage::class)
@SuppressLint("ClickableViewAccessibility")
@Composable
fun BarcodeCamera(modifier: Modifier = Modifier, zoom2x: Boolean, torch: Boolean, onFrame: (String?) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    val onFrameLatest by rememberUpdatedState(onFrame)
    var camera by remember { mutableStateOf<Camera?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val main = remember { Handler(Looper.getMainLooper()) }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).build()) }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close(); main.removeCallbacksAndMessages(null) } }

    LaunchedEffect(camera, zoom2x) {
        camera?.let { c -> c.cameraControl.setZoomRatio(if (zoom2x) minOf(2f, c.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f) else 1f) }
    }
    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }

    AndroidView(
        factory = { ctx ->
            val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            view.setOnTouchListener { v, ev ->
                if (ev.action == MotionEvent.ACTION_UP) {
                    camera?.cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(view.meteringPointFactory.createPoint(ev.x, ev.y)).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
                    )
                    v.performClick()
                }
                true
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build()
                    )
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null) { proxy.close(); return@setAnalyzer }
                    val rot = proxy.imageInfo.rotationDegrees
                    val w = (if (rot % 180 == 0) proxy.width else proxy.height).toFloat()
                    val h = (if (rot % 180 == 0) proxy.height else proxy.width).toFloat()
                    scanner.process(InputImage.fromMediaImage(media, rot))
                        .addOnSuccessListener { codes ->
                            val best = codes.mapNotNull { b ->
                                val raw = b.rawValue ?: return@mapNotNull null
                                if (raw.length != 13 || !(raw.startsWith("978") || raw.startsWith("979")) || !BookLookup.isValidIsbn(raw)) return@mapNotNull null
                                val box = b.boundingBox ?: return@mapNotNull raw to 1f
                                val fx = box.exactCenterX() / w
                                val fy = box.exactCenterY() / h
                                if (fx !in 0.12f..0.88f || fy !in 0.28f..0.72f) null else raw to (abs(fx - 0.5f) + abs(fy - 0.5f))
                            }.minByOrNull { it.second }?.first
                            main.post { onFrameLatest(best) }
                        }
                        .addOnFailureListener { main.post { onFrameLatest(null) } }
                        .addOnCompleteListener { proxy.close() }
                }
                provider.unbindAll()
                val cam = provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)).build())
                camera = cam
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
        modifier = modifier
    )
}

/** Masque assombri autour du cadre de visée, avec les coins colorés selon l'état de la lecture. */
@Composable
fun ScanFrameOverlay(frameColor: Color, frameHeight: Dp, showLine: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier) {
        val fw = size.width * 0.82f
        val fh = frameHeight.toPx()
        val left = (size.width - fw) / 2
        val top = (size.height - fh) / 2
        val dim = Color.Black.copy(alpha = 0.5f)
        drawRect(dim, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, top))
        drawRect(dim, Offset(0f, top + fh), androidx.compose.ui.geometry.Size(size.width, size.height - top - fh))
        drawRect(dim, Offset(0f, top), androidx.compose.ui.geometry.Size(left, fh))
        drawRect(dim, Offset(left + fw, top), androidx.compose.ui.geometry.Size(size.width - left - fw, fh))
        val len = 34.dp.toPx()
        val sw = 6.dp.toPx()
        listOf(
            Offset(left, top) to Offset(1f, 1f), Offset(left + fw, top) to Offset(-1f, 1f),
            Offset(left, top + fh) to Offset(1f, -1f), Offset(left + fw, top + fh) to Offset(-1f, -1f)
        ).forEach { (p, d) ->
            drawLine(frameColor, p, Offset(p.x + d.x * len, p.y), sw, StrokeCap.Round)
            drawLine(frameColor, p, Offset(p.x, p.y + d.y * len), sw, StrokeCap.Round)
        }
        if (showLine) drawLine(Color.Red.copy(alpha = 0.7f), Offset(left + 16.dp.toPx(), top + fh / 2), Offset(left + fw - 16.dp.toPx(), top + fh / 2), 2.dp.toPx())
    }
}
