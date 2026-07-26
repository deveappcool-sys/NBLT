package com.bililite.tv.data.storage

import android.content.Context
import com.bililite.tv.model.FollowedUp
import com.bililite.tv.model.VideoContentType
import com.bililite.tv.model.VideoItem
import com.bililite.tv.util.FormatUtils
import org.json.JSONArray
import org.json.JSONObject

class HomeSnapshotStorage(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun activateUser(userMid: Long) {
        if (userMid > 0L) {
            preferences.edit().putLong(KEY_ACTIVE_USER_MID, userMid).apply()
        }
    }

    fun loadHistory(userMid: Long): HomeHistorySnapshot? {
        return readSnapshot(historyKey(userMid)) { root ->
            HomeHistorySnapshot(
                videos = root.optJSONArray(KEY_VIDEOS).toVideoItems(),
                cursorMax = root.optLong(KEY_CURSOR_MAX),
                cursorViewAt = root.optLong(KEY_CURSOR_VIEW_AT),
                hasMore = root.optBoolean(KEY_HAS_MORE, true)
            ).takeIf { it.videos.isNotEmpty() }
        }
    }

    fun saveHistory(userMid: Long, snapshot: HomeHistorySnapshot) {
        if (!isActiveUser(userMid)) return
        if (snapshot.videos.isEmpty()) {
            clearHistory(userMid)
            return
        }
        writeSnapshot(
            key = historyKey(userMid),
            root = JSONObject()
                .put(KEY_VIDEOS, snapshot.videos.take(MAX_VIDEO_COUNT).toVideoJsonArray())
                .put(KEY_CURSOR_MAX, snapshot.cursorMax)
                .put(KEY_CURSOR_VIEW_AT, snapshot.cursorViewAt)
                .put(KEY_HAS_MORE, snapshot.hasMore)
        )
    }

    fun clearHistory(userMid: Long) {
        if (userMid > 0L) {
            preferences.edit().remove(historyKey(userMid)).apply()
        }
    }

    fun loadDynamic(userMid: Long): HomeDynamicSnapshot? {
        return readSnapshot(dynamicKey(userMid)) { root ->
            HomeDynamicSnapshot(
                followedUps = root.optJSONArray(KEY_FOLLOWED_UPS).toFollowedUps(),
                videos = root.optJSONArray(KEY_VIDEOS).toVideoItems(),
                offset = root.optString(KEY_OFFSET).takeIf { it.isNotBlank() },
                hasMore = root.optBoolean(KEY_HAS_MORE, true)
            ).takeIf { it.videos.isNotEmpty() || it.followedUps.isNotEmpty() }
        }
    }

    fun saveDynamic(userMid: Long, snapshot: HomeDynamicSnapshot) {
        if (!isActiveUser(userMid)) return
        if (snapshot.videos.isEmpty() && snapshot.followedUps.isEmpty()) {
            clearDynamic(userMid)
            return
        }
        writeSnapshot(
            key = dynamicKey(userMid),
            root = JSONObject()
                .put(KEY_FOLLOWED_UPS, snapshot.followedUps.take(MAX_FOLLOWED_UP_COUNT).toFollowedUpJsonArray())
                .put(KEY_VIDEOS, snapshot.videos.take(MAX_VIDEO_COUNT).toVideoJsonArray())
                .put(KEY_OFFSET, snapshot.offset.orEmpty())
                .put(KEY_HAS_MORE, snapshot.hasMore)
        )
    }

    fun clearDynamic(userMid: Long) {
        if (userMid > 0L) {
            preferences.edit().remove(dynamicKey(userMid)).apply()
        }
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun <T> readSnapshot(key: String, decode: (JSONObject) -> T?): T? {
        return runCatching {
            val raw = preferences.getString(key, null) ?: return null
            val root = JSONObject(raw)
            val version = root.optInt(KEY_VERSION)
            val savedAt = root.optLong(KEY_SAVED_AT)
            val ageMs = System.currentTimeMillis() - savedAt
            if (version != SNAPSHOT_VERSION || savedAt <= 0L || ageMs !in 0L..MAX_SNAPSHOT_AGE_MS) {
                preferences.edit().remove(key).apply()
                null
            } else {
                decode(root).also { decoded ->
                    if (decoded == null) {
                        preferences.edit().remove(key).apply()
                    }
                }
            }
        }.getOrElse {
            preferences.edit().remove(key).apply()
            null
        }
    }

    private fun writeSnapshot(key: String, root: JSONObject) {
        root.put(KEY_VERSION, SNAPSHOT_VERSION)
        root.put(KEY_SAVED_AT, System.currentTimeMillis())
        preferences.edit().putString(key, root.toString()).apply()
    }

    private fun isActiveUser(userMid: Long): Boolean {
        return userMid > 0L && preferences.getLong(KEY_ACTIVE_USER_MID, 0L) == userMid
    }

    private fun historyKey(userMid: Long): String = "history_$userMid"

    private fun dynamicKey(userMid: Long): String = "dynamic_$userMid"

    private fun JSONArray?.toVideoItems(): List<VideoItem> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index)?.toVideoItem(index) ?: continue
                add(item)
            }
        }
    }

    private fun List<VideoItem>.toVideoJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { video -> array.put(video.toJson()) }
        }
    }

    private fun VideoItem.toJson(): JSONObject {
        return JSONObject()
            .put(KEY_AID, aid)
            .put(KEY_BVID, bvid)
            .put(KEY_CID, cid)
            .put(KEY_COVER_URL, coverUrl)
            .put(KEY_TITLE, title)
            .put(KEY_OWNER_NAME, ownerName)
            .put(KEY_PLAY_COUNT, playCount)
            .put(KEY_DURATION, duration)
            .put(KEY_DESCRIPTION, description.take(MAX_DESCRIPTION_LENGTH))
            .put(KEY_OWNER_MID, ownerMid)
            .put(KEY_OWNER_FACE_URL, ownerFaceUrl)
            .put(KEY_HISTORY_VIEW_AT, historyViewAt)
            .put(KEY_HISTORY_PROGRESS, historyProgress)
            .put(KEY_PUBDATE, pubdate)
            .put(KEY_DANMAKU_COUNT, danmakuCount ?: JSONObject.NULL)
            .put(KEY_EP_ID, epId)
            .put(KEY_SEASON_ID, seasonId)
            .put(KEY_CONTENT_TYPE, contentType.name)
            .put(KEY_BADGE_TEXT, badgeText)
            .put(KEY_INDEX_SHOW, indexShow)
            .put(KEY_ORDER_TEXT, orderText)
            .put(KEY_SCORE_TEXT, scoreText)
            .put(KEY_MEDIA_ID, mediaId)
            .put(KEY_SEASON_TYPE, seasonType)
    }

    private fun JSONObject.toVideoItem(index: Int): VideoItem? {
        val aid = optLong(KEY_AID)
        val bvid = optString(KEY_BVID)
        val epId = optLong(KEY_EP_ID)
        if (aid <= 0L && bvid.isBlank() && epId <= 0L) return null
        return VideoItem(
            aid = aid,
            bvid = bvid,
            cid = optLong(KEY_CID),
            coverUrl = optString(KEY_COVER_URL),
            title = optString(KEY_TITLE),
            ownerName = optString(KEY_OWNER_NAME),
            playCount = optLong(KEY_PLAY_COUNT),
            duration = optLong(KEY_DURATION),
            description = optString(KEY_DESCRIPTION),
            accent = FormatUtils.accentFor(index),
            ownerMid = optLong(KEY_OWNER_MID),
            ownerFaceUrl = optString(KEY_OWNER_FACE_URL),
            historyViewAt = optLong(KEY_HISTORY_VIEW_AT),
            historyProgress = optLong(KEY_HISTORY_PROGRESS),
            pubdate = optLong(KEY_PUBDATE),
            danmakuCount = optNullableLong(KEY_DANMAKU_COUNT),
            epId = epId,
            seasonId = optLong(KEY_SEASON_ID),
            contentType = runCatching {
                VideoContentType.valueOf(optString(KEY_CONTENT_TYPE))
            }.getOrDefault(VideoContentType.UGC),
            badgeText = optString(KEY_BADGE_TEXT),
            indexShow = optString(KEY_INDEX_SHOW),
            orderText = optString(KEY_ORDER_TEXT),
            scoreText = optString(KEY_SCORE_TEXT),
            mediaId = optLong(KEY_MEDIA_ID),
            seasonType = optInt(KEY_SEASON_TYPE)
        )
    }

    private fun JSONArray?.toFollowedUps(): List<FollowedUp> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val mid = item.optLong(KEY_MID)
                if (mid <= 0L) continue
                add(
                    FollowedUp(
                        mid = mid,
                        name = item.optString(KEY_NAME),
                        avatarUrl = item.optString(KEY_AVATAR_URL)
                    )
                )
            }
        }
    }

    private fun List<FollowedUp>.toFollowedUpJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { up ->
                array.put(
                    JSONObject()
                        .put(KEY_MID, up.mid)
                        .put(KEY_NAME, up.name)
                        .put(KEY_AVATAR_URL, up.avatarUrl)
                )
            }
        }
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        return if (!has(key) || isNull(key)) null else optLong(key)
    }

    private companion object {
        const val PREFERENCES_NAME = "home_snapshot"
        const val SNAPSHOT_VERSION = 1
        const val MAX_VIDEO_COUNT = 30
        const val MAX_FOLLOWED_UP_COUNT = 30
        const val MAX_DESCRIPTION_LENGTH = 1_000
        const val MAX_SNAPSHOT_AGE_MS = 7L * 24L * 60L * 60L * 1_000L

        const val KEY_ACTIVE_USER_MID = "active_user_mid"
        const val KEY_VERSION = "version"
        const val KEY_SAVED_AT = "saved_at"
        const val KEY_VIDEOS = "videos"
        const val KEY_FOLLOWED_UPS = "followed_ups"
        const val KEY_CURSOR_MAX = "cursor_max"
        const val KEY_CURSOR_VIEW_AT = "cursor_view_at"
        const val KEY_HAS_MORE = "has_more"
        const val KEY_OFFSET = "offset"
        const val KEY_AID = "aid"
        const val KEY_BVID = "bvid"
        const val KEY_CID = "cid"
        const val KEY_COVER_URL = "cover_url"
        const val KEY_TITLE = "title"
        const val KEY_OWNER_NAME = "owner_name"
        const val KEY_PLAY_COUNT = "play_count"
        const val KEY_DURATION = "duration"
        const val KEY_DESCRIPTION = "description"
        const val KEY_OWNER_MID = "owner_mid"
        const val KEY_OWNER_FACE_URL = "owner_face_url"
        const val KEY_HISTORY_VIEW_AT = "history_view_at"
        const val KEY_HISTORY_PROGRESS = "history_progress"
        const val KEY_PUBDATE = "pubdate"
        const val KEY_DANMAKU_COUNT = "danmaku_count"
        const val KEY_EP_ID = "ep_id"
        const val KEY_SEASON_ID = "season_id"
        const val KEY_CONTENT_TYPE = "content_type"
        const val KEY_BADGE_TEXT = "badge_text"
        const val KEY_INDEX_SHOW = "index_show"
        const val KEY_ORDER_TEXT = "order_text"
        const val KEY_SCORE_TEXT = "score_text"
        const val KEY_MEDIA_ID = "media_id"
        const val KEY_SEASON_TYPE = "season_type"
        const val KEY_MID = "mid"
        const val KEY_NAME = "name"
        const val KEY_AVATAR_URL = "avatar_url"
    }
}

data class HomeHistorySnapshot(
    val videos: List<VideoItem>,
    val cursorMax: Long,
    val cursorViewAt: Long,
    val hasMore: Boolean
)

data class HomeDynamicSnapshot(
    val followedUps: List<FollowedUp>,
    val videos: List<VideoItem>,
    val offset: String?,
    val hasMore: Boolean
)
