package fr.mangatheque.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mangatheque.app.data.ReadStatus
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.components.Panel
import fr.mangatheque.app.ui.components.StatCard
import fr.mangatheque.app.ui.theme.extra
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val all by vm.series.collectAsStateWithLifecycle()
    val moments by vm.moments.collectAsStateWithLifecycle()
    val volumes = remember(all) { all.flatMap { s -> s.volumes.map { s to it } } }
    val now = remember { Calendar.getInstance() }
    fun sameMonth(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) == now.get(Calendar.YEAR) && it.get(Calendar.MONTH) == now.get(Calendar.MONTH) }
    fun sameYear(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) == now.get(Calendar.YEAR)

    val read = volumes.filter { it.second.status == ReadStatus.LU }
    val readMonth = read.count { it.second.readAt?.let(::sameMonth) == true }
    val readYear = read.count { it.second.readAt?.let(::sameYear) == true }
    val percent = if (volumes.isEmpty()) 0 else read.size * 100 / volumes.size
    val complete = all.count { !it.series.ongoing && it.volumes.isNotEmpty() && it.missingNumbers.isEmpty() }
    val ratings = volumes.map { it.second.rating }.filter { it > 0 }
    val avgRating = if (ratings.isEmpty()) null else ratings.average()
    // Genres, éditeurs et auteurs comptés en nombre de tomes possédés
    val topGenres = remember(all) { all.flatMap { s -> s.series.genreList.map { it to s.volumes.size } }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.take(6) }
    val topPublishers = remember(all) { all.filter { it.series.publisher.isNotBlank() }.groupBy { it.series.publisher }.mapValues { e -> e.value.sumOf { it.volumes.size } }.entries.sortedByDescending { it.value }.take(3) }
    val topAuthors = remember(all) { all.filter { it.series.author.isNotBlank() }.groupBy { it.series.author }.mapValues { e -> e.value.sumOf { it.volumes.size } }.entries.sortedByDescending { it.value }.take(3) }
    val lastRead = read.maxByOrNull { it.second.readAt ?: 0L }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Mes stats", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("${all.size}", "séries", Modifier.weight(1f))
                StatCard("${volumes.size}", "tomes", Modifier.weight(1f))
                StatCard("$percent %", "lus", Modifier.weight(1f), color = MaterialTheme.extra.ok)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("$complete", "complètes", Modifier.weight(1f))
                StatCard("${all.sumOf { it.missingNumbers.size }}", "manquants", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                StatCard("${moments.size}", "passages", Modifier.weight(1f))
            }

            Panel("Lecture", icon = Icons.Rounded.CalendarMonth) {
                Line("Ce mois-ci", "$readMonth tome${if (readMonth > 1) "s" else ""}")
                Line("Cette année", "$readYear tome${if (readYear > 1) "s" else ""}")
                Line("En cours", "${volumes.count { it.second.status == ReadStatus.EN_COURS }}")
                Line("À lire", "${volumes.count { it.second.status == ReadStatus.A_LIRE }}")
                avgRating?.let { Line("Note moyenne", "%.1f / 5".format(it)) }
                lastRead?.let { (s, v) -> Line("Dernier lu", "${s.series.title} · T${v.number}") }
            }

            if (topGenres.isNotEmpty()) Panel("Genres préférés", icon = Icons.Rounded.Category) {
                val max = topGenres.first().value.coerceAtLeast(1)
                topGenres.forEach { (g, n) -> BarLine(g, n, max) }
            }
            if (topAuthors.isNotEmpty()) Panel("Auteurs", icon = Icons.Rounded.Person) {
                topAuthors.forEach { (a, n) -> Line(a, "$n tome${if (n > 1) "s" else ""}") }
            }
            if (topPublishers.isNotEmpty()) Panel("Éditeurs", icon = Icons.Rounded.Store) {
                topPublishers.forEach { (p, n) -> Line(p, "$n tome${if (n > 1) "s" else ""}") }
            }
            if (complete > 0) Panel("Séries complètes", icon = Icons.Rounded.EmojiEvents) {
                all.filter { !it.series.ongoing && it.volumes.isNotEmpty() && it.missingNumbers.isEmpty() }
                    .forEach { Line(it.series.title, "${it.volumes.size} tomes") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Ligne avec une barre proportionnelle (genres). */
@Composable
private fun BarLine(label: String, value: Int, max: Int) {
    val f by animateFloatAsState(value / max.toFloat(), label = "barre")
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$value", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.primary))
        }
    }
}
