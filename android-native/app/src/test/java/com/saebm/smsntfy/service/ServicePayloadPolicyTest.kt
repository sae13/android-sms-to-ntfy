package com.saebm.smsntfy.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicePayloadPolicyTest {

    @Test
    fun singlePartCreatesOneCompleteSms() {
        val payloads = SmsPayload.fromParts(
            senders = arrayOf("sender-a"),
            bodies = arrayOf("پیام تکبخشی"),
            timestamps = longArrayOf(1_725_000_000_000L)
        )

        assertEquals(
            listOf(SmsPayload("sender-a", "پیام تکبخشی", 1_725_000_000_000L)),
            payloads
        )
    }

    @Test
    fun compatibleMultipartSmsIsMergedInPartOrderAndSentOnce() {
        val payloads = SmsPayload.fromParts(
            senders = arrayOf("sender-a", "sender-a", "sender-a"),
            bodies = arrayOf("بخش اول ", "بخش دوم ", "بخش سوم"),
            timestamps = longArrayOf(1_725_000_000_000L, 1_725_000_000_100L, 1_725_000_000_200L)
        )

        assertEquals(
            listOf(
                SmsPayload(
                    sender = "sender-a",
                    body = "بخش اول بخش دوم بخش سوم",
                    timestamp = 1_725_000_000_000L
                )
            ),
            payloads
        )
    }

    @Test
    fun incompatiblePartsAreProcessedSeparatelyWithoutLosingTheirValues() {
        val payloads = SmsPayload.fromParts(
            senders = arrayOf("111", "222", "111"),
            bodies = arrayOf("one", "two", "three"),
            timestamps = longArrayOf(10L, 20L, 30L)
        )

        assertEquals(
            listOf(
                SmsPayload("111", "one", 10L),
                SmsPayload("222", "two", 20L),
                SmsPayload("111", "three", 30L)
            ),
            payloads
        )
    }

    @Test
    fun emptyOrMismatchedRawArraysAreRejected() {
        assertTrue(SmsPayload.fromParts(emptyArray(), emptyArray(), longArrayOf()).isEmpty())
        assertTrue(
            SmsPayload.fromParts(
                senders = arrayOf("111"),
                bodies = arrayOf("one", "orphan"),
                timestamps = longArrayOf(10L)
            ).isEmpty()
        )
        assertTrue(
            SmsPayload.fromParts(
                senders = arrayOf(""),
                bodies = arrayOf("one"),
                timestamps = longArrayOf(10L)
            ).isEmpty()
        )
    }

    @Test
    fun malformedPartDoesNotDiscardValidParts() {
        val payloads = SmsPayload.fromParts(
            senders = arrayOf("111", "", "222"),
            bodies = arrayOf("one", "invalid", "two"),
            timestamps = longArrayOf(10L, 20L, 30L)
        )

        assertEquals(
            listOf(
                SmsPayload("111", "one", 10L),
                SmsPayload("222", "two", 30L)
            ),
            payloads
        )
    }

    @Test
    fun sameSenderPartsOutsideMultipartWindowStaySeparate() {
        val payloads = SmsPayload.fromParts(
            senders = arrayOf("111", "111"),
            bodies = arrayOf("first", "second"),
            timestamps = longArrayOf(10L, 20_000L)
        )

        assertEquals(
            listOf(
                SmsPayload("111", "first", 10L),
                SmsPayload("111", "second", 20_000L)
            ),
            payloads
        )
    }

    @Test
    fun nonAdjacentPartsFromSameSenderStaySeparate() {
        val payloads = SmsPayload.fromParts(
            senders = arrayOf("111", "222", "111"),
            bodies = arrayOf("first", "other", "second"),
            timestamps = longArrayOf(10L, 11L, 12L)
        )

        assertEquals(
            listOf(
                SmsPayload("111", "first", 10L),
                SmsPayload("222", "other", 11L),
                SmsPayload("111", "second", 12L)
            ),
            payloads
        )
    }

    @Test
    fun payloadUsesOnlySimpleValueTypes() {
        val declaredTypes = SmsPayload::class.java.declaredFields.map { it.type }.toSet()

        assertTrue(declaredTypes.contains(String::class.java))
        assertTrue(declaredTypes.contains(Long::class.javaPrimitiveType))
        assertFalse(declaredTypes.any { it.name.startsWith("android.telephony") })
        assertFalse(SmsPayload::class.java.interfaces.any { it.name == "java.io.Serializable" })
        assertFalse(SmsPayload::class.java.interfaces.any { it.name == "android.os.Parcelable" })
    }

    @Test
    fun receiverStartedSmsAndCallActionsRequireImmediateForeground() {
        assertTrue(ServiceStartPolicy.requiresImmediateForeground(SmsForwardingService.ACTION_PROCESS_SMS))
        assertTrue(ServiceStartPolicy.requiresImmediateForeground(SmsForwardingService.ACTION_PROCESS_CALL))
    }

    @Test
    fun stopActionDoesNotRequireImmediateForeground() {
        assertFalse(ServiceStartPolicy.requiresImmediateForeground(SmsForwardingService.ACTION_STOP_SERVICE))
    }

    @Test
    fun typedForegroundOverloadRequiresAndroidTen() {
        assertFalse(ServiceStartPolicy.supportsTypedForeground(sdkInt = 28))
        assertTrue(ServiceStartPolicy.supportsTypedForeground(sdkInt = 29))
    }

    @Test
    fun foregroundTypesStayCompatibleAcrossAndroidVersions() {
        assertEquals(
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            ServiceStartPolicy.foregroundServiceTypes(sdkInt = 33)
        )
        assertEquals(
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            ServiceStartPolicy.foregroundServiceTypes(sdkInt = 34)
        )
    }

    @Test
    fun typedForegroundInvocationRunsOnlyOnAndroidTenAndNewer() {
        var invocations = 0

        ServiceStartPolicy.runTypedForegroundIfSupported(sdkInt = 28) { invocations++ }
        assertEquals(0, invocations)

        ServiceStartPolicy.runTypedForegroundIfSupported(sdkInt = 29) { invocations++ }
        assertEquals(1, invocations)
    }

    @Test
    fun stopActionSkipsDependencyInitialization() {
        assertFalse(ServiceStartPolicy.requiresDependencies(SmsForwardingService.ACTION_STOP_SERVICE))
        assertTrue(ServiceStartPolicy.requiresDependencies(SmsForwardingService.ACTION_START_SERVICE))
        assertTrue(ServiceStartPolicy.requiresDependencies(SmsForwardingService.ACTION_PROCESS_SMS))
    }

    @Test
    fun coldStopClearsPersistedRunningStateWithoutDependencies() {
        assertEquals(
            false,
            ServiceStartPolicy.persistedRunningStateBeforeDependencies(
                SmsForwardingService.ACTION_STOP_SERVICE
            )
        )
        assertNull(
            ServiceStartPolicy.persistedRunningStateBeforeDependencies(
                SmsForwardingService.ACTION_START_SERVICE
            )
        )
    }

    @Test
    fun restartWithoutIntentOnlyResumesPersistentService() {
        assertEquals(
            SmsForwardingService.ACTION_START_SERVICE,
            ServiceStartPolicy.actionForRestart(isPersistent = true)
        )
        assertEquals(null, ServiceStartPolicy.actionForRestart(isPersistent = false))
    }

    @Test
    fun eventCompletionDoesNotStopPersistentService() {
        assertFalse(ServiceStartPolicy.shouldStopAfterEvent(isPersistent = true))
        assertTrue(ServiceStartPolicy.shouldStopAfterEvent(isPersistent = false))
    }

    @Test
    fun oneShotCompletionStopsOnlyItsOwnStartRequest() {
        assertEquals(42, ServiceStartPolicy.startIdToStopAfterOneShot(false, 42))
        assertEquals(null, ServiceStartPolicy.startIdToStopAfterOneShot(true, 42))
    }

    @Test
    fun concurrentOneShotStartsStopOnlyAfterEveryJobCompletes() {
        val tracker = OneShotStartTracker()
        tracker.register(41)
        tracker.register(42)

        assertEquals(null, tracker.complete(42, isPersistent = false))
        assertEquals(42, tracker.complete(41, isPersistent = false))
    }

    @Test
    fun failedOneShotStartDoesNotKeepLaterWorkAlive() {
        val tracker = OneShotStartTracker()
        tracker.register(41)
        tracker.abandon(41)
        tracker.register(42)

        assertEquals(42, tracker.complete(42, isPersistent = false))
        assertFalse(tracker.hasActiveStarts())
    }

    @Test
    fun persistentServicePreventsOneShotCompletionFromStoppingIt() {
        val tracker = OneShotStartTracker()
        tracker.register(42)

        assertEquals(null, tracker.complete(42, isPersistent = true))
    }

    @Test
    fun unknownActionsAreRejectedWithoutProcessing() {
        assertFalse(ServiceStartPolicy.isKnown("UNKNOWN"))
        assertTrue(ServiceStartPolicy.isKnown(SmsForwardingService.ACTION_PROCESS_SMS))
        assertTrue(ServiceStartPolicy.isKnown(SmsForwardingService.ACTION_PROCESS_CALL))
    }

    @Test
    fun rejectedActionDoesNotStopAnExistingPersistentService() {
        assertFalse(
            ServiceStartPolicy.shouldStopRejectedStart(
                isPersistent = true,
                hasActiveOneShots = false
            )
        )
        assertFalse(
            ServiceStartPolicy.shouldStopRejectedStart(
                isPersistent = false,
                hasActiveOneShots = true
            )
        )
        assertTrue(
            ServiceStartPolicy.shouldStopRejectedStart(
                isPersistent = false,
                hasActiveOneShots = false
            )
        )
    }
}