package com.funactivity.ppmr.domain

import com.funactivity.ppmr.domain.model.RecordingFile
import com.funactivity.ppmr.domain.sync.AudioFormats
import com.funactivity.ppmr.domain.sync.CandidateDecision
import com.funactivity.ppmr.domain.sync.CandidateRules
import com.funactivity.ppmr.domain.sync.FileIdentity
import com.funactivity.ppmr.domain.sync.RetryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRulesTest {
    private fun file(name: String = "call.m4a", size: Long = 100, modified: Long = 1_000) =
        RecordingFile("content://doc/$name", name, size, modified, "audio/mp4")

    @Test
    fun `identity is stable for the same name size and time`() {
        assertEquals(FileIdentity.keyOf("a.m4a", 10, 5), FileIdentity.keyOf("a.m4a", 10, 5))
    }

    @Test
    fun `identity changes when any field changes`() {
        val base = FileIdentity.keyOf("a.m4a", 10, 5)
        assertNotEquals(base, FileIdentity.keyOf("b.m4a", 10, 5))
        assertNotEquals(base, FileIdentity.keyOf("a.m4a", 11, 5))
        assertNotEquals(base, FileIdentity.keyOf("a.m4a", 10, 6))
    }

    @Test
    fun `identity fields cannot run into each other`() {
        assertNotEquals(FileIdentity.keyOf("a1", 1, 5), FileIdentity.keyOf("a", 11, 5))
    }

    @Test
    fun `identity fits in a Drive app property`() {
        val keyLength = "funActivityIdentity".length
        assertTrue(keyLength + FileIdentity.keyOf("a.m4a", 10, 5).length <= 124)
    }

    @Test
    fun `all required extensions are supported in any case`() {
        listOf("m4a", "mp3", "wav", "amr", "3gp", "aac", "ogg", "opus", "M4A", "Mp3").forEach {
            assertTrue(it, AudioFormats.isSupported("call.$it"))
        }
    }

    @Test
    fun `other media, no extension and hidden files are not supported`() {
        listOf("photo.jpg", "video.mp4", "notes.txt", "m4a", ".pending-123-call.m4a", ".trashed-1-call.mp3")
            .forEach { assertFalse(it, AudioFormats.isSupported(it)) }
    }

    @Test
    fun `mime type uses the provider value only when it is audio`() {
        assertEquals("audio/x-custom", AudioFormats.mimeTypeFor("a.m4a", "audio/x-custom"))
        assertEquals("audio/3gpp", AudioFormats.mimeTypeFor("a.3gp", "video/3gpp"))
        assertEquals("audio/mpeg", AudioFormats.mimeTypeFor("a.mp3", null))
    }

    @Test
    fun `unsupported and empty files are ignored`() {
        assertEquals(CandidateDecision.Ignored, CandidateRules.evaluate(file(name = "a.jpg"), 99_000, 30))
        assertEquals(CandidateDecision.Ignored, CandidateRules.evaluate(file(size = 0), 99_000, 30))
    }

    @Test
    fun `file younger than the minimum age waits until it is ready`() {
        val decision = CandidateRules.evaluate(file(modified = 10_000), now = 20_000, minAgeSeconds = 30)
        assertEquals(CandidateDecision.TooNew(readyAt = 40_000), decision)
    }

    @Test
    fun `file exactly at the minimum age is eligible`() {
        val decision = CandidateRules.evaluate(file(modified = 10_000), now = 40_000, minAgeSeconds = 30)
        assertEquals(CandidateDecision.Eligible, decision)
    }

    @Test
    fun `backoff doubles each attempt`() {
        assertEquals(listOf(30_000L, 60_000L, 120_000L, 240_000L), (1..4).map(RetryPolicy::delayAfter))
    }

    @Test
    fun `gives up on the fifth attempt`() {
        assertFalse(RetryPolicy.shouldGiveUp(4))
        assertTrue(RetryPolicy.shouldGiveUp(5))
    }
}
