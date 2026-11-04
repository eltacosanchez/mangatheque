package fr.mangatheque.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Size
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import fr.mangatheque.app.data.BookInfo
import fr.mangatheque.app.data.BookLookup
import fr.mangatheque.app.data.FrenchTranslator
import fr.mangatheque.app.data.MangaDb
import fr.mangatheque.app.data.MangaInfo
import fr.mangatheque.app.ui.components.Pill
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import fr.mangatheque.app.data.Series
import fr.mangatheque.app.data.Volume
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.components.ranges
import fr.mangatheque.app.ui.theme.extra
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Nombre de lectures identiques exigées avant de valider un code (évite les mauvaises lectures). */
private const val REQUIRED_HITS = 4
/** Durée minimale pendant laquelle le code doit rester lisible (ms). */
private const val MIN_STABLE_MS = 450L
private const val FRAME_HEIGHT_DP = 170

/**
 * Scanner de code-barres ISBN (EAN-13).
 * mode = "add"   : on part vers la fiche d'ajout.
 * mode = "check" : mode magasin, on dit juste si on l'a déjà (et quels tomes manquent).
 */
@OptIn(ExperimentalGetImage::class)
@SuppressLint("ClickableViewAccessibility")
@Composable
fun ScanScreen(vm: AppViewModel, mode: String, onBack: () -> Unit, onIsbn: (String) -> Unit, onOpenSeries: (Long) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val onIsbnLatest by rememberUpdatedState(onIsbn)
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askPermission.launch(Manifest.permission.CAMERA) }

    var manual by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var torch by remember { mutableStateOf(false) }
    var zoom2x by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // État de la lecture : on attend plusieurs lectures identiques avant de valider.
    var candidate by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableIntStateOf(0) }
    var firstSeen by remember { mutableLongStateOf(0L) }
    var lastSeen by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var accepted by remember { mutableStateOf<String?>(null) }
    var checkResult by remember { mutableStateOf<CheckResult?>(null) }
    var tip by remember { mutableStateOf<String?>(null) }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val main = remember { Handler(Looper.getMainLooper()) }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).build()) }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close(); main.removeCallbacksAndMessages(null) } }

    fun accept(code: String) {
        accepted = code
        vibrate(context)
        if (mode == "add") onIsbnLatest(code)
    }

    fun onFrame(code: String?) {
        if (accepted != null || manual) return
        val now = SystemClock.elapsedRealtime()
        if (code == null) {
            if (now - lastSeen > 700) { candidate = null; hits = 0 }
            return
        }
        if (code != candidate) { candidate = code; hits = 1; firstSeen = now } else hits++
        lastSeen = now
        if (hits >= REQUIRED_HITS && now - firstSeen >= MIN_STABLE_MS) accept(code)
    }

    fun restart() {
        candidate = null; hits = 0; accepted = null; checkResult = null
        lastSeen = SystemClock.elapsedRealtime()
    }

    // Conseils si rien n'est lu au bout de quelques secondes.
    LaunchedEffect(accepted) {
        tip = null
        while (accepted == null) {
            delay(1000)
            val idle = SystemClock.elapsedRealtime() - lastSeen
            tip = when {
                idle > 12_000 -> "Code abîmé ? Tape l'ISBN (⌨)"
                idle > 8_000 -> "Touche l'écran pour la mise au point"
                idle > 4_500 -> if (zoom2x) "Livre à 20 cm, à plat, éclairé" else "Rapproche le livre"
                else -> null
            }
        }
    }

    // Zoom ×2 par défaut : on n'a pas besoin de coller le téléphone au livre (sinon c'est flou).
    LaunchedEffect(camera, zoom2x) {
        camera?.let { c ->
            val max = c.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
            c.cameraControl.setZoomRatio(if (zoom2x) minOf(2f, max) else 1f)
        }
    }

    // Mode magasin : recherche locale à partir de l'ISBN validé
    LaunchedEffect(accepted, mode) {
        val code = accepted ?: return@LaunchedEffect
        if (mode != "check") return@LaunchedEffect
        val owned = vm.repo.findVolumeByIsbn(code)
        val info = if (owned == null) runCatching { BookLookup.lookup(code) }.getOrNull() else null
        val series = owned?.second ?: vm.repo.guessSeries(code, info?.seriesTitle)
        val full = series?.let { vm.repo.seriesDao.getOne(it.id) }
        var r = CheckResult(
            isbn = code,
            ownedVolume = owned?.first,
            series = series,
            missing = full?.missingNumbers ?: emptyList(),
            ownedNumbers = full?.ownedNumbers?.sorted() ?: emptyList(),
            title = info?.seriesTitle ?: owned?.second?.title,
            number = info?.volumeNumber ?: owned?.first?.number,
            book = info,
            summary = info?.description?.takeIf { it.isNotBlank() }
        )
        checkResult = r
        // Fiche complète (genres, statut, auteurs, résumé traduit si besoin) pour un tome que je n'ai pas.
        if (owned != null) return@LaunchedEffect
        val title = info?.seriesTitle?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val manga = MangaDb.search(title).getOrNull() ?: return@LaunchedEffect
        r = r.copy(manga = manga, translating = r.summary == null && manga.synopsisEn != null)
        checkResult = r
        if (r.translating) checkResult = r.copy(summary = FrenchTranslator.toFrench(manga.synopsisEn!!), translating = false)
    }

    val progress by animateFloatAsState(
        when {
            accepted != null -> 1f
            else -> (hits.toFloat() / REQUIRED_HITS).coerceIn(0f, 1f)
        }, label = "lecture"
    )
    val ok = MaterialTheme.extra.ok
    val reading = MaterialTheme.extra.doing
    val frameColor = when {
        accepted != null -> ok
        hits > 0 -> reading
        else -> Color.White
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val frameTop = (maxHeight - FRAME_HEIGHT_DP.dp) / 2
        if (granted) {
            AndroidView(
                factory = { ctx ->
                    val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                    // Toucher l'écran = mise au point à cet endroit
                    view.setOnTouchListener { v, ev ->
                        if (ev.action == MotionEvent.ACTION_UP) {
                            val point = view.meteringPointFactory.createPoint(ev.x, ev.y)
                            camera?.cameraControl?.startFocusAndMetering(
                                FocusMeteringAction.Builder(point).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
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
                            // Les coordonnées ML Kit sont celles de l'image remise à l'endroit.
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
                                        // On ne garde que le code situé dans le cadre (pas celui du livre d'à côté).
                                        if (fx !in 0.12f..0.88f || fy !in 0.28f..0.72f) null else raw to (abs(fx - 0.5f) + abs(fy - 0.5f))
                                    }.minByOrNull { it.second }?.first
                                    main.post { onFrame(best) }
                                }
                                .addOnFailureListener { main.post { onFrame(null) } }
                                .addOnCompleteListener { proxy.close() }
                        }
                        provider.unbindAll()
                        val cam = provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        cam.cameraControl.startFocusAndMetering(
                            FocusMeteringAction.Builder(SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)).build()
                        )
                        camera = cam
                    }, ContextCompat.getMainExecutor(ctx))
                    view
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Autorise l'appareil photo", color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { askPermission.launch(Manifest.permission.CAMERA) }) { Text("Autoriser l'appareil photo") }
            }
        }

        // Masque assombri + coins du cadre de visée (couleur = état de la lecture)
        Canvas(Modifier.fillMaxSize()) {
            val fw = size.width * 0.82f
            val fh = FRAME_HEIGHT_DP.dp.toPx()
            val left = (size.width - fw) / 2
            val top = (size.height - fh) / 2
            val dim = Color.Black.copy(alpha = 0.5f)
            drawRect(dim, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, top))
            drawRect(dim, Offset(0f, top + fh), androidx.compose.ui.geometry.Size(size.width, size.height - top - fh))
            drawRect(dim, Offset(0f, top), androidx.compose.ui.geometry.Size(left, fh))
            drawRect(dim, Offset(left + fw, top), androidx.compose.ui.geometry.Size(size.width - left - fw, fh))
            val len = 34.dp.toPx()
            val sw = 6.dp.toPx()
            val corners = listOf(
                Offset(left, top) to Offset(1f, 1f), Offset(left + fw, top) to Offset(-1f, 1f),
                Offset(left, top + fh) to Offset(1f, -1f), Offset(left + fw, top + fh) to Offset(-1f, -1f)
            )
            corners.forEach { (p, d) ->
                drawLine(frameColor, p, Offset(p.x + d.x * len, p.y), sw, StrokeCap.Round)
                drawLine(frameColor, p, Offset(p.x, p.y + d.y * len), sw, StrokeCap.Round)
            }
            // Ligne de visée
            if (accepted == null) drawLine(Color.Red.copy(alpha = 0.7f), Offset(left + 16.dp.toPx(), top + fh / 2), Offset(left + fw - 16.dp.toPx(), top + fh / 2), 2.dp.toPx())
        }

        // Jauge de lecture + consignes, sous le cadre
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = frameTop + FRAME_HEIGHT_DP.dp + 14.dp).fillMaxWidth(0.82f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { progress }, color = frameColor, trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    accepted != null -> "✓ Code lu !"
                    hits > 0 -> "Ne bouge plus…"
                    mode == "check" -> "Scanne le code-barres"
                    else -> "Code-barres dans le cadre"
                },
                color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            tip?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }

        Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(99.dp)).border(1.5.dp, Color.White, RoundedCornerShape(99.dp))
                        .clickable { zoom2x = !zoom2x }.padding(horizontal = 12.dp, vertical = 6.dp)
                ) { Text(if (zoom2x) "×2" else "×1", color = Color.White, fontWeight = FontWeight.Bold) }
                IconButton(onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) }) { Icon(if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, "Lampe", tint = Color.White) }
                IconButton(onClick = { manual = true }) { Icon(Icons.Rounded.Keyboard, "Saisir l'ISBN", tint = Color.White) }
            }
        }

        // Panneau du bas : chargement, puis fiche du manga (mode magasin)
        val code = accepted
        if (code != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth()
                    .heightIn(max = maxHeight * 0.75f)
                    .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                val r = checkResult
                if (mode != "check" || r == null) {
                    Text("✓ Code lu", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.extra.ok)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    StoreSheet(r, onAdd = { onIsbnLatest(r.isbn) }, onSeries = { r.series?.let { onOpenSeries(it.id) } }, onAgain = { restart() })
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
                    val c = BookLookup.normalizeIsbn(manualText)
                    if (BookLookup.isValidIsbn(c)) { manual = false; accept(c) } else vm.toast("ISBN invalide")
                }) { Text("Rechercher") }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Annuler") } }
        )
    }
}

/** Petite vibration de confirmation quand un code-barres est validé. */
private fun vibrate(context: Context) {
    runCatching {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

data class CheckResult(
    val isbn: String,
    val ownedVolume: Volume?,
    val series: Series?,
    val missing: List<Int>,
    val ownedNumbers: List<Int>,
    val title: String?,
    val number: Int?,
    val book: BookInfo? = null,
    val manga: MangaInfo? = null,
    val summary: String? = null,
    val translating: Boolean = false
)

/** Fiche du manga scanné en magasin : est-ce que je l'ai, et sinon de quoi ça parle. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreSheet(r: CheckResult, onAdd: () -> Unit, onSeries: () -> Unit, onAgain: () -> Unit) {
    val n = r.number
    val have = r.ownedVolume != null || (n != null && n in r.ownedNumbers)
    // Verdict en tête, en gros
    Text(
        if (have) "✅ Déjà à toi" else "🛒 Tu ne l'as pas",
        style = MaterialTheme.typography.headlineSmall,
        color = if (have) MaterialTheme.extra.ok else MaterialTheme.colorScheme.primary
    )
    if (r.series != null) {
        Text(
            "J'ai : ${ranges(r.ownedNumbers)}" + if (r.missing.isNotEmpty()) " · Manque : ${ranges(r.missing)}" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val cover = r.book?.coverUrl ?: r.ownedVolume?.coverPath
        if (cover != null) {
            AsyncImage(
                model = if (cover.startsWith("http")) cover else java.io.File(cover), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(96.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))
                    .border(1.5.dp, MaterialTheme.extra.outline, RoundedCornerShape(8.dp))
            )
        }
        Column(Modifier.weight(1f)) {
            Text(r.title ?: "ISBN ${r.isbn}", style = MaterialTheme.typography.titleLarge)
            if (n != null) Text("Tome $n", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            val author = r.manga?.authorsText?.takeIf { it.isNotBlank() } ?: r.book?.authors
            if (!author.isNullOrBlank()) Text(author, style = MaterialTheme.typography.bodyMedium)
            r.book?.publisher?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            r.manga?.finished?.let { done ->
                Spacer(Modifier.height(6.dp))
                Pill(
                    if (done) "Terminée" + (r.manga.volumes?.let { " · $it tomes" } ?: "") else "En cours",
                    if (done) MaterialTheme.extra.ok else MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    val genres = r.manga?.genres.orEmpty()
    if (genres.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { genres.forEach { Pill(it) } }
    }

    Spacer(Modifier.height(12.dp))
    when {
        r.summary != null -> {
            var open by remember { mutableStateOf(false) }
            Text("Résumé", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                r.summary, style = MaterialTheme.typography.bodyMedium,
                maxLines = if (open) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { open = !open }
            )
        }
        r.translating -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
            Text("Traduction du résumé…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!have) Button(onClick = onAdd) { Text("Ajouter") }
        if (r.series != null) TextButton(onClick = onSeries) { Text("Série") }
        TextButton(onClick = onAgain) { Text("Autre scan") }
    }
}
