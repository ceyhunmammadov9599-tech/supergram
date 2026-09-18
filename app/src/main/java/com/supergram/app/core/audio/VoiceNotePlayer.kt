package com.supergram.app.core.audio

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lightweight voice note playback engine built on Android's native
 * [MediaPlayer] (supports local OGG/Opus .oga files produced by TDLib).
 *
 * Exposes a single [state] stream with the active file id, play/pause flag,
 * and playback position so Compose bubbles can render progress reactively.
 */
class VoiceNotePlayer {

    /** Snapshot of the current (or last) playback session. */
    data class PlaybackState(
        val fileId: Int? = null,
        val isPlaying: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
    )

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineName("VoiceNotePlayer")
    )

    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    /** Starts playback of a downloaded voice file (stops anything playing). */
    fun play(fileId: Int, path: String) {
        stopPlayback()
        try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(path)
            mediaPlayer.setOnCompletionListener { stopPlayback() }
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
            _state.value = PlaybackState(
                fileId = fileId,
                isPlaying = true,
                positionMs = 0,
                durationMs = mediaPlayer.duration,
            )
            ticker = scope.launch {
                while (isActive && player != null) {
                    val p = player ?: break
                    _state.value = _state.value.copy(positionMs = p.currentPosition)
                    delay(200)
                }
            }
        } catch (e: Exception) {
            stopPlayback()
        }
    }

    /** Play/pause toggle for a voice file. */
    fun toggle(fileId: Int, path: String) {
        val current = _state.value
        if (current.fileId == fileId && current.isPlaying) {
            stopPlayback()
        } else {
            play(fileId, path)
        }
    }

    /** Stops playback and releases the underlying MediaPlayer. */
    fun stopPlayback() {
        ticker?.cancel()
        ticker = null
        player?.let { mp ->
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
        player = null
        _state.value = PlaybackState(
            fileId = _state.value.fileId,
            isPlaying = false,
            positionMs = 0,
            durationMs = _state.value.durationMs,
        )
    }

    fun dispose() {
        stopPlayback()
        scope.cancel()
    }
}
