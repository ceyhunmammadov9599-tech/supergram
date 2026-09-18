package com.supergram.app.domain.usecase

import com.supergram.app.domain.model.MediaFile

/**
 * Pure, stateful controller deciding what a voice-note toggle should do and
 * when a pending download finished so playback can start automatically.
 *
 * Kept free of Android/TDLib dependencies so it is fully unit-testable.
 */
class VoiceAutoPlayController {

    /** What the UI should do when the user taps a voice bubble. */
    sealed interface Action {
        /** The file is ready locally — play it. */
        data class Play(val path: String) : Action

        /** The file is not downloaded yet — start downloading it. */
        data class Download(val fileId: Int) : Action
    }

    private var pendingFileId: Int? = null

    /** Called on a user tap. Returns the action to perform. */
    fun onToggle(media: MediaFile): Action {
        val path = media.localPath
        return if (media.isDownloaded && path != null) {
            Action.Play(path)
        } else {
            pendingFileId = media.fileId
            Action.Download(media.fileId)
        }
    }

    /**
     * Called on every file download state change.
     * Returns the local path when the pending file just finished downloading
     * (and clears the pending state), otherwise null.
     */
    fun onFileUpdated(state: MediaFile): String? {
        val pending = pendingFileId ?: return null
        if (state.fileId != pending) return null
        val path = state.localPath
        if (state.isDownloaded && path != null) {
            pendingFileId = null
            return path
        }
        return null
    }

    /** Clears any pending auto-play request. */
    fun cancel() {
        pendingFileId = null
    }
}
