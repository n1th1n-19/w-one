package app.wone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wone.data.KeyboardFx
import app.wone.data.RingFx
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Seconds since start, ticking every frame while [running]. Read it in draw scopes only. */
@Composable
fun rememberClock(running: Boolean): State<Float> {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var start = -1L
        while (true) {
            androidx.compose.runtime.withFrameNanos { n ->
                if (start < 0) start = n
                clock.floatValue = (n - start) / 1_000_000_000f
            }
        }
    }
    return clock
}

fun hueColor(h: Float, sat: Float = 0.85f, value: Float = 1f): Color {
    val x = ((h % 1f) + 1f) % 1f
    return Color.hsv(x * 360f, sat, value)
}

private fun Float.frac() = this - kotlin.math.floor(this)

/** A thin light that runs along the screen edge. Flat segments, no blur or gradient. */
fun Modifier.ringLight(
    fx: RingFx, speed: Float, brightness: Float, accent: Color, clock: State<Float>,
    corner: Dp = 18.dp, stroke: Dp = 3.dp, inset: Dp = 1.5.dp,
): Modifier = if (fx == RingFx.Off) this else drawWithCache {
    val segments = 180
    val i = inset.toPx()
    val path = Path().apply {
        addRoundRect(RoundRect(i, i, size.width - i, size.height - i, CornerRadius(corner.toPx())))
    }
    val m = PathMeasure().apply { setPath(path, true) }
    val len = m.length
    val pts = Array(segments + 1) { k -> m.getPosition(len * k / segments) }
    onDrawWithContent {
        drawContent()
        val t = clock.value
        val period = max(0.5f, speed)
        for (k in 0 until segments) {
            val c = when (fx) {
                RingFx.Wave -> hueColor(k.toFloat() / segments - t / period)
                RingFx.Breathe -> accent.copy(alpha = 0.25f + 0.75f * (0.5f - 0.5f * cos(2 * PI.toFloat() * t / period)))
                RingFx.Static -> accent
                RingFx.Off -> Color.Transparent
            }
            drawLine(c.copy(alpha = c.alpha * brightness), pts[k], pts[k + 1], stroke.toPx(), cap = StrokeCap.Butt)
        }
    }
}

class KeyLight(val index: Int, val label: String, val nx: Float, val ny: Float, val col: Int, val row: Int, val seed: Float)
class Hit(val index: Int, val nx: Float, val ny: Float, val col: Int, val row: Int, val t0: Float)

/**
 * Light colour for one key. Alpha is intensity. [aspect] is keyboard width / height so rings
 * and spirals stay round.
 */
fun keyLight(
    fx: KeyboardFx, k: KeyLight, t: Float, speed: Float, accent: Color, aspect: Float,
    hits: List<Hit>, heat: Map<String, Int>, heatMax: Int,
): Color {
    val tau = 2 * PI.toFloat()
    val ph = t / max(0.5f, speed)
    val cx = k.nx - 0.5f
    val cy = (k.ny - 0.5f) / aspect
    return when (fx) {
        KeyboardFx.Off -> Color.Transparent
        KeyboardFx.Static -> accent
        KeyboardFx.Breathing -> accent.copy(alpha = 0.25f + 0.75f * (0.5f - 0.5f * cos(tau * ph)))
        KeyboardFx.Spectrum -> hueColor(ph)
        KeyboardFx.Wave -> hueColor(k.nx * 0.8f - ph)
        KeyboardFx.Spiral -> {
            val a = atan2(cy, cx) / tau
            hueColor(a + hypot(cx, cy) * 0.6f - ph)
        }
        KeyboardFx.Ripple -> {
            val d = hypot(cx, cy * aspect * 0.7f)
            val v = 0.5f + 0.5f * sin(tau * (d * 2.5f - ph))
            hueColor(d - ph).copy(alpha = 0.2f + 0.8f * v)
        }
        KeyboardFx.Starlight -> {
            val v = sin(tau * (ph * (0.6f + k.seed * 1.4f) + k.seed * 7f)).coerceAtLeast(0f)
            val b = v * v * v * v * v * v
            hueColor(k.seed * 3f).copy(alpha = 0.08f + 0.92f * b)
        }
        KeyboardFx.Rain -> {
            // Each column has a drop that falls and leaves a fading trail.
            val colSeed = ((k.col * 37) % 11) / 11f
            val head = (ph * (0.8f + colSeed) + colSeed * 3f).frac() * 1.5f
            val d = head - k.ny
            val b = if (d in 0f..0.6f) 1f - d / 0.6f else 0f
            Color(0xFF3DFF7A).copy(alpha = 0.1f + 0.9f * b)
        }
        KeyboardFx.Reactive -> {
            var a = 0.08f
            for (h in hits) if (h.index == k.index) a = max(a, exp(-(t - h.t0) * 2.5f))
            accent.copy(alpha = a)
        }
        KeyboardFx.Splash -> {
            var a = 0.06f; var hue = 0f
            for (h in hits) {
                val age = t - h.t0
                val d = hypot(k.nx - h.nx, (k.ny - h.ny) / aspect * 1.0f)
                val r = age * 0.9f
                val v = exp(-abs(d - r) * 14f) * exp(-age * 1.6f)
                if (v > a) { a = v; hue = d * 2f + age }
            }
            if (a <= 0.06f) accent.copy(alpha = 0.06f) else hueColor(hue).copy(alpha = min(1f, a))
        }
        KeyboardFx.Cross -> {
            var a = 0.06f
            for (h in hits) {
                val age = t - h.t0
                if (k.row == h.row || k.col == h.col) {
                    val d = if (k.row == h.row) abs(k.nx - h.nx) else abs(k.ny - h.ny)
                    a = max(a, exp(-age * 2.2f) * exp(-d * 1.4f))
                }
            }
            accent.copy(alpha = a)
        }
        KeyboardFx.Heatmap -> {
            val n = heat[k.label] ?: 0
            if (n == 0) Color(0xFF2A3140).copy(alpha = 0.6f) else {
                val v = n.toFloat() / max(1, heatMax)
                when {
                    v < 0.25f -> Color(0xFF2F6BFF)
                    v < 0.5f -> Color(0xFF00C2FF)
                    v < 0.75f -> Color(0xFF3DDC97)
                    v < 0.9f -> Color(0xFFFFE600)
                    else -> Color(0xFFFF5C7A)
                }
            }
        }
    }
}
