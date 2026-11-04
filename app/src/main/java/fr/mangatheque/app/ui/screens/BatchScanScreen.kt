package fr.mangatheque.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import fr.mangatheque.app.ui.components.Pill
import fr.mangatheque.app.ui.components.bounceClick
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import fr.mangatheque.app.ui.components.ScanFrameOverlay
import fr.mangatheque.app.ui.theme.extra
import kotlinx.coroutines.delay
import java.io.File

private const val HITS = 4
private const val STABLE_MS = 450L
private const val FRAME_H = 170

/**
 * Scan multiple : on scanne une pile de mangas, l'appli prépare chaque fiche en arrière-plan.
 * « Terminer » → page de vérification (on peut ouvrir chaque fiche, retirer un livre) → « Valider » enregistre tout.
 */
@Composable
fun BatchScanScreen(vm: AppViewModel, onClose: () -> Unit, onComplete: (String) -> Unit, onCollection: () -> Unit) {
    var review by rememberSaveable { mutableStateOf(false) }
    if (review) BatchReview(vm, onBackToScan = { review = false }, onComplete = onComplete, onCollection = onCollection)
    else BatchCamera(vm, onFinish = { if (vm.batch.isEmpty()) onClose() else review = true })
}

// ------------------------------------------------------------------ Caméra

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
    var flash by remember { mutableStateOf<String?>(null) }

    fun onFrame(code: String?) {
        val now = SystemClock.elapsedRealtime()
        if (now < pauseUntil) return
        if (code == null) { if (now - lastSeen > 700) { candidate = null; hits = 0 }; return }
        if (code != candidate) { candidate = code; hits = 1; firstSeen = now } else hits++
        lastSeen = now
        if (hits >= HITS && now - firstSeen >= STABLE_MS) {
            val added = vm.batchAdd(code)
            flash = if (added) "✓ Lu" else "Déjà scanné"
            if (added && vm.hapticsOn) vibrateShort(context)
            candidate = null; hits = 0
            pauseUntil = now + 1200 // le temps de changer de livre
        }
    }
    LaunchedEffect(flash) { if (flash != null) { delay(1200); flash = null } }

    val progress by animateFloatAsState((hits.toFloat() / HITS).coerceIn(0f, 1f), label = "lecture")
    val frameColor = when {
        flash != null -> MaterialTheme.extra.ok
        hits > 0 -> MaterialTheme.extra.doing
        else -> Color.White
    }
    val items = vm.batch

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val frameTop = (maxHeight - FRAME_H.dp) / 2
        if (granted) BarcodeCamera(Modifier.fillMaxSize(), zoom2x = true, torch = torch, onFrame = ::onFrame)
        ScanFrameOverlay(frameColor, FRAME_H.dp, showLine = flash == null)

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = frameTop + FRAME_H.dp + 14.dp).fillMaxWidth(0.82f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

        Row(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onFinish) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
            Text("Scan multiple", color = Color.White, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { torch = !torch }) { Icon(if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, "Lampe", tint = Color.White) }
        }

        // Barre du bas : dernier livre lu (« Ajout : One Piece · T50 »), compteur, bouton Terminer
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)
        ) {
            val last = items.firstOrNull()
            if (last != null) {
                AnimatedContent(
                    targetState = last, label = "dernier",
                    contentKey = { it.isbn to it.state::class },
                    transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith fadeOut() }
                ) { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(item, Modifier.width(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lineTitle(item), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(lineState(item.state), style = MaterialTheme.typography.bodySmall, color = stateIcon(item.state).second, maxLines = 1)
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("×${items.size}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(if (items.isEmpty()) "Fermer" else "Terminer les scans")
            }
        }
    }
}

// ------------------------------------------------------------------ Vérification

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BatchReview(vm: AppViewModel, onBackToScan: () -> Unit, onComplete: (String) -> Unit, onCollection: () -> Unit) {
    BackHandler(onBack = onBackToScan)
    val items = vm.batch
    val ready = items.count { it.state is BatchState.Ready }
    val searching = items.count { it.state is BatchState.Searching }
    val owned = items.count { it.state is BatchState.Owned || it.state is BatchState.Duplicate }
    val missing = items.count { it.state is BatchState.NotFound || it.state is BatchState.Failed }
    val saved = items.count { it.state is BatchState.Saved }
    var open by remember { mutableStateOf<BatchItem?>(null) }
    val saving = vm.batchSaving
    // Après « Valider » : écran de réussite (nombre enregistré)
    var success by rememberSaveable { mutableStateOf<Int?>(null) }
    success?.let { n ->
        BatchSuccess(
            saved = n, covers = items.mapNotNull { (it.state as? BatchState.Saved)?.cover },
            left = missing, onCollection = onCollection, onSeeLeft = { success = null }, onScanMore = { success = null; onBackToScan() }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Vérifie avant d'enregistrer", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBackToScan) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Continuer à scanner") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(16.dp)) {
                if (saving != null) {
                    Text("Enregistrement… ${saving.first + 1} / ${saving.second}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { (saving.first + 1f) / saving.second }, modifier = Modifier.fillMaxWidth())
                } else if (ready > 0 || searching > 0) {
                    Button(
                        onClick = { vm.batchValidate { n, _ -> success = n } },
                        enabled = ready > 0, shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(8.dp))
                        Text(
                            if (searching > 0) "Valider $ready livre${if (ready > 1) "s" else ""} · $searching en recherche" else "Valider $ready livre${if (ready > 1) "s" else ""}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    TextButton(onClick = onBackToScan, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Continuer à scanner") }
                } else {
                    Button(onClick = onCollection, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Voir ma bibliothèque") }
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                // En-tête : la pile en un coup d'œil
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${items.size}", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("livre${if (items.size > 1) "s" else ""} scanné${if (items.size > 1) "s" else ""}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (ready > 0) Pill("$ready à ajouter", MaterialTheme.colorScheme.primary)
                        if (saved > 0) Pill("$saved enregistré${if (saved > 1) "s" else ""}", MaterialTheme.extra.ok)
                        if (owned > 0) Pill("$owned déjà à toi", MaterialTheme.extra.muted)
                        if (missing > 0) Pill("$missing à compléter", MaterialTheme.extra.doing)
                        if (searching > 0) Pill("$searching en recherche", MaterialTheme.extra.muted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Touche un livre pour voir sa fiche.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(items, key = { it.isbn }) { item ->
                Column(Modifier.bounceClick { open = item }) {
                    Box {
                        Thumb(item, Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(10.dp)))
                        tagOf(item.state)?.let { (label, color) ->
                            Text(
                                label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White,
                                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(99.dp)).background(color).padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(titleOf(item), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(numberOf(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }

    open?.let { item ->
        // On relit l'élément à jour (sa recherche a pu se terminer depuis).
        val current = items.firstOrNull { it.isbn == item.isbn } ?: run { open = null; return }
        ModalBottomSheet(onDismissRequest = { open = null }) {
            BookSheet(
                current,
                onRemove = { vm.batchRemove(current.isbn); open = null },
                onComplete = { open = null; onComplete(current.isbn) },
                onRetry = { vm.batchRetry(current.isbn) }
            )
        }
    }
}

/** Fiche d'un livre scanné, avant enregistrement. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookSheet(item: BatchItem, onRemove: () -> Unit, onComplete: () -> Unit, onRetry: () -> Unit) {
    val s = item.state
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Thumb(item, Modifier.width(110.dp))
            Column(Modifier.weight(1f)) {
                Text(lineTitle(item), style = MaterialTheme.typography.headlineSmall)
                if (s is BatchState.Ready) {
                    val author = s.manga?.authorsText?.takeIf { it.isNotBlank() } ?: s.book.authors
                    if (author.isNotBlank()) Text(author, style = MaterialTheme.typography.bodyMedium)
                    if (s.book.publisher.isNotBlank()) Text(s.book.publisher, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Pill(if (s.knownSeries) "Dans ta série" else "Nouvelle série", if (s.knownSeries) MaterialTheme.extra.ok else MaterialTheme.colorScheme.primary)
                    if (s.needsCheck) { Spacer(Modifier.height(4.dp)); Pill("Numéro à vérifier", MaterialTheme.extra.doing) }
                } else {
                    Text(lineState(s), style = MaterialTheme.typography.bodyMedium, color = stateIcon(s).second)
                }
                Text("ISBN ${item.isbn}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (s is BatchState.Ready) {
            val genres = s.manga?.genres.orEmpty()
            if (genres.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { genres.forEach { Pill(it) } }
            }
            if (s.book.description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("Résumé", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.book.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (s) {
                BatchState.NotFound -> Button(onClick = onComplete) { Text("Compléter à la main") }
                is BatchState.Failed -> Button(onClick = onRetry) { Text("Réessayer") }
                else -> {}
            }
            if (s !is BatchState.Saved) OutlinedButton(onClick = onRemove) { Text("Retirer de la liste") }
        }
    }
}

// ------------------------------------------------------------------ Petits éléments

/** Couverture d'un livre scanné, avec une pastille d'état. */
@Composable
private fun Thumb(item: BatchItem, modifier: Modifier) {
    val (icon, color) = stateIcon(item.state)
    Box(modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        coverOf(item.state)?.let { c ->
            AsyncImage(if (c.startsWith("http")) c else File(c), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (item.state is BatchState.Searching) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            else Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        }
    }
}

private fun lineTitle(item: BatchItem): String = when (val s = item.state) {
    is BatchState.Ready -> "${s.seriesTitle} · T${s.number}"
    is BatchState.Saved -> "${s.title} · T${s.number}"
    is BatchState.Owned -> "${s.title} · T${s.number}"
    is BatchState.Duplicate -> "${s.title} · T${s.number}"
    else -> "ISBN ${item.isbn}"
}

private fun lineState(s: BatchState): String = when (s) {
    BatchState.Searching -> "Recherche…"
    is BatchState.Ready -> if (s.needsCheck) "Ajout · numéro à vérifier" else "Ajout"
    is BatchState.Saved -> "Enregistré ✓"
    is BatchState.Owned -> "Déjà dans ta bibliothèque"
    is BatchState.Duplicate -> "Tome déjà présent"
    BatchState.NotFound -> "Introuvable"
    is BatchState.Failed -> s.message
}

@Composable
private fun stateIcon(s: BatchState): Pair<ImageVector, Color> = when (s) {
    is BatchState.Ready -> (if (s.needsCheck) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle) to (if (s.needsCheck) MaterialTheme.extra.doing else MaterialTheme.colorScheme.primary)
    is BatchState.Saved -> Icons.Rounded.CheckCircle to MaterialTheme.extra.ok
    is BatchState.Owned, is BatchState.Duplicate -> Icons.Rounded.Inventory2 to MaterialTheme.extra.muted
    BatchState.NotFound -> Icons.AutoMirrored.Rounded.HelpOutline to MaterialTheme.colorScheme.primary
    is BatchState.Failed -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
    BatchState.Searching -> Icons.AutoMirrored.Rounded.HelpOutline to MaterialTheme.extra.muted
}

private fun coverOf(s: BatchState): String? = when (s) {
    is BatchState.Ready -> s.book.coverUrl
    is BatchState.Saved -> s.cover
    is BatchState.Owned -> s.cover
    else -> null
}

private fun vibrateShort(context: android.content.Context) {
    runCatching {
        val v = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        v.vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

/** Étiquette sur la couverture dans la page de vérification. */
@Composable
private fun tagOf(s: BatchState): Pair<String, Color>? = when (s) {
    is BatchState.Ready -> if (s.needsCheck) "À vérifier" to MaterialTheme.extra.doing else if (s.knownSeries) "Ta série" to MaterialTheme.extra.ok else "Nouveau" to MaterialTheme.colorScheme.primary
    is BatchState.Saved -> "✓" to MaterialTheme.extra.ok
    is BatchState.Owned, is BatchState.Duplicate -> "Déjà à toi" to MaterialTheme.extra.muted
    BatchState.NotFound -> "Introuvable" to MaterialTheme.extra.doing
    is BatchState.Failed -> "Erreur" to MaterialTheme.colorScheme.error
    BatchState.Searching -> null
}

private fun titleOf(item: BatchItem): String = when (val s = item.state) {
    is BatchState.Ready -> s.seriesTitle
    is BatchState.Saved -> s.title
    is BatchState.Owned -> s.title
    is BatchState.Duplicate -> s.title
    BatchState.Searching -> "Recherche…"
    else -> "ISBN ${item.isbn}"
}

private fun numberOf(item: BatchItem): String = when (val s = item.state) {
    is BatchState.Ready -> "Tome ${s.number}"
    is BatchState.Saved -> "Tome ${s.number}"
    is BatchState.Owned -> "Tome ${s.number}"
    is BatchState.Duplicate -> "Tome ${s.number}"
    BatchState.NotFound -> "À ajouter à la main"
    is BatchState.Failed -> s.message
    BatchState.Searching -> ""
}

/** Écran de réussite après « Valider » : coche animée, couvertures ajoutées, suite. */
@Composable
private fun BatchSuccess(saved: Int, covers: List<String>, left: Int, onCollection: () -> Unit, onSeeLeft: () -> Unit, onScanMore: () -> Unit) {
    BackHandler(onBack = onCollection)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(if (shown) 1f else 0.3f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 300f), label = "coche")
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(96.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(MaterialTheme.extra.ok),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(
            if (saved > 0) "$saved manga${if (saved > 1) "s" else ""} ajouté${if (saved > 1) "s" else ""} !" else "Rien de nouveau à ajouter",
            style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center
        )
        Text("Ils sont dans ta bibliothèque.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (covers.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            // Éventail de couvertures
            Box(Modifier.height(150.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val shownCovers = covers.take(5)
                shownCovers.forEachIndexed { i, c ->
                    val mid = (shownCovers.size - 1) / 2f
                    AsyncImage(
                        File(c), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(84.dp).aspectRatio(2f / 3f)
                            .graphicsLayer { translationX = (i - mid) * 52.dp.toPx(); rotationZ = (i - mid) * 7f }
                            .shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onCollection, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Voir ma bibliothèque", style = MaterialTheme.typography.titleMedium)
        }
        if (left > 0) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSeeLeft, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("$left livre${if (left > 1) "s" else ""} à compléter")
            }
        }
        TextButton(onClick = onScanMore) { Text("Scanner une autre pile") }
    }
}
