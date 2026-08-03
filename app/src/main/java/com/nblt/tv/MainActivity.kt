package com.nblt.tv

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nblt.tv.data.MockVideoRepository
import com.nblt.tv.data.api.LoginPollResult
import com.nblt.tv.data.repository.DynamicRepository
import com.nblt.tv.data.repository.HeartbeatRepository
import com.nblt.tv.data.repository.HistoryRepository
import com.nblt.tv.data.repository.InteractionRepository
import com.nblt.tv.data.repository.LoginRepository
import com.nblt.tv.data.repository.PlayerRepository
import com.nblt.tv.data.repository.PopularRepository
import com.nblt.tv.data.repository.RecommendRepository
import com.nblt.tv.data.repository.SearchRepository
import com.nblt.tv.data.repository.FavoriteRepository
import com.nblt.tv.data.repository.DuplicateUpSpaceRequestException
import com.nblt.tv.data.repository.UpSpaceVideoErrors
import com.nblt.tv.data.repository.UserRepository
import com.nblt.tv.data.repository.WatchLaterRepository
import com.nblt.tv.model.UpProfile
import com.nblt.tv.model.UpVideoItem
import com.nblt.tv.data.storage.DanmakuBlocklistStorage
import com.nblt.tv.data.storage.DanmakuSettingsStorage
import com.nblt.tv.data.storage.PlaybackSettingsStorage
import com.nblt.tv.data.storage.HomeDynamicSnapshot
import com.nblt.tv.data.storage.HomeHistorySnapshot
import com.nblt.tv.data.storage.HomeSnapshotStorage
import com.nblt.tv.data.api.BilibiliVideoDetailApi
import com.nblt.tv.data.api.SessionExpiredException
import com.nblt.tv.model.DanmakuSettings
import com.nblt.tv.model.FavoriteFolder
import com.nblt.tv.model.LoginQrCode
import com.nblt.tv.model.PlaybackEndBehavior
import com.nblt.tv.model.PlaybackProfile
import com.nblt.tv.model.PlayUrl
import com.nblt.tv.model.FollowedUp
import com.nblt.tv.model.SearchSuggestion
import com.nblt.tv.model.UserInfo
import com.nblt.tv.model.VideoItem
import com.nblt.tv.model.VideoPage
import com.nblt.tv.model.isSameVideo
import com.nblt.tv.model.mergeDetailFields
import com.nblt.tv.storage.CookieStorage
import com.nblt.tv.theme.NbltTheme
import com.nblt.tv.ui.components.ExitConfirmDialog
import com.nblt.tv.ui.detail.DetailRelatedSources
import com.nblt.tv.ui.detail.VideoDetailScreen
import com.nblt.tv.ui.detail.resolveDetailRelatedVideos
import com.nblt.tv.ui.home.HomeNavTabs
import com.nblt.tv.ui.favorite.FavoriteFolderDetailScreen
import com.nblt.tv.ui.favorite.FavoriteScreen
import com.nblt.tv.ui.home.HomeScreen
import com.nblt.tv.ui.home.HomeVideoFocusRestore
import com.nblt.tv.ui.home.homeFocusKey
import com.nblt.tv.ui.home.isOpenableVideo
import com.nblt.tv.ui.home.logOpenDetailFromTab
import com.nblt.tv.ui.my.MyMenuItem
import com.nblt.tv.ui.login.LoginScreen
import com.nblt.tv.ui.player.PlayerScreen
import com.nblt.tv.ui.player.isLowMemoryPlaybackDevice
import com.nblt.tv.ui.player.logPlayerMemory
import com.nblt.tv.ui.settings.DanmakuBlocklistScreen
import com.nblt.tv.ui.watchlater.WatchLaterScreen
import com.nblt.tv.theme.NbltBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.ui.state.DynamicUiState
import com.nblt.tv.ui.state.HistoryUiState
import com.nblt.tv.ui.state.PagedUpVideoList
import com.nblt.tv.ui.state.PagedVideoList
import com.nblt.tv.ui.state.UiState
import com.nblt.tv.ui.upspace.UpSpaceScreen
import com.nblt.tv.util.PaginationLoadGuard
import com.nblt.tv.util.UpSpaceDebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NbltTvApp()
        }
    }
}

private enum class Screen {
    Home,
    Detail,
    Player,
    Login,
    FavoriteFolders,
    FavoriteFolderDetail,
    WatchLater,
    DanmakuBlocklist,
    UpSpace
}

private enum class DetailReturnTarget {
    Home,
    FavoriteFolderDetail,
    WatchLater,
    UpSpace
}

private data class NavigationStackItem(
    val screen: Screen,
    val tab: String,
    val video: VideoItem?,
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val upMid: Long,
    val upName: String,
    val detailReturnTarget: DetailReturnTarget
)

@Composable
private fun NbltTvApp() {
    val context = LocalContext.current
    val playbackSettingsStorage = remember { PlaybackSettingsStorage(context.applicationContext) }
    val initialStartupTab = remember { playbackSettingsStorage.getStartupTab() }
    var screen by remember { mutableStateOf(Screen.Home) }
    var selectedHomeTab by remember { mutableStateOf(initialStartupTab) }
    var selectedDynamicUpMid by remember { mutableStateOf<Long?>(null) }
    var selectedVideo by remember { mutableStateOf<VideoItem>(MockVideoRepository.videos.first()) }
    var recommendState by remember { mutableStateOf<UiState<PagedVideoList>?>(null) }
    var popularState by remember { mutableStateOf<UiState<PagedVideoList>?>(null) }
    var searchSuggestionsState by remember { mutableStateOf<UiState<List<SearchSuggestion>>>(UiState.Loading) }
    var searchResultsState by remember { mutableStateOf<UiState<PagedVideoList>?>(null) }
    var currentSearchKeyword by remember { mutableStateOf("") }
    var dynamicState by remember { mutableStateOf<DynamicUiState?>(null) }
    var upVideoState by remember { mutableStateOf<UiState<PagedVideoList>?>(null) }
    var loadingUpVideoMid by remember { mutableStateOf<Long?>(null) }
    var loadedUpVideoMid by remember { mutableStateOf<Long?>(null) }
    var historyState by remember { mutableStateOf<HistoryUiState?>(null) }
    var playUrlState by remember { mutableStateOf<UiState<PlayUrl>>(UiState.Loading) }
    var playUrlRequestToken by remember { mutableLongStateOf(0L) }
    var currentPlayerQualityQn by remember { mutableStateOf(0) }
    var loginQrState by remember { mutableStateOf<UiState<LoginQrCode>>(UiState.Loading) }
    var loginMessage by remember { mutableStateOf("") }
    var currentUser by remember { mutableStateOf<UserInfo?>(null) }
    var preferredQualityQn by remember { mutableStateOf(playbackSettingsStorage.getPreferredQualityQn()) }
    var defaultPlaybackSpeed by remember { mutableStateOf(playbackSettingsStorage.getDefaultPlaybackSpeed()) }
    var playbackProfile by remember { mutableStateOf(playbackSettingsStorage.getPlaybackProfile()) }
    var currentPlayerProfile by remember { mutableStateOf(playbackProfile) }
    var attemptedPlaybackProfiles by remember { mutableStateOf<Set<PlaybackProfile>>(emptySet()) }
    var danmakuSettings by remember { mutableStateOf(DanmakuSettings()) }
    var startupTab by remember { mutableStateOf(initialStartupTab) }
    var autoPlayNextPageEnabled by remember { mutableStateOf(playbackSettingsStorage.isAutoPlayNextPageEnabled()) }
    var playbackEndBehavior by remember { mutableStateOf(playbackSettingsStorage.getPlaybackEndBehavior()) }
    var videoDetailState by remember { mutableStateOf<UiState<Unit>?>(null) }
    var detailReturnTarget by remember { mutableStateOf(DetailReturnTarget.Home) }
    var favoriteFoldersState by remember { mutableStateOf<UiState<List<FavoriteFolder>>?>(null) }
    var selectedFavoriteFolder by remember { mutableStateOf<FavoriteFolder?>(null) }
    var favoriteVideosState by remember { mutableStateOf<UiState<PagedVideoList>?>(null) }
    var watchLaterState by remember { mutableStateOf<UiState<PagedVideoList>?>(null) }
    var danmakuBlocklistEnabled by remember { mutableStateOf(true) }
    var danmakuBlocklist by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedUpMid by remember { mutableStateOf(0L) }
    var selectedUpDisplayName by remember { mutableStateOf("") }
    var upProfileState by remember { mutableStateOf<UiState<UpProfile>?>(null) }
    var upVideosState by remember { mutableStateOf<UiState<PagedUpVideoList>?>(null) }
    var playerUpProfileState by remember { mutableStateOf<UiState<UpProfile>?>(null) }
    var playerUpVideosState by remember { mutableStateOf<UiState<List<VideoItem>>?>(null) }
    var playerUpVideoPage by remember { mutableStateOf(1) }
    var playerUpVideoHasMore by remember { mutableStateOf(false) }
    var playerUpVideoLoadingMore by remember { mutableStateOf(false) }
    var playerUpVideoLoadMoreError by remember { mutableStateOf<String?>(null) }
    var upSpaceReturnScreen by remember { mutableStateOf(Screen.Home) }
    var upSpaceReturnHomeTab by remember { mutableStateOf(initialStartupTab) }
    var upSpaceReturnDetailVideo by remember { mutableStateOf<VideoItem?>(null) }
    var upSpaceReturnDetailTarget by remember { mutableStateOf<DetailReturnTarget?>(null) }
    var homeTabFromMyEntry by remember { mutableStateOf<MyMenuItem?>(null) }
    var restoreMyFocusEntry by remember { mutableStateOf<MyMenuItem?>(null) }
    var homeVideoFocusRestore by remember { mutableStateOf<HomeVideoFocusRestore?>(null) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var navigationBackStack by remember { mutableStateOf<List<NavigationStackItem>>(emptyList()) }
    var upSpaceLoadingPage by remember { mutableStateOf<Int?>(null) }
    var loginAwaitingUserInfo by remember { mutableStateOf(false) }
    var loginJob by remember { mutableStateOf<Job?>(null) }
    var startupRefreshHistoryMid by remember { mutableLongStateOf(0L) }
    var startupRefreshDynamicMid by remember { mutableLongStateOf(0L) }
    val paginationGuard = remember { PaginationLoadGuard() }
    val cookieStorage = remember { CookieStorage(context.applicationContext) }
    val danmakuSettingsStorage = remember { DanmakuSettingsStorage(context.applicationContext) }
    val danmakuBlocklistStorage = remember { DanmakuBlocklistStorage(context.applicationContext) }
    val homeSnapshotStorage = remember { HomeSnapshotStorage(context.applicationContext) }
    val loginRepository = remember { LoginRepository(cookieStorage) }
    val dynamicRepository = remember { DynamicRepository(cookieStorage) }
    val historyRepository = remember { HistoryRepository(cookieStorage) }
    val favoriteRepository = remember { FavoriteRepository(cookieStorage) }
    val interactionRepository = remember { InteractionRepository(cookieStorage) }
    val watchLaterRepository = remember { WatchLaterRepository(cookieStorage) }
    val userRepository = remember { UserRepository(cookieStorage) }
    val heartbeatRepository = remember { HeartbeatRepository(cookieStorage) }
    val recommendRepository = remember { RecommendRepository(cookieStorage) }
    val popularRepository = remember {
        PopularRepository(cookieStorage = cookieStorage)
    }
    val searchRepository = remember { SearchRepository(cookieStorage) }
    val playerRepository = remember { PlayerRepository(cookieStorage = cookieStorage) }
    val videoDetailApi = remember { BilibiliVideoDetailApi() }
    val saveableStateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val lowMemoryPlaybackMode = remember { isLowMemoryPlaybackDevice() }

    LaunchedEffect(Unit) {
        danmakuSettings = danmakuSettingsStorage.load()
        danmakuBlocklistEnabled = danmakuBlocklistStorage.isEnabled()
        danmakuBlocklist = danmakuBlocklistStorage.loadKeywords()
        val savedUser = loginRepository.getSavedUserInfo()
        currentUser = savedUser
        if (savedUser != null) {
            homeSnapshotStorage.activateUser(savedUser.mid)
            homeSnapshotStorage.loadHistory(savedUser.mid)?.let { snapshot ->
                historyState = HistoryUiState.Success(
                    videos = snapshot.videos,
                    cursorMax = snapshot.cursorMax,
                    cursorViewAt = snapshot.cursorViewAt,
                    hasMore = snapshot.hasMore
                )
                startupRefreshHistoryMid = savedUser.mid
                Log.i(TAG_HOME_SNAPSHOT, "restored history snapshot, mid=${savedUser.mid}, count=${snapshot.videos.size}")
            }
            homeSnapshotStorage.loadDynamic(savedUser.mid)?.let { snapshot ->
                dynamicState = DynamicUiState.Success(
                    com.nblt.tv.data.repository.DynamicHomeData(
                        followedUps = snapshot.followedUps,
                        videos = snapshot.videos,
                        offset = snapshot.offset,
                        hasMore = snapshot.hasMore
                    )
                )
                startupRefreshDynamicMid = savedUser.mid
                Log.i(TAG_HOME_SNAPSHOT, "restored dynamic snapshot, mid=${savedUser.mid}, count=${snapshot.videos.size}")
            }
        }
        if (loginRepository.isLoggedIn()) {
            loginRepository.fetchUserInfo().fold(
                onSuccess = {
                    if (savedUser != null && savedUser.mid != it.mid) {
                        Log.i(TAG_HOME_SNAPSHOT, "startup account changed, clear snapshot, oldMid=${savedUser.mid}, newMid=${it.mid}")
                        homeSnapshotStorage.clearAll()
                        historyState = null
                        dynamicState = null
                        startupRefreshHistoryMid = 0L
                        startupRefreshDynamicMid = 0L
                    }
                    homeSnapshotStorage.activateUser(it.mid)
                    currentUser = it
                    Log.i(TAG_LOGIN, "startup fetch user info success, mid=${it.mid}")
                },
                onFailure = {
                    Log.w(TAG_LOGIN, "startup fetch user info failed, clear half login state")
                    loginRepository.logout()
                    homeSnapshotStorage.clearAll()
                    currentUser = null
                    dynamicState = null
                    historyState = null
                }
            )
        }
    }

    LaunchedEffect(screen, selectedVideo.ownerMid, selectedVideo.ownerName) {
        val ownerMid = selectedVideo.ownerMid
        if (screen != Screen.Player || ownerMid <= 0L) {
            playerUpProfileState = null
            playerUpVideosState = null
            playerUpVideoPage = 1
            playerUpVideoHasMore = false
            playerUpVideoLoadingMore = false
            playerUpVideoLoadMoreError = null
            return@LaunchedEffect
        }
        val ownerName = selectedVideo.ownerName
        val cachedProfile = userRepository.getCachedProfile(ownerMid)
        val cachedVideos = userRepository.getCachedFirstPage(ownerMid)
        playerUpProfileState = cachedProfile?.let { UiState.Success(it) } ?: UiState.Loading
        playerUpVideosState = cachedVideos?.let { cached ->
            playerUpVideoPage = cached.page
            playerUpVideoHasMore = cached.hasMore
            UiState.Success(cached.videos.map { it.toVideoItem(ownerMid, ownerName) })
        } ?: UiState.Loading

        coroutineScope {
            if (cachedProfile == null) {
                launch {
                    playerUpProfileState = userRepository.loadUpProfile(ownerMid).fold(
                        onSuccess = { UiState.Success(it) },
                        onFailure = { UiState.Error(it.message ?: "UP 主资料加载失败") }
                    )
                }
            }
            if (cachedVideos == null) {
                launch {
                    playerUpVideosState = userRepository.loadUpVideos(ownerMid, ownerName, page = 1).fold(
                        onSuccess = { page ->
                            playerUpVideoPage = page.page
                            playerUpVideoHasMore = page.hasMore
                            UiState.Success(page.videos.map { it.toVideoItem(ownerMid, ownerName) })
                        },
                        onFailure = { UiState.Error(it.message ?: "UP 主投稿加载失败") }
                    )
                }
            }
        }
    }

    fun loadMorePlayerUpVideos() {
        val ownerMid = selectedVideo.ownerMid
        if (
            screen != Screen.Player || ownerMid <= 0L ||
            playerUpVideoLoadingMore || !playerUpVideoHasMore
        ) return
        val ownerName = selectedVideo.ownerName
        val nextPage = playerUpVideoPage + 1
        playerUpVideoLoadingMore = true
        playerUpVideoLoadMoreError = null
        scope.launch {
            userRepository.loadUpVideos(ownerMid, ownerName, page = nextPage).fold(
                onSuccess = { page ->
                    if (screen == Screen.Player && selectedVideo.ownerMid == ownerMid) {
                        val existing = (playerUpVideosState as? UiState.Success)?.data.orEmpty()
                        playerUpVideosState = UiState.Success(
                            (existing + page.videos.map { it.toVideoItem(ownerMid, ownerName) })
                                .distinctBy { item -> item.bvid.ifBlank { "aid:${item.aid}" } }
                        )
                        playerUpVideoPage = page.page
                        playerUpVideoHasMore = page.hasMore
                        playerUpVideoLoadMoreError = null
                    }
                },
                onFailure = { error ->
                    Log.w(TAG_UP_SPACE, "player panel load more failed: ${error.message}")
                    playerUpVideoLoadMoreError = "加载更多失败，继续向右可重试"
                }
            )
            playerUpVideoLoadingMore = false
        }
    }

    fun completeLogin(user: UserInfo) {
        loginAwaitingUserInfo = false
        homeSnapshotStorage.clearAll()
        homeSnapshotStorage.activateUser(user.mid)
        currentUser = user
        dynamicState = null
        historyState = null
        favoriteFoldersState = null
        favoriteVideosState = null
        watchLaterState = null
        selectedFavoriteFolder = null
        screen = Screen.Home
    }

    fun retryFetchUserInfo() {
        loginMessage = "\u6b63\u5728\u83b7\u53d6\u7528\u6237\u4fe1\u606f..."
        scope.launch {
            loginRepository.fetchUserInfo().fold(
                onSuccess = {
                    Log.i(TAG_LOGIN, "retry fetch user info success, mid=${it.mid}")
                    completeLogin(it)
                },
                onFailure = {
                    loginAwaitingUserInfo = true
                    loginMessage = "\u767b\u5f55\u9a8c\u8bc1\u6210\u529f\uff0c\u4f46\u83b7\u53d6\u7528\u6237\u4fe1\u606f\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
                    Log.w(TAG_LOGIN, "retry fetch user info failed: ${it.message}")
                }
            )
        }
    }

    fun logout() {
        loginJob?.cancel()
        loginRepository.logout()
        homeSnapshotStorage.clearAll()
        loginAwaitingUserInfo = false
        currentUser = null
        dynamicState = null
        historyState = null
        favoriteFoldersState = null
        favoriteVideosState = null
        watchLaterState = null
        selectedFavoriteFolder = null
    }

    fun handleSessionExpired() {
        Log.i(TAG_SESSION, "session expired, clear login state")
        loginJob?.cancel()
        loginRepository.logout()
        homeSnapshotStorage.clearAll()
        currentUser = null
        dynamicState = DynamicUiState.Error(SessionExpiredException.MESSAGE)
        historyState = HistoryUiState.Error(SessionExpiredException.MESSAGE)
        favoriteFoldersState = UiState.Error(SessionExpiredException.MESSAGE)
        watchLaterState = UiState.Error(SessionExpiredException.MESSAGE)
        upProfileState = UiState.Error(SessionExpiredException.MESSAGE)
        upVideosState = UiState.Error(SessionExpiredException.MESSAGE)
        if (favoriteVideosState is UiState.Loading || favoriteVideosState is UiState.Success) {
            favoriteVideosState = UiState.Error(SessionExpiredException.MESSAGE)
        }
        if (videoDetailState is UiState.Loading) {
            videoDetailState = UiState.Error(SessionExpiredException.MESSAGE)
        }
        if (playUrlState is UiState.Loading) {
            playUrlState = UiState.Error(SessionExpiredException.MESSAGE)
        }
    }

    fun resolveApiFailure(error: Throwable, defaultMessage: String): String {
        return if (SessionExpiredException.isExpired(error)) {
            handleSessionExpired()
            SessionExpiredException.MESSAGE
        } else {
            val raw = error.message.orEmpty()
            when {
                raw.contains("No playable URL", ignoreCase = true) ->
                    "暂时没有可用播放线路，请重试或选择其他视频"
                raw.contains("啥都木有") ->
                    "B站没有返回可用播放地址，请返回详情页后重新选择本集"
                raw.contains("大会员") || raw.contains("会员专享") ->
                    "本集需要大会员或相应播放权限"
                raw.contains("地区") || raw.contains("区域") ->
                    "你所在的地区暂时无法播放本集"
                raw.contains("试看") ->
                    "当前账号只能试看本集，完整播放需要相应权限"
                raw.contains("Missing cid", ignoreCase = true) ->
                    "视频播放信息不完整，请返回后重试"
                raw.startsWith("HTTP 403") || raw.startsWith("Bilibili playurl HTTP 403") ->
                    "播放线路被拒绝，正在尝试重新获取"
                raw.startsWith("HTTP 404") -> "请求的内容已失效或不存在"
                raw.startsWith("HTTP 412") -> "请求过于频繁，请稍后重试"
                raw.startsWith("HTTP 5") -> "B站服务暂时不可用，请稍后重试"
                raw.isBlank() -> defaultMessage
                else -> raw
            }
        }
    }

    fun loadRecommendVideosInternal(isRefresh: Boolean) {
        val oldVideos = (recommendState as? UiState.Success)?.data?.videos.orEmpty()
        if (isRefresh) {
            Log.i(TAG_REFRESH, "refresh tab name=推荐, refresh start")
        }
        recommendState = UiState.Loading
        scope.launch {
            recommendState = recommendRepository.getRecommendVideos(
                forceRefresh = isRefresh,
                currentUserPresent = currentUser != null
            ).fold(
                onSuccess = {
                    if (isRefresh) {
                        Log.i(TAG_REFRESH, "refresh tab name=推荐, refresh success")
                    }
                    if (isRefresh) {
                        logRefreshResult(
                            tab = "\u63a8\u8350",
                            oldVideos = oldVideos,
                            newVideos = it
                        )
                    }
                    if (isRefresh && it.isEmpty() && oldVideos.isNotEmpty()) {
                        Log.i(TAG_REFRESH, "refresh tab=推荐, new list empty, keep old list")
                        UiState.Success(PagedVideoList(videos = oldVideos, hasMore = true))
                    } else {
                        UiState.Success(PagedVideoList(videos = it, page = 1, hasMore = it.isNotEmpty()))
                    }
                },
                onFailure = {
                    if (isRefresh) {
                        Log.i(TAG_REFRESH, "refresh tab name=推荐, refresh error=${it.message}")
                    }
                    UiState.Error(resolveApiFailure(it, "\u63a8\u8350\u89c6\u9891\u52a0\u8f7d\u5931\u8d25"))
                }
            )
        }
    }

    fun loadRecommendVideos() = loadRecommendVideosInternal(isRefresh = false)

    fun reloadRecommendVideos() = loadRecommendVideosInternal(isRefresh = true)

    fun loadMoreRecommendVideos() {
        val current = (recommendState as? UiState.Success)?.data ?: return
        val nextPage = current.page + 1
        val pageKey = nextPage.toString()
        if (!paginationGuard.tryStart("recommend", pageKey, current.isLoadingMore, current.hasMore)) {
            return
        }
        Log.i(TAG_PAGINATION, "tab name=推荐, current page=${current.page}, load more start")
        recommendState = UiState.Success(current.copy(isLoadingMore = true, loadMoreError = null))
        scope.launch {
            recommendState = recommendRepository.getRecommendVideosPage(
                page = nextPage,
                currentUserPresent = currentUser != null
            ).fold(
                onSuccess = {
                    paginationGuard.finish("recommend", pageKey)
                    val merged = appendUniqueVideos(current.videos, it)
                    val hasMore = it.isNotEmpty()
                    Log.i(TAG_PAGINATION, "tab name=推荐, current page=$nextPage, loaded count=${it.size}, total count=${merged.size}, hasMore=$hasMore")
                    UiState.Success(current.copy(videos = merged, page = nextPage, hasMore = hasMore, isLoadingMore = false, loadMoreError = null))
                },
                onFailure = {
                    paginationGuard.finish("recommend", pageKey)
                    Log.i(TAG_PAGINATION, "tab name=推荐, current page=$nextPage, load more error=${it.message}")
                    UiState.Success(current.copy(isLoadingMore = false, loadMoreError = resolveApiFailure(it, "\u52a0\u8f7d\u5931\u8d25")))
                }
            )
        }
    }

    fun loadPopularVideosInternal(isRefresh: Boolean) {
        val oldVideos = (popularState as? UiState.Success)?.data?.videos.orEmpty()
        if (isRefresh) {
            Log.i(TAG_REFRESH, "refresh tab name=热门, refresh start")
        }
        popularState = UiState.Loading
        scope.launch {
            popularState = popularRepository.getPopularVideos(forceRefresh = isRefresh).fold(
                onSuccess = {
                    if (isRefresh) {
                        Log.i(TAG_REFRESH, "refresh tab name=热门, refresh success")
                    }
                    if (isRefresh) {
                        logRefreshResult(
                            tab = "\u70ed\u95e8",
                            oldVideos = oldVideos,
                            newVideos = it
                        )
                    }
                    if (isRefresh && it.isEmpty() && oldVideos.isNotEmpty()) {
                        Log.i(TAG_REFRESH, "refresh tab=热门, new list empty, keep old list")
                        UiState.Success(PagedVideoList(videos = oldVideos, hasMore = true))
                    } else {
                        UiState.Success(PagedVideoList(videos = it, page = 1, hasMore = it.isNotEmpty()))
                    }
                },
                onFailure = {
                    if (isRefresh) {
                        Log.i(TAG_REFRESH, "refresh tab name=热门, refresh error=${it.message}")
                    }
                    UiState.Error(it.message ?: "\u70ed\u95e8\u89c6\u9891\u52a0\u8f7d\u5931\u8d25")
                }
            )
        }
    }

    fun loadPopularVideos() = loadPopularVideosInternal(isRefresh = false)

    fun reloadPopularVideos() = loadPopularVideosInternal(isRefresh = true)

    fun loadSearchSuggestions(keyword: String) {
        Log.i(TAG_SEARCH, "input keyword=$keyword")
        if (keyword.isBlank()) {
            searchSuggestionsState = UiState.Success(defaultSearchSuggestions())
            Log.i(TAG_SEARCH, "suggestion count=${defaultSearchSuggestions().size}, using fallback suggestions=true")
            return
        }
        searchSuggestionsState = UiState.Loading
        scope.launch {
            searchSuggestionsState = searchRepository.getSuggestions(keyword).fold(
                onSuccess = { UiState.Success(it) },
                onFailure = {
                    Log.i(TAG_SEARCH, "using fallback suggestions=true, reason=${it.message}")
                    UiState.Success(defaultSearchSuggestions())
                }
            )
        }
    }

    fun searchVideos(keyword: String) {
        if (keyword.isBlank()) {
            return
        }
        currentSearchKeyword = keyword
        searchResultsState = UiState.Loading
        scope.launch {
            searchResultsState = searchRepository.searchVideos(keyword, page = 1).fold(
                onSuccess = {
                    UiState.Success(PagedVideoList(videos = it, page = 1, hasMore = it.isNotEmpty()))
                },
                onFailure = {
                    val message = if (it.message?.contains("412") == true || it.message?.contains("限制") == true) {
                        "搜索请求被限制，请稍后再试"
                    } else {
                        it.message ?: "\u641c\u7d22\u5931\u8d25"
                    }
                    UiState.Error(message)
                }
            )
        }
    }

    fun clearSearchResults() {
        searchResultsState = null
    }

    fun loadMoreSearchVideos() {
        val current = (searchResultsState as? UiState.Success)?.data ?: return
        if (currentSearchKeyword.isBlank()) return
        val nextPage = current.page + 1
        val pageKey = nextPage.toString()
        if (!paginationGuard.tryStart("search", pageKey, current.isLoadingMore, current.hasMore)) {
            return
        }
        Log.i(TAG_SEARCH, "load more page=$nextPage")
        searchResultsState = UiState.Success(current.copy(isLoadingMore = true, loadMoreError = null))
        scope.launch {
            searchResultsState = searchRepository.searchVideos(currentSearchKeyword, nextPage).fold(
                onSuccess = {
                    paginationGuard.finish("search", pageKey)
                    val merged = appendUniqueVideos(current.videos, it)
                    UiState.Success(
                        current.copy(
                            videos = merged,
                            page = nextPage,
                            hasMore = it.isNotEmpty(),
                            isLoadingMore = false,
                            loadMoreError = null
                        )
                    )
                },
                onFailure = {
                    paginationGuard.finish("search", pageKey)
                    UiState.Success(current.copy(isLoadingMore = false, loadMoreError = resolveApiFailure(it, "\u52a0\u8f7d\u5931\u8d25")))
                }
            )
        }
    }

    fun loadMorePopularVideos() {
        val current = (popularState as? UiState.Success)?.data ?: return
        val nextPage = current.page + 1
        val pageKey = nextPage.toString()
        if (!paginationGuard.tryStart("popular", pageKey, current.isLoadingMore, current.hasMore)) {
            return
        }
        Log.i(TAG_PAGINATION, "tab name=热门, current page=${current.page}, load more start")
        popularState = UiState.Success(current.copy(isLoadingMore = true, loadMoreError = null))
        scope.launch {
            popularState = popularRepository.getPopularVideosPage(nextPage).fold(
                onSuccess = {
                    paginationGuard.finish("popular", pageKey)
                    val merged = appendUniqueVideos(current.videos, it)
                    val hasMore = it.isNotEmpty()
                    Log.i(TAG_PAGINATION, "tab name=热门, current page=$nextPage, loaded count=${it.size}, total count=${merged.size}, hasMore=$hasMore")
                    UiState.Success(current.copy(videos = merged, page = nextPage, hasMore = hasMore, isLoadingMore = false, loadMoreError = null))
                },
                onFailure = {
                    paginationGuard.finish("popular", pageKey)
                    Log.i(TAG_PAGINATION, "tab name=热门, current page=$nextPage, load more error=${it.message}")
                    UiState.Success(current.copy(isLoadingMore = false, loadMoreError = resolveApiFailure(it, "\u52a0\u8f7d\u5931\u8d25")))
                }
            )
        }
    }

    fun loadPlayUrl(
        video: VideoItem,
        requestedQn: Int? = null,
        avoidedHosts: Set<String> = emptySet(),
        profile: PlaybackProfile = currentPlayerProfile,
        enableMediaPreconnect: Boolean = false
    ) {
        val requestedEffectiveQn = requestedQn ?: currentPlayerQualityQn.takeIf { it > 0 } ?: preferredQualityQn
        val effectiveQn = when {
            lowMemoryPlaybackMode && (requestedEffectiveQn == 0 || requestedEffectiveQn > 80) -> 80
            else -> requestedEffectiveQn
        }
        val requestToken = System.nanoTime()
        playUrlRequestToken = requestToken
        Log.i(TAG_PLAYER, "refresh play url start: bvid=${video.bvid}, aid=${video.aid}, currentScreen=$screen")
        Log.i(TAG_PROFILE, "current profile=${profile.name}, ua=${profile.shortName}, fallbackStep=playurl")
        if (effectiveQn != requestedEffectiveQn) {
            Log.i(TAG_MEMORY, "low memory playback quality capped: requested=$requestedEffectiveQn, effective=$effectiveQn")
        }
        Log.i(TAG_QUALITY, "player read preferred qn=$preferredQualityQn, request effective qn=$effectiveQn")
        Log.i(TAG_PLAYER_DEBUG, "playurl request token=$requestToken, cid=${video.cid}, qn=$effectiveQn, failedHosts=${avoidedHosts.joinToString(prefix = "[", postfix = "]")}")
        playUrlState = UiState.Loading
        scope.launch {
            val nextState = playerRepository.getPlayUrl(
                video = video,
                preferredQualityQn = effectiveQn,
                avoidedHosts = avoidedHosts,
                playbackProfile = profile,
                enableMediaPreconnect = enableMediaPreconnect
            ).fold(
                onSuccess = { result ->
                    if (playUrlRequestToken != requestToken || !selectedVideo.isSameVideo(video)) {
                        Log.w(
                            TAG_PLAYER_DEBUG,
                            "discard stale playurl result token=$requestToken, currentToken=$playUrlRequestToken"
                        )
                        return@launch
                    }
                    result.updatedVideo?.let { updated ->
                        if (updated.pages.isNotEmpty()) {
                            Log.i(TAG_PAGES, "playUrl loaded pages count=${updated.pages.size}")
                        }
                        if (selectedVideo.isSameVideo(updated)) {
                            selectedVideo = selectedVideo.mergeDetailFields(updated)
                            if (updated.pages.isNotEmpty()) {
                                Log.i(TAG_PAGES, "selectedVideo pages updated")
                            }
                        }
                    }
                    currentPlayerQualityQn = result.playUrl.quality?.qn ?: effectiveQn
                    Log.i(TAG_PLAYER, "refresh play url success: bvid=${video.bvid}, aid=${video.aid}")
                    Log.i(TAG_QUALITY, "selected actual qn=${result.playUrl.quality?.qn ?: 0}, selected actual description=${result.playUrl.quality?.description.orEmpty()}")
                    logPlayerMemory("playurl success bvid=${video.bvid} cid=${video.cid}")
                    UiState.Success(result.playUrl)
                },
                onFailure = {
                    if (playUrlRequestToken != requestToken || !selectedVideo.isSameVideo(video)) {
                        Log.w(
                            TAG_PLAYER_DEBUG,
                            "discard stale playurl error token=$requestToken, currentToken=$playUrlRequestToken"
                        )
                        return@launch
                    }
                    Log.e(TAG_PLAYER, "refresh play url fail: bvid=${video.bvid}, aid=${video.aid}", it)
                    UiState.Error(resolveApiFailure(it, "\u64ad\u653e\u5730\u5740\u52a0\u8f7d\u5931\u8d25"))
                }
            )
            playUrlState = nextState
        }
    }

    fun switchPlaybackProfileForRecovery(avoidedHosts: Set<String>): Boolean {
        val nextProfile = PlaybackProfile.fallbackOrder.firstOrNull {
            it != currentPlayerProfile && it !in attemptedPlaybackProfiles
        } ?: return false
        attemptedPlaybackProfiles = attemptedPlaybackProfiles + currentPlayerProfile + nextProfile
        currentPlayerProfile = nextProfile
        Log.i(
            TAG_PROFILE,
            "fallbackStep=switchProfile, current profile=${nextProfile.name}, ua=${nextProfile.shortName}, " +
                "attempted=${attemptedPlaybackProfiles.joinToString { it.name }}"
        )
        loadPlayUrl(selectedVideo, currentPlayerQualityQn.takeIf { it > 0 }, avoidedHosts, nextProfile)
        return true
    }

    fun loadVideoDetail(video: VideoItem) {
        videoDetailState = UiState.Loading
        scope.launch {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    videoDetailApi.fetchVideoWithCid(video)
                }
            }.onSuccess { fetched ->
                if (selectedVideo.isSameVideo(video)) {
                    selectedVideo = selectedVideo.mergeDetailFields(fetched)
                }
                videoDetailState = UiState.Success(Unit)
            }.onFailure { error ->
                Log.e(
                    TAG_PAGES,
                    "load video pages fail: bvid=${video.bvid}, aid=${video.aid}, error=${error.message}",
                    error
                )
                videoDetailState = UiState.Error(
                    resolveApiFailure(error, "\u5206 P \u4fe1\u606f\u52a0\u8f7d\u5931\u8d25")
                )
            }
        }
    }

    fun switchPlayerPage(page: VideoPage) {
        Log.i(
            TAG_PAGES,
            "selected page=${page.page}, cid=${page.cid}, aid=${page.aid}, " +
                "bvid=${page.bvid}, epId=${page.epId}"
        )
        val pageVideo = selectedVideo.copy(
            aid = page.aid.takeIf { it > 0L } ?: selectedVideo.aid,
            bvid = page.bvid.ifBlank { selectedVideo.bvid },
            cid = page.cid,
            coverUrl = page.coverUrl.ifBlank { selectedVideo.coverUrl },
            currentPage = page.page,
            duration = page.duration.toLong(),
            epId = page.epId.takeIf { it > 0L } ?: selectedVideo.epId,
            seasonId = page.seasonId.takeIf { it > 0L } ?: selectedVideo.seasonId,
            historyProgress = 0L,
            historyViewAt = 0L
        )
        selectedVideo = pageVideo
        currentPlayerQualityQn = (playUrlState as? UiState.Success)?.data?.quality?.qn ?: currentPlayerQualityQn
        attemptedPlaybackProfiles = setOf(currentPlayerProfile)
        loadPlayUrl(
            pageVideo,
            currentPlayerQualityQn.takeIf { it > 0 },
            profile = currentPlayerProfile,
            enableMediaPreconnect = true
        )
    }

    fun selectDetailPage(page: VideoPage) {
        Log.i(
            TAG_PAGES,
            "detail selected page=${page.page}, cid=${page.cid}, aid=${page.aid}, " +
                "bvid=${page.bvid}, epId=${page.epId}"
        )
        selectedVideo = selectedVideo.copy(
            aid = page.aid.takeIf { it > 0L } ?: selectedVideo.aid,
            bvid = page.bvid.ifBlank { selectedVideo.bvid },
            cid = page.cid,
            coverUrl = page.coverUrl.ifBlank { selectedVideo.coverUrl },
            currentPage = page.page,
            duration = page.duration.toLong(),
            epId = page.epId.takeIf { it > 0L } ?: selectedVideo.epId,
            seasonId = page.seasonId.takeIf { it > 0L } ?: selectedVideo.seasonId,
            historyProgress = 0L,
            historyViewAt = 0L
        )
    }

    fun handlePlaybackEnded() {
        val pages = selectedVideo.pages
        val currentPageIndex = pages.indexOfFirst { it.cid == selectedVideo.cid }
            .takeIf { it >= 0 }
            ?: pages.indexOfFirst { it.page == selectedVideo.currentPage }.takeIf { it >= 0 }
            ?: -1
        val nextPage = if (
            autoPlayNextPageEnabled &&
            pages.size > 1 &&
            currentPageIndex >= 0 &&
            currentPageIndex < pages.lastIndex
        ) {
            pages[currentPageIndex + 1]
        } else {
            null
        }
        Log.i(
            TAG_BEHAVIOR,
            "playback ended, auto play next enabled=$autoPlayNextPageEnabled, " +
                "current page index=$currentPageIndex, next page exists=${nextPage != null}"
        )
        if (nextPage != null) {
            Log.i(TAG_BEHAVIOR, "navigate target=next page P${nextPage.page}, cid=${nextPage.cid}")
            switchPlayerPage(nextPage)
            return
        }
        Log.i(TAG_BEHAVIOR, "end behavior=$playbackEndBehavior")
        when (playbackEndBehavior) {
            PlaybackEndBehavior.STAY_AT_END -> {
                Log.i(TAG_BEHAVIOR, "navigate target=stay at end")
            }
            PlaybackEndBehavior.BACK_TO_DETAIL -> {
                Log.i(TAG_BEHAVIOR, "navigate target=detail")
                screen = Screen.Detail
            }
            PlaybackEndBehavior.BACK_TO_LIST -> {
                Log.i(TAG_BEHAVIOR, "back to list target=$detailReturnTarget")
                screen = when (detailReturnTarget) {
                    DetailReturnTarget.Home -> Screen.Home
                    DetailReturnTarget.FavoriteFolderDetail -> Screen.FavoriteFolderDetail
                    DetailReturnTarget.WatchLater -> Screen.WatchLater
                    DetailReturnTarget.UpSpace -> Screen.UpSpace
                }
            }
        }
    }

    fun loadDynamicInternal(isRefresh: Boolean, keepVisibleContent: Boolean = false) {
        if (isRefresh) {
            Log.i(TAG_REFRESH, "refresh tab name=动态, refresh start")
        }
        upVideoState = null
        loadingUpVideoMid = null
        loadedUpVideoMid = null
        val requestUserMid = currentUser?.mid
        if (requestUserMid == null || requestUserMid <= 0L) {
            dynamicState = DynamicUiState.Error("\u8bf7\u5148\u767b\u5f55")
            if (isRefresh) {
                Log.i(TAG_REFRESH, "refresh tab name=动态, refresh error=not logged in")
            }
            return
        }
        val visibleState = dynamicState as? DynamicUiState.Success
        if (!keepVisibleContent || visibleState == null) {
            dynamicState = DynamicUiState.Loading
        }
        scope.launch {
            val nextState = dynamicRepository.loadDynamicHome().fold(
                onSuccess = {
                    if (isRefresh) {
                        Log.i(TAG_REFRESH, "refresh tab name=动态, refresh success")
                    }
                    if (it.videos.isEmpty() && it.followedUps.isEmpty()) {
                        scope.launch(Dispatchers.IO) { homeSnapshotStorage.clearDynamic(requestUserMid) }
                        DynamicUiState.Empty
                    } else {
                        scope.launch(Dispatchers.IO) {
                            homeSnapshotStorage.saveDynamic(
                                requestUserMid,
                                HomeDynamicSnapshot(
                                    followedUps = it.followedUps,
                                    videos = it.videos,
                                    offset = it.offset,
                                    hasMore = it.hasMore
                                )
                            )
                        }
                        DynamicUiState.Success(it)
                    }
                },
                onFailure = {
                    if (isRefresh) {
                        Log.i(TAG_REFRESH, "refresh tab name=动态, refresh error=${it.message}")
                    }
                    if (keepVisibleContent && visibleState != null) {
                        Log.w(TAG_HOME_SNAPSHOT, "dynamic refresh failed, keep snapshot, mid=$requestUserMid", it)
                        visibleState
                    } else {
                        DynamicUiState.Error(resolveApiFailure(it, "\u52a8\u6001\u52a0\u8f7d\u5931\u8d25"))
                    }
                }
            )
            if (currentUser?.mid == requestUserMid) {
                dynamicState = nextState
            }
        }
    }

    fun loadDynamic() = loadDynamicInternal(isRefresh = false)

    fun reloadDynamic() = loadDynamicInternal(isRefresh = true)

    fun loadMoreDynamicVideos() {
        val current = (dynamicState as? DynamicUiState.Success)?.data ?: return
        val pageKey = current.offset.orEmpty().ifBlank { "initial" }
        if (!paginationGuard.tryStart("dynamic", pageKey, current.isLoadingMore, current.hasMore)) {
            return
        }
        Log.i(TAG_PAGINATION, "tab name=动态, current cursor=${current.offset.orEmpty()}, load more start")
        dynamicState = DynamicUiState.Success(current.copy(isLoadingMore = true, loadMoreError = null))
        scope.launch {
            dynamicState = dynamicRepository.loadMoreDynamicVideos(current.offset).fold(
                onSuccess = {
                    paginationGuard.finish("dynamic", pageKey)
                    val merged = appendUniqueVideos(current.videos, it.videos)
                    Log.i(TAG_PAGINATION, "tab name=动态, current cursor=${it.offset.orEmpty()}, loaded count=${it.videos.size}, total count=${merged.size}, hasMore=${it.hasMore}")
                    DynamicUiState.Success(current.copy(videos = merged, offset = it.offset, hasMore = it.hasMore, isLoadingMore = false, loadMoreError = null))
                },
                onFailure = {
                    paginationGuard.finish("dynamic", pageKey)
                    Log.i(TAG_PAGINATION, "tab name=动态, current cursor=${current.offset.orEmpty()}, load more error=${it.message}")
                    DynamicUiState.Success(current.copy(isLoadingMore = false, loadMoreError = resolveApiFailure(it, "\u52a0\u8f7d\u5931\u8d25")))
                }
            )
        }
    }

    fun loadUpVideos(up: FollowedUp) {
        if (loadingUpVideoMid == up.mid) {
            Log.i(
                TAG_UP,
                "selected up name=${up.name}, selected mid=${up.mid}, " +
                    "whether request skipped because already loading=true"
            )
            return
        }
        if (loadedUpVideoMid == up.mid && upVideoState is UiState.Success) {
            Log.i(
                TAG_UP,
                "selected up name=${up.name}, selected mid=${up.mid}, whether using cache=true"
            )
            return
        }
        Log.i(
            TAG_NAV,
            "load up videos: currentScreen=$screen, currentTab=$selectedHomeTab, " +
                "selectedUpName=${up.name}, selectedUpMid=${up.mid}"
        )
        loadingUpVideoMid = up.mid
        upVideoState = UiState.Loading
        scope.launch {
            upVideoState = userRepository.loadUpVideos(up.mid, up.name, page = 1).fold(
                onSuccess = { page ->
                    val videos = page.videos.map { it.toVideoItem(up.mid, up.name) }
                    loadedUpVideoMid = up.mid
                    UiState.Success(PagedVideoList(videos = videos, page = page.page, hasMore = page.hasMore))
                },
                onFailure = {
                    if (it.message?.contains("频繁") == true) {
                        UiState.Error("请求太频繁，请稍后再试")
                    } else {
                        UiState.Error(resolveApiFailure(it, "\u6295\u7a3f\u89c6\u9891\u52a0\u8f7d\u5931\u8d25"))
                    }
                }
            )
            loadingUpVideoMid = null
        }
    }

    fun loadMoreUpVideos() {
        val selectedUp = (dynamicState as? DynamicUiState.Success)
            ?.data
            ?.followedUps
            ?.firstOrNull { it.mid == selectedDynamicUpMid } ?: return
        val current = (upVideoState as? UiState.Success)?.data ?: return
        val nextPage = current.page + 1
        val pageKey = nextPage.toString()
        if (!paginationGuard.tryStart("dynamic-up", pageKey, current.isLoadingMore, current.hasMore)) {
            return
        }
        Log.i(TAG_PAGINATION, "tab name=UP投稿, current page=${current.page}, load more start")
        upVideoState = UiState.Success(current.copy(isLoadingMore = true, loadMoreError = null))
        scope.launch {
            upVideoState = userRepository.loadUpVideos(selectedUp.mid, selectedUp.name, nextPage, forceRefresh = true).fold(
                onSuccess = { page ->
                    paginationGuard.finish("dynamic-up", pageKey)
                    val videos = page.videos.map { it.toVideoItem(selectedUp.mid, selectedUp.name) }
                    val merged = appendUniqueVideos(current.videos, videos)
                    Log.i(TAG_PAGINATION, "tab name=UP投稿, current page=$nextPage, loaded count=${videos.size}, total count=${merged.size}, hasMore=${page.hasMore}")
                    UiState.Success(current.copy(videos = merged, page = nextPage, hasMore = page.hasMore, isLoadingMore = false, loadMoreError = null))
                },
                onFailure = {
                    paginationGuard.finish("dynamic-up", pageKey)
                    Log.i(TAG_PAGINATION, "tab name=UP投稿, current page=$nextPage, load more error=${it.message}")
                    UiState.Success(current.copy(isLoadingMore = false, loadMoreError = resolveApiFailure(it, "\u52a0\u8f7d\u5931\u8d25")))
                }
            )
        }
    }

    fun loadHistoryInternal(isRefresh: Boolean, keepVisibleContent: Boolean = false) {
        if (isRefresh) {
            Log.i(TAG_REFRESH, "refresh tab name=历史, refresh start")
        }
        val requestUserMid = currentUser?.mid
        if (requestUserMid == null || requestUserMid <= 0L) {
            historyState = HistoryUiState.Error("\u8bf7\u5148\u767b\u5f55")
            if (isRefresh) {
                Log.i(TAG_REFRESH, "refresh tab name=历史, refresh error=not logged in")
            }
            return
        }
        val visibleState = historyState as? HistoryUiState.Success
        if (!keepVisibleContent || visibleState == null) {
            historyState = HistoryUiState.Loading
        }
        scope.launch {
            val nextState = historyRepository.loadHistory().fold(
                onSuccess = {
                    if (isRefresh) {
                        Log.i(TAG_REFRESH, "refresh tab name=历史, refresh success")
                    }
                    if (it.videos.isEmpty()) {
                        scope.launch(Dispatchers.IO) { homeSnapshotStorage.clearHistory(requestUserMid) }
                        HistoryUiState.Empty
                    } else {
                        val success = HistoryUiState.Success(
                            videos = it.videos,
                            cursorMax = it.cursorMax,
                            cursorViewAt = it.cursorViewAt,
                            hasMore = it.hasMore
                        )
                        scope.launch(Dispatchers.IO) {
                            homeSnapshotStorage.saveHistory(
                                requestUserMid,
                                HomeHistorySnapshot(
                                    videos = success.videos,
                                    cursorMax = success.cursorMax,
                                    cursorViewAt = success.cursorViewAt,
                                    hasMore = success.hasMore
                                )
                            )
                        }
                        success
                    }
                },
                onFailure = {
                    if (isRefresh) {
                        Log.i(TAG_REFRESH, "refresh tab name=历史, refresh error=${it.message}")
                    }
                    if (keepVisibleContent && visibleState != null) {
                        Log.w(TAG_HOME_SNAPSHOT, "history refresh failed, keep snapshot, mid=$requestUserMid", it)
                        visibleState
                    } else {
                        HistoryUiState.Error(resolveApiFailure(it, "\u5386\u53f2\u8bb0\u5f55\u52a0\u8f7d\u5931\u8d25"))
                    }
                }
            )
            if (currentUser?.mid == requestUserMid) {
                historyState = nextState
            }
        }
    }

    fun loadHistory() = loadHistoryInternal(isRefresh = false)

    fun reloadHistory() = loadHistoryInternal(isRefresh = true)

    LaunchedEffect(startupRefreshHistoryMid, startupRefreshDynamicMid) {
        val historyMid = startupRefreshHistoryMid
        if (historyMid > 0L && currentUser?.mid == historyMid) {
            startupRefreshHistoryMid = 0L
            loadHistoryInternal(isRefresh = false, keepVisibleContent = true)
        }
        val dynamicMid = startupRefreshDynamicMid
        if (dynamicMid > 0L && currentUser?.mid == dynamicMid) {
            startupRefreshDynamicMid = 0L
            loadDynamicInternal(isRefresh = false, keepVisibleContent = true)
        }
    }

    fun loadMoreHistory() {
        val current = historyState as? HistoryUiState.Success ?: return
        val pageKey = "${current.cursorMax}_${current.cursorViewAt}"
        if (!paginationGuard.tryStart("history", pageKey, current.isLoadingMore, current.hasMore)) {
            return
        }
        Log.i(TAG_PAGINATION, "tab name=历史, current cursor=${current.cursorMax}/${current.cursorViewAt}, load more start")
        historyState = current.copy(isLoadingMore = true, loadMoreError = null)
        scope.launch {
            historyState = historyRepository.loadMoreHistory(current.cursorMax, current.cursorViewAt).fold(
                onSuccess = {
                    paginationGuard.finish("history", pageKey)
                    val merged = appendUniqueVideos(current.videos, it.videos)
                    Log.i(TAG_PAGINATION, "tab name=历史, current cursor=${it.cursorMax}/${it.cursorViewAt}, loaded count=${it.videos.size}, total count=${merged.size}, hasMore=${it.hasMore}")
                    current.copy(videos = merged, cursorMax = it.cursorMax, cursorViewAt = it.cursorViewAt, hasMore = it.hasMore, isLoadingMore = false, loadMoreError = null)
                },
                onFailure = {
                    paginationGuard.finish("history", pageKey)
                    Log.i(TAG_PAGINATION, "tab name=历史, current cursor=${current.cursorMax}/${current.cursorViewAt}, load more error=${it.message}")
                    current.copy(isLoadingMore = false, loadMoreError = resolveApiFailure(it, "\u52a0\u8f7d\u5931\u8d25"))
                }
            )
        }
    }

    fun currentNavigationSnapshot(): NavigationStackItem {
        return NavigationStackItem(
            screen = screen,
            tab = selectedHomeTab,
            video = selectedVideo,
            bvid = selectedVideo.bvid,
            aid = selectedVideo.aid,
            cid = selectedVideo.cid,
            upMid = selectedUpMid,
            upName = selectedUpDisplayName,
            detailReturnTarget = detailReturnTarget
        )
    }

    fun pushNavigationStack(reason: String) {
        val item = currentNavigationSnapshot()
        navigationBackStack = navigationBackStack + item
        Log.i(
            TAG_NAV_STACK,
            "push reason=$reason current=${screen.name} sourceTab=$selectedHomeTab " +
                "bvid=${item.bvid} aid=${item.aid} cid=${item.cid} upMid=${item.upMid} stackSize=${navigationBackStack.size}"
        )
    }

    fun restoreNavigationItem(item: NavigationStackItem) {
        val wasUpSpace = screen == Screen.UpSpace
        screen = item.screen
        selectedHomeTab = item.tab
        detailReturnTarget = item.detailReturnTarget
        item.video?.let { restored ->
            selectedVideo = restored
            if (item.screen == Screen.Detail) {
                loadVideoDetail(restored)
            }
        }
        if (item.screen == Screen.UpSpace) {
            selectedUpMid = item.upMid
            selectedUpDisplayName = item.upName
        } else if (wasUpSpace) {
            selectedUpMid = 0L
            selectedUpDisplayName = ""
            upProfileState = null
            upVideosState = null
            upSpaceLoadingPage = null
        }
        Log.i(
            TAG_NAV_STACK,
            "current=${screen.name} target=${item.screen.name} sourceTab=${item.tab} " +
                "bvid=${item.bvid} aid=${item.aid} cid=${item.cid} upMid=${item.upMid}"
        )
    }

    fun popNavigationStack(reason: String): Boolean {
        val target = navigationBackStack.lastOrNull() ?: return false
        navigationBackStack = navigationBackStack.dropLast(1)
        Log.i(
            TAG_NAV_STACK,
            "pop reason=$reason current=${screen.name} target=${target.screen.name} " +
                "sourceTab=${target.tab} sourceScreen=${target.screen.name} stackSize=${navigationBackStack.size}"
        )
        restoreNavigationItem(target)
        return true
    }

    fun openVideoDetail(
        video: VideoItem,
        returnTarget: DetailReturnTarget,
        pushBackStack: Boolean = true
    ) {
        val fromScreen = screen
        if (pushBackStack) {
            pushNavigationStack("openDetail")
        }
        detailReturnTarget = returnTarget
        selectedVideo = video
        videoDetailState = UiState.Loading
        screen = Screen.Detail
        Log.i(
            TAG_NAV,
            "open detail video=${video.title} id=${video.aid} source=$returnTarget fromScreen=$fromScreen"
        )
        loadVideoDetail(video)
    }

    fun openHomeVideoDetail(video: VideoItem, index: Int) {
        if (!video.isOpenableVideo()) {
            Log.w(TAG_FOCUS, "open detail ignored: missing bvid/aid index=$index title=${video.title}")
            return
        }
        val tab = selectedHomeTab
        val dynamicUpMid = if (tab == HomeNavTabs.DYNAMIC) selectedDynamicUpMid else null
        logOpenDetailFromTab(tab, index, video)
        homeVideoFocusRestore = HomeVideoFocusRestore(
            tab = tab,
            videoKey = video.homeFocusKey(index),
            index = index,
            dynamicUpMid = dynamicUpMid,
            restoreToken = System.currentTimeMillis()
        )
        openVideoDetail(video, DetailReturnTarget.Home)
    }

    fun loadUpSpaceProfile(mid: Long, forceRefresh: Boolean = false) {
        val cached = userRepository.getCachedProfile(mid)
        if (!forceRefresh && cached != null) {
            selectedUpDisplayName = cached.nickname
            upProfileState = UiState.Success(cached)
            return
        }
        upProfileState = UiState.Loading
        scope.launch {
            upProfileState = userRepository.loadUpProfile(mid, forceRefresh = forceRefresh).fold(
                onSuccess = { profile ->
                    selectedUpDisplayName = profile.nickname
                    UiState.Success(profile)
                },
                onFailure = {
                    UiState.Error(resolveApiFailure(it, "\u7528\u6237\u8d44\u6599\u52a0\u8f7d\u5931\u8d25"))
                }
            )
        }
    }

    fun loadUpSpaceVideos(mid: Long, name: String, page: Int, forceRefresh: Boolean = false) {
        UpSpaceDebugLog.logRepositoryPath(
            path = "loadUpSpaceVideos called",
            detail = "mid=$mid, page=$page, forceRefresh=$forceRefresh, upName=$name"
        )
        if (page == 1 && !forceRefresh) {
            userRepository.getCachedFirstPage(mid)?.let { cached ->
                Log.i(
                    TAG_UP_SPACE,
                    "use cached videos mid=$mid count=${cached.videos.size}"
                )
                upVideosState = UiState.Success(
                    PagedUpVideoList(
                        videos = cached.videos,
                        page = cached.page,
                        hasMore = cached.hasMore
                    )
                )
                UpSpaceDebugLog.logUiStateTarget(
                    target = if (cached.videos.isEmpty()) "Empty" else "Success",
                    detail = "main cached first page mid=$mid count=${cached.videos.size}"
                )
                return
            }
        }

        if (page == 1 && forceRefresh && userRepository.isRetryCooldownActive()) {
            Log.i(TAG_UP_SPACE, "retry blocked by cooldown")
            UpSpaceDebugLog.logUiStateTarget(target = "Error", detail = "retry cooldown blocked")
            upVideosState = UiState.Error(UpSpaceVideoErrors.RETRY_COOLDOWN_MESSAGE)
            return
        }

        if (page == 1) {
            upVideosState = UiState.Loading
            UpSpaceDebugLog.logUiStateTarget(target = "Loading", detail = "page=1 before request")
        }

        Log.i(TAG_UP_SPACE, "request videos mid=$mid page=$page")
        scope.launch {
            userRepository.loadUpVideos(mid, name, page, forceRefresh = forceRefresh).fold(
                onSuccess = { result ->
                    if (page == 1) {
                        upVideosState = UiState.Success(
                            PagedUpVideoList(
                                videos = result.videos,
                                page = result.page,
                                hasMore = result.hasMore
                            )
                        )
                        UpSpaceDebugLog.logUiStateTarget(
                            target = if (result.videos.isEmpty()) "Empty" else "Success",
                            detail = "main page=1 count=${result.videos.size} hasMore=${result.hasMore}"
                        )
                    }
                },
                onFailure = { error ->
                    if (error is DuplicateUpSpaceRequestException) {
                        UpSpaceDebugLog.logRepositoryPath(
                            path = "duplicate ignored in main",
                            detail = "mid=$mid, page=$page"
                        )
                        return@fold
                    }
                    val message = resolveUpSpaceVideoFailure(
                        error,
                        defaultMessage = "\u6295\u7a3f\u5217\u8868\u52a0\u8f7d\u5931\u8d25"
                    )
                    UpSpaceDebugLog.logResponseClassification(
                        bilibiliCode = -1,
                        bilibiliMessage = error.message.orEmpty(),
                        sessionExpiredRecognized = UpSpaceDebugLog.isSessionExpiredThrowable(error),
                        rateLimitRecognized = UpSpaceDebugLog.isRateLimitMessage(error.message.orEmpty()),
                        mappedToRateLimitMessage = UpSpaceDebugLog.isMappedToRateLimitMessage(message)
                    )
                    if (page == 1) {
                        upVideosState = UiState.Error(message)
                        UpSpaceDebugLog.logUiStateTarget(
                            target = "Error",
                            detail = "main page=1 message=$message"
                        )
                    }
                }
            )
        }
    }

    fun retryUpSpaceVideos() {
        UpSpaceDebugLog.logRepositoryPath(path = "retry clicked", detail = "mid=$selectedUpMid")
        if (userRepository.isRetryCooldownActive()) {
            Log.i(TAG_UP_SPACE, "retry blocked by cooldown")
            upVideosState = UiState.Error(UpSpaceVideoErrors.RETRY_COOLDOWN_MESSAGE)
            return
        }
        Log.i(TAG_UP_SPACE, "retry allowed mid=$selectedUpMid")
        userRepository.invalidateVideoCache(selectedUpMid)
        loadUpSpaceVideos(
            mid = selectedUpMid,
            name = selectedUpDisplayName,
            page = 1,
            forceRefresh = true
        )
    }

    fun openUpSpace(mid: Long, name: String) {
        if (mid <= 0L) {
            return
        }
        if (screen != Screen.UpSpace) {
            pushNavigationStack("openUpSpace")
        }
        Log.i(TAG_UP_SPACE, "open up space mid=$mid")
        UpSpaceDebugLog.logOpenUpSpace(
            mid = mid,
            upName = name,
            sourceScreen = screen.name,
            sourceTab = selectedHomeTab
        )
        if (screen == Screen.UpSpace) {
            Log.i(
                TAG_NAV,
                "open up space mid=$mid already on UpSpace, keep return stack unchanged"
            )
        } else {
            Log.i(TAG_NAV, "open up space mid=$mid sourceScreen=${screen.name}")
        }
        selectedUpMid = mid
        selectedUpDisplayName = name
        upSpaceLoadingPage = null
        screen = Screen.UpSpace

        val cachedProfile = userRepository.getCachedProfile(mid)
        if (cachedProfile != null) {
            selectedUpDisplayName = cachedProfile.nickname
            upProfileState = UiState.Success(cachedProfile)
        } else {
            upProfileState = UiState.Loading
            loadUpSpaceProfile(mid)
        }

        val cachedVideos = userRepository.getCachedFirstPage(mid)
        if (cachedVideos != null) {
            Log.i(
                TAG_UP_SPACE,
                "use cached videos mid=$mid count=${cachedVideos.videos.size}"
            )
            upVideosState = UiState.Success(
                PagedUpVideoList(
                    videos = cachedVideos.videos,
                    page = cachedVideos.page,
                    hasMore = cachedVideos.hasMore
                )
            )
            UpSpaceDebugLog.logUiStateTarget(
                target = if (cachedVideos.videos.isEmpty()) "Empty" else "Success",
                detail = "openUpSpace cache mid=$mid"
            )
        } else if (userRepository.isVideoRequestInFlight(mid, page = 1)) {
            if (upVideosState !is UiState.Loading) {
                upVideosState = UiState.Loading
            }
            UpSpaceDebugLog.logUiStateTarget(target = "Loading", detail = "openUpSpace inFlight page=1")
        } else {
            loadUpSpaceVideos(mid, name, page = 1, forceRefresh = false)
        }
    }

    fun handleUpClick(mid: Long, name: String) {
        if (mid > 0L) {
            openUpSpace(mid, name)
        } else {
            Toast.makeText(
                context,
                "\u65e0\u6cd5\u6253\u5f00 UP \u7a7a\u95f4\uff0c\u7f3a\u5c11 UP \u4fe1\u606f",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun loadMoreUpSpaceVideos() {
        val current = (upVideosState as? UiState.Success)?.data ?: return
        val nextPage = current.page + 1
        val pageKey = nextPage.toString()
        val listKey = "upspace"
        UpSpaceDebugLog.logLoadMoreTrigger(
            videosSize = current.videos.size,
            hasMore = current.hasMore,
            isLoadingMore = current.isLoadingMore,
            loadMoreError = current.loadMoreError
        )
        val guardAllowed = paginationGuard.tryStart(listKey, pageKey, current.isLoadingMore, current.hasMore)
        if (!guardAllowed) {
            return
        }
        upSpaceLoadingPage = nextPage
        Log.i(TAG_UP_SPACE, "load more page=$nextPage")
        upVideosState = UiState.Success(current.copy(isLoadingMore = true, loadMoreError = null))
        scope.launch {
            userRepository.loadUpVideos(
                mid = selectedUpMid,
                upName = selectedUpDisplayName,
                page = nextPage,
                forceRefresh = true
            ).fold(
                onSuccess = { result ->
                    paginationGuard.finish(listKey, pageKey)
                    upSpaceLoadingPage = null
                    val merged = appendUniqueUpVideos(current.videos, result.videos)
                    userRepository.updateCachedVideos(
                        mid = selectedUpMid,
                        videos = merged,
                        page = nextPage,
                        hasMore = result.hasMore
                    )
                    upVideosState = UiState.Success(
                        current.copy(
                            videos = merged,
                            page = nextPage,
                            hasMore = result.hasMore,
                            isLoadingMore = false,
                            loadMoreError = null
                        )
                    )
                    UpSpaceDebugLog.logUiStateTarget(
                        target = "Success",
                        detail = "loadMore page=$nextPage mergedCount=${merged.size}"
                    )
                },
                onFailure = { error ->
                    paginationGuard.finish(listKey, pageKey)
                    upSpaceLoadingPage = null
                    if (error is DuplicateUpSpaceRequestException) {
                        upVideosState = UiState.Success(
                            current.copy(isLoadingMore = false)
                        )
                        UpSpaceDebugLog.logRepositoryPath(
                            path = "loadMore duplicate ignored",
                            detail = "page=$nextPage"
                        )
                        return@fold
                    }
                    val message = resolveUpSpaceVideoFailure(
                        error,
                        defaultMessage = "\u52a0\u8f7d\u5931\u8d25"
                    )
                    UpSpaceDebugLog.logResponseClassification(
                        bilibiliCode = -1,
                        bilibiliMessage = error.message.orEmpty(),
                        sessionExpiredRecognized = UpSpaceDebugLog.isSessionExpiredThrowable(error),
                        rateLimitRecognized = UpSpaceDebugLog.isRateLimitMessage(error.message.orEmpty()),
                        mappedToRateLimitMessage = UpSpaceDebugLog.isMappedToRateLimitMessage(message)
                    )
                    upVideosState = UiState.Success(
                        current.copy(isLoadingMore = false, loadMoreError = message)
                    )
                    UpSpaceDebugLog.logUiStateTarget(
                        target = "Success",
                        detail = "loadMore error kept list loadMoreError=$message"
                    )
                }
            )
        }
    }

    fun openVideoFromUpSpace(item: UpVideoItem) {
        Log.i(
            TAG_NAV,
            "open detail from up space video=${item.title} id=${item.aid} " +
                "upSpaceReturnScreen=$upSpaceReturnScreen upSpaceReturnDetailTarget=$upSpaceReturnDetailTarget"
        )
        Log.i(TAG_UP_SPACE, "enter video detail from up space")
        openVideoDetail(
            item.toVideoItem(selectedUpMid, selectedUpDisplayName),
            DetailReturnTarget.UpSpace
        )
    }

    fun loadFavoriteFolders() {
        if (currentUser == null) {
            favoriteFoldersState = UiState.Error("\u8bf7\u5148\u767b\u5f55")
            return
        }
        favoriteFoldersState = UiState.Loading
        scope.launch {
            favoriteFoldersState = favoriteRepository.loadFolders().fold(
                onSuccess = { folders ->
                    if (folders.isEmpty()) {
                        UiState.Success(folders)
                    } else {
                        UiState.Success(folders)
                    }
                },
                onFailure = {
                    UiState.Error(resolveApiFailure(it, "\u6536\u85cf\u5939\u52a0\u8f7d\u5931\u8d25"))
                }
            )
        }
    }

    fun openFavoriteFolder(folder: FavoriteFolder) {
        selectedFavoriteFolder = folder
        favoriteVideosState = UiState.Loading
        screen = Screen.FavoriteFolderDetail
        scope.launch {
            favoriteVideosState = favoriteRepository.loadFolderVideos(folder.id, page = 1).fold(
                onSuccess = {
                    UiState.Success(
                        PagedVideoList(
                            videos = it.videos,
                            page = it.page,
                            hasMore = it.hasMore
                        )
                    )
                },
                onFailure = {
                    UiState.Error(resolveApiFailure(it, "\u6536\u85cf\u89c6\u9891\u52a0\u8f7d\u5931\u8d25"))
                }
            )
        }
    }

    fun loadMoreFavoriteVideos() {
        val folder = selectedFavoriteFolder ?: return
        val current = (favoriteVideosState as? UiState.Success)?.data ?: return
        val nextPage = current.page + 1
        val pageKey = nextPage.toString()
        if (!paginationGuard.tryStart("favorite", pageKey, current.isLoadingMore, current.hasMore)) {
            return
        }
        favoriteVideosState = UiState.Success(current.copy(isLoadingMore = true, loadMoreError = null))
        scope.launch {
            favoriteVideosState = favoriteRepository.loadFolderVideos(folder.id, nextPage).fold(
                onSuccess = {
                    paginationGuard.finish("favorite", pageKey)
                    val merged = appendUniqueVideos(current.videos, it.videos)
                    UiState.Success(
                        current.copy(
                            videos = merged,
                            page = nextPage,
                            hasMore = it.hasMore,
                            isLoadingMore = false,
                            loadMoreError = null
                        )
                    )
                },
                onFailure = {
                    paginationGuard.finish("favorite", pageKey)
                    UiState.Success(current.copy(isLoadingMore = false, loadMoreError = resolveApiFailure(it, "\u52a0\u8f7d\u5931\u8d25")))
                }
            )
        }
    }

    fun loadWatchLater() {
        if (currentUser == null) {
            watchLaterState = UiState.Error("\u8bf7\u5148\u767b\u5f55")
            return
        }
        watchLaterState = UiState.Loading
        scope.launch {
            watchLaterState = watchLaterRepository.loadWatchLater(page = 1).fold(
                onSuccess = {
                    UiState.Success(
                        PagedVideoList(
                            videos = it.videos,
                            page = it.page,
                            hasMore = it.hasMore
                        )
                    )
                },
                onFailure = {
                    UiState.Error(resolveApiFailure(it, "\u7a0d\u540e\u518d\u770b\u52a0\u8f7d\u5931\u8d25"))
                }
            )
        }
    }

    fun loadMoreWatchLater() {
        val current = (watchLaterState as? UiState.Success)?.data ?: return
        val nextPage = current.page + 1
        val pageKey = nextPage.toString()
        if (!paginationGuard.tryStart("watchlater", pageKey, current.isLoadingMore, current.hasMore)) {
            return
        }
        watchLaterState = UiState.Success(current.copy(isLoadingMore = true, loadMoreError = null))
        scope.launch {
            watchLaterState = watchLaterRepository.loadWatchLater(nextPage).fold(
                onSuccess = {
                    paginationGuard.finish("watchlater", pageKey)
                    val merged = appendUniqueVideos(current.videos, it.videos)
                    UiState.Success(
                        current.copy(
                            videos = merged,
                            page = nextPage,
                            hasMore = it.hasMore,
                            isLoadingMore = false,
                            loadMoreError = null
                        )
                    )
                },
                onFailure = {
                    paginationGuard.finish("watchlater", pageKey)
                    UiState.Success(current.copy(isLoadingMore = false, loadMoreError = resolveApiFailure(it, "\u52a0\u8f7d\u5931\u8d25")))
                }
            )
        }
    }

    fun startLogin() {
        loginJob?.cancel()
        loginAwaitingUserInfo = false
        screen = Screen.Login
        loginQrState = UiState.Loading
        loginMessage = "\u6b63\u5728\u83b7\u53d6\u767b\u5f55\u4e8c\u7ef4\u7801..."
        loginJob = scope.launch {
            val qrCode = loginRepository.generateQrCode().getOrElse {
                loginQrState = UiState.Error(it.message ?: "\u83b7\u53d6\u4e8c\u7ef4\u7801\u5931\u8d25")
                return@launch
            }

            loginQrState = UiState.Success(qrCode)
            loginMessage = "\u8bf7\u4f7f\u7528\u624b\u673a Bilibili \u626b\u7801\u767b\u5f55"

            repeat(90) {
                delay(2_000)
                when (val result = loginRepository.pollQrCode(qrCode.qrcodeKey)) {
                    is LoginPollResult.Success -> {
                        loginMessage = "\u767b\u5f55\u6210\u529f\uff0c\u6b63\u5728\u83b7\u53d6\u7528\u6237\u4fe1\u606f..."
                        loginRepository.fetchUserInfo().fold(
                            onSuccess = {
                                Log.i(TAG_LOGIN, "login fetch user info success, mid=${it.mid}")
                                completeLogin(it)
                            },
                            onFailure = {
                                loginAwaitingUserInfo = true
                                loginMessage = "\u767b\u5f55\u9a8c\u8bc1\u6210\u529f\uff0c\u4f46\u83b7\u53d6\u7528\u6237\u4fe1\u606f\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
                                Log.w(TAG_LOGIN, "login fetch user info failed: ${it.message}")
                            }
                        )
                        return@launch
                    }

                    is LoginPollResult.Waiting -> loginMessage = result.message
                    is LoginPollResult.Expired -> {
                        loginQrState = UiState.Error(result.message)
                        return@launch
                    }

                    is LoginPollResult.Error -> {
                        loginQrState = UiState.Error(result.message)
                        return@launch
                    }
                }
            }

            loginQrState = UiState.Error("\u4e8c\u7ef4\u7801\u5df2\u8fc7\u671f")
        }
    }

    BackHandler(enabled = screen == Screen.Home && homeTabFromMyEntry == null && !showExitConfirmDialog) {
        Log.i(TAG_EXIT, "back at root, show exit confirm")
        showExitConfirmDialog = true
    }

    BackHandler(enabled = screen == Screen.Home && showExitConfirmDialog) {
        Log.i(TAG_EXIT, "exit dialog cancel")
        showExitConfirmDialog = false
    }

    BackHandler(enabled = screen == Screen.Home && homeTabFromMyEntry != null) {
        Log.i(
            TAG_NAV,
            "onBackPressed: return from my entry=${homeTabFromMyEntry} to My tab"
        )
        restoreMyFocusEntry = homeTabFromMyEntry
        homeTabFromMyEntry = null
        selectedHomeTab = HomeNavTabs.MY
    }

    BackHandler(enabled = screen == Screen.Detail) {
        if (!popNavigationStack("backFromDetail")) {
            Log.i(TAG_NAV_STACK, "pop empty from Detail, fallback Home tab=$selectedHomeTab")
            screen = Screen.Home
        }
    }

    BackHandler(enabled = screen == Screen.UpSpace) {
        if (!popNavigationStack("backFromUpSpace")) {
            Log.i(TAG_NAV_STACK, "pop empty from UpSpace, fallback Home tab=$selectedHomeTab")
            selectedUpMid = 0L
            selectedUpDisplayName = ""
            upProfileState = null
            upVideosState = null
            screen = Screen.Home
        }
    }

    BackHandler(enabled = screen == Screen.FavoriteFolders) {
        restoreMyFocusEntry = MyMenuItem.Favorites
        screen = Screen.Home
        selectedHomeTab = HomeNavTabs.MY
    }

    BackHandler(enabled = screen == Screen.FavoriteFolderDetail) {
        screen = Screen.FavoriteFolders
    }

    BackHandler(enabled = screen == Screen.WatchLater) {
        restoreMyFocusEntry = MyMenuItem.WatchLater
        screen = Screen.Home
        selectedHomeTab = HomeNavTabs.MY
    }

    BackHandler(enabled = screen == Screen.DanmakuBlocklist) {
        screen = Screen.Home
        selectedHomeTab = HomeNavTabs.SETTINGS
    }

    BackHandler(enabled = screen == Screen.Login) {
        loginJob?.cancel()
        if (loginAwaitingUserInfo || (loginRepository.isLoggedIn() && currentUser == null)) {
            Log.i(TAG_LOGIN, "exit login with pending user info, clear cookies")
            loginRepository.logout()
            homeSnapshotStorage.clearAll()
            loginAwaitingUserInfo = false
            currentUser = null
        }
        Log.i(
            TAG_NAV,
            "onBackPressed: currentScreen=$screen, currentTab=$selectedHomeTab, " +
                "sourceTab=$selectedHomeTab, selectedUpName=${selectedUpName(dynamicState, selectedDynamicUpMid)}, " +
                "selectedUpMid=${selectedDynamicUpMid ?: 0L}, returnTo=Home($selectedHomeTab)"
        )
        screen = Screen.Home
    }

    NbltTheme {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
            Screen.Home -> saveableStateHolder.SaveableStateProvider(Screen.Home.name) {
                HomeScreen(
                    mockVideos = MockVideoRepository.videos,
                    selectedNav = selectedHomeTab,
                    selectedDynamicUpMid = selectedDynamicUpMid,
                    recommendState = recommendState,
                    popularState = popularState,
                    searchSuggestionsState = searchSuggestionsState,
                    searchResultsState = searchResultsState,
                    dynamicState = dynamicState,
                    upVideoState = upVideoState,
                    historyState = historyState,
                    watchLaterState = watchLaterState,
                    currentUser = currentUser,
                    preferredQualityQn = preferredQualityQn,
                    defaultPlaybackSpeed = defaultPlaybackSpeed,
                    playbackProfile = playbackProfile,
                    danmakuSettings = danmakuSettings,
                    startupTab = startupTab,
                    autoPlayNextPageEnabled = autoPlayNextPageEnabled,
                    playbackEndBehavior = playbackEndBehavior,
                    onPreferredQualitySelected = {
                        preferredQualityQn = it
                        playbackSettingsStorage.setPreferredQualityQn(it)
                    },
                    onDefaultPlaybackSpeedSelected = {
                        defaultPlaybackSpeed = it
                        playbackSettingsStorage.setDefaultPlaybackSpeed(it)
                    },
                    onPlaybackProfileSelected = {
                        playbackProfile = it
                        if (screen != Screen.Player) {
                            currentPlayerProfile = it
                        }
                        playbackSettingsStorage.setPlaybackProfile(it)
                    },
                    onDanmakuSettingsChanged = {
                        danmakuSettings = it
                        danmakuSettingsStorage.save(it)
                    },
                    onStartupTabSelected = {
                        startupTab = it
                        playbackSettingsStorage.setStartupTab(it)
                    },
                    onAutoPlayNextPageSelected = {
                        autoPlayNextPageEnabled = it
                        playbackSettingsStorage.setAutoPlayNextPageEnabled(it)
                    },
                    onPlaybackEndBehaviorSelected = {
                        playbackEndBehavior = it
                        playbackSettingsStorage.setPlaybackEndBehavior(it)
                    },
                    onSelectedNav = { tab ->
                        homeTabFromMyEntry = null
                        selectedHomeTab = tab
                        Log.i(
                            TAG_NAV,
                            "select tab: currentScreen=$screen, currentTab=$selectedHomeTab, " +
                                "sourceTab=$selectedHomeTab, selectedUpName=${selectedUpName(dynamicState, selectedDynamicUpMid)}, " +
                                "selectedUpMid=${selectedDynamicUpMid ?: 0L}"
                        )
                    },
                onSelectedDynamicUpMid = {
                    selectedDynamicUpMid = it
                    if (it == null) {
                        upVideoState = null
                        loadingUpVideoMid = null
                        loadedUpVideoMid = null
                    }
                        Log.i(
                            TAG_NAV,
                            "select dynamic up: currentScreen=$screen, currentTab=$selectedHomeTab, " +
                                "sourceTab=$selectedHomeTab, selectedUpName=${selectedUpName(dynamicState, selectedDynamicUpMid)}, " +
                                "selectedUpMid=${selectedDynamicUpMid ?: 0L}"
                        )
                    },
                    onLoadRecommend = ::loadRecommendVideos,
                    onLoadPopular = ::loadPopularVideos,
                    onLoadSearchSuggestions = ::loadSearchSuggestions,
                    onSearch = ::searchVideos,
                    onClearSearchResults = ::clearSearchResults,
                    onLoadDynamic = ::loadDynamic,
                    onLoadUpVideos = ::loadUpVideos,
                    onLoadHistory = ::loadHistory,
                    onLoadWatchLater = ::loadWatchLater,
                    onLoadMoreRecommend = ::loadMoreRecommendVideos,
                    onLoadMorePopular = ::loadMorePopularVideos,
                    onLoadMoreSearch = ::loadMoreSearchVideos,
                    onLoadMoreDynamic = ::loadMoreDynamicVideos,
                    onLoadMoreUpVideos = ::loadMoreUpVideos,
                    onLoadMoreHistory = ::loadMoreHistory,
                    onRefreshRecommend = ::reloadRecommendVideos,
                    onRefreshPopular = ::reloadPopularVideos,
                    onRefreshDynamic = ::reloadDynamic,
                    onRefreshHistory = ::reloadHistory,
                    onLoginClick = ::startLogin,
                    onLogoutClick = ::logout,
                    onOpenFavorites = {
                        homeTabFromMyEntry = null
                        screen = Screen.FavoriteFolders
                        loadFavoriteFolders()
                    },
                    onOpenWatchLater = {
                        homeTabFromMyEntry = null
                        screen = Screen.WatchLater
                        loadWatchLater()
                    },
                    onOpenHistoryFromMy = {
                        homeTabFromMyEntry = MyMenuItem.History
                        selectedHomeTab = HomeNavTabs.HISTORY
                        if (currentUser != null && historyState == null) {
                            loadHistory()
                        }
                    },
                    onOpenSettingsFromMy = {
                        homeTabFromMyEntry = MyMenuItem.Settings
                        selectedHomeTab = HomeNavTabs.SETTINGS
                    },
                    restoreMyFocusEntry = restoreMyFocusEntry,
                    onMyFocusRestored = { restoreMyFocusEntry = null },
                    onOpenDanmakuBlocklist = {
                        homeTabFromMyEntry = null
                        selectedHomeTab = HomeNavTabs.SETTINGS
                        screen = Screen.DanmakuBlocklist
                    },
                    danmakuBlocklistEnabled = danmakuBlocklistEnabled,
                    onDanmakuBlocklistEnabledChanged = { enabled ->
                        danmakuBlocklistEnabled = enabled
                        danmakuBlocklistStorage.setEnabled(enabled)
                    },
                    onVideoClick = { video, index ->
                        Log.i(
                            TAG_NAV,
                            "open detail: currentScreen=$screen, currentTab=$selectedHomeTab, " +
                                "sourceTab=$selectedHomeTab, selectedUpName=${selectedUpName(dynamicState, selectedDynamicUpMid)}, " +
                                "selectedUpMid=${selectedDynamicUpMid ?: 0L}"
                        )
                        openHomeVideoDetail(video, index)
                    },
                    onVideoFocused = { tab, video, index, dynamicUpMid ->
                        if (FOCUS_LOG_ENABLED) {
                            Log.i(
                                TAG_FOCUS,
                                "video focused tab=$tab index=$index id=${video.homeFocusKey()}"
                            )
                        }
                    },
                    homeVideoFocusRestore = homeVideoFocusRestore,
                    onHomeVideoFocusRestored = { homeVideoFocusRestore = null },
                    onUpClick = ::handleUpClick
                )
            }

            Screen.FavoriteFolders -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NbltBackground)
                    .padding(horizontal = 44.dp, vertical = 36.dp)
            ) {
                Text(
                    text = "\u6211\u7684\u6536\u85cf",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                FavoriteScreen(
                    currentUser = currentUser,
                    foldersState = favoriteFoldersState,
                    onLoginClick = ::startLogin,
                    onRetry = ::loadFavoriteFolders,
                    onFolderClick = ::openFavoriteFolder,
                    modifier = Modifier.weight(1f)
                )
            }

            Screen.FavoriteFolderDetail -> {
                val folder = selectedFavoriteFolder
                if (folder != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NbltBackground)
                            .padding(horizontal = 44.dp, vertical = 36.dp)
                    ) {
                        FavoriteFolderDetailScreen(
                            folder = folder,
                            videosState = favoriteVideosState,
                            onRetry = { openFavoriteFolder(folder) },
                            onLoadMore = ::loadMoreFavoriteVideos,
                            onVideoClick = {
                                openVideoDetail(it, DetailReturnTarget.FavoriteFolderDetail)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Screen.WatchLater -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NbltBackground)
                    .padding(horizontal = 44.dp, vertical = 36.dp)
            ) {
                Text(
                    text = "\u7a0d\u540e\u518d\u770b",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                WatchLaterScreen(
                    currentUser = currentUser,
                    watchLaterState = watchLaterState,
                    onLoginClick = ::startLogin,
                    onRetry = ::loadWatchLater,
                    onLoadMore = ::loadMoreWatchLater,
                    onVideoClick = {
                        openVideoDetail(it, DetailReturnTarget.WatchLater)
                    }
                )
            }

            Screen.DanmakuBlocklist -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NbltBackground)
                    .padding(horizontal = 44.dp, vertical = 36.dp)
            ) {
                DanmakuBlocklistScreen(
                    enabled = danmakuBlocklistEnabled,
                    keywords = danmakuBlocklist,
                    onEnabledChanged = { enabled ->
                        danmakuBlocklistEnabled = enabled
                        danmakuBlocklistStorage.setEnabled(enabled)
                    },
                    onAddKeyword = { keyword ->
                        danmakuBlocklist = danmakuBlocklistStorage.addKeyword(keyword)
                    },
                    onRemoveKeyword = { keyword ->
                        danmakuBlocklist = danmakuBlocklistStorage.removeKeyword(keyword)
                    },
                    onClearKeywords = {
                        danmakuBlocklist = danmakuBlocklistStorage.clearKeywords()
                    },
                    onBack = {
                        screen = Screen.Home
                        selectedHomeTab = HomeNavTabs.SETTINGS
                    }
                )
            }

            Screen.Detail -> VideoDetailScreen(
                video = selectedVideo,
                pagesLoadState = videoDetailState,
                onRetryPagesLoad = { loadVideoDetail(selectedVideo) },
                onUpClick = ::handleUpClick,
                onPageSelect = ::selectDetailPage,
                onLikeClick = {
                    scope.launch {
                        interactionRepository.like(selectedVideo.aid).fold(
                            onSuccess = {
                                selectedVideo = selectedVideo.copy(likeCount = (selectedVideo.likeCount ?: 0L) + 1L)
                                Toast.makeText(context, "点赞成功", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { Toast.makeText(context, "点赞失败：${resolveApiFailure(it, "请稍后重试")}", Toast.LENGTH_SHORT).show() }
                        )
                    }
                },
                onCoinClick = {
                    scope.launch {
                        interactionRepository.coin(selectedVideo.aid).fold(
                            onSuccess = {
                                selectedVideo = selectedVideo.copy(coinCount = (selectedVideo.coinCount ?: 0L) + 1L)
                                Toast.makeText(context, "投币成功", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { Toast.makeText(context, "投币失败：${resolveApiFailure(it, "请稍后重试")}", Toast.LENGTH_SHORT).show() }
                        )
                    }
                },
                onFavoriteClick = {
                    scope.launch {
                        val folders = (favoriteFoldersState as? UiState.Success)?.data
                            ?: favoriteRepository.loadFolders().getOrElse {
                                Toast.makeText(context, "收藏失败：${resolveApiFailure(it, "请稍后重试")}", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                        val folder = folders.firstOrNull()
                        if (folder == null) {
                            Toast.makeText(context, "请先在 B 站创建收藏夹", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        interactionRepository.favorite(selectedVideo.aid, folder.id).fold(
                            onSuccess = {
                                selectedVideo = selectedVideo.copy(favoriteCount = (selectedVideo.favoriteCount ?: 0L) + 1L)
                                Toast.makeText(context, "已收藏到「${folder.title}」", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { Toast.makeText(context, "收藏失败：${resolveApiFailure(it, "请稍后重试")}", Toast.LENGTH_SHORT).show() }
                        )
                    }
                },
                relatedSources = buildDetailRelatedSources(
                    video = selectedVideo,
                    returnTarget = detailReturnTarget,
                    homeTab = selectedHomeTab,
                    recommendState = recommendState,
                    popularState = popularState,
                    searchResultsState = searchResultsState,
                    upVideoState = upVideoState,
                    historyState = historyState,
                    favoriteVideosState = favoriteVideosState,
                    watchLaterState = watchLaterState,
                    upVideosState = upVideosState,
                    selectedUpMid = selectedUpMid,
                    selectedUpDisplayName = selectedUpDisplayName
                ),
                onRelatedVideoClick = { related ->
                    openVideoDetail(related, detailReturnTarget)
                },
                onPlayClick = {
                    Log.i(
                        TAG_NAV,
                        "open player: currentScreen=$screen, currentTab=$selectedHomeTab, " +
                            "sourceTab=$selectedHomeTab, selectedUpName=${selectedUpName(dynamicState, selectedDynamicUpMid)}, " +
                            "selectedUpMid=${selectedDynamicUpMid ?: 0L}"
                    )
                    selectedVideo = it
                    screen = Screen.Player
                    currentPlayerQualityQn = preferredQualityQn
                    currentPlayerProfile = playbackProfile
                    attemptedPlaybackProfiles = setOf(playbackProfile)
                    loadPlayUrl(it, enableMediaPreconnect = true)
                }
            )

            Screen.Player -> {
                val playerRelatedSources = buildDetailRelatedSources(
                    video = selectedVideo,
                    returnTarget = detailReturnTarget,
                    homeTab = selectedHomeTab,
                    recommendState = recommendState,
                    popularState = popularState,
                    searchResultsState = searchResultsState,
                    upVideoState = upVideoState,
                    historyState = historyState,
                    favoriteVideosState = favoriteVideosState,
                    watchLaterState = watchLaterState,
                    upVideosState = upVideosState,
                    selectedUpMid = selectedUpMid,
                    selectedUpDisplayName = selectedUpDisplayName
                )
                val playerRelatedVideos = resolveDetailRelatedVideos(selectedVideo, playerRelatedSources)
                val prefetchedUpVideos = (playerUpVideosState as? UiState.Success)?.data
                    .orEmpty()
                    .filterNot { candidate ->
                        candidate.id == selectedVideo.id ||
                            (candidate.bvid.isNotBlank() && candidate.bvid == selectedVideo.bvid)
                    }
                val panelVideos = prefetchedUpVideos.ifEmpty { playerRelatedVideos }
                val prefetchedProfile = (playerUpProfileState as? UiState.Success)?.data
                val panelOwner = selectedVideo.copy(
                    ownerName = prefetchedProfile?.nickname.orEmpty().ifBlank { selectedVideo.ownerName },
                    ownerFaceUrl = prefetchedProfile?.avatarUrl.orEmpty().ifBlank { selectedVideo.ownerFaceUrl }
                )
                PlayerScreen(
                    video = selectedVideo,
                    playUrlState = playUrlState,
                    onRetry = { loadPlayUrl(selectedVideo) },
                    onRefreshPlayUrl = { avoidedHosts ->
                        Log.i(TAG_QUALITY, "recovery using qn=$currentPlayerQualityQn")
                        Log.i(TAG_PROFILE, "fallbackStep=refreshPlayUrl, current profile=${currentPlayerProfile.name}, ua=${currentPlayerProfile.shortName}")
                        loadPlayUrl(selectedVideo, currentPlayerQualityQn.takeIf { it > 0 }, avoidedHosts)
                    },
                    onSwitchCdn = { switchedPlayUrl ->
                        Log.i(
                            TAG_PLAYER_DEBUG,
                            "switch CDN without playurl refresh: videoHost=${switchedPlayUrl.videoUrl}, audioHost=${switchedPlayUrl.audioUrl.orEmpty()}"
                        )
                        playUrlState = UiState.Success(switchedPlayUrl)
                    },
                    onSwitchPlaybackProfile = { avoidedHosts ->
                        switchPlaybackProfileForRecovery(avoidedHosts)
                    },
                    onSwitchQuality = { qn, positionMs ->
                        Log.i(TAG_QUALITY, "user selected in-player qn=$qn")
                        Log.i(TAG_QUALITY, "switch quality current position=$positionMs")
                        currentPlayerQualityQn = qn
                        loadPlayUrl(selectedVideo, qn, profile = currentPlayerProfile)
                    },
                    onSwitchPage = ::switchPlayerPage,
                    onExit = {
                        Log.i(
                            TAG_NAV,
                            "player exit: currentScreen=$screen, currentTab=$selectedHomeTab, " +
                                "sourceTab=$selectedHomeTab, selectedUpName=${selectedUpName(dynamicState, selectedDynamicUpMid)}, " +
                                "selectedUpMid=${selectedDynamicUpMid ?: 0L}, returnTo=Detail"
                        )
                        screen = Screen.Detail
                    },
                    onReportPlaybackProgress = { video, state, event ->
                        scope.launch {
                            heartbeatRepository.report(
                                video = video,
                                currentPositionMs = state.currentPositionMs,
                                durationMs = state.durationMs,
                                event = event
                            )
                        }
                    },
                    defaultPlaybackSpeed = defaultPlaybackSpeed,
                    defaultDanmakuSettings = danmakuSettings,
                    danmakuBlocklistEnabled = danmakuBlocklistEnabled,
                    danmakuBlocklist = danmakuBlocklist,
                    onPlaybackEnded = ::handlePlaybackEnded,
                    onPlaybackError = { message ->
                        val errorMessage = if (SessionExpiredException.isExpired(Exception(message))) {
                            handleSessionExpired()
                            SessionExpiredException.MESSAGE
                        } else {
                            message
                        }
                        playUrlState = UiState.Error(errorMessage)
                    },
                    relatedPanelTitle = if (prefetchedUpVideos.isNotEmpty() || playerRelatedSources.sameUpVideos.isNotEmpty()) {
                        "\u8be5 UP \u4e3b\u5176\u4ed6\u89c6\u9891"
                    } else {
                        "\u76f8\u5173\u63a8\u8350"
                    },
                    relatedPanelOwner = panelOwner,
                    relatedPanelVideos = panelVideos,
                    recommendationPanelVideos = playerRelatedVideos,
                    relatedPanelLoading = playerUpVideosState is UiState.Loading,
                    relatedPanelLoadingMore = playerUpVideoLoadingMore,
                    relatedPanelHasMore = playerUpVideoHasMore,
                    relatedPanelLoadMoreError = playerUpVideoLoadMoreError,
                    onLoadMoreRelatedPanel = ::loadMorePlayerUpVideos,
                    onOpenOwnerSpace = { mid, name ->
                        openUpSpace(mid, name)
                    },
                    onRelatedPanelVideoClick = { related ->
                        selectedVideo = related
                        currentPlayerProfile = playbackProfile
                        attemptedPlaybackProfiles = setOf(playbackProfile)
                        loadPlayUrl(
                            related,
                            currentPlayerQualityQn.takeIf { it > 0 } ?: preferredQualityQn,
                            enableMediaPreconnect = true
                        )
                    }
                )
            }

            Screen.Login -> LoginScreen(
                qrState = loginQrState,
                message = loginMessage,
                onRetry = ::startLogin,
                awaitingUserInfo = loginAwaitingUserInfo,
                onRetryFetchUserInfo = ::retryFetchUserInfo
            )

            Screen.UpSpace -> saveableStateHolder.SaveableStateProvider("UpSpace:$selectedUpMid") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NbltBackground)
                        .padding(horizontal = 44.dp, vertical = 24.dp)
                ) {
                    UpSpaceScreen(
                        profileState = upProfileState,
                        videosState = upVideosState,
                        fallbackName = selectedUpDisplayName,
                        onRetryProfile = { loadUpSpaceProfile(selectedUpMid) },
                        onRetryVideos = ::retryUpSpaceVideos,
                        onLoadMore = ::loadMoreUpSpaceVideos,
                        onVideoClick = ::openVideoFromUpSpace,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
    }

    if (showExitConfirmDialog && screen == Screen.Home) {
        ExitConfirmDialog(
            onDismiss = { showExitConfirmDialog = false },
            onConfirmExit = {
                showExitConfirmDialog = false
                (context as? ComponentActivity)?.finish()
            }
        )
    }
}

private fun logRefreshResult(
    tab: String,
    oldVideos: List<VideoItem>,
    newVideos: List<VideoItem>
) {
    val oldFirst = oldVideos.firstOrNull()
    val newFirst = newVideos.firstOrNull()
    val changed = oldVideos.map { it.bvid } != newVideos.map { it.bvid }
    Log.i(
        TAG_REFRESH,
        "refresh tab=$tab, refresh success, " +
            "old first=${oldFirst?.bvid.orEmpty()}/${oldFirst?.title.orEmpty()}, " +
            "new first=${newFirst?.bvid.orEmpty()}/${newFirst?.title.orEmpty()}, " +
            "old list size=${oldVideos.size}, new list size=${newVideos.size}, " +
            "whether list changed=$changed"
    )
}

private fun selectedUpName(
    dynamicState: DynamicUiState?,
    selectedUpMid: Long?
): String {
    return (dynamicState as? DynamicUiState.Success)
        ?.data
        ?.followedUps
        ?.firstOrNull { it.mid == selectedUpMid }
        ?.name
        .orEmpty()
}

private fun appendUniqueUpVideos(
    current: List<UpVideoItem>,
    more: List<UpVideoItem>
): List<UpVideoItem> {
    val seen = current.map { it.bvid.ifBlank { it.aid.toString() } }.toMutableSet()
    return current + more.filter {
        seen.add(it.bvid.ifBlank { it.aid.toString() })
    }
}

private fun pagedVideoList(state: UiState<PagedVideoList>?): List<VideoItem> {
    return (state as? UiState.Success)?.data?.videos.orEmpty()
}

private fun historyVideoList(state: HistoryUiState?): List<VideoItem> {
    return (state as? HistoryUiState.Success)?.videos.orEmpty()
}

private fun buildDetailRelatedSources(
    video: VideoItem,
    returnTarget: DetailReturnTarget,
    homeTab: String,
    recommendState: UiState<PagedVideoList>?,
    popularState: UiState<PagedVideoList>?,
    searchResultsState: UiState<PagedVideoList>?,
    upVideoState: UiState<PagedVideoList>?,
    historyState: HistoryUiState?,
    favoriteVideosState: UiState<PagedVideoList>?,
    watchLaterState: UiState<PagedVideoList>?,
    upVideosState: UiState<PagedUpVideoList>?,
    selectedUpMid: Long,
    selectedUpDisplayName: String
): DetailRelatedSources {
    val recommend = pagedVideoList(recommendState)
    val popular = pagedVideoList(popularState)
    val search = pagedVideoList(searchResultsState)
    val dynamicUp = pagedVideoList(upVideoState)
    val history = historyVideoList(historyState)
    val favorites = pagedVideoList(favoriteVideosState)
    val watchLater = pagedVideoList(watchLaterState)

    val upSpaceVideos = (upVideosState as? UiState.Success)?.data?.videos.orEmpty().map { item ->
        item.toVideoItem(
            ownerMid = selectedUpMid.takeIf { it > 0 } ?: video.ownerMid,
            ownerName = selectedUpDisplayName.ifBlank { video.ownerName }
        )
    }

    val sourceList = when (returnTarget) {
        DetailReturnTarget.Home -> when (homeTab) {
            HomeNavTabs.RECOMMEND -> recommend
            HomeNavTabs.POPULAR -> popular
            HomeNavTabs.SEARCH -> search
            HomeNavTabs.DYNAMIC -> dynamicUp
            HomeNavTabs.HISTORY, HomeNavTabs.MY -> history
            else -> recommend.ifEmpty { popular }
        }
        DetailReturnTarget.UpSpace -> upSpaceVideos
        DetailReturnTarget.FavoriteFolderDetail -> favorites
        DetailReturnTarget.WatchLater -> watchLater
    }

    val ownerMid = video.ownerMid
    val sameUpVideos = when {
        ownerMid > 0L && upSpaceVideos.isNotEmpty() && selectedUpMid == ownerMid -> upSpaceVideos
        ownerMid > 0L -> {
            (sourceList + recommend + popular + dynamicUp + history)
                .filter { it.ownerMid == ownerMid }
        }
        else -> emptyList()
    }

    val fallbackPools = listOf(
        recommend,
        popular,
        search,
        dynamicUp,
        history,
        favorites,
        watchLater,
        upSpaceVideos
    )

    return DetailRelatedSources(
        sourceList = sourceList,
        sameUpVideos = sameUpVideos,
        fallbackPools = fallbackPools
    )
}

private fun appendUniqueVideos(
    current: List<VideoItem>,
    more: List<VideoItem>
): List<VideoItem> {
    val seen = current.mapIndexed { index, video -> video.homeFocusKey(index) }.toMutableSet()
    return current + more.filterIndexed { index, video ->
        seen.add(video.homeFocusKey(current.size + index))
    }
}

private fun resolveUpSpaceVideoFailure(error: Throwable, defaultMessage: String): String {
    return UpSpaceVideoErrors.normalizeVideoErrorMessage(error.message, defaultMessage)
}

private fun defaultSearchSuggestions(): List<SearchSuggestion> {
    return listOf("动画", "音乐", "游戏", "科技", "影视", "舞蹈", "美食", "纪录片")
        .map(::SearchSuggestion)
}

private const val TAB_RECOMMEND = "\u63a8\u8350"
private const val TAG_FOCUS = "BiliFocus"
private const val FOCUS_LOG_ENABLED = false
private const val TAG_EXIT = "BiliExit"
private const val TAG_REFRESH = "BiliRefresh"
private const val TAG_NAV = "BiliNavigation"
private const val TAG_UP = "BiliUpVideos"
private const val TAG_PLAYER = "BiliPlayer"
private const val TAG_PLAYER_DEBUG = "BiliPlayerDebug"
private const val TAG_PAGINATION = "BiliPagination"
private const val TAG_SEARCH = "BiliSearch"
private const val TAG_QUALITY = "BiliQuality"
private const val TAG_PAGES = "BiliPages"
private const val TAG_UP_SPACE = "BiliUpSpace"
private const val TAG_BEHAVIOR = "BiliPlaybackBehavior"
private const val TAG_SESSION = "BiliSession"
private const val TAG_LOGIN = "BiliLogin"
private const val TAG_HOME_SNAPSHOT = "BiliHomeSnapshot"
private const val TAG_MEMORY = "BiliMemory"
private const val TAG_PROFILE = "BiliPlaybackProfile"
private const val TAG_NAV_STACK = "BiliNavigationStack"
