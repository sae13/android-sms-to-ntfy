package com.smsntfy.ui

import android.Manifest
import android.os.Build

object PermissionPolicy {
    fun shouldRequestOnLaunch(
        initialRequestCompleted: Boolean,
        hasMissingPermissions: Boolean
    ): Boolean = !initialRequestCompleted && hasMissingPermissions

    fun requiredPermissions(sdkInt: Int): List<String> {
        if (sdkInt < Build.VERSION_CODES.M) return emptyList()

        return buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun missingPermissions(
        sdkInt: Int,
        isGranted: (String) -> Boolean
    ): List<String> = requiredPermissions(sdkInt).filterNot(isGranted)

    fun hasAllRequiredPermissions(
        sdkInt: Int,
        isGranted: (String) -> Boolean
    ): Boolean = missingPermissions(sdkInt, isGranted).isEmpty()
}
