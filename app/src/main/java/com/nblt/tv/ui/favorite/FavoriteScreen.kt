package com.nblt.tv.ui.favorite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.model.FavoriteFolder
import com.nblt.tv.model.UserInfo
import com.nblt.tv.theme.NbltBackground
import com.nblt.tv.theme.TvColors
import com.nblt.tv.theme.TvDimensions
import com.nblt.tv.ui.components.TvEmptyContent
import com.nblt.tv.ui.components.TvErrorContent
import com.nblt.tv.ui.components.TvLoadingContent
import com.nblt.tv.ui.components.TvNotLoggedInContent
import com.nblt.tv.ui.components.TvPageHeader
import com.nblt.tv.ui.state.UiState

@Composable
fun FavoriteScreen(
    currentUser: UserInfo?,
    foldersState: UiState<List<FavoriteFolder>>?,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onFolderClick: (FavoriteFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentUser == null) {
        TvNotLoggedInContent(
            hint = "\u767b\u5f55\u540e\u53ef\u67e5\u770b\u6211\u7684\u6536\u85cf",
            onLoginClick = onLoginClick
        )
        return
    }

    when (val state = foldersState ?: UiState.Loading) {
        UiState.Loading -> TvLoadingContent(message = "\u6b63\u5728\u52a0\u8f7d\u6536\u85cf\u5939...")
        is UiState.Error -> TvErrorContent(
            title = "\u6536\u85cf\u5939\u52a0\u8f7d\u5931\u8d25",
            message = state.message,
            onRetry = onRetry
        )
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                TvEmptyContent(message = "\u6682\u65e0\u6536\u85cf\u5939")
            } else {
                LazyColumn(modifier = modifier.fillMaxSize()) {
                    item {
                        TvPageHeader(title = "我的收藏", subtitle = "按确认键打开收藏夹")
                    }
                    items(state.data, key = { it.id }) { folder ->
                        FavoriteFolderRow(
                            folder = folder,
                            onClick = { onFolderClick(folder) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteFolderRow(
    folder: FavoriteFolder,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(TvDimensions.cardRadius)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(cardShape)
            .background(if (focused) TvColors.SurfaceElevated else TvColors.Surface)
            .border(
                width = if (focused) TvDimensions.focusBorderWidth else 1.dp,
                color = if (focused) TvColors.FocusAccent else TvColors.CardBorder,
                shape = cardShape
            )
            .onFocusChanged {
                if (focused != it.isFocused) {
                    focused = it.isFocused
                }
            }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = folder.title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${folder.mediaCount} \u4e2a\u89c6\u9891",
            color = Color(0xFFD6DAE1),
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
