package com.bililite.tv.data.api

import android.util.Log
import com.bililite.tv.model.VideoContentType
import com.bililite.tv.model.VideoItem
import com.bililite.tv.model.VideoPage
import com.bililite.tv.util.FormatUtils
import org.json.JSONObject

class BilibiliVideoDetailApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun fetchVideoWithCid(video: VideoItem): VideoItem {
        if (
            video.contentType == VideoContentType.PGC ||
            video.epId > 0L ||
            video.seasonId > 0L
        ) {
            return fetchPgcSeason(video)
        }

        val query = when {
            video.bvid.isNotBlank() -> "bvid=${video.bvid}"
            video.aid > 0 -> "aid=${video.aid}"
            else -> error("Missing video id")
        }
        val request = client.request("/x/web-interface/view?$query")

        client.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }

            val root = JSONObject(response.body?.string().orEmpty())
            val code = root.optInt("code", -1)
            SessionExpiredException.throwIfExpired(code)
            if (code != 0) {
                error(root.optString("message", "Video detail error"))
            }

            val data = root.optJSONObject("data") ?: error("Video detail is empty")
            val pages = data.optJSONArray("pages")
            val parsedPages = buildList {
                if (pages != null) {
                    for (index in 0 until pages.length()) {
                        val item = pages.optJSONObject(index) ?: continue
                        val cid = item.optLong("cid")
                        if (cid > 0) {
                            add(
                                VideoPage(
                                    cid = cid,
                                    page = item.optInt("page", index + 1),
                                    part = item.optString("part").ifBlank { "P${index + 1}" },
                                    duration = item.optInt("duration"),
                                    aid = data.optLong("aid", video.aid),
                                    bvid = data.optString("bvid", video.bvid),
                                    coverUrl = FormatUtils.normalizeImageUrl(
                                        item.optString("first_frame")
                                    )
                                )
                            )
                        }
                    }
                }
            }
            val matchedPage = parsedPages.firstOrNull { it.cid == video.cid }
            val firstPage = parsedPages.firstOrNull()
            val cid = when {
                video.cid > 0 -> video.cid
                firstPage != null -> firstPage.cid
                else -> data.optLong("cid")
            }
            if (cid <= 0) {
                error("Missing cid")
            }
            Log.i(
                TAG_PAGES,
                "ugc bvid=${video.bvid}, pages count=${parsedPages.size}, " +
                    "current page=${matchedPage?.page ?: firstPage?.page ?: 1}"
            )

            val owner = data.optJSONObject("owner")
            val ownerMid = owner?.optLong("mid") ?: 0L
            val ownerName = owner?.optString("name").orEmpty()
            val ownerFaceUrl = FormatUtils.normalizeImageUrl(owner?.optString("face").orEmpty())

            val stat = data.optJSONObject("stat")
            val playCount = stat.readStatLong("view")
            val likeCount = stat.readStatLong("like")
            val coinCount = stat.readStatLong("coin")
            val favoriteCount = stat.readStatLong("favorite")
            val danmakuCount = stat.readStatLong("danmaku")

            return video.copy(
                aid = data.optLong("aid", video.aid),
                bvid = data.optString("bvid", video.bvid),
                cid = cid,
                duration = data.optLong("duration", video.duration),
                playCount = playCount?.takeIf { it > 0L } ?: video.playCount,
                description = data.optString("desc", video.description).ifBlank { video.description },
                pages = parsedPages,
                currentPage = matchedPage?.page ?: firstPage?.page ?: video.currentPage,
                ownerMid = ownerMid.takeIf { it > 0L } ?: video.ownerMid,
                ownerName = ownerName.ifBlank { video.ownerName },
                ownerFaceUrl = ownerFaceUrl.ifBlank { video.ownerFaceUrl },
                likeCount = likeCount,
                coinCount = coinCount,
                favoriteCount = favoriteCount,
                danmakuCount = danmakuCount
            )
        }
    }

    /**
     * PGC / bangumi / TV drama details.
     *
     * /pgc/view/web/season returns the complete episodes array for the current
     * season. The previous implementation selected one episode and then built
     * pages = listOf(currentEpisode), which is why an 80-episode drama appeared
     * as a single video in the TV detail screen.
     */
    private fun fetchPgcSeason(video: VideoItem): VideoItem {
        val query = when {
            video.epId > 0L -> "ep_id=${video.epId}"
            video.seasonId > 0L -> "season_id=${video.seasonId}"
            else -> error("Missing PGC episode or season id")
        }
        val request = client.request("/pgc/view/web/season?$query")

        client.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")

            val root = JSONObject(response.body?.string().orEmpty())
            val code = root.optInt("code", -1)
            SessionExpiredException.throwIfExpired(code)
            if (code != 0) {
                error(root.optString("message", "影视详情加载失败"))
            }

            val result = root.optJSONObject("result") ?: error("影视详情为空")
            val episodesJson = result.optJSONArray("episodes") ?: error("影视剧集为空")
            val seasonId = result.optLong("season_id", video.seasonId)
            val seasonTitle = result.optString("title").ifBlank { video.title }
            val seasonCover = FormatUtils.normalizeImageUrl(
                result.optString("cover")
            ).ifBlank { video.coverUrl }

            val pages = buildList {
                for (index in 0 until episodesJson.length()) {
                    val episode = episodesJson.optJSONObject(index) ?: continue
                    val cid = episode.optLong("cid")
                    val aid = episode.optLong("aid")
                    if (cid <= 0L || aid <= 0L) continue

                    val epId = episode.optLong("id")
                    val bvid = episode.optString("bvid")
                    val rawTitle = episode.optString("title")
                    val longTitle = episode.optString("long_title")
                    val displayPart = when {
                        longTitle.isNotBlank() && rawTitle.isNotBlank() ->
                            "第${rawTitle}集 · $longTitle"
                        longTitle.isNotBlank() -> longTitle
                        rawTitle.isNotBlank() -> "第${rawTitle}集"
                        else -> "第${index + 1}集"
                    }
                    val durationSeconds = (
                        episode.optLong("duration") / 1000L
                    ).coerceAtLeast(0L)
                    val cover = FormatUtils.normalizeImageUrl(
                        episode.optString("cover")
                    ).ifBlank { seasonCover }

                    add(
                        VideoPage(
                            cid = cid,
                            page = index + 1,
                            part = displayPart,
                            duration = durationSeconds
                                .coerceAtMost(Int.MAX_VALUE.toLong())
                                .toInt(),
                            aid = aid,
                            bvid = bvid,
                            epId = epId,
                            seasonId = seasonId,
                            coverUrl = cover,
                            sectionTitle = seasonTitle,
                            isCollectionEpisode = true
                        )
                    )
                }
            }

            if (pages.isEmpty()) {
                error("影视剧集为空")
            }

            val selectedIndex = pages.indexOfFirst {
                when {
                    video.epId > 0L && it.epId > 0L -> it.epId == video.epId
                    video.cid > 0L -> it.cid == video.cid
                    video.bvid.isNotBlank() && it.bvid.isNotBlank() -> it.bvid == video.bvid
                    video.aid > 0L -> it.aid == video.aid
                    else -> false
                }
            }.takeIf { it >= 0 } ?: 0

            val selected = pages[selectedIndex]
            val seasonStat = result.optJSONObject("stat")
            val playCount = seasonStat.readStatLong("views")
                ?: seasonStat.readStatLong("view")

            Log.i(
                TAG_PAGES,
                "pgc seasonId=$seasonId, epId=${selected.epId}, " +
                    "episodes=${pages.size}, selected=${selectedIndex + 1}, " +
                    "title=$seasonTitle"
            )

            return video.copy(
                aid = selected.aid,
                bvid = selected.bvid,
                cid = selected.cid,
                coverUrl = selected.coverUrl.ifBlank { seasonCover },
                duration = selected.duration.toLong(),
                playCount = playCount?.takeIf { it > 0L } ?: video.playCount,
                description = result.optString("evaluate").ifBlank { video.description },
                pages = pages,
                currentPage = selected.page,
                epId = selected.epId.takeIf { it > 0L } ?: video.epId,
                seasonId = seasonId,
                contentType = VideoContentType.PGC
            )
        }
    }

    private companion object {
        const val TAG_PAGES = "BiliPages"
    }
}

private fun JSONObject?.readStatLong(key: String): Long? {
    if (this == null || !has(key) || isNull(key)) {
        return null
    }
    return optLong(key)
}
