package fr.mangatheque.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mangatheque.app.data.ReadStatus
import fr.mangatheque.app.ui.theme.extra
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun statusColor(s: ReadStatus): Color = when (s) {
    ReadStatus.LU -> MaterialTheme.extra.ok
    ReadStatus.EN_COURS -> MaterialTheme.extra.doing
    ReadStatus.A_LIRE -> MaterialTheme.extra.todo
}

@Composable
fun StatusDot(s: ReadStatus, modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp).clip(CircleShape).background(statusColor(s)))
}

// ------------------------------------------------------------------ Effets « manga »

/**
 * Trame de points (« screentone ») comme sur les planches de manga.
 * [fade] : les points rétrécissent vers le bas (dégradé de trame).
 * [onTop] : la trame est dessinée par-dessus le contenu (ex. sur une image).
 */
fun Modifier.halftone(
    color: Color,
    spacing: Dp = 9.dp,
    radius: Dp = 1.5.dp,
    fade: Boolean = false,
    onTop: Boolean = false
): Modifier = this.drawWithCache {
    val step = spacing.toPx()
    val r = radius.toPx()
    val path = Path()
    val h = size.height.coerceAtLeast(1f)
    var y = 0f
    var row = 0
    while (y <= size.height + step) {
        val rr = if (fade) r * (1f - (y / h).coerceIn(0f, 1f)) else r
        if (rr > 0.3f) {
            var x = if (row % 2 == 0) 0f else step / 2f
            while (x <= size.width + step) {
                path.addOval(Rect(Offset(x, y), rr))
                x += step
            }
        }
        y += step * 0.866f
        row++
    }
    onDrawWithContent {
        if (onTop) { drawContent(); drawPath(path, color) } else { drawPath(path, color); drawContent() }
    }
}

/** Case de planche : trait d'encre épais + ombre décalée. */
@Composable
fun Modifier.mangaPanel(
    corner: Dp = 14.dp,
    background: Color = MaterialTheme.colorScheme.surface,
    shadow: Dp = 3.dp,
    border: Dp = 2.dp
): Modifier {
    val ex = MaterialTheme.extra
    val shape = RoundedCornerShape(corner)
    return this
        .drawBehind {
            val o = shadow.toPx()
            if (o > 0f) drawRoundRect(ex.shadow, topLeft = Offset(o, o), size = size, cornerRadius = CornerRadius(corner.toPx()))
        }
        .clip(shape)
        .background(background)
        .border(border, ex.outline, shape)
}

/** Lignes de vitesse rayonnantes, typiques des cases d'action. */
@Composable
fun SpeedLines(modifier: Modifier = Modifier, color: Color, count: Int = 56, seed: Long = 7L) {
    Canvas(modifier.clipToBounds()) {
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val maxR = hypot(size.width, size.height)
        val rnd = java.util.Random(seed)
        repeat(count) { i ->
            val a = (i.toDouble() / count) * 2 * PI + rnd.nextDouble() * 0.07
            val inner = maxR * (0.30f + rnd.nextFloat() * 0.2f)
            val half = 0.004 + rnd.nextDouble() * 0.012
            val p = Path().apply {
                moveTo(center.x + (cos(a) * inner).toFloat(), center.y + (sin(a) * inner).toFloat())
                lineTo(center.x + (cos(a - half) * maxR).toFloat(), center.y + (sin(a - half) * maxR).toFloat())
                lineTo(center.x + (cos(a + half) * maxR).toFloat(), center.y + (sin(a + half) * maxR).toFloat())
                close()
            }
            drawPath(p, color)
        }
    }
}

/** Texte façon logo de manga : remplissage coloré + contour d'encre. */
@Composable
fun MangaTitle(text: String, style: TextStyle, fill: Color, outline: Color, modifier: Modifier = Modifier, stroke: Dp = 5.dp) {
    val sw = with(LocalDensity.current) { stroke.toPx() }
    Box(modifier) {
        Text(text, style = style.copy(color = outline, drawStyle = Stroke(width = sw, join = StrokeJoin.Round)))
        Text(text, style = style.copy(color = fill))
    }
}

/** Titre de page : « Ma Manga » à l'encre + « thèque » en jaune cerné, comme un logo. */
@Composable
fun Brand(first: String, accent: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(first, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
        MangaTitle(accent, MaterialTheme.typography.displayMedium, fill = MaterialTheme.extra.pop, outline = MaterialTheme.extra.shadow)
    }
}

/** Bulle de dialogue (messages, conseils). */
@Composable
fun SpeechBubble(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val outline = MaterialTheme.extra.outline
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().mangaPanel(corner = 22.dp, background = background, shadow = 0.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            content = content
        )
        Canvas(Modifier.padding(start = 34.dp).offset(y = (-2).dp).size(width = 26.dp, height = 16.dp)) {
            val fill = Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width * 0.15f, size.height); close() }
            drawPath(fill, background)
            val edges = Path().apply { moveTo(0f, 0f); lineTo(size.width * 0.15f, size.height); lineTo(size.width, 0f) }
            drawPath(edges, outline, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

// ------------------------------------------------------------------ Composants

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        shape = RoundedCornerShape(99.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.extra.outline,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Bouton de choix en relief (bordure + ombre décalée).
 * Actif : rouge plein, texte blanc, légèrement « enfoncé ». Inactif : blanc, prêt à être appuyé.
 */
@Composable
fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ex = MaterialTheme.extra
    val pressed = if (selected) 1.5.dp else 0.dp
    Box(
        modifier
            .padding(end = 3.dp, bottom = 3.dp) // place pour l'ombre
            .offset(x = pressed, y = pressed)
            .mangaPanel(
                corner = 99.dp,
                background = if (selected) ex.pop else MaterialTheme.colorScheme.surface,
                shadow = if (selected) 1.5.dp else 3.dp,
                border = if (selected) 2.dp else 1.5.dp
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
            color = if (selected) ex.onPop else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** Puce de filtre (genre…). */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    ChoiceButton(label, selected, onClick, modifier)

/** Groupe de boutons de choix (un seul actif). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) -> ChoiceButton(label, value == selected, { onSelect(value) }) }
    }
}

@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.mangaPanel(corner = 12.dp, shadow = 2.dp, border = 1.5.dp).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Pill(text: String, color: Color? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = color ?: MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(if (color != null) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
            .border(1.5.dp, if (color != null) color.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c, maxLines = 1)
    }
}

/** Barre de progression : lus (vert) / possédés non lus (gris) / parus non possédés (vide). */
@Composable
fun ProgressBar(read: Int, owned: Int, published: Int, modifier: Modifier = Modifier) {
    val total = maxOf(published, owned, 1)
    val rest = (owned - read).coerceAtLeast(0)
    val empty = (total - owned).coerceAtLeast(0)
    Row(
        modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(9.dp))
            .border(1.dp, MaterialTheme.extra.outline.copy(alpha = 0.5f), RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (read > 0) Box(Modifier.weight(read.toFloat()).height(6.dp).background(MaterialTheme.extra.ok))
        if (rest > 0) Box(Modifier.weight(rest.toFloat()).height(6.dp).background(MaterialTheme.extra.muted.copy(alpha = 0.45f)))
        if (empty > 0) Spacer(Modifier.weight(empty.toFloat()))
    }
}

@Composable
fun SectionHeader(title: String, sub: String? = null, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(6.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
        else if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Panneau = case de planche avec une étiquette jaune en guise de titre (comme un encadré de narration). */
@Composable
fun Panel(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val ex = MaterialTheme.extra
    Column(modifier.fillMaxWidth().mangaPanel().padding(16.dp)) {
        if (title != null) {
            Box(
                Modifier.clip(RoundedCornerShape(6.dp)).background(ex.pop)
                    .border(1.5.dp, ex.outline, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ex.onPop, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
fun VSpace(dp: Int) = Spacer(Modifier.height(dp.dp))

@Composable
fun HSpace(dp: Int) = Spacer(Modifier.width(dp.dp))

/** Plages compactes « 1–12, 15, 18–20 ». */
fun ranges(list: List<Int>): String {
    if (list.isEmpty()) return "aucun"
    val sorted = list.sorted()
    val out = mutableListOf<String>()
    var a = sorted[0]; var b = sorted[0]
    for (n in sorted.drop(1)) {
        if (n == b + 1) b = n else { out += if (a == b) "$a" else "$a–$b"; a = n; b = n }
    }
    out += if (a == b) "$a" else "$a–$b"
    return out.joinToString(", ")
}

fun plural(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n > 1) many else one}"
