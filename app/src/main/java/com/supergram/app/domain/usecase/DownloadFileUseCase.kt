package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository

/** Starts (or resumes) a media download; progress arrives via ObserveFileUpdatesUseCase. */
class DownloadFileUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(fileId: Int): Result<Unit> = repository.downloadFile(fileId)
}
