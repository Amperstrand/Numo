package com.electricdreams.numo.core.bridge

import android.content.Context

/**
 * Provider-mode preferences for the bridge terminal. Default base URL is the
 * rig's phone-facing address; on the emulator use http://10.0.2.2:8787.
 */
class BridgePrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("bridge_prefs", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    companion object {
        private const val KEY_URL = "base_url"
        private const val KEY_ENABLED = "enabled"
        const val DEFAULT_URL = "http://10.99.97.1:8787"

        @Volatile private var instance: BridgePrefs? = null
        fun getInstance(context: Context): BridgePrefs =
            instance ?: synchronized(this) {
                instance ?: BridgePrefs(context.applicationContext).also { instance = it }
            }
    }
}
