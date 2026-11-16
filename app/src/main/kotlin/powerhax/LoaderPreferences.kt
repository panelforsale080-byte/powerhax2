package com.accel.loader

import android.content.Context

class LoaderPreferences(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val licenses = LicenseStore(preferences)

    fun getMode(): String = preferences.getString(KEY_MODE, MODE_AUTO) ?: MODE_AUTO

    fun saveMode(mode: String) {
        preferences.edit().putString(KEY_MODE, mode).apply()
    }

    fun getDelaySeconds(): Int = preferences.getInt(KEY_DELAY, 0)

    fun saveDelaySeconds(seconds: Int) {
        preferences.edit().putInt(KEY_DELAY, seconds).apply()
    }

    fun getInjectionMethod(): String = preferences.getString(KEY_METHOD, DEFAULT_METHOD) ?: DEFAULT_METHOD

    fun saveInjectionMethod(method: String) {
        preferences.edit().putString(KEY_METHOD, method).apply()
    }

    fun getPreferredInjectTarget(): String? =
        preferences.getString(KEY_INJECT_TARGET, null)?.takeIf { it.isNotBlank() }

    fun savePreferredInjectTarget(target: String) {
        preferences.edit().putString(KEY_INJECT_TARGET, target).apply()
    }

    fun getModuleSha(): String = preferences.getString(KEY_MODULE_SHA, "") ?: ""

    fun saveModuleSha(sha: String) {
        preferences.edit().putString(KEY_MODULE_SHA, sha).apply()
    }

    fun clearModuleSha() {
        preferences.edit().remove(KEY_MODULE_SHA).apply()
    }

    fun getNotifiedModuleSha(): String = preferences.getString(KEY_MODULE_NOTIFIED, "") ?: ""

    fun setNotifiedModuleSha(sha: String) {
        preferences.edit().putString(KEY_MODULE_NOTIFIED, sha).apply()
    }

    fun getMenuKey(): String = preferences.getString(KEY_MENU_KEY, "") ?: ""

    fun getMenuKeyName(): String = preferences.getString(KEY_MENU_NAME, "") ?: ""

    fun getMenuKeyUntil(): String = preferences.getString(KEY_MENU_UNTIL, "") ?: ""

    fun saveMenuKey(key: String, name: String = "", until: String = "") {
        preferences.edit()
            .putString(KEY_MENU_KEY, key)
            .putString(KEY_MENU_NAME, name)
            .putString(KEY_MENU_UNTIL, until)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "loaderPrefs"
        const val KEY_MODE = "injection_mode"
        const val KEY_DELAY = "injection_delay"
        const val KEY_METHOD = "injection_method"
        const val KEY_INJECT_TARGET = "inject_target_winning"
        const val KEY_MODULE_SHA = "module_sha"
        const val KEY_MODULE_NOTIFIED = "module_notified_sha"
        const val KEY_MENU_KEY = "menu_key"
        const val KEY_MENU_NAME = "menu_key_name"
        const val KEY_MENU_UNTIL = "menu_key_until"
        const val MODE_AUTO = "auto"
        const val DEFAULT_METHOD = "2"
    }
}
