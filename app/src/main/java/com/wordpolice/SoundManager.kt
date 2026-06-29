package com.wordpolice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

class SoundManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
    private var sirenTrack: AudioTrack? = null
    private var loopingTrack: AudioTrack? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.UK)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setPitch(1.1f)
                    tts?.setSpeechRate(0.82f)
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) { loopingTrack?.setVolume(0.22f) }
                        override fun onDone(utteranceId: String?) { loopingTrack?.setVolume(1.0f) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { loopingTrack?.setVolume(1.0f) }
                    })
                    ttsReady = true
                }
            }
        }
    }

    fun speakWord(word: String) {
        if (!ttsReady) return
        tts?.stop()
        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word")
    }

    fun speakPhrase(phrase: String) {
        if (!ttsReady) return
        tts?.stop()
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "phrase")
    }

    /** Two rapid woop cycles on correct answer. */
    fun playCorrect() = playSiren(durationMs = 700, cycles = 2, lowHz = 700.0, highHz = 1150.0)

    /** Low buzzer on wrong answer. */
    fun playWrong() {
        toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 350)
    }

    /** Descending wail when criminal gains ground. */
    fun playEscape() = playSiren(durationMs = 700, cycles = 1, lowHz = 1200.0, highHz = 700.0)

    /** Full woop-woop siren on win. */
    fun playWinSiren() = playSiren(durationMs = 2400, cycles = 4, lowHz = 700.0, highHz = 1300.0)

    /** Low droning sound on lose. */
    fun playLose() {
        toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 900)
    }

    /** Start a continuously looping police woop-woop siren. */
    fun startLoopingSiren() {
        stopLoopingSiren()

        val sampleRate = 44100
        // 800ms cycle: midHz=900, total cycles=720 — integer, so the buffer loops seamlessly
        val cycleSamples = sampleRate * 8 / 10
        val buf = ShortArray(cycleSamples)
        val lowHz = 680.0
        val highHz = 1120.0

        var phase = 0.0
        for (i in buf.indices) {
            val t = i.toDouble() / cycleSamples
            val sweep = if (t < 0.5) t * 2.0 else (1.0 - t) * 2.0
            val freq = lowHz + (highHz - lowHz) * sweep
            // Fundamental + 2nd harmonic for siren character
            val raw = sin(phase) + 0.35 * sin(2.0 * phase)
            val envelope = when {
                i < 441 -> i / 441.0
                i > cycleSamples - 441 -> (cycleSamples - i) / 441.0
                else -> 1.0
            }
            buf[i] = (32767 * 0.16 * envelope * raw / 1.35).toInt().toShort()
            phase += 2.0 * PI * freq / sampleRate
        }

        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        loopingTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            maxOf(minBuf, cycleSamples * 2),
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        loopingTrack?.write(buf, 0, cycleSamples)
        loopingTrack?.setLoopPoints(0, cycleSamples, -1)
        loopingTrack?.play()
    }

    fun stopLoopingSiren() {
        loopingTrack?.stop()
        loopingTrack?.release()
        loopingTrack = null
    }

    private fun playSiren(durationMs: Int, cycles: Int, lowHz: Double, highHz: Double) {
        sirenTrack?.stop()
        sirenTrack?.release()

        val sampleRate = 44100
        val numSamples = sampleRate * durationMs / 1000
        val cycleLen = numSamples.toDouble() / cycles
        val buf = ShortArray(numSamples)

        var phase = 0.0
        for (i in buf.indices) {
            val posInCycle = (i % cycleLen) / cycleLen
            val freq = lowHz + (highHz - lowHz) * when {
                posInCycle < 0.5 -> posInCycle * 2.0
                else -> (1.0 - posInCycle) * 2.0
            }
            val envelope = when {
                i < 1500 -> i.toDouble() / 1500.0
                i > numSamples - 1500 -> (numSamples - i).toDouble() / 1500.0
                else -> 1.0
            }
            val raw = sin(phase) + 0.35 * sin(2.0 * phase)
            buf[i] = (32767 * 0.65 * envelope * raw / 1.35).toInt().toShort()
            phase += 2.0 * PI * freq / sampleRate
        }

        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        sirenTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            maxOf(minBuf, numSamples * 2),
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        sirenTrack?.write(buf, 0, numSamples)
        sirenTrack?.play()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        toneGen.release()
        sirenTrack?.stop()
        sirenTrack?.release()
        loopingTrack?.stop()
        loopingTrack?.release()
    }
}
