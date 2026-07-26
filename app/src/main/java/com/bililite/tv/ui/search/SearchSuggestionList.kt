package com.bililite.tv.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.model.SearchSuggestion
import com.bililite.tv.theme.TvColors
import com.bililite.tv.ui.components.CinematicGlassSurface
import com.bililite.tv.ui.components.GlassVariant
import com.bililite.tv.ui.state.UiState

@Composable
fun SearchSuggestionList(
    state: UiState<List<SearchSuggestion>>,
    onSelected: (SearchSuggestion) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        UiState.Loading -> SearchCenterText("\u52a0\u8f7d\u4e2d...", modifier)
        is UiState.Error -> SearchCenterText(state.message, modifier.clickable { onRetry() })
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                SearchCenterText("\u6682\u65e0\u5efa\u8bae", modifier)
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.data, key = { it.keyword }) { suggestion ->
                        SuggestionItem(
                            suggestion = suggestion,
                            onClick = { onSelected(suggestion) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: SearchSuggestion,
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
        selected = false,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        visualOverrides = SearchControlGlassOverrides
    ) {
        Text(
            text = suggestion.keyword,
            color = if (focused) TvColors.TextPrimary else TvColors.TextSecondary,
            fontSize = 20.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SearchCenterText(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = TvColors.TextSecondary, fontSize = 22.sp)
    }
}
