package com.smsntfy.ui

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {

    @Test
    fun freshInstallWithMissingPermissionsRequestsOnLaunch() {
        assertTrue(
            PermissionPolicy.shouldRequestOnLaunch(
                initialRequestCompleted = false,
                hasMissingPermissions = true
            )
        )
    }

    @Test
    fun completedInitialRequestIsNotRepeatedOnLaunch() {
        assertFalse(
            PermissionPolicy.shouldRequestOnLaunch(
                initialRequestCompleted = true,
                hasMissingPermissions = true
            )
        )
    }

    @Test
    fun androidBeforeMarshmallowNeedsNoRuntimePermissions() {
        assertTrue(PermissionPolicy.requiredPermissions(22).isEmpty())
    }

    @Test
    fun marshmallowRequiresOnlyFeaturePermissions() {
        assertEquals(
            listOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS
            ),
            PermissionPolicy.requiredPermissions(23)
        )
    }

    @Test
    fun readingSmsHistoryIsNeverRequested() {
        assertFalse(PermissionPolicy.requiredPermissions(35).contains(Manifest.permission.READ_SMS))
    }

    @Test
    fun marshmallowRequiresCallLogAccessForIncomingNumber() {
        assertTrue(PermissionPolicy.requiredPermissions(23).contains(Manifest.permission.READ_CALL_LOG))
    }

    @Test
    fun androidTwelveDoesNotRequestNotificationPermission() {
        assertFalse(
            PermissionPolicy.requiredPermissions(32)
                .contains(Manifest.permission.POST_NOTIFICATIONS)
        )
    }

    @Test
    fun androidThirteenAlsoRequiresNotificationPermission() {
        assertTrue(
            PermissionPolicy.requiredPermissions(33)
                .contains(Manifest.permission.POST_NOTIFICATIONS)
        )
    }

    @Test
    fun deniedRequiredPermissionKeepsServiceGateClosed() {
        val grantedPermissions = PermissionPolicy.requiredPermissions(33)
            .filterNot { it == Manifest.permission.READ_PHONE_STATE }
            .toSet()

        assertEquals(
            listOf(Manifest.permission.READ_PHONE_STATE),
            PermissionPolicy.missingPermissions(33) { it in grantedPermissions }
        )
        assertFalse(PermissionPolicy.hasAllRequiredPermissions(33) { it in grantedPermissions })
    }

    @Test
    fun serviceGateOpensAfterEveryRequiredPermissionIsGranted() {
        val grantedPermissions = PermissionPolicy.requiredPermissions(33).toSet()

        assertTrue(PermissionPolicy.hasAllRequiredPermissions(33) { it in grantedPermissions })
    }
}
