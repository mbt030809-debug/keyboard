package com.mbt.keyboard.ime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.mbt.keyboard.R

/**
 * 키보드 안에서 바로 음성인식 (Android SpeechRecognizer).
 * 마이크 키를 누르면 [start], 듣는 중에 다시 누르면 [stop]으로 마무리한다.
 */
class VoiceInput(private val context: Context, private val callback: Callback) {

    interface Callback {
        fun onListening(on: Boolean)
        /** 말하는 중 인식된 중간 결과 */
        fun onPartial(text: String)
        fun onResult(text: String)
        fun onError(message: String)
    }

    private var recognizer: SpeechRecognizer? = null

    var listening = false
        private set

    fun start(languageTag: String) {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onError(context.getString(R.string.voice_unavailable))
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(listener)
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        setListening(true)
        r.startListening(intent)
    }

    /** 말하기를 끝내고 지금까지의 결과를 받는다. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** 결과를 받지 않고 중단 */
    fun cancel() {
        release()
        setListening(false)
    }

    fun destroy() = release().also { listening = false }

    private fun release() {
        recognizer?.apply { setRecognitionListener(null); cancel(); destroy() }
        recognizer = null
    }

    private fun setListening(on: Boolean) {
        if (listening == on) return
        listening = on
        callback.onListening(on)
    }

    private fun finish() {
        release()
        setListening(false)
    }

    private fun Bundle?.first(key: String): String? =
        this?.getStringArrayList(key)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults.first(SpeechRecognizer.RESULTS_RECOGNITION)?.let(callback::onPartial)
        }

        override fun onResults(results: Bundle?) {
            val text = results.first(SpeechRecognizer.RESULTS_RECOGNITION)
            finish()
            if (text != null) callback.onResult(text)
            else callback.onError(context.getString(R.string.voice_no_match))
        }

        override fun onError(error: Int) {
            finish()
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    R.string.voice_no_match
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_permission_denied
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.voice_network
                else -> R.string.voice_error
            }
            callback.onError(context.getString(msg))
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
