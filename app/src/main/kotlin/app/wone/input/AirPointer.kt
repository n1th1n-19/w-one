package app.wone.input

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Turns phone rotation into cursor motion. It works on how much the phone turned between two
 * samples, so there is no absolute position to drift: lift your hand, let go of the clutch,
 * and start again from wherever you are.
 */
class AirPointer(private val out: (dx: Int, dy: Int) -> Unit) {

    var sensitivity = 1f
    var invertX = false
    var invertY = false

    /** 0, 1 (90 degrees), 2 or 3 (270), as Surface.ROTATION_*. */
    var displayRotation = 1

    private var havePrev = false
    private var pw = 0f; private var px = 0f; private var py = 0f; private var pz = 0f
    private var smoothX = 0f
    private var smoothY = 0f
    private var fracX = 0f
    private var fracY = 0f

    private val pxPerRadian = 1800f
    // Hand tremor below this turn rate (radians per second) is ignored.
    private val deadzoneRate = 0.025f
    private val smoothing = 0.55f

    /** Call when the clutch is pressed so the first sample does not jump. */
    fun reset() {
        havePrev = false; smoothX = 0f; smoothY = 0f; fracX = 0f; fracY = 0f
    }

    /** [w],[x],[y],[z] is the device-to-world quaternion; [dtSec] the time since the last sample. */
    fun onQuaternion(w: Float, x: Float, y: Float, z: Float, dtSec: Float) {
        if (havePrev) {
            // dq = conj(prev) * cur: the turn between samples, expressed in the device's own axes.
            val dw = pw * w + px * x + py * y + pz * z
            val dx = pw * x - px * w - py * z + pz * y
            val dy = pw * y + px * z - py * w - pz * x
            val dz = pw * z - px * y + py * x - pz * w
            val s = if (dw < 0f) -2f else 2f
            rotate(dx * s, dy * s, dz * s, dtSec)
        }
        pw = w; px = x; py = y; pz = z
        havePrev = true
    }

    /** Fallback for phones without a game rotation vector: turn rates in rad/s on device axes. */
    fun onGyro(wx: Float, wy: Float, wz: Float, dtSec: Float) = rotate(wx * dtSec, wy * dtSec, wz * dtSec, dtSec)

    private fun rotate(ax: Float, ay: Float, az: Float, dt: Float) {
        // Device axes to screen axes, so it works in either landscape direction.
        val sx: Float; val sy: Float
        when (displayRotation) {
            1 -> { sx = ay; sy = -ax }
            3 -> { sx = -ay; sy = ax }
            2 -> { sx = -ax; sy = -ay }
            else -> { sx = ax; sy = ay }
        }
        // Turn right moves right; tilt the top edge up moves up.
        var mx = -sy * pxPerRadian * sensitivity
        var my = sx * pxPerRadian * sensitivity
        if (invertX) mx = -mx
        if (invertY) my = -my
        val rate = sqrt(ax * ax + ay * ay) / dt.coerceAtLeast(0.001f)
        if (rate < deadzoneRate) { mx = 0f; my = 0f }
        smoothX += (mx - smoothX) * smoothing
        smoothY += (my - smoothY) * smoothing
        if (abs(smoothX) < 0.01f && abs(smoothY) < 0.01f) return
        fracX += smoothX; fracY += smoothY
        val ix = fracX.toInt(); val iy = fracY.toInt()
        fracX -= ix; fracY -= iy
        if (ix != 0 || iy != 0) out(ix, iy)
    }
}
