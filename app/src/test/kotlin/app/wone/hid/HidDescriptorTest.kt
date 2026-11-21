package app.wone.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Walks the descriptor like a host would and checks it against our report layouts. */
class HidDescriptorTest {

    private class Parsed(val inputBits: MutableMap<Int, Int>, val outputBits: MutableMap<Int, Int>, val balanced: Boolean)

    private fun parse(d: ByteArray): Parsed {
        var i = 0
        var size = 0
        var count = 0
        var id = 0
        var depth = 0
        val input = mutableMapOf<Int, Int>()
        val output = mutableMapOf<Int, Int>()
        while (i < d.size) {
            val p = d[i].toInt() and 0xFF
            val len = when (p and 3) { 3 -> 4; else -> p and 3 }
            var v = 0
            for (k in 0 until len) v = v or ((d[i + 1 + k].toInt() and 0xFF) shl (8 * k))
            val type = (p shr 2) and 3
            val tag = p shr 4
            when (type) {
                0 -> when (tag) {
                    0x8 -> input.merge(id, size * count, Int::plus)
                    0x9 -> output.merge(id, size * count, Int::plus)
                    0xA -> depth++
                    0xC -> depth--
                }
                1 -> when (tag) {
                    0x7 -> size = v
                    0x8 -> id = v
                    0x9 -> count = v
                }
            }
            i += 1 + len
        }
        return Parsed(input, output, depth == 0)
    }

    private val parsed = parse(HidDescriptor.full)
    private val parsedPad = parse(HidDescriptor.withTouchpad)

    @Test fun collectionsBalanced() { assertTrue(parsed.balanced); assertTrue(parsedPad.balanced) }

    @Test fun reportSizesMatchEncoders() {
        assertEquals(ReportSize.KEYBOARD * 8, parsed.inputBits[ReportId.KEYBOARD])
        assertEquals(ReportSize.MOUSE * 8, parsed.inputBits[ReportId.MOUSE])
        assertEquals(ReportSize.GAMEPAD * 8, parsed.inputBits[ReportId.GAMEPAD])
        assertEquals(ReportSize.KEYBOARD * 8, parsedPad.inputBits[ReportId.KEYBOARD])
        assertEquals(ReportSize.TOUCHPAD * 8, parsedPad.inputBits[ReportId.TOUCHPAD])
    }

    @Test fun encodersProduceDeclaredSizes() {
        assertEquals(ReportSize.KEYBOARD, HidReports.keyboard(0).size)
        assertEquals(ReportSize.MOUSE, HidReports.mouse(0, 0, 0).size)
        assertEquals(ReportSize.GAMEPAD, HidReports.gamepad(0, 0, 0, 0, 0, 0, 0, 0).size)
        assertEquals(ReportSize.TOUCHPAD, HidReports.touchpad(emptyList(), false).size)
    }

    @Test fun descriptorsStaySmall() { assertTrue("full ${HidDescriptor.full.size}", HidDescriptor.full.size < 340); assertTrue("pad ${HidDescriptor.withTouchpad.size}", HidDescriptor.withTouchpad.size < 340) }

    @Test fun mouseClampsDeltas() {
        val r = HidReports.mouse(1, 500, -500)
        assertEquals(127, r[1].toInt())
        assertEquals(-127, r[2].toInt())
    }

    @Test fun touchpadPacksContact() {
        val r = HidReports.touchpad(listOf(TouchContact(3, true, 0x0123, 0x0456)), click = true)
        assertEquals(((3 shl 1) or 1), r[0].toInt())
        assertEquals(0x23, r[1].toInt()); assertEquals(0x01, r[2].toInt())
        assertEquals(0x56, r[3].toInt()); assertEquals(0x04, r[4].toInt())
        val tail = Touchpad.MAX_CONTACTS * Touchpad.CONTACT_BYTES
        assertEquals(1, r[tail].toInt())
        assertEquals(1, r[tail + 1].toInt())
    }

    @Test fun keyboardPacksKeys() {
        val r = HidReports.keyboard(HidReports.MOD_LCTRL, intArrayOf(0x06))
        assertEquals(1, r[0].toInt()); assertEquals(0x06, r[2].toInt())
    }
}
