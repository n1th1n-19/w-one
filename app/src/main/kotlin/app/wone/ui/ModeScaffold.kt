package app.wone.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.wone.data.KeyboardFx
import app.wone.hid.HidState
import app.wone.wone
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** Short status for the chip: colour and text. */
@Composable
fun hostStatus(): Pair<Color, String> {
    val state by LocalContext.current.wone.engine.state.collectAsState()
    return when (val s = state) {
        is HidState.Connected -> Pal.ok to s.host.name
        is HidState.Connecting -> Pal.warn to "connecting " + s.host.name
        HidState.Ready -> Pal.warn to "not connected"
        HidState.Registering -> Pal.warn to "starting"
        HidState.BluetoothOff -> Pal.danger to "bluetooth off"
        HidState.Unsupported -> Pal.danger to "not supported"
        HidState.NeedsPermission -> Pal.danger to "no permission"
        HidState.Idle -> Pal.faint to "stopped"
    }
}

@Composable
fun HostChip(modifier: Modifier = Modifier) {
    val (color, text) = hostStatus()
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(Pal.surface).border(1.dp, Pal.line, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Tx(text, Type.label.copy(color = Pal.text))
    }
}

/**
 * Frame shared by every landscape mode: a Material 3 navigation rail on the left (every mode and
 * Home, one tap), a slim bar for the mode's own controls, then the mode itself.
 */
@Composable
fun ModeScaffold(
    mode: Mode?,
    nav: (Screen) -> Unit,
    bar: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize().background(Pal.bg).safeDrawingPadding()) {
        NavigationRail(
            containerColor = Pal.surface,
            modifier = Modifier.fillMaxHeight(),
            header = null,
        ) {
            Spacer(Modifier.weight(1f))
            for (m in Mode.entries) {
                NavigationRailItem(
                    selected = m == mode,
                    onClick = { nav(m.screen) },
                    icon = { Icon(m.icon, contentDescription = m.title) },
                    alwaysShowLabel = false,
                    label = { Text(m.title, maxLines = 1) },
                )
            }
            NavigationRailItem(
                selected = false,
                onClick = { nav(Screen.Home) },
                icon = { Icon(Icons.home, contentDescription = "Home") },
                alwaysShowLabel = false,
                label = { Text("Home") },
            )
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { bar() }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

/** Lighting picker for the keyboard: effects grouped by whether they react to typing. */
@Composable
fun LightingPanel(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalSettings.current
    val accent = LocalAccent.current
    val groups = listOf(
        "Always on" to listOf(KeyboardFx.Static, KeyboardFx.Breathing, KeyboardFx.Spectrum, KeyboardFx.Wave, KeyboardFx.Spiral, KeyboardFx.Ripple, KeyboardFx.Starlight, KeyboardFx.Rain),
        "Reacts to typing" to listOf(KeyboardFx.Reactive, KeyboardFx.Splash, KeyboardFx.Cross, KeyboardFx.Heatmap),
        "Off" to listOf(KeyboardFx.Off),
    )
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose() } }) {
        Column(
            Modifier
                .padding(start = 0.dp, top = 4.dp)
                .width(420.dp)
                .panel(Pal.raised, 14.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for ((title, list) in groups) {
                Tx(title.uppercase(), Type.label)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (fx in list) {
                        val on = fx == s.keyboardFx
                        Box(
                            Modifier.height(36.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (on) accent else Pal.key)
                                .pointerInput(fx) { detectTapGestures { scope.launch { ctx.wone.settings.update { it.copy(keyboardFx = fx) } } } }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Tx(fx.label, Type.label.copy(color = if (on) Pal.onAccent else Pal.text)) }
                    }
                }
            }
            Tx("SPEED", Type.label)
            // Slider runs fast to slow, so show it as "faster on the right".
            WSlider(11f - s.speed, { v -> scope.launch { ctx.wone.settings.update { it.copy(speed = 11f - v) } } }, 1f..10f, Modifier.fillMaxWidth(), "Lighting speed")
        }
    }
}
