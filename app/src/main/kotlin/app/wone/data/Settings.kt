package app.wone.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class KeyboardFx(val label: String) {
    Static("Static"), Breathing("Breathing"), Spectrum("Spectrum"), Wave("Rainbow wave"),
    Spiral("Spiral"), Ripple("Ripple out"), Starlight("Starlight"), Rain("Digital rain"),
    Reactive("Reactive"), Splash("Splash"), Cross("Cross"), Heatmap("Heatmap"), Off("Off");

    val reactsToTyping get() = this == Reactive || this == Splash || this == Cross || this == Heatmap
}

enum class ThemeMode(val label: String) { Dark("Dark"), Light("Light"), System("System") }
enum class RingFx(val label: String) { Wave("Wave"), Breathe("Breathe"), Static("Static"), Off("Off") }
enum class SwitchType(val label: String, val feel: String) {
    Thocky("Thocky", "Linear, deep"), Tactile("Tactile", "Bump, crisp"), Clicky("Clicky", "Sharp click")
}
enum class HapticLevel(val label: String) { Rich("Rich"), Light("Light"), Off("Off") }
enum class TrackpadMode(val label: String) { Native("Native multitouch"), Simple("Simple") }

/** Accent choices. Flat colours only. */
val AccentChoices = listOf(0xFF3DDC97, 0xFF4DA3FF, 0xFFFFB224, 0xFFFF5C7A, 0xFFB794F6)

data class Settings(
    val onboardingDone: Boolean = false,
    val ringFx: RingFx = RingFx.Off,
    val theme: ThemeMode = ThemeMode.Dark,
    val dynamicColor: Boolean = true,
    val keyboardFx: KeyboardFx = KeyboardFx.Wave,
    val speed: Float = 4f,           // seconds per cycle, 1..10; lower is faster
    val brightness: Float = 0.9f,
    val accentIndex: Int = 0,
    val airSensitivity: Float = 1f,
    val airInvertX: Boolean = false,
    val airInvertY: Boolean = false,
    val sounds: Boolean = true,
    val volume: Float = 0.8f,
    val switchType: SwitchType = SwitchType.Thocky,
    val haptics: HapticLevel = HapticLevel.Rich,
    val trackpadMode: TrackpadMode = TrackpadMode.Native,
    val trackpadSpeed: Float = 1.4f,
    val trackpadGain: Float = 1f,
    val naturalScroll: Boolean = false,
    val reconnectLast: Boolean = true,
    val keepScreenOn: Boolean = true,
    val secureTyping: Boolean = false,
    val lastHost: String = "",
    val gamepadLayouts: String = "",
    val gamepadActive: String = "",
) {
    val accent: Long get() = AccentChoices[accentIndex.coerceIn(0, AccentChoices.lastIndex)]
}

private val Context.store: DataStore<Preferences> by preferencesDataStore("w-one-settings")

class SettingsStore(private val context: Context) {
    private object K {
        val onboarding = booleanPreferencesKey("onboarding")
        val ringFx = stringPreferencesKey("ringFx")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamicColor")
        val kbFx = stringPreferencesKey("kbFx")
        val speed = floatPreferencesKey("speed")
        val brightness = floatPreferencesKey("brightness")
        val accent = floatPreferencesKey("accent")
        val airSens = floatPreferencesKey("airSens")
        val airInvX = booleanPreferencesKey("airInvX")
        val airInvY = booleanPreferencesKey("airInvY")
        val sounds = booleanPreferencesKey("sounds")
        val volume = floatPreferencesKey("volume")
        val switchType = stringPreferencesKey("switchType")
        val haptics = stringPreferencesKey("haptics")
        val tpMode = stringPreferencesKey("tpMode")
        val tpSpeed = floatPreferencesKey("tpSpeed")
        val tpGain = floatPreferencesKey("tpGain")
        val natural = booleanPreferencesKey("natural")
        val reconnect = booleanPreferencesKey("reconnect")
        val keepOn = booleanPreferencesKey("keepOn")
        val secure = booleanPreferencesKey("secure")
        val lastHost = stringPreferencesKey("lastHost")
        val layouts = stringPreferencesKey("layouts")
        val activeLayout = stringPreferencesKey("activeLayout")
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(k: Preferences.Key<String>, d: E): E =
        this[k]?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: d

    val flow: Flow<Settings> = context.store.data.map(::readNow)

    suspend fun update(change: (Settings) -> Settings) {
        context.store.edit { p ->
            val cur = readNow(p)
            val n = change(cur)
            p[K.onboarding] = n.onboardingDone
            p[K.ringFx] = n.ringFx.name
            p[K.theme] = n.theme.name
            p[K.dynamic] = n.dynamicColor
            p[K.kbFx] = n.keyboardFx.name
            p[K.speed] = n.speed
            p[K.brightness] = n.brightness
            p[K.accent] = n.accentIndex.toFloat()
            p[K.airSens] = n.airSensitivity
            p[K.airInvX] = n.airInvertX
            p[K.airInvY] = n.airInvertY
            p[K.sounds] = n.sounds
            p[K.volume] = n.volume
            p[K.switchType] = n.switchType.name
            p[K.haptics] = n.haptics.name
            p[K.tpMode] = n.trackpadMode.name
            p[K.tpSpeed] = n.trackpadSpeed
            p[K.tpGain] = n.trackpadGain
            p[K.natural] = n.naturalScroll
            p[K.reconnect] = n.reconnectLast
            p[K.keepOn] = n.keepScreenOn
            p[K.secure] = n.secureTyping
            p[K.lastHost] = n.lastHost
            p[K.layouts] = n.gamepadLayouts
            p[K.activeLayout] = n.gamepadActive
        }
    }

    private fun readNow(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            onboardingDone = p[K.onboarding] ?: d.onboardingDone,
            ringFx = p.enum(K.ringFx, d.ringFx),
            theme = p.enum(K.theme, d.theme),
            dynamicColor = p[K.dynamic] ?: d.dynamicColor,
            keyboardFx = p.enum(K.kbFx, d.keyboardFx),
            speed = p[K.speed] ?: d.speed,
            brightness = p[K.brightness] ?: d.brightness,
            accentIndex = (p[K.accent] ?: d.accentIndex.toFloat()).toInt(),
            airSensitivity = p[K.airSens] ?: d.airSensitivity,
            airInvertX = p[K.airInvX] ?: d.airInvertX,
            airInvertY = p[K.airInvY] ?: d.airInvertY,
            sounds = p[K.sounds] ?: d.sounds,
            volume = p[K.volume] ?: d.volume,
            switchType = p.enum(K.switchType, d.switchType),
            haptics = p.enum(K.haptics, d.haptics),
            trackpadMode = p.enum(K.tpMode, d.trackpadMode),
            trackpadSpeed = p[K.tpSpeed] ?: d.trackpadSpeed,
            trackpadGain = p[K.tpGain] ?: d.trackpadGain,
            naturalScroll = p[K.natural] ?: d.naturalScroll,
            reconnectLast = p[K.reconnect] ?: d.reconnectLast,
            keepScreenOn = p[K.keepOn] ?: d.keepScreenOn,
            secureTyping = p[K.secure] ?: d.secureTyping,
            lastHost = p[K.lastHost] ?: d.lastHost,
            gamepadLayouts = p[K.layouts] ?: d.gamepadLayouts,
            gamepadActive = p[K.activeLayout] ?: d.gamepadActive,
        )
    }
}
