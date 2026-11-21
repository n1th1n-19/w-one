package app.wone.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.wone.BuildConfig
import app.wone.HidService
import app.wone.data.HapticLevel
import app.wone.data.Settings
import app.wone.data.SwitchType
import app.wone.data.ThemeMode
import app.wone.data.TrackpadMode
import app.wone.hid.HidState
import app.wone.wone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Tab(val label: String) { Home("Home"), Computers("Computers"), Settings("Settings") }

/** Portrait shell: large title, three bottom destinations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShell(start: Screen, nav: (Screen) -> Unit) {
    var tab by remember(start) { mutableStateOf(if (start == Screen.Settings) Tab.Settings else Tab.Home) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(if (tab == Tab.Home) "w-one" else tab.label) },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t },
                        icon = { Icon(when (t) { Tab.Home -> Icons.home; Tab.Computers -> Icons.devices; Tab.Settings -> Icons.sliders }, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.Home -> HomeTab(nav) { tab = Tab.Computers }
                Tab.Computers -> ComputersTab(nav)
                Tab.Settings -> SettingsTab(nav)
            }
        }
    }
}

@Composable
private fun permissionsToAsk(): Array<String> = remember {
    buildList {
        if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
}

@Composable
private fun HomeTab(nav: (Screen) -> Unit, openComputers: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.wone
    val state by app.engine.state.collectAsState()
    val perms = permissionsToAsk()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (app.engine.hasPermission()) HidService.start(ctx) else app.engine.start()
    }
    LaunchedEffect(Unit) { if (app.engine.hasPermission()) HidService.start(ctx) else app.engine.start() }
    val cs = MaterialTheme.colorScheme
    val connected = state is HidState.Connected
    val (title, body) = when (val s = state) {
        is HidState.Connected -> s.host.name to "Connected over Bluetooth. Pick a mode."
        is HidState.Connecting -> s.host.name to "Connecting. Waiting for the computer to accept."
        HidState.Ready -> "Not connected" to "Pick a computer, or pair a new one."
        HidState.NeedsPermission -> "Bluetooth permission needed" to "w-one needs it to act as a keyboard and mouse."
        HidState.BluetoothOff -> "Bluetooth is off" to "Turn Bluetooth on to connect."
        HidState.Unsupported -> "Not supported" to "This phone's Bluetooth has no HID Device profile. Some makers turn it off; Pixel phones support it."
        HidState.Registering -> "Starting" to "Registering with the Bluetooth stack."
        HidState.Idle -> "Stopped" to "Start w-one to connect."
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (connected) cs.primaryContainer else cs.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (connected) "Connected" else "Status", style = MaterialTheme.typography.labelLarge, color = if (connected) cs.onPrimaryContainer else cs.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.headlineSmall, color = if (connected) cs.onPrimaryContainer else cs.onSurface)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = if (connected) cs.onPrimaryContainer else cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                when (state) {
                    HidState.NeedsPermission -> Button({ launcher.launch(perms) }) { Text("Allow Bluetooth") }
                    HidState.BluetoothOff -> Button({ runCatching { ctx.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Turn on Bluetooth") }
                    is HidState.Connected -> FilledTonalButton({ app.engine.disconnect() }) { Text("Disconnect") }
                    HidState.Ready -> FilledTonalButton(openComputers) { Text("Choose a computer") }
                    HidState.Idle -> Button({ HidService.start(ctx) }) { Text("Start") }
                    HidState.Unsupported -> FilledTonalButton({ app.engine.stop(); HidService.start(ctx) }) { Text("Try again") }
                    else -> Unit
                }
            }
        }

        Text("Modes", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        val modes = Mode.entries
        for (row in modes.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                for (m in row) {
                    Card(
                        onClick = { nav(m.screen) },
                        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
                        modifier = Modifier.weight(1f).height(120.dp),
                    ) {
                        Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(m.icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(28.dp))
                            Column {
                                Text(m.title, style = MaterialTheme.typography.titleMedium)
                                Text(m.hint, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ComputersTab(nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.wone
    val state by app.engine.state.collectAsState()
    var computers by remember { mutableStateOf(app.engine.pairedComputers()) }
    // Bonds change outside the app (the computer pairs with us), so look again every couple of seconds.
    LaunchedEffect(Unit) { while (true) { computers = app.engine.pairedComputers(); delay(2000) } }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
            if (computers.isEmpty()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("No computers yet", style = MaterialTheme.typography.titleMedium)
                    Text("Pair one below. After that it appears here and reconnects on its own.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            computers.forEachIndexed { i, h ->
                val on = (state as? HidState.Connected)?.host?.address == h.address
                val connecting = (state as? HidState.Connecting)?.host?.address == h.address
                ListItem(
                    headlineContent = { Text(h.name) },
                    supportingContent = { Text(if (on) "Connected" else if (connecting) "Connecting" else "Tap to connect") },
                    leadingContent = { Icon(Icons.computer, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable(enabled = !on && !connecting) { app.engine.connect(h.address) },
                )
                if (i < computers.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp), color = cs.outlineVariant)
            }
        }
        Button({ nav(Screen.Pairing) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.plus, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Pair a new computer")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("Pair a computer") },
                navigationIcon = { IconButton({ nav(Screen.Home) }) { Icon(Icons.back, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val steps = listOf(
                "Make this phone visible" to "Tap the button below. The phone stays discoverable for two minutes.",
                "Open Bluetooth on the computer" to "Choose this phone in the list and confirm the code on both screens.",
                "Let it connect" to "After pairing the computer connects by itself. If it only pairs, pick the phone again and choose Connect.",
            )
            Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                steps.forEachIndexed { i, (t, b) ->
                    ListItem(
                        headlineContent = { Text(t) },
                        supportingContent = { Text(b) },
                        leadingContent = {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = cs.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
            Button({
                runCatching { ctx.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Make phone visible") }
            OutlinedButton({
                runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Open Bluetooth settings") }

            Text("Linux tips", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
            Text("Newer BlueZ versions refuse input devices that are not bonded. Pair first, trust it, then connect:", style = MaterialTheme.typography.bodyMedium)
            Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
                Tx("bluetoothctl\npair <phone address>\ntrust <phone address>\nconnect <phone address>", Type.code, modifier = Modifier.padding(16.dp))
            }
            Text("If the computer starts playing your phone's music: it also took the phone as an audio device. On the phone open Settings, Connected devices, the computer, and turn off Media audio and Phone calls. Keyboard, mouse and gamepad keep working.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Text("A computer remembers what a device can do when it pairs. After updating w-one, remove the phone on the computer and pair again. Typing assumes a US keyboard layout on the computer.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer), modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = hint?.let { { Text(it) } },
        trailingContent = { Switch(on, onChange) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable { onChange(!on) },
    )
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(shown, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value, onChange, valueRange = range)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (v, text) ->
                SegmentedButton(
                    selected = v == selected, onClick = { onSelect(v) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(text, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun SettingsTab(nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.wone
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    fun upd(f: (Settings) -> Settings) { scope.launch { app.settings.update(f) } }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Group("Appearance") {
            ChoiceRow("Theme", ThemeMode.entries.map { it to it.label }, s.theme) { v -> upd { it.copy(theme = v) } }
            if (Build.VERSION.SDK_INT >= 31) SwitchRow("Use wallpaper colours", s.dynamicColor, "Match the colours of your phone's theme") { v -> upd { it.copy(dynamicColor = v) } }
        }
        Group("Sound and touch") {
            SwitchRow("Click sounds", s.sounds) { v -> upd { it.copy(sounds = v) } }
            SliderRow("Volume", s.volume, 0f..1f, "${(s.volume * 100).toInt()} %") { v -> upd { it.copy(volume = v) } }
            ChoiceRow("Keyboard switch sound", SwitchType.entries.map { it to it.label }, s.switchType) { v -> upd { it.copy(switchType = v) }; app.sounds.switchType = v; app.sounds.keyDown(0x04, false) }
            ChoiceRow("Haptic feedback", HapticLevel.entries.map { it to it.label }, s.haptics) { v -> upd { it.copy(haptics = v) }; app.haptics.level = v; app.haptics.click() }
        }
        Group("Keyboard") {
            SwitchRow("Secure typing", s.secureTyping, "Hide the typed text preview") { v -> upd { it.copy(secureTyping = v) } }
        }
        Group("Trackpad") {
            ChoiceRow("Default mode", TrackpadMode.entries.map { it to it.label }, s.trackpadMode) { v -> upd { it.copy(trackpadMode = v) } }
            SliderRow("Pointer speed (simple mode)", s.trackpadSpeed, 0.5f..3f, "%.1f".format(s.trackpadSpeed)) { v -> upd { it.copy(trackpadSpeed = v) } }
            SwitchRow("Natural scrolling (simple mode)", s.naturalScroll) { v -> upd { it.copy(naturalScroll = v) } }
        }
        Group("Air mouse") {
            SliderRow("Sensitivity", s.airSensitivity, 0.3f..3f, "%.1f".format(s.airSensitivity)) { v -> upd { it.copy(airSensitivity = v) } }
            SwitchRow("Invert left and right", s.airInvertX) { v -> upd { it.copy(airInvertX = v) } }
            SwitchRow("Invert up and down", s.airInvertY) { v -> upd { it.copy(airInvertY = v) } }
        }
        Group("Connection") {
            SwitchRow("Reconnect to last computer", s.reconnectLast) { v -> upd { it.copy(reconnectLast = v) } }
            SwitchRow("Keep screen on in modes", s.keepScreenOn) { v -> upd { it.copy(keepScreenOn = v) } }
            TextButton({ ctx.startService(Intent(ctx, HidService::class.java).setAction(HidService.ACTION_STOP)) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Stop w-one") }
        }
        Group("About") {
            ListItem(
                headlineContent = { Text("w-one ${BuildConfig.VERSION_NAME}") },
                supportingContent = { Text("Free software, GPL-3.0-or-later. No network access, no tracking.") },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            ListItem(
                headlineContent = { Text("Credits") },
                supportingContent = { Text("Key sounds: CC0 recordings by handygaber, humi74 and UberBosser from Freesound, cut by the Mechanify project. Other sounds are synthesised for w-one. Icons: Material Symbols (Apache-2.0). Font: JetBrains Mono (SIL OFL 1.1).") },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Three swipe steps, each showing a real piece of the app rather than a decorative picture. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(cs.background).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (pager.currentPage < 2) TextButton(onDone) { Text("Skip") }
        }
        HorizontalPager(pager, modifier = Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                when (page) {
                    0 -> Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            for (m in Mode.entries) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(m.icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(28.dp))
                                Text(m.title.substringBefore(' '), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                    1 -> Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)) {
                        ListItem(
                            headlineContent = { Text("fedora-desktop") },
                            supportingContent = { Text("Tap to connect") },
                            leadingContent = { Icon(Icons.computer, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        )
                    }
                    else -> Card(colors = CardDefaults.cardColors(containerColor = cs.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text("Connected", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
                            Text("fedora-desktop", style = MaterialTheme.typography.headlineSmall, color = cs.onPrimaryContainer)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                val (t, b) = when (page) {
                    0 -> "Four inputs in one phone" to "A programmer's keyboard, a real trackpad, an air mouse and a custom gamepad. Switch with one tap."
                    1 -> "Pair once, then just open" to "Pick this phone in your computer's Bluetooth settings. After that w-one reconnects on its own."
                    else -> "Nothing to install" to "The computer sees an ordinary Bluetooth keyboard, mouse, touchpad and controller, so it works on any Linux distro. w-one never uses the internet."
                }
                Text(t, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(b, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i -> Box(Modifier.size(width = if (i == pager.currentPage) 20.dp else 8.dp, height = 8.dp).clip(CircleShape).background(if (i == pager.currentPage) cs.primary else cs.outlineVariant)) }
            }
            Button({ if (pager.currentPage == 2) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                Text(if (pager.currentPage == 2) "Get started" else "Next")
            }
        }
    }
}

/** Cold-start screen: a new mascot each launch, drawn from noise with a random seed and frequency. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val look = remember { MascotLook.random() }
    val scale = remember { androidx.compose.animation.core.Animatable(0.8f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 260f)) }
        delay(1300)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(cs.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MascotView(
                look, 168.dp, body = androidx.compose.ui.graphics.Color(0xFFE6C9A8), face = androidx.compose.ui.graphics.Color(0xFF2A1A0A),
                modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            )
            Text("w-one", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium), color = cs.onBackground)
        }
    }
}
