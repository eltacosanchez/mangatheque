package fr.mangatheque.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.mangatheque.app.data.ReadStatus
import fr.mangatheque.app.ui.theme.extra

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

/** Titre de page façon maquette : « Ma Manga<thèque> » avec la fin en rouge. */
@Composable
fun Brand(first: String, accent: String, modifier: Modifier = Modifier) {
    Row(modifier) {
        Text(first, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(accent, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
    }
}

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
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Puce de filtre (genre…), style pilule. */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .border(1.dp, if (selected) bg else MaterialTheme.colorScheme.outline, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Sélecteur segmenté façon maquette. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val sel = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Pill(text: String, color: Color? = null, modifier: Modifier = Modifier) {
    val c = color ?: MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (color != null) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (color != null) Color.Transparent else MaterialTheme.colorScheme.outline, RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c, maxLines = 1)
    }
}

/** Barre de progression lus / possédés / parus. */
@Composable
fun ProgressBar(read: Int, owned: Int, published: Int, modifier: Modifier = Modifier) {
    val total = maxOf(published, owned, 1).toFloat()
    Row(
        modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (read > 0) Box(Modifier.fillMaxWidth(read / total).height(5.dp).background(MaterialTheme.extra.ok))
        val rest = owned - read
        if (rest > 0) Box(Modifier.fillMaxWidth((rest / total) / (1f - read / total).coerceAtLeast(0.0001f)).height(5.dp).background(MaterialTheme.extra.muted.copy(alpha = 0.45f)))
    }
}

@Composable
fun SectionHeader(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Panel(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        if (title != null) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp))
            Spacer(Modifier.height(10.dp))
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
