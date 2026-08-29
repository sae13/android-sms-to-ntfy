package com.smsntfy.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUpdatePolicyTest {

    @Test
    fun newerVersionSelectsApkDownload() {
        val release = ReleaseInfo(
            versionCode = 2,
            versionName = "1.1.0",
            htmlUrl = "https://github.com/sae13/android-sms-to-ntfy/releases/tag/v1.1.0",
            apkDownloadUrl = "https://github.com/sae13/android-sms-to-ntfy/releases/download/v1.1.0/app.apk"
        )

        val update = ReleaseUpdatePolicy.availableUpdate(currentVersionCode = 1, release = release)

        assertEquals("1.1.0", update?.versionName)
        assertEquals(release.apkDownloadUrl, update?.downloadUrl)
    }

    @Test
    fun currentOrOlderVersionDoesNotNotify() {
        assertNull(
            ReleaseUpdatePolicy.availableUpdate(
                currentVersionCode = 2,
                release = ReleaseInfo(2, "1.1.0", "https://example.com/release", "https://example.com/app.apk")
            )
        )
        assertNull(
            ReleaseUpdatePolicy.availableUpdate(
                currentVersionCode = 2,
                release = ReleaseInfo(1, "1.0.0", "https://example.com/release", "https://example.com/app.apk")
            )
        )
    }

    @Test
    fun missingApkFallsBackToReleasePage() {
        val release = ReleaseInfo(
            versionCode = 3,
            versionName = "2.0.0",
            htmlUrl = "https://github.com/sae13/android-sms-to-ntfy/releases/latest",
            apkDownloadUrl = null
        )

        val update = ReleaseUpdatePolicy.availableUpdate(1, release)

        assertEquals(release.htmlUrl, update?.downloadUrl)
    }

    @Test
    fun githubReleasePayloadIsParsedWithoutExecutingContent() {
        val json = """
            {
              "tag_name": "android-native-abc123",
              "name": "Android Native 1.4.0",
              "target_commitish": "abc123",
              "html_url": "https://github.com/sae13/android-sms-to-ntfy/releases/tag/android-native-abc123",
              "body": "Release notes <!-- versionCode: 14 -->",
              "draft": false,
              "prerelease": false,
              "assets": [{
                "name": "sms-ntfy-android-native-release.apk",
                "browser_download_url": "https://github.com/sae13/android-sms-to-ntfy/releases/download/android-native-abc123/app.apk"
              }]
            }
        """.trimIndent()

        val release = GitHubReleaseParser.parse(json)

        assertEquals(14, release?.versionCode)
        assertEquals("1.4.0", release?.versionName)
        assertEquals("https://github.com/sae13/android-sms-to-ntfy/releases/download/android-native-abc123/app.apk", release?.apkDownloadUrl)
    }

    @Test
    fun draftPrereleaseOrMissingVersionMetadataIsIgnored() {
        val draft = """{"tag_name":"v2","name":"2.0","html_url":"https://example.com","body":"versionCode: 2","draft":true,"prerelease":false,"assets":[]}"""
        val prerelease = """{"tag_name":"v2","name":"2.0","html_url":"https://example.com","body":"versionCode: 2","draft":false,"prerelease":true,"assets":[]}"""
        val missingCode = """{"tag_name":"v2","name":"2.0","html_url":"https://example.com","body":"notes","draft":false,"prerelease":false,"assets":[]}"""

        assertNull(GitHubReleaseParser.parse(draft))
        assertNull(GitHubReleaseParser.parse(prerelease))
        assertNull(GitHubReleaseParser.parse(missingCode))
    }

    @Test
    fun duplicateVersionNotificationIsSuppressed() {
        assertTrue(ReleaseUpdatePolicy.shouldNotify(versionCode = 4, lastNotifiedVersionCode = 3))
        assertFalse(ReleaseUpdatePolicy.shouldNotify(versionCode = 4, lastNotifiedVersionCode = 4))
        assertFalse(ReleaseUpdatePolicy.shouldNotify(versionCode = 4, lastNotifiedVersionCode = 5))
    }

    @Test
    fun notificationStateAdvancesOnlyAfterNotificationWasPosted() {
        assertEquals(3, ReleaseUpdatePolicy.notifiedVersionToPersist(3, notificationPosted = true))
        assertNull(ReleaseUpdatePolicy.notifiedVersionToPersist(3, notificationPosted = false))
    }

    @Test
    fun notificationIsDeliverableOnlyWhenEveryNotificationGateIsOpen() {
        assertTrue(
            NotificationDeliveryPolicy.canPost(
                permissionGranted = true,
                notificationsEnabled = true,
                channelEnabled = true
            )
        )
        assertFalse(NotificationDeliveryPolicy.canPost(false, true, true))
        assertFalse(NotificationDeliveryPolicy.canPost(true, false, true))
        assertFalse(NotificationDeliveryPolicy.canPost(true, true, false))
    }

    @Test
    fun commitTargetCanProvideMonotonicVersionCode() {
        val json = """{
          "tag_name":"android-native-abc123",
          "name":"Android Native latest",
          "target_commitish":"abc1234",
          "html_url":"https://github.com/sae13/android-sms-to-ntfy/releases/tag/android-native-abc123",
          "body":"Installable build",
          "draft":false,
          "prerelease":false,
          "assets":[]
        }""".trimIndent()

        val release = GitHubReleaseParser.parse(json) { commit ->
            if (commit == "abc1234") 27 else null
        }

        assertEquals(27, release?.versionCode)
    }

    @Test
    fun actionsResponseUsesAndroidWorkflowRunNumber() {
        val json = """{
          "workflow_runs":[
            {"name":"Other","run_number":99},
            {"name":"Android Native","run_number":4}
          ]
        }""".trimIndent()

        assertEquals(4, GitHubActionsParser.androidNativeRunNumber(json))
    }

    @Test
    fun githubUrlsMustMatchTheConfiguredRepository() {
        assertTrue(ReleaseUrlPolicy.isTrusted("https://github.com/sae13/android-sms-to-ntfy/releases/latest"))
        assertFalse(ReleaseUrlPolicy.isTrusted("https://github.com.evil.example/sae13/android-sms-to-ntfy/app.apk"))
        assertFalse(ReleaseUrlPolicy.isTrusted("https://github.com/other/repository/app.apk"))
        assertFalse(
            ReleaseUrlPolicy.isTrusted(
                "https://github.com/sae13/android-sms-to-ntfy/../../evil/malware.apk"
            )
        )
    }

    @Test
    fun apkAssetUrlMustBelongToTheNamedApkAsset() {
        val json = """{
          "tag_name":"android-native-abc123",
          "name":"Android Native 1.4.0",
          "target_commitish":"abc123",
          "html_url":"https://github.com/sae13/android-sms-to-ntfy/releases/tag/android-native-abc123",
          "body":"versionCode: 14",
          "draft":false,
          "prerelease":false,
          "assets":[
            {
              "name":"sms-ntfy-android-native-release.apk",
              "browser_download_url":"https://github.com/sae13/android-sms-to-ntfy/releases/download/android-native-abc123/notes.txt"
            }
          ]
        }""".trimIndent()

        val release = GitHubReleaseParser.parse(json)

        assertNull(release?.apkDownloadUrl)
    }

    @Test
    fun commitStyleReleaseNameUsesReadableVersionLabel() {
        assertEquals(
            "build 4",
            ReleaseVersionLabel.choose(
                releaseName = "Android Native latest",
                tagName = "android-native-a952f7df0014179b9cc587d5be99402082400d1c",
                versionCode = 4
            )
        )
    }
}