package com.eitan.airtracker.beep

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Low-latency short "tick" on the phone's built-in speaker, for the Geiger-counter
 * style search mode. One static AudioTrack is reused for every tick.
 */
class GeigerTicker(private val context: Context) {
    private var track: AudioTrack? = null

    fun prepare() {
        if (track != null) return
        val rate = 44100
        val len = rate * 30 / 1000
        val pcm = ShortArray(len) { i ->
            val env = exp(-i / (len / 5.0))
            (sin(2.0 * PI * 1900.0 * i / rate) * env * 0.9 * Short.MAX_VALUE).toInt().toShort()
        }
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        t.write(pcm, 0, pcm.size)
        context.getSystemService(AudioManager::class.java)
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?.let { t.setPreferredDevice(it) }
        track = t
    }

    fun tick() {
        val t = track ?: return
        runCatching {
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    fun release() {
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }
}
