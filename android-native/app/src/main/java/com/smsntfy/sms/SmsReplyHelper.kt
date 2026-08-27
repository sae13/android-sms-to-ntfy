package com.smsntfy.sms

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper for sending SMS messages (remote replies).
 * Uses SmsManager directly (no Google Play Services).
 */
object SmsReplyHelper {

    private const val TAG = "SmsReplyHelper"

    /**
     * Sends an SMS reply to the given phone number.
     * Handles multipart messages for long texts.
     *
     * @return true if the SMS was sent successfully
     */
    suspend fun sendSmsReply(context: Context, phoneNumber: String, message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (phoneNumber.isEmpty() || message.isEmpty()) {
                    Log.w(TAG, "Empty phone number or message")
                    return@withContext false
                }

                val smsManager = try {
                    SmsManager.getDefault()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get SmsManager", e)
                    return@withContext false
                }

                // Split long messages
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                }

                Log.d(TAG, "SMS reply sent to $phoneNumber: ${message.take(50)}...")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS reply", e)
                false
            }
        }
    }

    /**
     * Validates a phone number format.
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return phoneNumber.matches(Regex("^[+]?[0-9\\s\\-()]{6,20}\$"))
    }
}