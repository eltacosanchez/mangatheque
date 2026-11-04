package fr.mangatheque.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Export / import de la collection complète dans un fichier .zip :
 *   mangatheque.json  (séries, tomes, passages)
 *   photos/xxx.jpg    (couvertures et photos)
 */
object Backup {
    const val FORMAT_VERSION = 1

    suspend fun export(context: Context, repo: Repository, target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val series = repo.seriesDao.listAll()
            val volumes = repo.volumeDao.listAll()
            val moments = repo.momentDao.listAll()
            val files = mutableSetOf<String>()

            val json = JSONObject().apply {
                put("format", FORMAT_VERSION)
                put("exportedAt", System.currentTimeMillis())
                put("series", JSONArray().apply {
                    series.forEach { s ->
                        put(JSONObject().apply {
                            put("id", s.id); put("title", s.title); put("author", s.author); put("publisher", s.publisher)
                            put("genres", s.genres); put("ongoing", s.ongoing); put("totalVolumes", s.totalVolumes ?: JSONObject.NULL)
                            put("synopsis", s.synopsis); put("coverPath", fileName(s.coverPath, files) ?: JSONObject.NULL)
                            put("isbnPrefix", s.isbnPrefix); put("colorSeed", s.colorSeed); put("createdAt", s.createdAt)
                        })
                    }
                })
                put("volumes", JSONArray().apply {
                    volumes.forEach { v ->
                        put(JSONObject().apply {
                            put("id", v.id); put("seriesId", v.seriesId); put("number", v.number); put("isbn", v.isbn)
                            put("title", v.title); put("coverPath", fileName(v.coverPath, files) ?: JSONObject.NULL)
                            put("readStatus", v.readStatus); put("rating", v.rating); put("note", v.note)
                            put("readAt", v.readAt ?: JSONObject.NULL); put("addedAt", v.addedAt)
                        })
                    }
                })
                put("moments", JSONArray().apply {
                    moments.forEach { m ->
                        put(JSONObject().apply {
                            put("id", m.id); put("volumeId", m.volumeId); put("photoPath", fileName(m.photoPath, files) ?: JSONObject.NULL)
                            put("caption", m.caption); put("page", m.page ?: JSONObject.NULL); put("createdAt", m.createdAt)
                        })
                    }
                })
            }

            context.contentResolver.openOutputStream(target)!!.use { os ->
                ZipOutputStream(os.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry("mangatheque.json"))
                    zip.write(json.toString(2).toByteArray())
                    zip.closeEntry()
                    val dir = Images.photosDir(context)
                    files.forEach { name ->
                        val f = File(dir, name)
                        if (f.exists()) {
                            zip.putNextEntry(ZipEntry("photos/$name"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            series.size
        }
    }

    /** Remplace toute la collection par le contenu de la sauvegarde. */
    suspend fun import(context: Context, repo: Repository, source: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var jsonText: String? = null
            val dir = Images.photosDir(context)
            val tmpDir = File(context.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
            context.contentResolver.openInputStream(source)!!.use { ins ->
                ZipInputStream(ins.buffered()).use { zip ->
                    var e = zip.nextEntry
                    while (e != null) {
                        val name = e.name
                        if (name == "mangatheque.json") jsonText = zip.readBytes().toString(Charsets.UTF_8)
                        else if (name.startsWith("photos/") && !e.isDirectory) {
                            val safe = File(name).name
                            if (safe.isNotBlank() && !safe.contains("..")) File(tmpDir, safe).outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry(); e = zip.nextEntry
                    }
                }
            }
            val json = JSONObject(jsonText ?: error("Fichier de sauvegarde invalide (mangatheque.json absent)"))

            // On repart de zéro : anciennes données et photos supprimées.
            repo.seriesDao.deleteAll()
            dir.listFiles()?.forEach { it.delete() }
            tmpDir.listFiles()?.forEach { it.copyTo(File(dir, it.name), overwrite = true) }
            tmpDir.deleteRecursively()

            fun path(name: Any?): String? = (name as? String)?.takeIf { it.isNotBlank() }?.let { File(dir, it).path }

            val seriesIds = HashMap<Long, Long>()
            val sArr = json.getJSONArray("series")
            for (i in 0 until sArr.length()) {
                val o = sArr.getJSONObject(i)
                val newId = repo.seriesDao.insert(
                    Series(
                        title = o.optString("title"), author = o.optString("author"), publisher = o.optString("publisher"),
                        genres = o.optString("genres"), ongoing = o.optBoolean("ongoing", true),
                        totalVolumes = if (o.isNull("totalVolumes")) null else o.optInt("totalVolumes"),
                        synopsis = o.optString("synopsis"), coverPath = path(o.opt("coverPath")),
                        isbnPrefix = o.optString("isbnPrefix"), colorSeed = o.optInt("colorSeed"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
                seriesIds[o.getLong("id")] = newId
            }
            val volumeIds = HashMap<Long, Long>()
            val vArr = json.getJSONArray("volumes")
            for (i in 0 until vArr.length()) {
                val o = vArr.getJSONObject(i)
                val sid = seriesIds[o.getLong("seriesId")] ?: continue
                val newId = repo.volumeDao.insert(
                    Volume(
                        seriesId = sid, number = o.optInt("number", 1), isbn = o.optString("isbn"), title = o.optString("title"),
                        coverPath = path(o.opt("coverPath")), readStatus = o.optString("readStatus", ReadStatus.A_LIRE.name),
                        rating = o.optInt("rating"), note = o.optString("note"),
                        readAt = if (o.isNull("readAt")) null else o.optLong("readAt"),
                        addedAt = o.optLong("addedAt", System.currentTimeMillis())
                    )
                )
                volumeIds[o.getLong("id")] = newId
            }
            val mArr = json.optJSONArray("moments") ?: JSONArray()
            for (i in 0 until mArr.length()) {
                val o = mArr.getJSONObject(i)
                val vid = volumeIds[o.getLong("volumeId")] ?: continue
                val p = path(o.opt("photoPath")) ?: continue
                repo.momentDao.insert(
                    Moment(
                        volumeId = vid, photoPath = p, caption = o.optString("caption"),
                        page = if (o.isNull("page")) null else o.optInt("page"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            sArr.length()
        }
    }

    private fun fileName(path: String?, collect: MutableSet<String>): String? {
        if (path.isNullOrBlank() || path.startsWith("http")) return null
        val name = File(path).name
        collect += name
        return name
    }
}
