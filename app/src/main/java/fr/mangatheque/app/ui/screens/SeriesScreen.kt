package fr.mangatheque.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mangatheque.app.data.ReadStatus
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.components.Cover
import fr.mangatheque.app.ui.components.MissingCover
import fr.mangatheque.app.ui.components.Pill
import fr.mangatheque.app.ui.components.SectionHeader
import fr.mangatheque.app.ui.components.StatCard
import fr.mangatheque.app.ui.components.StatusDot
import fr.mangatheque.app.ui.theme.extra

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SeriesScreen(
    vm: AppViewModel,
    id: Long,
    onBack: () -> Unit,
    onOpenVolume: (Long) -> Unit,
    onEdit: () -> Unit,
    onAddVolume: () -> Unit,
    onScan: () -> Unit,
    onTakeCover: () -> Unit
) {
    val s by vm.repo.observeSeries(id).collectAsStateWithLifecycle(initialValue = null)
    val data = s ?: return
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addMissing by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                            DropdownMenuItem(text = { Text("Photographier la couverture de la série") }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = { menu = false; onTakeCover() })
                            DropdownMenuItem(text = { Text("Supprimer la série", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
                            data.series.publisher, data.series.colorSeed, Modifier.width(110.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(data.series.title, style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(6.dp))
                            val by = listOf(data.series.author, data.series.publisher).filter { it.isNotBlank() }.joinToString(" · ")
                            if (by.isNotBlank()) Text(by, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            if (data.series.ongoing) Pill("Série en cours · une suite arrive", MaterialTheme.colorScheme.primary)
                            else Pill("Série terminée", MaterialTheme.extra.ok)
                            if (data.series.genreList.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    data.series.genreList.forEach { Pill(it) }
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
                            modifier = Modifier.clickable { expanded = !expanded }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("${data.volumes.size} / ${data.published}", "possédés", Modifier.weight(1f))
                        StatCard("${data.readCount}", "lus", Modifier.weight(1f))
                        StatCard("${data.readingCount}", "en cours", Modifier.weight(1f))
                        StatCard("${data.missingNumbers.size}", "manquants", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Les tomes", "Touche un tome pour ouvrir sa fiche")
                }
            }

            val max = maxOf(data.published, 1)
            items((1..max).toList(), key = { it }) { n ->
                val v = data.volumes.firstOrNull { it.number == n }
                if (v != null) {
                    Column(Modifier.clickable { onOpenVolume(v.id) }) {
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
        }
    }

    addMissing?.let { n ->
        AlertDialog(
            onDismissRequest = { addMissing = null },
            title = { Text("Tome $n") },
            text = { Text("Tu viens de l'acheter ? Ajoute-le à ta collection (tu pourras scanner son code-barres plus tard pour récupérer la couverture).") },
            confirmButton = { TextButton(onClick = { vm.addMissingVolume(id, n); addMissing = null }) { Text("Je l'ai !") } },
            dismissButton = { TextButton(onClick = { addMissing = null }) { Text("Annuler") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${data.series.title} » ?") },
            text = { Text("Les ${data.volumes.size} tomes, leurs notes et leurs photos seront supprimés. Cette action est définitive.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteSeries(data, onBack) }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
        )
    }
}
