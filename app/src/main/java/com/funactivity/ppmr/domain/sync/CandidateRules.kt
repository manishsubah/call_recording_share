package com.funactivity.ppmr.domain.sync

import com.funactivity.ppmr.domain.model.RecordingFile

sealed interface CandidateDecision {
    data object Eligible : CandidateDecision
    data object Ignored : CandidateDecision

    /** Possibly still being written. Look again at [readyAt]. */
    data class TooNew(val readyAt: Long) : CandidateDecision
}

/** The checks a file must pass before it can be queued. Dedup comes after these. */
object CandidateRules {
    fun evaluate(file: RecordingFile, now: Long, minAgeSeconds: Int): CandidateDecision {
        if (!AudioFormats.isSupported(file.displayName) || file.size <= 0) {
            return CandidateDecision.Ignored
        }
        val readyAt = file.lastModified + minAgeSeconds * 1_000L
        return if (now < readyAt) CandidateDecision.TooNew(readyAt) else CandidateDecision.Eligible
    }
}
