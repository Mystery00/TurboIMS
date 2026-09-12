package io.github.vvb2060.ims.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import io.github.vvb2060.ims.BuildConfig
import io.github.vvb2060.ims.LogcatRepository
import io.github.vvb2060.ims.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LogcatViewModel(application: Application) : AndroidViewModel(application) {
    val logs = LogcatRepository.logs
    private var isExporting = false

    init {
        LogcatRepository.startLogcat()
    }

    fun clearLogs() {
        LogcatRepository.clearLogs()
    }

    fun exportLogFile() {
        if (isExporting) return
        isExporting = true
        viewModelScope.launch {
            try {
                val snapshot = LogcatRepository.snapshot()
                val file = withContext(Dispatchers.IO) {
                    // 每次分享对应独立文件，后续导出不能覆盖已经交给其他应用的内容。
                    val directory = application.externalCacheDir ?: application.cacheDir
                    File.createTempFile("tensor_ims_", ".log", directory).apply {
                        bufferedWriter().use { writer ->
                            writer.appendLine("App Version: ${BuildConfig.VERSION_NAME}")
                            writer.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                            writer.appendLine("Android Version: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                            writer.appendLine("System Build Version: ${Build.DISPLAY}")
                            writer.appendLine("Security Patch Version: ${Build.VERSION.SECURITY_PATCH}")
                            writer.appendLine("-----------------------------------------------------------------")
                            writer.appendLine("TensorIMS Logcat:")
                            snapshot.forEach { writer.appendLine(it.raw) }
                        }
                    }
                }
                val authority = "${application.packageName}.logcat_fileprovider"
                val uri = FileProvider.getUriForFile(application, authority, file)
                application.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .putExtra(Intent.EXTRA_STREAM, uri),
                        application.getString(R.string.export_logcat)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("LogcatViewModel", "Failed to export logcat", error)
                Toast.makeText(application, R.string.export_logcat_failed, Toast.LENGTH_LONG).show()
            } finally {
                isExporting = false
            }
        }
    }
}
