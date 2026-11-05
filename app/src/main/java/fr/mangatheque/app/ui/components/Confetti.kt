package fr.mangatheque.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Un petit morceau de confetti. */
private class Bit(
    val sx: Float, val sy: Float,       // départ, en fraction de l'élément (sur son contour)
    val dx: Float, val dy: Float,       // direction de départ (unitaire, vers l'extérieur)
    val speed: Float,                   // vitesse de départ, en dp/s
    val spin: Float, val phase: Float,  // rotation (°/s) et décalage du battement
    val color: Color, val w: Float, val h: Float, val round: Boolean, val delay: Float
)

private val palette = listOf(
    Color(0xFFE0262F), Color(0xFFFFC83D), Color(0xFF3FC489), Color(0xFF2F9BEA),
    Color(0xFFFF6B9E), Color(0xFFA06CFF), Color(0xFFFF8A3D)
)

private const val DURATION = 1.3f // secondes

/**
 * Petite gerbe de confettis tout autour d'un élément (≈ 1,3 s), partie à [startNanos]
 * (horloge `System.nanoTime()`). Les morceaux, petits, partent du contour vers l'extérieur,
 * ralentissent, retombent doucement et s'effacent. À poser en `matchParentSize()`
 * par-dessus l'élément : le dessin déborde autour de lui.
 */
@Composable
fun ConfettiPop(startNanos: Long, modifier: Modifier = Modifier, count: Int = 22) {
    var t by remember(startNanos) { mutableFloatStateOf((System.nanoTime() - startNanos) / 1e9f) }
    LaunchedEffect(startNanos) {
        while (t <= DURATION) {
            withFrameNanos { }
            t = (System.nanoTime() - startNanos) / 1e9f
        }
    }
    if (t > DURATION) return
    val bits = remember(startNanos) {
        val r = Random(startNanos)
        List(count) {
            // Point de départ sur le contour (couverture 2:3 : plus de morceaux sur les grands côtés)
            val p = r.nextFloat() * 10f
            val u = r.nextFloat()
            val (sx, sy) = when {
                p < 2f -> u to 0.02f
                p < 5f -> 0.98f to u
                p < 7f -> u to 0.98f
                else -> 0.02f to u
            }
            // Direction : depuis le centre vers ce point, avec un peu de hasard
            var vx = sx - 0.5f; var vy = (sy - 0.5f) * 1.5f
            val n = hypot(vx, vy).coerceAtLeast(0.001f); vx /= n; vy /= n
            val a = (r.nextFloat() - 0.5f) * 0.9f
            Bit(
                sx, sy,
                dx = vx * cos(a) - vy * sin(a), dy = vx * sin(a) + vy * cos(a),
                speed = 45f + r.nextFloat() * 60f,
                spin = r.nextFloat() * 540f - 270f, phase = r.nextFloat() * 6.28f,
                color = palette[r.nextInt(palette.size)],
                w = 3f + r.nextFloat() * 2f, h = 2f + r.nextFloat() * 2f,
                round = r.nextInt(3) == 0, delay = r.nextFloat() * 0.12f
            )
        }
    }
    Canvas(modifier) {
        val d = density
        bits.forEach { b ->
            val tt = t - b.delay
            if (tt <= 0f) return@forEach
            val travel = b.speed / 3.2f * (1f - exp(-3.2f * tt)) // freinage de l'air
            val fall = 0.5f * 38f * tt * tt                       // gravité douce
            val x = b.sx * size.width + (b.dx * travel + sin(tt * 8f + b.phase) * 1.5f) * d
            val y = b.sy * size.height + (b.dy * travel + fall) * d
            val alpha = if (tt < 0.08f) tt / 0.08f else 1f - ((tt - (DURATION - 0.6f)) / 0.5f).coerceIn(0f, 1f)
            val c = b.color.copy(alpha = alpha.coerceIn(0f, 1f))
            if (b.round) {
                drawCircle(c, b.w * d * 0.38f, Offset(x, y))
            } else {
                val w = b.w * d
                val h = b.h * d * (0.35f + 0.65f * abs(sin(tt * 10f + b.phase))) // battement
                rotate(b.spin * tt, Offset(x, y)) { drawRect(c, Offset(x - w / 2f, y - h / 2f), Size(w, h)) }
            }
        }
    }
}
