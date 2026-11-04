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

        Panel("Apparence") {
            Text("Fond de l'application", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Segmented(
                listOf("auto" to "Comme le téléphone", "clair" to "Blanc", "sombre" to "Sombre"),
                vm.themeMode, { vm.changeTheme(it) }
            )
        }

        Panel("Sauvegarde") {
            Text("Tout est stocké sur le téléphone. Exporte régulièrement un fichier .zip (fiches + photos) vers ton Drive, ta carte SD ou ton PC.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text("Dernière sauvegarde : ${vm.lastExportLabel()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.FRANCE).format(Date())
                    exportLauncher.launch("mangatheque_$stamp.zip")
                }) { Icon(Icons.Rounded.Upload, null); Spacer(Modifier.width(6.dp)); Text("Exporter") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(6.dp)); Text("Restaurer") }
            }
        }

        Panel("Mises à jour (GitHub Releases)") {
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
                label = { Text("Jeton GitHub (seulement si dépôt privé)") }, placeholder = { Text("github_pat_…") },
                supportingText = { Text("GitHub → Settings → Developer settings → Personal access tokens. Laisse vide pour un dépôt public.") },
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
                Text("Pour installer les mises à jour, autorise « Ma Mangathèque » à installer des applications.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { Updater.openUnknownSourcesSettings(context) }) { Text("Ouvrir le réglage") }
            }
        }

        Panel("À propos") {
            Text("Ma Mangathèque · données 100 % locales (aucun compte, aucune publicité).\nInfos des livres : Google Books et Open Library.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Restaurer cette sauvegarde ?") },
            text = { Text("Ta collection actuelle (séries, tomes, photos) sera remplacée par le contenu du fichier.") },
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
                Text("Taille : ${"%.1f".format(info.apkSize / 1_048_576.0)} Mo. Tes données sont conservées.", style = MaterialTheme.typography.bodySmall)
                when (val u = update) {
                    is UpdateState.Downloading -> { Text("Téléchargement ${(u.progress * 100).toInt()} %"); LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth()) }
                    is UpdateState.Downloaded -> Text("Téléchargée. Si l'installateur ne s'est pas ouvert, touche « Installer ».")
                    else -> {}
                }
                if (!Updater.canInstallUnknown(context)) {
                    Text("Android demande d'autoriser l'application à installer des mises à jour (une seule fois).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
