package app.wone.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HidState {
    /** Not started yet. */
    data object Idle : HidState
    data object NeedsPermission : HidState
    data object BluetoothOff : HidState
    /** The phone's Bluetooth stack has no HID Device profile or refused to register. */
    data object Unsupported : HidState
    data object Registering : HidState
    /** Registered and discoverable; no computer connected. */
    data object Ready : HidState
    data class Connecting(val host: Host) : HidState
    data class Connected(val host: Host) : HidState
}

data class Host(val address: String, val name: String)

/**
 * Owns the Bluetooth HID Device registration and sends reports to the connected computer.
 * Process-wide singleton: the foreground service keeps the process alive, the UI only reads state.
 */
@SuppressLint("MissingPermission") // every entry point checks [hasPermission] first
class HidEngine(private val context: Context, private val touchpadProfile: () -> Boolean = { false }) {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "wone-hid-send").apply { isDaemon = true } }

    private val _state = MutableStateFlow<HidState>(HidState.Idle)
    val state: StateFlow<HidState> = _state.asStateFlow()

    /** Lock LEDs the host reports back: bit 0 Num, bit 1 Caps, bit 2 Scroll. */
    private val _leds = MutableStateFlow(0)
    val leds: StateFlow<Int> = _leds.asStateFlow()

    /** Why the last connection attempt failed, in words the user can act on. Null when fine. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var pendingSwitch: String? = null
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var registered = false

    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Gets the HID profile proxy and registers the combined descriptor. Safe to call again. */
    fun start() {
        if (hid != null) return
        watchBluetooth()
        if (!hasPermission()) { _state.value = HidState.NeedsPermission; return }
        val a = adapter
        if (a == null) { _state.value = HidState.Unsupported; return }
        if (!a.isEnabled) { _state.value = HidState.BluetoothOff; return }
        _state.value = HidState.Registering
        val ok = a.getProfileProxy(context, proxyListener, BluetoothProfile.HID_DEVICE)
        if (!ok) { _state.value = HidState.Unsupported; return }
        // Some phones never answer when the profile is disabled in firmware.
        main.postDelayed({ if (_state.value == HidState.Registering) _state.value = HidState.Unsupported }, 6000)
    }

    private var watching = false
    private val btReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: android.content.Intent) {
            when (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF -> {
                    hid = null; host = null; registered = false; _leds.value = 0
                    if (_state.value != HidState.Idle) _state.value = HidState.BluetoothOff
                }
                BluetoothAdapter.STATE_ON -> if (_state.value == HidState.BluetoothOff) start()
            }
        }
    }

    /** Follows Bluetooth being switched off and on so the registration comes back by itself. */
    private fun watchBluetooth() {
        if (watching) return
        watching = true
        androidx.core.content.ContextCompat.registerReceiver(context, btReceiver, android.content.IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun stop() {
        if (watching) { watching = false; runCatching { context.unregisterReceiver(btReceiver) } }
        val h = hid
        if (h != null) {
            try { if (registered) h.unregisterApp() } catch (_: SecurityException) {}
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h)
        }
        hid = null; host = null; registered = false
        _leds.value = 0
        _state.value = HidState.Idle
    }

    /** Computers the user has already paired with (computer or unclassified devices). */
    fun pairedComputers(): List<Host> {
        if (!hasPermission()) return emptyList()
        val set = adapter?.bondedDevices ?: return emptyList()
        return set.filter {
            val major = it.bluetoothClass?.majorDeviceClass
            major == null || major == BluetoothClass.Device.Major.COMPUTER || major == BluetoothClass.Device.Major.UNCATEGORIZED
        }.map { Host(it.address, it.name ?: it.address) }.sortedBy { it.name.lowercase() }
    }

    /**
     * Connects to a paired computer. One attempt at a time: if a connection is already starting
     * or open to this computer nothing is done, and a computer that never answers is given up on
     * after a few seconds with a reason.
     */
    fun connect(address: String): Boolean {
        val h = hid ?: return false
        if (!registered || !hasPermission()) return false
        val cur = state.value
        if (cur is HidState.Connected && cur.host.address == address) return true
        if (cur is HidState.Connecting && cur.host.address == address) return true
        val device = adapter?.bondedDevices?.firstOrNull { it.address == address } ?: return false
        _error.value = null
        val ok = h.connect(device)
        if (ok) {
            _state.value = HidState.Connecting(Host(device.address, device.name ?: device.address))
            main.postDelayed({
                val s = _state.value
                if (s is HidState.Connecting && s.host.address == address) {
                    runCatching { h.disconnect(device) }
                    _error.value = "${s.host.name} did not answer. If it paired before an update, remove the phone on the computer and pair again."
                    _state.value = HidState.Ready
                }
            }, 12000)
        } else _error.value = "Could not start the connection. Turn Bluetooth off and on and try again."
        return ok
    }

    /** Moves to another computer: closes the current connection first, then opens the new one. */
    fun switchTo(address: String) {
        val cur = state.value
        if (cur is HidState.Connected && cur.host.address != address) {
            pendingSwitch = address
            disconnect()
            main.postDelayed({ pendingSwitch?.let { a -> pendingSwitch = null; connect(a) } }, 3000)
        } else connect(address)
    }

    fun disconnect() {
        val h = hid ?: return
        val d = host ?: return
        if (hasPermission()) h.disconnect(d)
    }

    fun connectedHost(): Host? = (state.value as? HidState.Connected)?.host

    // ---- sending -------------------------------------------------------------------------

    private val lock = Any()
    private var mouseScheduled = false
    private var latestPad: ByteArray? = null
    private var latestTouch: ByteArray? = null
    private var padScheduled = false
    private var touchScheduled = false

    private fun send(id: Int, data: ByteArray) {
        val h = hid ?: return
        val d = host ?: return
        if (state.value !is HidState.Connected) return
        try { h.sendReport(d, id, data) } catch (_: SecurityException) {}
    }

    /** Keyboard reports are ordered and never dropped. */
    fun keyboard(modifiers: Int, keys: IntArray = IntArray(0)) {
        val data = HidReports.keyboard(modifiers, keys)
        worker.execute { send(ReportId.KEYBOARD, data) }
    }

    private class MouseMsg(var buttons: Int, var dx: Int, var dy: Int, var wheel: Int, var pan: Int)
    private val mouseQueue = java.util.ArrayDeque<MouseMsg>()

    /**
     * Button changes keep their order; consecutive messages with the same buttons are merged,
     * so a burst of motion is summed instead of queued report by report.
     */
    fun mouse(buttons: Int, dx: Int, dy: Int, wheel: Int = 0, pan: Int = 0) {
        synchronized(lock) {
            val last = mouseQueue.peekLast()
            if (last != null && last.buttons == buttons) {
                last.dx += dx; last.dy += dy; last.wheel += wheel; last.pan += pan
            } else {
                mouseQueue.addLast(MouseMsg(buttons, dx, dy, wheel, pan))
            }
            if (mouseScheduled) return
            mouseScheduled = true
        }
        worker.execute(::drainMouse)
    }

    private fun drainMouse() {
        while (true) {
            val m: MouseMsg
            val dx: Int; val dy: Int; val w: Int; val p: Int
            synchronized(lock) {
                val head = mouseQueue.peekFirst()
                if (head == null) { mouseScheduled = false; return }
                m = head
                dx = m.dx.coerceIn(-127, 127); dy = m.dy.coerceIn(-127, 127)
                w = m.wheel.coerceIn(-127, 127); p = m.pan.coerceIn(-127, 127)
                m.dx -= dx; m.dy -= dy; m.wheel -= w; m.pan -= p
                if (m.dx == 0 && m.dy == 0 && m.wheel == 0 && m.pan == 0) mouseQueue.pollFirst()
            }
            send(ReportId.MOUSE, HidReports.mouse(m.buttons, dx, dy, w, p))
        }
    }

    /** Only the newest gamepad state matters; older ones are replaced while the sender is busy. */
    fun gamepad(data: ByteArray) {
        synchronized(lock) { latestPad = data; if (padScheduled) return; padScheduled = true }
        worker.execute {
            while (true) {
                val d: ByteArray
                synchronized(lock) { val x = latestPad; latestPad = null; if (x == null) { padScheduled = false; return@execute }; d = x }
                send(ReportId.GAMEPAD, d)
            }
        }
    }

    /** Touch frames are replaced the same way, except frames that end a contact must not be lost. */
    fun touchpad(data: ByteArray, mustDeliver: Boolean) {
        if (mustDeliver) { worker.execute { send(ReportId.TOUCHPAD, data) }; return }
        synchronized(lock) { latestTouch = data; if (touchScheduled) return; touchScheduled = true }
        worker.execute {
            while (true) {
                val d: ByteArray
                synchronized(lock) { val x = latestTouch; latestTouch = null; if (x == null) { touchScheduled = false; return@execute }; d = x }
                send(ReportId.TOUCHPAD, d)
            }
        }
    }

    /** Releases every key, button and finger. Call when leaving a mode or on disconnect. */
    fun releaseAll() {
        keyboard(0)
        mouse(0, 0, 0)
        gamepad(HidReports.gamepad(0, 0, 0, 0, 0, 0, 0, 0))
        touchpad(HidReports.touchpad(emptyList(), false), mustDeliver = true)
    }

    // ---- Bluetooth callbacks -------------------------------------------------------------

    private val proxyListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val h = proxy as BluetoothHidDevice
            hid = h
            val sdp = BluetoothHidDeviceAppSdpSettings(
                // Kept short: the whole SDP record has a small fixed size on Android.
                "w-one",
                "w-one",
                "w-one",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidDescriptor.forProfile(touchpadProfile()),
            )
            val ok = try {
                h.registerApp(sdp, null, null, { r -> main.post(r) }, callback)
            } catch (_: SecurityException) { false }
            if (!ok) main.post { _state.value = HidState.Unsupported }
        }

        override fun onServiceDisconnected(profile: Int) {
            hid = null; host = null; registered = false
            main.post { if (_state.value != HidState.Idle) _state.value = HidState.Registering }
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
            registered = isRegistered
            if (isRegistered) {
                if (_state.value is HidState.Registering || _state.value == HidState.Unsupported) _state.value = HidState.Ready
                pluggedDevice?.let { host = it }
            } else if (_state.value != HidState.Idle) {
                _state.value = HidState.Unsupported
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            val h = Host(device.address, device.name ?: device.address)
            when (state) {
                BluetoothProfile.STATE_CONNECTING -> _state.value = HidState.Connecting(h)
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device; _state.value = HidState.Connected(h)
                    // Hosts start every axis at zero until a report arrives, so send the neutral one.
                    releaseAll()
                }
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_DISCONNECTING -> {
                    if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        if (host?.address == device.address) host = null
                        _leds.value = 0
                        if (registered) _state.value = HidState.Ready
                        pendingSwitch?.let { a -> pendingSwitch = null; main.post { connect(a) } }
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val size = when (id.toInt()) {
                ReportId.KEYBOARD -> ReportSize.KEYBOARD
                ReportId.MOUSE -> ReportSize.MOUSE
                ReportId.GAMEPAD -> ReportSize.GAMEPAD
                ReportId.TOUCHPAD -> ReportSize.TOUCHPAD
                else -> 0
            }
            if (size == 0) hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            else hid?.replyReport(device, type, id, ByteArray(size))
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            readLeds(id.toInt(), data)
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            readLeds(reportId.toInt(), data)
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            if (host?.address == device.address) host = null
            if (registered) _state.value = HidState.Ready
        }
    }

    private fun readLeds(id: Int, data: ByteArray) {
        if (id == ReportId.KEYBOARD && data.isNotEmpty()) _leds.value = data[0].toInt() and 0x1F
        else if (id == 0 && data.size == 1) _leds.value = data[0].toInt() and 0x1F
    }
}
