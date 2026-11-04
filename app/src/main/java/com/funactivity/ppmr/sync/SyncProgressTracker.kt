package com.funactivity.ppmr.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SyncProgress {
    data object Idle : SyncProgress
    data object Scanning : SyncProgress
    data class Uploading(val fileName: String, val bytesUploaded: Long, val totalBytes: Long) : SyncProgress {
        val fraction: Float
            get() = if (totalBytes > 0) (bytesUploaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }
}

/** Live progress from the workers. In memory, because byte counts change too often for Room. */
@Singleton
class SyncProgressTracker @Inject constructor() {
    private val _progress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    fun update(value: SyncProgress) {
        _progress.value = value
    }
}
