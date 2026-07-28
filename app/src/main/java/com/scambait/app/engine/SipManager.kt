package com.scambait.app.engine

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.sip.SipAudioCall
import android.net.sip.SipManager as AndroidSipManager
import android.net.sip.SipProfile
import android.net.sip.SipRegistrationListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wraps Android's built-in SIP stack to register with Twilio SIP Domain
 * and auto-answer incoming forwarded calls silently — no ringing.
 *
 * Flow:
 * Scammer → Your Verizon # → (Decline) → Verizon *71 → Twilio Number
 * → Twilio TwiML routes to SIP Domain → SipManager registers here
 * → Auto-answers, streams audio to SttEngine + TtsEngine → Bot talks back
 */
@SuppressLint("NewApi")
class SipManager(private val context: Context) {

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered

    private val _statusMessage = MutableStateFlow("Not configured")
    val statusMessage: StateFlow<String> = _statusMessage

    private var sipManager: AndroidSipManager? = null
    private var sipProfile: SipProfile? = null
    private var activeCall: SipAudioCall? = null

    /** Called when an incoming call is auto-answered. Arg = caller number string. */
    var onIncomingCall: ((String) -> Unit)? = null
    /** Called when the call ends (hang up, remote disconnect, etc.) */
    var onCallEnded: (() -> Unit)? = null

    /**
     * Register this device as a SIP endpoint with Twilio.
     * Call this from ScamTrapService once SIP credentials are available.
     *
     * @param username  Credential List username (e.g. "scambaitbot")
     * @param server    Twilio SIP Domain host (e.g. "scambait.sip.us1.twilio.com")
     * @param password  Credential List password
     */
    fun configureSipAccount(username: String, server: String, password: String) {
        if (username.isBlank() || server.isBlank() || password.isBlank()) {
            _statusMessage.value = "SIP credentials not set"
            Log.w("SipManager", "SIP credentials missing — not registering")
            return
        }

        try {
            if (AndroidSipManager.isApiSupported(context).not()) {
                _statusMessage.value = "SIP API not supported on this device"
                Log.e("SipManager", "android.net.sip not supported")
                return
            }

            // Close any existing registration first
            unregisterSipClient()

            sipManager = AndroidSipManager.newInstance(context)

            val profile = SipProfile.Builder(username, server)
                .setPassword(password)
                .setPort(5060)
                .setProtocol("UDP")
                .setAutoRegistration(true)
                .build()

            sipProfile = profile

            // PendingIntent that fires into SipIncomingCallReceiver when a call arrives
            val incomingCallIntent = Intent("com.scambait.app.SIP_INCOMING_CALL")
            incomingCallIntent.setPackage(context.packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, incomingCallIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            sipManager?.open(profile, pendingIntent, object : SipRegistrationListener {
                override fun onRegistering(localProfileUri: String?) {
                    _statusMessage.value = "Registering with Twilio SIP..."
                    Log.i("SipManager", "SIP registering: $localProfileUri")
                }

                override fun onRegistrationDone(localProfileUri: String?, expiryTime: Long) {
                    _isRegistered.value = true
                    _statusMessage.value = "✅ SIP Registered — Ready for calls"
                    Log.i("SipManager", "SIP registered! Expires in ${expiryTime}s")
                }

                override fun onRegistrationFailed(
                    localProfileUri: String?,
                    errorCode: Int,
                    errorMessage: String?
                ) {
                    _isRegistered.value = false
                    _statusMessage.value = "❌ SIP Registration Failed: $errorMessage"
                    Log.e("SipManager", "SIP failed [$errorCode]: $errorMessage")
                }
            })

        } catch (e: Exception) {
            _statusMessage.value = "SIP error: ${e.message}"
            Log.e("SipManager", "SIP setup exception", e)
        }
    }

    /**
     * Called by SipIncomingCallReceiver when Android's SIP stack delivers an incoming call.
     * Auto-answers immediately with no ringing, then notifies the service.
     */
    fun handleIncomingCall(incomingCall: SipAudioCall) {
        try {
            activeCall = incomingCall

            incomingCall.setListener(object : SipAudioCall.Listener() {
                override fun onCallEstablished(call: SipAudioCall) {
                    call.startAudio()
                    call.setSpeakerMode(false) // Use earpiece / microphone path
                    val callerUri = call.peerProfile?.uriString ?: "Unknown"
                    val callerNumber = extractNumber(callerUri)
                    Log.i("SipManager", "Call established from $callerNumber")
                    onIncomingCall?.invoke(callerNumber)
                }

                override fun onCallEnded(call: SipAudioCall) {
                    Log.i("SipManager", "Call ended by remote")
                    activeCall = null
                    onCallEnded?.invoke()
                }

                override fun onError(call: SipAudioCall, errorCode: Int, errorMessage: String?) {
                    Log.e("SipManager", "Call error [$errorCode]: $errorMessage")
                    activeCall = null
                    onCallEnded?.invoke()
                }
            }, true)

            // Answer immediately — no ringing
            incomingCall.answerCall(30)

        } catch (e: Exception) {
            Log.e("SipManager", "Error handling incoming call", e)
        }
    }

    /** Hang up the current active call. Called when AI emits [HANG_UP]. */
    fun hangUpCurrentCall() {
        try {
            activeCall?.endCall()
            activeCall = null
            Log.i("SipManager", "Call hung up by bot")
            onCallEnded?.invoke()
        } catch (e: Exception) {
            Log.e("SipManager", "Error hanging up call", e)
        }
    }

    /** Close SIP registration (e.g. when trap is disabled). */
    fun unregisterSipClient() {
        try {
            sipProfile?.let { profile ->
                sipManager?.close(profile.uriString)
            }
            sipProfile = null
            _isRegistered.value = false
            _statusMessage.value = "SIP unregistered"
        } catch (e: Exception) {
            Log.e("SipManager", "Error unregistering SIP", e)
        }
    }

    /** For local simulation / testing from HomeScreen button. */
    fun triggerSimulatedCall(incomingNumber: String) {
        onIncomingCall?.invoke(incomingNumber)
    }

    private fun extractNumber(uriString: String): String {
        // sip:+15551234567@domain.sip.twilio.com → +15551234567
        return try {
            uriString.removePrefix("sip:").substringBefore("@")
        } catch (e: Exception) {
            uriString
        }
    }
}
