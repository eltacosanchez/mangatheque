package fr.mangatheque.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

// ------------------------------------------------------------------ Effets

/**
 * Trame de points (« screentone ») comme sur les planches de manga.
 * [fade] : les points rétrécissent vers le bas. [onTop] : dessinée par-dessus le contenu.
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

/** Carte moderne : coins arrondis, bordure fine et discrète, ombre douce facultative. */
@Composable
fun Modifier.mangaPanel(
    corner: Dp = 20.dp,
    background: Color = MaterialTheme.colorScheme.surface,
    shadow: Dp = 0.dp,
    border: Dp = 1.dp
): Modifier {
    val shape = RoundedCornerShape(corner)
    val line = MaterialTheme.colorScheme.outlineVariant
    return this
        .then(if (shadow > 0.dp) Modifier.shadow(shadow, shape, clip = false) else Modifier)
        .clip(shape)
        .background(background)
        .then(if (border > 0.dp) Modifier.border(border, line, shape) else Modifier)
}

/** Clic avec effet « enfoncé » (léger rétrécissement), et appui long facultatif. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bounceClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "appui")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = LocalIndication.current,
            onLongClick = onLongClick,
            onClick = onClick
        )
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

/** Texte façon logo de manga : remplissage coloré + contour. */
@Composable
fun MangaTitle(text: String, style: TextStyle, fill: Color, outline: Color, modifier: Modifier = Modifier, stroke: Dp = 5.dp) {
    val sw = with(LocalDensity.current) { stroke.toPx() }
    Box(modifier) {
        Text(text, style = style.copy(color = outline, drawStyle = Stroke(width = sw, join = StrokeJoin.Round)))
        Text(text, style = style.copy(color = fill))
    }
}

/** Titre de page : « Ma Manga » en blanc/encre + « thèque » en rouge, comme un logo. */
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
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().mangaPanel(corner = 22.dp, background = background).padding(horizontal = 16.dp, vertical = 12.dp),
            content = content
        )
        Canvas(Modifier.padding(start = 34.dp).offset(y = (-1).dp).size(width = 22.dp, height = 12.dp)) {
            val fill = Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width * 0.15f, size.height); close() }
            drawPath(fill, background)
            val edges = Path().apply { moveTo(0f, 0f); lineTo(size.width * 0.15f, size.height); lineTo(size.width, 0f) }
            drawPath(edges, line, style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round))
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
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Effacer la recherche") } }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(99.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Bouton de choix moderne : teinté quand inactif, rouge plein avec ombre douce quand actif.
 * La couleur change en douceur.
 */
@Composable
fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(99.dp)
    val bg by animateColorAsState(if (selected) cs.primary else cs.surfaceVariant, label = "fond")
    val fg by animateColorAsState(if (selected) cs.onPrimary else cs.onSurface, label = "texte")
    Box(
        modifier
            .padding(vertical = 4.dp)
            .then(if (selected) Modifier.shadow(6.dp, shape, ambientColor = cs.primary, spotColor = cs.primary) else Modifier)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

/** Puce de filtre (genre…). */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    ChoiceButton(label, selected, onClick, modifier)

/** Groupe de boutons de choix (un seul actif). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) -> ChoiceButton(label, value == selected, { onSelect(value) }) }
    }
}

@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
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
            .background(color?.copy(alpha = 0.15f) ?: MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c, maxLines = 1)
    }
}

/** Barre de progression animée : lus (vert) par-dessus possédés (gris), sur le nombre de tomes parus. */
@Composable
fun ProgressBar(read: Int, owned: Int, published: Int, modifier: Modifier = Modifier) {
    val total = maxOf(published, owned, 1).toFloat()
    val ownedF by animateFloatAsState((owned / total).coerceIn(0f, 1f), label = "possédés")
    val readF by animateFloatAsState((read / total).coerceIn(0f, 1f), label = "lus")
    val shape = RoundedCornerShape(99.dp)
    Box(modifier.fillMaxWidth().height(6.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (ownedF > 0f) Box(Modifier.fillMaxWidth(ownedF).fillMaxHeight().clip(shape).background(MaterialTheme.extra.muted.copy(alpha = 0.45f)))
        if (readF > 0f) Box(Modifier.fillMaxWidth(readF).fillMaxHeight().clip(shape).background(MaterialTheme.extra.ok))
    }
}

@Composable
fun SectionHeader(title: String, sub: String? = null, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
        else if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Petite icône dans une pastille ronde teintée (titres de panneaux, états vides). */
@Composable
fun IconBadge(icon: ImageVector, size: Dp = 32.dp, color: Color = MaterialTheme.colorScheme.primary) {
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/** Panneau : carte arrondie avec un titre simple (icône + texte), sans bloc de couleur. */
@Composable
fun Panel(
    title: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxWidth().mangaPanel().padding(16.dp)) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { IconBadge(icon); Spacer(Modifier.width(10.dp)) }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

/** État vide : grande icône douce + phrase courte. */
@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, size = 72.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
