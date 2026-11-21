package app.wone.input

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardControllerTest {
    private class Rec : KeyboardSink {
        val reports = mutableListOf<Pair<Int, List<Int>>>()
        override fun keyboard(modifiers: Int, keys: IntArray) { reports += modifiers to keys.toList() }
    }

    private val rec = Rec()
    private val typed = mutableListOf<String>()
    private val kb = KeyboardController(rec) { typed += it }
    private val ctrl = Keys.of("Ctrl")
    private val shift = Keys.of("⇧")
    private val c = Keys.of("c")

    @Test fun plainKeyPressAndRelease() {
        kb.down(c); kb.up(c)
        assertEquals(0 to listOf(0x06), rec.reports[0])
        assertEquals(0 to emptyList<Int>(), rec.reports[1])
        assertEquals(listOf("c"), typed)
    }

    @Test fun tapModifierThenKeyAppliesOnce() {
        kb.down(ctrl); kb.up(ctrl)
        assertEquals(StickyState.Latched, kb.stateOf(Keys.CTRL))
        kb.down(c)
        assertEquals(Keys.CTRL to listOf(0x06), rec.reports.last())
        kb.up(c)
        assertEquals(StickyState.Off, kb.stateOf(Keys.CTRL))
        assertEquals(0 to emptyList<Int>(), rec.reports.last())
        assertEquals(emptyList<String>(), typed)
    }

    @Test fun doubleTapLocksAndThirdTapClears() {
        repeat(2) { kb.down(ctrl); kb.up(ctrl) }
        assertEquals(StickyState.Locked, kb.stateOf(Keys.CTRL))
        kb.down(c); kb.up(c)
        assertEquals(StickyState.Locked, kb.stateOf(Keys.CTRL))
        kb.down(ctrl); kb.up(ctrl)
        assertEquals(StickyState.Off, kb.stateOf(Keys.CTRL))
    }

    @Test fun heldModifierChordDoesNotLatch() {
        kb.down(shift); kb.down(c); kb.up(c); kb.up(shift)
        assertEquals(StickyState.Off, kb.stateOf(Keys.SHIFT))
        assertEquals(Keys.SHIFT to listOf(0x06), rec.reports[1])
        assertEquals(listOf("C"), typed)
    }

    @Test fun symbolsAddShiftThemselves() {
        val bang = Keys.of("!")
        kb.down(bang)
        assertEquals(Keys.SHIFT to listOf(0x1E), rec.reports.last())
        val brace = Keys.of("{")
        kb.down(brace)
        assertEquals(Keys.SHIFT to listOf(0x1E, 0x2F), rec.reports.last())
    }

    @Test fun previewIgnoresEnterAndArrows() {
        for (l in listOf("⏎", "←", "Tab", "Esc", "F5")) { val k = Keys.of(l); kb.down(k); kb.up(k) }
        assertEquals(emptyList<String>(), typed)
        val sp = Keys.of("space"); kb.down(sp); kb.up(sp)
        val bs = Keys.of("⌫"); kb.down(bs); kb.up(bs)
        assertEquals(listOf(" ", "\b"), typed)
    }

    @Test fun resetReleasesEverything() {
        kb.down(ctrl); kb.down(c)
        kb.reset()
        assertEquals(0 to emptyList<Int>(), rec.reports.last())
        assertEquals(StickyState.Off, kb.stateOf(Keys.CTRL))
    }

    @Test fun everyLayerLabelHasAKey() {
        for (rows in Keys.layerRows.values) for (r in rows) for (l in r) Keys.of(l)
        for (l in Keys.topRow + Keys.bottomRow) Keys.of(l)
    }

    @Test fun asciiPrintablesCovered() {
        val ascii = ('a'..'z') + ('0'..'9') + "-=[]\\;'`,./_+{}|:\"~<>?!@#$%^&*()".toList()
        val report = ascii.map { Keys.of(it.toString()).usage }
        assertEquals(ascii.size, report.size)
        assertArrayEquals(intArrayOf(0x04), intArrayOf(Keys.of("a").usage))
    }
}
