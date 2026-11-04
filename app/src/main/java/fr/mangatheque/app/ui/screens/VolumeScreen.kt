package fr.mangatheque.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.mangatheque.app.data.Moment
import fr.mangatheque.app.data.ReadStatus
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.components.Backdrop
import fr.mangatheque.app.ui.components.Cover
import fr.mangatheque.app.ui.components.MangaTitle
import fr.mangatheque.app.ui.components.Panel
import fr.mangatheque.app.ui.components.Pill
import fr.mangatheque.app.ui.components.StatusDot
import fr.mangatheque.app.ui.components.statusColor
import fr.mangatheque.app.ui.theme.extra
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeScreen(
    vm: AppViewModel,
    id: Long,
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenVolume: (Long) -> Unit,
    onTakeCover: () -> Unit
) {
    val context = LocalContext.current
    // Flux mémorisés (sinon réabonnement à la base à chaque rafraîchissement).
    val volumeFlow = remember(id) { vm.repo.observeVolume(id) }
    val momentsFlow = remember(id) { vm.repo.observeMoments(id) }
    val volume by volumeFlow.collectAsStateWithLifecycle(initialValue = null)
    val moments by momentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val v = volume ?: return
    val seriesFlow = remember(v.seriesId) { vm.repo.observeSeries(v.seriesId) }
    val seriesData by seriesFlow.collectAsStateWithLifecycle(initialValue = null)
    val s = seriesData ?: return

    var note by remember(v.id) { mutableStateOf(v.note) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editInfo by remember { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    var momentDialogUri by remember { mutableStateOf<Uri?>(null) }
    var momentToEdit by remember { mutableStateOf<Moment?>(null) }
    var zoom by remember { mutableStateOf<Moment?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) momentDialogUri = pendingPhoto
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) momentDialogUri = uri
    }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.applyCoverUri("volume:${v.id}", uri)
    }
    fun launchCamera() {
        val f = File(context.cacheDir, "moment_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        pendingPhoto = uri
        takePicture.launch(uri)
    }

    val sorted = s.sortedVolumes
    val idx = sorted.indexOfFirst { it.id == v.id }
    val prev = sorted.getOrNull(idx - 1)
    val next = sorted.getOrNull(idx + 1)
    val unreadBefore = sorted.count { it.number < v.number && it.status != ReadStatus.LU }

    Box(Modifier.fillMaxSize()) {
        Backdrop(s.backdrop, s.series.colorSeed, height = 360.dp)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(s.series.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onOpenSeries(s.series.id) }) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                    actions = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Plus") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Modifier numéro / ISBN") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; editInfo = true })
                                DropdownMenuItem(text = { Text("Photographier la couverture") }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = { menu = false; onTakeCover() })
                                DropdownMenuItem(text = { Text("Couverture depuis la galerie") }, leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) }, onClick = {
                                    menu = false; pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                })
                                DropdownMenuItem(text = { Text("Supprimer ce tome", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Cover(v.coverPath, s.series.title, v.number, s.series.publisher, s.series.colorSeed, Modifier.width(140.dp), corner = 12)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("Tome ", style = MaterialTheme.typography.headlineMedium)
                            MangaTitle("${v.number}", MaterialTheme.typography.displayMedium, fill = MaterialTheme.colorScheme.primary, outline = MaterialTheme.extra.shadow, stroke = 4.dp)
                        }
                        if (v.title.isNotBlank()) Text(v.title, style = MaterialTheme.typography.titleMedium)
                        val by = listOf(s.series.author, s.series.publisher).filter { it.isNotBlank() }.joinToString(" · ")
                        if (by.isNotBlank()) Text(by, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Pill("Dans ma collection", MaterialTheme.extra.ok)
                        if (v.isbn.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("ISBN ${v.isbn}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (v.coverPath == null) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = onTakeCover) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Photographier") }
                        }
                    }
                }

                Panel("Ma lecture") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReadStatus.entries.forEach { st ->
                            val sel = v.status == st
                            val c = statusColor(st)
                            Column(
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) c.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
                                    .border(if (sel) 2.5.dp else 1.5.dp, if (sel) c else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    .clickable { vm.setStatus(v, st) }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                StatusDot(st)
                                Spacer(Modifier.height(4.dp))
                                Text(st.label, style = MaterialTheme.typography.labelLarge, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                    if (v.status == ReadStatus.LU && v.readAt != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Marqué comme lu le ${DateFormat.getDateInstance().format(Date(v.readAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (v.status == ReadStatus.LU && unreadBefore > 0) {
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { vm.markReadUpTo(s.series.id, v.number) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Icon(Icons.Rounded.DoneAll, null); Spacer(Modifier.width(6.dp))
                            Text("Marquer aussi les $unreadBefore tome${if (unreadBefore > 1) "s" else ""} d'avant comme lu${if (unreadBefore > 1) "s" else ""}")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Ma note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        (1..5).forEach { i ->
                            IconButton(onClick = { vm.setRating(v, if (v.rating == i) 0 else i) }, modifier = Modifier.size(42.dp)) {
                                Icon(
                                    if (i <= v.rating) Icons.Rounded.Star else Icons.Rounded.StarBorder, "$i étoiles",
                                    tint = if (i <= v.rating) MaterialTheme.extra.pop else MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }

                Panel("Ma note perso") {
                    OutlinedTextField(
                        note, { note = it; vm.setNote(v, it) },
                        placeholder = { Text("Ce que tu as pensé de ce tome, ton passage préféré…") },
                        minLines = 3, modifier = Modifier.fillMaxWidth()
                    )
                }

                Panel("Mes passages préférés") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { launchCamera() }) { Icon(Icons.Rounded.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text("Prendre une page") }
                        OutlinedButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Rounded.PhotoLibrary, "Galerie") }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (moments.isEmpty()) {
                        Text("Prends en photo une page ou une case que tu as adorée, puis ajoute un petit mot et le numéro de page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(moments, key = { it.id }) { m ->
                                Column(Modifier.width(130.dp)) {
                                    AsyncImage(
                                        File(m.photoPath), null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp))
                                            .border(1.5.dp, MaterialTheme.extra.outline, RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { zoom = m }
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    if (m.page != null) Text("Page ${m.page}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    if (m.caption.isNotBlank()) Text(m.caption, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    TextButton(onClick = { momentToEdit = m }, contentPadding = PaddingValues(0.dp)) { Text("Modifier", style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { prev?.let { onOpenVolume(it.id) } }, enabled = prev != null) { Text("← Tome ${prev?.number ?: ""}") }
                    Button(onClick = { next?.let { onOpenVolume(it.id) } }, enabled = next != null) { Text("Tome ${next?.number ?: ""} →") }
                }
            }
        }
    }

    momentDialogUri?.let { uri ->
        MomentDialog(
            title = "Nouveau passage", initialCaption = "", initialPage = "",
            onDismiss = { momentDialogUri = null },
            onConfirm = { caption, page -> vm.addMoment(v.id, uri, caption, page); momentDialogUri = null }
        )
    }
    momentToEdit?.let { m ->
        MomentDialog(
            title = "Modifier le passage", initialCaption = m.caption, initialPage = m.page?.toString() ?: "",
            onDismiss = { momentToEdit = null },
            onConfirm = { caption, page -> vm.updateMoment(m.copy(caption = caption, page = page)); momentToEdit = null },
            onDelete = { vm.deleteMoment(m); momentToEdit = null }
        )
    }
    zoom?.let { m -> Lightbox(m.photoPath, "${s.series.title} · tome ${v.number}" + (m.page?.let { " · page $it" } ?: ""), m.caption) { zoom = null } }

    if (editInfo) {
        var num by remember { mutableStateOf(v.number.toString()) }
        var isbn by remember { mutableStateOf(v.isbn) }
        var title by remember { mutableStateOf(v.title) }
        AlertDialog(
            onDismissRequest = { editInfo = false },
            title = { Text("Infos du tome") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(num, { num = it.filter { c -> c.isDigit() } }, label = { Text("Numéro de tome") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(title, { title = it }, label = { Text("Titre du tome (facultatif)") }, singleLine = true)
                    OutlinedTextField(isbn, { isbn = it }, label = { Text("ISBN") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = num.toIntOrNull() ?: v.number
                    if (n != v.number && s.volumes.any { it.number == n }) vm.toast("Le tome $n existe déjà")
                    else vm.updateVolume(v.copy(number = n, isbn = isbn.filter { it.isDigit() || it == 'X' }, title = title.trim()))
                    editInfo = false
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editInfo = false }) { Text("Annuler") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer le tome ${v.number} ?") },
            text = { Text("Sa note et ses photos de passages seront supprimées.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteVolume(v, onBack) }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
        )
    }
}

@Composable
fun MomentDialog(
    title: String,
    initialCaption: String,
    initialPage: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var caption by remember { mutableStateOf(initialCaption) }
    var page by remember { mutableStateOf(initialPage) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(page, { page = it.filter { c -> c.isDigit() } }, label = { Text("Numéro de page") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(caption, { caption = it }, label = { Text("Petit mot") }, placeholder = { Text("Pourquoi tu l'as aimé ?") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(caption.trim(), page.toIntOrNull()) }) { Text("Enregistrer") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        }
    )
}

/** Plein écran d'une photo de passage. */
@Composable
fun Lightbox(path: String, title: String, caption: String, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF20A0A0C)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                AsyncImage(File(path), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().weight(1f, fill = false).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.height(12.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                if (caption.isNotBlank()) Text(caption, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
