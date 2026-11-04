package fr.mangatheque.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.mangatheque.app.MangaApp
import fr.mangatheque.app.data.Backup
import fr.mangatheque.app.data.BookInfo
import fr.mangatheque.app.data.BookLookup
import fr.mangatheque.app.data.Images
import fr.mangatheque.app.data.MangaDb
import fr.mangatheque.app.data.Moment
import fr.mangatheque.app.data.MomentWithContext
import fr.mangatheque.app.data.ReadStatus
import fr.mangatheque.app.data.ReleaseInfo
import fr.mangatheque.app.data.Series
import fr.mangatheque.app.data.SeriesWithVolumes
import fr.mangatheque.app.data.Updater
import fr.mangatheque.app.data.Volume
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/** Brouillon d'ajout de tome après un scan (ou une saisie manuelle). */
data class AddDraft(
    val isbn: String = "",
    val lookup: LookupState = LookupState.Idle,
    val seriesId: Long? = null,          // null = nouvelle série
    val seriesTitle: String = "",
    val number: String = "",
    val author: String = "",
    val publisher: String = "",
    val genres: String = "",
    val synopsis: String = "",
    val ongoing: Boolean = true,
    val totalVolumes: String = "",
    val coverUrl: String? = null,        // couverture trouvée en ligne
    val coverPath: String? = null,       // couverture prise en photo / galerie (prioritaire)
    val bannerUrl: String? = null,       // image de l'œuvre (MyAnimeList) pour le fond de la fiche
    val seriesInfoFound: Boolean = false,
    val alreadyOwned: Pair<Volume, Series>? = null,
    val source: String = ""
)

sealed class LookupState {
    data object Idle : LookupState()
    /** stage 1 = recherche rapide, 2 = recherche approfondie. */
    data class Loading(val stage: Int = 1) : LookupState()
    data object NotFound : LookupState()
    data class Found(val info: BookInfo) : LookupState()
    data class Error(val message: String) : LookupState()
}

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val info: ReleaseInfo) : UpdateState()
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState()
    data class Downloaded(val info: ReleaseInfo, val file: File) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

enum class SortMode(val label: String) {
    TITRE("A → Z"),
    RECENT("Ajouts récents"),
    PROGRESSION("Progression de lecture"),
    MANQUANTS("Tomes manquants")
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val mangaApp = app as MangaApp
    val repo = mangaApp.repository
    val prefs = mangaApp.prefs

    val series: StateFlow<List<SeriesWithVolumes>> =
        repo.observeSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val moments: StateFlow<List<MomentWithContext>> =
        repo.observeAllMoments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Messages courts affichés en bas (snackbar). */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    fun toast(msg: String) { messages.tryEmit(msg) }

    // ----- Filtres et tri de l'accueil -----
    var query by mutableStateOf("")
    var genreFilter by mutableStateOf<String?>(null)
    var readFilter by mutableStateOf<ReadStatus?>(null)
    var ongoingFilter by mutableStateOf<Boolean?>(null)
    var storeQuery by mutableStateOf("")
    var sort by mutableStateOf(SortMode.entries.firstOrNull { it.name == prefs.sortMode } ?: SortMode.TITRE)
        private set

    fun changeSort(mode: SortMode) { sort = mode; prefs.sortMode = mode.name }

    /** Thème : "auto" (comme le téléphone), "clair" ou "sombre". */
    var themeMode by mutableStateOf(prefs.themeMode)
        private set

    fun changeTheme(mode: String) { themeMode = mode; prefs.themeMode = mode }

    init {
        // Nettoyage unique des données des anciennes versions (faux genres, « - Édition originale »).
        if (prefs.cleanupVersion < 1) viewModelScope.launch {
            repo.cleanupLegacyData()
            prefs.cleanupVersion = 1
        }
    }

    // ----- Ajout de tome -----
    var draft by mutableStateOf<AddDraft?>(null)
        private set
    private var draftJob: Job? = null

    fun startDraft(isbn: String) {
        val clean = BookLookup.normalizeIsbn(isbn)
        draftJob?.cancel()
        draft = AddDraft(isbn = clean, lookup = LookupState.Loading(1))
        draftJob = viewModelScope.launch {
            val owned = repo.findVolumeByIsbn(clean)
            if (owned != null) {
                draft = draft?.copy(alreadyOwned = owned, lookup = LookupState.Idle, seriesId = owned.second.id,
                    seriesTitle = owned.second.title, number = owned.first.number.toString())
                return@launch
            }
            val result = runCatching {
                BookLookup.lookup(clean) { stage ->
                    viewModelScope.launch {
                        val d = draft
                        if (d != null && d.isbn == clean && d.lookup is LookupState.Loading) draft = d.copy(lookup = LookupState.Loading(stage))
                    }
                }
            }
            val d = draft ?: return@launch
            if (d.isbn != clean) return@launch
            val error = result.exceptionOrNull()
            if (error != null) {
                val guess = repo.guessSeries(clean, null)
                draft = d.copy(
                    lookup = LookupState.Error(if (error is IOException) "pas de réseau" else (error.message ?: "erreur")),
                    seriesId = guess?.id, seriesTitle = guess?.title ?: "", number = guess?.let { nextNumber(it.id) } ?: ""
                )
                return@launch
            }
            val book = result.getOrNull()
            val guess = repo.guessSeries(clean, book?.seriesTitle)
            if (book == null) {
                draft = d.copy(
                    lookup = LookupState.NotFound,
                    seriesId = guess?.id, seriesTitle = guess?.title ?: "",
                    author = guess?.author ?: "", publisher = guess?.publisher ?: "", genres = guess?.genres ?: "",
                    ongoing = guess?.ongoing ?: true, totalVolumes = guess?.totalVolumes?.toString() ?: "",
                    number = guess?.let { nextNumber(it.id) } ?: ""
                )
            } else {
                draft = d.copy(
                    lookup = LookupState.Found(book),
                    seriesId = guess?.id,
                    seriesTitle = guess?.title ?: book.seriesTitle,
                    number = book.volumeNumber?.toString() ?: (guess?.let { nextNumber(it.id) } ?: ""),
                    author = guess?.author?.ifBlank { null } ?: book.authors,
                    publisher = guess?.publisher?.ifBlank { null } ?: book.publisher,
                    genres = guess?.genres?.ifBlank { null } ?: cleanCategories(book.categories).joinToString(", "),
                    synopsis = guess?.synopsis?.ifBlank { null } ?: book.description,
                    ongoing = guess?.ongoing ?: true,
                    totalVolumes = guess?.totalVolumes?.toString() ?: "",
                    coverUrl = book.coverUrl,
                    source = book.source
                )
                if (guess == null && book.seriesTitle.isNotBlank()) enrichWithMangaInfo(clean, book.seriesTitle)
            }
        }
    }

    /** Nouvelle série : complète genres, statut, nombre de tomes et image grâce à MyAnimeList. */
    private fun enrichWithMangaInfo(isbn: String, title: String) = viewModelScope.launch {
        val info = MangaDb.search(title).getOrNull() ?: return@launch
        val d = draft ?: return@launch
        if (d.isbn != isbn || d.seriesId != null) return@launch
        draft = d.copy(
            genres = if (info.genres.isNotEmpty()) info.genres.joinToString(", ") else d.genres,
            ongoing = info.finished?.not() ?: d.ongoing,
            totalVolumes = if (info.finished == true && info.volumes != null && d.totalVolumes.isBlank()) info.volumes.toString() else d.totalVolumes,
            bannerUrl = info.imageUrl,
            seriesInfoFound = true
        )
    }

    /** Les catégories Google (« Comics & Graphic Novels / Manga ») ne sont pas des genres utiles. */
    private fun cleanCategories(cats: List<String>): List<String> =
        cats.filterNot {
            it.contains("Comics", true) || it.contains("/") || it.contains(":") || it.contains("Fiction", true) ||
                it.contains("Juvenile", true) || it.contains("manga", true) || it.contains("graphic", true)
        }

    /** Démarre un ajout manuel (sans ISBN), éventuellement dans une série existante. */
    fun startManualDraft(seriesId: Long? = null) {
        draftJob?.cancel()
        viewModelScope.launch {
            val s = seriesId?.let { repo.seriesDao.getOne(it)?.series }
            draft = AddDraft(
                lookup = LookupState.Idle, seriesId = s?.id, seriesTitle = s?.title ?: "",
                author = s?.author ?: "", publisher = s?.publisher ?: "", genres = s?.genres ?: "",
                ongoing = s?.ongoing ?: true, totalVolumes = s?.totalVolumes?.toString() ?: "",
                number = s?.let { nextNumber(it.id) } ?: "1"
            )
        }
    }

    private suspend fun nextNumber(seriesId: Long): String {
        val owned = repo.volumeDao.listForSeries(seriesId).map { it.number }.toSet()
        var n = 1
        while (n in owned) n++
        return n.toString()
    }

    fun updateDraft(transform: (AddDraft) -> AddDraft) { draft = draft?.let(transform) }

    fun selectDraftSeries(s: Series?) {
        viewModelScope.launch {
            val d = draft ?: return@launch
            draft = if (s == null) d.copy(seriesId = null) else d.copy(
                seriesId = s.id, seriesTitle = s.title, author = s.author.ifBlank { d.author },
                publisher = s.publisher.ifBlank { d.publisher }, genres = s.genres.ifBlank { d.genres },
                ongoing = s.ongoing, totalVolumes = s.totalVolumes?.toString() ?: d.totalVolumes,
                number = d.number.ifBlank { nextNumber(s.id) }
            )
        }
    }

    fun cancelDraft() {
        draftJob?.cancel()
        draft?.coverPath?.let { runCatching { File(it).delete() } }
        draft = null
    }

    /** Enregistre le brouillon : crée la série si besoin, télécharge couverture et image de fond, ajoute le tome. */
    fun saveDraft(onDone: (volumeId: Long) -> Unit) {
        val d = draft ?: return
        viewModelScope.launch {
            val number = d.number.trim().toIntOrNull() ?: 1
            val title = d.seriesTitle.trim().ifBlank { "Sans titre" }
            val total = d.totalVolumes.trim().toIntOrNull()
            val seriesId = if (d.seriesId != null) {
                repo.seriesDao.getOne(d.seriesId)?.series?.let { s ->
                    repo.saveSeries(
                        s.copy(
                            author = s.author.ifBlank { d.author.trim() }, publisher = s.publisher.ifBlank { d.publisher.trim() },
                            genres = s.genres.ifBlank { d.genres.trim() }, synopsis = s.synopsis.ifBlank { d.synopsis.trim() },
                            ongoing = d.ongoing, totalVolumes = total ?: s.totalVolumes
                        )
                    )
                } ?: d.seriesId
            } else {
                val banner = d.bannerUrl?.let { Images.download(getApplication(), it, "banner")?.path }
                repo.saveSeries(
                    Series(
                        title = title, author = d.author.trim(), publisher = d.publisher.trim(), genres = d.genres.trim(),
                        synopsis = d.synopsis.trim(), ongoing = d.ongoing, totalVolumes = total,
                        colorSeed = title.hashCode(), bannerPath = banner
                    )
                )
            }
            if (repo.volumeDao.listForSeries(seriesId).any { it.number == number }) {
                toast("Le tome $number existe déjà dans « $title »")
                return@launch
            }
            val cover = d.coverPath ?: d.coverUrl?.let { Images.download(getApplication(), it)?.path }
            val id = repo.addVolume(Volume(seriesId = seriesId, number = number, isbn = d.isbn, coverPath = cover))
            draft = null
            toast("Tome $number ajouté à « $title »")
            onDone(id)
        }
    }

    // ----- Couvertures -----
    /** target : "draft", "volume:ID" ou "series:ID". */
    fun applyCover(target: String, file: File) {
        viewModelScope.launch {
            when {
                target == "draft" -> {
                    draft?.coverPath?.let { old -> runCatching { File(old).delete() } }
                    draft = draft?.copy(coverPath = file.path)
                }
                target.startsWith("volume:") -> {
                    val v = repo.volumeDao.getOne(target.substringAfter(':').toLong()) ?: return@launch
                    v.coverPath?.takeIf { !it.startsWith("http") }?.let { runCatching { File(it).delete() } }
                    repo.updateVolume(v.copy(coverPath = file.path))
                    toast("Couverture enregistrée")
                }
                target.startsWith("series:") -> {
                    val s = repo.seriesDao.getOne(target.substringAfter(':').toLong())?.series ?: return@launch
                    s.coverPath?.let { runCatching { File(it).delete() } }
                    repo.saveSeries(s.copy(coverPath = file.path))
                    toast("Couverture enregistrée")
                }
            }
        }
    }

    /** Couverture choisie dans la galerie. */
    fun applyCoverUri(target: String, uri: Uri) = viewModelScope.launch {
        val f = Images.importUri(getApplication(), uri, "cover")
        if (f == null) toast("Image illisible") else applyCover(target, f)
    }

    // ----- Image de fond des séries -----
    private val bannerJobs = mutableSetOf<Long>()
    /** Séries déjà complétées pendant cette session (évite de réinterroger AniList à chaque ouverture). */
    private val infoTried = mutableSetOf<Long>()

    /**
     * Cherche l'image de l'œuvre sur AniList et complète au passage les infos vides
     * (genres, série terminée + nombre de tomes). [manual] = demandé par l'utilisateur.
     */
    fun fetchBanner(seriesId: Long, manual: Boolean = false) {
        if (!manual && seriesId in infoTried) return
        if (!bannerJobs.add(seriesId)) return
        infoTried += seriesId
        viewModelScope.launch {
            try {
                val s = repo.seriesDao.getOne(seriesId)?.series ?: return@launch
                val wantBanner = manual || s.bannerPath == null
                if (!wantBanner && s.genreList.isNotEmpty()) return@launch
                val result = MangaDb.search(s.title)
                if (result.isFailure) {
                    infoTried -= seriesId
                    if (manual) toast("Impossible de joindre AniList, réessaie plus tard")
                    return@launch // on retentera à la prochaine ouverture
                }
                val info = result.getOrNull()
                val file = if (wantBanner) info?.imageUrl?.let { Images.download(getApplication(), it, "banner") } else null
                val fresh = repo.seriesDao.getOne(seriesId)?.series ?: return@launch
                var updated = fresh
                // Image de fond : seulement si l'utilisateur n'en a pas choisi une entre-temps.
                if (wantBanner && (manual || fresh.bannerPath == null)) {
                    if (file != null) {
                        fresh.bannerPath?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
                        updated = updated.copy(bannerPath = file.path)
                    } else if (!manual) {
                        updated = updated.copy(bannerPath = "")
                    }
                }
                if (info != null) {
                    if (updated.genreList.isEmpty() && info.genres.isNotEmpty()) updated = updated.copy(genres = info.genres.joinToString(", "))
                    if (updated.totalVolumes == null && info.finished == true && info.volumes != null) updated = updated.copy(totalVolumes = info.volumes, ongoing = false)
                }
                if (updated != fresh) repo.saveSeries(updated)
                if (manual) toast(if (file != null) "Image de fond trouvée" else "Aucune image trouvée pour « ${s.title} »")
            } finally {
                bannerJobs.remove(seriesId)
            }
        }
    }

    fun setBannerFromUri(seriesId: Long, uri: Uri) = viewModelScope.launch {
        val f = Images.importUri(getApplication(), uri, "banner") ?: run { toast("Image illisible"); return@launch }
        val s = repo.seriesDao.getOne(seriesId)?.series ?: return@launch
        s.bannerPath?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        repo.saveSeries(s.copy(bannerPath = f.path))
        toast("Image de fond enregistrée")
    }

    fun removeBanner(seriesId: Long) = viewModelScope.launch {
        val s = repo.seriesDao.getOne(seriesId)?.series ?: return@launch
        s.bannerPath?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        repo.saveSeries(s.copy(bannerPath = ""))
        toast("Image de fond retirée")
    }

    // ----- Tomes -----
    fun setStatus(v: Volume, s: ReadStatus) = viewModelScope.launch { repo.setStatus(v, s) }
    fun setRating(v: Volume, r: Int) = viewModelScope.launch { repo.updateVolume(v.copy(rating = r)) }
    fun setNote(v: Volume, note: String) = viewModelScope.launch { repo.updateVolume(v.copy(note = note)) }
    fun updateVolume(v: Volume) = viewModelScope.launch { repo.updateVolume(v); toast("Tome mis à jour") }
    fun deleteVolume(v: Volume, onDone: () -> Unit) = viewModelScope.launch { repo.deleteVolume(v); toast("Tome supprimé"); onDone() }

    /** Ajoute rapidement un tome manquant (sans ISBN) à une série. */
    fun addMissingVolume(seriesId: Long, number: Int) = viewModelScope.launch {
        if (repo.volumeDao.listForSeries(seriesId).any { it.number == number }) return@launch
        repo.addVolume(Volume(seriesId = seriesId, number = number))
        toast("Tome $number ajouté")
    }

    fun addVolumeRange(seriesId: Long, from: Int, to: Int) = viewModelScope.launch {
        val n = repo.addVolumeRange(seriesId, from, to)
        toast(if (n == 0) "Tu avais déjà tous ces tomes" else "$n tome${if (n > 1) "s" else ""} ajouté${if (n > 1) "s" else ""}")
    }

    fun markReadUpTo(seriesId: Long, upTo: Int) = viewModelScope.launch {
        val n = repo.markReadUpTo(seriesId, upTo)
        if (n > 0) toast("$n tome${if (n > 1) "s" else ""} marqué${if (n > 1) "s" else ""} comme lu${if (n > 1) "s" else ""}")
    }

    // ----- Séries -----
    fun saveSeries(s: Series, onDone: (Long) -> Unit) = viewModelScope.launch {
        val id = repo.saveSeries(if (s.colorSeed == 0) s.copy(colorSeed = s.title.hashCode()) else s)
        toast("Série enregistrée"); onDone(id)
    }
    fun deleteSeries(s: SeriesWithVolumes, onDone: () -> Unit) = viewModelScope.launch { repo.deleteSeries(s); toast("Série supprimée"); onDone() }

    // ----- Passages préférés -----
    fun addMoment(volumeId: Long, uri: Uri, caption: String, page: Int?) = viewModelScope.launch {
        val f = Images.importUri(getApplication(), uri, "moment")
        if (f == null) { toast("Photo illisible"); return@launch }
        repo.addMoment(Moment(volumeId = volumeId, photoPath = f.path, caption = caption, page = page))
        toast("Passage ajouté")
    }
    fun updateMoment(m: Moment) = viewModelScope.launch { repo.updateMoment(m) }
    fun deleteMoment(m: Moment) = viewModelScope.launch { repo.deleteMoment(m); toast("Passage supprimé") }

    // ----- Sauvegarde -----
    fun exportTo(uri: Uri) = viewModelScope.launch {
        Backup.export(getApplication(), repo, uri)
            .onSuccess { prefs.lastExport = System.currentTimeMillis(); toast("Sauvegarde créée ($it séries)") }
            .onFailure { toast("Export impossible : ${it.message}") }
    }
    fun importFrom(uri: Uri) = viewModelScope.launch {
        Backup.import(getApplication(), repo, uri)
            .onSuccess { toast("Collection restaurée ($it séries)") }
            .onFailure { toast("Import impossible : ${it.message}") }
    }

    fun lastExportLabel(): String =
        prefs.lastExport.takeIf { it > 0 }?.let { DateFormat.getDateInstance(DateFormat.LONG).format(Date(it)) } ?: "jamais"

    /** Rappel affiché à l'accueil si la collection n'a pas été sauvegardée depuis 30 jours. */
    fun backupReminderDue(totalVolumes: Int): Boolean {
        if (totalVolumes < 5) return false
        val last = maxOf(prefs.lastExport, prefs.backupSnooze)
        return System.currentTimeMillis() - last > 30L * 24 * 3600_000
    }
    fun snoozeBackupReminder() { prefs.backupSnooze = System.currentTimeMillis() }

    // ----- Mise à jour GitHub -----
    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    var updateDialogDismissed by mutableStateOf(false)

    fun checkUpdates(manual: Boolean) {
        val repoName = prefs.updateRepo
        if (repoName.isBlank()) {
            if (manual) _update.value = UpdateState.Failed("Indique ton dépôt GitHub (utilisateur/depot) dans les réglages.")
            return
        }
        if (!manual) {
            if (!prefs.autoCheckUpdates) return
            // Au plus une vérification automatique toutes les 6 heures.
            if (System.currentTimeMillis() - prefs.lastUpdateCheck < 6 * 3600_000L) return
        }
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            prefs.lastUpdateCheck = System.currentTimeMillis()
            Updater.checkLatest(repoName, prefs.githubToken)
                .onSuccess { info ->
                    _update.value = when {
                        info == null -> UpdateState.UpToDate
                        !manual && info.version == prefs.skippedVersion -> UpdateState.UpToDate
                        else -> { updateDialogDismissed = false; UpdateState.Available(info) }
                    }
                }
                .onFailure { _update.value = if (manual) UpdateState.Failed(it.message ?: "Erreur") else UpdateState.Idle }
        }
    }

    fun downloadUpdate() {
        val info = (_update.value as? UpdateState.Available)?.info ?: return
        _update.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            Updater.downloadApk(getApplication(), info, prefs.githubToken) { p -> _update.value = UpdateState.Downloading(info, p) }
                .onSuccess { f -> _update.value = UpdateState.Downloaded(info, f); Updater.install(getApplication(), f) }
                .onFailure { _update.value = UpdateState.Failed(it.message ?: "Téléchargement impossible") }
        }
    }

    fun installDownloaded() {
        (_update.value as? UpdateState.Downloaded)?.let { Updater.install(getApplication(), it.file) }
    }

    fun skipUpdate() {
        (_update.value as? UpdateState.Available)?.let { prefs.skippedVersion = it.info.version }
        _update.value = UpdateState.Idle
    }

    fun dismissUpdate() { updateDialogDismissed = true }
    fun resetUpdateState() { _update.value = UpdateState.Idle }
}
