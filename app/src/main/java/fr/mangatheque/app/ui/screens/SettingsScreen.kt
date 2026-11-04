package fr.mangatheque.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mangatheque.app.data.Updater
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.UpdateState
import fr.mangatheque.app.ui.components.Brand
import fr.mangatheque.app.ui.components.Panel
import fr.mangatheque.app.ui.components.Segmented
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val update by vm.update.collectAsStateWithLifecycle()
    var repo by remember { mutableStateOf(vm.prefs.updateRepo) }
    var token by remember { mutableStateOf(vm.prefs.githubToken) }
    var auto by remember { mutableStateOf(vm.prefs.autoCheckUpdates) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Brand("Ré", "glages", Modifier.padding(top = 12.dp))

        Panel("Apparence", icon = Icons.Rounded.Palette) {
            Text("Fond de l'application", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Segmented(
                    listOf("auto" to "Comme le téléphone", "clair" to "Blanc", "sombre" to "Sombre"),
                    vm.themeMode, { vm.changeTheme(it) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Vibrations", modifier = Modifier.weight(1f))
                Switch(checked = vm.hapticsOn, onCheckedChange = { vm.changeHaptics(it) })
            }
        }

        Panel("Sauvegarde", icon = Icons.Rounded.CloudUpload) {
            Text("Un fichier .zip avec fiches et photos.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text("Dernière sauvegarde : ${vm.lastExportLabel()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Sauvegarde auto chaque semaine")
                    Text("Téléchargements › Mangatheque · dernière : ${vm.lastAutoBackupLabel()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.autoBackupOn, onCheckedChange = { vm.changeAutoBackup(it) })
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.FRANCE).format(Date())
                    exportLauncher.launch("mangatheque_$stamp.zip")
                }) { Icon(Icons.Rounded.Upload, null); Spacer(Modifier.width(6.dp)); Text("Exporter") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(6.dp)); Text("Restaurer") }
            }
        }

        Panel("Mises à jour", icon = Icons.Rounded.SystemUpdate) {
            Text("Version installée : ${Updater.currentVersion()}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                repo, { repo = it; vm.prefs.updateRepo = it },
                label = { Text("Dépôt GitHub (utilisateur/depot)") }, placeholder = { Text("mon-pseudo/mangatheque") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                token, { token = it; vm.prefs.githubToken = it },
                label = { Text("Jeton GitHub (dépôt privé)") }, placeholder = { Text("github_pat_…") },
                supportingText = { Text("Vide si dépôt public") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Vérifier à l'ouverture", modifier = Modifier.weight(1f))
                Switch(checked = auto, onCheckedChange = { auto = it; vm.prefs.autoCheckUpdates = it })
            }
            Spacer(Modifier.height(6.dp))
            when (val u = update) {
                UpdateState.Checking -> Text("Vérification…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                UpdateState.UpToDate -> Text("✅ Tu as la dernière version.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                is UpdateState.Available -> Text("🆕 Mise à jour disponible : ${u.info.version}", color = MaterialTheme.colorScheme.primary)
                is UpdateState.Downloading -> Column { Text("Téléchargement ${(u.progress * 100).toInt()} %"); LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth()) }
                is UpdateState.Downloaded -> Text("Téléchargée, installation…")
                is UpdateState.Failed -> Text(u.message, color = MaterialTheme.colorScheme.error)
                UpdateState.Idle -> {}
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.checkUpdates(manual = true) }, enabled = update !is UpdateState.Checking && update !is UpdateState.Downloading) { Text("Vérifier maintenant") }
                if (update is UpdateState.Available) OutlinedButton(onClick = { vm.updateDialogDismissed = false }) { Text("Détails") }
            }
            if (!Updater.canInstallUnknown(context)) {
                Spacer(Modifier.height(8.dp))
                Text("Autorise l'installation des mises à jour.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { Updater.openUnknownSourcesSettings(context) }) { Text("Ouvrir le réglage") }
            }
        }

        Panel("À propos", icon = Icons.Rounded.Info) {
            var storage by remember { mutableStateOf("…") }
            LaunchedEffect(Unit) { storage = vm.storageUsedMb() }
            Text("Ma Mangathèque ${Updater.currentVersion()}", style = MaterialTheme.typography.titleSmall)
            Text("Espace utilisé : $storage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("Données sur le téléphone uniquement.\nSources : BnF, Google Books, Open Library, AniList, ePagine, Place des Libraires.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (vm.prefs.updateRepo.isNotBlank()) {
                TextButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/${vm.prefs.updateRepo}"))) }
                }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Voir le projet sur GitHub") }
            }
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Restaurer cette sauvegarde ?") },
            text = { Text("Ta collection actuelle sera remplacée.") },
            confirmButton = { TextButton(onClick = { vm.importFrom(uri); confirmImport = null }) { Text("Restaurer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Annuler") } }
        )
    }
}

/** Boîte de dialogue « Mise à jour disponible » affichée à l'ouverture. */
@Composable
fun UpdateDialog(vm: AppViewModel) {
    val context = LocalContext.current
    val update by vm.update.collectAsStateWithLifecycle()
    val info = when (val u = update) {
        is UpdateState.Available -> u.info
        is UpdateState.Downloading -> u.info
        is UpdateState.Downloaded -> u.info
        else -> return
    }
    AlertDialog(
        onDismissRequest = { if (update is UpdateState.Available) vm.dismissUpdate() },
        title = { Text("Mise à jour disponible") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version ${info.version} (tu as la ${Updater.currentVersion()}).", style = MaterialTheme.typography.bodyLarge)
                if (info.notes.isNotBlank()) Text(info.notes.take(600), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${"%.1f".format(info.apkSize / 1_048_576.0)} Mo · données conservées", style = MaterialTheme.typography.bodySmall)
                when (val u = update) {
                    is UpdateState.Downloading -> { Text("Téléchargement ${(u.progress * 100).toInt()} %"); LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth()) }
                    is UpdateState.Downloaded -> Text("Téléchargée.")
                    else -> {}
                }
                if (!Updater.canInstallUnknown(context)) {
                    Text("Autorisation requise (une seule fois).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            when (update) {
                is UpdateState.Available -> Button(onClick = {
                    if (Updater.canInstallUnknown(context)) vm.downloadUpdate() else Updater.openUnknownSourcesSettings(context)
                }) { Text(if (Updater.canInstallUnknown(context)) "Télécharger et installer" else "Autoriser l'installation") }
                is UpdateState.Downloaded -> Button(onClick = { vm.installDownloaded() }) { Text("Installer") }
                else -> {}
            }
        },
        dismissButton = {
            Row {
                if (update is UpdateState.Available) {
                    TextButton(onClick = { vm.skipUpdate() }) { Text("Ignorer cette version") }
                    TextButton(onClick = { vm.dismissUpdate() }) { Text("Plus tard") }
                }
                if (update is UpdateState.Downloaded) TextButton(onClick = { vm.resetUpdateState() }) { Text("Fermer") }
            }
        }
    )
}
