package fr.mangatheque.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import fr.mangatheque.app.ui.theme.Display
import java.io.File
import kotlin.math.abs

/** Paire de couleurs stable dérivée d'une graine (titre de la série). */
fun coverColors(seed: Int): Pair<Color, Color> {
    val palettes = listOf(
        Color(0xFFE0262F) to Color(0xFFF59E0B),
        Color(0xFFF97316) to Color(0xFF1E3A8A),
        Color(0xFF111827) to Color(0xFF7F1D1D),
        Color(0xFF4C1D95) to Color(0xFF0F172A),
        Color(0xFF0E7490) to Color(0xFFA7F3D0),
        Color(0xFFEA580C) to Color(0xFF111827),
        Color(0xFF16A34A) to Color(0xFFF472B6),
        Color(0xFF1D4ED8) to Color(0xFF0F172A),
        Color(0xFF334155) to Color(0xFF94A3B8),
        Color(0xFFBE123C) to Color(0xFF312E81),
        Color(0xFF0F766E) to Color(0xFFFDE68A),
        Color(0xFF7C2D12) to Color(0xFFF59E0B)
    )
    return palettes[abs(seed) % palettes.size]
}

/**
 * Couverture d'un tome ou d'une série : la photo/l'image si elle existe,
 * sinon une couverture générée (dégradé + titre + numéro), comme dans la maquette.
 */
@Composable
fun Cover(
    path: String?,
    title: String,
    number: Int?,
    publisher: String,
    seed: Int,
    modifier: Modifier = Modifier,
    corner: Int = 10,
    badge: String? = null,
    dimmed: Boolean = false
) {
    val shape = RoundedCornerShape(corner.dp)
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (!path.isNullOrBlank()) {
            AsyncImage(
                model = if (path.startsWith("http")) path else File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            GeneratedCover(title, number, publisher, seed)
        }
        if (dimmed) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        if (badge != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(badge, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun GeneratedCover(title: String, number: Int?, publisher: String, seed: Int) {
    val (c1, c2) = coverColors(seed)
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c1, c2)))
    ) {
        val w = maxWidth
        val titleSize = (w.value / 7f).coerceIn(9f, 22f).sp
        val numSize = (w.value / 2.6f).coerceIn(18f, 64f).sp
        val pubSize = (w.value / 14f).coerceIn(6f, 11f).sp
        // dos du livre
        Box(Modifier.fillMaxHeight().width(w * 0.07f).background(Color.Black.copy(alpha = 0.22f)))
        Text(
            title,
            color = Color.White,
            fontFamily = Display,
            fontSize = titleSize,
            lineHeight = titleSize * 1.05f,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(start = w * 0.12f, end = w * 0.08f, top = w * 0.12f)
        )
        if (number != null) {
            Text(
                number.toString(),
                color = Color.White.copy(alpha = 0.95f),
                fontFamily = Display,
                fontSize = numSize,
                lineHeight = numSize,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = w * 0.08f, bottom = w * 0.04f)
            )
        }
        if (publisher.isNotBlank()) {
            Text(
                publisher.uppercase(),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = pubSize,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = w * 0.12f, bottom = w * 0.08f, end = w * 0.45f)
            )
        }
    }
}

/** Case « manquant » en pointillés. */
@Composable
fun MissingCover(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            number.toString(),
            fontFamily = Display,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            "Manquant",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
        )
    }
}
