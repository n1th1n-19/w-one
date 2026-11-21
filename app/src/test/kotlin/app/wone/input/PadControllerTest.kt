package app.wone.input

import org.junit.Assert.assertEquals
import org.junit.Test

class PadControllerTest {
    private var last = ByteArray(9)
    private var kb: Pair<Int, List<Int>> = 0 to emptyList()
    private var ms = 0
    private val pad = PadController(object : PadOut {
        override fun gamepad(report: ByteArray) { last = report }
        override fun keyboard(mods: Int, keys: IntArray) { kb = mods to keys.toList() }
        override fun mouse(buttons: Int) { ms = buttons }
    })

    private fun btn(bind: Bind, id: String = "c") = PadControl(id, CtlType.Button, "", 0.5f, 0.5f, 0.2f, bind = bind)

    @Test fun faceButtonSetsBit() {
        pad.setButton(btn(Bind.Pad(PadBits.A)), true)
        assertEquals(1, last[0].toInt())
        pad.setButton(btn(Bind.Pad(PadBits.A)), false)
        assertEquals(0, last[0].toInt())
    }

    @Test fun triggerIsAnalogAndSetsDigitalBitPastHalf() {
        pad.setTrigger(1, 0.6f)
        assertEquals(153, last[8].toInt() and 0xFF)
        assertEquals(1 shl (PadBits.RT - 8), last[1].toInt() and 0xFF)
        pad.setTrigger(1, 0.2f)
        assertEquals(0, last[1].toInt())
    }

    @Test fun stickScalesToSignedByte() {
        pad.setStick(0, 1f, -1f)
        assertEquals(127, last[3].toInt()); assertEquals(-127, last[4].toInt())
    }

    @Test fun keyBindingSendsKeyboardReport() {
        pad.setButton(btn(Bind.Key(0x2C, 0x02), "jump"), true)
        assertEquals(0x02 to listOf(0x2C), kb)
        pad.setButton(btn(Bind.Key(0x2C, 0x02), "jump"), false)
        assertEquals(0 to emptyList<Int>(), kb)
    }

    @Test fun mouseBindingSendsButtons() {
        pad.setButton(btn(Bind.Mouse(1)), true)
        assertEquals(2, ms)
    }

    @Test fun hatDirections() {
        assertEquals(1, PadController.hatFor(0f, -10f, 3f))
        assertEquals(3, PadController.hatFor(10f, 0f, 3f))
        assertEquals(5, PadController.hatFor(0f, 10f, 3f))
        assertEquals(7, PadController.hatFor(-10f, 0f, 3f))
        assertEquals(2, PadController.hatFor(10f, -10f, 3f))
        assertEquals(0, PadController.hatFor(1f, 1f, 3f))
    }

    @Test fun releaseAllClearsEverything() {
        pad.setButton(btn(Bind.Pad(PadBits.B)), true); pad.setStick(0, 1f, 1f)
        pad.releaseAll()
        assertEquals(0, last[0].toInt()); assertEquals(0, last[3].toInt())
    }
}
