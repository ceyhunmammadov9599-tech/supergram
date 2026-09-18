package com.supergram.app.domain.usecase

import com.supergram.app.domain.model.MediaFile
import com.supergram.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.SharedFlow

/** Observes real-time file download state changes. */
class ObserveFileUpdatesUseCase(private val repository: ChatRepository) {
    operator fun invoke(): SharedFlow<MediaFile> = repository.fileUpdates
}
