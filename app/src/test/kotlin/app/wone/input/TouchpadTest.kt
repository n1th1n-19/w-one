package app.wone.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadTest {
    private data class M(val b: Int, val dx: Int, val dy: Int, val w: Int, val p: Int)

    private val out = mutableListOf<M>()
    private val pad = SimpleTrackpad { b, dx, dy, w, p -> out += M(b, dx, dy, w, p) }

    @Test fun tapClicksLeft() {
        pad.down(1, 100f, 100f, 0); pad.up(1, 80)
        assertEquals(listOf(M(1, 0, 0, 0, 0), M(0, 0, 0, 0, 0)), out)
    }

    @Test fun twoFingerTapClicksRight() {
        pad.down(1, 100f, 100f, 0); pad.down(2, 140f, 100f, 5)
        pad.up(1, 60); pad.up(2, 70)
        assertEquals(M(2, 0, 0, 0, 0), out.first())
    }

    @Test fun slowTouchDoesNotClick() {
        pad.down(1, 100f, 100f, 0); pad.up(1, 600)
        assertTrue(out.isEmpty())
    }

    @Test fun dragMovesPointer() {
        pad.down(1, 0f, 0f, 0)
        pad.move(1, 30f, 0f, 16)
        assertTrue(out.last().dx > 0)
        assertEquals(0, out.last().dy)
    }

    @Test fun twoFingerScrollEmitsWheel() {
        pad.down(1, 0f, 0f, 0); pad.down(2, 40f, 0f, 1)
        repeat(10) { i -> pad.move(1, 0f, (i + 1) * 20f, 10L * (i + 1)); pad.move(2, 40f, (i + 1) * 20f, 10L * (i + 1)) }
        assertTrue(out.any { it.w != 0 })
        assertFalse(out.any { it.dx != 0 || it.dy != 0 })
    }

    @Test fun tapThenHoldDrags() {
        pad.down(1, 0f, 0f, 0); pad.up(1, 60)
        out.clear()
        pad.down(1, 0f, 0f, 150)
        assertEquals(1, out.last().b)
        pad.up(1, 900)
        assertEquals(0, out.last().b)
    }

    @Test fun trackerReportsLiftOnce() {
        val t = ContactTracker()
        t.down(7, 10, 10); t.down(8, 20, 20)
        assertEquals(2, t.frame().size)
        t.up(7)
        val f = t.frame()
        assertEquals(2, f.size)
        assertTrue(f[0].tip); assertFalse(f[1].tip)
        assertEquals(1, t.frame().size)
    }

    @Test fun trackerLimitsContacts() {
        val t = ContactTracker()
        for (i in 0L until app.wone.hid.Touchpad.MAX_CONTACTS) assertTrue(t.down(i, 0, 0))
        assertFalse(t.down(9, 0, 0))
    }

    @Test fun mapperUsesSameScaleOnBothAxes() {
        val m = PadMapper(800f)
        assertEquals(4095, m.x(800f))
        assertEquals(m.x(100f), m.y(100f))
    }
}
