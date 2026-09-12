package io.github.vvb2060.ims.ui.components

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.vvb2060.ims.AutoRestoreNotifier
import io.github.vvb2060.ims.R

/** 通知权限只影响结果兜底提示，不作为自动应用配置的前置授权。 */
@Composable
fun AutoRestoreCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(AutoRestoreNotifier.notificationsEnabled(context)) }
    var enableAfterPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = AutoRestoreNotifier.notificationsEnabled(context)
        if (enableAfterPermission) {
            enableAfterPermission = false
            // 等通知弹窗结束后再启用，避免与 Shizuku 授权弹窗同时出现；拒绝通知仍可应用。
            onEnabledChange(true)
        }
    }
    LifecycleResumeEffect(Unit) {
        notificationsEnabled = AutoRestoreNotifier.notificationsEnabled(context)
        onPauseOrDispose { }
    }

    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = { value ->
                    enableAfterPermission = false
                    if (value && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED) {
                        AutoRestoreNotifier.prepareChannel(context)
                        enableAfterPermission = true
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onEnabledChange(value)
                    }
                },
            ).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.auto_restore_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.auto_restore_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
        if (enabled && !notificationsEnabled) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.auto_restore_notification_unavailable), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    AutoRestoreNotifier.prepareChannel(context)
                    val appNotificationsEnabled = context.getSystemService(NotificationManager::class.java)
                        .areNotificationsEnabled()
                    val action = if (appNotificationsEnabled) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                        else Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    context.startActivity(Intent(action).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        if (appNotificationsEnabled) putExtra(Settings.EXTRA_CHANNEL_ID, AutoRestoreNotifier.CHANNEL_ID)
                    })
                }) {
                    Text(stringResource(R.string.auto_restore_notification_settings))
                }
            }
        }
    }
}
