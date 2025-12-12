package io.github.vvb2060.ims.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.vvb2060.ims.LogEntry
import io.github.vvb2060.ims.LogLevel
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.ui.components.LogList
import io.github.vvb2060.ims.ui.components.LogcatToolbar
import io.github.vvb2060.ims.ui.components.SingleChoiceDialog
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
                            LogcatToolbar(
                                expanded = expanded,
                                onBack = { finish() },
                                onClearAll = { logs.clear() },
                                onExport = { exportLogFile(scope) },
                                onFilterClick = { filterMenuExpanded = true },
                                onScrollDown = {
                                    if (logs.isNotEmpty()) {
                                        scope.launch {
                                            listState.animateScrollToItem(logs.lastIndex)
                                        }
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        LogList(
                            listState = listState,
                            innerPadding = innerPadding,
                            logs = logs.filter {
                                it.level.isLevelEnabled(filter)
                            },
                        )
                    }
                }

                SingleChoiceDialog(
                    openDialog = filterMenuExpanded,
                    title = stringResource(R.string.title_filter_log_level),
                    list = LogLevel.entries.toList(),
                    initialValue = filter,
                    converter = { it.tag },
                    onDismiss = { filterMenuExpanded = false },
                    onConfirm = {
                        filter = it
                    }
                )

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

    fun exportLogFile(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            File(externalCacheDir, "turbo_ims.log").apply {
                writeText("TurboIms Logcat:\n")
                Runtime.getRuntime()
                    .exec(arrayOf("logcat", "-d")).inputStream.use { input ->
                        FileOutputStream(this, true).use {
                            input.copyTo(it)
                        }
                    }

                val authority = "${packageName}.logcat_fileprovider"
                val uri = FileProvider.getUriForFile(this@LogcatActivity, authority, this)
                startActivity(
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
