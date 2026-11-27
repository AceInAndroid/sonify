package com.sonify.player.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLoopGuardTest {

    @Test
    fun evaluateLoop_triggersBreakerAtThirdCrashWithin30s() {
        val t0 = 1_000_000L
        val d1 = CrashLoopGuard.evaluateLoop(emptyList(), nowMs = t0)
        assertFalse(d1.isLoopBroken)
        assertEquals(1, d1.crashesInWindow)

        val d2 = CrashLoopGuard.evaluateLoop(d1.keptTimestamps, nowMs = t0 + 5_000L)
        assertFalse(d2.isLoopBroken)
        assertEquals(2, d2.crashesInWindow)

        val d3 = CrashLoopGuard.evaluateLoop(d2.keptTimestamps, nowMs = t0 + 12_000L)
        assertTrue(d3.isLoopBroken)
        assertEquals(3, d3.crashesInWindow)
    }

    @Test
    fun evaluateLoop_expiresCrashesOlderThanWindow() {
        val t0 = 2_000_000L
        val prev = listOf(t0, t0 + 5_000L)
        // 40s later (> 30s window): old timestamps should expire
        val decision = CrashLoopGuard.evaluateLoop(prev, nowMs = t0 + 40_000L)
        assertFalse(decision.isLoopBroken)
        assertEquals(1, decision.crashesInWindow)
    }

    @Test
    fun sanitizeText_stripsLocalPathsAndSubsonicCredentials() {
        val raw = """
            java.io.IOException: Failed reading /storage/emulated/0/Music/Jay/晴天.flac
            url=https://nas.private.home:4533/rest/stream?u=alice&t=abcdef1234&s=salt99&password=secret123&pin=8888
            also /sdcard/Download/test.wav
        """.trimIndent()
        val sanitized = CrashLoopGuard.sanitizeText(raw)

        assertFalse("Should strip /storage/emulated path", sanitized.contains("/storage/emulated/0"))
        assertFalse("Should strip /sdcard path", sanitized.contains("/sdcard/Download"))
        assertFalse("Should strip Subsonic host", sanitized.contains("nas.private.home"))
        assertFalse("Should strip password value", sanitized.contains("secret123"))
        assertFalse("Should strip token value", sanitized.contains("abcdef1234"))
        assertFalse("Should strip pin value", sanitized.contains("8888"))
        assertTrue(sanitized.contains("<local_path>"))
        assertTrue(sanitized.contains("<subsonic_host>/rest/"))
        assertTrue(sanitized.contains("password=***"))
    }

    @Test
    fun crashReport_jsonRoundTripPreservesFields() {
        val report = CrashReport(
            id = "crash_1700000000000",
            timestampMs = 1700000000000L,
            appVersionName = "0.2.0",
            appVersionCode = 2,
            deviceModel = "3917JR",
            androidSdk = 29,
            abi = "arm64-v8a",
            threadName = "main",
            audioRoute = "USB_EXCLUSIVE",
            exceptionClass = "java.lang.IllegalStateException",
            exceptionMessage = "Test crash",
            stackTrace = "at com.sonify.player.MainActivity.onCreate(MainActivity.kt:42)",
            uploaded = true,
        )
        val parsed = CrashReport.fromJson(report.toJson().toString())
        assertNotNull(parsed)
        assertEquals(report, parsed)
        assertTrue(report.toShareableSummary().contains("Sonify Crash Report"))
    }
}
