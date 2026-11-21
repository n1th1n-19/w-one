package app.wone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.wone.hid.HidState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the HID registration alive. Android drops a HID app that is not
 * in the foreground, so this must run whenever a computer may be connected.
 */
class HidService : Service() {

    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            ACTION_DISCONNECT -> wone.engine.disconnect()
        }
        createChannel()
        val engine = wone.engine
        if (!engine.hasPermission()) { stopSelf(); return START_NOT_STICKY }
        startInForeground(notification(engine.state.value))
        engine.start()
        if (job == null) job = wone.scope.launch {
            engine.state.collect { s ->
                getSystemService(NotificationManager::class.java).notify(ID, notification(s))
                if (s is HidState.Ready && wone.settings.flow.first().reconnectLast) {
                    val last = wone.settings.flow.first().lastHost
                    if (last.isNotEmpty()) engine.connect(last)
                }
                if (s is HidState.Connected) wone.settings.update { it.copy(lastHost = s.host.address) }
            }
        }
        return START_STICKY
    }

    private fun startInForeground(n: Notification) {
        ServiceCompat.startForeground(this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }

    private fun shutdown() {
        job?.cancel(); job = null
        wone.engine.releaseAll()
        wone.engine.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Connection", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shows which computer w-one is connected to" },
        )
    }

    private fun notification(s: HidState): Notification {
        val (title, text) = when (s) {
            is HidState.Connected -> "Connected to ${s.host.name}" to "Your phone is acting as a keyboard, mouse and gamepad"
            is HidState.Connecting -> "Connecting to ${s.host.name}" to "Waiting for the computer"
            HidState.Ready -> "Ready to connect" to "Pick this phone in your computer's Bluetooth settings"
            HidState.Registering -> "Starting" to "Registering with Bluetooth"
            HidState.BluetoothOff -> "Bluetooth is off" to "Turn Bluetooth on to connect"
            HidState.Unsupported -> "Not supported" to "This phone's Bluetooth does not offer the HID Device profile"
            else -> "w-one" to ""
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_wone)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (s is HidState.Connected) b.addAction(0, "Disconnect", action(ACTION_DISCONNECT))
        b.addAction(0, "Stop", action(ACTION_STOP))
        return b.build()
    }

    private fun action(a: String) = PendingIntent.getService(
        this, a.hashCode(), Intent(this, HidService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val ID = 1
        private const val CHANNEL = "connection"
        const val ACTION_STOP = "app.wone.STOP"
        const val ACTION_DISCONNECT = "app.wone.DISCONNECT"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, HidService::class.java))
        }
    }
}
