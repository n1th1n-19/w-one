package app.wone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.wone.hid.HidState
import app.wone.hid.Host
import app.wone.wone
import kotlinx.coroutines.delay

private const val MAX_SLOTS = 5

/**
 * Paired computers as numbered slots 1 to 5. Order is remembered, new pairings fill the next free
 * slot, and one tap switches: the current computer is closed first, then the new one is opened.
 * Android keeps one active computer at a time, so this is switching, not two at once.
 */
@Composable
fun HostList(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = ctx.wone
    val settings = LocalSettings.current
    val state by app.engine.state.collectAsState()
    val error by app.engine.error.collectAsState()
    var paired by remember { mutableStateOf(app.engine.pairedComputers()) }
    // Pairing happens outside the app, so look again every couple of seconds.
    LaunchedEffect(Unit) { while (true) { paired = app.engine.pairedComputers(); delay(2000) } }

    val known = settings.slots.split(',').filter { it.isNotEmpty() }
    val byAddr = paired.associateBy { it.address }
    val ordered: List<Host> = (known.mapNotNull { byAddr[it] } + paired.filter { it.address !in known }).take(MAX_SLOTS)
    val csv = ordered.joinToString(",") { it.address }
    LaunchedEffect(csv) { if (csv != settings.slots) app.settings.update { it.copy(slots = csv) } }

    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        error?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp)) }
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
            if (ordered.isEmpty()) {
                Column(Modifier.padding(20.dp)) {
                    Text("No computers yet", style = MaterialTheme.typography.titleMedium)
                    Text("Pair one below. It appears here and reconnects on its own.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            ordered.forEachIndexed { i, h ->
                val on = (state as? HidState.Connected)?.host?.address == h.address
                val connecting = (state as? HidState.Connecting)?.host?.address == h.address
                ListItem(
                    headlineContent = { Text(h.name) },
                    supportingContent = { Text(if (on) "Connected" else if (connecting) "Connecting" else "Tap to switch") },
                    leadingContent = {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (on) cs.primary else cs.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", color = if (on) cs.onPrimary else cs.onSecondaryContainer, style = MaterialTheme.typography.labelLarge) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(enabled = !on && !connecting) { app.engine.switchTo(h.address) },
                )
                if (i < ordered.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp), color = cs.outlineVariant)
            }
        }
    }
}
