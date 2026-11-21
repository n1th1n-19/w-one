package app.wone.hid

/** One finger in a touchpad report. Coordinates are in the descriptor's logical range. */
data class TouchContact(val id: Int, val tip: Boolean, val x: Int, val y: Int)

/** Packs report payloads. Each function returns the bytes without the report id. */
object HidReports {

    const val MOD_LCTRL = 0x01
    const val MOD_LSHIFT = 0x02
    const val MOD_LALT = 0x04
    const val MOD_LGUI = 0x08

    fun keyboard(modifiers: Int, keys: IntArray = IntArray(0)): ByteArray {
        val r = ByteArray(ReportSize.KEYBOARD)
        r[0] = modifiers.toByte()
        for (i in 0 until minOf(keys.size, 6)) r[2 + i] = keys[i].toByte()
        return r
    }

    fun mouse(buttons: Int, dx: Int, dy: Int, wheel: Int = 0, pan: Int = 0): ByteArray {
        val r = ByteArray(ReportSize.MOUSE)
        r[0] = (buttons and 0x1F).toByte()
        r[1] = dx.coerceIn(-127, 127).toByte()
        r[2] = dy.coerceIn(-127, 127).toByte()
        r[3] = wheel.coerceIn(-127, 127).toByte()
        r[4] = pan.coerceIn(-127, 127).toByte()
        return r
    }

    /**
     * @param hat 0 released, 1 up, then clockwise to 8 up-left.
     * @param lx stick and trigger values: sticks -127..127, triggers 0..255.
     */
    fun gamepad(
        buttons: Int, hat: Int,
        lx: Int, ly: Int, rx: Int, ry: Int,
        lt: Int, rt: Int,
    ): ByteArray {
        val r = ByteArray(ReportSize.GAMEPAD)
        r[0] = buttons.toByte()
        r[1] = (buttons shr 8).toByte()
        r[2] = (hat and 0x0F).toByte()
        r[3] = lx.coerceIn(-127, 127).toByte()
        r[4] = ly.coerceIn(-127, 127).toByte()
        r[5] = rx.coerceIn(-127, 127).toByte()
        r[6] = ry.coerceIn(-127, 127).toByte()
        r[7] = lt.coerceIn(0, 255).toByte()
        r[8] = rt.coerceIn(0, 255).toByte()
        return r
    }

    /** Lifted fingers must appear once with tip = false; the host then ends that contact. */
    fun touchpad(contacts: List<TouchContact>, click: Boolean): ByteArray {
        val r = ByteArray(ReportSize.TOUCHPAD)
        val n = minOf(contacts.size, Touchpad.MAX_CONTACTS)
        for (i in 0 until n) {
            val c = contacts[i]
            val o = i * 5
            r[o] = ((if (c.tip) 1 else 0) or ((c.id and 0x7F) shl 1)).toByte()
            val x = c.x.coerceIn(0, Touchpad.X_MAX)
            val y = c.y.coerceIn(0, Touchpad.Y_MAX)
            r[o + 1] = x.toByte(); r[o + 2] = (x shr 8).toByte()
            r[o + 3] = y.toByte(); r[o + 4] = (y shr 8).toByte()
        }
        r[25] = n.toByte()
        r[26] = if (click) 1 else 0
        return r
    }
}
