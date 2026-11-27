package com.sonify.player.analytics

import org.json.JSONObject

/**
 * 匿名设备基础画像（零个人隐私：仅随机 UUIDv4、App 版本、机型、API 等级与外接 USB DAC 型号）。
 */
data class AnalyticsDeviceInfo(
    val installId: String,
    val appVersion: String,
    val versionCode: Int,
    val deviceModel: String,
    val manufacturer: String,
    val androidSdk: Int,
    val abi: String,
    val usbDac: String = "",
    val channel: String = "stable",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("installId", installId)
        put("appVersion", appVersion)
        put("versionCode", versionCode)
        put("deviceModel", deviceModel)
        put("manufacturer", manufacturer)
        put("androidSdk", androidSdk)
        put("abi", abi)
        put("usbDac", usbDac)
        put("channel", channel)
    }
}

/**
 * 单条脱敏埋点事件。
 */
data class AnalyticsEvent(
    val name: String,
    val tsMs: Long,
    val props: Map<String, Any> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("tsMs", tsMs)
        put("props", JSONObject().apply {
            props.forEach { (k, v) -> put(k, v) }
        })
    }

    companion object {
        fun fromJson(raw: String): AnalyticsEvent? = runCatching {
            val o = JSONObject(raw)
            val name = o.optString("name", "").trim()
            val tsMs = o.optLong("tsMs", 0L)
            if (name.isEmpty() || tsMs <= 0L) return null
            val propsObj = o.optJSONObject("props")
            val props = buildMap<String, Any> {
                if (propsObj != null) {
                    val keys = propsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = propsObj.opt(k)
                        if (v != null && v != JSONObject.NULL) put(k, v)
                    }
                }
            }
            AnalyticsEvent(name = name, tsMs = tsMs, props = props)
        }.getOrNull()
    }
}
