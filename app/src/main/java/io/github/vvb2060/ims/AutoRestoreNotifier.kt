package io.github.vvb2060.ims

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import io.github.vvb2060.ims.ui.MainActivity

/** 自动应用结果优先使用系统文本 Toast；未收到显示回调时尝试通知兜底。 */
class AutoRestoreNotifier(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var toast: Toast? = null
    private var fallback: Runnable? = null
    private var generation = 0

    fun showResult(successCount: Int, failureCount: Int) {
        val message = when {
            failureCount == 0 -> context.getString(R.string.auto_restore_result_success, successCount)
            successCount == 0 -> context.getString(R.string.auto_restore_result_failed, failureCount)
            else -> context.getString(R.string.auto_restore_result_partial, successCount, failureCount)
        }
        fallback?.let(handler::removeCallbacks)
        val current = ++generation
        cancelToast()
        val timeout = Runnable {
            if (current != generation) return@Runnable
            // Toast 没有“显示失败”回调；超时只表示未确认显示，先撤掉排队 Toast 再发通知。
            cancelToast()
            showNotification(message)
        }
        fallback = timeout
        try {
            val resultToast = Toast.makeText(context, message, Toast.LENGTH_LONG)
            toast = resultToast
            resultToast.addCallback(object : Toast.Callback() {
                override fun onToastShown() {
                    if (current != generation) return
                    handler.removeCallbacks(timeout)
                    // 显示回调可能迟于兜底计时器；移除同一结果的通知，减少重复提示。
                    try {
                        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Unable to clear automatic apply notification", e)
                    }
                }
            })
            handler.postDelayed(timeout, TOAST_CONFIRM_TIMEOUT_MS)
            resultToast.show()
        } catch (e: RuntimeException) {
            handler.removeCallbacks(timeout)
            Log.w(TAG, "Unable to show automatic apply toast", e)
            showNotification(message)
        }
    }

    private fun showNotification(message: String) {
        try {
            prepareChannel(context)
            if (!notificationsEnabled(context)) {
                Log.w(TAG, "Automatic apply notification unavailable: $message")
                return
            }
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(context.getString(R.string.auto_restore_notification_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to post automatic apply notification", e)
        }
    }

    fun close() {
        ++generation
        fallback?.let(handler::removeCallbacks)
        cancelToast()
    }

    private fun cancelToast() {
        try {
            toast?.cancel()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to cancel automatic apply toast", e)
        }
    }

    companion object {
        private const val TAG = "AutoRestoreNotifier"
        const val CHANNEL_ID = "auto_apply_results"
        private const val NOTIFICATION_ID = 0x4152
        private const val TOAST_CONFIRM_TIMEOUT_MS = 5_000L

        fun prepareChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.auto_restore_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }

        fun notificationsEnabled(context: Context): Boolean {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() &&
                manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        }
    }
}
