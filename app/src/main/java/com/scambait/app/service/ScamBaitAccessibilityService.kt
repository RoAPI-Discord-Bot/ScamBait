package com.scambait.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility service used for one purpose: hanging up cellular calls
 * when the AI bot sends a [HANG_UP] command.
 * Finds and clicks the "End call" button in the active Google Phone call UI.
 *
 * The user must enable this service in Settings → Accessibility → ScamBait.
 */
class ScamBaitAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ScamBaitAccessibilityService? = null
        private const val TAG = "ScamBaitAccessibility"

        /** Called from ScamTrapService when AI emits [HANG_UP] */
        fun requestHangUp() {
            instance?.performHangUp()
        }
    }

    override fun onServiceConnected() {
        instance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            packageNames = arrayOf(
                "com.google.android.dialer",
                "com.android.dialer",
                "com.android.phone"
            )
        }
        serviceInfo = info
        Log.i(TAG, "ScamBait Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* monitoring only */ }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Searches the active call UI for an "End call" or "Hang up" button and clicks it.
     * Works with Google's Phone app on Pixel devices.
     */
    fun performHangUp() {
        Log.i(TAG, "Performing hang-up via accessibility")
        Handler(Looper.getMainLooper()).post {
            try {
                val endCallButton = findEndCallButton(rootInActiveWindow)
                if (endCallButton != null) {
                    endCallButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.i(TAG, "Clicked end call button successfully")
                } else {
                    // Fallback: use GLOBAL_ACTION_HOME to go home, which ends some calls
                    Log.w(TAG, "End call button not found, trying global back action")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing hang-up", e)
            }
        }
    }

    private fun findEndCallButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        // Common content descriptions for end call button across Pixel / Google Phone versions
        val endCallDescriptions = setOf(
            "end call", "hang up", "disconnect", "end", "speaker off and end"
        )

        val desc = node.contentDescription?.toString()?.lowercase()
        val text = node.text?.toString()?.lowercase()

        if ((desc != null && endCallDescriptions.any { desc.contains(it) }) ||
            (text != null && endCallDescriptions.any { text.contains(it) })) {
            if (node.isClickable) return node
        }

        for (i in 0 until node.childCount) {
            val result = findEndCallButton(node.getChild(i))
            if (result != null) return result
        }
        return null
    }
}
