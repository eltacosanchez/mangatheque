package fr.mangatheque.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mangatheque.app.data.ReadStatus
import fr.mangatheque.app.data.Repository
import fr.mangatheque.app.data.SeriesWithVolumes
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.SortMode
import fr.mangatheque.app.ui.components.Brand
import fr.mangatheque.app.ui.components.Chip
import fr.mangatheque.app.ui.components.Cover
import fr.mangatheque.app.ui.components.ProgressBar
import fr.mangatheque.app.ui.components.SearchField
import fr.mangatheque.app.ui.components.SectionHeader
import fr.mangatheque.app.ui.components.Segmented
import fr.mangatheque.app.ui.components.SpeechBubble
import fr.mangatheque.app.ui.components.SpeedLines
import fr.mangatheque.app.ui.components.StatCard
import fr.mangatheque.app.ui.components.StatusDot
import fr.mangatheque.app.ui.components.halftone
import fr.mangatheque.app.ui.components.plural
import fr.mangatheque.app.ui.theme.extra

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenSeries: (Long) -> Unit,
    onOpenVolume: (Long) -> Unit,
    onScan: () -> Unit,
    onAddManual: () -> Unit,
    onNewSeries: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val all by vm.series.collectAsStateWithLifecycle()
    val genres = remember(all) { all.flatMap { it.series.genreList }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val q = Repository.normalize(vm.query)
    val filtered = remember(all, q, vm.genreFilter, vm.readFilter, vm.ongoingFilter, vm.sort) {
        val list = all.filter { s ->
            (q.isBlank() || Repository.normalize("${s.series.title} ${s.series.author} ${s.series.publisher} ${s.series.genres}").contains(q)) &&
                (vm.genreFilter == null || vm.genreFilter in s.series.genreList) &&
                (vm.readFilter == null || s.volumes.any { it.status == vm.readFilter }) &&
                (vm.ongoingFilter == null || s.series.ongoing == vm.ongoingFilter)
        }
        when (vm.sort) {
            SortMode.TITRE -> list.sortedBy { it.series.title.lowercase() }
            SortMode.RECENT -> list.sortedByDescending { s -> maxOf(s.series.createdAt, s.volumes.maxOfOrNull { it.addedAt } ?: 0L) }
            SortMode.PROGRESSION -> list.sortedWith(
                compareByDescending<SeriesWithVolumes> { if (it.volumes.isEmpty()) 0f else it.readCount.toFloat() / it.volumes.size }
                    .thenBy { it.series.title.lowercase() }
            )
            SortMode.MANQUANTS -> list.sortedWith(compareByDescending<SeriesWithVolumes> { it.missingNumbers.size }.thenBy { it.series.title.lowercase() })
        }
    }
    val reading = remember(all) { all.flatMap { s -> s.sortedVolumes.filter { it.status == ReadStatus.EN_COURS }.map { s to it } } }
    val noFilter = q.isBlank() && vm.genreFilter == null && vm.readFilter == null && vm.ongoingFilter == null
    val totalOwned = all.sumOf { it.volumes.size }
    val totalRead = all.sumOf { it.readCount }
    val totalMissing = all.sumOf { it.missingNumbers.size }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var backupHidden by remember { mutableStateOf(false) }
    var seriesToDelete by remember { mutableStateOf<SeriesWithVolumes?>(null) }
    val haptic = LocalHapticFeedback.current
    val backupDue = !backupHidden && vm.backupReminderDue(totalOwned)

    Box(Modifier.fillMaxSize()) {
        // Trame de points en haut de l'écran, qui s'efface vers le bas
        Box(Modifier.fillMaxWidth().height(300.dp).halftone(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), spacing = 11.dp, radius = 2.4.dp, fade = true))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        SpeedLines(Modifier.matchParentSize(), color = MaterialTheme.extra.outline.copy(alpha = 0.07f))
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Brand("Ma Manga", "thèque")
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.Add, "Ajouter") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Scanner un code-barres") }, leadingIcon = { Icon(Icons.Rounded.QrCodeScanner, null) }, onClick = { menu = false; onScan() })
                                    DropdownMenuItem(text = { Text("Ajouter un tome à la main") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onAddManual() })
                                    DropdownMenuItem(text = { Text("Créer une série vide") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { menu = false; onNewSeries() })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("${all.size}", "séries", Modifier.weight(1f))
                        StatCard("$totalOwned", "tomes", Modifier.weight(1f))
                        StatCard("$totalRead", "lus", Modifier.weight(1f), color = MaterialTheme.extra.ok)
                        StatCard("$totalMissing", "manquants", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                    }
                    if (backupDue) {
                        Spacer(Modifier.height(14.dp))
                        SpeechBubble(background = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("Pense à sauvegarder !", style = MaterialTheme.typography.titleSmall)
                            Text("Dernière : ${vm.lastExportLabel()}", style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = onOpenSettings) { Text("Sauvegarder") }
                                TextButton(onClick = { vm.snoozeBackupReminder(); backupHidden = true }) { Text("Plus tard") }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SearchField(vm.query, { vm.query = it }, "Titre, auteur, genre…")
                    Spacer(Modifier.height(10.dp))
                    if (genres.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Tous", vm.genreFilter == null, { vm.genreFilter = null })
                            genres.forEach { g -> Chip(g, vm.genreFilter == g, { vm.genreFilter = if (vm.genreFilter == g) null else g }) }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Segmented(
                            listOf(null to "Tout", ReadStatus.EN_COURS to "En cours", ReadStatus.LU to "Lus", ReadStatus.A_LIRE to "À lire"),
                            vm.readFilter, { vm.readFilter = it }
                        )
                        Segmented(listOf(null to "Toutes", true to "Suite à venir", false to "Terminées"), vm.ongoingFilter, { vm.ongoingFilter = it })
                    }
                }
            }

            if (noFilter && reading.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = 8.dp)) {
                        SectionHeader("Je suis en train de lire", plural(reading.size, "tome"))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(reading, key = { it.second.id }) { (s, v) ->
                                Column(Modifier.width(104.dp).clickable { onOpenVolume(v.id) }) {
                                    Cover(v.coverPath ?: s.series.coverPath, s.series.title, v.number, s.series.publisher, s.series.colorSeed)
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StatusDot(ReadStatus.EN_COURS); Spacer(Modifier.width(6.dp))
                                        Text(s.series.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Ma collection", modifier = Modifier.padding(top = 8.dp)) {
                    Box {
                        TextButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, null); Spacer(Modifier.width(4.dp)); Text(vm.sort.label, maxLines = 1)
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMode.entries.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text((if (m == vm.sort) "✓ " else "") + m.label) },
                                    onClick = { sortMenu = false; vm.changeSort(m) }
                                )
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (all.isEmpty()) "Collection vide. Touche « Scanner »." else "Aucun résultat",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp)
                    )
                }
            }

            if (filtered.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Appui long pour supprimer",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(filtered, key = { it.series.id }) { s ->
                SeriesCard(s, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); seriesToDelete = s }) { onOpenSeries(s.series.id) }
            }
        }

        seriesToDelete?.let { s ->
            AlertDialog(
                onDismissRequest = { seriesToDelete = null },
                title = { Text("Supprimer « ${s.series.title} » ?") },
                text = { Text("Tomes, notes et photos seront supprimés.") },
                confirmButton = { TextButton(onClick = { vm.deleteSeries(s) {}; seriesToDelete = null }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { seriesToDelete = null }) { Text("Annuler") } }
            )
        }

        ExtendedFloatingActionButton(
            onClick = onScan,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Rounded.QrCodeScanner, null) },
            text = { Text("Scanner") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                .border(2.dp, MaterialTheme.extra.outline, RoundedCornerShape(16.dp))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesCard(s: SeriesWithVolumes, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val last = s.sortedVolumes.lastOrNull()
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Cover(
            path = s.displayCover, title = s.series.title, number = if (s.series.coverPath == null) last?.number else null,
            publisher = s.series.publisher, seed = s.series.colorSeed,
            badge = if (s.series.ongoing) "Suite à venir" else "Terminée"
        )
        Spacer(Modifier.height(8.dp))
        Text(s.series.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            StatusDot(s.overallStatus); Spacer(Modifier.width(6.dp))
            Text("${s.readCount}/${s.volumes.size} lus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(s.readCount, s.volumes.size, s.published)
        Text("${s.volumes.size} / ${s.published} tomes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}
