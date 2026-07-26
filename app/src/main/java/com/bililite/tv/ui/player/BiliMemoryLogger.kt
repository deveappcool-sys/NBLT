package com.bililite.tv.ui.player

import android.os.Build
import android.util.Log

private const val TAG_MEMORY = "BiliMemory"

internal fun logPlayerMemory(stage: String) {
    val runtime = Runtime.getRuntime()
    val max = runtime.maxMemory()
    val total = runtime.totalMemory()
    val free = runtime.freeMemory()
    val used = total - free
    Log.i(
        TAG_MEMORY,
        "$stage memory: used=${used / 1024 / 1024}MB, free=${free / 1024 / 1024}MB, " +
            "total=${total / 1024 / 1024}MB, max=${max / 1024 / 1024}MB"
    )
}

internal fun isLowMemoryPlaybackDevice(): Boolean {
    val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()
    val model = Build.MODEL.orEmpty().lowercase()
    val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
    val brand = Build.BRAND.orEmpty().lowercase()
    return listOf(fingerprint, model, manufacturer, brand).any { value ->
        value.contains("mumu") ||
            value.contains("emulator") ||
            value.contains("generic") ||
            value.contains("sdk_gphone") ||
            value.contains("android sdk")
    }
}
