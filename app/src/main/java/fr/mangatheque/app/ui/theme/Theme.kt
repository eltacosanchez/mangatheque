package fr.mangatheque.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.mangatheque.app.R

/**
 * Palette « planche de manga » : papier crème, encre, rouge vif, jaune des titres,
 * bleu ciel et rose — plus coloré que le noir/blanc/rouge d'origine.
 */
object Palette {
    val paper = Color(0xFFFFF6E6)
    val surface = Color(0xFFFFFFFF)
    val ink = Color(0xFF1B1622)
    val muted = Color(0xFF6F6878)
    val line = Color(0xFFE6DAC4)
    val soft = Color(0xFFF6ECDA)
    val accent = Color(0xFFE62E3F)
    val yellow = Color(0xFFFFC83D)
    val blue = Color(0xFF2F9BEA)
    val pink = Color(0xFFFF6B9E)
    val ok = Color(0xFF1F9D63)
    val doing = Color(0xFFF29A00)
    val todo = Color(0xFF8B8494)

    val paperDark = Color(0xFF14111A)
    val surfaceDark = Color(0xFF1F1A27)
    val inkDark = Color(0xFFF6EFE3)
    val mutedDark = Color(0xFFA49CAF)
    val lineDark = Color(0xFF342C40)
    val softDark = Color(0xFF2A2333)
    val accentDark = Color(0xFFFF4F60)
    val yellowDark = Color(0xFFFFD25E)
    val blueDark = Color(0xFF5DB6F5)
    val pinkDark = Color(0xFFFF86B2)
    val okDark = Color(0xFF45CF8E)
    val doingDark = Color(0xFFFFB443)
    val outlineDark = Color(0xFF5E5170)
}

/** Couleurs « métier » et « manga » non couvertes par Material. */
data class ExtraColors(
    val ok: Color,
    val doing: Color,
    val todo: Color,
    val line: Color,
    val muted: Color,
    val soft: Color,
    val yellow: Color,
    val blue: Color,
    val pink: Color,
    /** Trait d'encre des cases et bordures. */
    val outline: Color,
    /** Ombre décalée façon case de planche. */
    val shadow: Color,
    /** Texte posé sur du jaune. */
    val onYellow: Color
)

private val lightExtras = ExtraColors(
    Palette.ok, Palette.doing, Palette.todo, Palette.line, Palette.muted, Palette.soft,
    Palette.yellow, Palette.blue, Palette.pink, Palette.ink, Palette.ink, Palette.ink
)
private val darkExtras = ExtraColors(
    Palette.okDark, Palette.doingDark, Palette.todo, Palette.lineDark, Palette.mutedDark, Palette.softDark,
    Palette.yellowDark, Palette.blueDark, Palette.pinkDark, Palette.outlineDark, Color.Black, Palette.ink
)

val LocalExtraColors = staticCompositionLocalOf { lightExtras }

val MaterialTheme.extra: ExtraColors
    @Composable get() = LocalExtraColors.current

val Display = FontFamily(Font(R.font.dela_gothic_one, FontWeight.Normal))
val Body = FontFamily(
    Font(R.font.zen_kaku_regular, FontWeight.Normal),
    Font(R.font.zen_kaku_medium, FontWeight.Medium),
    Font(R.font.zen_kaku_bold, FontWeight.Bold)
)

private val light: ColorScheme = lightColorScheme(
    primary = Palette.accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DC),
    onPrimaryContainer = Color(0xFF41000A),
    secondary = Palette.yellow,
    onSecondary = Palette.ink,
    secondaryContainer = Color(0xFFFFE9A8),
    onSecondaryContainer = Palette.ink,
    tertiary = Palette.blue,
    onTertiary = Color.White,
    background = Palette.paper,
    onBackground = Palette.ink,
    surface = Palette.surface,
    onSurface = Palette.ink,
    surfaceVariant = Palette.soft,
    onSurfaceVariant = Palette.muted,
    outline = Color(0xFFC9B99C),
    outlineVariant = Palette.line,
    surfaceContainer = Palette.surface,
    surfaceContainerLow = Palette.paper,
    surfaceContainerHigh = Palette.soft,
    surfaceContainerHighest = Palette.soft,
    error = Palette.accent
)

private val dark: ColorScheme = darkColorScheme(
    primary = Palette.accentDark,
    onPrimary = Palette.ink,
    primaryContainer = Color(0xFF6B1018),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = Palette.yellowDark,
    onSecondary = Palette.ink,
    secondaryContainer = Color(0xFF5A4500),
    onSecondaryContainer = Color(0xFFFFE9A8),
    tertiary = Palette.blueDark,
    onTertiary = Palette.ink,
    background = Palette.paperDark,
    onBackground = Palette.inkDark,
    surface = Palette.surfaceDark,
    onSurface = Palette.inkDark,
    surfaceVariant = Palette.softDark,
    onSurfaceVariant = Palette.mutedDark,
    outline = Palette.outlineDark,
    outlineVariant = Palette.lineDark,
    surfaceContainer = Palette.surfaceDark,
    surfaceContainerLow = Palette.paperDark,
    surfaceContainerHigh = Palette.softDark,
    surfaceContainerHighest = Palette.softDark,
    error = Palette.accentDark
)

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontSize = 38.sp, lineHeight = 42.sp),
    displayMedium = TextStyle(fontFamily = Display, fontSize = 30.sp, lineHeight = 34.sp),
    displaySmall = TextStyle(fontFamily = Display, fontSize = 24.sp, lineHeight = 28.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontSize = 26.sp, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontSize = 22.sp, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontSize = 20.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)

@Composable
fun MangathequeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) darkExtras else lightExtras) {
        MaterialTheme(colorScheme = if (darkTheme) dark else light, typography = AppTypography, content = content)
    }
}
