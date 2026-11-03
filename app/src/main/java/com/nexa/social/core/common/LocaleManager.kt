package com.nexa.social.core.common

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applies a per-app language choice on all supported API levels by wrapping the base context.
 * The value is mirrored in plain SharedPreferences so it can be read synchronously in
 * attachBaseContext, while the UI state continues to flow from DataStore.
 */
object LocaleManager {

    const val SYSTEM = "system"
    const val ARABIC = "ar"
    const val ENGLISH = "en"

    private const val PREFS = "nexa_locale"
    private const val KEY = "language"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun persist(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    fun wrap(base: Context): Context {
        val tag = current(base)
        if (tag == SYSTEM) return base
        val locale = Locale(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
