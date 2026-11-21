package app.wone.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import app.wone.data.RingFx
import app.wone.data.Settings
import app.wone.input.Bind
import app.wone.input.CtlType
import app.wone.input.PadControl
import app.wone.input.PadLayout
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/** Pixel geometry of one control inside the pad area. */
class PadGeom(val cx: Float, val cy: Float, val w: Float, val h: Float)

fun PadControl.geom(areaW: Float, areaH: Float): PadGeom {
    val d = size * areaH
    return when (type) {
        CtlType.Trigger, CtlType.Bumper -> PadGeom(x * areaW, y * areaH, d * 1.9f, d * 0.75f)
        else -> PadGeom(x * areaW, y * areaH, d, d)
    }
}

fun PadControl.contains(px: Float, py: Float, areaW: Float, areaH: Float, slop: Float = 0f): Boolean {
    val g = geom(areaW, areaH)
    return when (type) {
        CtlType.Button, CtlType.Stick -> hypot(px - g.cx, py - g.cy) <= g.w / 2 + slop
        else -> px >= g.cx - g.w / 2 - slop && px <= g.cx + g.w / 2 + slop && py >= g.cy - g.h / 2 - slop && py <= g.cy + g.h / 2 + slop
    }
}

/** Per-control animation state while drawing. */
class PadVisual(val depth: Float = 0f, val knobX: Float = 0f, val knobY: Float = 0f, val hat: Int = 0, val pull: Float = 0f)

fun controlLight(s: Settings, accent: Color, c: PadControl, t: Float): Color = when (s.ringFx) {
    RingFx.Wave -> hueColor(c.x * 0.8f - t / max(0.5f, s.speed))
    RingFx.Breathe -> accent.copy(alpha = 0.3f + 0.7f * (0.5f - 0.5f * cos(2 * PI.toFloat() * t / max(0.5f, s.speed))))
    RingFx.Static -> accent
    RingFx.Off -> Pal.line
}

fun DrawScope.drawControl(
    c: PadControl, areaW: Float, areaH: Float, v: PadVisual, light: Color,
    brightness: Float, tm: TextMeasurer, accent: Color, selected: Boolean,
) {
    val g = c.geom(areaW, areaH)
    val dpx = density
    val ring = light.copy(alpha = (light.alpha * brightness).coerceIn(0f, 1f))
    val a = c.opacity
    val sc = 1f - 0.12f * v.depth.coerceIn(-0.3f, 1.3f)
    val ringW = 2f * dpx
    when (c.type) {
        CtlType.Button -> {
            val r = g.w / 2 * sc
            drawCircle(Pal.raised.copy(alpha = a), r, Offset(g.cx, g.cy))
            drawCircle(ring.copy(alpha = ring.alpha * a), r, Offset(g.cx, g.cy), style = Stroke(ringW))
            label(tm, c.label, g.cx, g.cy, Pal.text.copy(alpha = a), if (c.label.length > 2) 11 else 17)
        }
        CtlType.Bumper, CtlType.Trigger -> {
            val dy = if (c.type == CtlType.Trigger) 5f * dpx * v.depth else 0f
            val w = g.w * (1f - 0.04f * v.depth.coerceIn(0f, 1f)); val h = g.h * (1f - 0.1f * v.depth.coerceIn(0f, 1f))
            val tl = Offset(g.cx - w / 2, g.cy - h / 2 + dy)
            val rad = CornerRadius(10f * dpx)
            drawRoundRect(Pal.raised.copy(alpha = a), tl, Size(w, h), rad)
            if (c.type == CtlType.Trigger && v.pull > 0f) {
                // Fill rises with how far the trigger is pulled.
                drawRoundRect(accent.copy(alpha = 0.5f * a), Offset(tl.x, tl.y + h * (1f - v.pull)), Size(w, h * v.pull), rad)
            }
            drawRoundRect(ring.copy(alpha = ring.alpha * a), tl, Size(w, h), rad, style = Stroke(ringW))
            label(tm, c.label, g.cx, g.cy + dy, Pal.text.copy(alpha = a), 14)
        }
        CtlType.Stick -> {
            val r = g.w / 2
            drawCircle(Pal.surface.copy(alpha = a), r, Offset(g.cx, g.cy))
            drawCircle(Pal.line.copy(alpha = a), r, Offset(g.cx, g.cy), style = Stroke(1.5f * dpx))
            val mag = hypot(v.knobX, v.knobY).coerceAtMost(1f)
            val kr = r * 0.46f
            val kc = Offset(g.cx + v.knobX * (r - kr), g.cy + v.knobY * (r - kr))
            drawCircle(Pal.keyHi.copy(alpha = a), kr, kc)
            // The ring brightens the further the stick is pushed.
            drawCircle(lerp(Pal.line, ring, 0.35f + 0.65f * mag).copy(alpha = a), kr, kc, style = Stroke(ringW))
            label(tm, c.label, kc.x, kc.y, Pal.muted.copy(alpha = a), 13)
        }
        CtlType.DPad -> {
            val arm = g.w * 0.34f; val len = g.w / 2
            val rad = CornerRadius(8f * dpx)
            fun armRect(dx: Int, dy: Int, active: Boolean) {
                val col = if (active) accent.copy(alpha = a) else Pal.raised.copy(alpha = a)
                val s = if (active) 0.94f else 1f
                val (w, h) = if (dx != 0) len to arm else arm to len
                val ox = g.cx + dx * len / 2 - w / 2 * s; val oy = g.cy + dy * len / 2 - h / 2 * s
                drawRoundRect(col, Offset(ox, oy), Size(w * s, h * s), rad)
                drawRoundRect(ring.copy(alpha = ring.alpha * a * 0.8f), Offset(ox, oy), Size(w * s, h * s), rad, style = Stroke(1.5f * dpx))
            }
            val h = v.hat
            armRect(0, -1, h == 1 || h == 2 || h == 8); armRect(1, 0, h in 2..4); armRect(0, 1, h in 4..6); armRect(-1, 0, h in 6..8)
            drawCircle(Pal.raised.copy(alpha = a), arm / 2, Offset(g.cx, g.cy))
        }
    }
    if (selected) {
        val pad = 6f * dpx
        val rect = if (c.type == CtlType.Button || c.type == CtlType.Stick) g.w to g.w else g.w to g.h
        drawRoundRect(
            accent, Offset(g.cx - rect.first / 2 - pad, g.cy - rect.second / 2 - pad),
            Size(rect.first + pad * 2, rect.second + pad * 2), CornerRadius(14f * dpx),
            style = Stroke(2f * dpx, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f * dpx, 6f * dpx))),
        )
    }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, cx: Float, cy: Float, color: Color, sp: Int) {
    if (text.isEmpty()) return
    val r = tm.measure(text, TextStyle(fontFamily = Mono, fontSize = sp.sp, color = color))
    drawText(r, topLeft = Offset(cx - r.size.width / 2f, cy - r.size.height / 2f))
}

fun layoutsFrom(s: Settings): List<PadLayout> =
    PadLayout.listFromJson(s.gamepadLayouts).ifEmpty { listOf(PadLayout.default()) }

fun activeLayout(s: Settings): PadLayout {
    val all = layoutsFrom(s)
    return all.firstOrNull { it.name == s.gamepadActive } ?: all.first()
}

val Bind.isDigital get() = this is Bind.Pad || this is Bind.Key || this is Bind.Mouse
