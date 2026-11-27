package com.sonify.player.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SonifyLocaleManagerTest {

    @Test
    fun appLanguage_fromStorageKey_handlesKnownAndUnknownValues() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorageKey(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorageKey(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorageKey("unknown"))
        assertEquals(AppLanguage.ZH_CN, AppLanguage.fromStorageKey("zh-CN"))
        assertEquals(AppLanguage.ZH_CN, AppLanguage.fromStorageKey("zh-cn"))
        assertEquals(AppLanguage.EN, AppLanguage.fromStorageKey("en"))
    }

    @Test
    fun resolveLocale_respectsExplicitChoiceAndSystemFallback() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, SonifyLocaleManager.resolveLocale(AppLanguage.ZH_CN, Locale.US))
        assertEquals(Locale.ENGLISH, SonifyLocaleManager.resolveLocale(AppLanguage.EN, Locale.CHINA))

        // Follow system: Chinese locales resolve to zh-CN; all others fall back to English
        assertEquals(Locale.SIMPLIFIED_CHINESE, SonifyLocaleManager.resolveLocale(AppLanguage.SYSTEM, Locale.CHINA))
        assertEquals(Locale.SIMPLIFIED_CHINESE, SonifyLocaleManager.resolveLocale(AppLanguage.SYSTEM, Locale.TAIWAN))
        assertEquals(Locale.ENGLISH, SonifyLocaleManager.resolveLocale(AppLanguage.SYSTEM, Locale.US))
        assertEquals(Locale.ENGLISH, SonifyLocaleManager.resolveLocale(AppLanguage.SYSTEM, Locale.JAPAN))
        assertEquals(Locale.ENGLISH, SonifyLocaleManager.resolveLocale(AppLanguage.SYSTEM, Locale.FRANCE))

        assertTrue(SonifyLocaleManager.isChinese(AppLanguage.ZH_CN, Locale.US))
        assertFalse(SonifyLocaleManager.isChinese(AppLanguage.EN, Locale.CHINA))
        assertTrue(SonifyLocaleManager.isChinese(AppLanguage.SYSTEM, Locale.CHINA))
        assertFalse(SonifyLocaleManager.isChinese(AppLanguage.SYSTEM, Locale.UK))
    }
}
