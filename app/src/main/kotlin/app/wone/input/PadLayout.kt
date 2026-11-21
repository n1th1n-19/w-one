package app.wone.input

import org.json.JSONArray
import org.json.JSONObject

enum class CtlType { Button, Trigger, Bumper, Stick, DPad }

/** What a control sends. Gamepad buttons use the bit positions of the report's 16 buttons. */
sealed interface Bind {
    data class Pad(val bit: Int) : Bind
    data class Key(val usage: Int, val mods: Int = 0) : Bind
    data class Mouse(val bit: Int) : Bind
    /** Analog: 0 = left, 1 = right. Used by sticks and triggers. */
    data class Axis(val side: Int) : Bind
    data object Hat : Bind
}

/** Report button bits, chosen so Linux maps them to the usual evdev gamepad codes. */
object PadBits {
    const val A = 0        // BTN_SOUTH
    const val B = 1        // BTN_EAST
    const val Y = 3        // BTN_NORTH
    const val X = 4        // BTN_WEST
    const val LB = 6       // BTN_TL
    const val RB = 7       // BTN_TR
    const val LT = 8       // BTN_TL2
    const val RT = 9       // BTN_TR2
    const val SELECT = 10
    const val START = 11
    const val HOME = 12    // BTN_MODE
    const val L3 = 13
    const val R3 = 14
}

/** [x], [y] are the centre as fractions of the pad area; [size] is a fraction of its height. */
data class PadControl(
    val id: String,
    val type: CtlType,
    val label: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val opacity: Float = 1f,
    val vibrate: Boolean = true,
    val bind: Bind,
)

data class PadLayout(val name: String, val controls: List<PadControl>) {

    fun toJson(): JSONObject = JSONObject().put("name", name).put(
        "controls",
        JSONArray().also { arr -> controls.forEach { arr.put(it.toJson()) } },
    )

    companion object {
        const val MAX_CONTROLS = 40

        fun fromJson(o: JSONObject): PadLayout {
            val arr = o.getJSONArray("controls")
            val list = ArrayList<PadControl>()
            for (i in 0 until minOf(arr.length(), MAX_CONTROLS)) list += controlFromJson(arr.getJSONObject(i))
            return PadLayout(o.getString("name").take(40), list)
        }

        fun listFromJson(text: String): List<PadLayout> = try {
            val arr = JSONArray(text)
            (0 until minOf(arr.length(), 20)).map { fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }

        fun listToJson(list: List<PadLayout>): String =
            JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString()

        fun default(): PadLayout = PadLayout(
            "Default",
            listOf(
                PadControl("lt", CtlType.Trigger, "LT", 0.10f, 0.09f, 0.17f, bind = Bind.Axis(0)),
                PadControl("lb", CtlType.Bumper, "LB", 0.10f, 0.27f, 0.15f, bind = Bind.Pad(PadBits.LB)),
                PadControl("rt", CtlType.Trigger, "RT", 0.90f, 0.09f, 0.17f, bind = Bind.Axis(1)),
                PadControl("rb", CtlType.Bumper, "RB", 0.90f, 0.27f, 0.15f, bind = Bind.Pad(PadBits.RB)),
                PadControl("ls", CtlType.Stick, "L", 0.17f, 0.62f, 0.40f, bind = Bind.Axis(0)),
                PadControl("dpad", CtlType.DPad, "", 0.36f, 0.80f, 0.30f, bind = Bind.Hat),
                PadControl("rs", CtlType.Stick, "R", 0.64f, 0.80f, 0.34f, bind = Bind.Axis(1)),
                PadControl("sel", CtlType.Button, "SEL", 0.43f, 0.42f, 0.11f, bind = Bind.Pad(PadBits.SELECT)),
                PadControl("home", CtlType.Button, "W1", 0.50f, 0.42f, 0.11f, bind = Bind.Pad(PadBits.HOME)),
                PadControl("sta", CtlType.Button, "STA", 0.57f, 0.42f, 0.11f, bind = Bind.Pad(PadBits.START)),
                PadControl("y", CtlType.Button, "Y", 0.85f, 0.40f, 0.17f, bind = Bind.Pad(PadBits.Y)),
                PadControl("x", CtlType.Button, "X", 0.76f, 0.57f, 0.17f, bind = Bind.Pad(PadBits.X)),
                PadControl("b", CtlType.Button, "B", 0.94f, 0.57f, 0.17f, bind = Bind.Pad(PadBits.B)),
                PadControl("a", CtlType.Button, "A", 0.85f, 0.74f, 0.17f, bind = Bind.Pad(PadBits.A)),
            ),
        )

        private fun controlFromJson(o: JSONObject): PadControl {
            val b = o.getJSONObject("bind")
            val bind = when (b.getString("k")) {
                "pad" -> Bind.Pad(b.getInt("v").coerceIn(0, 15))
                "key" -> Bind.Key(b.getInt("v").coerceIn(0, 255), b.optInt("m", 0).coerceIn(0, 15))
                "mouse" -> Bind.Mouse(b.getInt("v").coerceIn(0, 4))
                "axis" -> Bind.Axis(b.getInt("v").coerceIn(0, 1))
                "hat" -> Bind.Hat
                else -> error("unknown binding")
            }
            return PadControl(
                id = o.getString("id").take(24),
                type = CtlType.valueOf(o.getString("type")),
                label = o.optString("label", "").take(8),
                x = o.getDouble("x").toFloat().coerceIn(0f, 1f),
                y = o.getDouble("y").toFloat().coerceIn(0f, 1f),
                size = o.getDouble("size").toFloat().coerceIn(0.06f, 0.6f),
                opacity = o.optDouble("opacity", 1.0).toFloat().coerceIn(0.2f, 1f),
                vibrate = o.optBoolean("vibrate", true),
                bind = bind,
            )
        }
    }
}

private fun PadControl.toJson(): JSONObject {
    val b = JSONObject()
    when (val k = bind) {
        is Bind.Pad -> b.put("k", "pad").put("v", k.bit)
        is Bind.Key -> b.put("k", "key").put("v", k.usage).put("m", k.mods)
        is Bind.Mouse -> b.put("k", "mouse").put("v", k.bit)
        is Bind.Axis -> b.put("k", "axis").put("v", k.side)
        Bind.Hat -> b.put("k", "hat")
    }
    return JSONObject().put("id", id).put("type", type.name).put("label", label)
        .put("x", x.toDouble()).put("y", y.toDouble()).put("size", size.toDouble())
        .put("opacity", opacity.toDouble()).put("vibrate", vibrate).put("bind", b)
}
