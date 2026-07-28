package com.scambait.app.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SipManager(private val context: Context) {

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered

    private var sipUsername: String = ""
    private var sipServer: String = ""
    private var sipPassword: String = ""

    var onIncomingCall: ((String) -> Unit)? = null

    fun configureSipAccount(username: String, server: String, pass: String) {
        this.sipUsername = username
        this.sipServer = server
        this.sipPassword = pass

        if (username.isNotBlank() && server.isNotBlank()) {
            registerSipClient()
        }
    }

    private fun registerSipClient() {
        Log.i("SipManager", "Registering SIP endpoint for $sipUsername@$sipServer...")
        // Simulates/manages SIP REGISTER transaction
        _isRegistered.value = true
        Log.i("SipManager", "SIP Registration active. Listening for incoming forwarded VoIP calls.")
    }

    fun unregisterSipClient() {
        _isRegistered.value = false
        Log.i("SipManager", "SIP Registration closed.")
    }

    // Call bridge simulation / event trigger for testing
    fun triggerSimulatedCall(incomingNumber: String) {
        onIncomingCall?.invoke(incomingNumber)
    }
}
