package com.bililite.tv.ui.search

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bililite.tv.data.storage.SearchHistoryStorage
import com.bililite.tv.model.SearchSuggestion
import com.bililite.tv.model.VideoItem
import com.bililite.tv.theme.TvColors
import com.bililite.tv.ui.components.CinematicGlassSurface
import com.bililite.tv.ui.components.GlassVariant
import com.bililite.tv.ui.components.GlassVisualOverrides
import com.bililite.tv.ui.home.HomeVideoFocusRestore
import com.bililite.tv.ui.home.VideoGrid
import com.bililite.tv.ui.state.PagedVideoList
import com.bililite.tv.ui.state.UiState
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    suggestionsState: UiState<List<SearchSuggestion>>,
    resultsState: UiState<PagedVideoList>?,
    onSuggest: (String) -> Unit,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onClearResults: () -> Unit,
    onVideoClick: (VideoItem, Int) -> Unit,
    entryFocusRequester: FocusRequester? = null,
    homeVideoFocusRestore: HomeVideoFocusRestore? = null,
    onHomeVideoFocusRestored: () -> Unit = {}
) {
    var confirmedQuery by remember { mutableStateOf("") }
    var candidateChars by remember { mutableStateOf<List<String>>(emptyList()) }
    var candidateFocusSignal by remember { mutableStateOf(0) }
    var showingResults by rememberSaveable { mutableStateOf(false) }
    var selectedKeyIndex by remember { mutableStateOf(0) }
    var restoreFocusSignal by remember { mutableStateOf(1) }
    val context = LocalContext.current
    val historyStorage = remember(context) { SearchHistoryStorage(context) }
    var searchHistory by remember { mutableStateOf(historyStorage.loadHistory()) }
    val displayedQuery = confirmedQuery
    val inputFocusRequester = entryFocusRequester ?: remember { FocusRequester() }
    val searchButtonFocusRequester = remember { FocusRequester() }
    val keyboardFirstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        Log.i(INPUT_TAG, "SearchScreen entered")
        Log.i(INPUT_TAG, "focus ABC")
    }

    BackHandler(enabled = showingResults) {
        showingResults = false
        onClearResults()
        restoreFocusSignal += 1
    }

    fun closeCandidates() {
        candidateChars = emptyList()
        restoreFocusSignal += 1
    }

    BackHandler(enabled = candidateChars.isNotEmpty()) {
        closeCandidates()
    }

    LaunchedEffect(displayedQuery) {
        delay(700)
        Log.i(INPUT_TAG, "query changed=$displayedQuery")
        onSuggest(displayedQuery)
    }

    fun performSearch() {
        val clean = confirmedQuery.trim()
        Log.i(INPUT_TAG, "current query=$clean")
        Log.i(T9_TAG, "search clicked")
        if (clean.isBlank()) {
            return
        }
        searchHistory = historyStorage.addHistory(clean)
        Log.i(INPUT_TAG, "search executed keyword=$clean")
        Log.i(T9_TAG, "search keyword=$clean")
        showingResults = true
        onSearch(clean)
    }

    fun openCandidates(index: Int, key: NineKey) {
        if (key.chars.isEmpty()) return
        Log.i(T9_TAG, "key clicked: ${key.label}")
        selectedKeyIndex = index
        candidateChars = key.chars
        candidateFocusSignal += 1
    }

    if (showingResults && resultsState != null) {
        SearchResultsContent(
            state = resultsState,
            onBackToSuggestions = {
                showingResults = false
                onClearResults()
                restoreFocusSignal += 1
            },
            onLoadMore = onLoadMore,
            onVideoClick = onVideoClick,
            focusRestore = homeVideoFocusRestore,
            onFocusRestored = onHomeVideoFocusRestored
        )
        return
    }

    CinematicGlassSurface(
        modifier = Modifier.fillMaxSize(),
        variant = GlassVariant.Panel,
        focused = false,
        selected = false,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = 18.dp,
        ),
        visualOverrides = GlassVisualOverrides(
            bodyColor = TvColors.Glass.BodyRail.copy(alpha = 0.30f),
            gradientTopColor = TvColors.Glass.GradTop.copy(alpha = 0.12f),
            gradientBottomColor = TvColors.Glass.GradBottom.copy(alpha = 0.08f),
            innerHighlightColor = TvColors.Glass.InnerTop.copy(alpha = 0.08f),
            outerEdgeColor = TvColors.FocusRing.copy(alpha = 0.22f),
            innerEdgeColor = TvColors.Glass.EdgeBright.copy(alpha = 0.14f),
            normalEdgeWidth = 1.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Column(
                modifier = Modifier.width(480.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchInputDisplay(
                        keyword = displayedQuery,
                        focusRequester = inputFocusRequester,
                        rightFocusRequester = searchButtonFocusRequester,
                        downFocusRequester = keyboardFirstFocusRequester,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    SearchActionButton(
                        text = "\u641c\u7d22",
                        focusRequester = searchButtonFocusRequester,
                        leftFocusRequester = inputFocusRequester,
                        downFocusRequester = keyboardFirstFocusRequester,
                        modifier = Modifier
                            .width(104.dp)
                            .fillMaxHeight(),
                        onClick = {
                            Log.i(INPUT_TAG, "search button clicked")
                            performSearch()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    TvNineKeyKeyboard(
                        mode = SearchInputMode.CHINESE,
                        restoreFocusKeyIndex = selectedKeyIndex,
                        restoreFocusSignal = restoreFocusSignal,
                        firstKeyFocusRequester = keyboardFirstFocusRequester,
                        topRowUpFocusRequester = inputFocusRequester,
                        onKeyFocused = { index, key ->
                            selectedKeyIndex = index
                            Log.i(INPUT_TAG, "key focused: ${key.label}")
                        },
                        onKeySelected = { index, key ->
                            selectedKeyIndex = index
                            Log.i(INPUT_TAG, "key clicked: ${key.label}")
                            when (key.action) {
                                NineKeyAction.INPUT -> openCandidates(index, key)
                                NineKeyAction.DELETE -> {
                                    if (confirmedQuery.isNotEmpty()) {
                                        confirmedQuery = confirmedQuery.dropLast(1)
                                        Log.i(T9_TAG, "delete confirmed char")
                                        Log.i(T9_TAG, "query changed=$confirmedQuery")
                                    }
                                    Log.i(INPUT_TAG, "delete")
                                }
                                NineKeyAction.CLEAR -> {
                                    confirmedQuery = ""
                                    Log.i(INPUT_TAG, "clear")
                                    Log.i(T9_TAG, "query changed=")
                                }
                                NineKeyAction.SPACE -> {
                                    confirmedQuery += " "
                                    Log.i(INPUT_TAG, "input char=space")
                                    Log.i(T9_TAG, "query changed=$confirmedQuery")
                                }
                                NineKeyAction.SEARCH -> performSearch()
                                NineKeyAction.SWITCH_CHINESE,
                                NineKeyAction.SWITCH_ENGLISH,
                                NineKeyAction.SWITCH_NUMBER -> Unit
                            }
                        }
                    )
                    if (candidateChars.isNotEmpty()) {
                        CharacterCandidateBar(
                            candidates = candidateChars,
                            focusSignal = candidateFocusSignal,
                            onCandidateSelected = { candidate ->
                                confirmedQuery += candidate
                                Log.i(T9_TAG, "candidate committed=$candidate query=$confirmedQuery")
                                closeCandidates()
                            },
                            onReturnToNineKey = ::closeCandidates,
                            modifier = Modifier.offset(y = (-76).dp).zIndex(4f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Text(
                    text = if (displayedQuery.isBlank()) "\u641c\u7d22\u5386\u53f2" else "\u641c\u7d22\u5efa\u8bae",
                    color = TvColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                if (displayedQuery.isBlank()) {
                    SearchHistoryPanel(
                        history = searchHistory,
                        onSelected = { selected ->
                            Log.i(HISTORY_TAG, "selected history keyword=$selected")
                            confirmedQuery = selected
                            candidateChars = emptyList()
                            searchHistory = historyStorage.addHistory(selected)
                            showingResults = true
                            onSearch(selected)
                        },
                        onClear = {
                            historyStorage.clearHistory()
                            searchHistory = emptyList()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SearchSuggestionList(
                        state = suggestionsState,
                        onSelected = {
                            Log.i(INPUT_TAG, "selected suggestion=${it.keyword}")
                            confirmedQuery = it.keyword
                            candidateChars = emptyList()
                            searchHistory = historyStorage.addHistory(it.keyword)
                            showingResults = true
                            onSearch(it.keyword)
                        },
                        onRetry = { onSuggest(displayedQuery) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchInputDisplay(
    keyword: String,
    focusRequester: FocusRequester,
    rightFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    CinematicGlassSurface(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusProperties {
                right = rightFocusRequester
                down = downFocusRequester
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        variant = GlassVariant.Control,
        focused = focused,
        visualOverrides = SearchControlGlassOverrides,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = keyword.ifBlank { "\u8bf7\u8f93\u5165\u5173\u952e\u8bcd" },
                color = if (keyword.isBlank()) TvColors.TextMuted else TvColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SearchActionButton(
    text: String,
    focusRequester: FocusRequester,
    leftFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    CinematicGlassSurface(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusProperties {
                left = leftFocusRequester
                down = downFocusRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    Log.i(INPUT_TAG, "search button focused")
                }
            }
            .focusable()
            .clickable(onClick = onClick),
        variant = GlassVariant.Control,
        focused = focused,
        visualOverrides = SearchControlGlassOverrides,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = text,
            color = TvColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SearchHistoryPanel(
    history: List<String>,
    onSelected: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchCenterText("\u6682\u65e0\u641c\u7d22\u5386\u53f2", Modifier.weight(1f))
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(history, key = { it }) { keyword ->
            SearchHistoryItem(
                text = keyword,
                onClick = { onSelected(keyword) }
            )
        }
        item {
            SearchHistoryItem(
                text = "\u6e05\u7a7a\u5386\u53f2",
                isDanger = true,
                onClick = onClear
            )
        }
    }
}

@Composable
private fun SearchHistoryItem(
    text: String,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    CinematicGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        variant = GlassVariant.Control,
        focused = focused,
        visualOverrides = SearchControlGlassOverrides,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = if (isDanger) TvColors.Danger else if (focused) TvColors.TextPrimary else TvColors.TextSecondary,
            fontSize = 20.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SearchResultsContent(
    state: UiState<PagedVideoList>,
    onBackToSuggestions: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (VideoItem, Int) -> Unit,
    focusRestore: HomeVideoFocusRestore? = null,
    onFocusRestored: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "\u8fd4\u56de\u952e\u56de\u5230\u641c\u7d22\u5efa\u8bae",
            color = TvColors.TextMuted,
            fontSize = 16.sp,
            modifier = Modifier
                .clickable(onClick = onBackToSuggestions)
                .padding(bottom = 12.dp)
        )
        when (state) {
            UiState.Loading -> SearchCenterText("\u641c\u7d22\u4e2d...", Modifier.fillMaxSize())
            is UiState.Error -> SearchCenterText(state.message, Modifier.fillMaxSize())
            is UiState.Success -> {
                Log.i(INPUT_TAG, "search result count=${state.data.videos.size}")
                if (state.data.videos.isEmpty()) {
                    SearchCenterText("\u641c\u7d22\u65e0\u7ed3\u679c", Modifier.fillMaxSize())
                } else {
                    VideoGrid(
                        videos = state.data.videos,
                        onVideoClick = onVideoClick,
                        hasMore = state.data.hasMore,
                        isLoadingMore = state.data.isLoadingMore,
                        loadMoreError = state.data.loadMoreError,
                        onLoadMore = onLoadMore,
                        tabKey = "搜索",
                        focusRestore = focusRestore,
                        onFocusRestored = onFocusRestored
                    )
                }
            }
        }
    }
}

private const val INPUT_TAG = "BiliSearchInput"
private const val HISTORY_TAG = "BiliSearchHistory"
private const val T9_TAG = "BiliSearchT9"
private const val COMMIT_DELAY_MS = 800L
