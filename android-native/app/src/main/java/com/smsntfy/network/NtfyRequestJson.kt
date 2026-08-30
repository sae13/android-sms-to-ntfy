package com.smsntfy.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** JSON wire serializer kept as a pure seam so newline semantics are testable. */
object NtfyRequestJson {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(NtfyMessage::class.java)

    fun encode(message: NtfyMessage): String = adapter.toJson(message)
    fun decode(json: String): NtfyMessage? = adapter.fromJson(json)
}

data class NtfyMessage(
    val topic: String,
    val message: String,
    val title: String,
    val priority: Int,
    val tags: Array<String>,
    val click: String?,
    val email: String?,
    val actions: Array<NtfyAction>?
) {
    override fun equals(other: Any?): Boolean = other is NtfyMessage &&
        topic == other.topic && message == other.message && title == other.title &&
        priority == other.priority && tags.contentEquals(other.tags) && click == other.click &&
        email == other.email && nullableArrayEquals(actions, other.actions)

    override fun hashCode(): Int = 31 * message.hashCode() + tags.contentHashCode()

    private fun nullableArrayEquals(left: Array<NtfyAction>?, right: Array<NtfyAction>?): Boolean =
        if (left == null || right == null) left === right else left.contentEquals(right)
}

data class NtfyAction(val action: String, val label: String, val url: String)
