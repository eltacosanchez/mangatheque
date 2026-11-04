package fr.mangatheque.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.mangatheque.app.data.ReadStatus
import fr.mangatheque.app.data.SeriesWithVolumes
import fr.mangatheque.app.data.Volume
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.components.Cover
import fr.mangatheque.app.ui.components.plural
import fr.mangatheque.app.ui.components.statusColor

/** Petite vibration d'appui long (si les vibrations sont activées dans les réglages). */
@Composable
fun rememberHaptic(vm: AppViewModel): () -> Unit {
    val h = LocalHapticFeedback.current
    return { if (vm.hapticsOn) h.performHapticFeedback(HapticFeedbackType.LongPress) }
}

/** Icône de chaque statut de lecture. */
fun statusIcon(s: ReadStatus): ImageVector = when (s) {
    ReadStatus.A_LIRE -> Icons.Rounded.Bookmark
    ReadStatus.EN_COURS -> Icons.Rounded.HourglassTop
    ReadStatus.LU -> Icons.Rounded.CheckCircle
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, color: Color = MaterialTheme.colorScheme.onSurface, trailing: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        if (trailing != null) Icon(trailing, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SheetHeader(s: SeriesWithVolumes, subtitle: String, number: Int? = null, cover: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(cover ?: s.displayCover, s.series.title, number, s.series.publisher, s.series.colorSeed, Modifier.width(44.dp), corner = 6)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(s.series.title + (number?.let { " · T$it" } ?: ""), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Menu d'une série (appui long dans la collection). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesActionsSheet(s: SeriesWithVolumes, onDismiss: () -> Unit, onOpen: () -> Unit, onAllRead: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SheetHeader(s, "${plural(s.volumes.size, "tome")} · ${s.readCount} lu${if (s.readCount > 1) "s" else ""}")
            SheetItem(Icons.AutoMirrored.Rounded.OpenInNew, "Ouvrir") { onDismiss(); onOpen() }
            SheetItem(Icons.Rounded.DoneAll, "Tout marquer comme lu") { onDismiss(); onAllRead() }
            SheetItem(Icons.Rounded.Delete, "Supprimer la série", MaterialTheme.colorScheme.error) { onDismiss(); onDelete() }
        }
    }
}

/** Menu d'un tome (appui long dans une fiche série ou une étagère). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeActionsSheet(
    s: SeriesWithVolumes,
    v: Volume,
    onDismiss: () -> Unit,
    onStatus: (ReadStatus) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SheetHeader(s, v.status.label, number = v.number, cover = v.coverPath)
            ReadStatus.entries.forEach { st ->
                SheetItem(statusIcon(st), st.label, statusColor(st), trailing = if (v.status == st) Icons.Rounded.Check else null) { onDismiss(); onStatus(st) }
            }
            SheetItem(Icons.AutoMirrored.Rounded.OpenInNew, "Ouvrir la fiche") { onDismiss(); onOpen() }
            SheetItem(Icons.Rounded.Delete, "Supprimer le tome", MaterialTheme.colorScheme.error) { onDismiss(); onDelete() }
        }
    }
}
