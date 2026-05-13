package com.adam.xai_client.ui.theme

enum class AppThemeMode(val storageValue: String) {
    Light("light"),
    Dark("dark");

    val isDark: Boolean
        get() = this == Dark

    fun toggle(): AppThemeMode = if (isDark) Light else Dark

    companion object {
        val Default: AppThemeMode = Dark

        fun fromStorageValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: Default
    }
}
