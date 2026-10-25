package tv.jetflix.app.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Sound styles for moving around and selecting. Off is the default: the app has never made button sounds before. */
enum class ButtonSound(val label: String) {
    Off("Off"), Soft("Soft tick"), Click("Click"), Pop("Pop");
    fun next() = values()[(ordinal + 1) % values().size]
}

/**
 * Short, quiet tones made in code (no audio files). They play as UI sounds through a small preloaded track, never
 * take audio focus, and stay silent while the player or the screensaver is on screen.
 */
object ButtonSounds {
    private const val RATE = 22050
    @Volatile var style: ButtonSound = ButtonSound.Off
    /** Set by the app: true while the player or screensaver is showing. */
    @Volatile var suppressed: Boolean = false
    @Volatile var screensaverOn: Boolean = false
    @Volatile var onPlayer: Boolean = false
    private var lastMoveAt = 0L
    private val tracks = HashMap<String, AudioTrack>()

    /** 16-bit mono samples for one tone. Public so a test can check the shape without an audio device. */
    fun samples(style: ButtonSound, select: Boolean): ShortArray {
        val (hz, ms, amp, sweep) = when (style) {
            ButtonSound.Soft -> if (select) Tone(880.0, 45, 0.22, 0.0) else Tone(1250.0, 20, 0.14, 0.0)
            ButtonSound.Click -> if (select) Tone(1800.0, 16, 0.26, 0.0) else Tone(2600.0, 9, 0.16, 0.0)
            ButtonSound.Pop -> if (select) Tone(330.0, 70, 0.28, -120.0) else Tone(520.0, 28, 0.18, -160.0)
            ButtonSound.Off -> return ShortArray(0)
        }
        val n = RATE * ms / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val decay = exp(-t * (5.0 / (ms / 1000.0)))
            val attack = if (i < 40) i / 40.0 else 1.0 // no click from a hard start
            val f = hz + sweep * (t / (ms / 1000.0))
            (sin(2 * PI * f * t) * decay * attack * amp * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private data class Tone(val hz: Double, val ms: Int, val amp: Double, val sweep: Double)

    private fun track(style: ButtonSound, select: Boolean): AudioTrack? {
        val key = "${style.name}-$select"
        tracks[key]?.let { return it }
        val data = samples(style, select)
        if (data.isEmpty()) return null
        return try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(data.size * 2)
                .build()
            t.write(data, 0, data.size)
            tracks[key] = t
            t
        } catch (_: Exception) { null }
    }

    private fun play(style: ButtonSound, select: Boolean) {
        val t = track(style, select) ?: return
        try { t.stop(); t.reloadStaticData(); t.play() } catch (_: Exception) { }
    }

    /** Called for each D-pad press; a held direction key is limited to a few ticks per second. */
    fun onKey(select: Boolean, now: Long = System.currentTimeMillis()) {
        val s = style
        if (s == ButtonSound.Off || suppressed || onPlayer || screensaverOn) return
        if (!select) { if (now - lastMoveAt < 90) return; lastMoveAt = now }
        play(s, select)
    }

    /** Settings preview: plays the picked style's move tick, then the select tone. */
    fun preview(s: ButtonSound) {
        if (s == ButtonSound.Off) return
        play(s, false)
        play(s, true)
    }
}
