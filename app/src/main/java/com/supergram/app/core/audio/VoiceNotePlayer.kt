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
 * Platform audio engine abstraction. Keeps [VoiceNotePlayer] fully
 * unit-testable (a fake engine can be injected in JVM tests).
 */
interface AudioEngine {
    /** Invoked when playback reaches the end of the file. */
    var onPlaybackFinished: (() -> Unit)?

    /** Prepares and starts playback of a local audio file. */
    fun start(path: String)

    /** Stops playback and releases resources (no-op when idle). */
    fun stop()

    fun currentPositionMs(): Int

    fun durationMs(): Int
}

/** Production engine on Android's native MediaPlayer (OGG/Opus capable). */
class MediaPlayerEngine : AudioEngine {
    private var player: MediaPlayer? = null
    override var onPlaybackFinished: (() -> Unit)? = null

    override fun start(path: String) {
        stop()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(path)
            mp.setOnCompletionListener { onPlaybackFinished?.invoke() }
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            stop()
            onPlaybackFinished?.invoke()
        }
    }

    override fun stop() {
        player?.let { mp ->
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
        player = null
    }

    override fun currentPositionMs(): Int = player?.currentPosition ?: 0

    override fun durationMs(): Int = player?.duration ?: 0
}

/**
 * Lightweight voice note playback engine. Exposes a single [state] stream
 * with the active file id, play/pause flag, and playback position so Compose
 * bubbles can render progress reactively.
 *
 * @param engine platform audio engine (injectable for tests)
 * @param scope scope for the progress ticker (injectable for tests)
 */
class VoiceNotePlayer(
    private val engine: AudioEngine = MediaPlayerEngine(),
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineName("VoiceNotePlayer")
    ),
) {

    /** Snapshot of the current (or last) playback session. */
    data class PlaybackState(
        val fileId: Int? = null,
        val isPlaying: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
    )

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var ticker: Job? = null

    init {
        engine.onPlaybackFinished = { stopPlayback() }
    }

    /** Starts playback of a downloaded voice file (stops anything playing). */
    fun play(fileId: Int, path: String) {
        stopPlayback()
        engine.start(path)
        _state.value = PlaybackState(
            fileId = fileId,
            isPlaying = true,
            positionMs = 0,
            durationMs = engine.durationMs(),
        )
        startTicker()
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

    /** Stops playback and releases the underlying engine. */
    fun stopPlayback() {
        ticker?.cancel()
        ticker = null
        engine.stop()
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

    private fun startTicker() {
        ticker = scope.launch {
            while (isActive && _state.value.isPlaying) {
                _state.value = _state.value.copy(positionMs = engine.currentPositionMs())
                delay(TICK_MS)
            }
        }
    }

    private companion object {
        const val TICK_MS = 200L
    }
}
