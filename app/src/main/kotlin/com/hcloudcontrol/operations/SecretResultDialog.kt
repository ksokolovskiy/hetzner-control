package com.hcloudcontrol.operations

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hcloudcontrol.R
import com.hcloudcontrol.ui.designsystem.HCloudDialog
import java.util.UUID

/** Pass null after process recreation. The missing one-time value never triggers another mutation. */
@Composable
fun SecretResultDialog(result: SecretResult?, onDone: () -> Unit) {
    var revealed by remember(result) { mutableStateOf(false) }
    var acknowledged by remember(result) { mutableStateOf(false) }
    var copyFailed by remember(result) { mutableStateOf(false) }
    val finish = { result?.close(); onDone() }
    HCloudDialog(
        title = stringResource(R.string.server_recovery_password_title),
        confirmLabel = stringResource(R.string.server_rebuild_done),
        confirmEnabled = result == null || !result.available || acknowledged,
        dismissLabel = stringResource(R.string.common_close),
        securePolicy = SecureFlagPolicy.SecureOn,
        onConfirm = finish,
        onDismiss = finish,
    ) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val context = LocalContext.current
        val clipboard = remember(context) { OwnedSecretClipboard(context) }
        DisposableEffect(result, lifecycle, clipboard) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    result?.hide()
                    revealed = false
                }
                if (event == Lifecycle.Event.ON_START) clipboard.clearExpired()
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                clipboard.close()
                result?.close()
            }
        }
        if (result == null || !result.available) {
            Text(stringResource(R.string.operation_secret_lost))
        } else {
            Text(stringResource(R.string.server_recovery_password_warning))
            val displayed = if (revealed) {
                val chars = result.copyForExplicitUserAction()
                try { String(chars) } finally { chars.fill('\u0000') }
            } else "••••••••••••"
            Text(displayed)
            TextButton(onClick = {
                revealed = !revealed
                if (revealed) result.reveal() else result.hide()
            }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (revealed) R.string.common_hide else R.string.common_show))
            }
            TextButton(onClick = {
                val chars = result.copyForExplicitUserAction()
                try { copyFailed = !clipboard.copy(chars) } finally { chars.fill('\u0000') }
            }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.server_recovery_copy_password))
            }
            if (copyFailed) Text(stringResource(R.string.operation_copy_failed))
            Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = acknowledged, onCheckedChange = {
                    acknowledged = it
                    if (it) result.acknowledge()
                })
                Text(stringResource(R.string.server_recovery_password_acknowledgement))
            }
        }
    }
}

/** Clears only clips copied by this instance, never unrelated user clipboard content. */
private class OwnedSecretClipboard(context: Context) : AutoCloseable {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private val owner = "hcloud-secret-${UUID.randomUUID()}"
    private var expiresAt = Long.MAX_VALUE
    private val cleanup = Runnable { clearExpired() }
    fun copy(chars: CharArray): Boolean = runCatching {
        val clip = ClipData.newPlainText(owner, String(chars))
        clip.description.extras = PersistableBundle().apply {
            // Literal is compatible with API 26; Android 13+ recognizes the sensitive marker.
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)
        expiresAt = SystemClock.uptimeMillis() + 60_000
        handler.removeCallbacks(cleanup)
        handler.postDelayed(cleanup, 60_000)
        true
    }.getOrDefault(false)
    fun clearExpired() { if (SystemClock.uptimeMillis() >= expiresAt) clearOwned() }
    fun clearOwned() {
        // Background access can be denied; expiry is checked again on the next foreground.
        runCatching {
            if (clipboard.primaryClipDescription?.label?.toString() == owner) {
                if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
    override fun close() { handler.removeCallbacks(cleanup); clearOwned() }
}
