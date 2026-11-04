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

    /** Date du dernier export réussi (0 = jamais). */
    var lastExport: Long
        get() = sp.getLong("lastExport", 0L)
        set(v) = sp.edit().putLong("lastExport", v).apply()

    /** « Plus tard » sur le rappel de sauvegarde. */
    var backupSnooze: Long
        get() = sp.getLong("backupSnooze", 0L)
        set(v) = sp.edit().putLong("backupSnooze", v).apply()

    /** "auto" (comme le téléphone), "clair" ou "sombre". */
    var themeMode: String
        get() = sp.getString("themeMode", "auto") ?: "auto"
        set(v) = sp.edit().putString("themeMode", v).apply()

    /** Version du dernier nettoyage des anciennes données. */
    var cleanupVersion: Int
        get() = sp.getInt("cleanupVersion", 0)
        set(v) = sp.edit().putInt("cleanupVersion", v).apply()

    /** Collection en liste compacte plutôt qu'en grille. */
    var listMode: Boolean
        get() = sp.getBoolean("listMode", false)
        set(v) = sp.edit().putBoolean("listMode", v).apply()

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    /** Sauvegarde automatique chaque semaine dans Téléchargements/Mangatheque. */
    var autoBackup: Boolean
        get() = sp.getBoolean("autoBackup", true)
        set(v) = sp.edit().putBoolean("autoBackup", v).apply()

    var lastAutoBackup: Long
        get() = sp.getLong("lastAutoBackup", 0L)
        set(v) = sp.edit().putLong("lastAutoBackup", v).apply()

    var lastOrphanCleanup: Long
        get() = sp.getLong("lastOrphanCleanup", 0L)
        set(v) = sp.edit().putLong("lastOrphanCleanup", v).apply()

    /** Fiche série : ne montrer que les tomes possédés. */
    var hideMissing: Boolean
        get() = sp.getBoolean("hideMissing", false)
        set(v) = sp.edit().putBoolean("hideMissing", v).apply()

    /** Magasin : n'afficher que les séries où il manque des tomes. */
    var storeOnlyMissing: Boolean
        get() = sp.getBoolean("storeOnlyMissing", false)
        set(v) = sp.edit().putBoolean("storeOnlyMissing", v).apply()

    var sortMode: String
        get() = sp.getString("sortMode", "") ?: ""
        set(v) = sp.edit().putString("sortMode", v).apply()
}
