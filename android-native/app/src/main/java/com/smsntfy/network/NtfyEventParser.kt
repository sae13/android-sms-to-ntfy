package com.smsntfy.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

sealed class NtfyParseResult {
    data class Success(val data: NtfySseData) : NtfyParseResult()
    data class Malformed(val reason: String) : NtfyParseResult()
}

object NtfyEventParser {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(NtfySseData::class.java)

    fun parseResult(data: String): NtfyParseResult = try {
        val parsed = adapter.fromJson(data)
        if (parsed == null) NtfyParseResult.Malformed("JSON decoded to null")
        else NtfyParseResult.Success(parsed)
    } catch (error: Exception) {
        NtfyParseResult.Malformed(error.message ?: error.javaClass.simpleName)
    }

    fun parse(data: String): NtfySseData? =
        (parseResult(data) as? NtfyParseResult.Success)?.data
}

data class NtfySseData(
    val id: String = "",
    val event: String = "message",
    val message: String = "",
    val title: String = "",
    val priority: Int = 3,
    val tags: List<String> = emptyList()
)
