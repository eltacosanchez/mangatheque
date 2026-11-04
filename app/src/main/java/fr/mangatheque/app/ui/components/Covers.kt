package fr.mangatheque.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import fr.mangatheque.app.ui.theme.Display
import fr.mangatheque.app.ui.theme.extra
import java.io.File
import kotlin.math.abs

/** Paire de couleurs stable dérivée d'une graine (titre de la série). */
fun coverColors(seed: Int): Pair<Color, Color> {
    val palettes = listOf(
        Color(0xFFE62E3F) to Color(0xFFFFC83D),
        Color(0xFFF97316) to Color(0xFF1E3A8A),
        Color(0xFF111827) to Color(0xFFB91C1C),
        Color(0xFF6D28D9) to Color(0xFF0F172A),
        Color(0xFF0E7490) to Color(0xFF86EFAC),
        Color(0xFFEA580C) to Color(0xFF111827),
        Color(0xFF16A34A) to Color(0xFFFF6B9E),
        Color(0xFF2F9BEA) to Color(0xFF0F172A),
        Color(0xFF334155) to Color(0xFF94A3B8),
        Color(0xFFBE123C) to Color(0xFF312E81),
        Color(0xFF0F766E) to Color(0xFFFDE68A),
        Color(0xFFFF6B9E) to Color(0xFF7C3AED)
    )
    return palettes[abs(seed) % palettes.size]
}

/**
 * Couverture d'un tome ou d'une série : la photo/l'image si elle existe,
 * sinon une couverture générée (dégradé + trame + titre + numéro). Bordure d'encre façon case.
 */
@Composable
fun Cover(
    path: String?,
    title: String,
    number: Int?,
    publisher: String,
    seed: Int,
    modifier: Modifier = Modifier,
    corner: Int = 12,
    badge: String? = null,
    dimmed: Boolean = false,
    badgeColor: Color? = null
) {
    val shape = RoundedCornerShape(corner.dp)
    val ex = MaterialTheme.extra
    val description = "Couverture de $title" + (number?.let { ", tome $it" } ?: "")
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .semantics { contentDescription = description }
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
                    .background(badgeColor ?: ex.pop)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(badge, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun GeneratedCover(title: String, number: Int?, publisher: String, seed: Int) {
    val (c1, c2) = coverColors(seed)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(c1, c2)))
            .halftone(Color.White.copy(alpha = 0.13f), spacing = 7.dp, radius = 1.3.dp, onTop = false)
    ) {
        val w = maxWidth
        val titleSize = (w.value / 7f).coerceIn(9f, 22f).sp
        val numSize = (w.value / 2.6f).coerceIn(18f, 64f).sp
        val pubSize = (w.value / 14f).coerceIn(6f, 11f).sp
        Box(Modifier.fillMaxHeight().width(w * 0.07f).background(Color.Black.copy(alpha = 0.22f)))
        Text(
            title, color = Color.White, fontFamily = Display, fontSize = titleSize, lineHeight = titleSize * 1.05f,
            maxLines = 4, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(start = w * 0.12f, end = w * 0.08f, top = w * 0.12f)
        )
        if (number != null) {
            MangaTitle(
                number.toString(),
                MaterialTheme.typography.displayLarge.copy(fontSize = numSize, lineHeight = numSize),
                fill = Color.White, outline = Color.Black.copy(alpha = 0.55f), stroke = (w.value / 30f).coerceIn(2f, 5f).dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = w * 0.08f, bottom = w * 0.04f)
            )
        }
        if (publisher.isNotBlank()) {
            Text(
                publisher.uppercase(), color = Color.White.copy(alpha = 0.85f), fontSize = pubSize, letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = w * 0.12f, bottom = w * 0.08f, end = w * 0.45f)
            )
        }
    }
}

/**
 * Case d'un tome manquant : la vraie couverture en gris estompé si on la connaît,
 * sinon une case en pointillés avec le numéro.
 */
@Composable
fun MissingCover(number: Int, modifier: Modifier = Modifier, imageUrl: String? = null, label: String = "Manquant") {
    if (imageUrl != null) {
        val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
        val shape = RoundedCornerShape(12.dp)
        Box(
            modifier.aspectRatio(2f / 3f).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        ) {
            AsyncImage(
                model = imageUrl, contentDescription = "Couverture du tome $number (manquant)", contentScale = ContentScale.Crop,
                colorFilter = gray, modifier = Modifier.fillMaxSize().alpha(0.45f)
            )
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(RoundedCornerShape(99.dp))
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 2.dp)
            ) { Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White) }
        }
        return
    }
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .halftone(MaterialTheme.extra.outline.copy(alpha = 0.10f), spacing = 8.dp, radius = 1.2.dp)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(number.toString(), fontFamily = Display, fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
        )
    }
}

/**
 * Fond de fiche : l'image de l'œuvre, estompée (désaturée, floutée, trame de points),
 * qui se fond dans le papier vers le bas pour ne pas gêner la lecture.
 */
@Composable
fun Backdrop(path: String?, seed: Int, modifier: Modifier = Modifier, height: Dp = 420.dp) {
    val bg = MaterialTheme.colorScheme.background
    val matte = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.7f) }) }
    Box(modifier.fillMaxWidth().height(height)) {
        if (!path.isNullOrBlank()) {
            AsyncImage(
                model = if (path.startsWith("http")) path else File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -0.35f),
                colorFilter = matte,
                modifier = Modifier.matchParentSize().blur(2.dp).alpha(0.45f)
            )
        } else {
            val (c1, c2) = coverColors(seed)
            Box(Modifier.matchParentSize().alpha(0.22f).background(Brush.linearGradient(listOf(c1, c2))))
        }
        Box(Modifier.matchParentSize().halftone(bg.copy(alpha = 0.45f), spacing = 5.dp, radius = 1.1.dp))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to bg.copy(alpha = 0.15f), 0.5f to bg.copy(alpha = 0.6f), 1f to bg)))
    }
}
