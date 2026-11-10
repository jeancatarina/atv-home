package dev.jean.atvhome.social

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * The room's sound effects, synthesised on first use (a few notes of sine with a soft envelope) so
 * the app carries no audio files. Each effect is under half a second and plays on its own short
 * AudioTrack that releases itself. Phones never make sound: a beep would give a secret role away.
 */
object Sfx {
    enum class Effect { JOIN, START, WIN, STRIKE, OBJECTION, LEVELUP, BADGE, RECORD, REVEAL, STAMP, TICK }

    private const val RATE = 22_050
    private val cache = HashMap<Effect, ShortArray>()

    /** Notes as (frequency Hz, milliseconds). Zero Hz is a rest. */
    private fun notes(e: Effect): List<Pair<Double, Int>> = when (e) {
        Effect.JOIN -> listOf(660.0 to 70, 880.0 to 110)
        Effect.START -> listOf(392.0 to 90, 523.0 to 90, 659.0 to 160)
        Effect.WIN -> listOf(523.0 to 100, 659.0 to 100, 784.0 to 100, 1047.0 to 260)
        Effect.STRIKE -> listOf(150.0 to 260)
        Effect.OBJECTION -> listOf(880.0 to 80, 0.0 to 40, 880.0 to 80, 1175.0 to 160)
        Effect.LEVELUP -> listOf(523.0 to 80, 659.0 to 80, 784.0 to 80, 1047.0 to 320)
        Effect.BADGE -> listOf(784.0 to 90, 1047.0 to 240)
        Effect.RECORD -> listOf(523.0 to 90, 659.0 to 90, 784.0 to 90, 659.0 to 70, 1047.0 to 360)
        Effect.REVEAL -> listOf(440.0 to 90, 659.0 to 160)
        Effect.STAMP -> listOf(130.0 to 110)
        Effect.TICK -> listOf(1200.0 to 30)
    }

    private fun synth(e: Effect): ShortArray = cache.getOrPut(e) {
        val out = ArrayList<Short>()
        notes(e).forEach { (hz, ms) ->
            val n = RATE * ms / 1000
            for (i in 0 until n) {
                val env = minOf(1.0, i / (RATE * 0.008), (n - i) / (RATE * 0.03))   // 8 ms attack, 30 ms release: no clicks
                val wave = if (e == Effect.STRIKE || e == Effect.STAMP) sin(2 * PI * hz * i / RATE) + 0.5 * sin(2 * PI * hz * 1.5 * i / RATE) else sin(2 * PI * hz * i / RATE)
                out += (if (hz == 0.0) 0.0 else wave * env * 0.28 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        out.toShortArray()
    }

    fun play(e: Effect) {
        runCatching {
            val pcm = synth(e)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size - 1)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { t.release() }
                override fun onPeriodicNotification(t: AudioTrack) = Unit
            })
            track.play()
        }
    }
}
