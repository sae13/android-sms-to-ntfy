package com.smsntfy.update

import java.net.URI

data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?
)

data class AvailableUpdate(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String
)

object ReleaseUpdatePolicy {
    fun availableUpdate(currentVersionCode: Int, release: ReleaseInfo?): AvailableUpdate? {
        if (release == null || release.versionCode <= currentVersionCode) return null
        val downloadUrl = release.apkDownloadUrl ?: release.htmlUrl
        return AvailableUpdate(release.versionCode, release.versionName, downloadUrl)
    }

    fun shouldNotify(versionCode: Int, lastNotifiedVersionCode: Int): Boolean =
        versionCode > lastNotifiedVersionCode

    fun notifiedVersionToPersist(versionCode: Int, notificationPosted: Boolean): Int? =
        versionCode.takeIf { notificationPosted }
}

object NotificationDeliveryPolicy {
    fun canPost(
        permissionGranted: Boolean,
        notificationsEnabled: Boolean,
        channelEnabled: Boolean
    ): Boolean = permissionGranted && notificationsEnabled && channelEnabled
}

object ReleaseUrlPolicy {
    private const val OWNER = "sae13"
    private const val REPOSITORY = "android-sms-to-ntfy"

    fun isTrusted(url: String): Boolean = runCatching {
        val uri = URI(url).normalize()
        if (uri.scheme != "https" || uri.host != "github.com") return false
        val segments = uri.path.split('/').filter(String::isNotBlank)
        segments.size >= 2 && segments[0] == OWNER && segments[1] == REPOSITORY
    }.getOrDefault(false)
}

object ReleaseVersionLabel {
    private val semanticVersionPattern = Regex("(\\d+\\.\\d+(?:\\.\\d+)?)")

    fun choose(releaseName: String?, tagName: String, versionCode: Int): String =
        sequenceOf(releaseName, tagName)
            .mapNotNull { label -> label?.let { semanticVersionPattern.find(it)?.value } }
            .firstOrNull()
            ?: "build $versionCode"
}

object GitHubReleaseParser {
    private val versionCodePattern = Regex("versionCode\\s*:\\s*(\\d+)", RegexOption.IGNORE_CASE)

    private val stringFieldPattern = { name: String ->
        Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
    }
    private val booleanFieldPattern = { name: String ->
        Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
    }
    private val assetObjectPattern = Regex(
        "\\{[^{}]*?\\\"name\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"[^{}]*?" +
            "\\\"browser_download_url\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"[^{}]*?}",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        json: String,
        preferredAbis: List<String> = emptyList(),
        commitVersionCode: (String) -> Int? = { null }
    ): ReleaseInfo? = runCatching {
        if (booleanField(json, "draft") || booleanField(json, "prerelease")) return null

        val versionCode = versionCodePattern
            .find(stringField(json, "body").orEmpty())
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: stringField(json, "target_commitish")?.let(commitVersionCode)
            ?: return null
        val tagName = stringField(json, "tag_name").orEmpty()
        val versionLabel = ReleaseVersionLabel.choose(
            releaseName = stringField(json, "name"),
            tagName = tagName,
            versionCode = versionCode
        )
        val htmlUrl = stringField(json, "html_url")?.takeIf(ReleaseUrlPolicy::isTrusted)
            ?: return null
        val apkAssets = assetObjectPattern.findAll(json)
            .map { match ->
                match.groupValues[1].unescapeJson() to match.groupValues[2].unescapeJson()
            }
            .filter { (name, url) ->
                name.endsWith(".apk", ignoreCase = true) &&
                    runCatching { URI(url).path.endsWith(".apk", ignoreCase = true) }.getOrDefault(false) &&
                    ReleaseUrlPolicy.isTrusted(url)
            }
            .toList()
        val apkUrl = preferredAbis.asSequence()
            .mapNotNull { abi ->
                apkAssets.firstOrNull { (name, _) ->
                    name.endsWith("-$abi.apk", ignoreCase = true)
                }
            }
            .firstOrNull()
            ?.second
            ?: apkAssets.singleOrNull()?.second

        ReleaseInfo(versionCode, versionLabel, htmlUrl, apkUrl)
    }.getOrNull()

    private fun stringField(json: String, name: String): String? =
        stringFieldPattern(name).find(json)?.groupValues?.get(1)?.unescapeJson()

    private fun booleanField(json: String, name: String): Boolean =
        booleanFieldPattern(name).find(json)?.groupValues?.get(1)?.equals("true", ignoreCase = true) == true

    private fun String.unescapeJson(): String =
        replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\")
}

object GitHubActionsParser {
    private val androidRunNumberPattern = Regex(
        "\\\"name\\\"\\s*:\\s*\\\"Android Native\\\"[\\s\\S]*?" +
            "\\\"run_number\\\"\\s*:\\s*(\\d+)",
        RegexOption.IGNORE_CASE
    )

    fun androidNativeRunNumber(json: String): Int? = androidRunNumberPattern
        .find(json)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
}