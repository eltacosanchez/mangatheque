package fr.mangatheque.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import fr.mangatheque.app.ui.components.EmptyState
import fr.mangatheque.app.ui.components.Panel
import fr.mangatheque.app.ui.components.Pill
import fr.mangatheque.app.ui.components.Segmented
import fr.mangatheque.app.ui.components.bounceClick
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mangatheque.app.data.Repository
import fr.mangatheque.app.data.SeriesWithVolumes
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.components.Brand
import fr.mangatheque.app.ui.components.Cover
import fr.mangatheque.app.ui.components.SearchField
import fr.mangatheque.app.ui.components.mangaPanel
import fr.mangatheque.app.ui.components.ranges
import fr.mangatheque.app.ui.theme.extra

/** Mode « En magasin » : gros texte, lecture rapide de ce que j'ai et de ce qui manque. */
@Composable
fun StoreScreen(vm: AppViewModel, onOpenSeries: (Long) -> Unit, onScan: () -> Unit) {
    // L'écran reste allumé tant qu'on est en magasin (pas besoin de déverrouiller entre deux rayons).
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val all by vm.series.collectAsStateWithLifecycle()
    val q = Repository.normalize(vm.storeQuery)
    val list = remember(all, q, vm.storeOnlyMissing) {
        all.filter { q.isBlank() || Repository.normalize("${it.series.title} ${it.series.author}").contains(q) }
            .filter { !vm.storeOnlyMissing || it.missingNumbers.isNotEmpty() }
            .sortedWith(compareByDescending<SeriesWithVolumes> { it.missingNumbers.isNotEmpty() || it.series.ongoing }.thenBy { it.series.title.lowercase() })
    }
    // Liste de courses : tous les tomes manquants, série par série.
    val toBuy = remember(all) { all.filter { it.missingNumbers.isNotEmpty() }.sortedBy { it.series.title.lowercase() } }
    val toBuyCount = toBuy.sumOf { it.missingNumbers.size }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            item {
                Column {
                    Brand("En ", "magasin", Modifier.padding(top = 12.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tape un titre ou scanne.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    SearchField(vm.storeQuery, { vm.storeQuery = it }, "Titre du manga")
                    if (toBuyCount > 0 && vm.storeQuery.isBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Panel("À acheter · $toBuyCount tome${if (toBuyCount > 1) "s" else ""}", icon = Icons.Rounded.ShoppingCart, trailing = {
                            IconButton(onClick = { shareShoppingList(context, toBuy) }) { Icon(Icons.Rounded.Share, "Partager la liste") }
                        }) {
                            toBuy.take(6).forEach { s ->
                                Row(Modifier.fillMaxWidth().clickable { onOpenSeries(s.series.id) }.padding(vertical = 4.dp)) {
                                    Text(s.series.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.width(8.dp))
                                    Text("T" + ranges(s.missingNumbers), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (toBuy.size > 6) Text("+ ${toBuy.size - 6} autres séries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Segmented(listOf(false to "Toutes", true to "À compléter"), vm.storeOnlyMissing, { vm.changeStoreOnlyMissing(it) }, Modifier.padding(top = 8.dp))
                }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.Storefront, if (all.isEmpty()) "Collection vide." else if (vm.storeOnlyMissing && q.isBlank()) "Rien ne manque !" else "Pas dans ta collection.")
                }
            }
            items(list, key = { it.series.id }) { s ->
                val missing = s.missingNumbers
                Row(
                    Modifier.fillMaxWidth().mangaPanel().bounceClick { onOpenSeries(s.series.id) }.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Cover(s.displayCover, s.series.title, s.sortedVolumes.lastOrNull()?.number, s.series.publisher, s.series.colorSeed, Modifier.width(56.dp), corner = 6)
                    Column(Modifier.weight(1f)) {
                        Text(s.series.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Text("J'ai : ", style = MaterialTheme.typography.bodyLarge)
                            Text(ranges(s.ownedNumbers.toList()), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.extra.ok)
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            if (missing.isEmpty()) {
                                Text(if (s.series.ongoing) "À jour" else "Série complète", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.extra.ok)
                            } else {
                                Text("Manque : ", style = MaterialTheme.typography.bodyLarge)
                                Text("${ranges(missing)} (${missing.size})", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        val next = missing.firstOrNull()
                        Pill(
                            when {
                                next != null -> "Prochain : T$next"
                                s.series.ongoing -> "Prochain : T${s.published + 1}"
                                else -> "Série terminée"
                            },
                            if (next != null) MaterialTheme.colorScheme.primary else null
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onScan,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Rounded.QrCodeScanner, null) },
            text = { Text("Je l'ai déjà ?") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

/** Partage la liste des tomes à acheter (SMS, WhatsApp, notes…). */
private fun shareShoppingList(context: android.content.Context, toBuy: List<SeriesWithVolumes>) {
    val text = buildString {
        append("📚 Mangas à acheter\n")
        toBuy.forEach { append("• ${it.series.title} : tome${if (it.missingNumbers.size > 1) "s" else ""} ${ranges(it.missingNumbers)}\n") }
    }
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text) }
    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Partager la liste")) }
}
