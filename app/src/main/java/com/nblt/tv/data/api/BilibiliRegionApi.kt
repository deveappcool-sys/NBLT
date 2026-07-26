package com.nblt.tv.data.api

import com.nblt.tv.model.HomeSection
import com.nblt.tv.model.VideoContentType
import com.nblt.tv.model.VideoItem
import com.nblt.tv.util.FormatUtils
import org.json.JSONObject

class BilibiliRegionApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun fetchSection(
        id: Int,
        title: String,
        page: Int = 1,
        pageSize: Int = 20
    ): HomeSection {
        if (id in PGC_SECTION_IDS) {
            return fetchPgcSection(id, title, page, pageSize)
        }
        val request = client.request("/x/web-interface/newlist?rid=$id&pn=$page&ps=$pageSize")
        client.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val root = JSONObject(response.body?.string().orEmpty())
            if (root.optInt("code", -1) != 0) {
                error(root.optString("message", "分区加载失败"))
            }
            val archives = root.optJSONObject("data")?.optJSONArray("archives")
                ?: return HomeSection(id, title, emptyList(), page = page, hasMore = false)
            val videos = buildList {
                for (index in 0 until archives.length()) {
                    val item = archives.optJSONObject(index) ?: continue
                    add(item.toVideoItem(index))
                }
            }
            return HomeSection(
                id = id,
                title = title,
                videos = videos,
                page = page,
                hasMore = videos.size >= pageSize
            )
        }
    }

    private fun fetchPgcSection(
        id: Int,
        title: String,
        page: Int,
        pageSize: Int
    ): HomeSection {
        val seasonType = id - PGC_ID_OFFSET
        val path = "/pgc/season/index/result" +
            "?season_version=-1&area=-1&is_finish=-1&copyright=-1" +
            "&season_status=-1&year=-1&style_id=-1&order=2" +
            "&st=$seasonType&sort=0&page=$page&season_type=$seasonType" +
            "&pagesize=$pageSize&type=1"
        val request = client.request(path)
        client.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val root = JSONObject(response.body?.string().orEmpty())
            if (root.optInt("code", -1) != 0) {
                error(root.optString("message", "影视内容加载失败"))
            }
            val data = root.optJSONObject("data")
            val list = data?.optJSONArray("list")
            val videos = buildList {
                if (list != null) {
                    for (index in 0 until list.length()) {
                        val item = list.optJSONObject(index) ?: continue
                        val firstEp = item.optJSONObject("first_ep")
                        val epId = firstEp?.optLong("ep_id") ?: 0L
                        if (epId <= 0L) continue
                        val itemTitle = item.optString("title").ifBlank { "无标题" }
                        // 角标优先取 badge_info.text，缺失时回退 badge（如"大会员"/"出品"/"独家"）
                        val badgeInfoText = item.optJSONObject("badge_info")
                            ?.optString("text").orEmpty().ifBlank { item.optString("badge") }
                        add(
                            VideoItem(
                                aid = 0L,
                                bvid = "",
                                cid = 0L,
                                coverUrl = FormatUtils.normalizeImageUrl(item.optString("cover")),
                                title = itemTitle,
                                // PGC 是平台影视内容而非 UP 主投稿，固定内容来源名；
                                // 不再把角标(badge)误当作 UP 主名（已确认 bug 来源）
                                ownerName = "哔哩哔哩影视",
                                // 索引接口不提供数值型播放量/时长，0 仅表示"未提供"；
                                // UI 不应把该 0 当作真实统计展示，后续 PGC 详情请求会补全
                                playCount = 0L,
                                duration = 0L,
                                // 简介优先 subTitle，缺失时回退 index_show；不回退 title（标题重复无价值）
                                description = item.optString("subTitle").ifBlank {
                                    item.optString("index_show")
                                },
                                accent = FormatUtils.accentFor(index),
                                epId = epId,
                                seasonId = item.optLong("season_id"),
                                contentType = VideoContentType.PGC,
                                badgeText = badgeInfoText,
                                indexShow = item.optString("index_show"),
                                orderText = item.optString("order"),
                                scoreText = item.optString("score"),
                                mediaId = item.optLong("media_id"),
                                seasonType = item.optLong("season_type").toInt()
                            )
                        )
                    }
                }
            }
            return HomeSection(
                id = id,
                title = title,
                videos = videos,
                page = page,
                hasMore = data?.optInt("has_next", 0) == 1
            )
        }
    }

    private fun JSONObject.toVideoItem(index: Int): VideoItem {
        val owner = optJSONObject("owner")
        val stat = optJSONObject("stat")
        val title = optString("title").ifBlank { "无标题" }
        return VideoItem(
            aid = optLong("aid", index.toLong() + 1),
            bvid = optString("bvid"),
            cid = optLong("cid"),
            coverUrl = FormatUtils.normalizeImageUrl(optString("pic")),
            title = title,
            ownerName = owner?.optString("name").orEmpty().ifBlank { "Bilibili" },
            playCount = stat?.optLong("view") ?: 0L,
            duration = optLong("duration"),
            description = optString("desc").ifBlank { title },
            accent = FormatUtils.accentFor(index),
            ownerMid = owner?.optLong("mid") ?: 0L,
            ownerFaceUrl = FormatUtils.normalizeImageUrl(owner?.optString("face").orEmpty()),
            pubdate = optLong("pubdate"),
            danmakuCount = stat?.optLong("danmaku")
        )
    }

    private companion object {
        const val PGC_ID_OFFSET = 1000
        val PGC_SECTION_IDS = setOf(1002, 1003, 1005)
    }
}
