package com.supergram.app.domain.model

/** Kind of media attached to a message. */
enum class MediaKind { PHOTO, DOCUMENT, VOICE }

/**
 * Domain representation of a downloadable TDLib file (photo or document),
 * including its current local download state.
 */
data class MediaFile(
    val kind: MediaKind,
    val fileId: Int,
    val fileName: String?,
    val expectedSize: Long,
    val downloadedSize: Long,
    val isDownloadingActive: Boolean,
    val isDownloaded: Boolean,
    /** Local path once the download is complete (or a partial prefix exists). */
    val localPath: String?,
    /** Voice note duration in seconds (null for photos/documents). */
    val durationSeconds: Int? = null,
) {
    /** 0f..1f progress estimate for the UI. */
    val progress: Float
        get() = when {
            isDownloaded -> 1f
            expectedSize > 0 -> (downloadedSize.toFloat() / expectedSize).coerceIn(0f, 1f)
            isDownloadingActive -> 0.1f
            else -> 0f
        }
}
