package fr.mangatheque.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.material3.LinearProgressIndicator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import fr.mangatheque.app.data.BookLookup
import fr.mangatheque.app.data.Series
import fr.mangatheque.app.data.Volume
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.components.ranges
import java.util.concurrent.Executors

/**
 * Scanner de code-barres ISBN (EAN-13).
 * mode = "add"   : on part vers la fiche d'ajout.
 * mode = "check" : mode magasin, on dit juste si on l'a déjà (et quels tomes manquent).
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun ScanScreen(vm: AppViewModel, mode: String, onBack: () -> Unit, onIsbn: (String) -> Unit, onOpenSeries: (Long) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askPermission.launch(Manifest.permission.CAMERA) }

    var manual by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var torch by remember { mutableStateOf(false) }
    var lastCode by remember { mutableStateOf<String?>(null) }
    var checkResult by remember { mutableStateOf<CheckResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    // Un ISBN est toujours un EAN-13 : ne chercher que ce format rend la détection plus rapide.
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).build()) }
    var detected by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }

    fun handle(code: String) {
        if (lastCode == code || checking || detected) return
        if (!BookLookup.isValidIsbn(code)) return
        lastCode = code
        vibrate(context)
        if (mode == "check") {
            checking = true
            checkResult = null
        } else {
            detected = true
            onIsbn(code)
        }
    }

    // Mode magasin : recherche locale à partir de l'ISBN
    LaunchedEffect(lastCode, mode) {
        val code = lastCode ?: return@LaunchedEffect
        if (mode != "check") return@LaunchedEffect
        val owned = vm.repo.findVolumeByIsbn(code)
        val info = if (owned == null) runCatching { BookLookup.lookup(code) }.getOrNull() else null
        val series = owned?.second ?: vm.repo.guessSeries(code, info?.seriesTitle)
        val full = series?.let { vm.repo.seriesDao.getOne(it.id) }
        checkResult = CheckResult(
            isbn = code,
            ownedVolume = owned?.first,
            series = series,
            missing = full?.missingNumbers ?: emptyList(),
            ownedNumbers = full?.ownedNumbers?.sorted() ?: emptyList(),
            title = info?.rawTitle ?: owned?.let { "${it.second.title} · tome ${it.first.number}" },
            number = info?.volumeNumber ?: owned?.first?.number
        )
        checking = false
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                factory = { ctx ->
                    val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                        // Résolution réduite pour l'analyse : largement suffisante pour un code-barres, et bien plus rapide.
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
                            val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                            scanner.process(img)
                                .addOnSuccessListener { codes ->
                                    codes.firstOrNull { it.rawValue?.let { v -> v.startsWith("978") || v.startsWith("979") } == true }?.rawValue?.let { raw ->
                                        view.post { handle(raw) }
                                    }
                                }
                                .addOnCompleteListener { proxy.close() }
                        }
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }, ContextCompat.getMainExecutor(ctx))
                    view
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("L'appareil photo est nécessaire pour scanner le code-barres.", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { askPermission.launch(Manifest.permission.CAMERA) }) { Text("Autoriser l'appareil photo") }
            }
        }

        // Cadre de visée
        Box(Modifier.align(Alignment.Center).fillMaxWidth(0.8f).height(150.dp).border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp)))
        Text(
            if (mode == "check") "Scanne le code-barres au dos : je te dis si tu l'as déjà" else "Vise le code-barres ISBN au dos du manga",
            color = Color.White, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center).padding(top = 220.dp).padding(horizontal = 32.dp)
        )

        Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
            Row {
                IconButton(onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) }) { Icon(if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, "Lampe", tint = Color.White) }
                IconButton(onClick = { manual = true }) { Icon(Icons.Rounded.Keyboard, "Saisir l'ISBN", tint = Color.White) }
            }
        }

        if (checking || detected) {
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("✓ Code détecté", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(if (mode == "check") "Je regarde dans ta collection…" else "Recherche des infos du manga…", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        checkResult?.let { r ->
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)
            ) {
                Text(r.title ?: "ISBN ${r.isbn}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                when {
                    r.ownedVolume != null -> Text("✅ Tu l'as déjà : tome ${r.ownedVolume.number}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                    r.series != null -> {
                        val n = r.number
                        if (n != null && n in r.ownedNumbers) Text("✅ Tu as déjà le tome $n de « ${r.series.title} »", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                        else Text("🛒 Pas encore dans ta collection" + (n?.let { " (tome $it)" } ?: ""), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text("J'ai : ${ranges(r.ownedNumbers)}", style = MaterialTheme.typography.bodyMedium)
                        if (r.missing.isNotEmpty()) Text("Il me manque : ${ranges(r.missing)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    else -> Text("🛒 Tu n'as aucun tome de cette série : achat sans risque de doublon.", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.series != null) TextButton(onClick = { onOpenSeries(r.series.id) }) { Text("Voir la série") }
                    if (r.ownedVolume == null) Button(onClick = { onIsbn(r.isbn) }) { Text("Je l'achète : ajouter") }
                    TextButton(onClick = { checkResult = null; lastCode = null }) { Text("Scanner un autre") }
                }
            }
        }
    }

    if (manual) {
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text("Saisir l'ISBN") },
            text = {
                OutlinedTextField(manualText, { manualText = it }, label = { Text("ISBN (10 ou 13 chiffres)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = BookLookup.cleanIsbn(manualText)
                    if (BookLookup.isValidIsbn(c)) { manual = false; lastCode = null; if (mode == "add") onIsbn(c) else lastCode = c }
                    else vm.toast("ISBN invalide")
                }) { Text("Rechercher") }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Annuler") } }
        )
    }
}

/** Petite vibration de confirmation quand un code-barres est lu. */
private fun vibrate(context: Context) {
    runCatching {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

data class CheckResult(
    val isbn: String,
    val ownedVolume: Volume?,
    val series: Series?,
    val missing: List<Int>,
    val ownedNumbers: List<Int>,
    val title: String?,
    val number: Int?
)
