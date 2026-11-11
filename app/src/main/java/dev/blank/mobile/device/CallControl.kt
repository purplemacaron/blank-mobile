package dev.blank.mobile.device

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import dev.blank.mobile.agent.PhoneNumber
import kotlinx.coroutines.delay

class CallControl(private val context: Context) {
    fun dial(number: String): String {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Dialer opened with ${PhoneNumber.mask(number)}"
    }

    fun call(number: String): String {
        context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Calling ${PhoneNumber.mask(number)}"
    }

    fun answer(): String {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return try {
            telecom.acceptRingingCall()
            "Answered ringing call"
        } catch (e: SecurityException) {
            "Permission required: ANSWER_PHONE_CALLS"
        }
    }

    fun state(): String {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return try {
            @Suppress("DEPRECATION")
            stateName(telephony.callState)
        } catch (e: SecurityException) {
            "Permission required: READ_PHONE_STATE"
        }
    }

    suspend fun end(): String {
        if (callState() == TelephonyManager.CALL_STATE_IDLE) return "No active call"
        val dialer = telecom().defaultDialerPackage
        val intent = dialer?.let { context.packageManager.getLaunchIntentForPackage(it) }
        if (intent != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            delay(1200)
            if (DeviceBridge.tapByLabels(END_CALL_LABELS) && waitIdle()) return "Ended call"
        }
        // Answered calls keep the in-call UI in a hidden task; the ongoing-call
        // notification's hang-up action is reachable from any dialer.
        DeviceBridge.globalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        delay(900)
        val tapped = DeviceBridge.tapByLabels(END_CALL_LABELS)
        DeviceBridge.globalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        if (tapped && waitIdle()) return "Ended call"
        return "Could not find the end-call button. Hang up manually."
    }

    private suspend fun waitIdle(): Boolean {
        delay(1200)
        return callState() == TelephonyManager.CALL_STATE_IDLE
    }

    private fun callState(): Int = try {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).callState
    } catch (e: SecurityException) {
        TelephonyManager.CALL_STATE_OFFHOOK
    }

    private fun telecom(): TelecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    companion object {
        val END_CALL_LABELS = listOf("통화 종료", "끊기", "End call", "Hang up", "Hang")

        fun stateName(state: Int): String = when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "idle"
            TelephonyManager.CALL_STATE_RINGING -> "ringing"
            TelephonyManager.CALL_STATE_OFFHOOK -> "offhook"
            else -> "unknown"
        }
    }
}