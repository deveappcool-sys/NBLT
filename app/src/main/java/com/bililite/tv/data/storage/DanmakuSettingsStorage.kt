package com.bililite.tv.data.storage

import android.content.Context
import android.util.Log
import com.bililite.tv.model.DanmakuSettings

class DanmakuSettingsStorage(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): DanmakuSettings {
        val settings = DanmakuSettings(
            enabledByDefault = preferences.getBoolean(KEY_ENABLED, true),
            fontScale = preferences.getFloat(KEY_FONT_SCALE, 1.0f),
            alpha = preferences.getFloat(KEY_ALPHA, 0.8f),
            speed = preferences.getFloat(KEY_SPEED, 1.0f),
            displayAreaRatio = preferences.getFloat(KEY_DISPLAY_AREA_RATIO, 0.5f)
        )
        Log.i(TAG, "settings loaded=$settings")
        return settings
    }

    fun save(settings: DanmakuSettings) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabledByDefault)
            .putFloat(KEY_FONT_SCALE, settings.fontScale)
            .putFloat(KEY_ALPHA, settings.alpha)
            .putFloat(KEY_SPEED, settings.speed)
            .putFloat(KEY_DISPLAY_AREA_RATIO, settings.displayAreaRatio)
            .apply()
        Log.i(TAG, "settings saved=$settings")
    }

    private companion object {
        const val PREFS_NAME = "danmaku_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_ALPHA = "alpha"
        const val KEY_SPEED = "speed"
        const val KEY_DISPLAY_AREA_RATIO = "display_area_ratio"
        const val TAG = "BiliDanmaku"
    }
}
