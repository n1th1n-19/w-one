package app.wone.hid

import java.io.ByteArrayOutputStream

/** Report ids of the combined descriptor. Each id is one "device" the host sees. */
object ReportId {
    const val KEYBOARD = 1
    const val MOUSE = 2
    const val GAMEPAD = 3
    const val TOUCHPAD = 4
}

/** Sizes of report payloads, not counting the report id byte. */
object ReportSize {
    const val KEYBOARD = 8
    const val MOUSE = 5
    const val GAMEPAD = 9
    const val TOUCHPAD = 27
    const val KEYBOARD_LEDS = 1
}

object Touchpad {
    const val MAX_CONTACTS = 5
    const val X_MAX = 4095
    const val Y_MAX = 2559

    // Physical size in 0.1 mm steps (unit exponent -2 on cm): a 100 x 62.5 mm pad.
    // libinput uses this to scale motion, so it must roughly match the logical ratio.
    const val X_PHYS = 1000
    const val Y_PHYS = 625
}

/**
 * Builds the HID report descriptor with a tiny item encoder so every byte has a name.
 * One descriptor, four devices: keyboard, relative mouse, gamepad, multitouch touchpad.
 */
object HidDescriptor {

    val bytes: ByteArray by lazy { build() }

    private class Builder {
        val out = ByteArrayOutputStream()

        private fun item(tag: Int, type: Int, data: ByteArray) {
            val size = when (data.size) {
                0 -> 0
                1 -> 1
                2 -> 2
                else -> 3
            }
            out.write((tag shl 4) or (type shl 2) or size)
            out.write(data)
        }

        /** Smallest little-endian two's complement encoding that holds [v]. */
        private fun signed(v: Int): ByteArray = when (v) {
            in -128..127 -> byteArrayOf(v.toByte())
            in -32768..32767 -> byteArrayOf(v.toByte(), (v shr 8).toByte())
            else -> byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        }

        /** Unsigned data for usages and usage pages. */
        private fun unsigned(v: Int): ByteArray =
            if (v <= 0xFF) byteArrayOf(v.toByte()) else byteArrayOf(v.toByte(), (v shr 8).toByte())

        private fun main(tag: Int, v: Int) = item(tag, 0, byteArrayOf(v.toByte()))
        private fun global(tag: Int, data: ByteArray) = item(tag, 1, data)
        private fun local(tag: Int, data: ByteArray) = item(tag, 2, data)

        fun input(flags: Int) = main(0x8, flags)
        fun output(flags: Int) = main(0x9, flags)
        fun collection(kind: Int) = main(0xA, kind)
        fun endCollection() = item(0xC, 0, ByteArray(0))

        fun usagePage(v: Int) = global(0x0, unsigned(v))
        fun logicalMin(v: Int) = global(0x1, signed(v))
        fun logicalMax(v: Int) = global(0x2, signed(v))
        fun physicalMin(v: Int) = global(0x3, signed(v))
        fun physicalMax(v: Int) = global(0x4, signed(v))
        fun unitExponent(v: Int) = global(0x5, byteArrayOf((v and 0x0F).toByte()))
        fun unit(v: Int) = global(0x6, unsigned(v))
        fun reportSize(v: Int) = global(0x7, unsigned(v))
        fun reportId(v: Int) = global(0x8, unsigned(v))
        fun reportCount(v: Int) = global(0x9, unsigned(v))

        fun usage(v: Int) = local(0x0, unsigned(v))
        fun usageMin(v: Int) = local(0x1, unsigned(v))
        fun usageMax(v: Int) = local(0x2, unsigned(v))
    }

    private const val DATA_VAR_ABS = 0x02
    private const val DATA_VAR_REL = 0x06
    private const val DATA_VAR_ABS_NULL = 0x42
    private const val DATA_ARRAY = 0x00
    private const val CONSTANT = 0x01

    private const val APPLICATION = 0x01
    private const val PHYSICAL = 0x00
    private const val LOGICAL = 0x02

    private fun build(): ByteArray {
        val b = Builder()
        with(b) {
            keyboard()
            mouse()
            gamepad()
            touchpad()
        }
        return b.out.toByteArray()
    }

    private fun Builder.keyboard() {
        usagePage(0x01); usage(0x06); collection(APPLICATION)
        reportId(ReportId.KEYBOARD)
        // 8 modifier bits (Ctrl, Shift, Alt, Super, left then right).
        usagePage(0x07); usageMin(0xE0); usageMax(0xE7)
        logicalMin(0); logicalMax(1); reportSize(1); reportCount(8); input(DATA_VAR_ABS)
        // Reserved byte.
        reportCount(1); reportSize(8); input(CONSTANT)
        // Lock LEDs from the host (Num, Caps, Scroll, Compose, Kana) plus padding.
        usagePage(0x08); usageMin(1); usageMax(5)
        reportCount(5); reportSize(1); output(DATA_VAR_ABS)
        reportCount(1); reportSize(3); output(CONSTANT)
        // Six simultaneous keys.
        usagePage(0x07); usageMin(0); usageMax(0xFF)
        logicalMin(0); logicalMax(255); reportSize(8); reportCount(6); input(DATA_ARRAY)
        endCollection()
    }

    private fun Builder.mouse() {
        usagePage(0x01); usage(0x02); collection(APPLICATION)
        reportId(ReportId.MOUSE)
        usage(0x01); collection(PHYSICAL)
        usagePage(0x09); usageMin(1); usageMax(5)
        logicalMin(0); logicalMax(1); reportSize(1); reportCount(5); input(DATA_VAR_ABS)
        reportCount(1); reportSize(3); input(CONSTANT)
        usagePage(0x01); usage(0x30); usage(0x31)
        logicalMin(-127); logicalMax(127); reportSize(8); reportCount(2); input(DATA_VAR_REL)
        usage(0x38) // wheel
        reportCount(1); input(DATA_VAR_REL)
        usagePage(0x0C); usage(0x0238) // AC Pan, horizontal scroll
        reportCount(1); input(DATA_VAR_REL)
        endCollection()
        endCollection()
    }

    private fun Builder.gamepad() {
        usagePage(0x01); usage(0x05); collection(APPLICATION)
        reportId(ReportId.GAMEPAD)
        usagePage(0x09); usageMin(1); usageMax(16)
        logicalMin(0); logicalMax(1); reportSize(1); reportCount(16); input(DATA_VAR_ABS)
        // Hat: 1 = up, clockwise to 8 = up-left, 0 = released.
        usagePage(0x01); usage(0x39)
        logicalMin(1); logicalMax(8); reportSize(4); reportCount(1); input(DATA_VAR_ABS_NULL)
        reportSize(4); reportCount(1); input(CONSTANT)
        // Left stick X/Y, right stick Z/Rz.
        usage(0x30); usage(0x31); usage(0x32); usage(0x35)
        logicalMin(-127); logicalMax(127); reportSize(8); reportCount(4); input(DATA_VAR_ABS)
        // Analog triggers on Rx/Ry.
        usage(0x33); usage(0x34)
        logicalMin(0); logicalMax(255); reportSize(8); reportCount(2); input(DATA_VAR_ABS)
        endCollection()
    }

    private fun Builder.touchpad() {
        usagePage(0x0D); usage(0x05); collection(APPLICATION)
        reportId(ReportId.TOUCHPAD)
        repeat(Touchpad.MAX_CONTACTS) {
            usagePage(0x0D); usage(0x22); collection(LOGICAL)
            usage(0x42) // tip switch
            logicalMin(0); logicalMax(1); reportSize(1); reportCount(1); input(DATA_VAR_ABS)
            usage(0x51) // contact id, 7 bits
            logicalMax(127); reportSize(7); reportCount(1); input(DATA_VAR_ABS)
            usagePage(0x01)
            logicalMin(0); physicalMin(0); unitExponent(-2); unit(0x11)
            reportSize(16); reportCount(1)
            usage(0x30); logicalMax(Touchpad.X_MAX); physicalMax(Touchpad.X_PHYS); input(DATA_VAR_ABS)
            usage(0x31); logicalMax(Touchpad.Y_MAX); physicalMax(Touchpad.Y_PHYS); input(DATA_VAR_ABS)
            endCollection()
        }
        unit(0); unitExponent(0)
        usagePage(0x0D); usage(0x54) // contact count
        logicalMin(0); logicalMax(Touchpad.MAX_CONTACTS); reportSize(8); reportCount(1); input(DATA_VAR_ABS)
        usagePage(0x09); usageMin(1); usageMax(1) // pad click
        logicalMax(1); reportSize(1); reportCount(1); input(DATA_VAR_ABS)
        reportSize(7); reportCount(1); input(CONSTANT)
        endCollection()
    }
}
