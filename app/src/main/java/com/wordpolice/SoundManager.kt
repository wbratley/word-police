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

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.UK)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setPitch(1.1f)
                    tts?.setSpeechRate(0.82f)
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

    /** Short ascending siren blip on correct answer. */
    fun playCorrect() = playSiren(durationMs = 600, cycles = 1, lowHz = 800.0, highHz = 1300.0)

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

    private fun playSiren(durationMs: Int, cycles: Int, lowHz: Double, highHz: Double) {
        sirenTrack?.stop()
        sirenTrack?.release()

        val sampleRate = 44100
        val numSamples = sampleRate * durationMs / 1000
        val cycleLen = numSamples.toDouble() / cycles
        val buf = ShortArray(numSamples)

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
            buf[i] = (32767 * 0.72 * envelope * sin(2.0 * PI * freq * (i.toDouble() / sampleRate))).toInt().toShort()
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
    }
}
