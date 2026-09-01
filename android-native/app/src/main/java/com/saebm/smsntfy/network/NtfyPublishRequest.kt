package com.saebm.smsntfy.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.encodeUtf8

/** Pure builder for ntfy's raw-message publishing API. */
object NtfyPublishRequest {
    private val textMediaType = "text/plain; charset=utf-8".toMediaType()
    private val safeToken = Regex("^[A-Za-z0-9_-]+$")
    private val safeUri = Regex("^[A-Za-z][A-Za-z0-9+.-]*:[^\\r\\n]+$")

    fun build(
        url: String,
        body: String,
        metadata: NtfyPublishMetadata,
        authorization: String? = null
    ): Request {
        val builder = Request.Builder().url(url).post(body.toRequestBody(textMediaType))
        encodedTitle(metadata.title)?.let { builder.header("Title", it) }
        metadata.priority.takeIf { it in 1..5 }?.let { builder.header("Priority", it.toString()) }
        metadata.tags.takeIf { tags -> tags.isNotEmpty() && tags.all(safeToken::matches) }
            ?.let { builder.header("Tags", it.joinToString(",")) }
        metadata.click?.takeIf(::safeUriValue)?.let { builder.header("Click", it) }
        metadata.actions.takeIf { actions -> actions.isNotEmpty() && actions.all(::isSafeAction) }
            ?.let { builder.header("Actions", it.joinToString("; ") { action -> "${action.action}, ${action.label}, ${action.url}" }) }
        authorization?.takeIf(::safeHeaderValue)?.let { builder.header("Authorization", it) }
        return builder.build()
    }

    private fun encodedTitle(value: String): String? {
        if (!safeHeaderValue(value) || value.isBlank()) return null
        if (value.all { it.code in 0x20..0x7e }) return value
        val encoded = value.encodeUtf8().base64()
        return "=?UTF-8?B?$encoded?="
    }

    private fun safeHeaderValue(value: String): Boolean = value.none { it == '\r' || it == '\n' || it.code < 0x20 || it.code == 0x7f }
    private fun safeUriValue(value: String): Boolean = safeHeaderValue(value) && safeUri.matches(value)
    private fun isSafeAction(action: NtfyPublishAction): Boolean =
        safeToken.matches(action.action) && safeHeaderValue(action.label) && action.label.isNotBlank() &&
            ',' !in action.label && ';' !in action.label && safeUriValue(action.url) &&
            ',' !in action.url && ';' !in action.url
}

data class NtfyPublishMetadata(
    val title: String,
    val priority: Int,
    val tags: List<String>,
    val click: String?,
    val actions: List<NtfyPublishAction>
)

data class NtfyPublishAction(val action: String, val label: String, val url: String)
