package com.sonify.player.i18n

/**
 * Sonify 支持的应用内显示语言。
 * 新增语种流程见 `docs/i18n/I18N_STANDARD.md`：
 * 1. 添加 `res/values-<lang>/strings.xml`
 * 2. 在此枚举与 `res/xml/locales_config.xml` 注册
 * 3. 运行 `python3 scripts/check_i18n.py --strict --sync-doc`
 */
enum class AppLanguage(
    val storageKey: String,
    val bcp47Tag: String?,
    val nativeName: String,
) {
    SYSTEM("system", null, "Follow System"),
    ZH_CN("zh-CN", "zh-CN", "简体中文"),
    EN("en", "en", "English");

    companion object {
        fun fromStorageKey(key: String?): AppLanguage =
            entries.firstOrNull { it.storageKey.equals(key?.trim(), ignoreCase = true) } ?: SYSTEM
    }
}
