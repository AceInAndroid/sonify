package com.sonify.player.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsBatchPolicyTest {

    @Test
    fun isValidEventName_acceptsSnakeCaseAndRejectsInvalid() {
        assertTrue(AnalyticsBatchPolicy.isValidEventName("app_launch"))
        assertTrue(AnalyticsBatchPolicy.isValidEventName("usb_dac_connected"))
        assertFalse(AnalyticsBatchPolicy.isValidEventName("AppLaunch"))
        assertFalse(AnalyticsBatchPolicy.isValidEventName("a"))
        assertFalse(AnalyticsBatchPolicy.isValidEventName("bad event"))
    }

    @Test
    fun sanitizeProps_stripsForbiddenPrivacyKeysAndPathsOrUrls() {
        val cleaned = AnalyticsBatchPolicy.sanitizeProps(
            mapOf(
                "format" to "FLAC",
                "sample_rate" to 96000,
                "is_hires" to true,
                "path" to "/storage/emulated/0/Music/secret.flac",
                "query" to "周杰伦",
                "password" to "hunter2",
                "server_url" to "https://private.nas/rest",
                "custom_path_val" to "/sdcard/Music/song.flac",
                "custom_url_val" to "https://example.com/a",
                "imei" to "1234567890",
            )
        )
        assertEquals("FLAC", cleaned["format"])
        assertEquals(96000, cleaned["sample_rate"])
        assertEquals(true, cleaned["is_hires"])
        assertFalse(cleaned.containsKey("path"))
        assertFalse(cleaned.containsKey("query"))
        assertFalse(cleaned.containsKey("password"))
        assertFalse(cleaned.containsKey("server_url"))
        assertFalse(cleaned.containsKey("custom_path_val"))
        assertFalse(cleaned.containsKey("custom_url_val"))
        assertFalse(cleaned.containsKey("imei"))
    }

    @Test
    fun beijingDayAndRollover_detectsNewDayAccurately() {
        // 2026-11-27 23:59:59 UTC+8 = 2026-11-27 15:59:59 UTC
        val beforeMidnight = 1795795199_000L
        val afterMidnight = beforeMidnight + 2_000L
        val d1 = AnalyticsBatchPolicy.beijingDay(beforeMidnight)
        val d2 = AnalyticsBatchPolicy.beijingDay(afterMidnight)
        assertFalse(d1 == d2)
        assertFalse(AnalyticsBatchPolicy.isNewActiveDay(d1, beforeMidnight))
        assertTrue(AnalyticsBatchPolicy.isNewActiveDay(d1, afterMidnight))
    }

    @Test
    fun shouldFlush_respectsCountIntervalAndForce() {
        val t0 = 1_000_000L
        assertFalse(AnalyticsBatchPolicy.shouldFlush(0, t0, t0 + 120_000L, forceImmediate = true))
        assertTrue(AnalyticsBatchPolicy.shouldFlush(1, t0, t0 + 1_000L, forceImmediate = true))
        assertFalse(AnalyticsBatchPolicy.shouldFlush(3, t0, t0 + 10_000L))
        assertTrue(AnalyticsBatchPolicy.shouldFlush(AnalyticsBatchPolicy.BATCH_FLUSH_COUNT, t0, t0 + 5_000L))
        assertTrue(AnalyticsBatchPolicy.shouldFlush(1, t0, t0 + AnalyticsBatchPolicy.BATCH_FLUSH_INTERVAL_MS))
    }

    @Test
    fun bucketTrackCount_groupsWithoutExposingExactCounts() {
        assertEquals("0", AnalyticsBatchPolicy.bucketTrackCount(0))
        assertEquals("1_20", AnalyticsBatchPolicy.bucketTrackCount(15))
        assertEquals("21_100", AnalyticsBatchPolicy.bucketTrackCount(77))
        assertEquals("101_500", AnalyticsBatchPolicy.bucketTrackCount(250))
        assertEquals("500_plus", AnalyticsBatchPolicy.bucketTrackCount(1200))
    }
}
