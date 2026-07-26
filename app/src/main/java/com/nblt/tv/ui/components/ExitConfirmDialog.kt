package com.nblt.tv.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nblt.tv.theme.NbltPrimary
import com.nblt.tv.theme.TvColors
import com.nblt.tv.theme.TvDimensions

private const val TAG_EXIT = "BiliExit"

@Composable
fun ExitConfirmDialog(
    onDismiss: () -> Unit,
    onConfirmExit: () -> Unit
) {
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        cancelFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = {
            Log.i(TAG_EXIT, "exit dialog cancel")
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 420.dp, max = 520.dp)
                .background(Color(0xFF1E2430), RoundedCornerShape(14.dp))
                .border(1.dp, TvColors.CardBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\u9000\u51fa NBLT\uff1f",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Text(
                text = "\u786e\u5b9a\u8981\u9000\u51fa\u5e94\u7528\u5417\uff1f",
                color = TvColors.TextSecondary,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 22.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ExitDialogButton(
                    label = "\u53d6\u6d88",
                    primary = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(cancelFocusRequester),
                    onClick = {
                        Log.i(TAG_EXIT, "exit dialog cancel")
                        onDismiss()
                    }
                )
                ExitDialogButton(
                    label = "\u9000\u51fa",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        Log.i(TAG_EXIT, "exit app confirmed")
                        onConfirmExit()
                    }
                )
            }
        }
    }
}

@Composable
private fun ExitDialogButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                color = if (primary) NbltPrimary else Color(0xFF2A323C),
                shape = shape
            )
            .border(
                width = if (focused) TvDimensions.focusBorderWidth else 1.dp,
                color = if (focused) Color.White else TvColors.CardBorder,
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
