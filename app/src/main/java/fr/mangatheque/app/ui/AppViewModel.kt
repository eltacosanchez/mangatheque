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
import fr.mangatheque.app.data.Moment
import fr.mangatheque.app.data.MomentWithContext
import fr.mangatheque.app.data.ReadStatus
import fr.mangatheque.app.data.ReleaseInfo
import fr.mangatheque.app.data.Series
import fr.mangatheque.app.data.SeriesWithVolumes
import fr.mangatheque.app.data.Updater
import fr.mangatheque.app.data.Volume
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

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
    val coverPath: String? = null,       // couverture prise en photo (prioritaire)
    val alreadyOwned: Pair<Volume, Series>? = null,
    val source: String = ""
)

sealed class LookupState {
    data object Idle : LookupState()
    data object Loading : LookupState()
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

    // ----- Filtres de l'accueil -----
    var query by mutableStateOf("")
    var genreFilter by mutableStateOf<String?>(null)
    var readFilter by mutableStateOf<ReadStatus?>(null)
    var ongoingFilter by mutableStateOf<Boolean?>(null)
    var storeQuery by mutableStateOf("")

    // ----- Ajout de tome -----
    var draft by mutableStateOf<AddDraft?>(null)
        private set

    fun startDraft(isbn: String) {
        val clean = BookLookup.cleanIsbn(isbn)
        draft = AddDraft(isbn = clean, lookup = LookupState.Loading)
        viewModelScope.launch {
            val owned = repo.findVolumeByIsbn(clean)
            if (owned != null) {
                draft = draft?.copy(alreadyOwned = owned, lookup = LookupState.Idle, seriesId = owned.second.id,
                    seriesTitle = owned.second.title, number = owned.first.number.toString())
                return@launch
            }
            val info = runCatching { BookLookup.lookup(clean) }
            val d = draft ?: return@launch
            if (d.isbn != clean) return@launch
            info.onFailure { e -> draft = d.copy(lookup = LookupState.Error(e.message ?: "Erreur réseau")) ; return@launch }
            val book = info.getOrNull()
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
                    genres = guess?.genres?.ifBlank { null } ?: book.categories.joinToString(", "),
                    synopsis = guess?.synopsis?.ifBlank { null } ?: book.description,
                    ongoing = guess?.ongoing ?: true,
                    totalVolumes = guess?.totalVolumes?.toString() ?: "",
                    coverUrl = book.coverUrl,
                    source = book.source
                )
            }
        }
    }

    /** Démarre un ajout manuel (sans ISBN), éventuellement dans une série existante. */
    fun startManualDraft(seriesId: Long? = null) {
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
        draft?.coverPath?.let { runCatching { File(it).delete() } }
        draft = null
    }

    /** Enregistre le brouillon : crée la série si besoin, télécharge la couverture, ajoute le tome. */
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
                repo.saveSeries(
                    Series(
                        title = title, author = d.author.trim(), publisher = d.publisher.trim(), genres = d.genres.trim(),
                        synopsis = d.synopsis.trim(), ongoing = d.ongoing, totalVolumes = total,
                        colorSeed = title.hashCode()
                    )
                )
            }
            // Doublon de numéro dans la série ?
            val existing = repo.volumeDao.listForSeries(seriesId).firstOrNull { it.number == number }
            if (existing != null) {
                toast("Le tome $number existe déjà dans « $title »")
                return@launch
            }
            val cover = d.coverPath ?: d.coverUrl?.let { Images.download(getApplication(), it)?.path }
            val id = repo.addVolume(
                Volume(seriesId = seriesId, number = number, isbn = d.isbn, coverPath = cover)
            )
            draft = null
            toast("Tome $number ajouté à « $title »")
            onDone(id)
        }
    }

    // ----- Couverture prise en photo -----
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
            .onSuccess { toast("Sauvegarde créée ($it séries)") }
            .onFailure { toast("Export impossible : ${it.message}") }
    }
    fun importFrom(uri: Uri) = viewModelScope.launch {
        Backup.import(getApplication(), repo, uri)
            .onSuccess { toast("Collection restaurée ($it séries)") }
            .onFailure { toast("Import impossible : ${it.message}") }
    }

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
