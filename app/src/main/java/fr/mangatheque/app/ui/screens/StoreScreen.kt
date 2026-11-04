package fr.mangatheque.app.ui.screens

import androidx.compose.foundation.border
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
    val list = remember(all, q) {
        all.filter { q.isBlank() || Repository.normalize("${it.series.title} ${it.series.author}").contains(q) }
            .sortedWith(compareByDescending<SeriesWithVolumes> { it.missingNumbers.isNotEmpty() || it.series.ongoing }.thenBy { it.series.title.lowercase() })
    }

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
                        "Tape le nom du manga que tu as devant toi, ou scanne son code-barres : tu vois tout de suite ce que tu as et ce qui te manque. L'écran reste allumé.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    SearchField(vm.storeQuery, { vm.storeQuery = it }, "Quel manga as-tu en main ?")
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        if (all.isEmpty()) "Ta collection est vide pour l'instant." else "Tu n'as pas encore cette série : tu peux l'acheter sans risque de doublon.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp)
                    )
                }
            }
            items(list, key = { it.series.id }) { s ->
                val missing = s.missingNumbers
                Row(
                    Modifier.fillMaxWidth().mangaPanel().clickable { onOpenSeries(s.series.id) }.padding(12.dp),
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
                                Text(if (s.series.ongoing) "À jour, rien ne manque" else "Série complète", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.extra.ok)
                            } else {
                                Text("Il me manque : ", style = MaterialTheme.typography.bodyLarge)
                                Text("${ranges(missing)} (${missing.size})", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        val next = missing.firstOrNull()
                        Box(
                            Modifier.border(1.5.dp, MaterialTheme.extra.outline, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                when {
                                    next != null -> "👉 Prochain à acheter : tome $next"
                                    s.series.ongoing -> "Prochain tome à surveiller : T${s.published + 1}"
                                    else -> "Série terminée"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).border(2.dp, MaterialTheme.extra.outline, RoundedCornerShape(16.dp))
        )
    }
}
