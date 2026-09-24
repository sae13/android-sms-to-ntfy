package com.saebm.smsntfy.update

import com.saebm.smsntfy.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReleaseVersionRegressionTest {
    @Test
    fun `version 19 release is visible to an installed version 18`() {
        assertEquals(19, BuildConfig.VERSION_CODE)
        assertEquals("1.0.19", BuildConfig.VERSION_NAME)

        val update = ReleaseUpdatePolicy.availableUpdate(
            currentVersionCode = 18,
            release = ReleaseInfo(
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                htmlUrl = "https://github.com/sae13/android-sms-to-ntfy/releases/latest",
                apkDownloadUrl = "https://github.com/sae13/android-sms-to-ntfy/releases/download/example/sms-ntfy-android-native-arm64-v8a.apk"
            )
        )

        assertNotNull(update)
    }
}
