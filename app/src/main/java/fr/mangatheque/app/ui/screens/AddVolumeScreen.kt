package fr.mangatheque.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.LookupState
import fr.mangatheque.app.ui.components.Cover
import fr.mangatheque.app.ui.components.Panel
import fr.mangatheque.app.ui.components.Segmented
import fr.mangatheque.app.ui.components.SpeechBubble
import fr.mangatheque.app.ui.theme.extra

/** Fiche de vérification / correction après un scan, avant d'enregistrer le tome. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVolumeScreen(
    vm: AppViewModel,
    onCancel: () -> Unit,
    onSaved: (Long) -> Unit,
    onSavedScanNext: () -> Unit,
    onTakeCover: () -> Unit,
    onScanAgain: () -> Unit
) {
    // Le bouton « retour » du téléphone annule proprement l'ajout (la photo prise est effacée).
    BackHandler(onBack = onCancel)
    val d = vm.draft
    if (d == null) {
        // Cas rare (appli relancée par Android) : rien à afficher, on propose de revenir.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button(onClick = onCancel) { Text("Retour") } }
        return
    }
    val allSeries by vm.series.collectAsStateWithLifecycle()
    var seriesMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.applyCoverUri("draft", uri)
    }
    val loading = d.lookup is LookupState.Loading
    val canSave = !saving && !loading && d.seriesTitle.isNotBlank() && d.number.toIntOrNull() != null

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
                is LookupState.Loading -> Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp, color = if (l.stage == 1) MaterialTheme.colorScheme.primary else MaterialTheme.extra.doing)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (l.stage == 1) "Recherche en cours…" else "Recherche approfondie…", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (l.stage == 1) "ISBN ${d.isbn}"
                                else "Jusqu'à 20 secondes",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = if (l.stage == 1) MaterialTheme.colorScheme.primary else MaterialTheme.extra.doing)
                    Spacer(Modifier.height(6.dp))
                    Text("Étape ${l.stage} sur 2", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LookupState.NotFound -> Notice("Introuvable en ligne : remplis à la main.", MaterialTheme.extra.doing)
                is LookupState.Error -> Notice("Pas de connexion : remplis à la main.", MaterialTheme.colorScheme.primary)
                is LookupState.Found -> Notice("Trouvé : ${l.info.rawTitle}", MaterialTheme.extra.ok)
                LookupState.Idle -> {}
            }

            d.alreadyOwned?.let { (v, s) ->
                Panel {
                    Text("Tome ${v.number} de « ${s.title} » : déjà à toi.", style = MaterialTheme.typography.bodyLarge)
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
                    if (d.coverPath == null && d.coverUrl == null && !loading) {
                        Text("Pas de couverture", style = MaterialTheme.typography.titleSmall)
                        Button(onClick = onTakeCover) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Prendre en photo") }
                        Text("Recadrage automatique", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(if (d.coverPath != null) "Couverture : ta photo" else if (loading) "Recherche…" else "Couverture trouvée", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = onTakeCover) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text(if (d.coverPath != null) "Reprendre" else "Remplacer par une photo") }
                    }
                    TextButton(onClick = { pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Choisir dans la galerie")
                    }
                    if (d.isbn.isNotBlank()) Text("ISBN ${d.isbn}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ----- Série -----
            Panel("Série", icon = Icons.Rounded.CollectionsBookmark) {
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

            Panel("Infos", icon = Icons.Rounded.Info) {
                if (d.seriesInfoFound) {
                    Text("✨ Complété avec AniList", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extra.ok)
                    Spacer(Modifier.height(8.dp))
                }
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

            // Boutons en bas, pleine largeur : faciles à atteindre avec le pouce.
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { saving = true; vm.saveDraft { id -> saving = false; onSaved(id) } },
                    enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(if (saving) "Enregistrement…" else "Ajouter à ma collection") }
                OutlinedButton(
                    onClick = { saving = true; vm.saveDraft(chain = true) { saving = false; onSavedScanNext() } },
                    enabled = canSave, modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Icon(Icons.Rounded.QrCodeScanner, null); Spacer(Modifier.width(6.dp)); Text("Ajouter et scanner le suivant") }
                TextButton(onClick = onScanAgain, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Rescanner") }
            }
        }
    }
}

@Composable
private fun Notice(text: String, color: Color) {
    SpeechBubble {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
