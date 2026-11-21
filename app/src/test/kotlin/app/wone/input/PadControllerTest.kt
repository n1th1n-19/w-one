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
        assertEquals(255, last[3].toInt() and 0xFF); assertEquals(1, last[4].toInt() and 0xFF)
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
        assertEquals(0, last[0].toInt()); assertEquals(128, last[3].toInt() and 0xFF)
    }

    private fun keysStick() = PadControl("move", CtlType.Stick, "WASD", 0.2f, 0.6f, 0.4f, bind = Bind.Keys4(0x1A, 0x04, 0x16, 0x07))

    @Test fun stickInKeyModePressesDirectionKeys() {
        val c = keysStick()
        pad.setStickKeys(c, 0f, -1f)
        assertEquals(0 to listOf(0x1A), kb)
        pad.setStickKeys(c, 1f, -1f)
        assertEquals(0 to listOf(0x1A, 0x07), kb)
        pad.setStickKeys(c, 0.1f, 0.1f)
        assertEquals(0 to emptyList<Int>(), kb)
    }

    @Test fun dpadInKeyModeFollowsTheHat() {
        val c = PadControl("dir", CtlType.DPad, "", 0.2f, 0.6f, 0.4f, bind = Bind.Keys4(0x52, 0x50, 0x51, 0x4F))
        pad.setHatKeys(c, 2) // up-right
        assertEquals(0 to listOf(0x52, 0x4F), kb)
        pad.setHatKeys(c, 0)
        assertEquals(0 to emptyList<Int>(), kb)
    }

    @Test fun directionKeysAndButtonKeysShareOneReport() {
        pad.setStickKeys(keysStick(), 0f, -1f)
        pad.setButton(btn(Bind.Key(0x2C, 0), "jump"), true)
        assertEquals(0 to listOf(0x2C, 0x1A), kb)
    }

    @Test fun shiftOnlyKeyIsAModifier() {
        pad.setButton(btn(Bind.Key(0, 0x02), "shift"), true)
        assertEquals(0x02 to emptyList<Int>(), kb)
    }

    @Test fun lookSendsPointerMovementWithHeldButtons() {
        var seen: Triple<Int, Int, Int>? = null
        val p = PadController(object : PadOut {
            override fun gamepad(report: ByteArray) {}
            override fun keyboard(mods: Int, keys: IntArray) {}
            override fun mouse(buttons: Int) {}
            override fun look(buttons: Int, dx: Int, dy: Int) { seen = Triple(buttons, dx, dy) }
        })
        p.setButton(btn(Bind.Mouse(0)), true)
        p.look(5, -3)
        assertEquals(Triple(1, 5, -3), seen)
    }
}
