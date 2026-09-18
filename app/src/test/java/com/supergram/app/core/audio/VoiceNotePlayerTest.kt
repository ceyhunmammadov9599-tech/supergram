package com.supergram.app.core.audio

import com.supergram.app.domain.model.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [VoiceNotePlayer] playback transitions using a fake
 * [AudioEngine] and virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceNotePlayerTest {

    /** Deterministic fake engine: position advances 100 ms per tick query. */
    private class FakeEngine : AudioEngine {
        override var onPlaybackFinished: (() -> Unit)? = null
        var startedPath: String? = null
            private set
        var stopCount = 0
            private set
        private var elapsedMs = 0

        override fun start(path: String) {
            stop()
            startedPath = path
            elapsedMs = 0
        }

        override fun stop() {
            if (startedPath != null) stopCount++
            startedPath = null
        }

        override fun currentPositionMs(): Int {
            elapsedMs += 100
            return elapsedMs
        }

        override fun durationMs(): Int = 10_000
    }

    private fun player(engine: FakeEngine, testScope: CoroutineScope): VoiceNotePlayer =
        VoiceNotePlayer(engine, testScope)

    @Test
    fun `play sets playing state and reports duration`() = runTest {
        val engine = FakeEngine()
        val player = player(engine, backgroundScope)
        advanceUntilIdle()

        player.play(fileId = 7, path = "/tmp/voice.ogg")

        val state = player.state.value
        assertEquals(7, state.fileId)
        assertTrue(state.isPlaying)
        assertEquals(10_000, state.durationMs)
        assertEquals("/tmp/voice.ogg", engine.startedPath)
    }

    @Test
    fun `toggle stops an active playback of the same file`() = runTest {
        val engine = FakeEngine()
        val player = player(engine, backgroundScope)
        advanceUntilIdle()

        player.play(fileId = 7, path = "/tmp/voice.ogg")
        advanceUntilIdle()
        player.toggle(fileId = 7, path = "/tmp/voice.ogg")

        val state = player.state.value
        assertFalse(state.isPlaying)
        assertEquals(0, state.positionMs)
    }

    @Test
    fun `toggle on a different file starts that file`() = runTest {
        val engine = FakeEngine()
        val player = player(engine, backgroundScope)
        advanceUntilIdle()

        player.play(fileId = 7, path = "/tmp/a.ogg")
        player.toggle(fileId = 9, path = "/tmp/b.ogg")

        val state = player.state.value
        assertEquals(9, state.fileId)
        assertTrue(state.isPlaying)
        assertEquals("/tmp/b.ogg", engine.startedPath)
    }

    @Test
    fun `progress ticker advances positionMs over virtual time`() = runTest {
        val engine = FakeEngine()
        val player = player(engine, backgroundScope)
        advanceUntilIdle()

        player.play(fileId = 1, path = "/tmp/voice.ogg")
        advanceTimeBy(1_000) // 5 ticks x 200 ms

        assertTrue(player.state.value.positionMs > 0)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun `engine completion callback stops playback`() = runTest {
        val engine = FakeEngine()
        val player = player(engine, backgroundScope)
        advanceUntilIdle()

        player.play(fileId = 1, path = "/tmp/voice.ogg")
        engine.onPlaybackFinished?.invoke()

        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun `stopPlayback resets position but keeps last file id`() = runTest {
        val engine = FakeEngine()
        val player = player(engine, backgroundScope)
        advanceUntilIdle()

        player.play(fileId = 5, path = "/tmp/voice.ogg")
        player.stopPlayback()

        val state = player.state.value
        assertEquals(5, state.fileId)
        assertFalse(state.isPlaying)
        assertEquals(0, state.positionMs)
    }
}
