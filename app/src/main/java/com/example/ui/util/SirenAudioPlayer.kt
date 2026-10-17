package com.example.ui.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.R
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * High-fidelity Air Raid Siren audio synthesizer.
 * Generates an authentic undulating wailing siren (400Hz <-> 850Hz sweep)
 * played via real-time PCM audio streaming when Access is Denied or a wrong PIN is entered.
 */
object SirenAudioPlayer {
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var appContext: Context? = null
    private var mediaPlayer: MediaPlayer? = null

    /** Called once from MainActivity so the bundled wrong.mp3 can be played. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun playAirRaidSiren(scope: CoroutineScope, durationMs: Long = 3500L) {
        stop()

        // Item 5: play the provided wrong.mp3; fall back to the synthesized
        // siren below only if the file cannot be played for some reason.
        val ctx = appContext
        if (ctx != null) {
            try {
                val mp = MediaPlayer.create(ctx, R.raw.wrong)
                if (mp != null) {
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    mp.setOnCompletionListener { it.release(); if (mediaPlayer === it) mediaPlayer = null }
                    mediaPlayer = mp
                    mp.start()
                    return
                }
            } catch (e: Exception) {
                // fall through to synthesized siren
            }
        }

        playbackJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast( sampleRate / 2 )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val buffer = ShortArray(1024)
            var currentPhase = 0.0
            val startTime = System.currentTimeMillis()

            try {
                while (isActive && (System.currentTimeMillis() - startTime < durationMs)) {
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                    // Undulating siren frequency sweep between 420 Hz and 880 Hz every 1.4 seconds
                    val sirenCycle = sin(2.0 * PI * (elapsedSeconds / 1.4))
                    val currentFreq = 650.0 + (230.0 * sirenCycle)

                    val phaseIncrement = (2.0 * PI * currentFreq) / sampleRate

                    for (i in buffer.indices) {
                        currentPhase += phaseIncrement
                        if (currentPhase > 2.0 * PI) currentPhase -= 2.0 * PI

                        // Harmonics for realistic mechanical siren sound
                        val fundamental = sin(currentPhase)
                        val harmonic = 0.35 * sin(2.0 * currentPhase)
                        val subHarmonic = 0.15 * sin(3.0 * currentPhase)
                        val combined = (fundamental + harmonic + subHarmonic) / 1.5

                        buffer[i] = (combined * 30000.0).toInt().coerceIn(-32767, 32767).toShort()
                    }

                    track.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                // Catch any interrupted audio stream
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (ignored: Exception) {}
            }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (ignored: Exception) {}
        audioTrack = null
    }
}
