package com.scambait.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.scambait.app.R
import com.scambait.app.ScamTrapApplication
import com.scambait.app.data.db.AppDatabase
import com.scambait.app.data.model.CallLogEntity
import com.scambait.app.data.repository.SettingsRepository
import com.scambait.app.engine.AiPersonaEngine
import com.scambait.app.engine.CallRecorder
import com.scambait.app.engine.PersonaType
import com.scambait.app.engine.SttEngine
import com.scambait.app.engine.TtsEngine
import com.scambait.app.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class ScamTrapService : LifecycleService() {

    private val binder = LocalBinder()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sttEngine: SttEngine
    private lateinit var ttsEngine: TtsEngine
    private lateinit var aiPersonaEngine: AiPersonaEngine
    private lateinit var callRecorder: CallRecorder

    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive

    private val _currentCallerId = MutableStateFlow("")
    val currentCallerId: StateFlow<String> = _currentCallerId

    private var callStartTime = 0L
    private var currentRecordingFile: File? = null

    inner class LocalBinder : Binder() {
        fun getService(): ScamTrapService = this@ScamTrapService
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        sttEngine = SttEngine(this)
        ttsEngine = TtsEngine(this)
        aiPersonaEngine = AiPersonaEngine()
        callRecorder = CallRecorder(this)

        setupEngines()
        startForegroundServiceNotification()
    }

    private var pendingHangUp = false
    private var isLegitimateCallerDetected = false

    private fun setupEngines() {
        sttEngine.onSpeechRecognized = { transcribedText ->
            Log.d("ScamTrapService", "Caller said: $transcribedText")
            val personaResponse = aiPersonaEngine.generateResponse(transcribedText)
            Log.d("ScamTrapService", "AI response: ${personaResponse.spokenText}")

            if (personaResponse.isLegitimateCaller) {
                isLegitimateCallerDetected = true
            }

            pendingHangUp = personaResponse.actions.contains(com.scambait.app.engine.AiAction.HANG_UP)
            ttsEngine.speak(personaResponse.spokenText)
        }

        ttsEngine.onSpeechCompleted = {
            if (pendingHangUp) {
                pendingHangUp = false
                Log.i("ScamTrapService", "AI command triggered HANG_UP. Ending call now.")
                endCurrentCall()
            } else if (_isCallActive.value) {
                sttEngine.startListening()
            }
        }
    }

    fun handleIncomingCall(callerNumber: String) {
        lifecycleScope.launch {
            val protectContacts = settingsRepository.protectContacts.first()
            if (protectContacts && isContactSaved(callerNumber)) {
                Log.i("ScamTrapService", "Caller $callerNumber is in contacts. Pass through - trap bypassed.")
                return@launch
            }

            val personaName = settingsRepository.personaType.first()
            val pitch = settingsRepository.ttsPitch.first()
            val speed = settingsRepository.ttsSpeed.first()

            isLegitimateCallerDetected = false
            aiPersonaEngine.personaType = try {
                PersonaType.valueOf(personaName)
            } catch (e: Exception) {
                PersonaType.MARGARET
            }
            ttsEngine.applySettings(pitch, speed)
            aiPersonaEngine.resetHistory()

            _isCallActive.value = true
            _currentCallerId.value = callerNumber
            callStartTime = System.currentTimeMillis()

            currentRecordingFile = callRecorder.startRecording(callerNumber)

            // Initial AI Greeting
            val initialResponse = aiPersonaEngine.generateResponse("Hello")
            pendingHangUp = initialResponse.actions.contains(com.scambait.app.engine.AiAction.HANG_UP)
            ttsEngine.speak(initialResponse.spokenText)
        }
    }

    fun endCurrentCall() {
        if (!_isCallActive.value) return

        sttEngine.stopListening()
        ttsEngine.stop()
        val recordedFile = callRecorder.stopRecording()

        val durationSec = (System.currentTimeMillis() - callStartTime) / 1000
        val history = aiPersonaEngine.getHistory()
        val transcriptJson = history.joinToString(separator = "\n") { "${it.first}: ${it.second}" }

        val callLog = CallLogEntity(
            callerNumber = _currentCallerId.value.ifEmpty { "Unknown Caller" },
            timestamp = callStartTime,
            durationSeconds = durationSec,
            transcriptJson = transcriptJson,
            audioFilePath = recordedFile?.absolutePath ?: "",
            personaName = aiPersonaEngine.personaType.name,
            isSpamConfirmed = !isLegitimateCallerDetected
        )

        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext).callLogDao().insertCallLog(callLog)
        }

        _isCallActive.value = false
        _currentCallerId.value = ""
    }

    private fun isContactSaved(phoneNumber: String): Boolean {
        if (phoneNumber.isEmpty()) return false
        return try {
            val uri: Uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup._ID)
            val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)
            val exists = (cursor?.count ?: 0) > 0
            cursor?.close()
            exists
        } catch (e: Exception) {
            Log.e("ScamTrapService", "Error checking contacts whitelist", e)
            false
        }
    }

    private fun startForegroundServiceNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, ScamTrapApplication.CHANNEL_ID)
            .setContentTitle("ScamBait Active Protection")
            .setContentText("Monitoring for incoming scam calls to trap")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    1001,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e("ScamTrapService", "Could not start foreground notification: ${e.message}")
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        sttEngine.stopListening()
        ttsEngine.shutdown()
        callRecorder.stopRecording()
        super.onDestroy()
    }
}
