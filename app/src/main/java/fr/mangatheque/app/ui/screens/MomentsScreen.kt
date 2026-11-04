package fr.mangatheque.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.mangatheque.app.data.MomentWithContext
import fr.mangatheque.app.ui.AppViewModel
import fr.mangatheque.app.ui.components.Brand
import fr.mangatheque.app.ui.components.EmptyState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import fr.mangatheque.app.ui.components.plural
import java.io.File

/** Galerie de tous les passages préférés, tous mangas confondus. */
@Composable
fun MomentsScreen(vm: AppViewModel, onOpenVolume: (Long) -> Unit) {
    val all by vm.moments.collectAsStateWithLifecycle()
    var zoom by remember { mutableStateOf<MomentWithContext?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Brand("Mes ", "passages", Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    "Touche une photo pour l'agrandir",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(plural(all.size, "photo"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (all.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Rounded.PhotoLibrary, "Aucun passage.\nAjoute-en depuis la fiche d'un tome.")
            }
        }
        items(all, key = { it.moment.id }) { m ->
            Column {
                AsyncImage(
                    File(m.moment.photoPath), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { zoom = m }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${m.seriesTitle} · T${m.volumeNumber}" + (m.moment.page?.let { " · p. $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (m.moment.caption.isNotBlank()) Text(m.moment.caption, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onOpenVolume(m.moment.volumeId) }, contentPadding = PaddingValues(0.dp)) { Text("Ouvrir le tome", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }

    zoom?.let { m ->
        val context = androidx.compose.ui.platform.LocalContext.current
        Lightbox(
            m.moment.photoPath, "${m.seriesTitle} · tome ${m.volumeNumber}" + (m.moment.page?.let { " · page $it" } ?: ""), m.moment.caption,
            onShare = { shareMoment(context, m.moment, "${m.seriesTitle} · tome ${m.volumeNumber}") }
        ) { zoom = null }
    }
}
