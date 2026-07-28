package com.scambait.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.scambait.app.R

class ScamTrapConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val callerNumber = request?.address?.schemeSpecificPart ?: "Unknown"
        Log.i("ScamTrapConnectionService", "Incoming VoIP call from $callerNumber — auto-answering silently")

        val connection = ScamBaitConnection(applicationContext, callerNumber)
        connection.connectionCapabilities =
            Connection.CAPABILITY_SUPPORT_HOLD or Connection.CAPABILITY_HOLD
        connection.audioModeIsVoip = true
        // Auto-answer immediately — no ringing
        connection.onAnswer()

        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e("ScamTrapConnectionService", "Incoming VoIP connection failed")
    }

    companion object {
        private const val PHONE_ACCOUNT_ID = "scambait_voip_account"

        fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
            val componentName = ComponentName(context, ScamTrapConnectionService::class.java)
            return PhoneAccountHandle(componentName, PHONE_ACCOUNT_ID)
        }

        /**
         * Registers the ScamBait VoIP PhoneAccount with Android Telecom.
         * This allows ScamBait to intercept ONLY VoIP-forwarded calls (e.g. from TextFree),
         * leaving all regular cellular calls handled by the normal phone app.
         * No need to change default phone app.
         */
        fun registerPhoneAccount(context: Context) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val handle = getPhoneAccountHandle(context)

            val accountBuilder = PhoneAccount.builder(handle, "ScamBait VoIP Trap")
                .setCapabilities(
                    PhoneAccount.CAPABILITY_CALL_PROVIDER or
                    PhoneAccount.CAPABILITY_CONNECTION_MANAGER or
                    PhoneAccount.CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS
                )
                .addSupportedUriScheme(PhoneAccount.NO_HIGHLIGHT_COLOR.toString())
                .addSupportedUriScheme("sip")
                .addSupportedUriScheme("tel")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                accountBuilder.setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            }

            val account = accountBuilder.build()

            try {
                telecomManager.registerPhoneAccount(account)
                Log.i("ScamTrapConnectionService", "ScamBait PhoneAccount registered successfully")
            } catch (e: Exception) {
                Log.e("ScamTrapConnectionService", "Failed to register PhoneAccount: ${e.message}")
            }
        }

        fun isPhoneAccountEnabled(context: Context): Boolean {
            return try {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val handle = getPhoneAccountHandle(context)
                val account = telecomManager.getPhoneAccount(handle)
                account?.isEnabled == true
            } catch (e: Exception) {
                false
            }
        }
    }
}

class ScamBaitConnection(
    private val context: Context,
    private val callerNumber: String
) : Connection() {

    override fun onAnswer() {
        super.onAnswer()
        setActive()
        Log.i("ScamBaitConnection", "Auto-answered VoIP call from $callerNumber")

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
    }

    override fun onReject() {
        super.onReject()
        setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.REJECTED))
        destroy()
    }
}
