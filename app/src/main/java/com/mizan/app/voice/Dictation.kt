package com.mizan.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Callbacks arrive on the main thread. */
interface DictationListener {
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(code: DictationError)
    fun onStopped()
}

enum class DictationError { NO_ENGINE, NETWORK, PERMISSION, LANGUAGE, OTHER }

/**
 * Uses the speech recogniser installed on the device (Google, Huawei, Xiaomi, Samsung …).
 * Many engines need internet; some support offline Arabic/English packs. Keeps listening until stop().
 */
class Dictation(private val ctx: Context, private val listener: DictationListener) {
    private var sr: SpeechRecognizer? = null
    private var listening = false
    private var language = "en-US"

    companion object {
        fun available(ctx: Context) = SpeechRecognizer.isRecognitionAvailable(ctx)
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun restart() {
        if (!listening) return
        try {
            sr?.startListening(intent())
        } catch (_: Throwable) {
            listening = false
            listener.onError(DictationError.OTHER)
            listener.onStopped()
        }
    }

    fun start(language: String) {
        this.language = language
        if (!available(ctx)) {
            listener.onError(DictationError.NO_ENGINE)
            listener.onStopped()
            return
        }
        val r = sr ?: SpeechRecognizer.createSpeechRecognizer(ctx).also { sr = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                listener.onPartial(t)
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) listener.onFinal(t)
                restart()
            }
            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart()
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {}
                    else -> {
                        listening = false
                        listener.onError(
                            when (error) {
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> DictationError.NETWORK
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> DictationError.PERMISSION
                                12, 13 -> DictationError.LANGUAGE // ERROR_LANGUAGE_NOT_SUPPORTED / UNAVAILABLE (API 31+)
                                else -> DictationError.OTHER
                            },
                        )
                        listener.onStopped()
                    }
                }
            }
        })
        listening = true
        restart()
    }

    fun stop() {
        listening = false
        try {
            sr?.stopListening()
        } catch (_: Throwable) {
        }
        listener.onStopped()
    }

    fun release() {
        listening = false
        try {
            sr?.destroy()
        } catch (_: Throwable) {
        }
        sr = null
    }
}
