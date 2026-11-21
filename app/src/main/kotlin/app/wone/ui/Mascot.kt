package app.wone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/** A mascot look: a squircle pushed in and out by smooth noise. [freq] is how fast the outline wobbles. */
data class MascotLook(val freq: Float, val amp: Float, val sq: Float, val seed: Int) {
    companion object {
        /** The look chosen for the app icon. */
        val Icon = MascotLook(freq = 4.3f, amp = 0.17f, sq = 5.8f, seed = 29)

        /** A new look near the icon's family, for the splash screen. */
        fun random(rnd: kotlin.random.Random = kotlin.random.Random.Default) = MascotLook(
            freq = 3.0f + rnd.nextFloat() * 3.0f,
            amp = 0.11f + rnd.nextFloat() * 0.10f,
            sq = 4.3f + rnd.nextFloat() * 2.2f,
            seed = 1 + rnd.nextInt(60),
        )
    }
}

object MascotShape {
    // The hash mirrors the one in the mascot lab page exactly (double products, then 32-bit wrap),
    // so a look picked there draws the same outline here.
    private fun hash(ix: Int, iy: Int, seed: Int): Double {
        var h = (ix.toLong() * 374761393L + iy.toLong() * 668265263L + seed.toLong() * 1442695041L).toInt()
        h = h xor (h ushr 13)
        h = (h.toDouble() * 1274126177.0).toLong().toInt()
        h = h xor (h ushr 16)
        return ((h.toLong() and 0xFFFFFFFFL).toDouble() / 4294967295.0) * 2 - 1
    }

    private fun fade(t: Double) = t * t * t * (t * (t * 6 - 15) + 10)

    private fun noise(x: Double, y: Double, seed: Int): Double {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val fx = fade(x - x0); val fy = fade(y - y0)
        val a = hash(x0, y0, seed); val b = hash(x0 + 1, y0, seed)
        val c = hash(x0, y0 + 1, seed); val d = hash(x0 + 1, y0 + 1, seed)
        val top = a + (b - a) * fx
        return top + ((c + (d - c) * fx) - top) * fy
    }

    private fun field(x: Double, y: Double, seed: Int) =
        noise(x, y, seed) * 0.75 + noise(x * 2.1 + 5.3, y * 2.1 - 1.7, seed + 11) * 0.25

    /** Outline points on a 108 unit grid, x then y, [n] points around. */
    fun points(look: MascotLook, radius: Float = 31f, n: Int = 360): FloatArray {
        val out = FloatArray(n * 2)
        val sq = look.sq.toDouble(); val freq = look.freq.toDouble(); val amp = look.amp.toDouble()
        for (i in 0 until n) {
            val t = i.toDouble() / n * Math.PI * 2
            val c = cos(t); val s = sin(t)
            val base = radius / (abs(c).pow(sq) + abs(s).pow(sq)).pow(1 / sq)
            val r = base * (1 + amp * field(c * freq + 20, s * freq + 20, look.seed))
            out[i * 2] = (54 + r * c).toFloat()
            out[i * 2 + 1] = (54 + r * s).toFloat()
        }
        return out
    }
}

/** The mascot: body in [body], the w face in [face]. Drawn on a 108 unit grid scaled to [size]. */
@Composable
fun MascotView(look: MascotLook, size: Dp, body: Color, face: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 108f
        val p = MascotShape.points(look)
        val path = Path().apply {
            moveTo(p[0] * u, p[1] * u)
            for (i in 1 until p.size / 2) lineTo(p[i * 2] * u, p[i * 2 + 1] * u)
            close()
        }
        drawPath(path, body)
        drawCircle(face, 3.8f * u, Offset(45f * u, 47f * u))
        drawCircle(face, 3.8f * u, Offset(63f * u, 47f * u))
        val mouth = Path().apply {
            moveTo(42f * u, 57f * u); lineTo(48f * u, 66f * u); lineTo(54f * u, 57f * u); lineTo(60f * u, 66f * u); lineTo(66f * u, 57f * u)
        }
        drawPath(mouth, face, style = Stroke(3.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
