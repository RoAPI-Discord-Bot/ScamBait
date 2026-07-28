package com.scambait.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.net.Uri
import android.net.sip.SipAudioCall
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.scambait.app.R
import com.scambait.app.ScamTrapApplication
import com.scambait.app.data.db.AppDatabase
import com.scambait.app.data.model.CallLogEntity
import com.scambait.app.data.repository.SettingsRepository
import com.scambait.app.engine.AiAction
import com.scambait.app.engine.AiPersonaEngine
import com.scambait.app.engine.CallRecorder
import com.scambait.app.engine.PersonaType
import com.scambait.app.engine.SipManager
import com.scambait.app.engine.SttEngine
import com.scambait.app.engine.TtsEngine
import com.scambait.app.ui.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class ScamTrapService : LifecycleService() {

    companion object {
        const val ACTION_SCREEN_CALL = "com.scambait.app.ACTION_SCREEN_CALL"
        const val ACTION_SIP_INCOMING_CALL = "com.scambait.app.ACTION_SIP_INCOMING_CALL"
        const val EXTRA_CALLER_NUMBER = "CALLER_NUMBER"
        const val EXTRA_IS_SPAM = "IS_SPAM"

        /** Static holder for SipAudioCall passed from SipIncomingCallReceiver */
        @Volatile var pendingIncomingSipCall: SipAudioCall? = null
    }

    private val binder = LocalBinder()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sttEngine: SttEngine
    private lateinit var ttsEngine: TtsEngine
    private lateinit var aiPersonaEngine: AiPersonaEngine
    private lateinit var callRecorder: CallRecorder
    private lateinit var sipManager: SipManager

    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive

    private val _currentCallerId = MutableStateFlow("")
    val currentCallerId: StateFlow<String> = _currentCallerId

    private var callStartTime = 0L
    private var currentRecordingFile: File? = null
    private var pendingHangUp = false
    private var isLegitimateCallerDetected = false

    private var aecEffect: AcousticEchoCanceler? = null
    private var nsEffect: NoiseSuppressor? = null

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
        sipManager = SipManager(this)

        sipManager.onIncomingCall = { incomingNumber -> handleIncomingCall(incomingNumber) }
        sipManager.onCallEnded = { endCurrentCall() }

        setupEngines()
        startForegroundServiceNotification()

        // Register with Twilio SIP domain if credentials are saved
        lifecycleScope.launch {
            val username = settingsRepository.sipUsername.first()
            val server = settingsRepository.sipServer.first()
            val password = settingsRepository.sipPassword.first()
            if (username.isNotBlank() && server.isNotBlank() && password.isNotBlank()) {
                sipManager.configureSipAccount(username, server, password)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_SCREEN_CALL -> {
                // CallScreeningService detected an incoming cellular call on this device
                val callerNum = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: ""
                val isSpam = intent.getBooleanExtra(EXTRA_IS_SPAM, false)
                lifecycleScope.launch {
                    val protectContacts = settingsRepository.protectContacts.first()
                    val trapActive = settingsRepository.isTrapActive.first()
                    if (!trapActive) return@launch

                    if (protectContacts && isContactSaved(callerNum)) {
                        Log.i("ScamTrapService", "Caller $callerNum is in contacts — bypassing trap")
                        return@launch
                    }

                    // Auto-answer the ringing cellular call
                    autoAnswerCellularCall()

                    // Wait for call to fully connect before starting audio
                    delay(1500)

                    // Set up acoustic coupling: speakerphone on, echo cancellation OFF
                    // so TTS audio from speaker goes through mic to the caller
                    setupAcousticCoupling()

                    handleIncomingCall(callerNum)
                }
            }

            ACTION_SIP_INCOMING_CALL -> {
                // A real Twilio SIP call has arrived
                val call = pendingIncomingSipCall
                pendingIncomingSipCall = null
                if (call != null) {
                    sipManager.handleIncomingCall(call)
                }
            }

            else -> {
                // Direct caller number (simulation / legacy)
                val callerNum = intent?.getStringExtra(EXTRA_CALLER_NUMBER)
                if (!callerNum.isNullOrEmpty()) {
                    handleIncomingCall(callerNum)
                }
            }
        }
        return START_STICKY
    }

    /**
     * Auto-answers the currently ringing cellular call using Android's TelecomManager.
     * Requires ANSWER_PHONE_CALLS permission (granted at runtime in MainActivity).
     */
    private fun autoAnswerCellularCall() {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                telecomManager.acceptRingingCall()
                Log.i("ScamTrapService", "Auto-answered cellular call via TelecomManager")
            }
        } catch (e: SecurityException) {
            Log.e("ScamTrapService", "ANSWER_PHONE_CALLS permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e("ScamTrapService", "Failed to auto-answer call: ${e.message}")
        }
    }

    /**
     * Acoustic coupling setup:
     * - Enables speakerphone so TTS audio plays loudly
     * - Disables AcousticEchoCanceler so the speaker audio IS picked up by the mic
     * - This routes TTS speech TO the caller via the phone's own mic
     *
     * On Pixel 8a the hardware AEC is aggressive; disabling it via AudioEffect lets
     * the speaker audio reach the far-end uplink.
     */
    private fun setupAcousticCoupling() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true

            // Generate an audio session and disable echo cancellation + noise suppression
            // so TTS coming from the speaker IS captured by mic and heard by caller
            val sessionId = audioManager.generateAudioSessionId()
            if (AcousticEchoCanceler.isAvailable()) {
                aecEffect = AcousticEchoCanceler.create(sessionId)
                aecEffect?.enabled = false
                Log.i("ScamTrapService", "AEC disabled for acoustic coupling")
            }
            if (NoiseSuppressor.isAvailable()) {
                nsEffect = NoiseSuppressor.create(sessionId)
                nsEffect?.enabled = false
            }
        } catch (e: Exception) {
            Log.w("ScamTrapService", "Acoustic coupling setup warning: ${e.message}")
        }
    }

    private fun teardownAcousticCoupling() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            aecEffect?.release()
            nsEffect?.release()
            aecEffect = null
            nsEffect = null
        } catch (e: Exception) {
            Log.w("ScamTrapService", "Teardown warning: ${e.message}")
        }
    }

    private fun setupEngines() {
        sttEngine.onSpeechRecognized = { transcribedText ->
            Log.d("ScamTrapService", "Caller said: $transcribedText")
            val personaResponse = aiPersonaEngine.generateResponse(transcribedText)
            Log.d("ScamTrapService", "AI response: ${personaResponse.spokenText}")

            if (personaResponse.isLegitimateCaller) {
                isLegitimateCallerDetected = true
            }

            pendingHangUp = personaResponse.actions.contains(AiAction.HANG_UP)
            ttsEngine.speak(personaResponse.spokenText)
        }

        ttsEngine.onSpeechCompleted = {
            if (pendingHangUp) {
                pendingHangUp = false
                Log.i("ScamTrapService", "AI [HANG_UP] triggered — ending call")
                // Use accessibility service to click end-call button in Google Phone app
                ScamBaitAccessibilityService.requestHangUp()
                Handler(Looper.getMainLooper()).postDelayed({ endCurrentCall() }, 1500)
            } else if (_isCallActive.value) {
                sttEngine.startListening()
            }
        }
    }

    fun handleIncomingCall(callerNumber: String) {
        lifecycleScope.launch {
            val protectContacts = settingsRepository.protectContacts.first()
            if (protectContacts && isContactSaved(callerNumber)) {
                Log.i("ScamTrapService", "Caller $callerNumber is in contacts — bypassing")
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

            // Initial AI greeting
            val initialResponse = aiPersonaEngine.generateResponse("Hello")
            pendingHangUp = initialResponse.actions.contains(AiAction.HANG_UP)
            ttsEngine.speak(initialResponse.spokenText)
        }
    }

    fun endCurrentCall() {
        if (!_isCallActive.value) return

        sttEngine.stopListening()
        ttsEngine.stop()
        teardownAcousticCoupling()
        val recordedFile = callRecorder.stopRecording()

        val durationSec = (System.currentTimeMillis() - callStartTime) / 1000
        val transcriptJson = aiPersonaEngine.getHistory()
            .joinToString(separator = "\n") { "${it.first}: ${it.second}" }

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
            .setContentText("Monitoring for incoming scam calls")
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
            Log.e("ScamTrapService", "Could not start foreground: ${e.message}")
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
        sipManager.unregisterSipClient()
        teardownAcousticCoupling()
        super.onDestroy()
    }
}
