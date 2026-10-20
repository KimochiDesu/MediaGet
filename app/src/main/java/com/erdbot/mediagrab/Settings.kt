package com.erdbot.mediagrab

import android.content.Context
import com.erdbot.mediagrab.ui.theme.ThemeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User preferences, persisted in SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        runCatching { ThemeId.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeId.NIGHT_SHIFT),
    )
    val theme: StateFlow<ThemeId> = _theme

    private val _suggestClipboard = MutableStateFlow(prefs.getBoolean(KEY_CLIPBOARD, true))
    val suggestClipboard: StateFlow<Boolean> = _suggestClipboard

    private val _checkUpdates = MutableStateFlow(prefs.getBoolean(KEY_CHECK_UPDATES, true))
    val checkUpdates: StateFlow<Boolean> = _checkUpdates

    fun setTheme(id: ThemeId) {
        _theme.value = id
        prefs.edit().putString(KEY_THEME, id.name).apply()
    }

    fun setSuggestClipboard(on: Boolean) {
        _suggestClipboard.value = on
        prefs.edit().putBoolean(KEY_CLIPBOARD, on).apply()
    }

    fun setCheckUpdates(on: Boolean) {
        _checkUpdates.value = on
        prefs.edit().putBoolean(KEY_CHECK_UPDATES, on).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_CLIPBOARD = "suggest_clipboard"
        const val KEY_CHECK_UPDATES = "check_updates"
    }
}
