package com.hcloudcontrol.ui.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.hcloudcontrol.R

/** Display-only context; credential/token values must never be passed to the scaffold. */
data class ProjectContext(val projectName: String, val environmentLabel: String? = null)

/**
 * One application shell. Navigation owner supplies root/nested state and the same back action
 * for app-bar and system Back. A nested form/detail never displays root navigation.
 */
@Composable
fun HCloudAppScaffold(
    title: String,
    project: ProjectContext?,
    isRoot: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onProjectClick: (() -> Unit)? = null,
    overflow: @Composable RowScope.() -> Unit = {},
    rootNavigation: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    topBarContent: (@Composable () -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val appBarBack: () -> Unit = {
        if (imeVisible) keyboard?.hide() else onBack?.invoke()
    }
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        topBar = {
            if (topBarContent != null) topBarContent() else {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = HCloudDesignTokens.contentInset),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (!isRoot && onBack != null) {
                            TextButton(onClick = appBarBack, modifier = Modifier.heightIn(min = HCloudDesignTokens.minimumTarget)) {
                                Text(stringResource(R.string.ds_back))
                            }
                        }
                        Text(title, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).padding(vertical = HCloudDesignTokens.contentInset).semantics { heading() })
                        overflow()
                    }
                    project?.let {
                        HCloudProjectContext(it, onProjectClick,
                            Modifier.fillMaxWidth().padding(horizontal = HCloudDesignTokens.contentInset))
                    }
                }
            }
            }
        },
        bottomBar = { if (isRoot) rootNavigation() },
        content = content,
    )
}

@Composable
fun HCloudProjectContext(context: ProjectContext, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val label = listOfNotNull(context.projectName, context.environmentLabel).joinToString(" · ")
    if (onClick == null) {
        Text(label, modifier.padding(vertical = HCloudDesignTokens.spaceMedium),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface,
            modifier = modifier.heightIn(min = HCloudDesignTokens.minimumTarget).semantics { role = Role.Button }) {
            Box(Modifier.padding(vertical = HCloudDesignTokens.spaceMedium), contentAlignment = Alignment.CenterStart) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
