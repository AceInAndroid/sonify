package com.sonify.player.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private fun sampleRelease(
        versionCode: Int = 3,
        minSupported: Int = 1,
        force: Boolean = false,
        sha256: String = "a".repeat(64),
    ) = ReleaseInfo(
        channel = "stable",
        versionCode = versionCode,
        versionName = "0.3.0",
        minSupportedVersionCode = minSupported,
        forceUpdate = force,
        releaseNotes = "- 新增全局搜索\n- 新增崩溃恢复",
        apkUrl = "https://sonify.zhangbing.tech/releases/sonify-v0.3.0.apk",
        fileSize = 18_500_000L,
        sha256 = sha256,
        publishedAt = "2026-11-27T12:00:00Z",
    )

    @Test
    fun shouldRunAutoCheck_respects12HourThrottle() {
        val t0 = 10_000_000L
        assertFalse(UpdatePolicy.shouldRunAutoCheck(false, 0L, t0))
        assertTrue(UpdatePolicy.shouldRunAutoCheck(true, 0L, t0))
        assertFalse(UpdatePolicy.shouldRunAutoCheck(true, t0, t0 + 3_600_000L))
        assertTrue(UpdatePolicy.shouldRunAutoCheck(true, t0, t0 + UpdatePolicy.AUTO_CHECK_INTERVAL_MS))
    }

    @Test
    fun evaluate_handlesUpToDateIgnoredManualAndForced() {
        val sameVer = sampleRelease(versionCode = 2)
        val eSame = UpdatePolicy.evaluate(2, sameVer, ignoredVersionCode = 0, isManualCheck = true)
        assertFalse(eSame.hasUpdate)
        assertFalse(eSame.shouldPrompt)

        val newer = sampleRelease(versionCode = 3)
        val eIgnoredAuto = UpdatePolicy.evaluate(2, newer, ignoredVersionCode = 3, isManualCheck = false)
        assertTrue(eIgnoredAuto.hasUpdate)
        assertFalse("Ignored version should not auto-prompt", eIgnoredAuto.shouldPrompt)
        assertFalse(eIgnoredAuto.isForced)

        val eIgnoredManual = UpdatePolicy.evaluate(2, newer, ignoredVersionCode = 3, isManualCheck = true)
        assertTrue("Manual check should still prompt even if ignored", eIgnoredManual.shouldPrompt)

        val belowMin = sampleRelease(versionCode = 4, minSupported = 3)
        val eForced = UpdatePolicy.evaluate(
            currentVersionCode = 2,
            release = belowMin,
            ignoredVersionCode = 4,
            isManualCheck = false,
            allowNormalAutoPrompt = false,
        )
        assertTrue(eForced.isForced)
        assertTrue("Forced update bypasses ignore and autoCheck=false", eForced.shouldPrompt)

        val normalNoAuto = UpdatePolicy.evaluate(
            currentVersionCode = 2,
            release = newer,
            ignoredVersionCode = 0,
            isManualCheck = false,
            allowNormalAutoPrompt = false,
        )
        assertTrue(normalNoAuto.hasUpdate)
        assertFalse("Non-forced update should NOT prompt when allowNormalAutoPrompt=false", normalNoAuto.shouldPrompt)

        // All UpdateState variants preserve isForcedUpdate flag
        assertTrue(UpdateState.Downloading(belowMin, 100L, 1000L, 50L, isForced = true).isForcedUpdate)
        assertTrue(UpdateState.Verifying(belowMin, isForced = true).isForcedUpdate)
        assertTrue(UpdateState.ReadyToInstall(belowMin, java.io.File("/tmp/a.apk"), isForced = true).isForcedUpdate)
        assertTrue(UpdateState.Error("net", belowMin, isForced = true).isForcedUpdate)
        assertFalse(UpdateState.Downloading(newer, 100L, 1000L, 50L, isForced = false).isForcedUpdate)
    }

    @Test
    fun verifySha256_validatesExact64CharHexCaseInsensitively() {
        val hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        assertTrue(UpdatePolicy.verifySha256(hash.uppercase(), hash))
        assertFalse(UpdatePolicy.verifySha256(hash, hash.replaceFirst('0', 'f')))
        assertFalse(UpdatePolicy.verifySha256("short", "short"))
    }

    @Test
    fun releaseInfo_fromJsonParsesValidAndRejectsInvalid() {
        val json = """
            {
              "channel": "stable",
              "versionCode": 3,
              "versionName": "0.3.0",
              "minSupportedVersionCode": 1,
              "forceUpdate": false,
              "releaseNotes": "Test release",
              "apkUrl": "https://sonify.zhangbing.tech/releases/sonify-v0.3.0.apk",
              "fileSize": 10485760,
              "sha256": "${"B".repeat(64)}",
              "publishedAt": "2026-11-27T12:00:00Z"
            }
        """.trimIndent()
        val parsed = ReleaseInfo.fromJson(json)
        assertNotNull(parsed)
        assertEquals(3, parsed!!.versionCode)
        assertEquals("0.3.0", parsed.versionName)
        assertEquals("b".repeat(64), parsed.sha256)

        assertNull(ReleaseInfo.fromJson("""{"versionCode": 0, "versionName": ""}"""))
    }
}
