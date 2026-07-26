package com.bililite.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bililite.tv.model.DanmakuSettings
import com.bililite.tv.model.PlaybackEndBehavior
import com.bililite.tv.model.PlaybackProfile
import com.bililite.tv.model.UserInfo
import com.bililite.tv.theme.TvColors
import com.bililite.tv.theme.TvDimensions
import com.bililite.tv.ui.home.HomeNavTabs
import com.bililite.tv.util.BilibiliImageUrl

private val CATEGORY_TITLE_SIZE = 40.sp
private val CATEGORY_GRID_H_GAP = 24.dp
private val CATEGORY_GRID_V_GAP = 24.dp
private val CATEGORY_ICON_SIZE = 64.dp

/**
 * Visual tuning for the 2x2 category landing grid.
 *
 * The grid is always composed while the settings page is shown (no composition
 * gate), so selecting SETTINGS always renders immediately. The 账号 card uses a
 * stable FocusRequester owned by HomeScreen and wired to the rail's
 * `selectedItemRightFocusRequester`, so a D-pad Right on the rail's "设置"
 * item deterministically enters the 账号 card (spatial nav from the bottom rail
 * item finds no vertically-overlapping card, so the rail falls back to this
 * explicit requester).
 */
private enum class SettingsCategory(val label: String) {
    Account("\u8d26\u53f7"),
    Playback("\u64ad\u653e"),
    Danmaku("\u5f39\u5e55"),
    Interface("\u754c\u9762")
}

@Composable
fun SettingsScreen(
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
    danmakuBlocklistEnabled: Boolean,
    onDanmakuBlocklistEnabledChanged: (Boolean) -> Unit,
    onOpenDanmakuBlocklist: () -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    settingsFocusCategory: String,
    accountFocusRequester: FocusRequester,
    onReturnToRail: () -> Unit,
    onCategoryOpened: (String) -> Unit
) {
    var viewingCategory by remember { mutableStateOf<String?>(null) }
    // When returning from a detail panel, restore focus to the opened card.
    // On the very first entry (OK selected 设置) this is false, so focus can
    // stay on the rail's "设置" icon — OK only switches the page, it never
    // grabs content focus.
    var restoreGridFocus by remember { mutableStateOf(false) }

    if (viewingCategory != null) {
        val category = SettingsCategory.valueOf(viewingCategory!!)
        CategoryDetail(
            category = category,
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
            onBack = { viewingCategory = null }
        )
    } else {
        CategoryGrid(
            pendingFocusCategory = settingsFocusCategory,
            restoreFocus = restoreGridFocus,
            accountFocusRequester = accountFocusRequester,
            onCategorySelected = { category ->
                onCategoryOpened(category.name)
                viewingCategory = category.name
                restoreGridFocus = true
            },
            onReturnToRail = onReturnToRail,
            onFocusRestored = { restoreGridFocus = false }
        )
    }
}

@Composable
private fun CategoryGrid(
    pendingFocusCategory: String,
    restoreFocus: Boolean,
    accountFocusRequester: FocusRequester,
    onCategorySelected: (SettingsCategory) -> Unit,
    onReturnToRail: () -> Unit,
    onFocusRestored: () -> Unit
) {
    // Only the non-账号 cards get local requesters; 账号 uses the stable
    // requester owned by HomeScreen (so the rail's Right can target it).
    val requesters = remember {
        (SettingsCategory.values().toSet() - SettingsCategory.Account)
            .associateWith { FocusRequester() }
    }
    val requesterFor: (SettingsCategory) -> FocusRequester = { category ->
        if (category == SettingsCategory.Account) accountFocusRequester
        else requesters.getValue(category)
    }
    // Only re-grab focus when returning from a detail panel (restoreFocus),
    // never on the initial OK entry — so focus can stay on the rail.
    LaunchedEffect(Unit) {
        if (restoreFocus) {
            runCatching {
                requesterFor(SettingsCategory.valueOf(pendingFocusCategory)).requestFocus()
            }
            onFocusRestored()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Text(
            text = "\u8bbe\u7f6e",
            color = TvColors.TextPrimary,
            fontSize = CATEGORY_TITLE_SIZE,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 24.dp)
        )
        // Responsive 2x2 grid: rows and cards share the remaining space via
        // weight(1f), so all four cards fit a single 1920x1080 screen with no
        // scroll. No fixed width/height is applied to any card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(CATEGORY_GRID_V_GAP)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(CATEGORY_GRID_H_GAP)
            ) {
                CategoryCard(
                    category = SettingsCategory.Account,
                    requester = accountFocusRequester,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    upBlock = true,
                    leftAction = onReturnToRail,
                    onSelected = { onCategorySelected(SettingsCategory.Account) }
                )
                CategoryCard(
                    category = SettingsCategory.Playback,
                    requester = requesterFor(SettingsCategory.Playback),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    upBlock = true,
                    rightBlock = true,
                    onSelected = { onCategorySelected(SettingsCategory.Playback) }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(CATEGORY_GRID_H_GAP)
            ) {
                CategoryCard(
                    category = SettingsCategory.Danmaku,
                    requester = requesterFor(SettingsCategory.Danmaku),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    downBlock = true,
                    leftAction = onReturnToRail,
                    onSelected = { onCategorySelected(SettingsCategory.Danmaku) }
                )
                CategoryCard(
                    category = SettingsCategory.Interface,
                    requester = requesterFor(SettingsCategory.Interface),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    downBlock = true,
                    rightBlock = true,
                    onSelected = { onCategorySelected(SettingsCategory.Interface) }
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: SettingsCategory,
    requester: FocusRequester,
    modifier: Modifier = Modifier,
    upBlock: Boolean = false,
    downBlock: Boolean = false,
    rightBlock: Boolean = false,
    leftAction: (() -> Unit)? = null,
    onSelected: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val icon = when (category) {
        SettingsCategory.Account -> Icons.Filled.Person
        SettingsCategory.Playback -> Icons.Filled.PlayArrow
        SettingsCategory.Danmaku -> Icons.Filled.Send
        SettingsCategory.Interface -> Icons.Filled.Build
    }
    Box(
        modifier = modifier
            .focusRequester(requester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionUp -> upBlock
                        Key.DirectionDown -> downBlock
                        Key.DirectionRight -> rightBlock
                        Key.DirectionLeft -> {
                            if (leftAction != null) {
                                leftAction()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onSelected)
            .background(TvColors.SurfaceGlass, RoundedCornerShape(20.dp))
            .border(
                width = 2.dp,
                color = if (focused) TvColors.FocusRing else TvColors.CardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = category.label,
                tint = if (focused) TvColors.FocusRing else TvColors.TextSecondary,
                modifier = Modifier.size(CATEGORY_ICON_SIZE)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = category.label,
                color = if (focused) TvColors.TextPrimary else TvColors.TextSecondary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CategoryDetail(
    category: SettingsCategory,
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
    danmakuBlocklistEnabled: Boolean,
    onDanmakuBlocklistEnabledChanged: (Boolean) -> Unit,
    onOpenDanmakuBlocklist: () -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onBack: () -> Unit
) {
    val detailFocusRequester = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = category.label,
                color = TvColors.TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "\u6309\u8fd4\u56de\u952e\u56de\u5230\u8bbe\u7f6e",
                color = TvColors.TextMuted,
                fontSize = 16.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(TvDimensions.cardRadius))
                .background(TvColors.SurfaceGlass)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            when (category) {
                SettingsCategory.Account -> AccountSettingsPanel(
                    currentUser = currentUser,
                    onLoginClick = onLoginClick,
                    onLogoutClick = onLogoutClick,
                    initialFocusRequester = detailFocusRequester
                )
                SettingsCategory.Playback -> PlaybackSettingsPanel(
                    preferredQualityQn = preferredQualityQn,
                    defaultPlaybackSpeed = defaultPlaybackSpeed,
                    playbackProfile = playbackProfile,
                    autoPlayNextPageEnabled = autoPlayNextPageEnabled,
                    playbackEndBehavior = playbackEndBehavior,
                    onPreferredQualitySelected = onPreferredQualitySelected,
                    onDefaultPlaybackSpeedSelected = onDefaultPlaybackSpeedSelected,
                    onPlaybackProfileSelected = onPlaybackProfileSelected,
                    onAutoPlayNextPageSelected = onAutoPlayNextPageSelected,
                    onPlaybackEndBehaviorSelected = onPlaybackEndBehaviorSelected,
                    initialFocusRequester = detailFocusRequester
                )
                SettingsCategory.Danmaku -> DanmakuSettingsPanel(
                    danmakuSettings = danmakuSettings,
                    danmakuBlocklistEnabled = danmakuBlocklistEnabled,
                    onDanmakuSettingsChanged = onDanmakuSettingsChanged,
                    onDanmakuBlocklistEnabledChanged = onDanmakuBlocklistEnabledChanged,
                    onOpenDanmakuBlocklist = onOpenDanmakuBlocklist,
                    initialFocusRequester = detailFocusRequester
                )
                SettingsCategory.Interface -> InterfaceSettingsPanel(
                    startupTab = startupTab,
                    onStartupTabSelected = onStartupTabSelected,
                    initialFocusRequester = detailFocusRequester
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    LaunchedEffect(Unit) {
        runCatching { detailFocusRequester.requestFocus() }
    }
}

@Composable
private fun AccountSettingsPanel(
    currentUser: UserInfo?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    SettingsSectionTitle("\u8d26\u53f7\u72b6\u6001")
    if (currentUser == null) {
        Text(
            text = "\u672a\u767b\u5f55",
            color = TvColors.TextSecondary,
            fontSize = 17.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )
        CinematicActionButton(
            text = "\u767b\u5f55\u8d26\u53f7",
            onClick = onLoginClick,
            focusRequester = initialFocusRequester,
            blockLeft = true,
            blockUp = true,
            blockDown = true,
            blockRight = true
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 18.dp)
        ) {
            AsyncImage(
                model = BilibiliImageUrl.avatar(currentUser.avatarUrl, size = 96),
                contentDescription = currentUser.nickname,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TvColors.SurfaceElevated)
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = currentUser.nickname,
                    color = TvColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "UID ${currentUser.mid}",
                    color = TvColors.TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        CinematicActionButton(
            text = "\u9000\u51fa\u767b\u5f55",
            onClick = onLogoutClick,
            focusRequester = initialFocusRequester,
            blockLeft = true,
            blockUp = true,
            blockDown = true,
            blockRight = true
        )
    }
}

@Composable
private fun CinematicSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TvColors.SurfaceGlass)
            .border(
                width = 1.dp,
                color = TvColors.CardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
    ) {
        Text(
            text = title,
            color = TvColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        content()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CinematicOptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    blockLeft: Boolean = false,
    blockUp: Boolean = false,
    blockDown: Boolean = false,
    blockRight: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(44.dp)
            .background(TvColors.SurfaceElevated, RoundedCornerShape(12.dp))
            .border(
                width = if (focused) TvDimensions.focusBorderWidth else if (selected) 2.dp else 1.dp,
                color = if (focused) TvColors.FocusRing
                else if (selected) TvColors.FocusRingSoft
                else TvColors.CardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .focusProperties {
                if (blockLeft) left = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
                if (blockRight) right = FocusRequester.Cancel
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) TvColors.TextPrimary
            else if (selected) TvColors.FocusRing
            else TvColors.TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CinematicActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    blockLeft: Boolean = false,
    blockUp: Boolean = false,
    blockDown: Boolean = false,
    blockRight: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(52.dp)
            .background(
                color = if (focused) TvColors.SurfaceElevated else TvColors.SurfaceGlass,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (focused) TvDimensions.focusBorderWidth else 1.dp,
                color = if (focused) TvColors.FocusRing else TvColors.CardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .focusProperties {
                if (blockLeft) left = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
                if (blockRight) right = FocusRequester.Cancel
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) TvColors.TextPrimary else TvColors.TextSecondary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PlaybackSettingsPanel(
    preferredQualityQn: Int,
    defaultPlaybackSpeed: Float,
    playbackProfile: PlaybackProfile,
    autoPlayNextPageEnabled: Boolean,
    playbackEndBehavior: PlaybackEndBehavior,
    onPreferredQualitySelected: (Int) -> Unit,
    onDefaultPlaybackSpeedSelected: (Float) -> Unit,
    onPlaybackProfileSelected: (PlaybackProfile) -> Unit,
    onAutoPlayNextPageSelected: (Boolean) -> Unit,
    onPlaybackEndBehaviorSelected: (PlaybackEndBehavior) -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    var firstAssigned by remember { mutableStateOf(false) }
    val firstModifier = if (initialFocusRequester != null) {
        Modifier.focusRequester(initialFocusRequester)
    } else {
        Modifier
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        CinematicSettingsSection(title = "\u9ed8\u8ba4\u6e05\u6670\u5ea6") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                qualityOptions.chunked(6).forEachIndexed { rowIndex, rowOptions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowOptions.forEachIndexed { optionIndex, option ->
                            val isFirst = !firstAssigned
                            if (isFirst) firstAssigned = true
                            CinematicOptionChip(
                                text = option.label,
                                selected = preferredQualityQn == option.qn,
                                onClick = { onPreferredQualitySelected(option.qn) },
                                modifier = if (isFirst) firstModifier else Modifier,
                                blockLeft = optionIndex == 0,
                                blockUp = rowIndex == 0,
                                blockRight = optionIndex == rowOptions.lastIndex
                            )
                        }
                    }
                }
            }
        }

        CinematicSettingsSection(title = "\u9ed8\u8ba4\u64ad\u653e\u901f\u5ea6") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                playbackSpeedOptions.forEachIndexed { index, speed ->
                    CinematicOptionChip(
                        text = if (speed == 1.0f) "1.0x" else "${speed}x",
                        selected = defaultPlaybackSpeed == speed,
                        onClick = { onDefaultPlaybackSpeedSelected(speed) },
                        blockLeft = index == 0,
                        blockRight = index == playbackSpeedOptions.lastIndex
                    )
                }
            }
        }

        CinematicSettingsSection(title = "Web Playback Profile") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaybackProfile.values().forEachIndexed { index, profile ->
                    CinematicOptionChip(
                        text = profile.label,
                        selected = playbackProfile == profile,
                        onClick = { onPlaybackProfileSelected(profile) },
                        blockLeft = index == 0,
                        blockRight = index == PlaybackProfile.values().lastIndex
                    )
                }
            }
        }

        CinematicSettingsSection(title = "\u81ea\u52a8\u64ad\u653e\u4e0b\u4e00 P") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CinematicOptionChip(
                    text = "\u5f00",
                    selected = autoPlayNextPageEnabled,
                    onClick = { onAutoPlayNextPageSelected(true) },
                    blockLeft = true
                )
                CinematicOptionChip(
                    text = "\u5173",
                    selected = !autoPlayNextPageEnabled,
                    onClick = { onAutoPlayNextPageSelected(false) },
                    blockRight = true
                )
            }
        }

        CinematicSettingsSection(title = "\u64ad\u653e\u5b8c\u6210\u540e\u884c\u4e3a") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                playbackEndBehaviorOptions.forEachIndexed { index, option ->
                    CinematicOptionChip(
                        text = option.label,
                        selected = playbackEndBehavior == option.behavior,
                        onClick = { onPlaybackEndBehaviorSelected(option.behavior) },
                        blockLeft = index == 0,
                        blockDown = true,
                        blockRight = index == playbackEndBehaviorOptions.lastIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun DanmakuSettingsPanel(
    danmakuSettings: DanmakuSettings,
    danmakuBlocklistEnabled: Boolean,
    onDanmakuSettingsChanged: (DanmakuSettings) -> Unit,
    onDanmakuBlocklistEnabledChanged: (Boolean) -> Unit,
    onOpenDanmakuBlocklist: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    var firstAssigned by remember { mutableStateOf(false) }
    val firstModifier = if (initialFocusRequester != null) {
        Modifier.focusRequester(initialFocusRequester)
    } else {
        Modifier
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        CinematicSettingsSection(title = "默认弹幕") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isFirst = !firstAssigned
                if (isFirst) firstAssigned = true
                CinematicOptionChip(
                    text = "开",
                    selected = danmakuSettings.enabledByDefault,
                    onClick = { onDanmakuSettingsChanged(danmakuSettings.copy(enabledByDefault = true)) },
                    modifier = if (isFirst) firstModifier else Modifier,
                    blockLeft = true,
                    blockUp = true
                )
                CinematicOptionChip(
                    text = "关",
                    selected = !danmakuSettings.enabledByDefault,
                    onClick = { onDanmakuSettingsChanged(danmakuSettings.copy(enabledByDefault = false)) },
                    blockUp = true,
                    blockRight = true
                )
            }
        }

        CinematicSettingsSection(title = "弹幕字号") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                danmakuFontOptions.forEachIndexed { index, option ->
                    CinematicOptionChip(
                        text = option.label,
                        selected = danmakuSettings.fontScale == option.value,
                        onClick = { onDanmakuSettingsChanged(danmakuSettings.copy(fontScale = option.value)) },
                        blockLeft = index == 0,
                        blockRight = index == danmakuFontOptions.lastIndex
                    )
                }
            }
        }

        CinematicSettingsSection(title = "弹幕透明度") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                danmakuAlphaOptions.forEachIndexed { index, option ->
                    CinematicOptionChip(
                        text = option.label,
                        selected = danmakuSettings.alpha == option.value,
                        onClick = { onDanmakuSettingsChanged(danmakuSettings.copy(alpha = option.value)) },
                        blockLeft = index == 0,
                        blockRight = index == danmakuAlphaOptions.lastIndex
                    )
                }
            }
        }

        CinematicSettingsSection(title = "弹幕速度") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                danmakuSpeedOptions.forEachIndexed { index, option ->
                    CinematicOptionChip(
                        text = option.label,
                        selected = danmakuSettings.speed == option.value,
                        onClick = { onDanmakuSettingsChanged(danmakuSettings.copy(speed = option.value)) },
                        blockLeft = index == 0,
                        blockRight = index == danmakuSpeedOptions.lastIndex
                    )
                }
            }
        }

        CinematicSettingsSection(title = "弹幕显示区域") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                danmakuDisplayAreaOptions.forEachIndexed { index, option ->
                    CinematicOptionChip(
                        text = option.label,
                        selected = danmakuSettings.displayAreaRatio == option.value,
                        onClick = { onDanmakuSettingsChanged(danmakuSettings.copy(displayAreaRatio = option.value)) },
                        blockLeft = index == 0,
                        blockRight = index == danmakuDisplayAreaOptions.lastIndex
                    )
                }
            }
        }

        CinematicSettingsSection(title = "弹幕屏蔽词") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CinematicOptionChip(
                    text = "启用",
                    selected = danmakuBlocklistEnabled,
                    onClick = { onDanmakuBlocklistEnabledChanged(true) },
                    blockLeft = true
                )
                CinematicOptionChip(
                    text = "关闭",
                    selected = !danmakuBlocklistEnabled,
                    onClick = { onDanmakuBlocklistEnabledChanged(false) },
                    blockRight = true
                )
            }
        }

        CinematicSettingsSection(title = "屏蔽词管理") {
            CinematicActionButton(
                text = "管理屏蔽词",
                onClick = onOpenDanmakuBlocklist,
                blockLeft = true,
                blockDown = true,
                blockRight = true
            )
        }
    }
}

@Composable
private fun InterfaceSettingsPanel(
    startupTab: String,
    onStartupTabSelected: (String) -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    var firstAssigned by remember { mutableStateOf(false) }
    val firstModifier = if (initialFocusRequester != null) {
        Modifier.focusRequester(initialFocusRequester)
    } else {
        Modifier
    }
    val rows = startupTabOptions.chunked(3)
    CinematicSettingsSection(title = "启动默认页面") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEachIndexed { rowIndex, rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowOptions.forEachIndexed { columnIndex, tab ->
                        val isFirst = !firstAssigned
                        if (isFirst) firstAssigned = true
                        CinematicOptionChip(
                            text = tab,
                            selected = startupTab == tab,
                            onClick = { onStartupTabSelected(tab) },
                            modifier = if (isFirst) firstModifier else Modifier,
                            blockLeft = columnIndex == 0,
                            blockRight = columnIndex == rowOptions.lastIndex,
                            blockUp = rowIndex == 0,
                            blockDown = rowIndex == rows.lastIndex
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(
    text: String,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    small: Boolean = false
) {
    Text(
        text = text,
        color = TvColors.TextPrimary,
        fontSize = if (small) 18.sp else 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = topPadding, bottom = 10.dp)
    )
}


private data class QualityOption(val qn: Int, val label: String)
private data class DanmakuOption(val label: String, val value: Float)
private data class PlaybackEndBehaviorOption(val label: String, val behavior: PlaybackEndBehavior)

private val qualityOptions = listOf(
    QualityOption(0, "\u81ea\u52a8"),
    QualityOption(16, "360P"),
    QualityOption(32, "480P"),
    QualityOption(64, "720P"),
    QualityOption(80, "1080P"),
    QualityOption(112, "1080P+"),
    QualityOption(116, "1080P60"),
    QualityOption(120, "4K"),
    QualityOption(125, "HDR"),
    QualityOption(126, "\u675c\u6bd4\u89c6\u754c"),
    QualityOption(127, "8K")
)

private val playbackSpeedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val startupTabOptions = HomeNavTabs.ALL
private val playbackEndBehaviorOptions = listOf(
    PlaybackEndBehaviorOption("\u505c\u5728\u7ed3\u5c3e", PlaybackEndBehavior.STAY_AT_END),
    PlaybackEndBehaviorOption("\u8fd4\u56de\u8be6\u60c5\u9875", PlaybackEndBehavior.BACK_TO_DETAIL),
    PlaybackEndBehaviorOption("\u8fd4\u56de\u5217\u8868\u9875", PlaybackEndBehavior.BACK_TO_LIST)
)
private val danmakuFontOptions = listOf(
    DanmakuOption("\u5c0f", 0.85f),
    DanmakuOption("\u4e2d", 1.0f),
    DanmakuOption("\u5927", 1.2f)
)
private val danmakuAlphaOptions = listOf(
    DanmakuOption("\u4f4e", 0.45f),
    DanmakuOption("\u4e2d", 0.7f),
    DanmakuOption("\u6807\u51c6", 0.8f),
    DanmakuOption("\u9ad8", 0.9f)
)
private val danmakuSpeedOptions = listOf(
    DanmakuOption("\u6162", 0.75f),
    DanmakuOption("\u4e2d", 1.0f),
    DanmakuOption("\u5feb", 1.35f)
)
private val danmakuDisplayAreaOptions = listOf(
    DanmakuOption("1/4 \u5c4f", 0.25f),
    DanmakuOption("1/2 \u5c4f", 0.5f),
    DanmakuOption("3/4 \u5c4f", 0.75f),
    DanmakuOption("\u5168\u5c4f", 1.0f)
)
