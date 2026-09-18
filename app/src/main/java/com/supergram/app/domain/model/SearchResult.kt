package com.supergram.app.domain.model

/**
 * A message search result, used by both in-chat search (SearchChatMessages)
 * and global cross-chat search (SearchMessages).
 */
data class SearchResult(
    val messageId: Long,
    val chatId: Long,
    val chatTitle: String,
    val senderName: String?,
    val snippet: String,
    val date: Long,
)

/**
 * All case-insensitive ranges in [text] where [query] occurs.
 * Pure function so text highlighting stays unit-testable.
 */
fun matchRanges(text: String, query: String): List<IntRange> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    val lowerText = text.lowercase()
    val lowerQuery = trimmed.lowercase()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val index = lowerText.indexOf(lowerQuery, from)
        if (index < 0) break
        ranges += index until (index + trimmed.length)
        from = index + trimmed.length
    }
    return ranges
}
