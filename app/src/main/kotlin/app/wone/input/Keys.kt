package app.wone.input

import app.wone.hid.HidReports

enum class KeyKind { Char, Modifier, Special, Layer, Space }

/** One key on screen. [usage] is the HID keyboard usage; [shiftChar] is what Shift turns it into. */
data class KeyDef(
    val label: String,
    val kind: KeyKind,
    val usage: Int = 0,
    val modBit: Int = 0,
    val shiftChar: String? = null,
    val needsShift: Boolean = false,
    val wide: Boolean = false,
    val layer: Layer? = null,
    val typed: String? = null,
)

enum class Layer(val label: String) { Abc("ABC"), Num("123"), Sym("{}#"), Fn("Fn") }

object Keys {
    const val CTRL = HidReports.MOD_LCTRL
    const val SHIFT = HidReports.MOD_LSHIFT
    const val ALT = HidReports.MOD_LALT
    const val SUPER = HidReports.MOD_LGUI

    private const val UNSHIFTED = "-=[]\\;'`,./"
    private const val SHIFTED = "_+{}|:\"~<>?"
    private val SYMBOL_USAGE = intArrayOf(0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38)
    private const val DIGIT_SHIFT = "!@#$%^&*()"

    private val special = mapOf(
        "Esc" to 0x29, "Tab" to 0x2B, "Del" to 0x4C, "Home" to 0x4A, "End" to 0x4D,
        "PgUp" to 0x4B, "PgDn" to 0x4E, "Ins" to 0x49, "Caps" to 0x39,
        "⏎" to 0x28, "⌫" to 0x2A, "←" to 0x50, "→" to 0x4F, "↑" to 0x52, "↓" to 0x51,
        "space" to 0x2C,
    )

    private val modifiers = mapOf("Ctrl" to CTRL, "⇧" to SHIFT, "Alt" to ALT, "Super" to SUPER)
    private val layers = mapOf("ABC" to Layer.Abc, "123" to Layer.Num, "{}#" to Layer.Sym, "Fn" to Layer.Fn)

    /** Names for the preview when a modifier combo is typed. */
    val usageName: Map<Int, String> = buildMap {
        for ((n, u) in special) put(u, n)
        for (i in 0 until 26) put(0x04 + i, ('A' + i).toString())
        for (i in 1..9) put(0x1D + i, i.toString())
        put(0x27, "0")
        for (i in 0 until 12) put(0x3A + i, "F${i + 1}")
        for (i in UNSHIFTED.indices) put(SYMBOL_USAGE[i], UNSHIFTED[i].toString())
    }

    fun of(label: String): KeyDef {
        modifiers[label]?.let { return KeyDef(label, KeyKind.Modifier, modBit = it, wide = label == "⇧") }
        layers[label]?.let { return KeyDef(label, KeyKind.Layer, layer = it) }
        if (label == "space") return KeyDef(label, KeyKind.Space, usage = 0x2C, wide = true, typed = " ")
        special[label]?.let { u ->
            val typed = when (label) { "⏎" -> "\n"; "⌫" -> "\b"; "Tab" -> "\t"; else -> null }
            return KeyDef(label, KeyKind.Special, usage = u, wide = label == "⏎" || label == "⌫", typed = typed)
        }
        if (label.length > 1 && label[0] == 'F' && label.drop(1).all { it.isDigit() }) {
            return KeyDef(label, KeyKind.Special, usage = 0x3A + label.drop(1).toInt() - 1)
        }
        val c = label[0]
        return when {
            c in 'a'..'z' -> KeyDef(label, KeyKind.Char, 0x04 + (c - 'a'), shiftChar = label.uppercase(), typed = label)
            c in '1'..'9' -> KeyDef(label, KeyKind.Char, 0x1D + (c - '0'), shiftChar = DIGIT_SHIFT[c - '1'].toString(), typed = label)
            c == '0' -> KeyDef(label, KeyKind.Char, 0x27, shiftChar = ")", typed = label)
            UNSHIFTED.indexOf(c) >= 0 -> {
                val i = UNSHIFTED.indexOf(c)
                KeyDef(label, KeyKind.Char, SYMBOL_USAGE[i], shiftChar = SHIFTED[i].toString(), typed = label)
            }
            SHIFTED.indexOf(c) >= 0 -> {
                val i = SHIFTED.indexOf(c)
                KeyDef(label, KeyKind.Char, SYMBOL_USAGE[i], needsShift = true, typed = label)
            }
            DIGIT_SHIFT.indexOf(c) >= 0 -> {
                val i = DIGIT_SHIFT.indexOf(c)
                val u = if (i == 9) 0x27 else 0x1E + i
                KeyDef(label, KeyKind.Char, u, needsShift = true, typed = label)
            }
            else -> error("no key for '$label'")
        }
    }

    // Rows per layer, between the modifier row on top and the control row at the bottom.
    val layerRows: Map<Layer, List<List<String>>> = mapOf(
        Layer.Abc to listOf(
            "q w e r t y u i o p".split(' '),
            "a s d f g h j k l".split(' '),
            listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
        ),
        Layer.Num to listOf(
            "1 2 3 4 5 6 7 8 9 0".split(' '),
            listOf("-", "_", "=", "+", "*", "/", "%", ".", ","),
            listOf("⇧", "(", ")", ":", ";", "'", "\"", "⌫"),
        ),
        Layer.Sym to listOf(
            listOf("{", "}", "[", "]", "(", ")", "<", ">"),
            listOf("|", "\\", "/", "`", "~", "'", "\"", ":", ";"),
            listOf("!", "@", "#", "$", "%", "^", "&", "?", "⌫"),
        ),
        Layer.Fn to listOf(
            listOf("F1", "F2", "F3", "F4", "F5", "F6"),
            listOf("F7", "F8", "F9", "F10", "F11", "F12"),
            listOf("PgUp", "PgDn", "Ins", "Caps", "Home", "End", "⌫"),
        ),
    )

    val topRow = listOf("Esc", "Tab", "Ctrl", "Alt", "Super", "Del", "Home", "End")
    val bottomRow = listOf("ABC", "123", "{}#", "Fn", "space", "←", "↑", "↓", "→", "⏎")
}
