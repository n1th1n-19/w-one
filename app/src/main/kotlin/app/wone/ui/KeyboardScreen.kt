package app.wone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wone.data.KeyboardFx
import app.wone.input.KeyDef
import app.wone.input.KeyKind
import app.wone.input.KeyboardController
import app.wone.input.Keys
import app.wone.input.Layer
import app.wone.input.StickyState
import app.wone.wone
import kotlin.math.sqrt

private class KeyRect(
    val def: KeyDef, val x: Float, val y: Float, val w: Float, val h: Float,
    val row: Int, val col: Int, val index: Int, val light: KeyLight,
)

private fun weightOf(d: KeyDef, rowHasWideMods: Boolean): Float = when {
    d.kind == KeyKind.Space -> 3.2f
    d.label == "⏎" -> 1.6f
    d.kind == KeyKind.Layer -> 1.3f
    d.label == "⇧" || d.label == "⌫" -> if (rowHasWideMods) 1.5f else 1f
    else -> 1f
}

/** Lays the keys of one layer out to fill [w] x [h]; every row spans the width. */
private fun layoutKeys(layer: Layer, w: Float, h: Float, gap: Float): List<KeyRect> {
    val rows = buildList {
        add(Keys.topRow)
        addAll(Keys.layerRows.getValue(layer))
        add(Keys.bottomRow)
    }
    val rowH = (h - gap * (rows.size - 1)) / rows.size
    val out = ArrayList<KeyRect>()
    var index = 0
    rows.forEachIndexed { r, labels ->
        val defs = labels.map { Keys.of(it) }
        val weights = defs.map { weightOf(it, true) }
        val sum = weights.sum()
        val isCharRow = r in 1..3 && defs.size >= 9
        val unitFull = (w - gap * (defs.size - 1)) / sum
        // Rows of 9 or 10 character keys keep one key width across rows and are centred.
        val unitRef = (w - gap * 9) / 10f
        val centred = isCharRow && sum < 10f
        val unit = if (centred) unitRef else unitFull
        val total = sum * unit + gap * (defs.size - 1)
        var x = if (centred) (w - total) / 2f else 0f
        val y = r * (rowH + gap)
        defs.forEachIndexed { c, d ->
            val kw = weights[c] * unit
            val light = KeyLight(index, d.label, (x + kw / 2) / w, (y + rowH / 2) / h, c, r, ((index * 53) % 97) / 97f)
            out += KeyRect(d, x, y, kw, rowH, r, c, index, light)
            x += kw + gap
            index++
        }
    }
    return out
}

private fun appendTyped(cur: String, token: String): String {
    val next = if (token == "\b") {
        if (cur.endsWith("]")) cur.substring(0, cur.lastIndexOf('[').coerceAtLeast(0)) else cur.dropLast(1)
    } else cur + token
    return if (next.length > 2000) next.takeLast(2000) else next
}

@Composable
fun KeyboardScreen(nav: (Screen) -> Unit) {
    val app = LocalContext.current.wone
    val settings = LocalSettings.current
    val accent = LocalAccent.current
    val secure = rememberUpdatedState(settings.secureTyping)
    var layer by remember { mutableStateOf(Layer.Abc) }
    val typed = remember { mutableStateOf("") }
    var panel by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val time = remember { mutableFloatStateOf(0f) }
    val heat = remember { HashMap<String, Int>() }
    val hits = remember { ArrayList<Hit>() }
    val pressed = remember { BooleanArray(80) }
    val depth = remember { FloatArray(80) }
    val vel = remember { FloatArray(80) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalContext.current.resources.displayMetrics.density

    val ctl = remember {
        KeyboardController({ m, k -> app.engine.keyboard(m, k) }, onTyped = { typed.value = appendTyped(typed.value, it) })
    }
    DisposableEffect(Unit) { onDispose { ctl.reset() } }

    // Frame loop: advances the clock and the key springs.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                val dt = if (last == 0L) 0.016f else ((n - last) / 1e9f).coerceAtMost(0.05f)
                last = n
                time.floatValue += dt
                val k = 1500f; val c = 2f * 0.32f * sqrt(k)
                repeat(2) {
                    val h = dt / 2f
                    for (i in depth.indices) {
                        val target = if (pressed[i]) 1f else 0f
                        vel[i] += (-k * (depth[i] - target) - c * vel[i]) * h
                        depth[i] += vel[i] * h
                    }
                }
                hits.removeAll { time.floatValue - it.t0 > 3.5f }
            }
        }
    }

    val gap = 5f * density
    val keys = remember(layer, size) {
        if (size.width == 0) emptyList() else layoutKeys(layer, size.width.toFloat(), size.height.toFloat(), gap)
    }
    val keysNow = rememberUpdatedState(keys)
    val tm = rememberTextMeasurer(64)

    ModeScaffold(
        mode = Mode.Keyboard, nav = nav,
        bar = {
            PreviewBar(typed.value, settings.secureTyping, Modifier.weight(1f))
            IconBtn(if (expanded) Icons.up else Icons.down, "Expand typed text", { expanded = !expanded }, active = expanded)
            IconBtn(Icons.close, "Clear typed text", { typed.value = "" })
            IconBtn(Icons.light, "Lighting effects", { panel = !panel }, active = panel)
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier.fillMaxSize()
                    .onSizeChanged { size = it }
                    .pointerInput(Unit) {
                        val down = HashMap<PointerId, KeyRect>()
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                for (c in ev.changes) {
                                    if (c.changedToDown()) {
                                        val hit = keysNow.value.firstOrNull { c.position.x >= it.x && c.position.x <= it.x + it.w && c.position.y >= it.y && c.position.y <= it.y + it.h }
                                            ?: keysNow.value.minByOrNull { k -> (k.x + k.w / 2 - c.position.x).let { dx -> dx * dx } + (k.y + k.h / 2 - c.position.y).let { dy -> dy * dy } }
                                        if (hit != null) {
                                            down[c.id] = hit
                                            val d = hit.def
                                            val wide = d.wide || d.kind == KeyKind.Space
                                            if (d.kind == KeyKind.Layer) {
                                                layer = d.layer!!
                                                app.sounds.keyDown(d.label.hashCode(), false); app.haptics.click()
                                            } else {
                                                ctl.down(d)
                                                app.sounds.keyDown(if (d.usage != 0) d.usage else d.label.hashCode(), wide)
                                                app.haptics.key()
                                                heat.merge(d.label, 1, Int::plus)
                                                hits += Hit(hit.index, hit.light.nx, hit.light.ny, hit.col, hit.row, time.floatValue)
                                            }
                                            pressed[hit.index] = true
                                            vel[hit.index] = vel[hit.index].coerceAtLeast(2f)
                                        }
                                        c.consume()
                                    } else if (c.changedToUp()) {
                                        val hit = down.remove(c.id)
                                        if (hit != null) {
                                            pressed[hit.index] = false
                                            val d = hit.def
                                            if (d.kind != KeyKind.Layer) {
                                                ctl.up(d)
                                                app.sounds.keyUp(if (d.usage != 0) d.usage else d.label.hashCode(), d.wide || d.kind == KeyKind.Space)
                                            }
                                        }
                                        c.consume()
                                    }
                                }
                            }
                        }
                    },
            ) {
                val t = time.floatValue
                val heatMax = heat.values.maxOrNull() ?: 1
                val aspect = size.width.toFloat() / size.height.coerceAtLeast(1)
                val shiftOn = ctl.isActive(Keys.SHIFT)
                val radius = CornerRadius(9f * density)
                val bar = 3f * density
                for (k in keys) {
                    val d = k.def
                    val fx = settings.keyboardFx
                    val light = keyLight(fx, k.light, t, settings.speed, accent, aspect, hits, heat, heatMax)
                    val a = light.alpha * settings.brightness
                    val lc = light.copy(alpha = 1f)
                    val isMod = d.kind == KeyKind.Modifier
                    val st = if (isMod) ctl.stateOf(d.modBit) else StickyState.Off
                    val held = isMod && ctl.isActive(d.modBit) && st == StickyState.Off
                    val isLayer = d.kind == KeyKind.Layer && d.layer == layer
                    var body = if (isMod || d.kind == KeyKind.Layer || d.kind == KeyKind.Special) Pal.raised else Pal.key
                    if (a > 0f) body = lerp(body, lc, a * 0.16f)
                    if (st == StickyState.Locked) body = accent
                    if (held || isLayer) body = Pal.keyHi
                    val dp = depth[k.index].coerceIn(-0.4f, 1.4f)
                    val sc = 1f - 0.06f * dp
                    val cx = k.x + k.w / 2f; val cy = k.y + k.h / 2f + 2f * density * dp
                    val w = k.w * sc; val h = k.h * sc
                    val tl = Offset(cx - w / 2f, cy - h / 2f)
                    drawRoundRect(body, tl, Size(w, h), radius)
                    if (st == StickyState.Latched) drawRoundRect(accent, tl, Size(w, h), radius, style = Stroke(2f * density))
                    if (a > 0.02f) {
                        drawRoundRect(lc.copy(alpha = a), Offset(tl.x + 8f * density, tl.y + h - bar - 2f * density), Size(w - 16f * density, bar), CornerRadius(bar / 2))
                    }
                    var label = d.label
                    if (shiftOn && d.shiftChar != null) label = d.shiftChar
                    if (d.kind == KeyKind.Space) label = "space"
                    val ink = when {
                        st == StickyState.Locked -> Pal.onAccent
                        isLayer || held || st == StickyState.Latched -> accent
                        else -> if (a > 0f) lerp(Pal.text, lc, a * 0.55f) else Pal.text
                    }
                    val small = d.label.length > 2
                    val style = TextStyle(fontFamily = Mono, fontSize = (if (small) 12 else 16).sp, color = ink)
                    val layout = tm.measure(label, style)
                    drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f - bar / 3f))
                }
            }
            if (expanded) ExpandedPreview(typed.value, settings.secureTyping, Modifier.align(Alignment.TopStart))
            if (panel) LightingPanel(onClose = { panel = false })
        }
    }
}

private fun mask(text: String, secure: Boolean) = if (secure) text.replace(Regex("[^\\n]"), "•") else text

/** One line that always ends at the latest character, using all the width there is. */
@Composable
private fun PreviewBar(text: String, secure: Boolean, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val charPx = remember { tm.measure("M", Type.code).size.width.coerceAtLeast(1) }
    var widthPx by remember { mutableStateOf(0) }
    val fit = ((widthPx - 28 * LocalContext.current.resources.displayMetrics.density) / charPx).toInt().coerceAtLeast(4)
    val shown = mask(text, secure).takeLast(fit)
    Row(
        modifier.height(44.dp).onSizeChanged { widthPx = it.width }.panel(Pal.surface, 12.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start,
    ) {
        if (shown.isEmpty()) Tx("typed text shows here", Type.code, Pal.faint)
        else Tx(shown, Type.code)
    }
}

/** Taller panel with the whole text, wrapped, scrolled to the newest line. */
@Composable
private fun ExpandedPreview(text: String, secure: Boolean, modifier: Modifier) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
    Box(
        modifier.fillMaxWidth().fillMaxHeight(0.4f).panel(Pal.raised, 14.dp)
            .verticalScroll(scroll).padding(14.dp),
    ) {
        val shown = mask(text, secure)
        if (shown.isEmpty()) Tx("typed text shows here", Type.code, Pal.faint) else Tx(shown, Type.code)
    }
}

