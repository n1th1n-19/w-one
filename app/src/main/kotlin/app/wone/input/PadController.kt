package app.wone.input

import app.wone.hid.HidReports
import kotlin.math.roundToInt

interface PadOut {
    fun gamepad(report: ByteArray)
    fun keyboard(mods: Int, keys: IntArray)
    fun mouse(buttons: Int)
}

/**
 * Holds the state of every on-screen control and sends it: gamepad buttons and axes in one
 * report, keyboard and mouse bindings as their own reports.
 */
class PadController(private val out: PadOut) {
    private var buttons = 0
    private var hat = 0
    private val stick = FloatArray(4)   // lx, ly, rx, ry in -1..1
    private val trig = FloatArray(2)    // 0..1
    private val keys = LinkedHashMap<String, Bind.Key>()
    private var mouse = 0

    /** Digital press for a Pad, Key or Mouse binding. */
    fun setButton(c: PadControl, down: Boolean) {
        when (val b = c.bind) {
            is Bind.Pad -> {
                buttons = if (down) buttons or (1 shl b.bit) else buttons and (1 shl b.bit).inv()
                sendPad()
            }
            is Bind.Key -> {
                if (down) keys[c.id] = b else keys.remove(c.id)
                out.keyboard(keys.values.fold(0) { m, k -> m or k.mods }, keys.values.map { it.usage }.take(6).toIntArray())
            }
            is Bind.Mouse -> {
                mouse = if (down) mouse or (1 shl b.bit) else mouse and (1 shl b.bit).inv()
                out.mouse(mouse)
            }
            else -> Unit
        }
    }

    /** Stick position in -1..1 on each axis, screen down is positive. */
    fun setStick(side: Int, x: Float, y: Float) {
        stick[side * 2] = x.coerceIn(-1f, 1f)
        stick[side * 2 + 1] = y.coerceIn(-1f, 1f)
        sendPad()
    }

    fun setTrigger(side: Int, v: Float) {
        trig[side] = v.coerceIn(0f, 1f)
        val bit = 1 shl (if (side == 0) PadBits.LT else PadBits.RT)
        buttons = if (trig[side] > 0.5f) buttons or bit else buttons and bit.inv()
        sendPad()
    }

    /** 0 released, 1 up, then clockwise to 8 up-left. */
    fun setHat(h: Int) { hat = h; sendPad() }

    fun releaseAll() {
        buttons = 0; hat = 0; stick.fill(0f); trig.fill(0f)
        keys.clear(); mouse = 0
        sendPad(); out.keyboard(0, IntArray(0)); out.mouse(0)
    }

    private fun sendPad() {
        fun ax(v: Float) = (v * 127f).roundToInt()
        out.gamepad(
            HidReports.gamepad(
                buttons, hat,
                ax(stick[0]), ax(stick[1]), ax(stick[2]), ax(stick[3]),
                (trig[0] * 255f).roundToInt(), (trig[1] * 255f).roundToInt(),
            ),
        )
    }

    companion object {
        /** D-pad direction from the touch offset, or 0 inside the dead centre. */
        fun hatFor(dx: Float, dy: Float, dead: Float): Int {
            if (dx * dx + dy * dy < dead * dead) return 0
            val deg = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), -dy.toDouble())).let { if (it < 0) it + 360 else it }
            return ((deg + 22.5) / 45.0).toInt() % 8 + 1
        }
    }
}
