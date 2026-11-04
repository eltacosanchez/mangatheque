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
import fr.mangatheque.app.data.Editions
import androidx.compose.runtime.mutableStateMapOf
import fr.mangatheque.app.data.MangaInfo
import fr.mangatheque.app.data.FrenchTranslator
import fr.mangatheque.app.data.Repository
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import androidx.compose.runtime.mutableStateListOf
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
    MANQUANTS("Tomes manquants"),
    AUTEUR("Auteur")
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

    /** Collection affichée en liste compacte plutôt qu'en grille. */
    var listMode by mutableStateOf(prefs.listMode)
        private set
    fun toggleListMode() { listMode = !listMode; prefs.listMode = listMode }

    /** Vibrations (scan, appui long, changement de statut). */
    var hapticsOn by mutableStateOf(prefs.haptics)
        private set
    fun changeHaptics(on: Boolean) { hapticsOn = on; prefs.haptics = on }

    var autoBackupOn by mutableStateOf(prefs.autoBackup)
        private set
    fun changeAutoBackup(on: Boolean) { autoBackupOn = on; prefs.autoBackup = on }

    var hideMissing by mutableStateOf(prefs.hideMissing)
        private set
    fun changeHideMissing(on: Boolean) { hideMissing = on; prefs.hideMissing = on }

    var storeOnlyMissing by mutableStateOf(prefs.storeOnlyMissing)
        private set
    fun changeStoreOnlyMissing(on: Boolean) { storeOnlyMissing = on; prefs.storeOnlyMissing = on }

    /** Nombre de tomes ajoutés d'affilée avec « Ajouter et scanner le suivant ». */
    var chainCount by mutableStateOf(0)
        private set
    fun resetChain() { chainCount = 0 }

    init {
        // Nettoyage unique des données des anciennes versions (faux genres, « - Édition originale »).
        if (prefs.cleanupVersion < 1) viewModelScope.launch {
            repo.cleanupLegacyData()
            prefs.cleanupVersion = 1
        }
        val now = System.currentTimeMillis()
        // Sauvegarde automatique hebdomadaire dans Téléchargements/Mangatheque.
        if (prefs.autoBackup && now - prefs.lastAutoBackup > 7L * 24 * 3600_000) viewModelScope.launch {
            if (repo.seriesDao.listAll().isNotEmpty()) {
                Backup.exportToDownloads(getApplication(), repo, "auto").onSuccess { prefs.lastAutoBackup = now }
            }
        }
        // Ménage des images inutilisées, une fois par semaine.
        if (now - prefs.lastOrphanCleanup > 7L * 24 * 3600_000) viewModelScope.launch {
            repo.cleanupOrphanFiles()
            prefs.lastOrphanCleanup = now
        }
    }

    fun lastAutoBackupLabel(): String =
        prefs.lastAutoBackup.takeIf { it > 0 }?.let { DateFormat.getDateInstance(DateFormat.LONG).format(Date(it)) } ?: "jamais"

    suspend fun storageUsedMb(): String = "%.1f Mo".format(repo.storageUsed() / 1_048_576.0)

    // ----- Résumé du tome -----
    private val summaryTried = mutableSetOf<Long>()

    /** Cherche le résumé du dos du livre d'un tome (une fois), et l'enregistre. */
    fun fetchVolumeSummary(v: Volume) {
        if (v.summary != null || !BookLookup.isValidIsbn(v.isbn) || !summaryTried.add(v.id)) return
        viewModelScope.launch {
            val text = BookLookup.frenchSummary(v.isbn)
            val fresh = repo.volumeDao.getOne(v.id) ?: return@launch
            // "" = cherché mais introuvable (on ne recherche plus).
            if (fresh.summary == null) repo.volumeDao.update(fresh.copy(summary = text ?: ""))
        }
    }

    fun setReadDate(v: Volume, date: Long) = viewModelScope.launch { repo.setReadDate(v, date) }

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
                fillDraftCover(clean) // le livre est inconnu, mais une librairie a peut-être sa couverture
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
            author = info.authorsText.ifBlank { d.author },
            bannerUrl = info.imageUrl,
            seriesInfoFound = true
        )
        // Pas de résumé français trouvé : on traduit celui d'AniList.
        if (d.synopsis.isBlank() && info.synopsisEn != null) {
            val fr = FrenchTranslator.toFrench(info.synopsisEn) ?: return@launch
            draft?.takeIf { it.isbn == isbn && it.synopsis.isBlank() }?.let { draft = it.copy(synopsis = fr) }
        }
    }

    /** Les noms de l'auteur déjà enregistré se retrouvent-ils dans ceux d'AniList ? */
    private fun sameAuthors(current: String, info: MangaInfo): Boolean {
        val tokens = Repository.normalize(current).split(" ").filter { it.length >= 3 }.toSet()
        return tokens.isNotEmpty() && info.authors.any { p -> Repository.normalize(p.latin).split(" ").any { it in tokens } }
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
    fun saveDraft(chain: Boolean = false, onDone: (volumeId: Long) -> Unit) {
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
            val summary = (d.lookup as? LookupState.Found)?.info?.description?.takeIf { it.isNotBlank() }
            val id = repo.addVolume(Volume(seriesId = seriesId, number = number, isbn = d.isbn, coverPath = cover, summary = summary))
            if (chain) chainCount++ else chainCount = 0
            // Livre « introuvable » du scan multiple complété à la main : on le marque comme ajouté.
            batch.indexOfFirst { it.isbn == d.isbn && d.isbn.isNotBlank() }.takeIf { it >= 0 }?.let { i ->
                batch[i] = batch[i].copy(state = BatchState.Saved(id, title, number, cover))
            }
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

    // ----- Scan multiple : on scanne, l'appli prépare les fiches, on vérifie, puis on valide -----
    /** Livres scannés pendant la session de scan multiple (le plus récent en premier). */
    val batch = mutableStateListOf<BatchItem>()
    private val batchSlots = Semaphore(3)   // 3 recherches en même temps
    private val batchDb = Mutex()           // une seule écriture à la fois (évite de créer 2 fois la même série)
    /** Enregistrement en cours : (fait, total). */
    var batchSaving by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    fun batchReset() { if (batch.none { it.state is BatchState.Searching } && batchSaving == null) batch.clear() }

    /** Ajoute un ISBN à la liste ; retourne false s'il a déjà été scanné dans cette session. */
    fun batchAdd(isbn: String): Boolean {
        val clean = BookLookup.normalizeIsbn(isbn)
        if (batch.any { it.isbn == clean }) return false
        batch.add(0, BatchItem(clean, BatchState.Searching))
        launchBatch(clean)
        return true
    }

    fun batchRemove(isbn: String) { batch.removeAll { it.isbn == isbn } }

    fun batchRetry(isbn: String) {
        val i = batch.indexOfFirst { it.isbn == isbn }.takeIf { it >= 0 } ?: return
        batch[i] = batch[i].copy(state = BatchState.Searching)
        launchBatch(isbn)
    }

    private fun launchBatch(isbn: String) = viewModelScope.launch {
        val state = batchSlots.withPermit { runCatching { prepare(isbn) }.getOrElse { BatchState.Failed("Erreur") } }
        val i = batch.indexOfFirst { it.isbn == isbn }
        if (i >= 0) batch[i] = batch[i].copy(state = state)
    }

    /** Prépare la fiche du livre (recherche, série, numéro) sans rien enregistrer. */
    private suspend fun prepare(isbn: String): BatchState {
        repo.findVolumeByIsbn(isbn)?.let { (v, s) -> return BatchState.Owned(s.title, v.number, v.coverPath) }
        val book = try { BookLookup.lookup(isbn) } catch (e: IOException) { return BatchState.Failed("Pas de réseau") }
            ?: return BatchState.NotFound
        val title = book.seriesTitle.ifBlank { book.rawTitle }.ifBlank { "Sans titre" }
        val guess = repo.guessSeries(isbn, title)
        val manga = MangaDb.search(guess?.title ?: title).getOrNull()
        val number = book.volumeNumber ?: guess?.let { nextNumber(it.id).toInt() } ?: 1
        return BatchState.Ready(book, guess?.title ?: title, number, guess != null, book.volumeNumber == null, manga)
    }

    /** Enregistre dans la bibliothèque tous les livres prêts de la liste. */
    fun batchValidate(onDone: (saved: Int, left: Int) -> Unit) {
        if (batchSaving != null) return
        val ready = batch.filter { it.state is BatchState.Ready }
        if (ready.isEmpty()) { onDone(0, batch.count { it.state is BatchState.NotFound || it.state is BatchState.Failed }); return }
        viewModelScope.launch {
            var saved = 0
            ready.forEachIndexed { k, item ->
                batchSaving = k to ready.size
                val r = item.state as BatchState.Ready
                val state = runCatching { save(item.isbn, r) }.getOrElse { BatchState.Failed("Erreur d'enregistrement") }
                if (state is BatchState.Saved) saved++
                val i = batch.indexOfFirst { it.isbn == item.isbn }
                if (i >= 0) batch[i] = batch[i].copy(state = state)
            }
            batchSaving = null
            toast("$saved manga${if (saved > 1) "s" else ""} ajouté${if (saved > 1) "s" else ""} à ta bibliothèque")
            onDone(saved, batch.count { it.state is BatchState.NotFound || it.state is BatchState.Failed })
        }
    }

    private suspend fun save(isbn: String, r: BatchState.Ready): BatchState = batchDb.withLock {
        repo.findVolumeByIsbn(isbn)?.let { (v, s) -> return@withLock BatchState.Owned(s.title, v.number, v.coverPath) }
        val book = r.book
        // La série a pu être créée par un livre précédent de la même liste.
        val guess = repo.guessSeries(isbn, r.seriesTitle)
        val seriesId = guess?.id ?: run {
            val info = r.manga
            val banner = info?.imageUrl?.let { Images.download(getApplication(), it, "banner")?.path }
            repo.saveSeries(
                Series(
                    title = r.seriesTitle, author = info?.authorsText?.ifBlank { null } ?: book.authors, publisher = book.publisher,
                    genres = info?.genres?.joinToString(", ") ?: cleanCategories(book.categories).joinToString(", "),
                    synopsis = book.description, ongoing = info?.finished?.not() ?: true,
                    totalVolumes = if (info?.finished == true) info.volumes else null,
                    colorSeed = r.seriesTitle.hashCode(), bannerPath = banner
                )
            )
        }
        val title = guess?.title ?: r.seriesTitle
        if (repo.volumeDao.listForSeries(seriesId).any { it.number == r.number }) return@withLock BatchState.Duplicate(title, r.number)
        val cover = book.coverUrl?.let { Images.download(getApplication(), it)?.path }
        val id = repo.addVolume(
            Volume(seriesId = seriesId, number = r.number, isbn = isbn, coverPath = cover, summary = book.description.takeIf { it.isNotBlank() })
        )
        BatchState.Saved(id, title, r.number, cover)
    }

    // ----- Couvertures manquantes -----
    private fun fillDraftCover(isbn: String) = viewModelScope.launch {
        val url = BookLookup.findCover(isbn) ?: return@launch
        val d = draft ?: return@launch
        if (d.isbn == isbn && d.coverPath == null && d.coverUrl == null) draft = d.copy(coverUrl = url)
    }

    /** Séries dont on a déjà cherché les couvertures manquantes pendant cette session. */
    private val coversTried = mutableSetOf<Long>()

    /** Cherche la couverture des tomes enregistrés sans image (et qui ont un ISBN). */
    fun fetchMissingCovers(seriesId: Long) {
        if (!coversTried.add(seriesId)) return
        viewModelScope.launch {
            var found = 0
            repo.volumeDao.listForSeries(seriesId)
                .filter { it.coverPath == null && BookLookup.isValidIsbn(it.isbn) }
                .forEach { v ->
                    val url = BookLookup.findCover(v.isbn) ?: return@forEach
                    val file = Images.download(getApplication(), url) ?: return@forEach
                    val fresh = repo.volumeDao.getOne(v.id) ?: return@forEach
                    if (fresh.coverPath == null) { repo.updateVolume(fresh.copy(coverPath = file.path)); found++ }
                }
            if (found > 0) toast("$found couverture${if (found > 1) "s" else ""} retrouvée${if (found > 1) "s" else ""}")
        }
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
                val wantInfo = s.genreList.isEmpty() || s.synopsis.isBlank() || !s.author.contains('(')
                if (!wantBanner && !wantInfo) return@launch
                val result = MangaDb.search(s.title)
                if (result.isFailure) {
                    infoTried -= seriesId
                    if (manual) toast("AniList injoignable")
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
                    // Auteur japonais : nom d'origine + nom lisible entre parenthèses.
                    if (info.authorsText.contains('(') && !updated.author.contains('(') && (updated.author.isBlank() || sameAuthors(updated.author, info)))
                        updated = updated.copy(author = info.authorsText)
                    if (updated.synopsis.isBlank() && info.synopsisEn != null)
                        FrenchTranslator.toFrench(info.synopsisEn)?.let { updated = updated.copy(synopsis = it) }
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
    /** Relit le tome avant d'écrire la note : ne réécrase pas un statut changé entre-temps. */
    fun setNoteById(id: Long, note: String) = viewModelScope.launch { repo.volumeDao.getOne(id)?.let { repo.volumeDao.update(it.copy(note = note)) } }
    fun updateVolume(v: Volume) = viewModelScope.launch { repo.updateVolume(v); toast("Tome mis à jour") }
    fun deleteVolume(v: Volume, onDone: () -> Unit) = viewModelScope.launch { repo.deleteVolume(v); toast("Tome supprimé"); onDone() }

    /** Ajoute un tome manquant ; s'il est connu de la BnF, il reçoit son ISBN et sa vraie couverture. */
    fun addMissingVolume(seriesId: Long, number: Int) = viewModelScope.launch {
        if (repo.volumeDao.listForSeries(seriesId).any { it.number == number }) return@launch
        val isbn = editions[seriesId]?.get(number).orEmpty()
        val cover = if (isbn.isNotBlank()) (coverUrls[isbn] ?: BookLookup.findCover(isbn))?.let { Images.download(getApplication(), it)?.path } else null
        repo.addVolume(Volume(seriesId = seriesId, number = number, isbn = isbn, coverPath = cover))
        toast("Tome $number ajouté")
    }

    fun addVolumeRange(seriesId: Long, from: Int, to: Int) = viewModelScope.launch {
        val n = repo.addVolumeRange(seriesId, from, to, editions[seriesId].orEmpty())
        toast(if (n == 0) "Tu avais déjà tous ces tomes" else "$n tome${if (n > 1) "s" else ""} ajouté${if (n > 1) "s" else ""}")
        coversTried -= seriesId
        fetchMissingCovers(seriesId) // les couvertures arrivent en arrière-plan
    }

    // ----- Prochaines sorties en France -----
    /** Tomes à paraître (ou sortis il y a moins de 45 jours et pas encore achetés) des séries en cours. */
    val releases = mutableStateListOf<Release>()
    var releasesLoading by mutableStateOf(false)
        private set

    // (le cache des sorties est relu plus bas, une fois la liste des séries suivies prête)

    /** Séries dont on ne suit pas les sorties. */
    var mutedReleases by mutableStateOf(prefs.mutedReleases)
        private set

    fun followsReleases(seriesId: Long) = seriesId.toString() !in mutedReleases

    fun setFollowReleases(seriesId: Long, follow: Boolean) {
        mutedReleases = if (follow) mutedReleases - seriesId.toString() else mutedReleases + seriesId.toString()
        prefs.mutedReleases = mutedReleases
        if (follow) refreshReleases(force = true) else releases.removeAll { it.seriesId == seriesId }
    }

    init { loadReleasesCache()
    }

    /** Recherche les sorties, au plus toutes les 12 heures (ou tout de suite si [force]). */
    fun refreshReleases(force: Boolean = false) {
        if (releasesLoading) return
        if (!force && System.currentTimeMillis() - prefs.releasesAt < 12 * 3600_000L) return
        releasesLoading = true
        viewModelScope.launch {
            try {
                val today = java.time.LocalDate.now()
                val found = mutableListOf<Release>()
                for (s in repo.seriesDao.listAll().filter { it.ongoing && followsReleases(it.id) }) {
                    val data = repo.seriesDao.getOne(s.id) ?: continue
                    if (data.volumes.isEmpty()) continue
                    val known = data.volumes.map { it.isbn }.filter { BookLookup.isValidIsbn(it) }
                    val map = editions[s.id] ?: Editions.frenchVolumes(s.title, s.publisher, known).getOrNull()?.takeIf { it.isNotEmpty() }?.also { editions[s.id] = it } ?: continue
                    for (n in map.keys.sortedDescending().take(3)) {
                        val isbn = map.getValue(n)
                        val date = BookLookup.releaseDate(isbn) ?: continue
                        val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                        val owned = n in data.ownedNumbers
                        if (days >= 0 || (days >= -45 && !owned)) found += Release(s.id, s.title, n, isbn, date.toString(), owned)
                    }
                }
                releases.clear(); releases.addAll(found.sortedBy { it.date })
                prefs.releasesAt = System.currentTimeMillis()
                prefs.releasesJson = org.json.JSONArray().apply {
                    found.forEach { r -> put(org.json.JSONObject().put("s", r.seriesId).put("t", r.title).put("n", r.number).put("i", r.isbn).put("d", r.date).put("o", r.owned)) }
                }.toString()
            } finally {
                releasesLoading = false
            }
        }
    }

    private fun loadReleasesCache() {
        runCatching {
            val arr = org.json.JSONArray(prefs.releasesJson.ifBlank { "[]" })
            val today = java.time.LocalDate.now().toString()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val r = Release(o.getLong("s"), o.getString("t"), o.getInt("n"), o.getString("i"), o.getString("d"), o.optBoolean("o"))
                if ((r.date >= today || !r.owned) && followsReleases(r.seriesId)) releases.add(r)
            }
        }
    }

    // ----- Édition française (BnF) : tomes parus, ISBN et couvertures -----
    /** Série → (numéro de tome → ISBN de l'édition française). */
    val editions = mutableStateMapOf<Long, Map<Int, String>>()
    /** ISBN → adresse de la couverture (pour afficher les tomes manquants). */
    val coverUrls = mutableStateMapOf<String, String>()
    private val coverChecked = mutableSetOf<String>()
    private val editionsTried = mutableSetOf<Long>()

    /**
     * Récupère la liste des tomes parus en France : met à jour le nombre de tomes parus,
     * le statut (en cours / terminée) et donne leur ISBN (donc leur couverture) aux tomes ajoutés à la main.
     */
    fun syncFrenchEdition(seriesId: Long) {
        if (!editionsTried.add(seriesId)) return
        viewModelScope.launch {
            val data = repo.seriesDao.getOne(seriesId) ?: return@launch
            val s = data.series
            val known = data.volumes.map { it.isbn }.filter { BookLookup.isValidIsbn(it) }
            val map = Editions.frenchVolumes(s.title, s.publisher, known).getOrNull()
            if (map.isNullOrEmpty()) { editionsTried -= seriesId; return@launch }
            editions[seriesId] = map
            val frenchMax = map.keys.max()
            val info = MangaDb.search(s.title).getOrNull()
            val fresh = repo.seriesDao.getOne(seriesId)?.series ?: return@launch
            var updated = fresh
            if ((fresh.totalVolumes ?: 0) < frenchMax) updated = updated.copy(totalVolumes = frenchMax)
            // Terminée = finie au Japon ET tous ses tomes déjà parus en France.
            info?.finished?.let { finishedJp ->
                val done = finishedJp && (info.volumes == null || frenchMax >= info.volumes)
                updated = updated.copy(ongoing = !done)
            }
            if (updated != fresh) repo.saveSeries(updated)
            // Tomes ajoutés à la main (sans ISBN) : on leur donne celui de l'édition française.
            var changed = false
            data.volumes.filter { it.isbn.isBlank() && map[it.number] != null }.forEach { v ->
                repo.volumeDao.getOne(v.id)?.let { repo.volumeDao.update(it.copy(isbn = map.getValue(v.number))); changed = true }
            }
            if (changed) { coversTried -= seriesId; fetchMissingCovers(seriesId) }
        }
    }

    /** Cherche (une fois) la couverture d'un ISBN, pour afficher un tome manquant. */
    fun requestCover(isbn: String) {
        if (!coverChecked.add(isbn)) return
        viewModelScope.launch { BookLookup.findCover(isbn)?.let { coverUrls[isbn] = it } }
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
        // Sécurité : la collection actuelle est sauvegardée avant d'être remplacée.
        if (repo.seriesDao.listAll().isNotEmpty()) Backup.exportToDownloads(getApplication(), repo, "avant_restauration", keep = 2)
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
            if (manual) _update.value = UpdateState.Failed("Indique ton dépôt GitHub dans les réglages.")
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

/** Un livre scanné en mode « scan multiple ». */
data class BatchItem(val isbn: String, val state: BatchState)

sealed class BatchState {
    data object Searching : BatchState()
    /** Fiche prête, pas encore enregistrée. [knownSeries] = la série existe déjà dans la bibliothèque. */
    data class Ready(
        val book: BookInfo, val seriesTitle: String, val number: Int,
        val knownSeries: Boolean, val needsCheck: Boolean, val manga: MangaInfo?
    ) : BatchState()
    data class Saved(val volumeId: Long, val title: String, val number: Int, val cover: String?) : BatchState()
    data class Owned(val title: String, val number: Int, val cover: String?) : BatchState()
    data class Duplicate(val title: String, val number: Int) : BatchState()
    data object NotFound : BatchState()
    data class Failed(val message: String) : BatchState()
}

/** Tome d'une de mes séries qui sort (ou vient de sortir) en France. [date] au format AAAA-MM-JJ. */
data class Release(val seriesId: Long, val title: String, val number: Int, val isbn: String, val date: String, val owned: Boolean)
