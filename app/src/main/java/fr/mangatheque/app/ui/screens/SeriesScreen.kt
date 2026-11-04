package fr.mangatheque.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.FloatingActionButton
import fr.mangatheque.app.ui.components.ProgressBar
import fr.mangatheque.app.ui.components.bounceClick
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import fr.mangatheque.app.data.Volume
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.components.Backdrop
import fr.mangatheque.app.ui.components.Cover
import fr.mangatheque.app.ui.components.MissingCover
import fr.mangatheque.app.ui.components.Pill
import fr.mangatheque.app.ui.components.SectionHeader
import fr.mangatheque.app.ui.components.StatCard
import fr.mangatheque.app.ui.components.StatusDot
import fr.mangatheque.app.ui.theme.extra

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SeriesScreen(
    vm: AppViewModel,
    id: Long,
    onBack: () -> Unit,
    onOpenVolume: (Long) -> Unit,
    onEdit: () -> Unit,
    onAddVolume: () -> Unit,
    onScan: () -> Unit,
    onTakeCover: () -> Unit,
    onGenre: (String) -> Unit
) {
    // Flux mémorisé : sinon l'écran se réabonne à la base à chaque rafraîchissement.
    val flow = remember(id) { vm.repo.observeSeries(id) }
    val s by flow.collectAsStateWithLifecycle(initialValue = null)
    val data = s ?: return
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var volumeToDelete by remember { mutableStateOf<Volume?>(null) }
    var volumeSheet by remember { mutableStateOf<Volume?>(null) }
    val haptic = rememberHaptic(vm)
    // Couvertures des tomes enregistrés sans image : on les recherche une fois.
    LaunchedEffect(id) { vm.fetchMissingCovers(id) }
    var confirmAllRead by remember { mutableStateOf(false) }
    var addMissing by remember { mutableStateOf<Int?>(null) }
    var rangeDialog by remember { mutableStateOf(false) }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setBannerFromUri(id, uri)
    }

    // Première ouverture : on cherche l'image de l'œuvre pour le fond.
    // (et pour retrouver de vrais genres si la série n'en a pas).
    LaunchedEffect(data.series.id) {
        vm.fetchBanner(data.series.id) // ne fait rien si tout est déjà complet
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(data.backdrop, data.series.colorSeed)
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(onClick = onScan, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Icon(Icons.Rounded.QrCodeScanner, "Scanner un tome")
                }
            },
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                    actions = {
                        IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Modifier la série") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Plus") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Scanner un tome") }, leadingIcon = { Icon(Icons.Rounded.QrCodeScanner, null) }, onClick = { menu = false; onScan() })
                                DropdownMenuItem(text = { Text("Ajouter un tome à la main") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { menu = false; onAddVolume() })
                                DropdownMenuItem(text = { Text("Ajouter plusieurs tomes") }, leadingIcon = { Icon(Icons.Rounded.LibraryAdd, null) }, onClick = { menu = false; rangeDialog = true })
                                DropdownMenuItem(text = { Text("Tout marquer comme lu") }, leadingIcon = { Icon(Icons.Rounded.DoneAll, null) }, onClick = { menu = false; confirmAllRead = true })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Fond : depuis la galerie") }, leadingIcon = { Icon(Icons.Rounded.Wallpaper, null) }, onClick = {
                                    menu = false; pickBanner.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                })
                                DropdownMenuItem(text = { Text("Fond : automatique") }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) }, onClick = { menu = false; vm.fetchBanner(id, manual = true) })
                                if (data.backdrop != null) DropdownMenuItem(text = { Text("Retirer le fond") }, leadingIcon = { Icon(Icons.Rounded.HideImage, null) }, onClick = { menu = false; vm.removeBanner(id) })
                                DropdownMenuItem(text = { Text("Photo de la couverture") }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = { menu = false; onTakeCover() })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Supprimer la série", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 92.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Cover(
                                data.displayCover, data.series.title, if (data.series.coverPath == null) data.sortedVolumes.lastOrNull()?.number else null,
                                data.series.publisher, data.series.colorSeed, Modifier.width(112.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(data.series.title, style = MaterialTheme.typography.headlineLarge)
                                Spacer(Modifier.height(6.dp))
                                val by = listOf(data.series.author, data.series.publisher).filter { it.isNotBlank() }.joinToString(" · ")
                                if (by.isNotBlank()) Text(by, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                if (data.series.ongoing) Pill("En cours", MaterialTheme.colorScheme.primary)
                                else Pill("Terminée", MaterialTheme.extra.ok)
                                if (data.series.genreList.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        data.series.genreList.forEach { g -> Pill(g, onClick = { onGenre(g) }) }
                                    }
                                }
                            }
                        }
                        if (data.series.synopsis.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            var expanded by remember { mutableStateOf(false) }
                            Text(
                                data.series.synopsis, style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.animateContentSize().clickable { expanded = !expanded }
                            )
                            Text(
                                if (expanded) "Réduire" else "Lire plus", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp)
                            )
                        }
                        if (data.volumes.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            val pct = data.readCount * 100 / data.volumes.size
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$pct % lu", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.extra.ok)
                                Spacer(Modifier.width(10.dp))
                                ProgressBar(data.readCount, data.volumes.size, data.published, Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatCard("${data.volumes.size}/${data.published}", "possédés", Modifier.weight(1f))
                            StatCard("${data.readCount}", "lus", Modifier.weight(1f), color = MaterialTheme.extra.ok)
                            StatCard("${data.readingCount}", "en cours", Modifier.weight(1f), color = MaterialTheme.extra.doing)
                            StatCard("${data.missingNumbers.size}", "manquants", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(20.dp))
                        SectionHeader("Les tomes")
                        Text(
                            "Appui long sur un tome : statut ou suppression",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                val max = maxOf(data.published, 1)
                items((1..max).toList(), key = { it }) { n ->
                    val v = data.volumes.firstOrNull { it.number == n }
                    if (v != null) {
                        Column(Modifier.bounceClick(onLongClick = { haptic(); volumeSheet = v }) { onOpenVolume(v.id) }) {
                            Cover(v.coverPath, data.series.title, n, data.series.publisher, data.series.colorSeed)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(v.status); Spacer(Modifier.width(5.dp))
                                Text("T$n · ${v.status.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        Column(Modifier.clickable { addMissing = n }) {
                            MissingCover(n)
                            Spacer(Modifier.height(6.dp))
                            Text("T$n · à acheter", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }

                if (data.series.ongoing || data.series.totalVolumes == null) {
                    item {
                        Column(Modifier.clickable { addMissing = max + 1 }) {
                            MissingCover(max + 1)
                            Spacer(Modifier.height(6.dp))
                            Text("Ajouter T${max + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Supprimer la série", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 20.dp).clickable { confirmDelete = true }.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }

    volumeSheet?.let { v ->
        VolumeActionsSheet(
            data, v, onDismiss = { volumeSheet = null },
            onStatus = { vm.setStatus(v, it) },
            onOpen = { onOpenVolume(v.id) },
            onDelete = { volumeToDelete = v }
        )
    }
    volumeToDelete?.let { v ->
        AlertDialog(
            onDismissRequest = { volumeToDelete = null },
            title = { Text("Supprimer le tome ${v.number} ?") },
            text = { Text("Sa note et ses photos seront supprimées.") },
            confirmButton = { TextButton(onClick = { vm.deleteVolume(v) {}; volumeToDelete = null }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { volumeToDelete = null }) { Text("Annuler") } }
        )
    }

    addMissing?.let { n ->
        AlertDialog(
            onDismissRequest = { addMissing = null },
            title = { Text("Tome $n") },
            text = { Text("L'ajouter à ta collection ?") },
            confirmButton = { TextButton(onClick = { vm.addMissingVolume(id, n); addMissing = null }) { Text("Je l'ai !") } },
            dismissButton = { TextButton(onClick = { addMissing = null }) { Text("Annuler") } }
        )
    }
    if (rangeDialog) {
        val firstMissing = data.missingNumbers.firstOrNull() ?: ((data.volumes.maxOfOrNull { it.number } ?: 0) + 1)
        var from by remember { mutableStateOf(firstMissing.toString()) }
        var to by remember { mutableStateOf(maxOf(data.published, firstMissing).toString()) }
        val f = from.toIntOrNull(); val t = to.toIntOrNull()
        val valid = f != null && t != null && f in 1..999 && t in f..999 && t - f < 300
        AlertDialog(
            onDismissRequest = { rangeDialog = false },
            title = { Text("Ajouter plusieurs tomes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Les tomes déjà présents sont ignorés.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(from, { from = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Du tome") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(to, { to = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("au tome") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = { TextButton(enabled = valid, onClick = { vm.addVolumeRange(id, f!!, t!!); rangeDialog = false }) { Text("Ajouter") } },
            dismissButton = { TextButton(onClick = { rangeDialog = false }) { Text("Annuler") } }
        )
    }
    if (confirmAllRead) {
        AlertDialog(
            onDismissRequest = { confirmAllRead = false },
            title = { Text("Tout marquer comme lu ?") },
            text = { Text("Tous les tomes passeront en « Lu ».") },
            confirmButton = { TextButton(onClick = { confirmAllRead = false; vm.markReadUpTo(id, Int.MAX_VALUE) }) { Text("Oui, tout est lu") } },
            dismissButton = { TextButton(onClick = { confirmAllRead = false }) { Text("Annuler") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${data.series.title} » ?") },
            text = { Text("Tomes, notes et photos seront supprimés.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteSeries(data, onBack) }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
        )
    }
}
