package com.aryan.reader.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.BuildConfig
import com.aryan.reader.R

@Composable
fun UpdateDialog(
    state: UpdateState,
    onDismiss: () -> Unit,
    onUpdate: (ReleaseInfo) -> Unit,
    onOpenPermission: () -> Unit,
) {
    when (state) {
        UpdateState.Idle -> Unit
        UpdateState.Checking -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(stringResource(R.string.update_checking)) },
            confirmButton = {},
        )

        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(stringResource(R.string.update_up_to_date)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
        )

        UpdateState.NotPersonal -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(stringResource(R.string.update_not_personal)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
        )

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(
                            R.string.update_available_message,
                            state.release.tag,
                            BuildConfig.VERSION_NAME,
                        ),
                    )
                    if (state.release.notes.isNotBlank()) {
                        Text(
                            stringResource(R.string.update_whats_new),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            state.release.notes,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 12,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onUpdate(state.release) }) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.update_downloading))
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
            },
        )

        is UpdateState.NeedPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission_message)) },
            confirmButton = { TextButton(onClick = onOpenPermission) { Text(stringResource(R.string.update_now)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
        )

        is UpdateState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(stringResource(R.string.update_failed, state.message)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
        )
    }
}
