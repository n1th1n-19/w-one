package app.wone.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotShapeTest {
    // Reference points from the mascot lab's own JavaScript for frequency 4.3, amount 0.17,
    // squareness 5.8, seed 29, radius 31, 360 points.
    @Test fun matchesTheLabOutline() {
        val p = MascotShape.points(MascotLook.Icon)
        fun at(i: Int) = p[i * 2] to p[i * 2 + 1]
        val ref = mapOf(0 to (87.02f to 54f), 1 to (86.68f to 54.57f), 90 to (54f to 88.55f), 181 to (19.72f to 53.4f), 359 to (87.18f to 53.42f))
        for ((i, r) in ref) {
            assertEquals("x at $i", r.first, at(i).first, 0.02f)
            assertEquals("y at $i", r.second, at(i).second, 0.02f)
        }
    }

    @Test fun randomLooksStayInRange() {
        repeat(200) {
            val l = MascotLook.random()
            assertTrue(l.freq in 3f..6f && l.amp in 0.11f..0.21f && l.sq in 4.3f..6.5f && l.seed in 1..60)
        }
    }
}
