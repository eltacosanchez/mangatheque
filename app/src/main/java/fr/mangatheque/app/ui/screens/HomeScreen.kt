package fr.mangatheque.app.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mangatheque.app.data.BookLookup
import fr.mangatheque.app.data.ReadStatus
import fr.mangatheque.app.data.Repository
import fr.mangatheque.app.data.SeriesWithVolumes
import fr.mangatheque.app.data.Volume
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.SortMode
import fr.mangatheque.app.ui.components.Brand
import fr.mangatheque.app.ui.components.Chip
import fr.mangatheque.app.ui.components.Cover
import fr.mangatheque.app.ui.components.EmptyState
import fr.mangatheque.app.ui.components.ProgressBar
import fr.mangatheque.app.ui.components.SearchField
import fr.mangatheque.app.ui.components.SectionHeader
import fr.mangatheque.app.ui.components.Segmented
import fr.mangatheque.app.ui.components.SpeechBubble
import fr.mangatheque.app.ui.components.SpeedLines
import fr.mangatheque.app.ui.components.StatCard
import fr.mangatheque.app.ui.components.StatusDot
import fr.mangatheque.app.ui.components.bounceClick
import fr.mangatheque.app.ui.components.halftone
import fr.mangatheque.app.ui.components.mangaPanel
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
    onOpenSettings: () -> Unit,
    onIsbn: (String) -> Unit,
    onStats: () -> Unit,
    onBatch: () -> Unit
) {
    val all by vm.series.collectAsStateWithLifecycle()
    val genres = remember(all) { all.flatMap { it.series.genreList }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val q = Repository.normalize(vm.query)
    val digits = vm.query.filter { it.isDigit() }
    val filtered = remember(all, q, digits, vm.genreFilter, vm.readFilter, vm.ongoingFilter, vm.sort) {
        val list = all.filter { s ->
            (q.isBlank() || Repository.normalize("${s.series.title} ${s.series.author} ${s.series.publisher} ${s.series.genres}").contains(q) ||
                (digits.length >= 4 && s.volumes.any { it.isbn.contains(digits) })) &&
                (vm.genreFilter == null || vm.genreFilter in s.series.genreList) &&
                (vm.readFilter == null || s.volumes.any { it.status == vm.readFilter }) &&
                (vm.ongoingFilter == null || s.series.ongoing == vm.ongoingFilter)
        }
        val byTitle = compareBy<SeriesWithVolumes> { it.series.title.lowercase() }
        when (vm.sort) {
            SortMode.TITRE -> list.sortedWith(byTitle)
            SortMode.RECENT -> list.sortedByDescending { s -> maxOf(s.series.createdAt, s.volumes.maxOfOrNull { it.addedAt } ?: 0L) }
            SortMode.PROGRESSION -> list.sortedWith(compareByDescending<SeriesWithVolumes> { if (it.volumes.isEmpty()) 0f else it.readCount.toFloat() / it.volumes.size }.then(byTitle))
            SortMode.MANQUANTS -> list.sortedWith(compareByDescending<SeriesWithVolumes> { it.missingNumbers.size }.then(byTitle))
            SortMode.AUTEUR -> list.sortedWith(compareBy<SeriesWithVolumes> { it.series.author.ifBlank { "~" }.lowercase() }.then(byTitle))
        }
    }
    val reading = remember(all) { all.flatMap { s -> s.sortedVolumes.filter { it.status == ReadStatus.EN_COURS }.map { s to it } } }
    // Prochain tome à lire dans les séries déjà commencées (sans tome « en cours »).
    val upNext = remember(all) {
        all.filter { s -> s.readCount > 0 && s.readingCount == 0 }
            .mapNotNull { s -> s.sortedVolumes.firstOrNull { it.status == ReadStatus.A_LIRE }?.let { s to it } }
            .take(12)
    }
    val recent = remember(all) {
        all.flatMap { s -> s.volumes.map { s to it } }.sortedByDescending { it.second.addedAt }.take(10)
    }
    val noFilter = q.isBlank() && vm.genreFilter == null && vm.readFilter == null && vm.ongoingFilter == null
    val totalOwned = all.sumOf { it.volumes.size }
    val totalRead = all.sumOf { it.readCount }
    val totalMissing = all.sumOf { it.missingNumbers.size }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var isbnDialog by remember { mutableStateOf(false) }
    var scanChooser by remember { mutableStateOf(false) }
    var backupHidden by remember { mutableStateOf(false) }
    var seriesSheet by remember { mutableStateOf<SeriesWithVolumes?>(null) }
    var volumeSheet by remember { mutableStateOf<Pair<SeriesWithVolumes, Volume>?>(null) }
    var seriesToDelete by remember { mutableStateOf<SeriesWithVolumes?>(null) }
    var volumeToDelete by remember { mutableStateOf<Volume?>(null) }
    val haptic = rememberHaptic(vm)
    val backupDue = !backupHidden && vm.backupReminderDue(totalOwned)
    // Prochaines sorties : recherche en arrière-plan (au plus toutes les 12 h)
    LaunchedEffect(all.size) { if (all.isNotEmpty()) vm.refreshReleases() }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(300.dp).halftone(MaterialTheme.colorScheme.primary.copy(alpha = if (MaterialTheme.extra.isDark) 0.08f else 0.05f), spacing = 11.dp, radius = 2.4.dp, fade = true))

        LazyVerticalGrid(
            columns = if (vm.listMode) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(if (vm.listMode) 10.dp else 16.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        SpeedLines(Modifier.matchParentSize(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Brand("Ma Manga", "thèque", Modifier.weight(1f))
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.Add, "Ajouter") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Scanner") }, leadingIcon = { Icon(Icons.Rounded.QrCodeScanner, null) }, onClick = { menu = false; onScan() })
                                    DropdownMenuItem(text = { Text("Scan multiple") }, leadingIcon = { Icon(Icons.Rounded.DocumentScanner, null) }, onClick = { menu = false; onBatch() })
                                    DropdownMenuItem(text = { Text("Taper un ISBN") }, leadingIcon = { Icon(Icons.Rounded.Keyboard, null) }, onClick = { menu = false; isbnDialog = true })
                                    DropdownMenuItem(text = { Text("Tome à la main") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onAddManual() })
                                    DropdownMenuItem(text = { Text("Série vide") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { menu = false; onNewSeries() })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("${all.size}", "séries", Modifier.weight(1f), onClick = onStats)
                        StatCard("$totalOwned", "tomes", Modifier.weight(1f), onClick = onStats)
                        StatCard("$totalRead", "lus", Modifier.weight(1f), color = MaterialTheme.extra.ok, onClick = onStats)
                        StatCard("$totalMissing", "manquants", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, onClick = onStats)
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
                    SearchField(vm.query, { vm.query = it }, "Titre, auteur, genre, ISBN…")
                    Spacer(Modifier.height(6.dp))
                    if (genres.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("Tous", vm.genreFilter == null, { vm.genreFilter = null })
                            genres.forEach { g -> Chip(g, vm.genreFilter == g, { vm.genreFilter = if (vm.genreFilter == g) null else g }) }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Segmented(
                            listOf(null to "Tout", ReadStatus.EN_COURS to "En cours", ReadStatus.LU to "Lus", ReadStatus.A_LIRE to "À lire"),
                            vm.readFilter, { vm.readFilter = it }
                        )
                        Segmented(listOf(null to "Toutes", true to "Suite à venir", false to "Terminées"), vm.ongoingFilter, { vm.ongoingFilter = it })
                    }
                    if (!noFilter) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(plural(filtered.size, "résultat"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.query = ""; vm.genreFilter = null; vm.readFilter = null; vm.ongoingFilter = null }) { Text("Effacer les filtres") }
                        }
                    }
                }
            }

            if (noFilter && vm.releases.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "sorties") {
                    ReleasesShelf(vm, onOpenSeries)
                }
            }

            if (noFilter) {
                shelf(this, "Je lis", reading, vm, onOpenVolume, onLongClick = { haptic(); volumeSheet = it })
                shelf(this, "À lire ensuite", upNext, vm, onOpenVolume, onLongClick = { haptic(); volumeSheet = it })
                if (totalOwned >= 4) shelf(this, "Ajoutés récemment", recent, vm, onOpenVolume, onLongClick = { haptic(); volumeSheet = it })
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Ma collection", modifier = Modifier.padding(top = 8.dp)) {
                    // Affichage en grille ou en liste
                    IconButton(onClick = { vm.toggleListMode() }) {
                        Icon(if (vm.listMode) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList, if (vm.listMode) "Affichage en grille" else "Affichage en liste", tint = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        TextButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, null); Spacer(Modifier.width(4.dp)); Text(vm.sort.label, maxLines = 1)
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMode.entries.forEach { m ->
                                DropdownMenuItem(text = { Text((if (m == vm.sort) "✓ " else "") + m.label) }, onClick = { sortMenu = false; vm.changeSort(m) })
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (all.isEmpty()) EmptyState(Icons.Rounded.AutoStories, "Collection vide.\nTouche « Scanner » pour commencer.")
                    else EmptyState(Icons.Rounded.SearchOff, "Aucun résultat")
                }
            }

            items(filtered, key = { it.series.id }) { s ->
                val longClick = { haptic(); seriesSheet = s }
                if (vm.listMode) SeriesRow(s, onLongClick = longClick) { onOpenSeries(s.series.id) }
                else SeriesCard(s, onLongClick = longClick) { onOpenSeries(s.series.id) }
            }
        }

        // Bouton Scanner dépliable : deux petits boutons apparaissent au-dessus (Unique / Multi)
        ScanSpeedDial(
            open = scanChooser, onToggle = { scanChooser = !scanChooser },
            onSingle = { scanChooser = false; onScan() }, onMultiple = { scanChooser = false; onBatch() }
        )
    }

    seriesSheet?.let { s ->
        SeriesActionsSheet(
            s, onDismiss = { seriesSheet = null },
            onOpen = { onOpenSeries(s.series.id) },
            onAllRead = { vm.markReadUpTo(s.series.id, Int.MAX_VALUE) },
            onDelete = { seriesToDelete = s }
        )
    }
    volumeSheet?.let { (s, v) ->
        VolumeActionsSheet(
            s, v, onDismiss = { volumeSheet = null },
            onStatus = { vm.setStatus(v, it) },
            onOpen = { onOpenVolume(v.id) },
            onDelete = { volumeToDelete = v }
        )
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
    volumeToDelete?.let { v ->
        AlertDialog(
            onDismissRequest = { volumeToDelete = null },
            title = { Text("Supprimer le tome ${v.number} ?") },
            text = { Text("Sa note et ses photos seront supprimées.") },
            confirmButton = { TextButton(onClick = { vm.deleteVolume(v) {}; volumeToDelete = null }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { volumeToDelete = null }) { Text("Annuler") } }
        )
    }
    if (isbnDialog) {
        var text by remember { mutableStateOf("") }
        val valid = BookLookup.isValidIsbn(BookLookup.normalizeIsbn(text))
        AlertDialog(
            onDismissRequest = { isbnDialog = false },
            title = { Text("Taper un ISBN") },
            text = {
                OutlinedTextField(
                    text, { text = it }, label = { Text("10 ou 13 chiffres") }, singleLine = true,
                    isError = text.length >= 10 && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = { TextButton(enabled = valid, onClick = { isbnDialog = false; onIsbn(BookLookup.normalizeIsbn(text)) }) { Text("Rechercher") } },
            dismissButton = { TextButton(onClick = { isbnDialog = false }) { Text("Annuler") } }
        )
    }
}

/** Étagère horizontale de tomes (Je lis, À lire ensuite, Ajoutés récemment). */
private fun shelf(
    scope: androidx.compose.foundation.lazy.grid.LazyGridScope,
    title: String,
    entries: List<Pair<SeriesWithVolumes, Volume>>,
    vm: AppViewModel,
    onOpenVolume: (Long) -> Unit,
    onLongClick: (Pair<SeriesWithVolumes, Volume>) -> Unit
) {
    if (entries.isEmpty()) return
    scope.item(span = { GridItemSpan(maxLineSpan) }, key = "shelf_$title") {
        Column(Modifier.padding(top = 8.dp)) {
            SectionHeader(title, plural(entries.size, "tome"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
                items(entries, key = { it.second.id }) { pair ->
                    val (s, v) = pair
                    Column(Modifier.width(100.dp).bounceClick(onLongClick = { onLongClick(pair) }) { onOpenVolume(v.id) }) {
                        Cover(v.coverPath ?: s.series.coverPath, s.series.title, v.number, s.series.publisher, s.series.colorSeed)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(v.status); Spacer(Modifier.width(6.dp))
                            Text("${s.series.title} · ${v.number}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Badge en haut de la couverture : complète, suite à venir ou terminée. */
@Composable
private fun badgeOf(s: SeriesWithVolumes): Pair<String, androidx.compose.ui.graphics.Color> = when {
    !s.series.ongoing && s.volumes.isNotEmpty() && s.missingNumbers.isEmpty() -> "Complète ✓" to MaterialTheme.extra.ok
    s.series.ongoing -> "Suite à venir" to MaterialTheme.colorScheme.primary
    else -> "Terminée" to MaterialTheme.extra.muted
}

@Composable
fun SeriesCard(s: SeriesWithVolumes, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val last = s.sortedVolumes.lastOrNull()
    val (badge, badgeColor) = badgeOf(s)
    Column(Modifier.bounceClick(onLongClick = onLongClick, onClick = onClick)) {
        Cover(
            path = s.displayCover, title = s.series.title, number = if (s.series.coverPath == null) last?.number else null,
            publisher = s.series.publisher, seed = s.series.colorSeed, badge = badge, badgeColor = badgeColor
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

/** Ligne compacte de la vue liste. */
@Composable
fun SeriesRow(s: SeriesWithVolumes, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().mangaPanel(corner = 16.dp).bounceClick(onLongClick = onLongClick, onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(s.displayCover, s.series.title, s.sortedVolumes.lastOrNull()?.number, s.series.publisher, s.series.colorSeed, Modifier.width(52.dp), corner = 8)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.series.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.series.author.isNotBlank()) Text(s.series.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                "${s.readCount}/${s.volumes.size} lus" + if (s.missingNumbers.isNotEmpty()) " · ${s.missingNumbers.size} manquant${if (s.missingNumbers.size > 1) "s" else ""}" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            ProgressBar(s.readCount, s.volumes.size, s.published)
        }
    }
}
