package app.wone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.wone.data.RingFx
import app.wone.hid.HidReports
import app.wone.input.Bind
import app.wone.input.CtlType
import app.wone.input.KeyKind
import app.wone.input.Keys
import app.wone.input.PadControl
import app.wone.input.PadLayout
import app.wone.wone
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

private val padNames = listOf(
    "A" to 0, "B" to 1, "X" to 4, "Y" to 3, "LB" to 6, "RB" to 7, "LT" to 8, "RT" to 9,
    "SEL" to 10, "START" to 11, "HOME" to 12, "L3" to 13, "R3" to 14,
)
private val keyChoices = listOf("Esc", "Tab", "space", "⏎", "⌫", "←", "↑", "↓", "→") +
    ('a'..'z').map { it.toString() } + ('0'..'9').map { it.toString() } + (1..12).map { "F$it" }
private val modChoices = listOf("Ctrl" to HidReports.MOD_LCTRL, "Shift" to HidReports.MOD_LSHIFT, "Alt" to HidReports.MOD_LALT, "Super" to HidReports.MOD_LGUI)
private val mouseChoices = listOf("Left" to 0, "Right" to 1, "Middle" to 2)

@Composable
fun GamepadEditScreen(nav: (Screen) -> Unit) {
    val app = LocalContext.current.wone
    val settings = LocalSettings.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()

    val all = remember { mutableStateListOf<PadLayout>().also { it.addAll(layoutsFrom(settings)) } }
    var current by remember { mutableStateOf(activeLayout(settings).name) }
    var name by remember { mutableStateOf(current) }
    val controls = remember { mutableStateListOf<PadControl>().also { it.addAll(activeLayout(settings).controls) } }
    var selected by remember { mutableStateOf<String?>(null) }
    var snap by remember { mutableStateOf(true) }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    val tm = rememberTextMeasurer(32)
    val clock = rememberClock(false)

    fun sel(): PadControl? = controls.firstOrNull { it.id == selected }
    fun update(id: String, f: (PadControl) -> PadControl) {
        val i = controls.indexOfFirst { it.id == id }
        if (i >= 0) controls[i] = f(controls[i])
    }
    fun stash() {
        val i = all.indexOfFirst { it.name == current }
        val l = PadLayout(name.ifBlank { "Layout" }.take(40), controls.toList())
        if (i >= 0) all[i] = l else all.add(l)
        current = l.name
    }
    fun persist() {
        stash()
        val json = PadLayout.listToJson(all.toList())
        scope.launch { app.settings.update { it.copy(gamepadLayouts = json, gamepadActive = current) } }
    }
    fun load(l: PadLayout) { current = l.name; name = l.name; controls.clear(); controls.addAll(l.controls); selected = null }

    ModeScaffold(
        mode = Mode.Gamepad, nav = { s -> persist(); nav(s) },
        bar = {
            WButton("Add button", {
                if (controls.size < PadLayout.MAX_CONTROLS) {
                    val id = "c" + System.currentTimeMillis()
                    controls += PadControl(id, CtlType.Button, "A", 0.5f, 0.5f, 0.15f, bind = Bind.Pad(0))
                    selected = id
                }
            }, Modifier.height(44.dp), icon = Icons.plus)
            Row(Modifier.height(44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tx("Snap", Type.label)
                WSwitch(snap, { snap = it }, "Snap to grid")
            }
            Tx("Drag to move. Pinch to resize.", Type.label, Pal.faint, Modifier.weight(1f))
            WButton("Done", { persist(); nav(Screen.Gamepad) }, Modifier.height(44.dp), primary = true, icon = Icons.check)
        },
    ) {
        val s = sel()
        val panelLeft = (s?.x ?: 0f) > 0.5f
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().panel(Pal.surface, 16.dp).onSizeChanged { areaSize = it }.pointerInput(Unit) {
                    val pos = HashMap<PointerId, androidx.compose.ui.geometry.Offset>()
                    var dragging: String? = null
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val w = areaSize.width.toFloat(); val h = areaSize.height.toFloat()
                            val before = pos.values.toList()
                            for (c in ev.changes) {
                                if (c.changedToDown()) {
                                    pos[c.id] = c.position
                                    if (pos.size == 1) {
                                        val hit = controls.lastOrNull { it.contains(c.position.x, c.position.y, w, h, 12.dp.toPx()) }
                                        if (hit != null) { selected = hit.id; dragging = hit.id } else { selected = null; dragging = null }
                                    }
                                } else if (c.changedToUp()) {
                                    pos.remove(c.id)
                                    if (pos.isEmpty()) {
                                        dragging = null
                                    }
                                } else if (pos.containsKey(c.id)) {
                                    pos[c.id] = c.position
                                }
                                c.consume()
                            }
                            val now = pos.values.toList()
                            val dragId = dragging
                            if (now.size == 1 && before.size == 1 && dragId != null) {
                                val dx = (now[0].x - before[0].x) / w; val dy = (now[0].y - before[0].y) / h
                                update(dragId) { c -> c.copy(x = (c.x + dx).coerceIn(0.02f, 0.98f), y = (c.y + dy).coerceIn(0.02f, 0.98f)) }
                            } else if (now.size >= 2 && before.size >= 2 && selected != null) {
                                val d0 = hypot(before[0].x - before[1].x, before[0].y - before[1].y)
                                val d1 = hypot(now[0].x - now[1].x, now[0].y - now[1].y)
                                if (d0 > 20f) update(selected!!) { c -> c.copy(size = (c.size * d1 / d0).coerceIn(0.06f, 0.6f)) }
                            }
                            // Snap when the last finger lifts.
                            if (snap && pos.isEmpty() && selected != null) {
                                val step = 0.025f
                                update(selected!!) { c -> c.copy(x = (c.x / step).roundToInt() * step, y = (c.y / step).roundToInt() * step) }
                            }
                        }
                    }
                },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (snap) {
                        val gx = size.width * 0.05f
                        var x = gx
                        while (x < size.width) {
                            var y = size.height * 0.1f
                            while (y < size.height) { drawCircle(Pal.line, 1.2f * density, androidx.compose.ui.geometry.Offset(x, y)); y += size.height * 0.1f }
                            x += gx
                        }
                    }
                    for (c in controls) {
                        val light = if (settings.ringFx == RingFx.Off) Pal.line else accent
                        drawControl(c, size.width, size.height, PadVisual(), light, 0.9f, tm, accent, c.id == selected)
                    }
                }
            }
            if (s != null) {
                Panel(
                    s, Modifier.align(if (panelLeft) Alignment.CenterStart else Alignment.CenterEnd).padding(6.dp),
                    onChange = { f -> update(s.id, f) },
                    onDelete = { controls.removeAll { it.id == s.id }; selected = null },
                )
            } else {
                Panel2(
                    name = name, onName = { name = it }, layouts = all.map { it.name }, current = current,
                    onPick = { n -> stash(); all.firstOrNull { it.name == n }?.let { load(it) } },
                    onNew = {
                        stash()
                        var i = all.size + 1
                        while (all.any { it.name == "Layout $i" }) i++
                        val l = PadLayout("Layout $i", controls.toList())
                        all.add(l); load(l)
                    },
                    onDeleteLayout = {
                        if (all.size > 1) {
                            val i = all.indexOfFirst { it.name == current }
                            if (i >= 0) all.removeAt(i)
                            load(all.first())
                        }
                    },
                    onReset = { load(PadLayout.default().copy(name = name)) },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).background(if (on) accent else Pal.key)
            .pointerInput(text) { detectTapGestures { onClick() } }.padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Tx(text, Type.label.copy(color = if (on) Pal.bg else Pal.text)) }
}

@Composable
private fun Panel(s: PadControl, modifier: Modifier, onChange: ((PadControl) -> PadControl) -> Unit, onDelete: () -> Unit) {
    val digital = s.type == CtlType.Button || s.type == CtlType.Bumper
    val kind = when (s.bind) { is Bind.Key -> 1; is Bind.Mouse -> 2; else -> 0 }
    Column(
        modifier.width(250.dp).fillMaxSize().panel(Pal.raised, 14.dp).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Tx("SELECTED", Type.label)
        Tx(when (s.type) { CtlType.Stick -> "Stick"; CtlType.DPad -> "D-pad"; CtlType.Trigger -> "Trigger"; CtlType.Bumper -> "Bumper"; else -> "Button" } + if (s.label.isNotEmpty()) " " + s.label else "", Type.heading)
        if (digital) {
            Tx("SENDS", Type.label)
            Segmented(
                listOf(0 to "Pad", 1 to "Key", 2 to "Mouse"), kind,
                { k ->
                    onChange { c ->
                        when (k) {
                            1 -> c.copy(bind = Bind.Key(0x2C), label = "space")
                            2 -> c.copy(bind = Bind.Mouse(0), label = "M1")
                            else -> c.copy(bind = Bind.Pad(0), label = "A")
                        }
                    }
                },
            )
            when (val b = s.bind) {
                is Bind.Pad -> FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    for ((n, bit) in padNames) Chip(n, b.bit == bit) { onChange { it.copy(bind = Bind.Pad(bit), label = n) } }
                }
                is Bind.Mouse -> FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for ((n, bit) in mouseChoices) Chip(n, b.bit == bit) { onChange { it.copy(bind = Bind.Mouse(bit), label = "M" + (bit + 1)) } }
                }
                is Bind.Key -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for ((n, bit) in modChoices) Chip(n, (b.mods and bit) != 0) { onChange { it.copy(bind = b.copy(mods = b.mods xor bit)) } }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (k in keyChoices) {
                            val def = Keys.of(k)
                            if (def.kind == KeyKind.Layer) continue
                            Chip(k, b.usage == def.usage) { onChange { it.copy(bind = b.copy(usage = def.usage), label = k.take(5)) } }
                        }
                    }
                }
                else -> Unit
            }
        }
        Tx("SIZE", Type.label)
        WSlider(s.size, { v -> onChange { it.copy(size = v) } }, 0.06f..0.6f, Modifier.fillMaxWidth(), "Size")
        Tx("OPACITY", Type.label)
        WSlider(s.opacity, { v -> onChange { it.copy(opacity = v) } }, 0.2f..1f, Modifier.fillMaxWidth(), "Opacity")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Tx("Vibrate on press", Type.body)
            WSwitch(s.vibrate, { v -> onChange { it.copy(vibrate = v) } }, "Vibrate on press")
        }
        WButton("Delete", onDelete, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Panel2(
    name: String, onName: (String) -> Unit, layouts: List<String>, current: String,
    onPick: (String) -> Unit, onNew: () -> Unit, onDeleteLayout: () -> Unit, onReset: () -> Unit, modifier: Modifier,
) {
    Column(
        modifier.width(250.dp).panel(Pal.raised, 14.dp).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Tx("LAYOUT NAME", Type.label)
        BasicTextField(
            name, { onName(it.take(40)) }, singleLine = true,
            textStyle = Type.code, cursorBrush = SolidColor(LocalAccent.current),
            modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(Pal.key).border(1.dp, Pal.line, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        )
        Tx("LAYOUTS", Type.label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (l in layouts) Chip(l, l == current) { onPick(l) }
            Chip("+ New", false, onNew)
        }
        Tx("Tap a control to edit it.", Type.muted)
        WButton("Reset to default", onReset, Modifier.fillMaxWidth())
        if (layouts.size > 1) WButton("Delete layout", onDeleteLayout, Modifier.fillMaxWidth())
    }
}
