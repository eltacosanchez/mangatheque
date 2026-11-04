package fr.mangatheque.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Tomes parus en France d'une série, d'après le catalogue de la BnF (dépôt légal) :
 * numéro de tome → ISBN. Sert à retrouver les couvertures, le nombre de tomes parus
 * et les tomes manquants.
 */
object Editions {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, Map<Int, String>>()

    private data class Candidate(val number: Int, val isbn: String, val year: Int)

    /**
     * [title] : titre de la série ; [publisher] : éditeur connu (facultatif, affine la recherche) ;
     * [knownIsbns] : ISBN déjà possédés, pour choisir la même édition (ancienne / nouvelle).
     * Retourne une carte vide si la BnF ne trouve rien.
     */
    suspend fun frenchVolumes(title: String, publisher: String, knownIsbns: List<String>): Result<Map<Int, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = Repository.normalize(title) + "|" + Repository.normalize(publisher) + "|" + knownIsbns.sorted().joinToString()
                cache[key]?.let { return@runCatching it }
                val target = Repository.normalize(title)
                if (target.isBlank()) return@runCatching emptyMap()
                val cleanTitle = title.replace("\"", " ").trim()
                val pubWord = publisher.split(Regex("""[\s(]""")).firstOrNull { it.length >= 3 }?.replace("\"", "")
                val query = "bib.title all \"$cleanTitle\"" + (pubWord?.let { " and bib.publisher all \"$it\"" } ?: "")

                val found = mutableListOf<Candidate>()
                var start = 1
                while (start in 1..1000) {
                    val url = "https://catalogue.bnf.fr/api/SRU?version=1.2&operation=searchRetrieve&query=" +
                        URLEncoder.encode(query, "UTF-8") + "&recordSchema=unimarcxchange&maximumRecords=200&startRecord=$start"
                    val body = client.newCall(Request.Builder().url(url).header("User-Agent", "Mangatheque/1.3 (Android)").build())
                        .execute().use { r -> if (!r.isSuccessful) error("BnF ${r.code}"); r.body?.string().orEmpty() }
                    found += parse(body, target)
                    start = Regex("""<srw:nextRecordPosition>(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                }
                choose(found, knownIsbns).also { cache[key] = it }
            }
        }


    /** Lit chaque notice : série (461 ou 225), numéro, ISBN (010) et année (210/214). */
    private fun parse(body: String, target: String): List<Candidate> =
        body.split("<srw:record>").drop(1).mapNotNull { rec ->
            val f461 = field(rec, "461"); val f225 = field(rec, "225")
            val series = f461['t'] ?: f225['a'] ?: return@mapNotNull null
            if (Repository.normalize(TitleParser.clean(series)) != target) return@mapNotNull null
            val number = listOfNotNull(f461['v'], f225['v']).firstNotNullOfOrNull { Regex("""\d{1,3}""").find(it)?.value?.toIntOrNull() }
                ?: return@mapNotNull null
            val isbn = BookLookup.normalizeIsbn(field(rec, "010")['a'] ?: return@mapNotNull null)
            if (!BookLookup.isValidIsbn(isbn) || isbn.length != 13) return@mapNotNull null
            val year = listOf("214", "210").firstNotNullOfOrNull { t -> field(rec, t)['d']?.let { Regex("""(19|20)\d{2}""").find(it)?.value?.toIntOrNull() } } ?: 0
            Candidate(number, isbn, year)
        }

    /** Pour chaque numéro, garde l'édition la plus proche de celle possédée, sinon la plus récente. */
    private fun choose(found: List<Candidate>, known: List<String>): Map<Int, String> {
        fun closeness(isbn: String) = known.maxOfOrNull { it.commonPrefixWith(isbn).length } ?: 0
        return found.groupBy { it.number }
            .mapValues { (_, c) -> c.maxWith(compareBy<Candidate>({ closeness(it.isbn) }, { it.year })).isbn }
            .toSortedMap()
    }

    private fun field(rec: String, tag: String): Map<Char, String> {
        val m = Regex("""<mxc:datafield tag="$tag"[^>]*>(.*?)</mxc:datafield>""", RegexOption.DOT_MATCHES_ALL).find(rec) ?: return emptyMap()
        return Regex("""<mxc:subfield code="(.)">(.*?)</mxc:subfield>""", RegexOption.DOT_MATCHES_ALL).findAll(m.groupValues[1])
            .associate { it.groupValues[1][0] to it.groupValues[2].replace("&amp;", "&").trim() }
    }
}
