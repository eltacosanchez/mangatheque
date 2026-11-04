package fr.mangatheque.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import fr.mangatheque.app.data.Images
import kotlinx.coroutines.launch
import java.io.File

// Le cadre de la couverture : 2:3, centré, 86 % de la hauteur de l'aperçu 3:4.
private const val FRAME_H = 0.86f
private const val FRAME_W = FRAME_H * (2f / 3f) * (4f / 3f) // largeur relative dans un aperçu 3:4

/**
 * Prise de photo d'une couverture : l'utilisateur aligne le livre dans le cadre,
 * l'image est recadrée automatiquement sur le cadre puis resserrée sur la couverture.
 */
@Composable
fun CoverCameraScreen(onBack: () -> Unit, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askPermission.launch(Manifest.permission.CAMERA) }

    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
            .build()
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
                Text("Place la couverture dans le cadre", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(48.dp))
            }
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                if (granted) {
                    AndroidView(
                        factory = { ctx ->
                            val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                val provider = future.get()
                                val preview = Preview.Builder()
                                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
                                    .build().also { it.surfaceProvider = view.surfaceProvider }
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                            }, ContextCompat.getMainExecutor(ctx))
                            view
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Masque + cadre 2:3
                Canvas(Modifier.fillMaxSize()) {
                    val fw = size.width * FRAME_W
                    val fh = size.height * FRAME_H
                    val left = (size.width - fw) / 2
                    val top = (size.height - fh) / 2
                    val dim = Color.Black.copy(alpha = 0.55f)
                    drawRect(dim, Offset(0f, 0f), Size(size.width, top))
                    drawRect(dim, Offset(0f, top + fh), Size(size.width, size.height - top - fh))
                    drawRect(dim, Offset(0f, top), Size(left, fh))
                    drawRect(dim, Offset(left + fw, top), Size(size.width - left - fw, fh))
                    drawRoundRect(Color.White, Offset(left, top), Size(fw, fh), androidx.compose.ui.geometry.CornerRadius(18f, 18f), style = Stroke(width = 5f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Pose le manga à plat, remplis bien le cadre. Le recadrage est automatique.",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp)) }
            Spacer(Modifier.height(16.dp))
            if (busy) CircularProgressIndicator(color = Color.White)
            else if (granted) FilledIconButton(
                onClick = {
                    busy = true; error = null
                    val tmp = File(context.cacheDir, "cover_raw_${System.currentTimeMillis()}.jpg")
                    val opts = ImageCapture.OutputFileOptions.Builder(tmp).build()
                    capture.takePicture(opts, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            scope.launch {
                                val l = (1f - FRAME_W) / 2f
                                val t = (1f - FRAME_H) / 2f
                                val f = Images.cropToFrame(context, tmp, RectF(l, t, l + FRAME_W, t + FRAME_H))
                                busy = false
                                if (f == null) error = "Impossible de traiter la photo, réessaie." else onCaptured(f)
                            }
                        }
                        override fun onError(exception: ImageCaptureException) { busy = false; error = "Échec de la photo : ${exception.message}" }
                    })
                },
                modifier = Modifier.size(78.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)
            ) { Icon(Icons.Rounded.PhotoCamera, "Prendre la photo", Modifier.size(34.dp)) }
            else Button(onClick = { askPermission.launch(Manifest.permission.CAMERA) }) { Text("Autoriser l'appareil photo") }
        }
    }
}
