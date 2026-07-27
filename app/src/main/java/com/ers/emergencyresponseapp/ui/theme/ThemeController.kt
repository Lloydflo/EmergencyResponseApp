// ui/theme/ThemeController.kt
package com.ers.emergencyresponseapp.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf

object ThemeController {
    private const val PREFS = "ers_prefs"
    private const val KEY = "dark_mode"

    var isDarkMode = mutableStateOf(false)
        private set

    fun init(context: Context) {
        isDarkMode.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        isDarkMode.value = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, enabled).apply()
    }
}