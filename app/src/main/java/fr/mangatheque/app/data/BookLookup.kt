package fr.mangatheque.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Résultat d'une recherche ISBN, déjà découpé en série + numéro de tome. */
data class BookInfo(
    val isbn: String,
    val rawTitle: String,
    val seriesTitle: String,
    val volumeNumber: Int?,
    val authors: String,
    val publisher: String,
    val description: String,
    val coverUrl: String?,
    val categories: List<String>,
    val source: String
)

/**
 * Recherche d'un livre par ISBN, en deux temps :
 *  1. Rapide : Google Books, Open Library et la BnF en même temps, délais courts.
 *  2. Si rien n'est trouvé : recherche approfondie, plus de sources et plus de patience.
 */
object BookLookup {
    private val quick = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()
    private val patient = quick.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * [onStage] reçoit 1 (recherche rapide) puis 2 (recherche approfondie) si besoin.
     * Retourne null si l'ISBN est inconnu partout ; lève IOException si aucune source n'a pu être jointe.
     */
    suspend fun lookup(isbn: String, onStage: (Int) -> Unit = {}): BookInfo? = withContext(Dispatchers.IO) {
        val clean = normalizeIsbn(isbn)
        val isbn10 = toIsbn10(clean)
        // Couverture de l'édition française en haute qualité (librairies ePagine), cherchée en parallèle.
        val frenchCover = async { runCatching { epagineCover(clean, quick) }.getOrNull() }
        onStage(1)
        val first = coroutineScope {
            listOf(
                async { attempt { googleBooks(clean, isbn10, quick, "isbn:$clean") } },
                async { attempt { openLibrary(clean, quick) } },
                async { attempt { bnf(clean, quick) } }
            ).awaitAll()
        }
        merge(first)?.let { return@withContext it.withCover(frenchCover.await()) }

        onStage(2)
        val second = coroutineScope {
            listOfNotNull(
                async { attempt { googleBooks(clean, isbn10, patient, "isbn:$clean") } },
                async { attempt { googleBooks(clean, isbn10, patient, clean) } },
                isbn10?.let { i10 -> async { attempt { googleBooks(clean, isbn10, patient, "isbn:$i10") } } },
                async { attempt { openLibrary(clean, patient) } },
                async { attempt { openLibraryEdition(clean, patient) } },
                async { attempt { bnf(clean, patient) } }
            ).awaitAll()
        }
        merge(second)?.let { return@withContext it.withCover(frenchCover.await() ?: runCatching { epagineCover(clean, patient) }.getOrNull()) }
        if ((first + second).all { it.isFailure }) throw IOException("Aucune source n'a répondu (pas de réseau ?)")
        null
    }

    /** La couverture ePagine (édition française, grande taille) passe avant celles de Google / Open Library. */
    private fun BookInfo.withCover(french: String?): BookInfo = if (french != null) copy(coverUrl = french) else this

    /**
     * Cherche seulement une couverture pour un ISBN (tome ajouté sans image, ou livre introuvable ailleurs).
     * Retourne l'adresse de l'image, ou null.
     */
    suspend fun findCover(isbn: String): String? = withContext(Dispatchers.IO) {
        val clean = normalizeIsbn(isbn)
        if (!isValidIsbn(clean)) return@withContext null
        runCatching { epagineCover(clean, patient) }.getOrNull()
            ?: "https://covers.openlibrary.org/b/isbn/$clean-L.jpg?default=false".takeIf { coverExists(patient, it) }
    }

    /** Taille exacte de l'image « pas de couverture » renvoyée par ePagine quand il n'a pas le livre. */
    private const val EPAGINE_PLACEHOLDER_SIZE = 151_627L

    internal fun epagineCover(isbn: String, client: OkHttpClient = patient): String? {
        if (isbn.length != 13) return null
        val url = "https://images.epagine.fr/${isbn.substring(10)}/${isbn}_1_75.jpg"
        val req = Request.Builder().url(url).head().header("User-Agent", "Mangatheque/1.1 (Android)").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val len = r.header("Content-Length")?.toLongOrNull() ?: return null
            val type = r.header("Content-Type").orEmpty()
            if (len == EPAGINE_PLACEHOLDER_SIZE || len < 5_000 || !type.startsWith("image")) return null
            return url
        }
    }

    private inline fun attempt(block: () -> BookInfo?): Result<BookInfo?> =
        try { Result.success(block()) } catch (e: Exception) { Result.failure(e) }

    /** Fusionne les réponses : la plus complète sert de base, les autres comblent les trous. */
    private fun merge(results: List<Result<BookInfo?>>): BookInfo? {
        // Priorité : Google Books, puis la BnF (données françaises : éditeur, auteur en alphabet latin), puis Open Library.
        val found = results.mapNotNull { it.getOrNull() }.filter { it.rawTitle.isNotBlank() }
            .sortedBy { when (it.source) { "Google Books" -> 0; "BnF" -> 1; else -> 2 } }
        if (found.isEmpty()) return null
        val base = found.firstOrNull { it.volumeNumber != null } ?: found.first()
        return base.copy(
            authors = base.authors.ifBlank { found.firstOrNull { it.authors.isNotBlank() }?.authors ?: "" },
            publisher = base.publisher.ifBlank { found.firstOrNull { it.publisher.isNotBlank() }?.publisher ?: "" },
            description = base.description.ifBlank { found.firstOrNull { it.description.isNotBlank() }?.description ?: "" },
            coverUrl = base.coverUrl ?: found.firstOrNull { it.coverUrl != null }?.coverUrl,
            categories = base.categories.ifEmpty { found.firstOrNull { it.categories.isNotEmpty() }?.categories ?: emptyList() },
            source = found.map { it.source }.distinct().joinToString(" + ")
        )
    }

    // ---------------------------------------------------------------- ISBN

    fun cleanIsbn(raw: String): String = raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()

    /** Nettoie et convertit un ISBN-10 valide en ISBN-13 (format des codes-barres). */
    fun normalizeIsbn(raw: String): String {
        val c = cleanIsbn(raw)
        return if (c.length == 10 && isValidIsbn(c)) toIsbn13(c) else c
    }

    fun isValidIsbn(raw: String): Boolean {
        val s = cleanIsbn(raw)
        return when (s.length) {
            13 -> s.all { it.isDigit() } && s.mapIndexed { i, c -> (c - '0') * (if (i % 2 == 0) 1 else 3) }.sum() % 10 == 0
            10 -> {
                if (!s.substring(0, 9).all { it.isDigit() }) return false
                var sum = 0
                for (i in 0 until 10) {
                    val v = if (i == 9 && s[i] == 'X') 10 else (s[i] - '0')
                    sum += v * (10 - i)
                }
                sum % 11 == 0
            }
            else -> false
        }
    }

    fun toIsbn13(isbn10: String): String {
        val core = "978" + isbn10.substring(0, 9)
        val sum = core.mapIndexed { i, c -> (c - '0') * (if (i % 2 == 0) 1 else 3) }.sum()
        return core + ((10 - sum % 10) % 10)
    }

    fun toIsbn10(isbn13: String): String? {
        if (isbn13.length != 13 || !isbn13.startsWith("978")) return null
        val core = isbn13.substring(3, 12)
        val sum = core.mapIndexed { i, c -> (c - '0') * (10 - i) }.sum()
        val check = (11 - sum % 11) % 11
        return core + (if (check == 10) "X" else check.toString())
    }

    // ---------------------------------------------------------------- Réseau

    /** null = introuvable (404) ; exception = problème réseau ou service saturé. */
    private fun get(client: OkHttpClient, url: String, accept: String = "application/json"): String? {
        val req = Request.Builder().url(url).header("User-Agent", "Mangatheque/1.1 (Android)").header("Accept", accept).build()
        client.newCall(req).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string()
        }
    }

    private fun googleBooks(isbn: String, isbn10: String?, client: OkHttpClient, query: String): BookInfo? {
        val url = "https://www.googleapis.com/books/v1/volumes?q=" + URLEncoder.encode(query, "UTF-8") + "&maxResults=5&country=FR"
        val body = get(client, url) ?: return null
        val items = JSONObject(body).optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            val info = items.getJSONObject(i).optJSONObject("volumeInfo") ?: continue
            val ids = info.optJSONArray("industryIdentifiers")
            val idList = if (ids == null) emptyList() else (0 until ids.length()).map { ids.getJSONObject(it).optString("identifier") }
            val matches = idList.any { it == isbn || (isbn10 != null && it == isbn10) }
            // Recherche « isbn: » : on fait confiance au premier résultat ; recherche libre : l'ISBN doit correspondre.
            if (!matches && !(query.startsWith("isbn:") && i == 0)) continue
            val title = listOfNotNull(info.optString("title").takeIf { it.isNotBlank() }, info.optString("subtitle").takeIf { it.isNotBlank() })
                .joinToString(" ")
            if (title.isBlank()) continue
            val authors = info.optJSONArray("authors")?.let { a -> (0 until a.length()).joinToString(", ") { a.getString(it) } } ?: ""
            val cats = info.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            val img = info.optJSONObject("imageLinks")?.let { it.optString("thumbnail").ifBlank { it.optString("smallThumbnail") } }
                ?.takeIf { it.isNotBlank() }
                ?.replace("http://", "https://")
                ?.replace("&edge=curl", "")
                ?.replace("zoom=1", "zoom=2")
            val parsed = TitleParser.parse(title)
            return BookInfo(
                isbn = isbn, rawTitle = title, seriesTitle = parsed.first, volumeNumber = parsed.second,
                authors = authors, publisher = info.optString("publisher"), description = info.optString("description"),
                coverUrl = img, categories = cats, source = "Google Books"
            )
        }
        return null
    }

    private fun openLibrary(isbn: String, client: OkHttpClient): BookInfo? {
        val body = get(client, "https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data") ?: return null
        val book = JSONObject(body).optJSONObject("ISBN:$isbn") ?: return null
        val title = listOfNotNull(book.optString("title").takeIf { it.isNotBlank() }, book.optString("subtitle").takeIf { it.isNotBlank() })
            .joinToString(" ")
        val authors = book.optJSONArray("authors")?.let { a -> (0 until a.length()).joinToString(", ") { a.getJSONObject(it).optString("name") } } ?: ""
        val publisher = book.optJSONArray("publishers")?.let { a -> if (a.length() > 0) a.getJSONObject(0).optString("name") else "" } ?: ""
        val subjects = book.optJSONArray("subjects")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } } ?: emptyList()
        val cover = book.optJSONObject("cover")?.let { it.optString("large").ifBlank { it.optString("medium") } }?.takeIf { it.isNotBlank() }
            ?: "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false".takeIf { coverExists(client, it) }
        val parsed = TitleParser.parse(title)
        return BookInfo(
            isbn = isbn, rawTitle = title, seriesTitle = parsed.first, volumeNumber = parsed.second,
            authors = authors, publisher = publisher, description = "",
            coverUrl = cover, categories = subjects.take(5), source = "Open Library"
        )
    }

    /** Fiche « édition » d'Open Library : parfois présente alors que l'API livres ne répond rien. */
    private fun openLibraryEdition(isbn: String, client: OkHttpClient): BookInfo? {
        val body = get(client, "https://openlibrary.org/isbn/$isbn.json") ?: return null
        val o = JSONObject(body)
        val title = listOfNotNull(o.optString("title").takeIf { it.isNotBlank() }, o.optString("subtitle").takeIf { it.isNotBlank() })
            .joinToString(" ")
        if (title.isBlank()) return null
        val publisher = o.optJSONArray("publishers")?.let { if (it.length() > 0) it.optString(0) else "" } ?: ""
        val coverId = o.optJSONArray("covers")?.let { if (it.length() > 0) it.optLong(0) else 0L } ?: 0L
        val parsed = TitleParser.parse(title)
        return BookInfo(
            isbn = isbn, rawTitle = title, seriesTitle = parsed.first, volumeNumber = parsed.second,
            authors = "", publisher = publisher, description = "",
            coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else null,
            categories = emptyList(), source = "Open Library"
        )
    }

    /** Catalogue de la Bibliothèque nationale de France : très complet pour les mangas édités en France. */
    private fun bnf(isbn: String, client: OkHttpClient): BookInfo? {
        val q = URLEncoder.encode("bib.isbn all \"$isbn\"", "UTF-8")
        val url = "https://catalogue.bnf.fr/api/SRU?version=1.2&operation=searchRetrieve&query=$q&recordSchema=unimarcxchange&maximumRecords=1"
        val body = get(client, url, accept = "application/xml") ?: return null
        return parseBnf(body, isbn)
    }

    /**
     * Lit une notice UNIMARC de la BnF :
     * 461 $t/$v ou 225 $a/$v = série et numéro, 200 $a/$e = titre du tome, 200 $f = auteur, 210/214 $c = éditeur.
     */
    internal fun parseBnf(body: String, isbn: String): BookInfo? {
        val f200 = field(body, "200").firstOrNull() ?: return null
        val volTitle = listOfNotNull(f200['a'], f200['e']).joinToString(" : ").trim()
        val f461 = field(body, "461").firstOrNull { !it['t'].isNullOrBlank() }
        val f225 = field(body, "225").firstOrNull { !it['a'].isNullOrBlank() }
        val series = (f461?.get('t') ?: f225?.get('a'))?.let { titleCase(TitleParser.clean(it)) }
        val number = listOfNotNull(f461?.get('v'), f225?.get('v'), f200['h'])
            .firstNotNullOfOrNull { Regex("""\d{1,3}""").find(it)?.value?.toIntOrNull() }
        val author = f200['f']?.substringBefore(" ; ")?.trim()?.takeIf { it.isNotBlank() }
            ?: field(body, "700").firstOrNull()?.let { listOfNotNull(it['b'], it['a']).joinToString(" ") }.orEmpty()
        val publisher = (field(body, "214") + field(body, "210")).firstNotNullOfOrNull { it['c'] }?.trim().orEmpty()
        val raw = when {
            series != null && number != null -> "$series, tome $number" + (if (volTitle.isNotBlank()) " : $volTitle" else "")
            series != null -> "$series : $volTitle"
            else -> volTitle
        }
        if (raw.isBlank()) return null
        val parsed = if (series != null) series to number else TitleParser.parse(volTitle)
        return BookInfo(
            isbn = isbn, rawTitle = raw, seriesTitle = parsed.first, volumeNumber = parsed.second,
            authors = author, publisher = publisher, description = "",
            coverUrl = null, categories = emptyList(), source = "BnF"
        )
    }

    /** Toutes les occurrences d'un champ UNIMARC, sous forme code de sous-champ → valeur. */
    private fun field(body: String, tag: String): List<Map<Char, String>> =
        Regex("""<mxc:datafield tag="$tag"[^>]*>(.*?)</mxc:datafield>""", RegexOption.DOT_MATCHES_ALL).findAll(body).map { m ->
            Regex("""<mxc:subfield code="(.)">(.*?)</mxc:subfield>""", RegexOption.DOT_MATCHES_ALL).findAll(m.groupValues[1])
                .associate { it.groupValues[1][0] to decode(it.groupValues[2]).trim() }
        }.toList()

    private fun decode(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")

    private val smallWords = setOf("de", "des", "du", "la", "le", "les", "et", "à", "au", "aux", "en", "un", "une", "x", "of", "the", "no")

    /** La BnF écrit « One piece » : on remet des majuscules (« One Piece », « L'Attaque des Titans »). */
    internal fun titleCase(s: String): String = s.split(" ").mapIndexed { i, w ->
        when {
            w.isEmpty() -> w
            i > 0 && w.lowercase() in smallWords -> w.lowercase()
            w.length > 2 && w[1] == '\'' -> w.substring(0, 2).uppercase() + w.substring(2).replaceFirstChar { it.uppercase() }
            else -> w.replaceFirstChar { it.uppercase() }
        }
    }.joinToString(" ")

    private fun coverExists(client: OkHttpClient, url: String): Boolean = runCatching {
        val req = Request.Builder().url(url).head().build()
        client.newCall(req).execute().use { it.isSuccessful && (it.header("Content-Length")?.toLongOrNull() ?: 1000) > 500 }
    }.getOrDefault(false)
}

/** Découpe « One Piece, Tome 12 », « Jujutsu kaisen. 3, … » ou « Naruto 72 » en (série, numéro). */
object TitleParser {
    private val keyword = Regex("""^(.*)[\s,.:\-–—(]+(?:tome|t\.?|vol\.?|volume|n°)\s*0*(\d{1,3})\b.*$""", RegexOption.IGNORE_CASE)
    private val trailing = Regex("""^(.*?\D)[\s,.:\-–—#]+0*(\d{1,3})\s*(?:[,.:\-–—(/].*)?$""")
    private val edition = Regex(
        """\s*[-–—:(]\s*(?:(?:nouvelle|perfect|ultimate|deluxe|collector)\s+)?[ÉéEe]dition(?:\s+(?:originale|deluxe|collector|limitée|double))?\s*\)?\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(title: String): Pair<String, Int?> {
        val t = title.trim().replace(Regex("\\s+"), " ")
        for (p in listOf(keyword, trailing)) {
            val m = p.find(t) ?: continue
            val name = clean(m.groupValues[1])
            val num = m.groupValues[2].toIntOrNull()
            if (name.isNotBlank() && num != null && num > 0) return name to num
        }
        return clean(t) to null
    }

    /** Retire la ponctuation finale et les mentions d'édition (« - Édition originale »…). */
    fun clean(name: String): String {
        var n = name.trim().trimEnd(',', ':', '-', '–', '—', '.', ' ', '(')
        repeat(2) { n = n.replace(edition, "").trim().trimEnd(',', ':', '-', '–', '—', '.', ' ') }
        return n
    }
}
