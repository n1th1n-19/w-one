package app.wone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.wone.data.TrackpadMode
import app.wone.hid.HidReports
import app.wone.input.ContactTracker
import app.wone.input.PadMapper
import app.wone.input.SimpleTrackpad
import app.wone.wone
import kotlinx.coroutines.launch

@Composable
fun TrackpadScreen(nav: (Screen) -> Unit) {
    val app = LocalContext.current.wone
    val settings = LocalSettings.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val native = settings.trackpadMode == TrackpadMode.Native

    val tracker = remember { ContactTracker() }
    val simple = remember { SimpleTrackpad { b, dx, dy, w, p -> app.engine.mouse(b, dx, dy, w, p) } }
    simple.speed = settings.trackpadSpeed
    simple.natural = settings.naturalScroll
    var leftDown by remember { mutableStateOf(false) }
    var padW by remember { mutableFloatStateOf(1f) }
    var padH by remember { mutableFloatStateOf(1f) }
    val fingers = remember { mutableStateMapOf<Long, Offset>() }
    // Native mode sends where each finger is, so sensitivity scales how far a finger's movement counts.
    val virt = remember { HashMap<Long, Offset>() }
    val lastPos = remember { HashMap<Long, Offset>() }
    val gain = rememberUpdatedState(settings.trackpadGain)

    fun sendNative(newDown: Boolean) {
        val frame = tracker.frame()
        if (frame.isEmpty()) return
        app.engine.touchpad(HidReports.touchpad(frame, leftDown), mustDeliver = newDown || frame.any { !it.tip })
    }

    fun releaseAll() {
        tracker.clear(); simple.reset(); fingers.clear(); leftDown = false
        app.engine.touchpad(HidReports.touchpad(emptyList(), false), mustDeliver = true)
        app.engine.mouse(0, 0, 0)
    }
    DisposableEffect(native) { onDispose { releaseAll() } }

    ModeScaffold(
        mode = Mode.Trackpad, nav = nav,
        bar = {
            Segmented(
                listOf(TrackpadMode.Native to "Native", TrackpadMode.Simple to "Simple"),
                settings.trackpadMode,
                { m -> scope.launch { app.settings.update { it.copy(trackpadMode = m) } } },
                Modifier.width(200.dp),
            )
            Tx("Speed", Type.label)
            WSlider(
                if (native) settings.trackpadGain else settings.trackpadSpeed,
                { v -> scope.launch { app.settings.update { if (native) it.copy(trackpadGain = v) else it.copy(trackpadSpeed = v) } } },
                if (native) 0.4f..3f else 0.5f..3f, Modifier.weight(1f).padding(horizontal = 4.dp), "Trackpad sensitivity",
            )
            HostChip()
        },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().panel(Pal.surface, 16.dp)
                    .onSizeChanged { padW = it.width.toFloat(); padH = it.height.toFloat() }
                    .pointerInput(native) {
                        val map = { PadMapper(padW) }
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                var newDown = false
                                var any = false
                                for (c in ev.changes) {
                                    val id = c.id.value
                                    val m = map()
                                    if (c.changedToDown()) {
                                        any = true
                                        virt[id] = c.position; lastPos[id] = c.position
                                        if (native) { if (tracker.down(id, m.x(c.position.x), m.y(c.position.y))) newDown = true }
                                        else simple.down(id, c.position.x, c.position.y, c.uptimeMillis)
                                        fingers[id] = c.position
                                        if (fingers.size == 1) app.haptics.tap()
                                    } else if (c.changedToUp()) {
                                        any = true
                                        if (native) tracker.up(id) else simple.up(id, c.uptimeMillis)
                                        virt.remove(id); lastPos.remove(id)
                                        fingers.remove(id)
                                    } else if (c.positionChanged()) {
                                        any = true
                                        if (native) {
                                            val v0 = virt[id] ?: c.position
                                            val d = c.position - (lastPos[id] ?: c.position)
                                            val v1 = Offset(
                                                (v0.x + d.x * gain.value).coerceIn(0f, padW),
                                                (v0.y + d.y * gain.value).coerceIn(0f, padH),
                                            )
                                            virt[id] = v1
                                            tracker.move(id, m.x(v1.x), m.y(v1.y))
                                        } else simple.move(id, c.position.x, c.position.y, c.uptimeMillis)
                                        lastPos[id] = c.position
                                        fingers[id] = c.position
                                    }
                                    c.consume()
                                }
                                if (any && native) sendNative(newDown)
                            }
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    for ((_, p) in fingers) {
                        drawCircle(accent.copy(alpha = 0.18f), 38.dp.toPx(), p)
                        drawCircle(accent, 38.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                    }
                }
                if (fingers.isEmpty()) {
                    Tx("touch here", Type.label, Pal.faint, Modifier.align(Alignment.Center))
                }
            }
            Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WButton(
                    "Left click", {}, Modifier.weight(2f).height(56.dp),
                    onDown = {
                        app.sounds.thunkDown(); app.haptics.click(); leftDown = true
                        if (native) { val s = tracker.snapshot(); app.engine.touchpad(HidReports.touchpad(s, true), true) } else simple.setButton(1, true)
                    },
                    onUp = {
                        app.sounds.thunkUp(); leftDown = false
                        if (native) { val s = tracker.snapshot(); app.engine.touchpad(HidReports.touchpad(s, false), true) } else simple.setButton(1, false)
                    },
                )
                WButton(
                    "Right click", {}, Modifier.weight(1f).height(56.dp),
                    onDown = { app.sounds.thunkDown(); app.haptics.click(); if (native) app.engine.mouse(2, 0, 0) else simple.setButton(2, true) },
                    onUp = { app.sounds.thunkUp(); if (native) app.engine.mouse(0, 0, 0) else simple.setButton(2, false) },
                )
            }
        }
    }
}
