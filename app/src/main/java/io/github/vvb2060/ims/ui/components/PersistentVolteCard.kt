package io.github.vvb2060.ims.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.model.PersistentVolteState

/** 独立即时操作区域，不与“本次应用”的配置草稿混用。 */
@Composable
fun PersistentVolteCard(
    state: PersistentVolteState?,
    singleSimSelected: Boolean,
    shizukuReady: Boolean,
    busy: Boolean,
    onEnable: () -> Unit,
    onRestore: () -> Unit,
    onRefresh: () -> Unit,
) {
    val canOperate = singleSimSelected && shizukuReady && !busy
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.persistent_volte_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.persistent_volte_description), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.persistent_volte_usage), style = MaterialTheme.typography.bodySmall)
            when {
                !singleSimSelected -> Text(stringResource(R.string.persistent_volte_select_sim))
                !shizukuReady -> Text(stringResource(R.string.persistent_volte_shizuku_required))
                else -> {
                    StateRow(stringResource(R.string.persistent_volte_opt_in), state?.optIn)
                    StateRow(stringResource(R.string.persistent_volte_user_setting), state?.userEnabled)
                    StateRow(stringResource(R.string.persistent_volte_registration), state?.imsRegistered)
                    if (state?.unsupported == true) {
                        Text(stringResource(R.string.persistent_volte_unsupported), color = MaterialTheme.colorScheme.error)
                    }
                    state?.error?.let {
                        Text(stringResource(R.string.persistent_volte_error, it),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onEnable,
                    enabled = canOperate && state != null && !state.unsupported &&
                        state.optIn != null && state.userEnabled != null,
                ) { Text(stringResource(R.string.persistent_volte_enable)) }
                TextButton(onClick = onRestore, enabled = canOperate && state?.canRestore == true) {
                    Text(stringResource(R.string.persistent_volte_restore))
                }
            }
            TextButton(onClick = onRefresh, enabled = canOperate) { Text(stringResource(R.string.refresh)) }
            Text(stringResource(R.string.persistent_volte_restore_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.persistent_volte_limits),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Text(stringResource(R.string.persistent_volte_update_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun StateRow(label: String, value: Boolean?) {
    val state = stringResource(when (value) {
        true -> R.string.persistent_volte_on
        false -> R.string.persistent_volte_off
        null -> R.string.persistent_volte_unknown
    })
    Text("$label: $state", style = MaterialTheme.typography.bodyMedium)
}
