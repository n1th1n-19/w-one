package app.wone.input

import app.wone.hid.TouchContact
import app.wone.hid.Touchpad
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Keeps the touch slots stable between frames so the host sees continuous contacts. */
class ContactTracker {
    private class Slot(val id: Int, var x: Int, var y: Int)

    private val slots = LinkedHashMap<Long, Slot>()
    private val lifted = ArrayList<TouchContact>()
    private var nextId = 0

    val active: Int get() = slots.size

    fun down(pointer: Long, x: Int, y: Int): Boolean {
        if (slots.size >= Touchpad.MAX_CONTACTS) return false
        slots[pointer] = Slot(nextId, x, y)
        nextId = (nextId + 1) and 0x7F
        return true
    }

    fun move(pointer: Long, x: Int, y: Int) {
        slots[pointer]?.let { it.x = x; it.y = y }
    }

    fun up(pointer: Long) {
        val s = slots.remove(pointer) ?: return
        lifted += TouchContact(s.id, false, s.x, s.y)
    }

    /** Fingers that just lifted appear once with tip = false, after the ones still down. */
    fun frame(): List<TouchContact> {
        val out = ArrayList<TouchContact>(slots.size + lifted.size)
        for (s in slots.values) out += TouchContact(s.id, true, s.x, s.y)
        out += lifted
        lifted.clear()
        return out.take(Touchpad.MAX_CONTACTS)
    }

    /** Current fingers without consuming lifted ones, for a button-only report. */
    fun snapshot(): List<TouchContact> = slots.values.map { TouchContact(it.id, true, it.x, it.y) }

    fun clear() { slots.clear(); lifted.clear() }
}

fun interface MouseSink {
    fun mouse(buttons: Int, dx: Int, dy: Int, wheel: Int, pan: Int)
}

/**
 * Laptop-style gestures turned into plain mouse reports, for hosts where the native touchpad
 * does not work: one finger moves, a tap clicks, two fingers scroll, a two-finger tap right-clicks,
 * tap then hold drags. Time is passed in so the logic can be tested.
 */
class SimpleTrackpad(private val sink: MouseSink) {
    var speed = 1.4f
    var natural = false

    private class P(var x: Float, var y: Float, val t0: Long, val x0: Float, val y0: Float, var lastT: Long)

    private val fingers = LinkedHashMap<Long, P>()
    private var buttons = 0
    private var maxFingers = 0
    private var moved = false
    private var lastTapEnd = -1000L
    private var dragging = false
    private var scrollAccX = 0f
    private var scrollAccY = 0f
    private var fracX = 0f
    private var fracY = 0f

    private val tapSlop = 14f
    private val tapMs = 220L
    private val dragWindowMs = 280L
    private val scrollStepPx = 34f

    fun setButton(bit: Int, down: Boolean) {
        buttons = if (down) buttons or bit else buttons and bit.inv()
        sink.mouse(buttons, 0, 0, 0, 0)
    }

    fun down(id: Long, x: Float, y: Float, t: Long) {
        if (fingers.isEmpty()) {
            maxFingers = 0; moved = false
            // A second touch soon after a tap starts a drag: press and hold the left button.
            if (t - lastTapEnd < dragWindowMs) { dragging = true; buttons = buttons or 1; sink.mouse(buttons, 0, 0, 0, 0) }
        }
        fingers[id] = P(x, y, t, x, y, t)
        maxFingers = max(maxFingers, fingers.size)
    }

    fun move(id: Long, x: Float, y: Float, t: Long) {
        val p = fingers[id] ?: return
        val dx = x - p.x; val dy = y - p.y
        val dt = max(1L, t - p.lastT)
        p.x = x; p.y = y; p.lastT = t
        if (hypot(x - p.x0, y - p.y0) > tapSlop) moved = true
        when (fingers.size) {
            1 -> {
                // Pointer acceleration: faster strokes travel further.
                val v = hypot(dx, dy) / dt
                val gain = speed * (1f + 0.9f * min(v, 3f))
                emitMove(dx * gain, dy * gain)
            }
            2 -> {
                // Scroll follows the average motion of the two fingers.
                val sx = dx / 2f; val sy = dy / 2f
                scrollAccX += sx; scrollAccY += sy
                var wheel = 0; var pan = 0
                while (abs(scrollAccY) >= scrollStepPx) { wheel += if (scrollAccY > 0) 1 else -1; scrollAccY -= if (scrollAccY > 0) scrollStepPx else -scrollStepPx }
                while (abs(scrollAccX) >= scrollStepPx) { pan += if (scrollAccX > 0) 1 else -1; scrollAccX -= if (scrollAccX > 0) scrollStepPx else -scrollStepPx }
                // Content follows the fingers when natural; otherwise the wheel runs the other way.
                val k = if (natural) 1 else -1
                if (wheel != 0 || pan != 0) sink.mouse(buttons, 0, 0, k * wheel, -k * pan)
            }
        }
    }

    private fun emitMove(dx: Float, dy: Float) {
        fracX += dx; fracY += dy
        val ix = fracX.toInt(); val iy = fracY.toInt()
        fracX -= ix; fracY -= iy
        if (ix != 0 || iy != 0) sink.mouse(buttons, ix, iy, 0, 0)
    }

    fun up(id: Long, t: Long) {
        val p = fingers.remove(id) ?: return
        if (fingers.isNotEmpty()) return
        val quick = t - p.t0 < tapMs && !moved
        if (dragging) {
            dragging = false
            buttons = buttons and 1.inv()
            sink.mouse(buttons, 0, 0, 0, 0)
            lastTapEnd = -1000L
            return
        }
        if (quick) {
            val bit = if (maxFingers >= 2) 2 else 1
            sink.mouse(buttons or bit, 0, 0, 0, 0)
            sink.mouse(buttons, 0, 0, 0, 0)
            lastTapEnd = if (bit == 1) t else -1000L
        } else lastTapEnd = -1000L
        scrollAccX = 0f; scrollAccY = 0f
    }

    fun reset() {
        fingers.clear(); dragging = false
        if (buttons != 0) { buttons = 0; sink.mouse(0, 0, 0, 0, 0) }
    }
}

/** Maps a screen position to the descriptor's range with the same scale on both axes. */
class PadMapper(private val widthPx: Float) {
    private val s = Touchpad.X_MAX / max(1f, widthPx)
    fun x(px: Float) = (px * s).roundToInt().coerceIn(0, Touchpad.X_MAX)
    fun y(py: Float) = (py * s).roundToInt().coerceIn(0, Touchpad.Y_MAX)
}
