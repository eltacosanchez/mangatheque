package fr.mangatheque.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.BatchItem
import fr.mangatheque.app.ui.BatchState
import fr.mangatheque.app.ui.components.BarcodeCamera
import fr.mangatheque.app.ui.components.ScanFrameOverlay
import fr.mangatheque.app.ui.components.SectionHeader
import fr.mangatheque.app.ui.components.StatCard
import fr.mangatheque.app.ui.components.mangaPanel
import fr.mangatheque.app.ui.theme.extra
import kotlinx.coroutines.delay
import java.io.File

private const val HITS = 4
private const val STABLE_MS = 450L
private const val FRAME_H = 170

/**
 * Scan multiple : on scanne les mangas à la suite, chacun est recherché et ajouté en arrière-plan.
 * « Terminer » affiche le récapitulatif.
 */
@Composable
fun BatchScanScreen(vm: AppViewModel, onClose: () -> Unit, onOpenVolume: (Long) -> Unit, onComplete: (String) -> Unit, onCollection: () -> Unit) {
    var summary by rememberSaveable { mutableStateOf(false) }
    if (summary) BatchSummary(vm, onBackToScan = { summary = false }, onOpenVolume = onOpenVolume, onComplete = onComplete, onCollection = onCollection)
    else BatchCamera(vm, onFinish = { if (vm.batch.isEmpty()) onClose() else summary = true })
}

@Composable
private fun BatchCamera(vm: AppViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onFinish)
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    var torch by remember { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableIntStateOf(0) }
    var firstSeen by remember { mutableLongStateOf(0L) }
    var lastSeen by remember { mutableLongStateOf(0L) }
    var pauseUntil by remember { mutableLongStateOf(0L) }
    var flash by remember { mutableStateOf<String?>(null) }   // message bref après une lecture
    var flashOk by remember { mutableStateOf(true) }

    fun onFrame(code: String?) {
        val now = SystemClock.elapsedRealtime()
        if (now < pauseUntil) return
        if (code == null) { if (now - lastSeen > 700) { candidate = null; hits = 0 }; return }
        if (code != candidate) { candidate = code; hits = 1; firstSeen = now } else hits++
        lastSeen = now
        if (hits >= HITS && now - firstSeen >= STABLE_MS) {
            val added = vm.batchAdd(code)
            flashOk = added
            flash = if (added) "✓ Ajouté à la liste" else "Déjà scanné"
            if (added && vm.hapticsOn) vibrateShort(context)
            candidate = null; hits = 0
            pauseUntil = now + 1200 // le temps de changer de livre
        }
    }
    LaunchedEffect(flash) { if (flash != null) { delay(1300); flash = null } }

    val progress by animateFloatAsState((hits.toFloat() / HITS).coerceIn(0f, 1f), label = "lecture")
    val frameColor = when {
        flash != null && flashOk -> MaterialTheme.extra.ok
        hits > 0 -> MaterialTheme.extra.doing
        else -> Color.White
    }
    val items = vm.batch
    val searching = items.count { it.state is BatchState.Searching }
    val added = items.count { it.state is BatchState.Added }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val frameTop = (maxHeight - FRAME_H.dp) / 2
        if (granted) BarcodeCamera(Modifier.fillMaxSize(), zoom2x = true, torch = torch, onFrame = ::onFrame)
        ScanFrameOverlay(frameColor, FRAME_H.dp, showLine = flash == null)

        Column(Modifier.align(Alignment.TopCenter).padding(top = frameTop + FRAME_H.dp + 14.dp).fillMaxWidth(0.82f), horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(
                progress = { if (flash != null) 1f else progress }, color = frameColor, trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.height(12.dp))
            Text(
                flash ?: if (hits > 0) "Ne bouge plus…" else "Scanne tes mangas à la suite",
                color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
        }

        Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFinish) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
            Text("Scan multiple", color = Color.White, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { torch = !torch }) { Icon(if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, "Lampe", tint = Color.White) }
        }

        // Panneau du bas : compteurs, derniers livres scannés, bouton Terminer
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${items.size} scanné${if (items.size > 1) "s" else ""}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (searching > 0) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp))
                    Text("$searching en recherche", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                }
                Text("$added ajouté${if (added > 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extra.ok)
            }
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items.take(20), key = { it.isbn }) { BatchThumb(it) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(if (items.isEmpty()) "Fermer" else "Terminer (${items.size})")
            }
        }
    }
}

/** Miniature d'un livre scanné, avec son état (en recherche, ajouté, déjà possédé…). */
@Composable
private fun BatchThumb(item: BatchItem) {
    val (icon, color) = stateIcon(item.state)
    Box(Modifier.width(48.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        coverOf(item.state)?.let { AsyncImage(File(it), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.align(Alignment.BottomEnd).padding(2.dp).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            if (item.state is BatchState.Searching) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            else Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun stateIcon(s: BatchState): Pair<ImageVector, Color> = when (s) {
    is BatchState.Added -> (if (s.needsCheck) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle) to (if (s.needsCheck) MaterialTheme.extra.doing else MaterialTheme.extra.ok)
    is BatchState.Owned, is BatchState.Duplicate -> Icons.Rounded.Inventory2 to MaterialTheme.extra.muted
    BatchState.NotFound -> Icons.Rounded.HelpOutline to MaterialTheme.colorScheme.primary
    is BatchState.Failed -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
    BatchState.Searching -> Icons.Rounded.HelpOutline to MaterialTheme.extra.muted
}

private fun coverOf(s: BatchState): String? = when (s) {
    is BatchState.Added -> s.cover
    is BatchState.Owned -> s.cover
    else -> null
}

/** Récapitulatif : ce qui a été ajouté, déjà possédé, introuvable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchSummary(vm: AppViewModel, onBackToScan: () -> Unit, onOpenVolume: (Long) -> Unit, onComplete: (String) -> Unit, onCollection: () -> Unit) {
    BackHandler(onBack = onCollection)
    val items = vm.batch
    val added = items.filter { it.state is BatchState.Added }
    val owned = items.filter { it.state is BatchState.Owned || it.state is BatchState.Duplicate }
    val problems = items.filter { it.state is BatchState.NotFound || it.state is BatchState.Failed }
    val searching = items.filter { it.state is BatchState.Searching }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Récapitulatif", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onCollection) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("${added.size}", "ajoutés", Modifier.weight(1f), color = MaterialTheme.extra.ok)
                    StatCard("${owned.size}", "déjà à toi", Modifier.weight(1f))
                    StatCard("${problems.size}", "à compléter", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                }
                if (searching.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                        Text("${searching.size} encore en recherche…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCollection, modifier = Modifier.weight(1f)) { Text("Voir ma collection") }
                    OutlinedButton(onClick = onBackToScan, modifier = Modifier.weight(1f)) { Text("Scanner encore") }
                }
            }
            if (problems.isNotEmpty()) {
                item { SectionHeader("À compléter", modifier = Modifier.padding(top = 12.dp)) }
                items(problems, key = { it.isbn }) { BatchRow(it, vm, onOpenVolume, onComplete) }
            }
            if (added.isNotEmpty()) {
                item { SectionHeader("Ajoutés", modifier = Modifier.padding(top = 12.dp)) }
                items(added, key = { it.isbn }) { BatchRow(it, vm, onOpenVolume, onComplete) }
            }
            if (owned.isNotEmpty()) {
                item { SectionHeader("Déjà dans ta collection", modifier = Modifier.padding(top = 12.dp)) }
                items(owned, key = { it.isbn }) { BatchRow(it, vm, onOpenVolume, onComplete) }
            }
            if (searching.isNotEmpty()) {
                item { SectionHeader("En recherche", modifier = Modifier.padding(top = 12.dp)) }
                items(searching, key = { it.isbn }) { BatchRow(it, vm, onOpenVolume, onComplete) }
            }
        }
    }
}

@Composable
private fun BatchRow(item: BatchItem, vm: AppViewModel, onOpenVolume: (Long) -> Unit, onComplete: (String) -> Unit) {
    val s = item.state
    val (title, detail) = when (s) {
        is BatchState.Added -> "${s.title} · T${s.number}" to if (s.needsCheck) "Ajouté · numéro à vérifier" else "Ajouté"
        is BatchState.Owned -> "${s.title} · T${s.number}" to "Déjà dans ta collection"
        is BatchState.Duplicate -> "${s.title} · T${s.number}" to "Ce tome est déjà présent"
        BatchState.NotFound -> "ISBN ${item.isbn}" to "Introuvable en ligne"
        is BatchState.Failed -> "ISBN ${item.isbn}" to s.message
        BatchState.Searching -> "ISBN ${item.isbn}" to "Recherche…"
    }
    Row(
        Modifier.fillMaxWidth().mangaPanel(corner = 16.dp)
            .then(if (s is BatchState.Added) Modifier.clickable { onOpenVolume(s.volumeId) } else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BatchThumb(item)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = stateIcon(s).second)
        }
        when (s) {
            BatchState.NotFound -> TextButton(onClick = { onComplete(item.isbn) }) { Text("Compléter") }
            is BatchState.Failed -> TextButton(onClick = { vm.batchRetry(item.isbn) }) { Text("Réessayer") }
            else -> {}
        }
    }
}

private fun vibrateShort(context: android.content.Context) {
    runCatching {
        val v = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        v.vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
