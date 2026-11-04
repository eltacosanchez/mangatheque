package fr.mangatheque.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Un morceau de confetti : départ, vitesse, rotation, couleur, forme. */
private class Piece(
    val x0: Float, val y0: Float,     // départ, en fraction de l'écran
    val vx: Float, val vy: Float,     // vitesse initiale (fraction d'écran par seconde)
    val spin: Float, val sway: Float, // rotation (°/s) et balancement
    val color: Color, val w: Float, val h: Float, val round: Boolean, val delay: Float
)

/**
 * Pluie de confettis (environ 3,5 secondes) : explosion depuis le haut,
 * puis les morceaux retombent en tournoyant et en se balançant.
 */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier.fillMaxSize(), count: Int = 150) {
    val colors = listOf(
        Color(0xFFE0262F), Color(0xFFFF4A52), Color(0xFFFFC83D), Color(0xFF3FC489),
        Color(0xFF2F9BEA), Color(0xFFFF6B9E), Color(0xFFFFFFFF), Color(0xFFA06CFF)
    )
    val pieces = remember {
        val r = Random(System.nanoTime())
        List(count) {
            val angle = Math.toRadians(r.nextDouble(200.0, 340.0)) // vers le haut, en éventail
            val speed = r.nextFloat() * 0.9f + 0.5f
            Piece(
                x0 = 0.5f + (r.nextFloat() - 0.5f) * 0.3f, y0 = 0.32f + r.nextFloat() * 0.05f,
                vx = (cos(angle) * speed).toFloat() * 0.6f, vy = (sin(angle) * speed).toFloat() * 0.55f,
                spin = r.nextFloat() * 720f - 360f, sway = r.nextFloat() * 6f + 2f,
                color = colors[r.nextInt(colors.size)], w = r.nextFloat() * 8f + 6f, h = r.nextFloat() * 10f + 6f,
                round = r.nextInt(4) == 0, delay = r.nextFloat() * 0.25f
            )
        }
    }
    var start by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        start = withFrameNanos { it }
        while (true) {
            now = withFrameNanos { it }
            if ((now - start) / 1e9f > 4f) break
        }
    }
    if (start == 0L) return
    val t = ((now - start) / 1e9f).coerceAtLeast(0f)
    Canvas(modifier) {
        val gravity = 1.1f // fraction d'écran par seconde²
        pieces.forEach { p ->
            val tt = t - p.delay
            if (tt <= 0f) return@forEach
            // Freinage de l'air : la vitesse horizontale s'atténue, la chute se stabilise
            val drag = 1f - kotlin.math.exp(-tt * 1.6f)
            val x = (p.x0 + p.vx * drag / 1.6f + sin(tt * p.sway) * 0.015f) * size.width
            val y = (p.y0 + p.vy * drag / 1.6f + 0.5f * gravity * tt * tt * 0.55f) * size.height
            if (y > size.height + 40f) return@forEach
            val alpha = (1f - ((tt - 2.8f) / 1f).coerceIn(0f, 1f)) // disparition en douceur
            val wPx = p.w * density * 0.6f; val hPx = p.h * density * 0.6f * (0.4f + 0.6f * kotlin.math.abs(sin(tt * 6f + p.sway)))
            rotate(p.spin * tt, Offset(x, y)) {
                if (p.round) drawCircle(p.color.copy(alpha = alpha), wPx / 2f, Offset(x, y))
                else drawRect(p.color.copy(alpha = alpha), Offset(x - wPx / 2f, y - hPx / 2f), Size(wPx, hPx))
            }
        }
    }
}

