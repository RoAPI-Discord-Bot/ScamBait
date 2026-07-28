package com.scambait.app.service

import android.content.Intent
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

class ScamTrapConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i("ScamTrapConnectionService", "Incoming call intercepted by Telecom ConnectionService!")

        val connection = ScamBaitConnection(applicationContext, request?.address?.schemeSpecificPart ?: "Unknown")
        connection.connectionCapabilities = Connection.CAPABILITY_SUPPORT_HOLD or Connection.CAPABILITY_HOLD
        connection.audioModeIsVoip = true

        // Auto-answer immediately in 0 seconds
        connection.onAnswer()

        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e("ScamTrapConnectionService", "Incoming connection failed")
    }
}

class ScamBaitConnection(
    private val context: android.content.Context,
    private val callerNumber: String
) : Connection() {

    override fun onAnswer() {
        super.onAnswer()
        setActive()
        Log.i("ScamBaitConnection", "Auto-answered call from $callerNumber via ConnectionService!")

        val intent = Intent(context, ScamTrapService::class.java).apply {
            putExtra("CALLER_NUMBER", callerNumber)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun onDisconnect() {
        super.onDisconnect()
        setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
        destroy()
        Log.i("ScamBaitConnection", "Call disconnected")
    }

    override fun onReject() {
        super.onReject()
        setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.REJECTED))
        destroy()
    }
}
