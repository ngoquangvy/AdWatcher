package com.adwatcher.app

import android.content.Context

object ProtectionSettings {
    private const val PREFS_NAME = "adwatcher_protection"
    private const val KEY_STRONG_PROTECTION = "strong_protection_enabled"

    fun isStrongProtectionEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_STRONG_PROTECTION, true)
    }

    fun setStrongProtectionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STRONG_PROTECTION, enabled)
            .apply()
    }
}
