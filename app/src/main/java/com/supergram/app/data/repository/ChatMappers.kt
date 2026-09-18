package com.supergram.app.data.repository

import com.supergram.app.domain.model.ChatCategory
import com.supergram.app.domain.model.SearchResult
import org.drinkless.tdlib.TdApi

/**
 * Pure chat categorization used by the chat list filter tabs.
 *
 * Bot detection is passed in as a flag because resolving a bot account
 * requires a suspend TDLib request (GetUser), which stays in the repository.
 */
object ChatCategoryResolver {

    fun resolve(chatType: TdApi.ChatType?, isBot: Boolean = false): ChatCategory =
        when (chatType) {
            is TdApi.ChatTypePrivate -> if (isBot) ChatCategory.BOT else ChatCategory.DIRECT
            is TdApi.ChatTypeSecret -> ChatCategory.DIRECT
            is TdApi.ChatTypeBasicGroup -> ChatCategory.GROUP
            is TdApi.ChatTypeSupergroup ->
                if (chatType.isChannel) ChatCategory.CHANNEL else ChatCategory.GROUP
            else -> ChatCategory.DIRECT
        }
}

/**
 * Maps a TDLib message into the domain [SearchResult] model.
 * Pure mapper: chat title and sender name are resolved by the caller
 * (they require suspend TDLib requests).
 */
fun TdApi.Message.toSearchResult(chatTitle: String, senderName: String?): SearchResult {
    val snippet = (content as? TdApi.MessageText)?.text?.text ?: content.toSnippet()
    return SearchResult(
        messageId = id,
        chatId = chatId,
        chatTitle = chatTitle,
        senderName = senderName,
        snippet = snippet,
        date = date.toLong(),
    )
}

/**
 * Decodes the raw TDLib voice waveform (5-bit samples, bit-packed
 * little-endian) into normalized amplitudes, bucketed to at most
 * [maxSamples] bars.
 */
fun decodeWaveform(bytes: ByteArray?, maxSamples: Int = 64): List<Float>? {
    if (bytes == null || bytes.isEmpty()) return null

    val sampleCount = bytes.size * 8 / 5
    if (sampleCount <= 0) return null

    // Unpack 5-bit samples.
    val samples = IntArray(sampleCount)
    var bitPos = 0
    for (i in 0 until sampleCount) {
        var value = 0
        for (b in 0 until 5) {
            val byteIndex = (bitPos + b) / 8
            val bitIndex = (bitPos + b) % 8
            val bit = (bytes[byteIndex].toInt() shr bitIndex) and 1
            value = value or (bit shl b)
        }
        samples[i] = value
        bitPos += 5
    }

    // Bucket to at most maxSamples bars (max amplitude per bucket).
    val bucketCount = minOf(sampleCount, maxSamples).coerceAtLeast(1)
    val bucketSize = sampleCount / bucketCount
    val result = FloatArray(bucketCount)
    for (bucket in 0 until bucketCount) {
        var max = 0
        val start = bucket * bucketSize
        val end = if (bucket == bucketCount - 1) sampleCount else start + bucketSize
        for (i in start until end) {
            if (samples[i] > max) max = samples[i]
        }
        // 5-bit values are 0..31; keep a visible minimum bar height.
        result[bucket] = (max / 31f).coerceIn(0.12f, 1f)
    }
    return result.toList()
}
