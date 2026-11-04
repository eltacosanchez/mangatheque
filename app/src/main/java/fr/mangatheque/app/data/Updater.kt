package fr.mangatheque.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import fr.mangatheque.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val notes: String,
    /** URL de téléchargement direct (dépôt public). */
    val apkUrl: String,
    /** URL API de l'asset (fonctionne aussi en privé, avec un jeton). */
    val apkApiUrl: String,
    val apkName: String,
    val apkSize: Long,
    val pageUrl: String
)

/**
 * Vérifie la dernière Release GitHub du dépôt configuré, compare la version et télécharge l'APK.
 * Le dépôt est « utilisateur/depot » (ex. "eltaco/mangatheque"), configurable dans les Réglages.
 * Si le dépôt est privé, un jeton d'accès personnel GitHub (scope « repo » ou « Contents: read ») est requis.
 */
object Updater {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun currentVersion(): String = BuildConfig.VERSION_NAME

    private fun Request.Builder.github(token: String): Request.Builder {
        header("Accept", "application/vnd.github+json")
        header("User-Agent", "Mangatheque-Android")
        header("X-GitHub-Api-Version", "2022-11-28")
        if (token.isNotBlank()) header("Authorization", "Bearer $token")
        return this
    }

    /** Retourne la release si elle est plus récente que la version installée, sinon null. */
    suspend fun checkLatest(repo: String, token: String): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val r = repo.trim().trim('/').removePrefix("https://github.com/")
            if (!r.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) error("Dépôt GitHub invalide : « $r » (format attendu : utilisateur/depot)")
            val req = Request.Builder().url("https://api.github.com/repos/$r/releases/latest").github(token).build()
            val body = client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    404 -> error(if (token.isBlank()) "Aucune release trouvée sur $r. Si le dépôt est privé, ajoute un jeton GitHub dans les réglages." else "Aucune release publiée sur $r (vérifie le nom du dépôt et le jeton).")
                    401 -> error("Jeton GitHub refusé (expiré ou invalide).")
                    403 -> error("Accès refusé par GitHub (limite atteinte ou jeton sans droit « Contents »).")
                }
                if (!resp.isSuccessful) error("GitHub a répondu ${resp.code}")
                resp.body?.string() ?: error("Réponse vide")
            }
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            val version = tag.removePrefix("v").removePrefix("V")
            val assets = json.optJSONArray("assets")
            var apk: JSONObject? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) { apk = a; break }
            }
            if (apk == null) error("La release $tag ne contient aucun fichier .apk")
            val info = ReleaseInfo(
                tag = tag, version = version, notes = json.optString("body"),
                apkUrl = apk.optString("browser_download_url"), apkApiUrl = apk.optString("url"),
                apkName = apk.optString("name"), apkSize = apk.optLong("size"), pageUrl = json.optString("html_url")
            )
            if (isNewer(info.version, currentVersion())) info else null
        }
    }

    /** Compare « 1.2.10 » et « 1.2.9 » numériquement, segment par segment. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-', '+').map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Télécharge l'APK dans le dossier privé de l'appli, avec progression.
     * Avec un jeton, on passe par l'API (asset « application/octet-stream ») : ça marche aussi en dépôt privé.
     * OkHttp retire lui-même l'en-tête Authorization lors de la redirection vers le serveur de fichiers de GitHub.
     */
    suspend fun downloadApk(context: Context, info: ReleaseInfo, token: String, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, info.apkName.ifBlank { "mangatheque-${info.version}.apk" }.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                val url = if (token.isNotBlank() && info.apkApiUrl.isNotBlank()) info.apkApiUrl else info.apkUrl
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Mangatheque-Android")
                    .header("Accept", "application/octet-stream")
                    .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("Téléchargement refusé (${resp.code})")
                    val body = resp.body ?: error("Réponse vide")
                    val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
                    var done = 0L
                    body.byteStream().use { input ->
                        target.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var lastReport = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0 && done - lastReport > 256 * 1024) { lastReport = done; onProgress(done.toFloat() / total) }
                            }
                        }
                    }
                }
                if (target.length() < 1_000_000) error("Fichier téléchargé incomplet (${target.length()} octets)")
                onProgress(1f)
                target
            }
        }

    /** Ouvre l'installateur Android sur l'APK téléchargé. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun canInstallUnknown(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
