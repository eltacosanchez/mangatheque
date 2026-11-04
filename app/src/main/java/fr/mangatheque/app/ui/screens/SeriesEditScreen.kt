package fr.mangatheque.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import fr.mangatheque.app.data.Series
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.components.Segmented

/** Création (id = 0) ou modification d'une série. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesEditScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    var loaded by remember { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<Series?>(null) }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var publisher by remember { mutableStateOf("") }
    var genres by remember { mutableStateOf("") }
    var synopsis by remember { mutableStateOf("") }
    var ongoing by remember { mutableStateOf(true) }
    var total by remember { mutableStateOf("") }

    LaunchedEffect(id) {
        if (id != 0L) {
            vm.repo.seriesDao.getOne(id)?.series?.let { s ->
                original = s; title = s.title; author = s.author; publisher = s.publisher; genres = s.genres
                synopsis = s.synopsis; ongoing = s.ongoing; total = s.totalVolumes?.toString() ?: ""
            }
            loaded = true
        }
    }
    if (!loaded) return

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "Nouvelle série" else "Modifier la série", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Titre de la série *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(author, { author = it }, label = { Text("Auteur(s)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(publisher, { publisher = it }, label = { Text("Éditeur") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(genres, { genres = it }, label = { Text("Genres (séparés par des virgules)") }, placeholder = { Text("Shōnen, Action, Comédie") }, modifier = Modifier.fillMaxWidth())
            Text("Publication", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Segmented(listOf(true to "En cours", false to "Terminée"), ongoing, { ongoing = it })
            OutlinedTextField(
                total, { total = it.filter { c -> c.isDigit() } },
                label = { Text(if (ongoing) "Tomes parus à ce jour" else "Nombre total de tomes") },
                supportingText = { Text("Sert à calculer les tomes qui te manquent.") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(synopsis, { synopsis = it }, label = { Text("Résumé") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = {
                        val base = original ?: Series(title = title)
                        vm.saveSeries(
                            base.copy(
                                title = title.trim(), author = author.trim(), publisher = publisher.trim(), genres = genres.trim(),
                                synopsis = synopsis.trim(), ongoing = ongoing, totalVolumes = total.toIntOrNull()
                            ), onSaved
                        )
                    },
                    enabled = title.isNotBlank()
                ) { Text("Enregistrer") }
            }
        }
    }
}
