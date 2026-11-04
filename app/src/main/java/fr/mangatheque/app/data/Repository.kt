package fr.mangatheque.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** Point d'accès unique aux données (base Room + fichiers photo). */
class Repository(private val context: Context, private val db: AppDatabase) {

    val seriesDao get() = db.seriesDao()
    val volumeDao get() = db.volumeDao()
    val momentDao get() = db.momentDao()

    fun observeSeries(): Flow<List<SeriesWithVolumes>> = seriesDao.observeAll()
    fun observeSeries(id: Long): Flow<SeriesWithVolumes?> = seriesDao.observeOne(id)
    fun observeVolume(id: Long): Flow<Volume?> = volumeDao.observeOne(id)
    fun observeMoments(volumeId: Long): Flow<List<Moment>> = momentDao.observeForVolume(volumeId)
    fun observeAllMoments(): Flow<List<MomentWithContext>> = momentDao.observeAll()

    suspend fun findVolumeByIsbn(isbn: String): Pair<Volume, Series>? {
        val v = volumeDao.findByIsbn(isbn) ?: return null
        val s = seriesDao.getOne(v.seriesId)?.series ?: return null
        return v to s
    }

    /**
     * Cherche la série la plus probable pour un ISBN : préfixe ISBN commun le plus long (≥ 9 chiffres),
     * sinon titre proche. Retourne null si rien de convaincant.
     */
    suspend fun guessSeries(isbn: String, title: String?): Series? {
        val all = seriesDao.listAll()
        if (isbn.isNotBlank()) {
            val byPrefix = all.filter { it.isbnPrefix.length >= 9 && isbn.startsWith(it.isbnPrefix) }
                .maxByOrNull { it.isbnPrefix.length }
            if (byPrefix != null) return byPrefix
        }
        if (!title.isNullOrBlank()) {
            val norm = normalize(title)
            all.firstOrNull { normalize(it.title) == norm }?.let { return it }
            all.firstOrNull { norm.startsWith(normalize(it.title)) || normalize(it.title).startsWith(norm) }?.let { return it }
        }
        return null
    }

    suspend fun saveSeries(series: Series): Long =
        if (series.id == 0L) seriesDao.insert(series) else { seriesDao.update(series); series.id }

    suspend fun deleteSeries(s: SeriesWithVolumes) {
        s.volumes.forEach { v -> momentDao.listAll().filter { it.volumeId == v.id }.forEach { deleteFile(it.photoPath) }; deleteFile(v.coverPath) }
        deleteFile(s.series.coverPath)
        deleteFile(s.series.bannerPath)
        seriesDao.delete(s.series)
    }

    /**
     * Nettoyage des séries créées par les anciennes versions : faux genres retirés,
     * « - Édition originale » enlevé du titre. Retourne le nombre de séries corrigées.
     */
    suspend fun cleanupLegacyData(): Int {
        var fixed = 0
        seriesDao.listAll().forEach { s ->
            val genres = s.genreList.joinToString(", ")
            val title = TitleParser.clean(s.title).ifBlank { s.title }
            if (genres != s.genres || title != s.title) { seriesDao.update(s.copy(genres = genres, title = title)); fixed++ }
        }
        return fixed
    }

    /** Ajoute d'un coup les tomes [from]..[to] qui ne sont pas encore dans la série. Retourne le nombre ajouté. */
    suspend fun addVolumeRange(seriesId: Long, from: Int, to: Int): Int {
        val owned = volumeDao.listForSeries(seriesId).map { it.number }.toSet()
        var added = 0
        for (n in from..to) if (n !in owned) { volumeDao.insert(Volume(seriesId = seriesId, number = n)); added++ }
        return added
    }

    /** Marque comme lus tous les tomes jusqu'au numéro [upTo] inclus. Retourne le nombre modifié. */
    suspend fun markReadUpTo(seriesId: Long, upTo: Int): Int {
        var changed = 0
        volumeDao.listForSeries(seriesId).filter { it.number <= upTo && it.status != ReadStatus.LU }.forEach {
            setStatus(it, ReadStatus.LU); changed++
        }
        return changed
    }

    /** Ajoute un tome et met à jour le préfixe ISBN de la série. */
    suspend fun addVolume(volume: Volume): Long {
        val id = volumeDao.insert(volume)
        refreshIsbnPrefix(volume.seriesId)
        return id
    }

    suspend fun updateVolume(volume: Volume) {
        volumeDao.update(volume)
        refreshIsbnPrefix(volume.seriesId)
    }

    suspend fun deleteVolume(volume: Volume) {
        momentDao.listAll().filter { it.volumeId == volume.id }.forEach { deleteFile(it.photoPath) }
        deleteFile(volume.coverPath)
        volumeDao.delete(volume)
        refreshIsbnPrefix(volume.seriesId)
    }

    suspend fun setStatus(volume: Volume, status: ReadStatus) {
        volumeDao.update(
            volume.copy(
                readStatus = status.name,
                readAt = if (status == ReadStatus.LU) (volume.readAt ?: System.currentTimeMillis()) else null
            )
        )
    }

    suspend fun addMoment(m: Moment): Long = momentDao.insert(m)
    suspend fun updateMoment(m: Moment) = momentDao.update(m)
    suspend fun deleteMoment(m: Moment) { deleteFile(m.photoPath); momentDao.delete(m) }

    /** Calcule le plus long préfixe commun des ISBN des tomes de la série (9 à 12 chiffres). */
    private suspend fun refreshIsbnPrefix(seriesId: Long) {
        val isbns = volumeDao.listForSeries(seriesId).map { it.isbn }.filter { it.length == 13 }
        val series = seriesDao.getOne(seriesId)?.series ?: return
        val prefix = when (isbns.size) {
            0 -> ""
            1 -> isbns[0].take(10) // un seul tome : préfixe éditeur + début de référence
            else -> {
                var p = isbns[0]
                for (i in isbns) p = p.commonPrefixWith(i)
                p.take(12).let { if (it.length < 9) isbns[0].take(9) else it }
            }
        }
        if (prefix != series.isbnPrefix) seriesDao.update(series.copy(isbnPrefix = prefix))
    }

    /** Supprime les images que plus aucune fiche n'utilise (et vieilles d'au moins un jour). */
    suspend fun cleanupOrphanFiles(): Int = withContext(Dispatchers.IO) {
        val used = HashSet<String>()
        seriesDao.listAll().forEach { s ->
            s.coverPath?.let { used += File(it).name }
            s.bannerPath?.takeIf { it.isNotBlank() }?.let { used += File(it).name }
        }
        volumeDao.listAll().forEach { v -> v.coverPath?.let { used += File(it).name } }
        momentDao.listAll().forEach { m -> used += File(m.photoPath).name }
        val dayAgo = System.currentTimeMillis() - 24 * 3600_000L
        var n = 0
        File(context.filesDir, "photos").listFiles()?.forEach { f ->
            if (f.isFile && f.name !in used && f.lastModified() < dayAgo && f.delete()) n++
        }
        n
    }

    /** Place occupée par la collection (base de données + images), en octets. */
    suspend fun storageUsed(): Long = withContext(Dispatchers.IO) {
        val photos = File(context.filesDir, "photos").listFiles()?.sumOf { it.length() } ?: 0L
        val db = context.getDatabasePath("mangatheque.db")
        photos + db.length() + File(db.path + "-wal").length()
    }

    suspend fun setReadDate(volume: Volume, date: Long) = volumeDao.update(volume.copy(readAt = date))

    private fun deleteFile(path: String?) {
        if (path.isNullOrBlank() || path.startsWith("http")) return
        runCatching { File(path).delete() }
    }

    suspend fun photosDir(): File = withContext(Dispatchers.IO) {
        File(context.filesDir, "photos").apply { mkdirs() }
    }

    companion object {
        fun normalize(s: String): String = s.lowercase()
            .replace(Regex("[àáâäā]"), "a").replace(Regex("[éèêëē]"), "e").replace(Regex("[îïī]"), "i")
            .replace(Regex("[ôöō]"), "o").replace(Regex("[ùûüū]"), "u").replace("ç", "c")
            .replace(Regex("[^a-z0-9]+"), " ").trim()
    }
}
