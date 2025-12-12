package io.github.vvb2060.ims

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import io.github.vvb2060.ims.model.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object LogcatRepository {
    private const val TAG = "LogcatRepository"

    private val _logs = mutableStateListOf<LogEntry>()
    val logs: List<LogEntry> = _logs

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var logProcess: Process? = null
    private var isCapturing = false

    fun isCapturing(): Boolean = isCapturing

    fun startLogcat() {
        if (isCapturing) return
        isCapturing = true

        repositoryScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "start read logcat")
                withContext(Dispatchers.Main) { _logs.clear() }
                val processBuilder = ProcessBuilder(listOf("logcat", "-v", "threadtime"))
                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()
                logProcess = process

                val bufferedReader = process.inputStream.bufferedReader()

                var line: String? = null

                while (isActive && bufferedReader.readLine().also { line = it } != null) {
                    if (!isCapturing) break
                    line?.let { rawLog ->
                        val entry = LogEntry.parseLog(rawLog)
                        withContext(Dispatchers.Main) {
                            _logs.add(entry)
                            if (_logs.size > 2000) {
                                _logs.removeFirst()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "read logcat error", e)
            } finally {
                logProcess?.destroy()
                logProcess = null
                isCapturing = false
            }
        }
    }

    fun clearLogs() {
        _logs.clear()
    }

    fun stopAndClear() {
        Log.d(TAG, "killing logcat process")
        logProcess?.destroy()
        logProcess = null
        isCapturing = false
    }
}