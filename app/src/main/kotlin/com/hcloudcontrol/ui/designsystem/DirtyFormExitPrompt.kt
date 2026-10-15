package com.hcloudcontrol.ui.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.R
import com.hcloudcontrol.ui.navigation.DirtyFormExitGuard

/** Every dirty form uses this shared retain/discard/stay decision. No draft value enters the prompt. */
@Composable
fun DirtyFormExitPrompt(guard: DirtyFormExitGuard) {
    if (!guard.awaitingChoice) return
    HCloudDialog(title = stringResource(R.string.ds_leave_form), confirmLabel = stringResource(R.string.ds_keep_draft),
        onConfirm = guard::retainAndLeave, onDismiss = guard::stay) {
        Text(stringResource(R.string.ds_leave_form_description))
        OutlinedButton(onClick = guard::discardAndLeave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ds_discard_draft))
        }
    }
}
