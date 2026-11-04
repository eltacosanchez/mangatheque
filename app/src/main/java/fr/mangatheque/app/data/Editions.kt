package fr.mangatheque.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Tomes parus en France d'une série : numéro de tome → ISBN.
 *  1. Catalogue de la BnF (dépôt légal) : quasiment tous les tomes.
 *  2. Place des Libraires : comble les trous de la BnF et repère le tome suivant s'il existe déjà.
 * Les coffrets, artbooks, romans et éditions spéciales sont ignorés.
 */
object Editions {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, Map<Int, String>>()

    /** [priority] : 2 = série bien renseignée (461), 1 = collection (225), 0 = déduite du titre (200). */
    private data class Candidate(val number: Int, val isbn: String, val year: Int, val priority: Int)

    private val excluded = Regex("""\b(coffret|coffre|collector|artbook|art book|fanbook|guide|roman|novel|film|anime comics|magazine|vivre card|color walk|pack)\b""", RegexOption.IGNORE_CASE)

    /**
     * [title] : titre de la série ; [publisher] : éditeur connu (affine la recherche) ;
     * [knownIsbns] : ISBN déjà possédés, pour choisir la même édition (ancienne / nouvelle).
     */
    suspend fun frenchVolumes(title: String, publisher: String, knownIsbns: List<String>): Result<Map<Int, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = Repository.normalize(title) + "|" + Repository.normalize(publisher) + "|" + knownIsbns.sorted().joinToString()
                cache[key]?.let { return@runCatching it }
                val target = Repository.normalize(title)
                if (target.isBlank()) return@runCatching emptyMap()

                val map = choose(bnf(title, publisher, target), knownIsbns).toMutableMap()
                val refs = knownIsbns + map.values

                // Trous dans la numérotation (notices absentes de la BnF) : on demande au libraire.
                val max = map.keys.maxOrNull() ?: 0
                (1..max).filter { it !in map }.take(12).forEach { n ->
                    runCatching { bookshop(title, n, refs) }.getOrNull()?.let { map[n] = it }
                }
                // Le tome suivant existe-t-il déjà (sorti ou annoncé) ?
                var next = max + 1
                while (next <= max + 3) {
                    val isbn = runCatching { bookshop(title, next, refs) }.getOrNull() ?: break
                    map[next] = isbn; next++
                }
                map.toSortedMap().also { cache[key] = it }
            }
        }

    // ------------------------------------------------------------------ BnF

    private fun bnf(title: String, publisher: String, target: String): List<Candidate> {
        val cleanTitle = title.replace("\"", " ").trim()
        val pubWord = publisher.split(Regex("""[\s(]""")).firstOrNull { it.length >= 3 }?.replace("\"", "")
        val query = "bib.title all \"$cleanTitle\"" + (pubWord?.let { " and bib.publisher all \"$it\"" } ?: "")
        val found = mutableListOf<Candidate>()
        var start = 1
        while (start in 1..1000) {
            val url = "https://catalogue.bnf.fr/api/SRU?version=1.2&operation=searchRetrieve&query=" +
                URLEncoder.encode(query, "UTF-8") + "&recordSchema=unimarcxchange&maximumRecords=200&startRecord=$start"
            val body = get(url) ?: break
            found += parseBnf(body, target)
            start = Regex("""<srw:nextRecordPosition>(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        return found
    }

    /** Lit chaque notice : la série peut être en 461, en 225 ou directement dans le titre (200). */
    private fun parseBnf(body: String, target: String): List<Candidate> =
        body.split("<srw:record>").drop(1).mapNotNull { rec ->
            val f200 = field(rec, "200"); val f225 = field(rec, "225"); val f461 = field(rec, "461"); val f010 = field(rec, "010")
            val whole = f200.values.joinToString(" ") + " " + f010['b'].orEmpty()
            if (excluded.containsMatchIn(whole)) return@mapNotNull null
            val fromTitle = f200['a']?.let { TitleParser.parse(it) }
            val (priority, number) = when {
                f461['t']?.let { same(it, target) } == true -> 2 to listOfNotNull(f461['v'], f225['v'], f200['h']).firstNotNullOfOrNull(::num)
                f225['a']?.let { same(it, target) } == true -> 1 to listOfNotNull(f225['v'], f200['h']).firstNotNullOfOrNull(::num)
                fromTitle != null && same(fromTitle.first, target) -> 0 to (f200['h']?.let(::num) ?: fromTitle.second)
                else -> return@mapNotNull null
            }
            if (number == null || number <= 0) return@mapNotNull null
            val isbn = BookLookup.normalizeIsbn(f010['a'] ?: return@mapNotNull null)
            if (!BookLookup.isValidIsbn(isbn) || isbn.length != 13) return@mapNotNull null
            val year = listOf("214", "210").firstNotNullOfOrNull { t -> field(rec, t)['d']?.let { Regex("""(19|20)\d{2}""").find(it)?.value?.toIntOrNull() } } ?: 0
            Candidate(number, isbn, year, priority)
        }

    // ------------------------------------------------------------------ Place des Libraires

    /** Cherche « <série> tome N » chez le libraire et renvoie l'ISBN du bon tome (pas d'un coffret). */
    private fun bookshop(title: String, n: Int, refs: List<String>): String? {
        val url = "https://www.placedeslibraires.fr/listeliv.php?base=allbooks&mots_recherche=" + URLEncoder.encode("$title tome $n", "UTF-8")
        val html = get(url, browser = true) ?: return null
        val slugTitle = Repository.normalize(title).replace(' ', '-')
        val tome = Regex("""(^|-)(tome|t|vol|volume)-0*$n(-|$)""")
        val hits = Regex("""href="/livre/(\d{13})-([^"/]+)/?"""").findAll(html)
            .map { it.groupValues[1] to it.groupValues[2] }
            .filter { (isbn, slug) -> slug.contains(slugTitle) && tome.containsMatchIn(slug) && !excluded.containsMatchIn(slug.replace('-', ' ')) && BookLookup.isValidIsbn(isbn) }
            .map { it.first }.distinct().toList()
        if (hits.isEmpty()) return null
        return hits.maxBy { isbn -> refs.maxOfOrNull { it.commonPrefixWith(isbn).length } ?: 0 }
    }

    // ------------------------------------------------------------------ Outils

    /** Pour chaque numéro : la notice la mieux renseignée, puis l'édition la plus proche de celle possédée, puis la plus récente. */
    private fun choose(found: List<Candidate>, known: List<String>): Map<Int, String> {
        fun closeness(isbn: String) = known.maxOfOrNull { it.commonPrefixWith(isbn).length } ?: 0
        return found.groupBy { it.number }
            .mapValues { (_, c) -> c.maxWith(compareBy<Candidate>({ closeness(it.isbn) }, { it.priority }, { it.year })).isbn }
    }

    private fun same(series: String, target: String) = Repository.normalize(TitleParser.clean(series)) == target
    private fun num(s: String): Int? = Regex("""\d{1,3}""").find(s)?.value?.toIntOrNull()

    private fun get(url: String, browser: Boolean = false): String? {
        val ua = if (browser) "Mozilla/5.0 (Android) Mangatheque/1.3" else "Mangatheque/1.3 (Android)"
        client.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return r.body?.string()
        }
    }

    private fun field(rec: String, tag: String): Map<Char, String> {
        val m = Regex("""<mxc:datafield tag="$tag"[^>]*>(.*?)</mxc:datafield>""", RegexOption.DOT_MATCHES_ALL).find(rec) ?: return emptyMap()
        return Regex("""<mxc:subfield code="(.)">(.*?)</mxc:subfield>""", RegexOption.DOT_MATCHES_ALL).findAll(m.groupValues[1])
            .associate { it.groupValues[1][0] to it.groupValues[2].replace("&amp;", "&").trim() }
    }
}
