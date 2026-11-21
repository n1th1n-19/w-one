package app.wone.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class AirPointerTest {
    private var sx = 0
    private var sy = 0
    private val air = AirPointer { dx, dy -> sx += dx; sy += dy }

    /** Quaternion for a turn of [a] radians about a device axis. */
    private fun feed(axis: Char, a: Float, steps: Int = 20) {
        air.reset()
        var w = 1f; var x = 0f; var y = 0f; var z = 0f
        air.onQuaternion(w, x, y, z, 0.01f)
        for (i in 1..steps) {
            val ang = a * i / steps
            w = cos(ang / 2)
            x = if (axis == 'x') sin(ang / 2) else 0f
            y = if (axis == 'y') sin(ang / 2) else 0f
            z = if (axis == 'z') sin(ang / 2) else 0f
            air.onQuaternion(w, x, y, z, 0.01f)
        }
    }

    @Test fun firstSampleDoesNotMove() {
        air.onQuaternion(1f, 0f, 0f, 0f, 0.01f)
        assertEquals(0, sx); assertEquals(0, sy)
    }

    @Test fun tinyJitterIsIgnored() {
        feed('x', 0.0004f)
        assertEquals(0, sx); assertEquals(0, sy)
    }

    @Test fun bigTurnMovesMoreThanSmallTurn() {
        feed('y', 0.05f); val small = kotlin.math.abs(sx) + kotlin.math.abs(sy)
        sx = 0; sy = 0
        feed('y', 0.2f); val big = kotlin.math.abs(sx) + kotlin.math.abs(sy)
        assertTrue(big > small)
    }

    @Test fun invertFlipsDirection() {
        air.displayRotation = 1
        feed('x', 0.1f); val a = sy
        sx = 0; sy = 0
        air.invertY = true; air.invertX = true
        feed('x', 0.1f)
        assertEquals(-a, sy)
    }

    @Test fun gyroFallbackMoves() {
        air.displayRotation = 0
        repeat(20) { air.onGyro(0f, 1.5f, 0f, 0.01f) }
        assertTrue(sx != 0 || sy != 0)
    }
}
