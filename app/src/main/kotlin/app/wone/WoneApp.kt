package app.wone

import android.app.Application
import app.wone.data.SettingsStore
import app.wone.feedback.Haptics
import app.wone.feedback.Sounds
import app.wone.hid.HidEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Process-wide objects. The foreground service keeps this process alive while connected. */
class WoneApp : Application() {
    lateinit var engine: HidEngine
    lateinit var settings: SettingsStore
    lateinit var sounds: Sounds
    lateinit var haptics: Haptics
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        engine = HidEngine(this)
        settings = SettingsStore(this)
        sounds = Sounds(this)
        haptics = Haptics(this)
        // Keep the feedback helpers in step with the settings.
        scope.launch {
            settings.flow.collect { s ->
                sounds.enabled = s.sounds
                sounds.volume = s.volume
                sounds.switchType = s.switchType
                haptics.level = s.haptics
            }
        }
    }
}

val android.content.Context.wone: WoneApp get() = applicationContext as WoneApp
