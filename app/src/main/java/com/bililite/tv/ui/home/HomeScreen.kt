@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.bililite.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.model.DanmakuSettings
import com.bililite.tv.model.FollowedUp
import com.bililite.tv.model.PlaybackEndBehavior
import com.bililite.tv.model.PlaybackProfile
import com.bililite.tv.model.SearchSuggestion
import com.bililite.tv.model.UserInfo
import com.bililite.tv.model.VideoItem
import com.bililite.tv.ui.components.TvEmptyContent
import com.bililite.tv.ui.components.TvErrorContent
import com.bililite.tv.ui.components.TvLoadingContent
import com.bililite.tv.ui.components.TvNotLoggedInContent
import com.bililite.tv.ui.components.CinematicSideRail
import com.bililite.tv.ui.components.CoverPlaceholder
import com.bililite.tv.ui.components.formatPubDate
import com.bililite.tv.theme.TvColors
import com.bililite.tv.theme.TvDimensions
import com.bililite.tv.ui.state.DynamicUiState
import com.bililite.tv.ui.state.HistoryUiState
import com.bililite.tv.ui.state.PagedVideoList
import com.bililite.tv.ui.state.UiState
import com.bililite.tv.ui.search.SearchScreen
import com.bililite.tv.ui.my.MyMenuItem
import com.bililite.tv.ui.my.MyScreen
import com.bililite.tv.ui.settings.SettingsScreen
import com.bililite.tv.model.stableContentKey
import com.bililite.tv.ui.components.VideoCardStyle
import com.bililite.tv.util.FormatUtils

private val navItems = HomeNavTabs.ALL
private const val HOME_ROW_CONTINUE = "continue"
private const val HOME_ROW_DYNAMIC = "dynamic"

@Composable
fun HomeScreen(
    mockVideos: List<VideoItem>,
    selectedNav: String,
    selectedDynamicUpMid: Long?,
    recommendState: UiState<PagedVideoList>?,
    popularState: UiState<PagedVideoList>?,
    searchSuggestionsState: UiState<List<SearchSuggestion>>,
    searchResultsState: UiState<PagedVideoList>?,
    dynamicState: DynamicUiState?,
    upVideoState: UiState<PagedVideoList>?,
    historyState: HistoryUiState?,
    watchLaterState: UiState<PagedVideoList>?,
    currentUser: UserInfo?,
    preferredQualityQn: Int,
    defaultPlaybackSpeed: Float,
    playbackProfile: PlaybackProfile,
    danmakuSettings: DanmakuSettings,
    startupTab: String,
    autoPlayNextPageEnabled: Boolean,
    playbackEndBehavior: PlaybackEndBehavior,
    onPreferredQualitySelected: (Int) -> Unit,
    onDefaultPlaybackSpeedSelected: (Float) -> Unit,
    onPlaybackProfileSelected: (PlaybackProfile) -> Unit,
    onDanmakuSettingsChanged: (DanmakuSettings) -> Unit,
    onStartupTabSelected: (String) -> Unit,
    onAutoPlayNextPageSelected: (Boolean) -> Unit,
    onPlaybackEndBehaviorSelected: (PlaybackEndBehavior) -> Unit,
    onSelectedNav: (String) -> Unit,
    onSelectedDynamicUpMid: (Long?) -> Unit,
    onLoadRecommend: () -> Unit,
    onLoadPopular: () -> Unit,
    onLoadSearchSuggestions: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClearSearchResults: () -> Unit,
    onLoadDynamic: () -> Unit,
    onLoadUpVideos: (FollowedUp) -> Unit,
    onLoadHistory: () -> Unit,
    onLoadWatchLater: () -> Unit,
    onLoadMoreRecommend: () -> Unit,
    onLoadMorePopular: () -> Unit,
    onLoadMoreSearch: () -> Unit,
    onLoadMoreDynamic: () -> Unit,
    onLoadMoreUpVideos: () -> Unit,
    onLoadMoreHistory: () -> Unit,
    onRefreshRecommend: () -> Unit,
    onRefreshPopular: () -> Unit,
    onRefreshDynamic: () -> Unit,
    onRefreshHistory: () -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenWatchLater: () -> Unit,
    onOpenHistoryFromMy: () -> Unit,
    onOpenSettingsFromMy: () -> Unit,
    restoreMyFocusEntry: MyMenuItem? = null,
    onMyFocusRestored: () -> Unit = {},
    onOpenDanmakuBlocklist: () -> Unit,
    danmakuBlocklistEnabled: Boolean,
    onDanmakuBlocklistEnabledChanged: (Boolean) -> Unit,
    onVideoClick: (VideoItem, Int) -> Unit,
    onVideoFocused: (String, VideoItem, Int, Long?) -> Unit = { _, _, _, _ -> },
    homeVideoFocusRestore: HomeVideoFocusRestore? = null,
    onHomeVideoFocusRestored: () -> Unit = {},
    onUpClick: ((Long, String) -> Unit)? = null
) {
    var contentFocusActive by remember { mutableStateOf(false) }
    var sideNavHasFocus by remember { mutableStateOf(false) }
    val reportVideoFocused: (String, VideoItem, Int, Long?) -> Unit = { tab, video, index, upMid ->
        contentFocusActive = true
        onVideoFocused(tab, video, index, upMid)
    }
    fun refreshCurrentTab(tab: String) {
        when (tab) {
            HomeNavTabs.HOME -> {
                if (currentUser != null) {
                    onRefreshHistory()
                    onRefreshDynamic()
                }
            }
            HomeNavTabs.RECOMMEND -> onRefreshRecommend()
            HomeNavTabs.POPULAR -> onRefreshPopular()
            HomeNavTabs.SEARCH -> Unit
            HomeNavTabs.DYNAMIC -> {
                val selectedUp = (dynamicState as? DynamicUiState.Success)
                    ?.data
                    ?.followedUps
                    ?.firstOrNull { it.mid == selectedDynamicUpMid }
                if (selectedUp == null) {
                    onRefreshDynamic()
                } else {
                    onLoadUpVideos(selectedUp)
                }
            }
            HomeNavTabs.HISTORY -> onRefreshHistory()
            HomeNavTabs.MY -> {
                if (currentUser != null) {
                    onLoadHistory()
                    onLoadWatchLater()
                }
            }
            HomeNavTabs.SETTINGS -> Unit
            else -> Unit
        }
    }

    LaunchedEffect(selectedNav, recommendState, popularState, dynamicState, currentUser, historyState) {
        if (selectedNav == HomeNavTabs.HOME) {
            if (currentUser != null && historyState == null) onLoadHistory()
            if (currentUser != null && dynamicState == null) onLoadDynamic()
        } else when {
            selectedNav == HomeNavTabs.RECOMMEND && recommendState == null -> onLoadRecommend()
            selectedNav == HomeNavTabs.POPULAR && popularState == null -> onLoadPopular()
            selectedNav == HomeNavTabs.DYNAMIC && currentUser != null && dynamicState == null -> onLoadDynamic()
            selectedNav == HomeNavTabs.HISTORY && currentUser != null && historyState == null -> onLoadHistory()
            selectedNav == HomeNavTabs.MY && currentUser != null && historyState == null -> onLoadHistory()
            selectedNav == HomeNavTabs.MY && currentUser != null && watchLaterState == null -> onLoadWatchLater()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val sideNavItemFocusRequesters = remember {
            navItems.associateWith { FocusRequester() }
        }
        val sideNavFocusRequester = sideNavItemFocusRequesters.getValue(selectedNav)
        val sideNavItemCenters = remember { mutableMapOf<String, Float>() }
        val homeRowCenters = remember { mutableMapOf<String, Float>() }
        val homeContinueEntryFocusRequester = remember { FocusRequester() }
        val homeDynamicEntryFocusRequester = remember { FocusRequester() }
        val homeRowEntryFocusRequesters = remember {
            mapOf(
                HOME_ROW_CONTINUE to homeContinueEntryFocusRequester,
                HOME_ROW_DYNAMIC to homeDynamicEntryFocusRequester
            )
        }
        val searchEntryFocusRequester = remember { FocusRequester() }
        val myAccountActionFocusRequester = remember { FocusRequester() }
        val dynamicFilterFocusRequester = remember { FocusRequester() }
        // Stable 账号 card requester, owned here and wired to the rail's
        // selectedItemRightFocusRequester so a rail Right deterministically
        // enters the 账号 card (UI-R4-02-FIX-01).
        val settingsAccountFocusRequester = remember { FocusRequester() }
        // Stable entry requesters owned here, one per video list tab. Each is wired to
        // the tab's VideoGrid index-0 card (via entryFocusRequester) AND to the rail's
        // selectedItemRightFocusRequester, so a rail Right deterministically lands on the
        // first row / first card (index 0), independent of rail-item geometry. DYNAMIC
        // reuses the same requester for both the total list and the selected-UP list,
        // but only one of those VideoGrids is ever composed at a time (if/else), so the
        // requester is never bound to two live nodes simultaneously.
        val recommendEntryFocusRequester = remember { FocusRequester() }
        val popularEntryFocusRequester = remember { FocusRequester() }
        val dynamicEntryFocusRequester = remember { FocusRequester() }
        val historyEntryFocusRequester = remember { FocusRequester() }
        val homeContinueVideos = (historyState as? HistoryUiState.Success)?.videos.orEmpty()
        val homeDynamicVideos = (dynamicState as? DynamicUiState.Success)?.data?.videos.orEmpty()
        val homeVisibleRowKeys = buildList {
            if (currentUser != null && homeContinueVideos.isNotEmpty()) add(HOME_ROW_CONTINUE)
            if (currentUser != null && homeDynamicVideos.isNotEmpty()) add(HOME_ROW_DYNAMIC)
        }
        val homeSourcesSettled = currentUser != null &&
            historyState != null && historyState != HistoryUiState.Loading &&
            dynamicState != null && dynamicState != DynamicUiState.Loading
        val homeDefaultEntryFocusRequester = homeVisibleRowKeys.firstOrNull()
            ?.let(homeRowEntryFocusRequesters::get)

        fun requestNearestHomeRow(railItem: String): Boolean {
            if (homeVisibleRowKeys.isEmpty()) return false
            val measuredRows = homeVisibleRowKeys.mapNotNull { key ->
                homeRowCenters[key]?.let { centerY -> key to centerY }
            }
            val railCenterY = sideNavItemCenters[railItem]
            val targetKey = if (railCenterY != null && measuredRows.isNotEmpty()) {
                measuredRows.minByOrNull { (_, rowCenterY) ->
                    kotlin.math.abs(rowCenterY - railCenterY)
                }?.first
            } else {
                val enabledItems = navItems.filterNot(HomeNavTabs.DISABLED::contains)
                val enabledIndex = enabledItems.indexOf(railItem).coerceAtLeast(0)
                val targetIndex = if (homeVisibleRowKeys.size == 1) {
                    0
                } else if (enabledIndex * 2 < enabledItems.size) {
                    0
                } else {
                    homeVisibleRowKeys.lastIndex
                }
                homeVisibleRowKeys.getOrNull(targetIndex)
            } ?: return false
            val requester = homeRowEntryFocusRequesters[targetKey] ?: return false
            return runCatching { requester.requestFocus(); true }.getOrDefault(false)
        }

        fun requestNearestRailItem(homeRowKey: String): Boolean {
            val rowCenterY = homeRowCenters[homeRowKey]
            val enabledItems = navItems.filterNot(HomeNavTabs.DISABLED::contains)
            val measuredItems = enabledItems.mapNotNull { item ->
                sideNavItemCenters[item]?.let { centerY -> item to centerY }
            }
            val targetItem = if (rowCenterY != null && measuredItems.isNotEmpty()) {
                measuredItems.minByOrNull { (_, itemCenterY) ->
                    kotlin.math.abs(itemCenterY - rowCenterY)
                }?.first
            } else {
                HomeNavTabs.HOME
            } ?: HomeNavTabs.HOME
            val requester = sideNavItemFocusRequesters[targetItem]
                ?: sideNavItemFocusRequesters[HomeNavTabs.HOME]
                ?: return false
            return runCatching { requester.requestFocus(); true }.getOrDefault(false)
        }

        var initialHomeFocusApplied by remember { mutableStateOf(false) }
        // Settings focus machine (UI-R4-02 / FIX-01). All state lives here so the
        // rail (CinematicSideRail) and MainActivity stay untouched.
        //
        // The grid stays COMPOSED whenever the settings page is shown (no
        // composition gate) so entering SETTINGS always renders immediately.
        // The 账号 card uses a stable FocusRequester owned here and wired to the
        // rail's selectedItemRightFocusRequester, so pressing Right on the rail's
        // "设置" item deterministically enters the 账号 card: spatial nav from the
        // bottom rail item finds no vertically-overlapping card, so the rail falls
        // back to this explicit requester. OK only selects the page; it never
        // grabs content focus.
        var settingsFocusCategory by rememberSaveable { mutableStateOf("Account") }
        val onSettingsLeft: () -> Unit = {
            // First-column card Left: return focus to the rail "设置" icon. The
            // grid stays composed; focus simply moves back to the rail.
            runCatching { sideNavFocusRequester.requestFocus() }
        }

        LaunchedEffect(
            selectedNav,
            homeVisibleRowKeys,
            homeSourcesSettled,
            homeVideoFocusRestore?.restoreToken
        ) {
            if (!initialHomeFocusApplied &&
                selectedNav == HomeNavTabs.HOME &&
                homeSourcesSettled &&
                homeVisibleRowKeys.isNotEmpty() &&
                homeVideoFocusRestore?.tab != HomeNavTabs.HOME
            ) {
                withFrameNanos { }
                withFrameNanos { }
                val focused = homeDefaultEntryFocusRequester?.let { requester ->
                    runCatching { requester.requestFocus(); true }.getOrDefault(false)
                } ?: false
                if (focused) initialHomeFocusApplied = true
            }
        }
        BackHandler(enabled = !sideNavHasFocus) {
            if (
                contentFocusActive &&
                selectedNav == HomeNavTabs.DYNAMIC &&
                selectedDynamicUpMid != null
            ) {
                contentFocusActive = false
                runCatching { dynamicFilterFocusRequester.requestFocus() }
            } else {
                contentFocusActive = false
                runCatching { sideNavFocusRequester.requestFocus() }
            }
        }
        Box(Modifier.fillMaxSize()) {
            if (selectedNav == HomeNavTabs.HOME) {
                LightweightHomeContent(
                    currentUser = currentUser,
                    historyState = historyState,
                    dynamicState = dynamicState,
                    continueEntryFocusRequester = homeContinueEntryFocusRequester,
                    dynamicEntryFocusRequester = homeDynamicEntryFocusRequester,
                    onRetryHistory = onLoadHistory,
                    onRetryDynamic = onLoadDynamic,
                    onLoadMoreHistory = onLoadMoreHistory,
                    onLoadMoreDynamic = onLoadMoreDynamic,
                    onVideoClick = onVideoClick,
                    onVideoFocused = { video, index ->
                        reportVideoFocused(HomeNavTabs.HOME, video, index, null)
                    },
                    focusRestore = homeVideoFocusRestore.takeIf { it?.tab == HomeNavTabs.HOME },
                    onFocusRestored = onHomeVideoFocusRestored,
                    onRowPositioned = { key, centerY -> homeRowCenters[key] = centerY },
                    onFirstCardLeft = ::requestNearestRailItem
                )
            } else Box(
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxWidth()
                    .padding(
                        start = TvDimensions.sideRailContentInsetNew,
                        end = TvDimensions.pageHorizontal,
                        top = TvDimensions.pageVertical,
                        bottom = TvDimensions.pageVertical
                    )
            ) {
                when (selectedNav) {
                HomeNavTabs.RECOMMEND -> VideoListContent(
                    state = recommendState ?: UiState.Loading,
                    errorTitle = "\u63a8\u8350\u89c6\u9891\u52a0\u8f7d\u5931\u8d25",
                    emptyMessage = "\u6682\u65e0\u5185\u5bb9",
                    onRetry = onLoadRecommend,
                    onLoadMore = onLoadMoreRecommend,
                    onVideoClick = onVideoClick,
                    leftFocusRequester = sideNavFocusRequester,
                    tabKey = HomeNavTabs.RECOMMEND,
                    focusRestore = homeVideoFocusRestore.takeIf { it?.tab == HomeNavTabs.RECOMMEND },
                    onVideoFocused = { video, index ->
                        reportVideoFocused(HomeNavTabs.RECOMMEND, video, index, null)
                    },
                    onFocusRestored = onHomeVideoFocusRestored,
                    entryFocusRequester = recommendEntryFocusRequester,
                    cardStyle = VideoCardStyle.Cinematic
                )

                HomeNavTabs.POPULAR -> VideoListContent(
                    state = popularState ?: UiState.Loading,
                    errorTitle = "\u70ed\u95e8\u89c6\u9891\u52a0\u8f7d\u5931\u8d25",
                    emptyMessage = "\u6682\u65e0\u5185\u5bb9",
                    onRetry = onLoadPopular,
                    onLoadMore = onLoadMorePopular,
                    onVideoClick = onVideoClick,
                    leftFocusRequester = sideNavFocusRequester,
                    showRanking = false,
                    tabKey = HomeNavTabs.POPULAR,
                    focusRestore = homeVideoFocusRestore.takeIf { it?.tab == HomeNavTabs.POPULAR },
                    onVideoFocused = { video, index ->
                        reportVideoFocused(HomeNavTabs.POPULAR, video, index, null)
                    },
                    onFocusRestored = onHomeVideoFocusRestored,
                    entryFocusRequester = popularEntryFocusRequester,
                    cardStyle = VideoCardStyle.Cinematic
                )

                HomeNavTabs.SEARCH -> SearchScreen(
                    suggestionsState = searchSuggestionsState,
                    resultsState = searchResultsState,
                    onSuggest = onLoadSearchSuggestions,
                    onSearch = onSearch,
                    onLoadMore = onLoadMoreSearch,
                    onClearResults = onClearSearchResults,
                    onVideoClick = { video, index -> onVideoClick(video, index) },
                    entryFocusRequester = searchEntryFocusRequester,
                    homeVideoFocusRestore = homeVideoFocusRestore,
                    onHomeVideoFocusRestored = onHomeVideoFocusRestored
                )

                HomeNavTabs.DYNAMIC -> DynamicContent(
                    currentUser = currentUser,
                    dynamicState = dynamicState,
                    upVideoState = upVideoState,
                    selectedUpMid = selectedDynamicUpMid,
                    onLoadMoreDynamic = onLoadMoreDynamic,
                    onLoadMoreUpVideos = onLoadMoreUpVideos,
                    onSelectedUp = { up ->
                        onSelectedDynamicUpMid(up?.mid)
                        if (up != null) {
                            onLoadUpVideos(up)
                        }
                    },
                    onLoginClick = onLoginClick,
                    onRetry = onLoadDynamic,
                    onVideoClick = onVideoClick,
                    onVideoFocused = reportVideoFocused,
                    filterFocusRequester = dynamicFilterFocusRequester,
                    homeVideoFocusRestore = homeVideoFocusRestore,
                    onHomeVideoFocusRestored = onHomeVideoFocusRestored,
                    leftFocusRequester = sideNavFocusRequester,
                    onUpClick = onUpClick,
                    entryFocusRequester = dynamicEntryFocusRequester
                )

                HomeNavTabs.HISTORY -> HistoryContent(
                    currentUser = currentUser,
                    historyState = historyState,
                    onLoginClick = onLoginClick,
                    onRetry = onLoadHistory,
                    onLoadMore = onLoadMoreHistory,
                    onVideoClick = onVideoClick,
                    tabKey = HomeNavTabs.HISTORY,
                    focusRestore = homeVideoFocusRestore.takeIf { it?.tab == HomeNavTabs.HISTORY },
                    onVideoFocused = { video, index ->
                        reportVideoFocused(HomeNavTabs.HISTORY, video, index, null)
                    },
                    onFocusRestored = onHomeVideoFocusRestored
                    , leftFocusRequester = sideNavFocusRequester,
                    entryFocusRequester = historyEntryFocusRequester
                )

                HomeNavTabs.MY -> MyScreen(
                    currentUser = currentUser,
                    historyState = historyState,
                    watchLaterState = watchLaterState,
                    onLoginClick = onLoginClick,
                    onLogoutClick = onLogoutClick,
                    onOpenFavorites = onOpenFavorites,
                    onOpenWatchLater = onOpenWatchLater,
                    onOpenHistory = onOpenHistoryFromMy,
                    onOpenSettings = onOpenSettingsFromMy,
                    accountActionFocusRequester = myAccountActionFocusRequester,
                    leftFocusRequester = sideNavFocusRequester,
                    restoreFocusEntry = restoreMyFocusEntry,
                    onFocusRestored = onMyFocusRestored,
                    onLoginRequired = onLoginClick,
                    onVideoClick = { video -> onVideoClick(video, 0) }
                )

                HomeNavTabs.SETTINGS -> SettingsScreen(
                    currentUser = currentUser,
                    preferredQualityQn = preferredQualityQn,
                    defaultPlaybackSpeed = defaultPlaybackSpeed,
                    playbackProfile = playbackProfile,
                    danmakuSettings = danmakuSettings,
                    startupTab = startupTab,
                    autoPlayNextPageEnabled = autoPlayNextPageEnabled,
                    playbackEndBehavior = playbackEndBehavior,
                    onPreferredQualitySelected = onPreferredQualitySelected,
                    onDefaultPlaybackSpeedSelected = onDefaultPlaybackSpeedSelected,
                    onPlaybackProfileSelected = onPlaybackProfileSelected,
                    onDanmakuSettingsChanged = onDanmakuSettingsChanged,
                    onStartupTabSelected = onStartupTabSelected,
                    onAutoPlayNextPageSelected = onAutoPlayNextPageSelected,
                    onPlaybackEndBehaviorSelected = onPlaybackEndBehaviorSelected,
                    danmakuBlocklistEnabled = danmakuBlocklistEnabled,
                    onDanmakuBlocklistEnabledChanged = onDanmakuBlocklistEnabledChanged,
                    onOpenDanmakuBlocklist = onOpenDanmakuBlocklist,
                    onLoginClick = onLoginClick,
                    onLogoutClick = onLogoutClick,
                    settingsFocusCategory = settingsFocusCategory,
                    accountFocusRequester = settingsAccountFocusRequester,
                    onReturnToRail = onSettingsLeft,
                    onCategoryOpened = { settingsFocusCategory = it }
                )

                else -> VideoGrid(
                    videos = mockVideos,
                    onVideoClick = onVideoClick,
                    tabKey = selectedNav
                )
                }
            }
            CinematicSideRail(
                items = navItems,
                selected = selectedNav,
                onSelected = onSelectedNav,
                onRefreshSelected = ::refreshCurrentTab,
                selectedItemFocusRequester = sideNavFocusRequester,
                selectedItemRightFocusRequester = when (selectedNav) {
                    HomeNavTabs.HOME -> null
                    HomeNavTabs.RECOMMEND -> recommendEntryFocusRequester
                    HomeNavTabs.POPULAR -> popularEntryFocusRequester
                    HomeNavTabs.DYNAMIC -> dynamicEntryFocusRequester
                    HomeNavTabs.HISTORY -> historyEntryFocusRequester
                    HomeNavTabs.SEARCH -> searchEntryFocusRequester
                    HomeNavTabs.MY -> myAccountActionFocusRequester
                    HomeNavTabs.SETTINGS -> settingsAccountFocusRequester
                    else -> null
                },
                selectedItemOnRight = null,
                preferSelectedItemRightFocusRequester =
                    selectedNav == HomeNavTabs.RECOMMEND ||
                    selectedNav == HomeNavTabs.POPULAR ||
                    selectedNav == HomeNavTabs.DYNAMIC ||
                    selectedNav == HomeNavTabs.HISTORY ||
                    selectedNav == HomeNavTabs.SETTINGS,
                itemFocusRequesters = sideNavItemFocusRequesters,
                onItemPositioned = { item, centerY -> sideNavItemCenters[item] = centerY },
                onItemRight = if (selectedNav == HomeNavTabs.HOME) {
                    { item -> requestNearestHomeRow(item) }
                } else null,
                preferItemRightHandler = selectedNav == HomeNavTabs.HOME,
                disabledItems = HomeNavTabs.DISABLED,
                onFocusWithinChanged = { focused ->
                    sideNavHasFocus = focused
                    if (focused) {
                        contentFocusActive = false
                    }
                },
                modifier = Modifier.padding(
                    start = TvDimensions.sideRailStartMargin,
                    top = TvDimensions.sideRailVerticalMargin,
                    bottom = TvDimensions.sideRailVerticalMargin
                )
            )
        }
    }
}


private data class LightweightHomeRowModel(
    val key: String,
    val title: String,
    val videos: List<VideoItem>,
    val showProgress: Boolean,
    val onLoadMore: () -> Unit
)

private data class LightweightHomeRowRuntime(
    val model: LightweightHomeRowModel,
    val cardFocusRequesters: List<FocusRequester>
)

@Composable
private fun LightweightHomeContent(
    currentUser: UserInfo?,
    historyState: HistoryUiState?,
    dynamicState: DynamicUiState?,
    continueEntryFocusRequester: FocusRequester,
    dynamicEntryFocusRequester: FocusRequester,
    onRetryHistory: () -> Unit,
    onRetryDynamic: () -> Unit,
    onLoadMoreHistory: () -> Unit,
    onLoadMoreDynamic: () -> Unit,
    onVideoClick: (VideoItem, Int) -> Unit,
    onVideoFocused: (VideoItem, Int) -> Unit,
    focusRestore: HomeVideoFocusRestore? = null,
    onFocusRestored: () -> Unit = {},
    onRowPositioned: (String, Float) -> Unit,
    onFirstCardLeft: (String) -> Boolean
) {
    val historyVideos = (historyState as? HistoryUiState.Success)?.videos.orEmpty()
    val dynamicVideos = (dynamicState as? DynamicUiState.Success)?.data?.videos.orEmpty()

    if (currentUser == null) {
        TvEmptyContent(message = "登录后可查看继续观看和动态")
        return
    }

    val continueRequesterCache = remember { mutableMapOf<String, FocusRequester>() }
    val dynamicRequesterCache = remember { mutableMapOf<String, FocusRequester>() }

    fun requestersFor(
        videos: List<VideoItem>,
        entryRequester: FocusRequester,
        cache: MutableMap<String, FocusRequester>
    ): List<FocusRequester> = videos.mapIndexed { index, video ->
        if (index == 0) {
            entryRequester
        } else {
            cache.getOrPut(video.stableContentKey) { FocusRequester() }
        }
    }

    val rows = buildList {
        if (historyVideos.isNotEmpty()) {
            add(
                LightweightHomeRowRuntime(
                    model = LightweightHomeRowModel(
                        key = HOME_ROW_CONTINUE,
                        title = "继续观看",
                        videos = historyVideos,
                        showProgress = true,
                        onLoadMore = onLoadMoreHistory
                    ),
                    cardFocusRequesters = requestersFor(
                        historyVideos,
                        continueEntryFocusRequester,
                        continueRequesterCache
                    )
                )
            )
        }
        if (dynamicVideos.isNotEmpty()) {
            add(
                LightweightHomeRowRuntime(
                    model = LightweightHomeRowModel(
                        key = HOME_ROW_DYNAMIC,
                        title = "动态",
                        videos = dynamicVideos,
                        showProgress = false,
                        onLoadMore = onLoadMoreDynamic
                    ),
                    cardFocusRequesters = requestersFor(
                        dynamicVideos,
                        dynamicEntryFocusRequester,
                        dynamicRequesterCache
                    )
                )
            )
        }
    }

    if (rows.isEmpty()) {
        val loading = historyState == null ||
            historyState == HistoryUiState.Loading ||
            dynamicState == null ||
            dynamicState == DynamicUiState.Loading
        if (loading) {
            TvLoadingContent()
            return
        }

        val errors = buildList {
            (historyState as? HistoryUiState.Error)?.message?.let(::add)
            (dynamicState as? DynamicUiState.Error)?.message?.let(::add)
        }
        if (errors.isNotEmpty()) {
            TvErrorContent(
                title = "首页内容加载失败",
                message = errors.joinToString("；"),
                onRetry = {
                    onRetryHistory()
                    onRetryDynamic()
                }
            )
        } else {
            TvEmptyContent(message = "暂无继续观看或动态")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = TvDimensions.sideRailContentInsetNew + 20.dp,
                end = 24.dp,
                top = 24.dp,
                bottom = 16.dp
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        rows.forEachIndexed { rowIndex, rowRuntime ->
            val upperRowRequesters = rows.getOrNull(rowIndex - 1)?.cardFocusRequesters
            val lowerRowRequesters = rows.getOrNull(rowIndex + 1)?.cardFocusRequesters
            LightweightHomeRow(
                runtime = rowRuntime,
                upperRowFocusRequesters = upperRowRequesters,
                lowerRowFocusRequesters = lowerRowRequesters,
                focusRestore = focusRestore,
                onFocusRestored = onFocusRestored,
                onVideoClick = onVideoClick,
                onVideoFocused = onVideoFocused,
                onRowPositioned = onRowPositioned,
                onFirstCardLeft = onFirstCardLeft
            )
        }
    }
}

@Composable
private fun LightweightHomeRow(
    runtime: LightweightHomeRowRuntime,
    upperRowFocusRequesters: List<FocusRequester>?,
    lowerRowFocusRequesters: List<FocusRequester>?,
    focusRestore: HomeVideoFocusRestore?,
    onFocusRestored: () -> Unit,
    onVideoClick: (VideoItem, Int) -> Unit,
    onVideoFocused: (VideoItem, Int) -> Unit,
    onRowPositioned: (String, Float) -> Unit,
    onFirstCardLeft: (String) -> Boolean
) {
    val row = runtime.model
    val rowState = rememberLazyListState()

    val rowVideoKeys = remember(row.videos) {
        row.videos.map { it.stableContentKey }
    }
    LaunchedEffect(
        focusRestore?.restoreToken,
        focusRestore?.videoKey,
        rowVideoKeys
    ) {
        val restore = focusRestore ?: return@LaunchedEffect
        if (restore.restoreToken == 0L || restore.tab != HomeNavTabs.HOME) return@LaunchedEffect
        val targetIndex = row.videos.indexOfFirst { it.stableContentKey == restore.videoKey }
        if (targetIndex !in runtime.cardFocusRequesters.indices) return@LaunchedEffect
        val requester = runtime.cardFocusRequesters[targetIndex]
        var restored = runCatching { requester.requestFocus(); true }.getOrDefault(false)
        if (!restored) {
            runCatching { rowState.scrollToItem(targetIndex) }
            withFrameNanos { }
            restored = runCatching { requester.requestFocus(); true }.getOrDefault(false)
        }
        if (restored) onFocusRestored()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onRowPositioned(row.key, coordinates.boundsInRoot().center.y)
            }
    ) {
        Text(
            text = row.title,
            color = TvColors.TextPrimary,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 7.dp)
        )
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(end = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = row.videos,
                key = { _, video -> "${row.key}:${video.stableContentKey}" },
                contentType = { _, _ -> row.key }
            ) { index, video ->
                val upRequester = upperRowFocusRequesters?.let { requesters ->
                    requesters.getOrNull(index) ?: requesters.lastOrNull()
                }
                val downRequester = lowerRowFocusRequesters?.let { requesters ->
                    requesters.getOrNull(index) ?: requesters.lastOrNull()
                }
                val requester = runtime.cardFocusRequesters[index]
                LightweightHomeCard(
                    video = video,
                    showProgress = row.showProgress,
                    focusRequester = requester,
                    upFocusRequester = upRequester,
                    downFocusRequester = downRequester,
                    blockUp = upperRowFocusRequesters == null,
                    blockDown = lowerRowFocusRequesters == null,
                    onLeftFromFirstCard = if (index == 0) {
                        { onFirstCardLeft(row.key) }
                    } else null,
                    onFocused = {
                        onVideoFocused(video, index)
                        if (index >= row.videos.lastIndex - 3) {
                            row.onLoadMore()
                        }
                    },
                    onClick = { onVideoClick(video, index) }
                )
            }
        }
    }
}

@Composable
private fun LightweightHomeCard(
    video: VideoItem,
    showProgress: Boolean,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester?,
    blockUp: Boolean,
    blockDown: Boolean,
    onLeftFromFirstCard: (() -> Boolean)?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val coverShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    val metadata = remember(
        video.ownerName,
        video.historyProgress,
        video.duration,
        video.historyViewAt,
        video.pubdate,
        showProgress
    ) {
        buildLightweightHomeMetadata(video, showProgress)
    }
    Column(
        modifier = Modifier
            .width(242.dp)
            .background(Color(0xE60B1014), shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvColors.FocusRing else TvColors.SideRailBorder,
                shape = shape
            )
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionLeft -> onLeftFromFirstCard?.invoke() == true
                        Key.DirectionUp -> {
                            when {
                                upFocusRequester != null -> runCatching {
                                    upFocusRequester.requestFocus()
                                    true
                                }.getOrDefault(false)
                                blockUp -> true
                                else -> false
                            }
                        }
                        Key.DirectionDown -> {
                            when {
                                downFocusRequester != null -> runCatching {
                                    downFocusRequester.requestFocus()
                                    true
                                }.getOrDefault(false)
                                blockDown -> true
                                else -> false
                            }
                        }
                        else -> false
                    }
                }
            }
            .onFocusChanged {
                val nowFocused = it.isFocused
                if (focused != nowFocused) {
                    focused = nowFocused
                    if (nowFocused) onFocused()
                }
            }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        CoverPlaceholder(
            video = video,
            showDuration = true,
            showStatsOverlay = true,
            badgeFontSize = 10.sp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(coverShape)
        )

        val hasProgress = showProgress && video.duration > 0L && video.historyProgress > 0L
        val progress = if (hasProgress) {
            (video.historyProgress.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(if (showProgress) TvColors.ProgressTrack else Color.Transparent)
        ) {
            if (hasProgress) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(TvColors.ProgressFill)
                )
            }
        }

        Text(
            text = video.title,
            color = TvColors.TextPrimary,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .height(42.dp)
                .padding(start = 9.dp, end = 9.dp, top = 5.dp)
        )
        Text(
            text = metadata,
            color = TvColors.TextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .padding(horizontal = 9.dp, vertical = 2.dp)
        )
    }
}

private fun buildLightweightHomeMetadata(
    video: VideoItem,
    showProgress: Boolean
): String {
    val owner = video.ownerName.ifBlank { "未知UP主" }
    val detail = if (showProgress) {
        FormatUtils.formatProgressWithDuration(video.historyProgress, video.duration)
            .ifBlank { FormatUtils.formatHistoryTime(video.historyViewAt) }
    } else {
        formatPubDate(video.pubdate)
    }
    return listOf(owner, detail)
        .filter { it.isNotBlank() }
        .joinToString("  ·  ")
}

@Composable
private fun HistoryContent(
    currentUser: UserInfo?,
    historyState: HistoryUiState?,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (VideoItem, Int) -> Unit,
    tabKey: String,
    focusRestore: HomeVideoFocusRestore? = null,
    onVideoFocused: ((VideoItem, Int) -> Unit)? = null,
    onFocusRestored: () -> Unit = {}
    , leftFocusRequester: FocusRequester? = null,
    entryFocusRequester: FocusRequester? = null
) {
    if (currentUser == null) {
        NotLoggedInContent(onLoginClick = onLoginClick)
        return
    }

    when (val state = historyState ?: HistoryUiState.Loading) {
        HistoryUiState.Loading -> TvLoadingContent()

        HistoryUiState.Empty -> TvEmptyContent(message = "\u6682\u65e0\u89c2\u770b\u8bb0\u5f55")

        is HistoryUiState.Error -> TvErrorContent(
            title = "\u5386\u53f2\u8bb0\u5f55\u52a0\u8f7d\u5931\u8d25",
            message = state.message,
            onRetry = onRetry
        )

        is HistoryUiState.Success -> {
            if (state.videos.isEmpty()) {
                TvEmptyContent(message = "\u6682\u65e0\u89c2\u770b\u8bb0\u5f55")
            } else {
                VideoGrid(
                    videos = state.videos,
                    onVideoClick = onVideoClick,
                    leftFocusRequester = leftFocusRequester,
                    hasMore = state.hasMore,
                    isLoadingMore = state.isLoadingMore,
                    loadMoreError = state.loadMoreError,
                    onLoadMore = onLoadMore,
                    tabKey = tabKey,
                    focusRestore = focusRestore,
                    onVideoFocused = onVideoFocused,
                    onFocusRestored = onFocusRestored,
                    entryFocusRequester = entryFocusRequester,
                    showProgress = true
                )
            }
        }
    }
}

@Composable
private fun DynamicContent(
    currentUser: UserInfo?,
    dynamicState: DynamicUiState?,
    upVideoState: UiState<PagedVideoList>?,
    selectedUpMid: Long?,
    onLoadMoreDynamic: () -> Unit,
    onLoadMoreUpVideos: () -> Unit,
    onSelectedUp: (FollowedUp?) -> Unit,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onVideoClick: (VideoItem, Int) -> Unit,
    onVideoFocused: (String, VideoItem, Int, Long?) -> Unit,
    homeVideoFocusRestore: HomeVideoFocusRestore? = null,
    onHomeVideoFocusRestored: () -> Unit = {},
    leftFocusRequester: FocusRequester? = null,
    filterFocusRequester: FocusRequester? = null,
    onUpClick: ((Long, String) -> Unit)? = null,
    entryFocusRequester: FocusRequester? = null
) {
    if (currentUser == null) {
        NotLoggedInContent(onLoginClick = onLoginClick)
        return
    }

    when (val state = dynamicState ?: DynamicUiState.Loading) {
        DynamicUiState.Loading -> TvLoadingContent()

        DynamicUiState.Empty -> TvEmptyContent(message = "\u6682\u65e0\u66f4\u65b0")

        is DynamicUiState.Error -> TvErrorContent(
            title = "\u52a8\u6001\u52a0\u8f7d\u5931\u8d25",
            message = state.message,
            onRetry = onRetry
        )

        is DynamicUiState.Success -> DynamicSuccessContent(
            state = state,
            upVideoState = upVideoState,
            selectedUpMid = selectedUpMid,
            onLoadMoreDynamic = onLoadMoreDynamic,
            onLoadMoreUpVideos = onLoadMoreUpVideos,
            onSelectedUp = onSelectedUp,
            onVideoClick = onVideoClick,
            onVideoFocused = onVideoFocused,
            homeVideoFocusRestore = homeVideoFocusRestore,
            onHomeVideoFocusRestored = onHomeVideoFocusRestored,
            leftFocusRequester = leftFocusRequester,
            filterFocusRequester = filterFocusRequester,
            onUpClick = onUpClick,
            entryFocusRequester = entryFocusRequester
        )
    }
}

@Composable
private fun DynamicSuccessContent(
    state: DynamicUiState.Success,
    upVideoState: UiState<PagedVideoList>?,
    selectedUpMid: Long?,
    onLoadMoreDynamic: () -> Unit,
    onLoadMoreUpVideos: () -> Unit,
    onSelectedUp: (FollowedUp?) -> Unit,
    onVideoClick: (VideoItem, Int) -> Unit,
    onVideoFocused: (String, VideoItem, Int, Long?) -> Unit,
    homeVideoFocusRestore: HomeVideoFocusRestore? = null,
    onHomeVideoFocusRestored: () -> Unit = {},
    leftFocusRequester: FocusRequester? = null,
    filterFocusRequester: FocusRequester? = null,
    onUpClick: ((Long, String) -> Unit)? = null,
    entryFocusRequester: FocusRequester? = null
) {
    val dynamicRestore = homeVideoFocusRestore?.takeIf { it.tab == HomeNavTabs.DYNAMIC }

    LaunchedEffect(dynamicRestore?.restoreToken, dynamicRestore?.dynamicUpMid) {
        val restore = dynamicRestore ?: return@LaunchedEffect
        if (restore.dynamicUpMid != null && restore.dynamicUpMid != selectedUpMid) {
            val up = state.data.followedUps.firstOrNull { it.mid == restore.dynamicUpMid }
            if (up != null) {
                onSelectedUp(up)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        UpFilterBar(
            followedUps = state.data.followedUps,
            selectedUpMid = selectedUpMid,
            onSelected = { mid ->
                val selectedUp = state.data.followedUps.firstOrNull { it.mid == mid }
                onSelectedUp(selectedUp)
            },
            selectedItemFocusRequester = filterFocusRequester,
            modifier = Modifier
                .height(40.dp)
                .padding(bottom = 6.dp)
        )

        if (selectedUpMid == null) {
            if (state.data.videos.isEmpty()) {
                TvEmptyContent(message = "\u6682\u65e0\u52a8\u6001\u66f4\u65b0")
            } else {
                VideoGrid(
                    videos = state.data.videos,
                    hasMore = state.data.hasMore,
                    isLoadingMore = state.data.isLoadingMore,
                    loadMoreError = state.data.loadMoreError,
                    onLoadMore = onLoadMoreDynamic,
                    onVideoClick = onVideoClick,
                    leftFocusRequester = leftFocusRequester,
                    tabKey = HomeNavTabs.DYNAMIC,
                    focusRestore = dynamicRestore?.takeIf { it.dynamicUpMid == null },
                    onVideoFocused = { video, index ->
                        onVideoFocused(HomeNavTabs.DYNAMIC, video, index, null)
                    },
                    onFocusRestored = onHomeVideoFocusRestored,
                    entryFocusRequester = entryFocusRequester
                )
            }
        } else {
                UpVideoContent(
                    state = upVideoState ?: UiState.Loading,
                    onLoadMore = onLoadMoreUpVideos,
                    onRetry = {
                        val selectedUp = state.data.followedUps.firstOrNull { it.mid == selectedUpMid }
                        if (selectedUp != null) {
                            onSelectedUp(selectedUp)
                        }
                    },
                    onVideoClick = onVideoClick,
                    onUpClick = onUpClick,
                    leftFocusRequester = leftFocusRequester,
                    tabKey = HomeNavTabs.DYNAMIC,
                    focusRestore = dynamicRestore?.takeIf { it.dynamicUpMid == selectedUpMid },
                    onVideoFocused = { video, index ->
                        onVideoFocused(HomeNavTabs.DYNAMIC, video, index, selectedUpMid)
                    },
                    onFocusRestored = onHomeVideoFocusRestored,
                    entryFocusRequester = entryFocusRequester
                )
        }
    }
}

@Composable
private fun UpVideoContent(
    state: UiState<PagedVideoList>,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (VideoItem, Int) -> Unit,
    onUpClick: ((Long, String) -> Unit)? = null,
    leftFocusRequester: FocusRequester? = null,
    tabKey: String,
    focusRestore: HomeVideoFocusRestore? = null,
    onVideoFocused: ((VideoItem, Int) -> Unit)? = null,
    onFocusRestored: () -> Unit = {},
    entryFocusRequester: FocusRequester? = null
) {
    when (state) {
        UiState.Loading -> TvLoadingContent()

        is UiState.Success -> {
            if (state.data.videos.isEmpty()) {
                TvEmptyContent(message = "\u6682\u65e0\u6295\u7a3f\u89c6\u9891")
            } else {
                VideoGrid(
                    videos = state.data.videos,
                    hasMore = state.data.hasMore,
                    isLoadingMore = state.data.isLoadingMore,
                    loadMoreError = state.data.loadMoreError,
                    onLoadMore = onLoadMore,
                    onVideoClick = onVideoClick,
                    leftFocusRequester = leftFocusRequester,
                    tabKey = tabKey,
                    focusRestore = focusRestore,
                    onVideoFocused = onVideoFocused,
                    onFocusRestored = onFocusRestored,
                    entryFocusRequester = entryFocusRequester
                )
            }
        }

        is UiState.Error -> TvErrorContent(
            title = "\u6295\u7a3f\u89c6\u9891\u52a0\u8f7d\u5931\u8d25",
            message = state.message,
            onRetry = onRetry
        )
    }
}

@Composable
private fun NotLoggedInContent(onLoginClick: () -> Unit) {
    TvNotLoggedInContent(
        hint = "\u767b\u5f55\u540e\u53ef\u67e5\u770b\u5173\u6ce8 UP \u4e3b\u7684\u89c6\u9891\u52a8\u6001",
        onLoginClick = onLoginClick
    )
}


@Composable
private fun VideoListContent(
    state: UiState<PagedVideoList>,
    errorTitle: String,
    emptyMessage: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (VideoItem, Int) -> Unit,
    leftFocusRequester: FocusRequester? = null,
    showRanking: Boolean = false,
    tabKey: String,
    focusRestore: HomeVideoFocusRestore? = null,
    onVideoFocused: ((VideoItem, Int) -> Unit)? = null,
    onFocusRestored: () -> Unit = {},
    entryFocusRequester: FocusRequester? = null,
    cardStyle: VideoCardStyle = VideoCardStyle.Standard
) {
    when (state) {
        UiState.Loading -> TvLoadingContent()

        is UiState.Success -> {
            if (state.data.videos.isEmpty()) {
                TvEmptyContent(message = emptyMessage)
            } else {
                VideoGrid(
                    videos = state.data.videos,
                    onVideoClick = onVideoClick,
                    leftFocusRequester = leftFocusRequester,
                    showRanking = showRanking,
                    hasMore = state.data.hasMore,
                    isLoadingMore = state.data.isLoadingMore,
                    loadMoreError = state.data.loadMoreError,
                    onLoadMore = onLoadMore,
                    tabKey = tabKey,
                    focusRestore = focusRestore,
                    onVideoFocused = onVideoFocused,
                    onFocusRestored = onFocusRestored,
                    entryFocusRequester = entryFocusRequester,
                    cardStyle = cardStyle
                )
            }
        }

        is UiState.Error -> TvErrorContent(
            title = errorTitle,
            message = state.message,
            onRetry = onRetry
        )
    }
}
