package fr.mangatheque.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.Release
import fr.mangatheque.app.ui.components.Cover
import fr.mangatheque.app.ui.components.SectionHeader
import fr.mangatheque.app.ui.components.bounceClick
import fr.mangatheque.app.ui.theme.extra
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ------------------------------------------------------------------ Bouton Scanner dépliable

/**
 * Bouton « Scanner » : un appui déplie deux petits boutons au-dessus — « Unique » (un livre)
 * et « Multi » (toute une pile). Un appui ailleurs referme.
 */
@Composable
fun BoxScope.ScanSpeedDial(open: Boolean, onToggle: () -> Unit, onSingle: () -> Unit, onMultiple: () -> Unit) {
    // Voile léger derrière les options (un appui le referme)
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.28f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
        )
    }
    Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            open,
            enter = fadeIn() + slideInVertically { it / 3 } + scaleIn(initialScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
            exit = fadeOut() + slideOutVertically { it / 3 } + scaleOut(targetScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f))
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DialOption(Icons.Rounded.AutoAwesomeMotion, "Multi", onMultiple)
                DialOption(Icons.Rounded.QrCodeScanner, "Unique", onSingle)
            }
        }
        val angle by animateFloatAsState(if (open) 45f else 0f, label = "rotation")
        ExtendedFloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(if (open) Icons.Rounded.Add else Icons.Rounded.QrCodeScanner, null, Modifier.rotate(if (open) angle else 0f)) },
            text = { Text(if (open) "Fermer" else "Scanner") }
        )
    }
}

@Composable
private fun DialOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.shadow(3.dp, RoundedCornerShape(99.dp)).clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)
        )
        Spacer(Modifier.width(10.dp))
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) { Icon(icon, label) }
        Spacer(Modifier.width(6.dp))
    }
}

// ------------------------------------------------------------------ Prochaines sorties

/** Étagère « Prochaines sorties » : tomes de mes séries qui sortent bientôt en France (ou viennent de sortir). */
@Composable
fun ReleasesShelf(vm: AppViewModel, onOpenSeries: (Long) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        SectionHeader("Prochaines sorties", if (vm.releasesLoading) "Mise à jour…" else null)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(vm.releases, key = { it.isbn }) { r ->
                LaunchedEffect(r.isbn) { vm.requestCover(r.isbn) }
                val (badge, soon) = releaseBadge(r)
                Column(Modifier.width(112.dp).bounceClick { onOpenSeries(r.seriesId) }) {
                    Cover(
                        vm.coverUrls[r.isbn], r.title, r.number, "", r.title.hashCode(),
                        badge = badge, badgeColor = if (soon) MaterialTheme.colorScheme.primary else MaterialTheme.extra.ok
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${r.title} · T${r.number}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(releaseText(r), style = MaterialTheme.typography.bodySmall, color = if (soon) MaterialTheme.colorScheme.primary else MaterialTheme.extra.ok, maxLines = 1)
                }
            }
        }
    }
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)

private fun daysUntil(r: Release): Long = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(r.date))

/** Étiquette courte sur la couverture (J-10, Demain, Sorti…) ; second = à venir. */
private fun releaseBadge(r: Release): Pair<String, Boolean> {
    val d = daysUntil(r)
    return when {
        d > 1 -> "J-$d" to true
        d == 1L -> "Demain" to true
        d == 0L -> "Aujourd'hui" to true
        else -> (if (r.owned) "À toi" else "Sorti") to false
    }
}

/** « Sort dans 10 jours », « Sort le 2 décembre », « Sorti hier »… */
private fun releaseText(r: Release): String {
    val d = daysUntil(r)
    val date = LocalDate.parse(r.date)
    return when {
        d > 30 -> "Sort le ${date.format(dayMonth)}"
        d > 1 -> "Sort dans $d jours"
        d == 1L -> "Sort demain"
        d == 0L -> "Sort aujourd'hui"
        d == -1L -> "Sorti hier"
        else -> "Sorti il y a ${-d} jours"
    }
}
