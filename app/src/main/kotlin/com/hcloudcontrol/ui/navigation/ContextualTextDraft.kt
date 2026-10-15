package com.hcloudcontrol.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/** Public, non-secret form values only. Scope is verified on process restore, not merely an input. */
fun restoreContextualText(scope: String, saved: List<String>, fallback: String = ""): String =
    if (saved.size == 3 && saved[0] == "1" && saved[1] == scope) saved[2] else fallback

@Composable
fun rememberContextualText(scope: String, initial: String = ""): MutableState<String> = rememberSaveable(scope,
    stateSaver = Saver<String, ArrayList<String>>(
        save = { arrayListOf("1", scope, it) }, restore = { restoreContextualText(scope, it, initial) },
    )) { mutableStateOf(initial) }
