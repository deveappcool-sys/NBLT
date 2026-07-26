package com.bililite.tv.data.storage

import android.content.Context
import android.util.Log
import com.bililite.tv.model.PlaybackEndBehavior
import com.bililite.tv.model.PlaybackProfile
import com.bililite.tv.ui.home.HomeNavTabs

class PlaybackSettingsStorage(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPreferredQualityQn(): Int {
        val qn = preferences.getInt(KEY_PREFERRED_QUALITY_QN, 0)
        Log.i(TAG, "user preferred qn=$qn")
        return qn
    }

    fun setPreferredQualityQn(qn: Int) {
        preferences.edit().putInt(KEY_PREFERRED_QUALITY_QN, qn).apply()
        Log.i(TAG, "settings saved preferred qn=$qn")
    }

    fun getDefaultPlaybackSpeed(): Float {
        val speed = preferences.getFloat(KEY_DEFAULT_PLAYBACK_SPEED, 1.0f)
        Log.i(TAG_SPEED, "default speed loaded=$speed")
        return speed
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        preferences.edit().putFloat(KEY_DEFAULT_PLAYBACK_SPEED, speed).apply()
        Log.i(TAG_SPEED, "default speed saved=$speed")
    }

    fun getStartupTab(): String {
        val stored = preferences.getString(KEY_STARTUP_TAB, DEFAULT_STARTUP_TAB) ?: DEFAULT_STARTUP_TAB
        if (stored in VALID_STARTUP_TABS) {
            Log.i(TAG_BEHAVIOR, "startup default tab=$stored")
            return stored
        }
        Log.i(TAG_BEHAVIOR, "invalid startup tab=$stored, fallback to $DEFAULT_STARTUP_TAB")
        return DEFAULT_STARTUP_TAB
    }

    fun setStartupTab(tab: String) {
        val safeTab = if (tab in VALID_STARTUP_TABS) tab else DEFAULT_STARTUP_TAB
        preferences.edit().putString(KEY_STARTUP_TAB, safeTab).apply()
        Log.i(TAG_BEHAVIOR, "startup default tab saved=$safeTab")
    }

    fun isAutoPlayNextPageEnabled(): Boolean {
        val enabled = preferences.getBoolean(KEY_AUTO_PLAY_NEXT_PAGE, true)
        Log.i(TAG_BEHAVIOR, "auto play next enabled=$enabled")
        return enabled
    }

    fun setAutoPlayNextPageEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_PLAY_NEXT_PAGE, enabled).apply()
        Log.i(TAG_BEHAVIOR, "auto play next saved=$enabled")
    }

    fun getPlaybackEndBehavior(): PlaybackEndBehavior {
        val name = preferences.getString(KEY_PLAYBACK_END_BEHAVIOR, PlaybackEndBehavior.STAY_AT_END.name)
        val behavior = runCatching { PlaybackEndBehavior.valueOf(name.orEmpty()) }
            .getOrDefault(PlaybackEndBehavior.STAY_AT_END)
        Log.i(TAG_BEHAVIOR, "end behavior=$behavior")
        return behavior
    }

    fun setPlaybackEndBehavior(behavior: PlaybackEndBehavior) {
        preferences.edit().putString(KEY_PLAYBACK_END_BEHAVIOR, behavior.name).apply()
        Log.i(TAG_BEHAVIOR, "end behavior saved=$behavior")
    }

    fun getPlaybackProfile(): PlaybackProfile {
        val profile = PlaybackProfile.fromName(preferences.getString(KEY_PLAYBACK_PROFILE, null))
        Log.i(TAG_PROFILE, "default playback profile loaded=${profile.name}, ua=${profile.shortName}")
        return profile
    }

    fun setPlaybackProfile(profile: PlaybackProfile) {
        preferences.edit().putString(KEY_PLAYBACK_PROFILE, profile.name).apply()
        Log.i(TAG_PROFILE, "default playback profile saved=${profile.name}, ua=${profile.shortName}")
    }

    private companion object {
        const val PREFS_NAME = "playback_settings"
        const val KEY_PREFERRED_QUALITY_QN = "preferred_quality_qn"
        const val KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed"
        const val KEY_STARTUP_TAB = "startup_tab"
        const val KEY_AUTO_PLAY_NEXT_PAGE = "auto_play_next_page"
        const val KEY_PLAYBACK_END_BEHAVIOR = "playback_end_behavior"
        const val KEY_PLAYBACK_PROFILE = "playback_profile"
        val DEFAULT_STARTUP_TAB = HomeNavTabs.HOME
        val VALID_STARTUP_TABS = HomeNavTabs.ALL.toSet()
        const val TAG = "BiliQuality"
        const val TAG_SPEED = "BiliSpeed"
        const val TAG_BEHAVIOR = "BiliPlaybackBehavior"
        const val TAG_PROFILE = "BiliPlaybackProfile"
    }
}
