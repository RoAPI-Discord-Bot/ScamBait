package com.scambait.app.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * Registered as the system Call Screening role.
 * Intercepts every incoming call, checks if it's from a contact,
 * and for unknown/spam callers, triggers the ScamBait bot to auto-answer.
 */
class ScamBaitCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val callerNumber = callDetails.handle?.schemeSpecificPart ?: ""
        val isSuspectedSpam = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            callDetails.callerNumberVerificationStatus == 2 || callerNumber.isEmpty() // 2 = CALLER_NUMBER_VERIFICATION_STATUS_FAILED
        } else {
            callerNumber.isEmpty()
        }

        Log.i("ScamBaitCallScreening", "Screening call from: $callerNumber | spam=$isSuspectedSpam")

        // Always let the call through (don't reject here — ScamTrapService handles it)
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setSilenceCall(false)
            .build()

        respondToCall(callDetails, response)

        // Notify ScamTrapService so it can auto-answer and engage the bot
        val intent = android.content.Intent(this, ScamTrapService::class.java).apply {
            action = ScamTrapService.ACTION_SCREEN_CALL
            putExtra(ScamTrapService.EXTRA_CALLER_NUMBER, callerNumber)
            putExtra(ScamTrapService.EXTRA_IS_SPAM, isSuspectedSpam)
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("ScamBaitCallScreening", "Failed to start ScamTrapService: ${e.message}")
        }
    }
}
