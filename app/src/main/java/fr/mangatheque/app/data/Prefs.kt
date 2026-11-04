package fr.mangatheque.app.data

import android.content.Context
import fr.mangatheque.app.BuildConfig

/** Petits réglages persistants (SharedPreferences). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mangatheque", Context.MODE_PRIVATE)

    var updateRepo: String
        get() = sp.getString("updateRepo", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.UPDATE_REPO
        set(v) = sp.edit().putString("updateRepo", v.trim()).apply()

    /** Jeton d'accès personnel GitHub (nécessaire seulement si le dépôt est privé). */
    var githubToken: String
        get() = sp.getString("ghToken", "") ?: ""
        set(v) = sp.edit().putString("ghToken", v.trim()).apply()

    var autoCheckUpdates: Boolean
        get() = sp.getBoolean("autoCheck", true)
        set(v) = sp.edit().putBoolean("autoCheck", v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("lastCheck", 0L)
        set(v) = sp.edit().putLong("lastCheck", v).apply()

    var skippedVersion: String
        get() = sp.getString("skipped", "") ?: ""
        set(v) = sp.edit().putString("skipped", v).apply()

    var storeLargeText: Boolean
        get() = sp.getBoolean("storeLarge", true)
        set(v) = sp.edit().putBoolean("storeLarge", v).apply()
}
