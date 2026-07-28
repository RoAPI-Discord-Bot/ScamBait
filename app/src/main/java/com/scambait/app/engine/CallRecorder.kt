package com.scambait.app.engine

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class CallRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isRecording = false

    fun startRecording(callerId: String): File? {
        val storageDir = File(context.getExternalFilesDir(null), "Recordings")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val fileName = "scambait_${callerId.replace("+", "")}_${System.currentTimeMillis()}.m4a"
        val outputFile = File(storageDir, fileName)
        currentOutputFile = outputFile

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(outputFile.absolutePath)

            try {
                prepare()
                start()
                isRecording = true
                Log.d("CallRecorder", "Started recording call to ${outputFile.absolutePath}")
            } catch (e: IOException) {
                Log.e("CallRecorder", "MediaRecorder prepare failed", e)
                return null
            }
        }
        return outputFile
    }

    fun stopRecording(): File? {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                Log.e("CallRecorder", "Error stopping recorder", e)
            } finally {
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
            }
        }
        return currentOutputFile
    }
}
