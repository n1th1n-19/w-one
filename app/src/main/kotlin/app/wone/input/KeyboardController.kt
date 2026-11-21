package app.wone.input

import app.wone.hid.HidReports

/** Where finished reports go. The HID engine in the app, a recorder in tests. */
fun interface KeyboardSink {
    fun keyboard(modifiers: Int, keys: IntArray)
}

enum class StickyState { Off, Latched, Locked }

/**
 * Turns key presses into keyboard reports.
 *
 * Modifiers work three ways: hold one while pressing a key (a normal chord), tap it once to apply
 * to the next key only, tap it twice to lock it. Held keys stay down in the report, so the
 * computer does its own auto-repeat.
 */
class KeyboardController(private val sink: KeyboardSink, private val onTyped: (String) -> Unit = {}) {

    private val held = LinkedHashSet<KeyDef>()          // keys currently down
    private var heldMods = 0                             // modifier keys physically down
    private var heldModUsed = 0                          // held modifiers that were used in a chord
    val sticky = IntArray(4)                             // StickyState ordinal per modifier bit index

    private val bits = intArrayOf(Keys.CTRL, Keys.SHIFT, Keys.ALT, Keys.SUPER)
    private fun idx(bit: Int) = bits.indexOf(bit)

    /** Callback when sticky or held modifier state changes, for drawing the keys as active. */
    var onModsChanged: () -> Unit = {}

    fun stateOf(bit: Int): StickyState = StickyState.entries[sticky[idx(bit)]]
    fun isActive(bit: Int): Boolean = (heldMods and bit) != 0 || stateOf(bit) != StickyState.Off

    val activeMods: Int
        get() = bits.fold(0) { acc, b -> if (isActive(b)) acc or b else acc }

    fun down(key: KeyDef) {
        when (key.kind) {
            KeyKind.Modifier -> { heldMods = heldMods or key.modBit; send(); onModsChanged() }
            KeyKind.Layer -> Unit
            else -> {
                heldModUsed = heldModUsed or heldMods
                held.add(key)
                send()
                typedFor(key)
            }
        }
    }

    fun up(key: KeyDef) {
        when (key.kind) {
            KeyKind.Modifier -> {
                val used = (heldModUsed and key.modBit) != 0
                heldMods = heldMods and key.modBit.inv()
                heldModUsed = heldModUsed and key.modBit.inv()
                if (!used) {
                    val i = idx(key.modBit)
                    sticky[i] = (sticky[i] + 1) % 3
                }
                send(); onModsChanged()
            }
            KeyKind.Layer -> Unit
            else -> {
                held.remove(key)
                // Latched (one-shot) modifiers end with the key they applied to.
                for (i in sticky.indices) if (sticky[i] == StickyState.Latched.ordinal) sticky[i] = 0
                send(); onModsChanged()
            }
        }
    }

    /** Releases everything, for leaving the screen or a lost connection. */
    fun reset() {
        held.clear(); heldMods = 0; heldModUsed = 0; sticky.fill(0)
        sink.keyboard(0, IntArray(0))
        onModsChanged()
    }

    private fun send() {
        var mods = activeMods
        if (held.any { it.needsShift }) mods = mods or Keys.SHIFT
        val keys = held.map { it.usage }.take(6).toIntArray()
        sink.keyboard(mods, keys)
    }

    /** The preview shows text only: letters, symbols, space and backspace. No chords, arrows or Enter. */
    private fun typedFor(key: KeyDef) {
        val mods = activeMods
        if (mods and (Keys.CTRL or Keys.ALT or Keys.SUPER) != 0) return
        val text = key.typed ?: return
        if (text == "\n" || text == "\t") return
        val shifted = (mods and Keys.SHIFT) != 0
        onTyped(if (key.kind == KeyKind.Char && shifted && key.shiftChar != null) key.shiftChar else text)
    }
}
