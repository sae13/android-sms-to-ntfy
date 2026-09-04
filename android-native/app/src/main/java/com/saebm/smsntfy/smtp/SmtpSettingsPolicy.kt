package com.saebm.smsntfy.smtp

enum class SmtpSettingsField { HOST, PORT, USERNAME, PASSWORD, FROM, RECIPIENT }

sealed interface SmtpSettingsValidation {
    data class Valid(val config: SmtpConfig) : SmtpSettingsValidation
    data class Invalid(val field: SmtpSettingsField) : SmtpSettingsValidation
}

data class SmtpConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val from: String,
    val recipient: String
)

object SmtpSettingsPolicy {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun validate(
        requestedEnabled: Boolean,
        host: String,
        port: String,
        username: String,
        password: String,
        from: String,
        recipient: String
    ): SmtpSettingsValidation {
        if (!requestedEnabled) {
            return SmtpSettingsValidation.Valid(
                SmtpConfig(false, "", 0, "", "", "", "")
            )
        }
        val h = host.trim()
        if (h.isEmpty()) return SmtpSettingsValidation.Invalid(SmtpSettingsField.HOST)
        val p = port.trim().toIntOrNull() ?: return SmtpSettingsValidation.Invalid(SmtpSettingsField.PORT)
        if (p < 1 || p > 65535) return SmtpSettingsValidation.Invalid(SmtpSettingsField.PORT)
        if (username.isBlank()) return SmtpSettingsValidation.Invalid(SmtpSettingsField.USERNAME)
        if (password.isEmpty()) return SmtpSettingsValidation.Invalid(SmtpSettingsField.PASSWORD)
        val f = from.trim()
        if (!EMAIL.matches(f)) return SmtpSettingsValidation.Invalid(SmtpSettingsField.FROM)
        val r = recipient.trim()
        if (!EMAIL.matches(r)) return SmtpSettingsValidation.Invalid(SmtpSettingsField.RECIPIENT)
        return SmtpSettingsValidation.Valid(
            SmtpConfig(true, h, p, username.trim(), password, f, r)
        )
    }
}
