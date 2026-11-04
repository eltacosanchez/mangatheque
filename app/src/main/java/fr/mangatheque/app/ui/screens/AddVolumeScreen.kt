package fr.mangatheque.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.LookupState
import fr.mangatheque.app.ui.components.Cover
import fr.mangatheque.app.ui.components.Panel
import fr.mangatheque.app.ui.components.Segmented
import fr.mangatheque.app.ui.theme.extra

/** Fiche de vérification / correction après un scan, avant d'enregistrer le tome. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVolumeScreen(vm: AppViewModel, onCancel: () -> Unit, onSaved: (Long) -> Unit, onTakeCover: () -> Unit, onScanAgain: () -> Unit) {
    val d = vm.draft ?: run { onCancel(); return }
    val allSeries by vm.series.collectAsStateWithLifecycle()
    var seriesMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (d.alreadyOwned != null) "Déjà dans ta collection" else "Vérifie et corrige", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Annuler") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ----- État de la recherche -----
            when (val l = d.lookup) {
                LookupState.Loading -> Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(28.dp).height(28.dp), strokeWidth = 3.dp); Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Recherche en cours…", style = MaterialTheme.typography.titleMedium)
                            Text("ISBN ${d.isbn} · Google Books et Open Library interrogés en même temps (quelques secondes).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                LookupState.NotFound -> Notice("ISBN ${d.isbn} introuvable en ligne. Remplis la fiche à la main : l'ISBN sera quand même gardé pour reconnaître les prochains tomes de la série.")
                is LookupState.Error -> Notice("Pas de réseau ? (${l.message}). Tu peux remplir la fiche à la main, l'ISBN sera conservé.")
                is LookupState.Found -> Notice("Trouvé sur ${l.info.source} : « ${l.info.rawTitle} ». Corrige si quelque chose est faux.", good = true)
                LookupState.Idle -> {}
            }

            d.alreadyOwned?.let { (v, s) ->
                Panel {
                    Text("Ce code-barres correspond au tome ${v.number} de « ${s.title} », déjà dans ta collection.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.cancelDraft(); onSaved(v.id) }) { Text("Ouvrir le tome") }
                        OutlinedButton(onClick = onScanAgain) { Icon(Icons.Rounded.QrCodeScanner, null); Spacer(Modifier.width(6.dp)); Text("Scanner un autre") }
                    }
                }
                return@Column
            }

            // ----- Couverture -----
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Cover(
                    d.coverPath ?: d.coverUrl, d.seriesTitle.ifBlank { "Titre" }, d.number.toIntOrNull(), d.publisher,
                    d.seriesTitle.hashCode(), Modifier.width(120.dp)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (d.coverPath == null && d.coverUrl == null && d.lookup !is LookupState.Loading) {
                        Text("Aucune couverture trouvée.", style = MaterialTheme.typography.titleSmall)
                        Button(onClick = onTakeCover) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Prendre la couverture en photo") }
                        Text("Le recadrage se fait automatiquement sur le cadre.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(if (d.coverPath != null) "Couverture : ta photo" else "Couverture trouvée en ligne", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = onTakeCover) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text(if (d.coverPath != null) "Reprendre la photo" else "Remplacer par une photo") }
                    }
                    if (d.isbn.isNotBlank()) Text("ISBN ${d.isbn}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ----- Série -----
            Panel("Série") {
                Box {
                    OutlinedButton(onClick = { seriesMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (d.seriesId == null) "Nouvelle série" else "Série existante : ${d.seriesTitle}", maxLines = 1)
                    }
                    DropdownMenu(expanded = seriesMenu, onDismissRequest = { seriesMenu = false }) {
                        DropdownMenuItem(text = { Text("➕ Nouvelle série") }, onClick = { seriesMenu = false; vm.selectDraftSeries(null) })
                        allSeries.forEach { s ->
                            DropdownMenuItem(text = { Text("${s.series.title} (${s.volumes.size} tomes)") }, onClick = { seriesMenu = false; vm.selectDraftSeries(s.series) })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    d.seriesTitle, { t -> vm.updateDraft { it.copy(seriesTitle = t) } },
                    label = { Text("Titre de la série *") }, singleLine = true, enabled = d.seriesId == null, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    d.number, { t -> vm.updateDraft { it.copy(number = t.filter { c -> c.isDigit() }) } },
                    label = { Text("Numéro du tome *") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                )
            }

            Panel("Infos (modifiables)") {
                OutlinedTextField(d.author, { t -> vm.updateDraft { it.copy(author = t) } }, label = { Text("Auteur(s)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(d.publisher, { t -> vm.updateDraft { it.copy(publisher = t) } }, label = { Text("Éditeur") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(d.genres, { t -> vm.updateDraft { it.copy(genres = t) } }, label = { Text("Genres (virgules)") }, placeholder = { Text("Shōnen, Action") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Publication", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Segmented(listOf(true to "Série en cours", false to "Terminée"), d.ongoing, { o -> vm.updateDraft { it.copy(ongoing = o) } })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    d.totalVolumes, { t -> vm.updateDraft { it.copy(totalVolumes = t.filter { c -> c.isDigit() }) } },
                    label = { Text("Tomes parus (pour les manquants)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(d.synopsis, { t -> vm.updateDraft { it.copy(synopsis = t) } }, label = { Text("Résumé") }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onScanAgain) { Icon(Icons.Rounded.QrCodeScanner, null); Spacer(Modifier.width(6.dp)); Text("Rescanner") }
                Button(
                    onClick = { saving = true; vm.saveDraft { id -> saving = false; onSaved(id) } },
                    enabled = !saving && d.seriesTitle.isNotBlank() && d.number.toIntOrNull() != null && d.lookup !is LookupState.Loading
                ) { Text(if (saving) "Enregistrement…" else "Ajouter à ma collection") }
            }
        }
    }
}

@Composable
private fun Notice(text: String, good: Boolean = false) {
    val c = if (good) MaterialTheme.extra.ok else MaterialTheme.extra.doing
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.12f)).border(1.dp, c.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(12.dp)
    ) { Text(text, style = MaterialTheme.typography.bodyMedium) }
}
