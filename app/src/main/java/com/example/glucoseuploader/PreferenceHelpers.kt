package com.example.glucoseuploader

import android.content.SharedPreferences

/**
 * Helper functions for preference management
 */
object PreferenceHelpers {
    /**
     * Save a boolean preference
     */
    fun saveBoolean(prefs: SharedPreferences, key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    /**
     * Save an integer preference
     */
    fun saveInt(prefs: SharedPreferences, key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    /**
     * Save a string preference
     */
    fun saveString(prefs: SharedPreferences, key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}