package app.wone.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import app.wone.data.SwitchType
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * Plays short samples through one low-latency stream that is pinned to the phone's own speaker.
 * A normal sound pool follows the system route, so while the phone is paired with a computer for
 * audio the clicks would play on the computer, late. Samples are decoded once at start-up.
 */
class Sounds(private val context: Context) {

    @Volatile var enabled = true
    @Volatile var volume = 0.8f
    @Volatile var switchType = SwitchType.Thocky

    private class Voice(val pcm: ShortArray, var pos: Float, val step: Float, val gain: Float)

    private val rate = 48000
    private val samples = HashMap<String, List<ShortArray>>()
    private val voices = ArrayList<Voice>()
    private val lock = Object()
    private val rnd = Random(7)
    @Volatile private var ready = false
    @Volatile private var running = true

    init {
        Thread({ decodeAll(); ready = true; mixLoop() }, "wone-sound").apply { isDaemon = true; priority = Thread.MAX_PRIORITY }.start()
    }

    // ---- decoding ----------------------------------------------------------------------

    private fun decodeAll() {
        val assets = context.assets
        for (dir in assets.list("sounds").orEmpty()) {
            val groups = HashMap<String, MutableList<ShortArray>>()
            for (file in assets.list("sounds/$dir").orEmpty().sorted()) {
                val pcm = runCatching { decode("sounds/$dir/$file") }.getOrNull() ?: continue
                groups.getOrPut(file.substringBeforeLast('_')) { mutableListOf() }.add(pcm)
            }
            for ((g, list) in groups) samples["$dir/$g"] = list
        }
    }

    /** Decodes a mono OGG asset to 16-bit PCM at the stream rate. */
    private fun decode(path: String): ShortArray {
        val ex = MediaExtractor()
        context.assets.openFd(path).use { ex.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        ex.selectTrack(0)
        val fmt = ex.getTrackFormat(0)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        val out = ArrayList<ShortArray>()
        val info = MediaCodec.BufferInfo()
        var inDone = false; var outDone = false
        var srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        while (!outDone) {
            if (!inDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                    if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                    else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            if (o >= 0) {
                val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                val s = ShortArray(info.size / 2); buf.position(info.offset); buf.asShortBuffer().get(s)
                out += s
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
            } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val f = codec.outputFormat
                srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
        }
        codec.stop(); codec.release(); ex.release()
        val all = ShortArray(out.sumOf { it.size }); var p = 0
        for (a in out) { a.copyInto(all, p); p += a.size }
        // Down-mix to mono, then resample to the stream rate if the file was recorded at another one.
        val mono = if (channels == 1) all else ShortArray(all.size / channels) { i -> (0 until channels).sumOf { all[i * channels + it].toInt() }.div(channels).toShort() }
        if (srcRate == rate) return mono
        val n = (mono.size.toLong() * rate / srcRate).toInt()
        return ShortArray(n) { i -> mono[(i.toLong() * srcRate / rate).toInt().coerceAtMost(mono.size - 1)] }
    }

    // ---- mixing ------------------------------------------------------------------------

    private fun speaker(): AudioDeviceInfo? =
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    private fun mixLoop() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val burst = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
        val block = burst
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf, block * 2 * 4))
            .build()
        speaker()?.let { track.setPreferredDevice(it) }
        track.setBufferSizeInFrames(block * 3)

        val mix = FloatArray(block)
        val outBuf = ShortArray(block)
        var idleBlocks = 0
        var playing = false
        while (running) {
            synchronized(lock) {
                while (voices.isEmpty() && idleBlocks > 400) { if (playing) { track.pause(); track.flush(); playing = false }; lock.wait() ; idleBlocks = 0 }
                mix.fill(0f)
                val it = voices.iterator()
                while (it.hasNext()) {
                    val v = it.next()
                    for (i in 0 until block) {
                        val ip = v.pos.toInt()
                        if (ip >= v.pcm.size - 1) { it.remove(); break }
                        val f = v.pos - ip
                        mix[i] += (v.pcm[ip] * (1f - f) + v.pcm[ip + 1] * f) * v.gain / 32768f
                        v.pos += v.step
                    }
                }
                if (voices.isEmpty()) idleBlocks++ else idleBlocks = 0
            }
            if (!playing) { track.play(); playing = true }
            for (i in 0 until block) outBuf[i] = (mix[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            track.write(outBuf, 0, block, AudioTrack.WRITE_BLOCKING)
        }
        track.release()
    }

    private fun play(key: String, pick: Int = -1, rate: Float = 1f, gain: Float = 1f) {
        if (!enabled || !ready) return
        val list = samples[key] ?: return
        val pcm = list[(if (pick >= 0) pick else rnd.nextInt(list.size)) % list.size]
        val jitter = 0.97f + rnd.nextFloat() * 0.06f
        val v = Voice(pcm, 0f, rate * jitter, (volume * gain).coerceIn(0f, 1.5f))
        synchronized(lock) {
            if (voices.size < 24) voices += v
            lock.notifyAll()
        }
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

    fun release() { running = false; synchronized(lock) { lock.notifyAll() } }

    private val SwitchType.dir get() = name.lowercase()
}
