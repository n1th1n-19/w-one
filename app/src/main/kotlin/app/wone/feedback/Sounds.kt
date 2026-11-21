package app.wone.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import app.wone.data.SwitchType
import kotlin.random.Random

/** Short recorded or generated samples played through SoundPool with a little pitch jitter. */
class Sounds(private val context: Context) {

    @Volatile var enabled = true
    @Volatile var volume = 0.8f
    @Volatile var switchType = SwitchType.Thocky

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(24)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                .build(),
        )
        .build()

    private val ids = HashMap<String, IntArray>()
    private val rnd = Random(7)

    init { load() }

    private fun load() {
        val assets = context.assets
        val root = "sounds"
        for (dir in assets.list(root).orEmpty()) {
            val groups = HashMap<String, MutableList<Int>>()
            for (file in assets.list("$root/$dir").orEmpty()) {
                // File names look like "down_03.ogg": group is everything before the last underscore.
                val group = file.substringBeforeLast('_')
                val id = assets.openFd("$root/$dir/$file").use { pool.load(it, 1) }
                groups.getOrPut(group) { mutableListOf() }.add(id)
            }
            for ((g, list) in groups) ids["$dir/$g"] = list.toIntArray()
        }
    }

    private fun play(key: String, pick: Int = -1, rate: Float = 1f, gain: Float = 1f) {
        if (!enabled) return
        val list = ids[key] ?: return
        val id = list[(if (pick >= 0) pick else rnd.nextInt(list.size)) % list.size]
        val v = (volume * gain).coerceIn(0f, 1f)
        val jitter = 0.97f + rnd.nextFloat() * 0.06f
        pool.play(id, v, v, 1, 0, (rate * jitter).coerceIn(0.5f, 2f))
    }

    // Keyboard: each key keeps its own sample, deep stabiliser sounds for the wide keys.
    fun keyDown(code: Int, wide: Boolean) {
        val set = switchType.dir
        play(if (wide) "$set/down_deep" else "$set/down", pick = code)
    }

    fun keyUp(code: Int, wide: Boolean) {
        val set = switchType.dir
        play(if (wide) "$set/up_deep" else "$set/up", pick = code, gain = 0.7f)
    }

    fun mouseDown() = play("ui/mouse_down")
    fun mouseUp() = play("ui/mouse_up", gain = 0.8f)
    fun thunkDown() = play("ui/thunk_down")
    fun thunkUp() = play("ui/thunk_up", gain = 0.7f)
    fun padDown() = play("ui/pad_down")
    fun padUp() = play("ui/pad_up", gain = 0.7f)
    fun dpad() = play("ui/dpad_down")
    fun bumper() = play("ui/bumper_down")
    fun ratchet() = play("ui/ratchet_tick")
    fun chime() = play("ui/chime_home")
    fun tick() = play("ui/ratchet_tick", gain = 0.5f, rate = 1.3f)

    fun release() = pool.release()

    private val SwitchType.dir get() = name.lowercase()
}
