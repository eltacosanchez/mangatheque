package fr.mangatheque.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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

object BookLookup {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Interroge Google Books ET Open Library en même temps (et non l'un après l'autre),
     * puis fusionne : Google Books en priorité, Open Library complète les trous.
     * Retourne null si l'ISBN est inconnu des deux.
     */
    suspend fun lookup(isbn: String): BookInfo? = withContext(Dispatchers.IO) {
        val clean = cleanIsbn(isbn)
        val (google, open) = coroutineScope {
            val g = async { runCatching { googleBooks(clean) }.getOrNull() }
            val o = async { runCatching { openLibrary(clean) }.getOrNull() }
            g.await() to o.await()
        }
        when {
            google != null && open != null -> google.copy(
                coverUrl = google.coverUrl ?: open.coverUrl,
                publisher = google.publisher.ifBlank { open.publisher },
                authors = google.authors.ifBlank { open.authors }
            )
            google != null -> google
            else -> open
        }
    }

    fun cleanIsbn(raw: String): String = raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()

    fun isValidIsbn(raw: String): Boolean {
        val s = cleanIsbn(raw)
        return when (s.length) {
            13 -> s.all { it.isDigit() } && s.mapIndexed { i, c -> (c - '0') * (if (i % 2 == 0) 1 else 3) }.sum() % 10 == 0
            10 -> {
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

    private fun get(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", "Mangatheque/1.0 (Android)").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            return r.body?.string()
        }
    }

    private fun googleBooks(isbn: String): BookInfo? {
        val body = get("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn&maxResults=1") ?: return null
        val json = JSONObject(body)
        val items = json.optJSONArray("items") ?: return null
        if (items.length() == 0) return null
        val info = items.getJSONObject(0).optJSONObject("volumeInfo") ?: return null
        val title = listOfNotNull(info.optString("title").takeIf { it.isNotBlank() }, info.optString("subtitle").takeIf { it.isNotBlank() })
            .joinToString(" ")
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

    private fun openLibrary(isbn: String): BookInfo? {
        val body = get("https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data") ?: return null
        val json = JSONObject(body)
        val book = json.optJSONObject("ISBN:$isbn") ?: return null
        val title = listOfNotNull(book.optString("title").takeIf { it.isNotBlank() }, book.optString("subtitle").takeIf { it.isNotBlank() })
            .joinToString(" ")
        val authors = book.optJSONArray("authors")?.let { a -> (0 until a.length()).joinToString(", ") { a.getJSONObject(it).optString("name") } } ?: ""
        val publisher = book.optJSONArray("publishers")?.let { a -> if (a.length() > 0) a.getJSONObject(0).optString("name") else "" } ?: ""
        val subjects = book.optJSONArray("subjects")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } } ?: emptyList()
        val cover = book.optJSONObject("cover")?.let { it.optString("large").ifBlank { it.optString("medium") } }?.takeIf { it.isNotBlank() }
            ?: "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false".takeIf { coverExists(it) }
        val parsed = TitleParser.parse(title)
        return BookInfo(
            isbn = isbn, rawTitle = title, seriesTitle = parsed.first, volumeNumber = parsed.second,
            authors = authors, publisher = publisher, description = "",
            coverUrl = cover, categories = subjects.take(5), source = "Open Library"
        )
    }

    private fun coverExists(url: String): Boolean = runCatching {
        val req = Request.Builder().url(url).head().build()
        client.newBuilder().callTimeout(4, TimeUnit.SECONDS).build()
            .newCall(req).execute().use { it.isSuccessful && (it.header("Content-Length")?.toLongOrNull() ?: 1000) > 500 }
    }.getOrDefault(false)
}

/** Découpe « One Piece, Tome 12 » en ("One Piece", 12). */
object TitleParser {
    private val patterns = listOf(
        Regex("""^(.*?)[\s,:\-–—]*(?:tome|vol\.?|volume|t\.?|#|n°|no\.?)\s*(\d{1,3})\b.*$""", RegexOption.IGNORE_CASE),
        Regex("""^(.*?)[\s,:\-–—]+(\d{1,3})\s*$"""),
        Regex("""^(.*?)[\s,:\-–—]+(\d{1,3})\s*[:\-–—(].*$""")
    )

    fun parse(title: String): Pair<String, Int?> {
        val t = title.trim()
        for (p in patterns) {
            val m = p.find(t) ?: continue
            val name = m.groupValues[1].trim().trimEnd(',', ':', '-', '–', '—', ' ')
            val num = m.groupValues[2].toIntOrNull()
            if (name.isNotBlank() && num != null) return name to num
        }
        return t to null
    }
}
