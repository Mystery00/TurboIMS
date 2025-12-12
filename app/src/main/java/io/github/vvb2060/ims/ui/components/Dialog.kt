package io.github.vvb2060.ims.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun PreviewSingleChoiceDialog() {
    SingleChoiceDialog(
        openDialog = true,
        title = "请选择Sim卡",
        list = listOf("卡1", "卡2", "卡3"),
        initialValue = "卡1",
        onDismiss = {},
        onConfirm = {},
    )
}

@Composable
fun <T> SingleChoiceDialog(
    openDialog: Boolean,
    title: String,
    list: List<T>,
    initialValue: T? = null,
    converter: (T) -> String = { it.toString() },
    confirmText: String = stringResource(android.R.string.ok),
    dismissText: String = stringResource(android.R.string.cancel),
    onDismiss: () -> Unit,
    onConfirm: (T) -> Unit,
) {
    if (!openDialog) return
    var selected by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(initialValue) {
        selected = initialValue
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                list.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selected == item),
                                onClick = { selected = item }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selected == item),
                            onClick = { selected = item })
                        Text(converter(item))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                selected?.let {
                    onConfirm(it)
                }
                onDismiss()
            }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
            }) {
                Text(dismissText)
            }
        }
    )
}