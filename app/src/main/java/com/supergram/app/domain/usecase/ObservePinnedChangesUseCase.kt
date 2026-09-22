package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.SharedFlow

class ObservePinnedChangesUseCase(private val repository: ChatRepository) {
    operator fun invoke(): SharedFlow<Long> = repository.pinnedChanges
}
