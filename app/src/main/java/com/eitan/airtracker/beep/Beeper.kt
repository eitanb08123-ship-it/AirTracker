package com.eitan.airtracker.beep

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.eitan.airtracker.Graph
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays a short "found it" chirp. The audio track is pinned to the phone's built-in
 * speaker, so it does not play inside the AirPods even when they are connected.
 */
object Beeper {
    private const val TAG = "Beeper"
    private const val RATE = 44100
    private val playing = AtomicBoolean(false)

    fun play(context: Context) {
        if (!playing.compareAndSet(false, true)) return
        val app = context.applicationContext
        thread(name = "beeper") {
            var track: AudioTrack? = null
            try {
                val pcm = buildChirp()
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                val am = app.getSystemService(AudioManager::class.java)
                am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { track.setPreferredDevice(it) }
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 150)
            } catch (e: Exception) {
                Log.w(TAG, "beep failed", e)
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
                playing.set(false)
            }
        }
    }

    /** Three rising tones with soft edges (no clicks). */
    private fun buildChirp(): ShortArray {
        val tones = listOf(1046.5, 1318.5, 1568.0) // C6, E6, G6
        val toneMs = 140
        val gapMs = 60
        val toneLen = RATE * toneMs / 1000
        val gapLen = RATE * gapMs / 1000
        val out = ShortArray(tones.size * (toneLen + gapLen))
        var idx = 0
        val fade = RATE * 10 / 1000
        for (f in tones) {
            for (i in 0 until toneLen) {
                val env = min(1.0, min(i, toneLen - i).toDouble() / fade)
                val v = sin(2.0 * PI * f * i / RATE) * env * 0.85
                out[idx++] = (v * Short.MAX_VALUE).toInt().toShort()
            }
            idx += gapLen
        }
        return out
    }
}

/**
 * Decides when to beep: only when the smoothed signal crosses the user's threshold,
 * respecting a cooldown, and re-arming only after the signal clearly drops again
 * (hysteresis), so it never beeps non-stop.
 */
class ProximityBeepController(private val context: Context) {
    private var armed = true
    private var lastBeep = 0L

    fun onSignal(smoothedRssi: Double, connected: Boolean, now: Long) {
        val s = Graph.settings.value
        if (!s.beepEnabled) return
        if (connected && !s.beepWhenConnected) return
        if (smoothedRssi >= s.thresholdRssi) {
            if (armed && now - lastBeep >= s.cooldownSec * 1000L) {
                armed = false
                lastBeep = now
                Beeper.play(context)
            }
        } else if (smoothedRssi < s.thresholdRssi - HYSTERESIS_DB) {
            armed = true
        }
    }

    fun reset() {
        armed = true
    }

    companion object {
        const val HYSTERESIS_DB = 6
    }
}
