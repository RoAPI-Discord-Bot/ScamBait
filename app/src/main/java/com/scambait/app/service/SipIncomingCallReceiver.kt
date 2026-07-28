package com.scambait.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.sip.SipAudioCall
import android.net.sip.SipManager as AndroidSipManager
import android.util.Log

/**
 * Receives the PendingIntent broadcast fired by Android's SIP stack
 * when an incoming SIP call arrives on the registered profile.
 * Passes it to ScamTrapService to auto-answer silently.
 */
class SipIncomingCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i("SipIncomingCallReceiver", "Incoming SIP call broadcast received!")

        try {
            val androidSipManager = AndroidSipManager.newInstance(context)

            // Take the incoming call from the intent
            val incomingCall: SipAudioCall = androidSipManager.takeAudioCall(intent, null)

            // Route to ScamTrapService via a local broadcast so the service handles audio
            val serviceIntent = Intent(context, ScamTrapService::class.java).apply {
                action = ScamTrapService.ACTION_SIP_INCOMING_CALL
                // We pass the call via a static holder since SipAudioCall isn't Parcelable
                ScamTrapService.pendingIncomingSipCall = incomingCall
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

        } catch (e: Exception) {
            Log.e("SipIncomingCallReceiver", "Error taking incoming SIP call", e)
        }
    }
}
