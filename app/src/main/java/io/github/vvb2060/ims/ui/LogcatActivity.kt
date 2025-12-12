package io.github.vvb2060.ims.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.vvb2060.ims.LogEntry
import io.github.vvb2060.ims.LogLevel
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.ui.theme.TurbolImsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class LogcatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TurbolImsTheme {
                val logs = remember { mutableStateListOf<LogEntry>() }
                var filter by remember { mutableStateOf(LogLevel.DEBUG) }
                var expanded by rememberSaveable { mutableStateOf(true) }
                var filterMenuExpanded by remember { mutableStateOf(false) }

                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .floatingToolbarVerticalNestedScroll(
                            expanded = expanded,
                            onExpand = { expanded = true },
                            onCollapse = { expanded = false },
                        )
                ) {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0.dp),
                        floatingActionButtonPosition = FabPosition.Center,
                        floatingActionButton = {
                            HorizontalFloatingToolbar(
                                expanded = expanded,
                                trailingContent = {},
                                leadingContent = {
                                    IconButton(
                                        onClick = { finish() }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                    IconButton(
                                        onClick = { logs.clear() }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear all"
                                        )
                                    }
                                    IconButton(
                                        onClick = { exportLogFile(context, scope) }) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Export"
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { filterMenuExpanded = true }) {
                                            Icon(
                                                imageVector = Icons.Default.FilterList,
                                                contentDescription = "Filter",
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = filterMenuExpanded,
                                            onDismissRequest = { filterMenuExpanded = false }
                                        ) {
                                            LogLevel.entries.forEach { level ->
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .background(
                                                                    level.bgColor,
                                                                    shape = MaterialTheme.shapes.small
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = level.tag.first().toString(),
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                        }
                                                    },
                                                    text = { Text(level.tag) },
                                                    onClick = {
                                                        filter = level
                                                        filterMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            ) {
                                FilledIconButton(
                                    onClick = {
                                        if (logs.isNotEmpty()) {
                                            scope.launch {
                                                listState.animateScrollToItem(logs.lastIndex)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Scroll Latest"
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
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
                                if (log.level.isLevelEnabled(filter))
                                    Card(
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    ) {
                                        LogItem(
                                            level = log.level,
                                            timeText = log.time,
                                            contentText = log.content,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                            }
                            item {
                                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                            }
                        }
                    }
                }

                DisposableEffect(Unit) {
                    val process = runLogcat(scope) { logs.add(it) }
                    onDispose {
                        logs.add(LogEntry(LogLevel.INFO, "", "stop read logcat"))
                        process.destroy()
                    }
                }
            }
        }
    }

    @Composable
    fun LogItem(
        modifier: Modifier = Modifier,
        level: LogLevel,
        timeText: String,
        contentText: String,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            level.bgColor,
                            shape = MaterialTheme.shapes.small
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = level.tag.first().toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.offset(y = (-1.5).dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = timeText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Text(
                text = contentText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    fun runLogcat(
        scope: CoroutineScope,
        pushLogs: (LogEntry) -> Unit,
    ): Process {
        val process = ProcessBuilder(listOf("logcat", "-v", "threadtime")).start()

        scope.launch(Dispatchers.Default) {
            Log.i("logcat process", "start read logcat")
            process.inputStream.bufferedReader().use {
                while (true) {
                    try {
                        it.readLine()?.let { line ->
                            pushLogs(LogEntry.parseLog(line))
                        } ?: break
                    } catch (e: Exception) {
                        Log.w("read log failed", "$e")
                        break
                    }
                }
            }

            Log.i("logcat process", "stop read logcat")
            process.destroy()
        }

        return process
    }

    fun exportLogFile(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            File(context.externalCacheDir, "yuhaiin.log").apply {
                writeText("Yuhaiin Logcat:\n")
                Runtime.getRuntime()
                    .exec(arrayOf("logcat", "-d")).inputStream.use { input ->
                        FileOutputStream(this, true).use {
                            input.copyTo(it)
                        }
                    }

                val authority = "${context.packageName}.logcat_fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, this)
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .putExtra(Intent.EXTRA_STREAM, uri),
                        "Export Logcat"
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}