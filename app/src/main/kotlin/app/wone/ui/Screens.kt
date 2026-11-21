package app.wone.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.wone.BuildConfig
import app.wone.HidService
import app.wone.data.AccentChoices
import app.wone.data.HapticLevel
import app.wone.data.RingFx
import app.wone.data.Settings
import app.wone.data.SwitchType
import app.wone.data.TrackpadMode
import app.wone.hid.HidState
import app.wone.wone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The keycap mark, drawn flat. */
@Composable
fun Mark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Canvas(modifier.size(size)) {
        val u = this.size.width / 54f
        drawRoundRect(Color(0xFF2B3240), Offset(0f, 0f), Size(54f * u, 54f * u), CornerRadius(9f * u))
        drawRoundRect(Color(0xFF394255), Offset(4f * u, 4f * u), Size(46f * u, 40f * u), CornerRadius(6f * u))
        val p = Path().apply {
            moveTo(11f * u, 16f * u); lineTo(17f * u, 35f * u); lineTo(27f * u, 21f * u); lineTo(37f * u, 35f * u); lineTo(43f * u, 16f * u)
        }
        drawPath(p, accent, style = Stroke(5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Pal.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Mark(88.dp)
            Tx("w-one", Type.title.copy(fontSize = androidx.compose.ui.unit.TextUnit(30f, androidx.compose.ui.unit.TextUnitType.Sp)))
            Tx("phone as keyboard, trackpad, mouse and gamepad", Type.label)
        }
    }
}

@Composable
fun IntroScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val pages = listOf(
        Triple("Four inputs in one phone", "Switch between a programmer's keyboard, a real trackpad, an air mouse and a custom gamepad at any time.", true),
        Triple("Pair once, then just open", "Open Bluetooth settings on your computer and pick this phone. After that w-one reconnects on its own.", false),
        Triple("Nothing to install", "Your computer sees an ordinary Bluetooth keyboard, mouse, touchpad and controller, so it works on any Linux distro. w-one never uses the internet.", false),
    )
    val (title, body, showModes) = pages[step]
    Column(Modifier.fillMaxSize().background(Pal.bg).safeDrawingPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Tx("${step + 1} / ${pages.size}", Type.label)
            if (step < pages.lastIndex) Tx("Skip", Type.heading.copy(color = Pal.muted), modifier = Modifier.clickable { onDone() }.padding(12.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (m in Mode.entries) {
                    Column(
                        Modifier.weight(1f).height(84.dp).panel(Pal.surface, 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Ico(m.icon, if (showModes) LocalAccent.current else Pal.muted, 26.dp)
                        Gap(6.dp)
                        Tx(m.title.split(' ').first().lowercase(), Type.label)
                    }
                }
            }
            Gap(32.dp)
            Tx(title, Type.title)
            Gap(12.dp)
            Tx(body, Type.muted.copy(fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)))
            if (step == 2) {
                Gap(16.dp)
                Tx("Fedora, Ubuntu, Debian, Arch, Mint, openSUSE and more", Type.label)
            }
        }
        WButton(if (step == pages.lastIndex) "Get started" else "Next", { if (step == pages.lastIndex) onDone() else step++ }, Modifier.fillMaxWidth(), primary = true)
    }
}

@Composable
fun HomeScreen(nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.wone
    val settings = LocalSettings.current
    val state by app.engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    val perms = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (app.engine.hasPermission()) HidService.start(ctx) else app.engine.start()
    }
    LaunchedEffect(Unit) {
        if (app.engine.hasPermission()) HidService.start(ctx) else app.engine.start()
    }
    val (dot, status) = hostStatus()
    var computers by remember { mutableStateOf(app.engine.pairedComputers()) }
    LaunchedEffect(state) { computers = app.engine.pairedComputers() }

    Column(Modifier.fillMaxSize().background(Pal.bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mark(34.dp)
            GapW(12.dp)
            Tx("w-one", Type.title, modifier = Modifier.weight(1f))
            IconBtn(Icons.sliders, "Settings", { nav(Screen.Settings) })
        }
        Gap(20.dp)

        // Connection card
        val ring = rememberClock(settings.ringFx == RingFx.Wave || settings.ringFx == RingFx.Breathe)
        Column(
            Modifier.fillMaxWidth().ringLight(settings.ringFx, settings.speed, settings.brightness, LocalAccent.current, ring, corner = 16.dp, stroke = 2.dp, inset = 1.dp)
                .panel(Pal.surface, 16.dp, Color.Transparent).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                Tx(if (state is HidState.Connected) "Connected" else "Not connected", Type.label)
            }
            Tx(status, Type.title)
            Tx(
                when (state) {
                    is HidState.Connected -> "Bluetooth Classic HID. Pick a mode below."
                    HidState.Ready -> "Pick a computer below, or pair a new one."
                    HidState.NeedsPermission -> "w-one needs the Bluetooth permission to act as a keyboard and mouse."
                    HidState.BluetoothOff -> "Turn Bluetooth on to connect."
                    HidState.Unsupported -> "This phone's Bluetooth does not offer the HID Device profile. Some makers turn it off. Pixel and most Samsung phones support it."
                    HidState.Registering -> "Registering with the Bluetooth stack."
                    is HidState.Connecting -> "Waiting for the computer to accept."
                    HidState.Idle -> "Stopped."
                },
                Type.muted,
            )
            when (state) {
                HidState.NeedsPermission -> WButton("Allow Bluetooth", { launcher.launch(perms) }, Modifier.fillMaxWidth(), primary = true, icon = Icons.bluetooth)
                HidState.BluetoothOff -> WButton("Turn on Bluetooth", {
                    runCatching { ctx.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, Modifier.fillMaxWidth(), primary = true, icon = Icons.bluetooth)
                is HidState.Connected -> WButton("Disconnect", { app.engine.disconnect() }, Modifier.fillMaxWidth())
                HidState.Ready -> WButton("Pair a new computer", { nav(Screen.Pairing) }, Modifier.fillMaxWidth(), icon = Icons.plus)
                HidState.Idle -> WButton("Start", { HidService.start(ctx) }, Modifier.fillMaxWidth(), primary = true)
                else -> Unit
            }
        }

        SectionLabel("Modes")
        Column(Modifier.fillMaxWidth().panel(Pal.surface, 16.dp)) {
            Mode.entries.forEachIndexed { i, m ->
                Row(
                    Modifier.fillMaxWidth().height(64.dp).clickable { nav(m.screen) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Ico(m.icon, LocalAccent.current, 24.dp)
                    Column(Modifier.weight(1f)) {
                        Tx(m.title, Type.heading)
                        Tx(m.hint, Type.label)
                    }
                    Ico(Icons.chevron, Pal.faint, 18.dp, Modifier.padding(0.dp))
                }
                if (i < Mode.entries.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 54.dp).height(1.dp).background(Pal.line))
            }
        }

        SectionLabel("Paired computers")
        Column(Modifier.fillMaxWidth().panel(Pal.surface, 16.dp)) {
            if (computers.isEmpty()) {
                Tx("None yet. Use \"Pair a new computer\" above.", Type.muted, modifier = Modifier.padding(16.dp))
            }
            computers.forEachIndexed { i, h ->
                val on = (state as? HidState.Connected)?.host?.address == h.address
                Row(
                    Modifier.fillMaxWidth().height(60.dp).clickable(enabled = !on) { app.engine.connect(h.address) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Pal.ok else Pal.faint))
                    Column(Modifier.weight(1f)) {
                        Tx(h.name, Type.heading)
                        Tx(if (on) "connected" else "tap to connect", Type.label)
                    }
                }
                if (i < computers.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 36.dp).height(1.dp).background(Pal.line))
            }
        }
        Gap(24.dp)
    }
}

@Composable
fun PairingScreen(nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(Pal.bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.back, "Back", { nav(Screen.Home) })
            GapW(12.dp)
            Tx("Pair a computer", Type.title)
        }
        Gap(20.dp)
        val steps = listOf(
            "Make this phone visible" to "Tap the button below. The phone stays discoverable for two minutes.",
            "Open Bluetooth on the computer" to "Choose \"w-one\" or this phone's name in the device list and confirm the code on both screens.",
            "Let it connect" to "After pairing the computer connects by itself. If it only pairs, pick the phone again and choose Connect.",
        )
        steps.forEachIndexed { i, (t, b) ->
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Pal.raised), contentAlignment = Alignment.Center) { Tx("${i + 1}", Type.code) }
                Column { Tx(t, Type.heading); Tx(b, Type.muted) }
            }
        }
        WButton("Make phone visible", {
            runCatching {
                ctx.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, Modifier.fillMaxWidth(), primary = true, icon = Icons.bluetooth)
        Gap(10.dp)
        WButton("Open Bluetooth settings", {
            runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, Modifier.fillMaxWidth())
        SectionLabel("Linux tips")
        Tx(
            "Newer BlueZ versions refuse input devices that are not bonded. Pair first, trust it, then connect:",
            Type.muted,
        )
        Gap(8.dp)
        Tx("bluetoothctl\npair <phone address>\ntrust <phone address>\nconnect <phone address>", Type.code, modifier = Modifier.fillMaxWidth().panel(Pal.surface, 12.dp).padding(14.dp))
        Gap(10.dp)
        Tx("If a computer paired before an update of w-one, remove the phone there and pair again: computers cache what a device can do.", Type.muted)
        Gap(10.dp)
        Tx("Typing assumes a US keyboard layout on the computer.", Type.muted)
    }
}

@Composable
fun SettingsScreen(nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.wone
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    fun upd(f: (Settings) -> Settings) { scope.launch { app.settings.update(f) } }

    Column(Modifier.fillMaxSize().background(Pal.bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.back, "Back", { nav(Screen.Home) })
            GapW(12.dp)
            Tx("Settings", Type.title)
        }

        SectionLabel("Lighting")
        Segmented(RingFx.entries.map { it to it.label }, s.ringFx, { v -> upd { it.copy(ringFx = v) } }, Modifier.fillMaxWidth())
        Gap(10.dp)
        Row { Tx("Speed", Type.body, modifier = Modifier.weight(1f)); Tx("${"%.1f".format(s.speed)} s", Type.label) }
        WSlider(11f - s.speed, { v -> upd { it.copy(speed = 11f - v) } }, 1f..10f, Modifier.fillMaxWidth(), "Lighting speed")
        Row { Tx("Brightness", Type.body, modifier = Modifier.weight(1f)); Tx("${(s.brightness * 100).toInt()} %", Type.label) }
        WSlider(s.brightness, { v -> upd { it.copy(brightness = v) } }, 0.1f..1f, Modifier.fillMaxWidth(), "Lighting brightness")
        Tx("Accent colour", Type.body)
        Gap(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentChoices.forEachIndexed { i, c ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                        .let { if (i == s.accentIndex) it.then(Modifier.padding(0.dp)) else it }
                        .pointerInput(i) { detectTapGestures { upd { it.copy(accentIndex = i) } } },
                    contentAlignment = Alignment.Center,
                ) { if (i == s.accentIndex) Ico(Icons.check, Pal.bg, 22.dp) }
            }
        }
        Gap(4.dp)
        Tx("Keyboard lighting effects are picked on the keyboard screen.", Type.muted)

        SectionLabel("Sound and touch")
        SwitchRow("Click sounds", s.sounds) { v -> upd { it.copy(sounds = v) } }
        Row { Tx("Volume", Type.body, modifier = Modifier.weight(1f)); Tx("${(s.volume * 100).toInt()} %", Type.label) }
        WSlider(s.volume, { v -> upd { it.copy(volume = v) } }, 0f..1f, Modifier.fillMaxWidth(), "Volume")
        Tx("Keyboard switch sound", Type.body)
        Gap(8.dp)
        Segmented(SwitchType.entries.map { it to it.label }, s.switchType, { v -> upd { it.copy(switchType = v) }; app.sounds.switchType = v; app.sounds.keyDown(0x04, false) }, Modifier.fillMaxWidth())
        Tx(s.switchType.feel, Type.label, modifier = Modifier.padding(top = 6.dp))
        Gap(12.dp)
        Tx("Haptic feedback", Type.body)
        Gap(8.dp)
        Segmented(HapticLevel.entries.map { it to it.label }, s.haptics, { v -> upd { it.copy(haptics = v) }; app.haptics.level = v; app.haptics.click() }, Modifier.fillMaxWidth())

        SectionLabel("Keyboard")
        SwitchRow("Secure typing (hide preview text)", s.secureTyping) { v -> upd { it.copy(secureTyping = v) } }

        SectionLabel("Trackpad")
        Tx("Default mode", Type.body)
        Gap(8.dp)
        Segmented(TrackpadMode.entries.map { it to it.label }, s.trackpadMode, { v -> upd { it.copy(trackpadMode = v) } }, Modifier.fillMaxWidth())
        Gap(8.dp)
        Row { Tx("Pointer speed (simple mode)", Type.body, modifier = Modifier.weight(1f)); Tx("%.1f".format(s.trackpadSpeed), Type.label) }
        WSlider(s.trackpadSpeed, { v -> upd { it.copy(trackpadSpeed = v) } }, 0.5f..3f, Modifier.fillMaxWidth(), "Pointer speed")
        SwitchRow("Natural scrolling (simple mode)", s.naturalScroll) { v -> upd { it.copy(naturalScroll = v) } }

        SectionLabel("Air mouse")
        Row { Tx("Sensitivity", Type.body, modifier = Modifier.weight(1f)); Tx("%.1f".format(s.airSensitivity), Type.label) }
        WSlider(s.airSensitivity, { v -> upd { it.copy(airSensitivity = v) } }, 0.3f..3f, Modifier.fillMaxWidth(), "Air mouse sensitivity")
        SwitchRow("Invert left / right", s.airInvertX) { v -> upd { it.copy(airInvertX = v) } }
        SwitchRow("Invert up / down", s.airInvertY) { v -> upd { it.copy(airInvertY = v) } }

        SectionLabel("Connection")
        SwitchRow("Reconnect to last computer", s.reconnectLast) { v -> upd { it.copy(reconnectLast = v) } }
        SwitchRow("Keep screen on in modes", s.keepScreenOn) { v -> upd { it.copy(keepScreenOn = v) } }
        Gap(10.dp)
        WButton("Stop w-one", {
            ctx.startService(Intent(ctx, HidService::class.java).setAction(HidService.ACTION_STOP))
        }, Modifier.fillMaxWidth())

        SectionLabel("About")
        Tx("w-one ${BuildConfig.VERSION_NAME}", Type.code)
        Gap(4.dp)
        Tx("Free software under the GPL-3.0-or-later. No network access, no tracking.", Type.muted)
        Gap(8.dp)
        Tx(
            "Keyboard sounds: CC0 recordings by handygaber, humi74 and UberBosser from Freesound, cut by the Mechanify project. Other sounds are synthesised for w-one. Font: JetBrains Mono, SIL OFL 1.1.",
            Type.muted,
        )
        Gap(24.dp)
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Tx(label, Type.body, modifier = Modifier.weight(1f))
        WSwitch(on, onChange, label)
    }
}
