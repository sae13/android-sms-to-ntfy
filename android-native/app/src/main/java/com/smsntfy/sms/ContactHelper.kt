package com.smsntfy.sms

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

/**
 * Helper for looking up contact names from phone numbers.
 * Uses ContactsContract (no Google Play Services Contacts API).
 */
object ContactHelper {

    private const val TAG = "ContactHelper"

    /**
     * Looks up a contact name for a given phone number.
     * Returns the phone number itself if no contact is found.
     */
    fun getContactName(context: Context, phoneNumber: String): String {
        if (phoneNumber.isEmpty()) return phoneNumber

        val uri: Uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )

        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        return try {
            val cursor: Cursor? = context.contentResolver.query(
                uri, projection, null, null, null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        it.getString(nameIndex) ?: phoneNumber
                    } else {
                        phoneNumber
                    }
                } else {
                    phoneNumber
                }
            } ?: phoneNumber
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup contact for $phoneNumber", e)
            phoneNumber
        }
    }

    /**
     * Checks if a contact exists for the given number.
     */
    fun hasContact(context: Context, phoneNumber: String): Boolean {
        return getContactName(context, phoneNumber) != phoneNumber
    }
}