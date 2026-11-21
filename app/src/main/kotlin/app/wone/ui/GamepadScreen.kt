package app.wone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.wone.input.Bind
import app.wone.input.CtlType
import app.wone.input.PadBits
import app.wone.input.PadController
import app.wone.input.PadOut
import app.wone.wone
import kotlin.math.hypot
import kotlin.math.sqrt

@Composable
fun GamepadScreen(nav: (Screen) -> Unit) {
    val app = LocalContext.current.wone
    val settings = LocalSettings.current
    val accent = LocalAccent.current
    val layout = remember(settings.gamepadLayouts, settings.gamepadActive) { activeLayout(settings) }
    val controls = layout.controls
    val n = controls.size

    val pad = remember {
        PadController(object : PadOut {
            override fun gamepad(report: ByteArray) = app.engine.gamepad(report)
            override fun keyboard(mods: Int, keys: IntArray) = app.engine.keyboard(mods, keys)
            override fun mouse(buttons: Int) = app.engine.mouse(buttons, 0, 0)
            override fun look(buttons: Int, dx: Int, dy: Int) = app.engine.mouse(buttons, dx, dy)
        })
    }
    DisposableEffect(Unit) { onDispose { pad.releaseAll() } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    fun stickOut(ctl: app.wone.input.PadControl, x: Float, y: Float) {
        if (ctl.bind is Bind.Keys4) pad.setStickKeys(ctl, x, y) else pad.setStick((ctl.bind as? Bind.Axis)?.side ?: 0, x, y)
    }
    fun hatOut(ctl: app.wone.input.PadControl, hat: Int) {
        if (ctl.bind is Bind.Keys4) pad.setHatKeys(ctl, hat) else pad.setHat(hat)
    }
    val lastLook = remember { HashMap<PointerId, androidx.compose.ui.geometry.Offset>() }
    val lookFrac = remember { floatArrayOf(0f, 0f) }

    val pressed = remember(controls) { BooleanArray(n) }
    val depth = remember(controls) { FloatArray(n) }
    val vel = remember(controls) { FloatArray(n) }
    val knob = remember(controls) { FloatArray(n * 4) }      // x, y, vx, vy
    val holding = remember(controls) { BooleanArray(n) }
    val pull = remember(controls) { FloatArray(n) }
    val hats = remember(controls) { IntArray(n) }
    val edge = remember(controls) { BooleanArray(n) }
    val moved = remember(controls) { BooleanArray(n) }
    val time = remember { mutableFloatStateOf(0f) }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    val tm = rememberTextMeasurer(48)

    LaunchedEffect(controls) {
        var last = 0L
        while (true) {
            withFrameNanos { ns ->
                val dt = if (last == 0L) 0.016f else ((ns - last) / 1e9f).coerceAtMost(0.05f)
                last = ns
                time.floatValue += dt
                val k = 1400f; val c = 2f * 0.3f * sqrt(k)
                for (i in 0 until n) {
                    val ctl = controls[i]
                    // Pressed depth spring for every control type.
                    val target = if (pressed[i]) 1f else 0f
                    vel[i] += (-k * (depth[i] - target) - c * vel[i]) * dt
                    depth[i] += vel[i] * dt
                    when (ctl.type) {
                        CtlType.Trigger -> {
                            val side = (ctl.bind as? Bind.Axis)?.side ?: 0
                            val old = pull[i]
                            pull[i] = if (pressed[i]) (pull[i] + dt * 7f).coerceAtMost(1f) else 0f
                            if (pull[i] != old) {
                                pad.setTrigger(side, pull[i])
                                if (ctl.vibrate && pressed[i] && (old < 0.34f && pull[i] >= 0.34f || old < 0.67f && pull[i] >= 0.67f || old < 1f && pull[i] >= 1f)) app.haptics.trigger(pull[i])
                            }
                        }
                        CtlType.Stick -> if (!holding[i]) {
                            val o = i * 4
                            if (knob[o] != 0f || knob[o + 1] != 0f || knob[o + 2] != 0f || knob[o + 3] != 0f) {
                                val ks = 900f; val kc = 2f * 0.22f * sqrt(ks)
                                knob[o + 2] += (-ks * knob[o] - kc * knob[o + 2]) * dt
                                knob[o + 3] += (-ks * knob[o + 1] - kc * knob[o + 3]) * dt
                                knob[o] += knob[o + 2] * dt; knob[o + 1] += knob[o + 3] * dt
                                val settled = hypot(knob[o], knob[o + 1]) < 0.01f && hypot(knob[o + 2], knob[o + 3]) < 0.05f
                                if (settled) {
                                    knob[o] = 0f; knob[o + 1] = 0f; knob[o + 2] = 0f; knob[o + 3] = 0f
                                    if (moved[i] && ctl.vibrate) app.haptics.settle()
                                    moved[i] = false
                                }
                                stickOut(ctl, knob[o], knob[o + 1])
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    ModeScaffold(
        mode = Mode.Gamepad, nav = nav,
        bar = {
            Box(Modifier.weight(1f)) {
                androidx.compose.material3.TextButton({ menu = true }) {
                    androidx.compose.material3.Text(layout.name)
                    androidx.compose.material3.Icon(Icons.chevron, contentDescription = "Choose layout", modifier = Modifier.padding(start = 4.dp).height(20.dp))
                }
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    for (l in layoutsFrom(settings)) androidx.compose.material3.DropdownMenuItem(
                        text = { androidx.compose.material3.Text(l.name) },
                        onClick = { menu = false; scope.launch { app.settings.update { it.copy(gamepadActive = l.name) } } },
                    )
                }
            }
            WButton("Edit layout", { nav(Screen.GamepadEdit) }, Modifier.height2(), icon = Icons.edit)
            HostChip()
        },
    ) {
        val owner = remember { HashMap<PointerId, Int>() }
        Box(Modifier.fillMaxSize().onSizeChanged { areaSize = it }.pointerInput(controls) {
            owner.clear()
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent()
                    val w = areaSize.width.toFloat(); val h = areaSize.height.toFloat()
                    for (c in ev.changes) {
                        if (c.changedToDown()) {
                            val slop = 10.dp.toPx()
                            val idx = (n - 1 downTo 0).firstOrNull { controls[it].contains(c.position.x, c.position.y, w, h, slop) && !owner.containsValue(it) }
                            if (idx != null) {
                                owner[c.id] = idx
                                val ctl = controls[idx]
                                val g = ctl.geom(w, h)
                                when (ctl.type) {
                                    CtlType.Button, CtlType.Bumper -> {
                                        pressed[idx] = true; vel[idx] = 3f
                                        pad.setButton(ctl, true)
                                        val home = (ctl.bind as? Bind.Pad)?.bit == PadBits.HOME
                                        if (home) app.sounds.chime() else if (ctl.type == CtlType.Bumper) app.sounds.bumper() else app.sounds.padDown()
                                        if (ctl.vibrate) if (home) app.haptics.sharp() else app.haptics.click()
                                    }
                                    CtlType.Trigger -> { pressed[idx] = true; vel[idx] = 3f; app.sounds.bumper(); if (ctl.vibrate) app.haptics.click() }
                                    CtlType.Stick -> {
                                        holding[idx] = true; moved[idx] = true; edge[idx] = false
                                        val r = g.w / 2 * 0.9f
                                        val o = idx * 4
                                        knob[o + 2] = 0f; knob[o + 3] = 0f
                                        val dx = (c.position.x - g.cx) / r; val dy = (c.position.y - g.cy) / r
                                        val m = hypot(dx, dy).coerceAtLeast(1f)
                                        knob[o] = dx / m; knob[o + 1] = dy / m
                                        stickOut(ctl, knob[o], knob[o + 1])
                                    }
                                    CtlType.Look -> { lastLook[c.id] = c.position; if (ctl.vibrate) app.haptics.tap() }
                                    CtlType.DPad -> {
                                        val hat = PadController.hatFor(c.position.x - g.cx, c.position.y - g.cy, g.w * 0.12f)
                                        hats[idx] = hat; hatOut(ctl, hat)
                                        app.sounds.dpad(); if (ctl.vibrate) app.haptics.sharp()
                                    }
                                }
                            }
                            c.consume()
                        } else if (c.changedToUp()) {
                            val idx = owner.remove(c.id)
                            if (idx != null) {
                                val ctl = controls[idx]
                                when (ctl.type) {
                                    CtlType.Button, CtlType.Bumper -> { pressed[idx] = false; pad.setButton(ctl, false); app.sounds.padUp() }
                                    CtlType.Trigger -> { pressed[idx] = false; pull[idx] = 0f; pad.setTrigger((ctl.bind as? Bind.Axis)?.side ?: 0, 0f) }
                                    CtlType.Stick -> holding[idx] = false
                                    CtlType.DPad -> { hats[idx] = 0; hatOut(ctl, 0) }
                                    CtlType.Look -> lastLook.remove(c.id)
                                }
                            }
                            c.consume()
                        } else if (c.positionChanged()) {
                            val idx = owner[c.id]
                            if (idx != null) {
                                val ctl = controls[idx]; val g = ctl.geom(w, h)
                                when (ctl.type) {
                                    CtlType.Stick -> {
                                        val r = g.w / 2 * 0.9f; val o = idx * 4
                                        val dx = (c.position.x - g.cx) / r; val dy = (c.position.y - g.cy) / r
                                        val m = hypot(dx, dy).coerceAtLeast(1f)
                                        knob[o] = dx / m; knob[o + 1] = dy / m
                                        val mag = hypot(knob[o], knob[o + 1])
                                        if (mag > 0.97f && !edge[idx]) { edge[idx] = true; if (ctl.vibrate) app.haptics.edge() } else if (mag < 0.85f) edge[idx] = false
                                        stickOut(ctl, knob[o], knob[o + 1])
                                    }
                                    CtlType.Look -> {
                                        val prev = lastLook[c.id] ?: c.position
                                        val gain = (ctl.bind as? Bind.Look)?.gain ?: 2f
                                        lookFrac[0] += (c.position.x - prev.x) * gain / 2f
                                        lookFrac[1] += (c.position.y - prev.y) * gain / 2f
                                        val ix = lookFrac[0].toInt(); val iy = lookFrac[1].toInt()
                                        lookFrac[0] -= ix; lookFrac[1] -= iy
                                        if (ix != 0 || iy != 0) pad.look(ix, iy)
                                        lastLook[c.id] = c.position
                                    }
                                    CtlType.DPad -> {
                                        val hat = PadController.hatFor(c.position.x - g.cx, c.position.y - g.cy, g.w * 0.12f)
                                        if (hat != hats[idx]) { hats[idx] = hat; hatOut(ctl, hat); if (hat != 0 && ctl.vibrate) app.haptics.tap() }
                                    }
                                    else -> Unit
                                }
                            }
                            c.consume()
                        }
                    }
                }
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val t = time.floatValue
                for (i in 0 until n) {
                    val c = controls[i]
                    val o = i * 4
                    val v = PadVisual(depth[i], knob[o], knob[o + 1], hats[i], pull[i])
                    drawControl(c, size.width, size.height, v, controlLight(settings, accent, c, t), settings.brightness, tm, accent, false)
                }
            }
        }
    }
}

private fun Modifier.padding2() = this.then(androidx.compose.ui.Modifier.padding(start = 4.dp))
private fun Modifier.height2() = this.then(androidx.compose.ui.Modifier.height(44.dp))
