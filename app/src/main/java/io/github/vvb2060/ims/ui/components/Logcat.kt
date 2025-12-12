package io.github.vvb2060.ims.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vvb2060.ims.model.LogEntry
import io.github.vvb2060.ims.model.LogLevel

@Composable
fun LogList(
    listState: LazyListState,
    innerPadding: PaddingValues,
    logs: List<LogEntry>,
) {
    LazyColumn(
        modifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        }
        items(logs) { log ->
            Card(
                modifier = Modifier.padding(horizontal = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.small,
            ) {
                LogItem(
                    level = log.level,
                    timeText = log.time,
                    tagText = log.tag ?: "",
                    contentText = log.content,
                    modifier = Modifier.fillMaxWidth(),
                    textColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        item {
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
fun LogItem(
    modifier: Modifier = Modifier,
    level: LogLevel,
    timeText: String,
    tagText: String,
    contentText: String,
    textColor: Color,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        level.bgColor,
                        shape = MaterialTheme.shapes.extraSmall
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = level.tag.first().toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.offset(y = (-2.5).dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = timeText,
                fontSize = 12.sp,
                color = textColor,
            )
            if (tagText.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = tagText,
                    fontSize = 12.sp,
                    color = textColor,
                )
            }
        }

        Text(
            text = contentText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun LogcatToolbar(
    expanded: Boolean,
    onBack: () -> Unit = {},
    onClearAll: () -> Unit = {},
    onExport: () -> Unit = {},
    onFilterClick: () -> Unit = {},
    onScrollDown: () -> Unit = {},
) {
    HorizontalFloatingToolbar(
        expanded = expanded,
        leadingContent = {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back"
                )
            }
            IconButton(
                onClick = onClearAll
            ) {
                Icon(
                    imageVector = Icons.Rounded.Clear,
                    contentDescription = "Clear all"
                )
            }
            IconButton(
                onClick = onExport
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Export"
                )
            }
            IconButton(onClick = onFilterClick) {
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = "Filter",
                )
            }
        }
    ) {
        FilledIconButton(
            onClick = onScrollDown
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Scroll Latest"
            )
        }
    }
}