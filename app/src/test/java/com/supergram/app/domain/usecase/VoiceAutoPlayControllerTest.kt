package com.supergram.app.domain.usecase

import com.supergram.app.domain.model.MediaFile
import com.supergram.app.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the voice-note auto-download / auto-play triggering logic.
 */
class VoiceAutoPlayControllerTest {

    private val controller = VoiceAutoPlayController()

    private fun voice(
        fileId: Int,
        downloaded: Boolean,
        path: String? = if (downloaded) "/tmp/$fileId.oga" else null,
    ) = MediaFile(
        kind = MediaKind.VOICE,
        fileId = fileId,
        fileName = null,
        expectedSize = 1024,
        downloadedSize = if (downloaded) 1024 else 0,
        isDownloadingActive = !downloaded,
        isDownloaded = downloaded,
        localPath = path,
    )

    @Test
    fun `toggle on a downloaded file returns Play with local path`() {
        val action = controller.onToggle(voice(fileId = 3, downloaded = true))

        assertEquals(VoiceAutoPlayController.Action.Play("/tmp/3.oga"), action)
    }

    @Test
    fun `toggle on a not-downloaded file returns Download and arms pending`() {
        val action = controller.onToggle(voice(fileId = 3, downloaded = false))

        assertEquals(VoiceAutoPlayController.Action.Download(3), action)
    }

    @Test
    fun `file update for unrelated file returns null`() {
        controller.onToggle(voice(fileId = 3, downloaded = false))

        val result = controller.onFileUpdated(voice(fileId = 99, downloaded = true))

        assertNull(result)
    }

    @Test
    fun `file update for pending file while still downloading returns null`() {
        controller.onToggle(voice(fileId = 3, downloaded = false))

        val stillDownloading = voice(fileId = 3, downloaded = false).copy(
            isDownloadingActive = true,
            downloadedSize = 512,
        )
        val result = controller.onFileUpdated(stillDownloading)

        assertNull(result)
    }

    @Test
    fun `file update completing pending download returns play path once`() {
        controller.onToggle(voice(fileId = 3, downloaded = false))

        val completed = voice(fileId = 3, downloaded = true, path = "/tmp/3.oga")
        val first = controller.onFileUpdated(completed)
        val second = controller.onFileUpdated(completed)

        assertEquals("/tmp/3.oga", first)
        assertNull(second)
    }

    @Test
    fun `cancel clears the pending request`() {
        controller.onToggle(voice(fileId = 3, downloaded = false))
        controller.cancel()

        val result = controller.onFileUpdated(voice(fileId = 3, downloaded = true))

        assertNull(result)
    }
}
