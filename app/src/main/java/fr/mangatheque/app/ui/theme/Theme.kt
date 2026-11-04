package fr.mangatheque.app.ui.theme

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

/** Palette d'origine de la maquette : blanc, encre et rouge. */
object Palette {
    val paper = Color(0xFFF3F1EC)
    val surface = Color(0xFFFFFFFF)
    val ink = Color(0xFF16151A)
    val muted = Color(0xFF6C6A72)
    val line = Color(0xFFE2DFD8)
    val accent = Color(0xFFE0262F)
    val soft = Color(0xFFECE9E2)
    val ok = Color(0xFF1F8A5B)
    val doing = Color(0xFFD98A00)
    val todo = Color(0xFF8B8893)

    val paperDark = Color(0xFF111015)
    val surfaceDark = Color(0xFF1B1A21)
    val inkDark = Color(0xFFF2F0EA)
    val mutedDark = Color(0xFF9D9AA5)
    val lineDark = Color(0xFF2C2A33)
    val accentDark = Color(0xFFFF4A52)
    val softDark = Color(0xFF24222B)
    val okDark = Color(0xFF3FC489)
    val doingDark = Color(0xFFF2A93B)
    val outlineDark = Color(0xFF4A4753)
}

/** Couleurs « métier » non couvertes par Material. */
data class ExtraColors(
    val ok: Color,
    val doing: Color,
    val todo: Color,
    val line: Color,
    val muted: Color,
    val soft: Color,
    /** Couleur des éléments actifs (boutons sélectionnés, étiquettes, logo) : le rouge. */
    val pop: Color,
    /** Texte posé sur [pop] : le blanc. */
    val onPop: Color,
    /** Trait d'encre des cases et bordures. */
    val outline: Color,
    /** Ombre décalée façon case de planche. */
    val shadow: Color
)

private val lightExtras = ExtraColors(
    Palette.ok, Palette.doing, Palette.todo, Palette.line, Palette.muted, Palette.soft,
    Palette.accent, Color.White, Palette.ink, Palette.ink
)
private val darkExtras = ExtraColors(
    Palette.okDark, Palette.doingDark, Palette.todo, Palette.lineDark, Palette.mutedDark, Palette.softDark,
    Palette.accentDark, Color.White, Palette.outlineDark, Color.Black
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
    primaryContainer = Color(0xFFFFDAD8),
    onPrimaryContainer = Color(0xFF410006),
    secondary = Palette.ink,
    onSecondary = Palette.paper,
    secondaryContainer = Color(0xFFFFE4E2),
    onSecondaryContainer = Palette.ink,
    tertiary = Palette.accent,
    onTertiary = Color.White,
    background = Palette.paper,
    onBackground = Palette.ink,
    surface = Palette.surface,
    onSurface = Palette.ink,
    surfaceVariant = Palette.soft,
    onSurfaceVariant = Palette.muted,
    outline = Color(0xFFC8C4BB),
    outlineVariant = Palette.line,
    surfaceContainer = Palette.surface,
    surfaceContainerLow = Palette.paper,
    surfaceContainerHigh = Palette.soft,
    surfaceContainerHighest = Palette.soft,
    error = Palette.accent
)

private val dark: ColorScheme = darkColorScheme(
    primary = Palette.accentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6B1016),
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = Palette.inkDark,
    onSecondary = Palette.paperDark,
    secondaryContainer = Color(0xFF3A1A1E),
    onSecondaryContainer = Palette.inkDark,
    tertiary = Palette.accentDark,
    onTertiary = Color.White,
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
    displayMedium = TextStyle(fontFamily = Display, fontSize = 28.sp, lineHeight = 32.sp),
    displaySmall = TextStyle(fontFamily = Display, fontSize = 22.sp, lineHeight = 26.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontSize = 23.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontSize = 20.sp, lineHeight = 24.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontSize = 18.sp, lineHeight = 22.sp),
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
fun MangathequeTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) darkExtras else lightExtras) {
        MaterialTheme(colorScheme = if (darkTheme) dark else light, typography = AppTypography, content = content)
    }
}
