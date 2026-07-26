package com.nblt.tv.data.api

import android.util.Log
import com.nblt.tv.model.PlayUrl
import com.nblt.tv.model.PlaybackProfile
import com.nblt.tv.model.VideoItem
import com.nblt.tv.model.VideoContentType
import com.nblt.tv.model.VideoQuality
import com.nblt.tv.data.api.BilibiliCdnPreference.hostOnly
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

class BilibiliPlayUrlApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun fetchPlayUrl(
        video: VideoItem,
        preferredQn: Int = 0,
        cookieHeader: String = "",
        avoidedHosts: Set<String> = emptySet(),
        playbackProfile: PlaybackProfile = PlaybackProfile.WEB_CHROME_WINDOWS
    ): PlayUrl {
        if (video.cid <= 0) {
            error("Missing cid")
        }

        val isPgc = video.isPgcPlayback()

        Log.i(
            TAG_QUALITY,
            "bvid=${video.bvid}, aid=${video.aid}, cid=${video.cid}, " +
                "epId=${video.epId}, contentType=${video.contentType}, requested qn=$preferredQn"
        )
        Log.i(
            TAG_PLAY_URL_DEBUG,
            "fetch playurl: bvid=${video.bvid}, aid=${video.aid}, cid=${video.cid}, qn=$preferredQn, " +
                "isLogin=${cookieHeader.isNotBlank()}, hasCookie=${cookieHeader.isNotBlank()}, " +
                "hasSESSDATA=${cookieHeader.contains("SESSDATA")}, hasDedeUserID=${cookieHeader.contains("DedeUserID")}, " +
                "referer=${video.videoReferer()}, profile=${playbackProfile.name}, userAgent=${playbackProfile.shortName}, " +
                "failedHosts=${avoidedHosts.joinToString(prefix = "[", postfix = "]")}"
        )
        val result = runCatching {
            requestPlayUrl(
                video = video,
                requestedQn = preferredQn,
                cookieHeader = cookieHeader,
                playbackProfile = playbackProfile,
                preferDash = true,
                avoidedHosts = avoidedHosts
            )
        }.recoverCatching { dashError ->
            Log.w(
                TAG,
                "${if (isPgc) "PGC" else "UGC"} DASH playurl parse failed, " +
                    "fallback to durl request: ${dashError.message}"
            )
            requestPlayUrl(
                video = video,
                requestedQn = preferredQn,
                cookieHeader = cookieHeader,
                playbackProfile = playbackProfile,
                preferDash = false,
                avoidedHosts = avoidedHosts
            )
        }.getOrThrow()
        val actualQn = result.quality?.qn ?: 0
        if (preferredQn > 0 && actualQn != preferredQn) {
            Log.i(
                TAG_QUALITY,
                "requested qn=$preferredQn, response quality=$actualQn, " +
                    "available qualities=${result.availableQualities.joinToString { "${it.qn}:${it.description}" }}, " +
                    "reason=API returned downgraded quality; possible account permission, unsupported video, or not logged in"
            )
        }
        return result
    }

    private fun requestPlayUrl(
        video: VideoItem,
        requestedQn: Int,
        cookieHeader: String,
        playbackProfile: PlaybackProfile,
        preferDash: Boolean,
        avoidedHosts: Set<String>
    ): PlayUrl {
        val qnQuery = requestedQn.takeIf { it > 0 }?.let { "&qn=$it" }.orEmpty()
        val fnval = if (preferDash) 16 else 0
        val cid = video.cid
        val isPgc = video.isPgcPlayback()
        val path = if (isPgc) {
            val epId = video.epId.takeIf { it > 0L }
                ?: error("剧集播放信息不完整：缺少 ep_id")
            val avidQuery = video.aid.takeIf { it > 0L }
                ?.let { "&avid=$it" }
                .orEmpty()
            "/pgc/player/web/v2/playurl?ep_id=$epId$avidQuery&cid=$cid$qnQuery" +
                "&fnver=0&fnval=$fnval&fourk=1&otype=json&module=bangumi"
        } else {
            val idQuery = when {
                video.bvid.isNotBlank() -> "bvid=${video.bvid}"
                video.aid > 0 -> "avid=${video.aid}"
                else -> error("Missing video id")
            }
            "/x/player/playurl?$idQuery&cid=$cid$qnQuery" +
                "&fnver=0&fnval=$fnval&fourk=1&otype=json"
        }
        Log.i(TAG_QUALITY, "playurl request qn=$requestedQn")
        Log.i(TAG_QUALITY, "playurl request URL=https://api.bilibili.com$path")
        Log.i(
            TAG_PLAY_URL_DEBUG,
            "request params: type=${if (isPgc) "PGC" else "UGC"}, " +
                "epId=${video.epId}, aid=${video.aid}, cid=$cid, qn=$requestedQn, " +
                "fnval=$fnval, fourk=1, platform=web"
        )
        Log.i(
            TAG_WEB_LIKE,
            "playurl requested: referer=${video.videoReferer()}, fnval=$fnval, qn=$requestedQn, " +
                "hasCookie=${cookieHeader.isNotBlank()}, profile=${playbackProfile.name}, ua=${playbackProfile.shortName}, bypassCache=true"
        )
        val request = client.request(path).withPlayerHeaders(cookieHeader, video.videoReferer(), playbackProfile.userAgent)

        client.httpClient.newCall(request).execute().use { response ->
            Log.i(TAG_PROFILE, "profile=${playbackProfile.name}, ua=${playbackProfile.shortName}, playurl HTTP=${response.code}")
            Log.i(TAG_PLAY_URL_DEBUG, "response code=${response.code}, response message=${response.message}")
            if (response.code == 403 || response.code == 412) {
                error("Bilibili playurl HTTP ${response.code}")
            }
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }

            val root = JSONObject(response.body?.string().orEmpty())
            logPlayUrlSummary(root)

            val rootCode = root.optInt("code", -1)
            val nestedEnvelope = root.optJSONObject("data")
                ?: root.optJSONObject("raw")
            val nestedCode = nestedEnvelope?.optInt("code", rootCode) ?: rootCode
            val code = if (rootCode != 0) rootCode else nestedCode
            val serverMessage = root.optString("message")
                .ifBlank { root.optString("msg") }
                .ifBlank { nestedEnvelope?.optString("message").orEmpty() }
                .ifBlank { nestedEnvelope?.optString("msg").orEmpty() }
            Log.i(
                TAG_PROFILE,
                "profile=${playbackProfile.name}, playurl code=$code, " +
                    "message=$serverMessage, type=${if (isPgc) "PGC" else "UGC"}"
            )
            SessionExpiredException.throwIfExpired(code)
            if (code != 0) {
                error(
                    resolvePlayUrlApiError(
                        code = code,
                        serverMessage = serverMessage,
                        root = root,
                        video = video
                    )
                )
            }

            val data = extractPlayUrlData(root)
                ?: error("B站没有返回可用播放地址")
            val qualities = parseAvailableQualities(data)
            val actualQn = data.optInt("quality", data.optInt("qn", 0))
            val actualQuality = qualityFor(actualQn, qualities)
            Log.i(
                TAG_QUALITY,
                "response quality=$actualQn, " +
                    "accept_quality=${data.optJSONArray("accept_quality")?.toString().orEmpty()}, " +
                    "accept_description=${data.optJSONArray("accept_description")?.toString().orEmpty()}, " +
                    "selected actual qn=${actualQuality?.qn ?: 0}, " +
                    "selected actual description=${actualQuality?.description.orEmpty()}, " +
                    "available qualities=${qualities.joinToString { "${it.qn}:${it.description}" }}"
            )

            if (preferDash) {
                parseDashPlayUrl(data, actualQuality, qualities, requestedQn, cookieHeader, playbackProfile, video.videoReferer(), avoidedHosts, video.cid)?.let { return it }
                parseDurlPlayUrl(data, actualQuality, qualities, requestedQn, cookieHeader, playbackProfile, video.videoReferer(), avoidedHosts, video.cid)?.let { return it }
            } else {
                parseDurlPlayUrl(data, actualQuality, qualities, requestedQn, cookieHeader, playbackProfile, video.videoReferer(), avoidedHosts, video.cid)?.let { return it }
                parseDashPlayUrl(data, actualQuality, qualities, requestedQn, cookieHeader, playbackProfile, video.videoReferer(), avoidedHosts, video.cid)?.let { return it }
            }

            error("No playable URL returned")
        }
    }

    private fun parseDashPlayUrl(
        data: JSONObject,
        actualQuality: VideoQuality?,
        availableQualities: List<VideoQuality>,
        requestedQn: Int,
        cookieHeader: String,
        playbackProfile: PlaybackProfile,
        referer: String,
        avoidedHosts: Set<String>,
        cid: Long
    ): PlayUrl? {
        val dash = data.optJSONObject("dash") ?: return null
        val videos = dash.optJSONArray("video") ?: return null
        val audios = dash.optJSONArray("audio")
        val codecPlan = DashVideoCodecPreferenceMemory.recommendedPlan(cid)
        val video = selectDashVideo(
            videos = videos,
            targetQn = actualQuality?.qn ?: 0,
            codecPreference = codecPlan?.preference ?: DashVideoCodecPreference.AVC_FIRST
        ) ?: return null
        val selectedTrackQn = video.optInt("id")
        val selectedCodec = video.optString("codecs")
        val selectedQuality = qualityFor(selectedTrackQn, availableQualities)
            ?: actualQuality

        if (
            codecPlan?.preference == DashVideoCodecPreference.HEVC_FIRST &&
            !selectedCodec.isHevcCodec()
        ) {
            DashVideoCodecPreferenceMemory.clear(cid)
            Log.w(
                TAG_CODEC_RECOVERY,
                "preferred HEVC track unavailable; continue AVC software fallback " +
                    "cid=$cid, requestedQn=$requestedQn, responseQn=${actualQuality?.qn ?: 0}, " +
                    "selectedTrackQn=$selectedTrackQn, selectedCodec=$selectedCodec"
            )
        } else if (codecPlan?.preference == DashVideoCodecPreference.HEVC_FIRST) {
            Log.i(
                TAG_CODEC_RECOVERY,
                "apply remembered HEVC preference cid=$cid, " +
                    "requestedQn=$requestedQn, responseQn=${actualQuality?.qn ?: 0}, " +
                    "selectedTrackQn=$selectedTrackQn, selectedCodec=$selectedCodec"
            )
        }
        val videoUrl = video.firstPlayableUrl()
        if (videoUrl.isBlank()) {
            return null
        }

        val audio = audios?.let(::selectDashAudio)
        val audioUrl = audio?.firstPlayableUrl()?.ifBlank { null }
        if (audioUrl == null) {
            Log.w(TAG, "DASH audio is empty, fallback to durl if available")
            return null
        }
        val videoCandidates = video.playableUrls()
        val audioCandidates = audio.playableUrls()
        val selectedVideo = selectCandidateAvoidingHosts(videoCandidates, avoidedHosts, "video", cid)
        val selectedAudio = selectCandidateAvoidingHosts(audioCandidates, avoidedHosts, "audio", cid)
        if (selectedVideo.url.isBlank() || selectedAudio.url.isBlank()) {
            return null
        }
        val expireInfo = estimateExpireInfo(videoCandidates + audioCandidates)
        Log.i(TAG, "Selected DASH video=${video.optInt("id")} codec=${video.optString("codecs")} audio=true")
        Log.i(
            TAG_PLAY_URL_DEBUG,
            "selected stream type=dash, selected qn=${selectedQuality?.qn ?: selectedTrackQn}, " +
                "selected format=${video.optString("mimeType")}, selected codec=${video.optString("codecs")}, " +
                "video baseUrl host=${videoUrl.hostOnly()}, video backupUrl count=${(videoCandidates.size - 1).coerceAtLeast(0)}, " +
                "audio baseUrl host=${audioUrl.hostOnly()}, audio backupUrl count=${(audioCandidates.size - 1).coerceAtLeast(0)}, " +
                "url has expires/deadline-like params=${expireInfo.first}, estimatedUrlExpireTime=${expireInfo.second ?: 0}"
        )
        Log.i(
            TAG_CDN,
            "failedHosts=${avoidedHosts.joinToString(prefix = "[", postfix = "]")}, " +
                "candidate video hosts=${videoCandidates.hostList()}, candidate audio hosts=${audioCandidates.hostList()}, " +
                "selectedVideoHost=${selectedVideo.url.hostOnly()}, selectedAudioHost=${selectedAudio.url.hostOnly()}, " +
                "fallback action=${selectedVideo.fallbackAction(selectedAudio)}, " +
                "reason=${selectedVideo.reason(selectedAudio)}"
        )
        Log.i(
            TAG_WEB_LIKE,
            "selected DASH: qn=${selectedQuality?.qn ?: selectedTrackQn}, codec=${video.optString("codecs")}, " +
                "videoHost=${selectedVideo.url.hostOnly()}, audioHost=${selectedAudio.url.hostOnly()}, " +
                "videoBackupCount=${(videoCandidates.size - 1).coerceAtLeast(0)}, " +
                "audioBackupCount=${(audioCandidates.size - 1).coerceAtLeast(0)}"
        )
        Log.i(
            TAG_PROFILE,
            "profile=${playbackProfile.name}, ua=${playbackProfile.shortName}, selected qn=${selectedQuality?.qn ?: selectedTrackQn}, " +
                "codec=${video.optString("codecs")}, videoHost=${selectedVideo.url.hostOnly()}, " +
                "audioHost=${selectedAudio.url.hostOnly()}, " +
                "backupUrlCount=${(videoCandidates.size - 1).coerceAtLeast(0) + (audioCandidates.size - 1).coerceAtLeast(0)}, " +
                "fallbackStep=${selectedVideo.fallbackAction(selectedAudio)}"
        )

        return PlayUrl(
            videoUrl = selectedVideo.url,
            audioUrl = selectedAudio.url,
            referer = referer,
            origin = BilibiliApiClient.ORIGIN,
            userAgent = playbackProfile.userAgent,
            playbackProfile = playbackProfile,
            cookieHeader = cookieHeader,
            requestedQn = requestedQn,
            sourceType = "dashVideo+dashAudio",
            selectedFormat = video.optString("mimeType").ifBlank { "dash" },
            videoCodec = video.optString("codecs").takeIf { it.isNotBlank() },
            audioCodec = audio?.optString("codecs")?.takeIf { it.isNotBlank() },
            backupUrls = video.backupUrls(),
            videoUrlCandidates = videoCandidates,
            audioUrlCandidates = audioCandidates,
            selectedVideoUrlIndex = selectedVideo.index,
            selectedAudioUrlIndex = selectedAudio.index,
            hasExpiringUrlParams = expireInfo.first,
            estimatedExpireTimeSeconds = expireInfo.second,
            quality = selectedQuality,
            availableQualities = availableQualities
        )
    }

    private fun parseDurlPlayUrl(
        data: JSONObject,
        actualQuality: VideoQuality?,
        availableQualities: List<VideoQuality>,
        requestedQn: Int,
        cookieHeader: String,
        playbackProfile: PlaybackProfile,
        referer: String,
        avoidedHosts: Set<String>,
        cid: Long
    ): PlayUrl? {
        val durl = data.optJSONArray("durl") ?: return null
        val first = durl.optJSONObject(0) ?: return null
        val url = first.optString("url")
        if (url.isBlank()) {
            return null
        }
        val candidates = first.playableUrls("url")
        val selected = selectCandidateAvoidingHosts(candidates, avoidedHosts, "durl", cid)
        if (selected.url.isBlank()) {
            return null
        }
        val expireInfo = estimateExpireInfo(candidates)
        Log.i(TAG, "Selected durl[0], size=${first.optLong("size")}, length=${first.optLong("length")}")
        Log.i(
            TAG_PLAY_URL_DEBUG,
            "selected stream type=durl, selected qn=${actualQuality?.qn ?: 0}, " +
                "selected format=${data.optString("format")}, selected codec=, " +
                "video baseUrl host=${url.hostOnly()}, video backupUrl count=${(candidates.size - 1).coerceAtLeast(0)}, " +
                "audio baseUrl host=, audio backupUrl count=0, " +
                "url has expires/deadline-like params=${expireInfo.first}, estimatedUrlExpireTime=${expireInfo.second ?: 0}"
        )
        Log.i(
            TAG_CDN,
            "failedHosts=${avoidedHosts.joinToString(prefix = "[", postfix = "]")}, " +
                "candidate video hosts=${candidates.hostList()}, candidate audio hosts=[], " +
                "selectedVideoHost=${selected.url.hostOnly()}, selectedAudioHost=, " +
                "fallback action=${selected.fallbackAction()}, " +
                "reason=${selected.reason()}"
        )
        Log.i(
            TAG_WEB_LIKE,
            "selected durl: qn=${actualQuality?.qn ?: 0}, videoHost=${selected.url.hostOnly()}, " +
                "videoBackupCount=${(candidates.size - 1).coerceAtLeast(0)}"
        )
        Log.i(
            TAG_PROFILE,
            "profile=${playbackProfile.name}, ua=${playbackProfile.shortName}, selected qn=${actualQuality?.qn ?: 0}, " +
                "codec=, videoHost=${selected.url.hostOnly()}, audioHost=, " +
                "backupUrlCount=${(candidates.size - 1).coerceAtLeast(0)}, fallbackStep=${selected.fallbackAction()}"
        )
        return PlayUrl(
            videoUrl = selected.url,
            audioUrl = null,
            referer = referer,
            origin = BilibiliApiClient.ORIGIN,
            userAgent = playbackProfile.userAgent,
            playbackProfile = playbackProfile,
            cookieHeader = cookieHeader,
            requestedQn = requestedQn,
            sourceType = "durl",
            selectedFormat = data.optString("format").ifBlank { "durl" },
            videoCodec = null,
            audioCodec = null,
            backupUrls = first.backupUrls(),
            videoUrlCandidates = candidates,
            audioUrlCandidates = emptyList(),
            selectedVideoUrlIndex = selected.index,
            selectedAudioUrlIndex = 0,
            hasExpiringUrlParams = expireInfo.first,
            estimatedExpireTimeSeconds = expireInfo.second,
            quality = actualQuality,
            availableQualities = availableQualities
        )
    }

    private fun selectDashVideo(
        videos: JSONArray,
        targetQn: Int,
        codecPreference: DashVideoCodecPreference
    ): JSONObject? {
        val all = (0 until videos.length()).mapNotNull { videos.optJSONObject(it) }
            .filter { it.firstPlayableUrl().isNotBlank() }

        if (codecPreference == DashVideoCodecPreference.HEVC_FIRST) {
            val selectedHevc = selectPreferredTrackForQuality(
                candidates = all.filter {
                    it.optString("mimeType") == "video/mp4" &&
                        it.optString("codecs").isHevcCodec()
                },
                targetQn = targetQn
            )
            if (selectedHevc != null) {
                Log.i(
                    TAG_CODEC_RECOVERY,
                    "select HEVC recovery track responseQn=$targetQn, " +
                        "trackQn=${selectedHevc.optInt("id")}, " +
                        "codec=${selectedHevc.optString("codecs")}, " +
                        "size=${selectedHevc.optInt("width")}x${selectedHevc.optInt("height")}, " +
                        "bandwidth=${selectedHevc.optLong("bandwidth")}"
                )
                return selectedHevc
            }
        }

        val sameQuality = all.filter { it.optInt("id") == targetQn }
        return sameQuality.firstOrNull {
            it.optString("mimeType") == "video/mp4" &&
                it.optString("codecs").startsWith("avc1")
        } ?: sameQuality.firstOrNull {
            it.optString("mimeType") == "video/mp4"
        } ?: sameQuality.firstOrNull()
            ?: all.firstOrNull {
                it.optString("mimeType") == "video/mp4" &&
                    it.optString("codecs").startsWith("avc1")
            } ?: all.firstOrNull {
                it.optString("mimeType") == "video/mp4" &&
                    !it.optString("codecs").startsWith("av01")
            } ?: all.firstOrNull { !it.optString("codecs").startsWith("av01") }
            ?: all.firstOrNull()
    }

    private fun selectPreferredTrackForQuality(
        candidates: List<JSONObject>,
        targetQn: Int
    ): JSONObject? {
        if (candidates.isEmpty()) {
            return null
        }
        if (targetQn <= 0) {
            return candidates.maxByOrNull { it.optInt("id") }
        }
        return candidates.firstOrNull { it.optInt("id") == targetQn }
            ?: candidates
                .filter { it.optInt("id") in 1..targetQn }
                .maxByOrNull { it.optInt("id") }
            ?: candidates.minByOrNull { it.optInt("id") }
    }

    private fun selectDashAudio(audios: JSONArray): JSONObject? {
        val all = (0 until audios.length()).mapNotNull { audios.optJSONObject(it) }
        return all.firstOrNull {
            it.optString("mimeType") == "audio/mp4" &&
                it.firstPlayableUrl().isNotBlank()
        } ?: all.firstOrNull { it.firstPlayableUrl().isNotBlank() }
    }

    private fun JSONObject.firstPlayableUrl(): String {
        val baseUrl = optString("baseUrl").ifBlank { optString("base_url") }
        if (baseUrl.isNotBlank()) {
            return baseUrl
        }
        val backups = optJSONArray("backupUrl") ?: optJSONArray("backup_url")
        return backups?.optString(0).orEmpty()
    }

    private fun JSONObject.playableUrls(primaryKey: String = "baseUrl"): List<String> {
        val primary = when (primaryKey) {
            "url" -> optString("url")
            else -> optString("baseUrl").ifBlank { optString("base_url") }
        }
        return buildList {
            if (primary.isNotBlank()) add(primary)
            addAll(backupUrls())
        }.distinct()
    }

    private fun JSONObject.backupUrls(): List<String> {
        val backups = optJSONArray("backupUrl") ?: optJSONArray("backup_url")
        return backups?.toStringList().orEmpty()
    }

    private fun parseAvailableQualities(data: JSONObject): List<VideoQuality> {
        val qualities = data.optJSONArray("accept_quality")?.toIntList().orEmpty()
        val descriptions = data.optJSONArray("accept_description")?.toStringList().orEmpty()
        val formats = data.optJSONArray("accept_format")?.toStringList().orEmpty()
        return qualities.mapIndexed { index, qn ->
            VideoQuality(
                qn = qn,
                description = descriptions.getOrNull(index).orEmpty().ifBlank { qualityName(qn) },
                format = formats.getOrNull(index),
                available = true
            )
        }
    }

    private fun qualityFor(qn: Int, qualities: List<VideoQuality>): VideoQuality? {
        if (qn <= 0) return null
        return qualities.firstOrNull { it.qn == qn } ?: VideoQuality(qn = qn, description = qualityName(qn))
    }

    private fun JSONArray.toIntList(): List<Int> {
        return (0 until length()).map { optInt(it) }.filter { it > 0 }
    }

    private fun JSONArray.toStringList(): List<String> {
        return (0 until length()).map { optString(it) }
    }

    private fun Request.withPlayerHeaders(cookieHeader: String, referer: String, userAgent: String): Request {
        return newBuilder()
            .header("Referer", referer)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .apply {
                if (cookieHeader.isNotBlank()) {
                    header("Cookie", cookieHeader)
                }
            }
            .build()
    }

    private fun qualityName(qn: Int): String {
        return when (qn) {
            16 -> "360P"
            32 -> "480P"
            64 -> "720P"
            80 -> "1080P"
            112 -> "1080P+"
            116 -> "1080P 60fps"
            120 -> "4K"
            125 -> "HDR"
            126 -> "Dolby Vision"
            127 -> "8K"
            else -> "qn=$qn"
        }
    }

    private fun extractPlayUrlData(root: JSONObject): JSONObject? {
        val topLevel = root.optJSONObject("data")
            ?: root.optJSONObject("result")
            ?: root.optJSONObject("raw")

        val nestedData = topLevel?.optJSONObject("data") ?: topLevel
        val nestedResult = nestedData?.optJSONObject("result") ?: nestedData
        return nestedResult?.optJSONObject("video_info") ?: nestedResult
    }

    private fun resolvePlayUrlApiError(
        code: Int,
        serverMessage: String,
        root: JSONObject,
        video: VideoItem
    ): String {
        val searchable = buildString {
            append(serverMessage)
            append(' ')
            append(root.toString())
        }

        return when {
            code == -10403 ||
                searchable.contains("大会员") ||
                searchable.contains("会员专享") ||
                searchable.contains("vip", ignoreCase = true) ->
                "本集需要大会员或相应播放权限"

            searchable.contains("地区") ||
                searchable.contains("区域") ||
                searchable.contains("AreaLimitPanel", ignoreCase = true) ||
                searchable.contains("area_limit", ignoreCase = true) ->
                "你所在的地区暂时无法播放本集"

            searchable.contains("试看") ||
                searchable.contains("preview", ignoreCase = true) ->
                "当前账号只能试看本集，完整播放需要相应权限"

            code == -101 ->
                "登录状态已失效，请重新登录后播放"

            code == -404 && video.isPgcPlayback() ->
                "B站没有返回该剧集的播放信息，请确认剧集仍可播放"

            code == -404 ->
                "该视频已失效、下架或不存在"

            code == -400 ->
                "播放参数无效，请返回详情页后重新选择本集"

            code == 403 || code == 412 ->
                "B站暂时拒绝了播放请求，请稍后重试"

            serverMessage.isNotBlank() &&
                !serverMessage.contains("啥都木有") ->
                "B站播放接口返回错误（$code）：$serverMessage"

            else ->
                "B站没有返回可用播放地址（错误码 $code）"
        }
    }

    private fun logPlayUrlSummary(root: JSONObject) {
        val data = extractPlayUrlData(root)
        val dash = data?.optJSONObject("dash")
        val durl = data?.optJSONArray("durl")
        Log.i(
            TAG,
            "playurl raw summary: code=${root.optInt("code", -1)}, " +
                "message=${root.optString("message").ifBlank { root.optString("msg") }}, " +
                "payload=${when {
                    root.optJSONObject("data") != null -> "data"
                    root.optJSONObject("result") != null -> "result"
                    root.optJSONObject("raw") != null -> "raw"
                    else -> "none"
                }}, " +
                "hasDash=${dash != null}, " +
                "dashVideoCount=${dash?.optJSONArray("video")?.length() ?: 0}, " +
                "dashAudioCount=${dash?.optJSONArray("audio")?.length() ?: 0}, " +
                "durlCount=${durl?.length() ?: 0}, " +
                "quality=${data?.optInt("quality", data.optInt("qn", 0)) ?: 0}, " +
                "accept_quality=${data?.optJSONArray("accept_quality")?.toString().orEmpty()}, " +
                "accept_description=${data?.optJSONArray("accept_description")?.toString().orEmpty()}, " +
                "accept_format=${data?.optJSONArray("accept_format")?.toString().orEmpty()}"
        )
    }

    private companion object {
        const val TAG = "BiliPlayUrlApi"
        const val TAG_QUALITY = "BiliQuality"
        const val TAG_PLAY_URL_DEBUG = "BiliPlayUrlDebug"
        const val TAG_CDN = "BiliCdnFallback"
        const val TAG_WEB_LIKE = "BiliWebLikePlayback"
        const val TAG_PROFILE = "BiliPlaybackProfile"
        const val TAG_CODEC_RECOVERY = "BiliCodecRecovery"
    }
}


private fun String.isHevcCodec(): Boolean {
    return startsWith("hvc1", ignoreCase = true) ||
        startsWith("hev1", ignoreCase = true)
}

private data class SelectedCandidate(
    val url: String,
    val index: Int,
    val avoidedFailedHost: Boolean,
    val avoidedWeakHost: Boolean,
    val usedPreferredHost: Boolean,
    val controlledProbe: Boolean
)

private fun selectCandidateAvoidingHosts(
    candidates: List<String>,
    avoidedHosts: Set<String>,
    streamName: String,
    cid: Long
): SelectedCandidate {
    if (candidates.isEmpty()) {
        return SelectedCandidate("", 0, false, false, false, false)
    }

    val ranked = candidates.mapIndexed { index, url ->
        val host = url.hostOnly()
        val route = BilibiliCdnPreference.routeStatus(
            host = host,
            cid = cid,
            explicitFailedHosts = avoidedHosts,
            streamName = streamName
        )
        CandidateRank(
            url = url,
            index = index,
            host = host,
            route = route
        )
    }

    ranked.forEach { candidate ->
        when {
            candidate.route.coolingDown -> Log.i(
                "BiliCdnFallback",
                "cooldown host=${candidate.host}, stream=$streamName, " +
                    "remainingMs=${candidate.route.cooldownRemainingMs}, " +
                    "failureCount=${candidate.route.failureCount}, reason=${candidate.route.reason}"
            )
            candidate.route.weak -> Log.i(
                "BiliCdnFallback",
                "deprioritize host=${candidate.host} reason=weak blacklist stream=$streamName"
            )
        }
    }

    val healthy = ranked
        .filterNot { it.route.coolingDown }
        .sortedWith(
            compareByDescending<CandidateRank> { it.route.score }
                .thenBy { it.index }
        )

    var controlledProbe = false
    val selected = healthy.firstOrNull() ?: ranked
        .sortedWith(
            compareBy<CandidateRank> { if (it.route.badSource) 1 else 0 }
                .thenBy { it.route.cooldownRemainingMs }
                .thenBy { it.route.failureCount }
                .thenByDescending { it.route.score }
                .thenBy { it.index }
        )
        .firstOrNull { candidate ->
            BilibiliCdnPreference.reserveControlledProbe(
                host = candidate.host,
                streamName = streamName,
                forceWhenExhausted = true
            )
        }
        ?.also { controlledProbe = true }

    if (selected == null) {
        Log.w("BiliCdnFallback", "no route candidate available for stream=$streamName")
        return SelectedCandidate("", 0, false, false, false, false)
    }

    Log.i(
        "BiliCdnFallback",
        "selected stream=$streamName index=${selected.index}, selectedHost=${selected.host}, " +
            "preferred=${selected.route.preferred}, weak=${selected.route.weak}, " +
            "coolingDown=${selected.route.coolingDown}, controlledProbe=$controlledProbe, " +
            "cooldownRemainingMs=${selected.route.cooldownRemainingMs}, " +
            "failureCount=${selected.route.failureCount}, selectedHostScore=${selected.route.score}, " +
            "dynamicScore=${selected.route.dynamicScore}, " +
            "dynamicConfidence=${selected.route.dynamicConfidencePercent}, " +
            "performanceSamples=${selected.route.performanceSamples}"
    )

    return SelectedCandidate(
        url = selected.url,
        index = selected.index,
        avoidedFailedHost = !controlledProbe && ranked.any {
            it.index < selected.index && it.route.coolingDown
        },
        avoidedWeakHost = ranked.any {
            it.index < selected.index && it.route.weak
        },
        usedPreferredHost = selected.route.preferred,
        controlledProbe = controlledProbe
    )
}

private data class CandidateRank(
    val url: String,
    val index: Int,
    val host: String,
    val route: BilibiliCdnPreference.HostRouteStatus
)

private fun SelectedCandidate.fallbackAction(other: SelectedCandidate? = null): String {
    return when {
        controlledProbe || other?.controlledProbe == true -> "controlledProbe"
        avoidedFailedHost || other?.avoidedFailedHost == true -> "sameQnBackupUrl"
        avoidedWeakHost || other?.avoidedWeakHost == true -> "weakHostBackupUrl"
        usedPreferredHost || other?.usedPreferredHost == true -> "preferredHost"
        else -> "baseUrl"
    }
}

private fun SelectedCandidate.reason(other: SelectedCandidate? = null): String {
    return when {
        controlledProbe || other?.controlledProbe == true -> "allCandidatesCoolingDown"
        avoidedFailedHost || other?.avoidedFailedHost == true -> "failedHostAvoided"
        avoidedWeakHost || other?.avoidedWeakHost == true -> "weakHostAvoided"
        usedPreferredHost || other?.usedPreferredHost == true -> "preferredHost"
        else -> "normal"
    }
}

private fun List<String>.hostList(): String {
    return map { it.hostOnly() }.joinToString(prefix = "[", postfix = "]")
}

private fun VideoItem.isPgcPlayback(): Boolean {
    return contentType == VideoContentType.PGC || epId > 0L
}

private fun VideoItem.videoReferer(): String {
    return when {
        isPgcPlayback() && epId > 0L ->
            "https://www.bilibili.com/bangumi/play/ep$epId"
        bvid.isNotBlank() ->
            "https://www.bilibili.com/video/$bvid"
        else ->
            BilibiliApiClient.REFERER
    }
}

private fun estimateExpireInfo(urls: List<String>): Pair<Boolean, Long?> {
    val keys = setOf("expires", "deadline", "nbs", "gen", "trid", "oi", "mid")
    var hasExpiringKey = false
    var expireTime: Long? = null
    urls.forEach { url ->
        val query = runCatching { URI(url).rawQuery.orEmpty() }.getOrDefault("")
        if (query.isBlank()) return@forEach
        query.split("&").forEach { part ->
            val key = part.substringBefore("=")
            val value = part.substringAfter("=", "")
            if (key in keys) {
                hasExpiringKey = true
            }
            if (key == "expires" || key == "deadline") {
                value.toLongOrNull()?.let { candidate ->
                    expireTime = when (val current = expireTime) {
                        null -> candidate
                        else -> minOf(current, candidate)
                    }
                }
            }
        }
    }
    return hasExpiringKey to expireTime
}
