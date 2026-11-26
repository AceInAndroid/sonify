package com.sonify.player.ui

import org.json.JSONObject

data class AltInfo(
    val iface: Int,
    val alt: Int,
    val ep: String,
    val maxPacket: Int,
    val bits: Int,
    val subslot: Int,
    val channels: Int,
    val syncType: Int,
    val feedbackEp: String,
    val rates: List<Int>,
)

data class UacInfo(
    val uacVersion: Int,
    val vid: String,
    val pid: String,
    val product: String,
    val manufacturer: String,
    val acInterface: Int,
    val clockSourceId: Int,
    val hasClockSource: Boolean,
    val hasHwVol: Boolean = false,
    val hwVolUnitId: Int = -1,
    val hwVolMinDb: Double = -60.0,
    val hwVolMaxDb: Double = 0.0,
    val alts: List<AltInfo>,
)

/** 解析 cpp/uac_desc.cpp 里 uac_info_to_json() 的输出 */
fun parseInfo(json: String): UacInfo? = try {
    val o = JSONObject(json)
    val arr = o.getJSONArray("alts")
    val alts = ArrayList<AltInfo>(arr.length())
    for (i in 0 until arr.length()) {
        val a = arr.getJSONObject(i)
        val rs = a.getJSONArray("rates")
        alts += AltInfo(
            iface = a.getInt("iface"),
            alt = a.getInt("alt"),
            ep = a.getString("ep"),
            maxPacket = a.getInt("maxPacket"),
            bits = a.getInt("bits"),
            subslot = a.getInt("subslot"),
            channels = a.getInt("channels"),
            syncType = a.getInt("syncType"),
            feedbackEp = a.getString("feedbackEp"),
            rates = List(rs.length()) { rs.getInt(it) },
        )
    }
    UacInfo(
        uacVersion = o.getInt("uacVersion"),
        vid = o.getString("vid"),
        pid = o.getString("pid"),
        product = o.optString("product"),
        manufacturer = o.optString("manufacturer"),
        acInterface = o.getInt("acInterface"),
        clockSourceId = o.getInt("clockSourceId"),
        hasClockSource = o.getBoolean("hasClockSource"),
        hasHwVol = o.optBoolean("hasHwVol", false),
        hwVolUnitId = o.optInt("hwVolUnitId", -1),
        hwVolMinDb = o.optDouble("hwVolMinDb", -60.0),
        hwVolMaxDb = o.optDouble("hwVolMaxDb", 0.0),
        alts = alts,
    )
} catch (e: Exception) {
    null
}
