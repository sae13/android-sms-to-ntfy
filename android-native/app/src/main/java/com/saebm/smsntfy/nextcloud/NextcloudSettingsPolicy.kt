package com.saebm.smsntfy.nextcloud

enum class NextcloudSettingsField { SERVER_URL, USERNAME, APP_PASSWORD, TALK_TOKEN }

sealed interface NextcloudSettingsValidation {
    data class Valid(val config: NextcloudConfig) : NextcloudSettingsValidation
    data class Invalid(val field: NextcloudSettingsField) : NextcloudSettingsValidation
}

data class NextcloudConfig(
    val enabled: Boolean,
    val serverUrl: String,
    val username: String,
    val appPassword: String,
    val talkToken: String
)

object NextcloudSettingsPolicy {
    fun validate(
        requestedEnabled: Boolean,
        serverUrl: String,
        username: String,
        appPassword: String,
        talkToken: String
    ): NextcloudSettingsValidation {
        if (!requestedEnabled) {
            return NextcloudSettingsValidation.Valid(NextcloudConfig(false, "", "", "", ""))
        }
        val url = serverUrl.trim().trimEnd('/')
        if (!url.startsWith("https://")) {
            return NextcloudSettingsValidation.Invalid(NextcloudSettingsField.SERVER_URL)
        }
        if (username.isBlank()) return NextcloudSettingsValidation.Invalid(NextcloudSettingsField.USERNAME)
        if (appPassword.isEmpty()) return NextcloudSettingsValidation.Invalid(NextcloudSettingsField.APP_PASSWORD)
        val token = talkToken.trim()
        if (!Regex("^[A-Za-z0-9]{4,}$").matches(token)) {
            return NextcloudSettingsValidation.Invalid(NextcloudSettingsField.TALK_TOKEN)
        }
        return NextcloudSettingsValidation.Valid(
            NextcloudConfig(true, url, username.trim(), appPassword, token)
        )
    }
}
