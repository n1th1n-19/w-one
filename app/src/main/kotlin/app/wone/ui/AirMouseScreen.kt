package app.wone.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.wone.input.AirPointer
import app.wone.wone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AirMouseScreen(nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.wone
    val settings = LocalSettings.current
    val accent = LocalAccent.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var moving by remember { mutableStateOf(false) }
    var buttons by remember { mutableStateOf(0) }
    val pointer = remember { AirPointer { dx, dy -> app.engine.mouse(buttons, dx, dy) } }
    pointer.sensitivity = settings.airSensitivity
    pointer.invertX = settings.airInvertX
    pointer.invertY = settings.airInvertY

    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rotation = remember { sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) }
    val gyro = remember { sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val listener = remember {
        object : SensorEventListener {
            var last = 0L
            val q = FloatArray(4)
            override fun onSensorChanged(e: SensorEvent) {
                val dt = if (last == 0L) 0.01f else ((e.timestamp - last) / 1e9f).coerceIn(0.001f, 0.1f)
                last = e.timestamp
                if (e.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                    SensorManager.getQuaternionFromVector(q, e.values)
                    pointer.onQuaternion(q[0], q[1], q[2], q[3], dt)
                } else if (e.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    pointer.onGyro(e.values[0], e.values[1], e.values[2], dt)
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
    }
    val sensor = rotation ?: gyro

    fun startMoving() {
        if (sensor == null) return
        pointer.displayRotation = view.display?.rotation ?: 1
        pointer.reset(); listener.last = 0L
        sm.registerListener(listener, sensor, 8000, 0)
        moving = true
    }
    fun stopMoving() { sm.unregisterListener(listener); moving = false }
    DisposableEffect(Unit) {
        onDispose { sm.unregisterListener(listener); app.engine.mouse(0, 0, 0) }
    }

    var scrollJob by remember { mutableStateOf<Job?>(null) }
    fun scrollStart(dir: Int) {
        scrollJob?.cancel()
        scrollJob = scope.launch {
            while (true) { app.engine.mouse(buttons, 0, 0, dir); app.sounds.ratchet(); app.haptics.tap(); delay(110) }
        }
    }
    fun scrollStop() { scrollJob?.cancel(); scrollJob = null }

    ModeScaffold(
        mode = Mode.Air, nav = nav,
        bar = {
            Tx("Sensitivity", Type.label)
            WSlider(settings.airSensitivity, { v -> scope.launch { app.settings.update { it.copy(airSensitivity = v) } } }, 0.3f..3f, Modifier.width(200.dp), "Air mouse sensitivity")
            Tx(
                if (sensor == null) "No motion sensor on this phone" else "Hold with your thumb, then tilt or turn the phone",
                Type.label, if (sensor == null) Pal.danger else Pal.faint, Modifier.weight(1f).padding(start = 4.dp),
            )
            HostChip()
        },
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Clutch: movement only while held.
            val s by animateFloatAsState(if (moving) 0.97f else 1f, pressSpring(), label = "clutch")
            Box(
                Modifier.weight(1.1f).fillMaxHeight().scale(s)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (moving) accent else Pal.surface)
                    .border(1.dp, if (moving) accent else Pal.line, RoundedCornerShape(20.dp))
                    .pressable(
                        onDown = { app.sounds.padDown(); app.haptics.click(); startMoving() },
                        onUp = { app.sounds.padUp(); stopMoving() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tx(if (moving) "moving" else "hold to move", Type.title, if (moving) Pal.bg else Pal.text)
                    Tx("let go to reposition your hand", Type.label, if (moving) Pal.bg else Pal.faint)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WButton(
                    "Left click", {}, Modifier.fillMaxWidth().weight(2f),
                    onDown = { app.sounds.mouseDown(); app.haptics.click(); buttons = buttons or 1; app.engine.mouse(buttons, 0, 0) },
                    onUp = { app.sounds.mouseUp(); buttons = buttons and 1.inv(); app.engine.mouse(buttons, 0, 0) },
                )
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WButton(
                        "Right click", {}, Modifier.weight(1.6f).fillMaxHeight(),
                        onDown = { app.sounds.mouseDown(); app.haptics.click(); buttons = buttons or 2; app.engine.mouse(buttons, 0, 0) },
                        onUp = { app.sounds.mouseUp(); buttons = buttons and 2.inv(); app.engine.mouse(buttons, 0, 0) },
                    )
                    WButton("", {}, Modifier.weight(0.7f).fillMaxHeight(), icon = Icons.up, onDown = { scrollStart(1) }, onUp = { scrollStop() })
                    WButton("", {}, Modifier.weight(0.7f).fillMaxHeight(), icon = Icons.down, onDown = { scrollStart(-1) }, onUp = { scrollStop() })
                }
            }
        }
    }
}
