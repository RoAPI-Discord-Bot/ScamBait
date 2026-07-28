package com.scambait.app.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsEngine(
    context: Context,
    private var pitch: Float = 0.75f,
    private var speed: Float = 0.85f
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false
    var onSpeechCompleted: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsEngine", "Language US is not supported")
            } else {
                isInitialized = true
                applySettings(pitch, speed)
                setupProgressListener()
            }
        } else {
            Log.e("TtsEngine", "TTS Initialization failed")
        }
    }

    fun applySettings(newPitch: Float, newSpeed: Float) {
        pitch = newPitch
        speed = newSpeed
        tts?.setPitch(pitch)
        tts?.setSpeechRate(speed)
    }

    fun speak(text: String, utteranceId: String = "scambait_utt_${System.currentTimeMillis()}") {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onSpeechCompleted?.invoke()
            }
            override fun onError(utteranceId: String?) {
                onSpeechCompleted?.invoke()
            }
        })
    }
}
