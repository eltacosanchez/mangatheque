package fr.mangatheque.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Infos d'une série trouvées sur AniList (ou MyAnimeList en secours). */
data class MangaInfo(
    val title: String,
    /** Image de l'œuvre pour le fond de la fiche (bannière large si possible, sinon couverture). */
    val imageUrl: String?,
    /** true = terminée, false = en cours, null = inconnu. */
    val finished: Boolean?,
    /** Nombre de tomes au Japon (connu surtout pour les séries terminées). */
    val volumes: Int?,
    val genres: List<String>
)

/**
 * Base de données de mangas : AniList d'abord (rapide, fiable, images larges),
 * MyAnimeList via Jikan en secours.
 */
object MangaDb {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(14, TimeUnit.SECONDS)
        .build()

    private val genreFr = mapOf(
        "Shounen" to "Shōnen", "Seinen" to "Seinen", "Shoujo" to "Shōjo", "Josei" to "Josei", "Kids" to "Jeunesse",
        "Action" to "Action", "Adventure" to "Aventure", "Comedy" to "Comédie", "Drama" to "Drame", "Fantasy" to "Fantasy",
        "Horror" to "Horreur", "Mystery" to "Mystère", "Romance" to "Romance", "Sci-Fi" to "Science-fiction",
        "Slice of Life" to "Tranche de vie", "Sports" to "Sport", "Supernatural" to "Surnaturel", "Suspense" to "Thriller",
        "Thriller" to "Thriller", "Psychological" to "Psychologique", "Mahou Shoujo" to "Magical girl", "Mecha" to "Mecha",
        "Music" to "Musique", "Gourmet" to "Cuisine", "Boys Love" to "Boys Love", "Girls Love" to "Girls Love", "Ecchi" to "Ecchi",
        "Martial Arts" to "Arts martiaux", "Historical" to "Historique", "School" to "École", "Military" to "Militaire",
        "Isekai" to "Isekai", "Super Power" to "Super-pouvoirs", "Time Travel" to "Voyage dans le temps", "Mythology" to "Mythologie",
        "Samurai" to "Samouraï", "Detective" to "Policier", "Gore" to "Gore", "Survival" to "Survie", "Team Sports" to "Sport",
        "Combat Sports" to "Sport", "Urban Fantasy" to "Fantasy urbaine", "Space" to "Espace", "Vampire" to "Vampires",
        "Iyashikei" to "Feel-good", "Reincarnation" to "Réincarnation", "Video Games" to "Jeu vidéo", "Video Game" to "Jeu vidéo",
        "Organized Crime" to "Crime organisé", "Medicine" to "Médical", "Medical" to "Médical", "Delinquents" to "Délinquants",
        "Parody" to "Parodie", "Gag Humor" to "Humour", "Pirates" to "Pirates", "Ninja" to "Ninja", "Demons" to "Démons",
        "Zombie" to "Zombies", "Post-Apocalyptic" to "Post-apocalyptique", "Dystopian" to "Dystopie", "Cooking" to "Cuisine"
    )

    /** Succès(null) = rien trouvé ; échec = aucune base joignable (on pourra réessayer plus tard). */
    suspend fun search(title: String): Result<MangaInfo?> = withContext(Dispatchers.IO) {
        val queries = listOf(searchTerm(title), title.trim()).distinct().filter { it.length >= 2 }
        var anyAnswered = false
        var lastError: Exception? = null
        for (q in queries) {
            try { aniList(q, title)?.let { return@withContext Result.success(it) }; anyAnswered = true } catch (e: Exception) { lastError = e }
        }
        for (q in queries.take(1)) {
            try { jikan(q, title)?.let { return@withContext Result.success(it) }; anyAnswered = true } catch (e: Exception) { lastError = e }
        }
        if (anyAnswered) Result.success(null) else Result.failure(lastError ?: IOException("Pas de réseau"))
    }

    /** « One Piece - Édition originale » → « One Piece » ; « Naruto (Kana) » → « Naruto ». */
    fun searchTerm(title: String): String =
        TitleParser.clean(title.split(Regex("""\s[-–—:]\s|\(""")).first()).trim()

    // ------------------------------------------------------------------ AniList

    private const val ANILIST_QUERY = """
        query(${'$'}s: String) {
          Page(perPage: 8) {
            media(search: ${'$'}s, type: MANGA, isAdult: false, sort: SEARCH_MATCH) {
              title { romaji english native }
              synonyms format status volumes genres popularity
              tags { name rank isMediaSpoiler }
              coverImage { extraLarge large }
              bannerImage
            }
          }
        }"""

    private fun aniList(q: String, original: String): MangaInfo? {
        val payload = JSONObject().put("query", ANILIST_QUERY).put("variables", JSONObject().put("s", q)).toString()
        val req = Request.Builder().url("https://graphql.anilist.co")
            .header("Accept", "application/json").header("User-Agent", "Mangatheque-Android")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val body = client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("AniList a répondu ${r.code}")
            r.body?.string() ?: return null
        }
        val media = JSONObject(body).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: return null
        val targets = targets(original, q)
        var best: JSONObject? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until media.length()) {
            val o = media.getJSONObject(i)
            val t = o.optJSONObject("title")
            val names = mutableListOf(t?.optString("romaji").orEmpty(), t?.optString("english").orEmpty(), t?.optString("native").orEmpty())
            o.optJSONArray("synonyms")?.let { s -> for (j in 0 until s.length()) names += s.optString(j) }
            var score = matchScore(names, targets)
            when (o.optString("format")) { "MANGA" -> score += 10; "NOVEL" -> score -= 30 }
            score += (o.optInt("popularity") / 20000).coerceAtMost(15) // départage par popularité
            score -= i
            if (score > bestScore) { bestScore = score; best = o }
        }
        val o = best ?: return null
        if (bestScore < 20) return null
        val image = o.optString("bannerImage").takeIf { it.isNotBlank() && it != "null" }
            ?: o.optJSONObject("coverImage")?.let { c -> c.optString("extraLarge").ifBlank { c.optString("large") } }?.takeIf { it.isNotBlank() }
        val finished = when (o.optString("status")) {
            "FINISHED", "CANCELLED" -> true
            "RELEASING", "HIATUS", "NOT_YET_RELEASED" -> false
            else -> null
        }
        val volumes = if (o.isNull("volumes")) null else o.optInt("volumes").takeIf { it > 0 }
        val demographics = setOf("Shounen", "Seinen", "Shoujo", "Josei", "Kids")
        val tags = o.optJSONArray("tags")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }
                .filter { !it.optBoolean("isMediaSpoiler") && it.optInt("rank") >= 60 }
                .map { it.optString("name") }
        } ?: emptyList()
        val genres = (tags.filter { it in demographics } + strings(o.optJSONArray("genres")) + tags.filter { it !in demographics })
            .mapNotNull { genreFr[it] }.distinct().take(7)
        val t = o.optJSONObject("title")
        return MangaInfo(t?.optString("english")?.takeIf { it.isNotBlank() && it != "null" } ?: t?.optString("romaji").orEmpty(), image, finished, volumes, genres)
    }

    // ------------------------------------------------------------------ Jikan (MyAnimeList)

    private fun jikan(q: String, original: String): MangaInfo? {
        val url = "https://api.jikan.moe/v4/manga?q=" + URLEncoder.encode(q, "UTF-8") + "&limit=10&order_by=members&sort=desc&sfw=true"
        val req = Request.Builder().url(url).header("User-Agent", "Mangatheque-Android").build()
        val body = client.newCall(req).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) throw IOException("MyAnimeList a répondu ${r.code}")
            r.body?.string() ?: return null
        }
        val arr = JSONObject(body).optJSONArray("data") ?: return null
        val targets = targets(original, q)
        var best: JSONObject? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val names = mutableListOf(o.optString("title"), o.optString("title_english"), o.optString("title_japanese"))
            o.optJSONArray("titles")?.let { t -> for (j in 0 until t.length()) names += t.getJSONObject(j).optString("title") }
            var score = matchScore(names, targets)
            when (o.optString("type")) { "Manga" -> score += 10; "Light Novel", "Novel" -> score -= 30 }
            score -= i
            if (score > bestScore) { bestScore = score; best = o }
        }
        val o = best ?: return null
        if (bestScore < 20) return null
        val image = o.optJSONObject("images")?.let { im ->
            im.optJSONObject("webp")?.optString("large_image_url")?.takeIf { it.isNotBlank() } ?: im.optJSONObject("jpg")?.optString("large_image_url")
        }?.takeIf { it.isNotBlank() }
        val finished = when (o.optString("status")) {
            "Finished", "Discontinued" -> true
            "Publishing", "On Hiatus" -> false
            else -> null
        }
        val volumes = if (o.isNull("volumes")) null else o.optInt("volumes").takeIf { it > 0 }
        fun names(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it).optString("name") }
        val genres = (names(o.optJSONArray("demographics")) + names(o.optJSONArray("genres")) + names(o.optJSONArray("themes")))
            .mapNotNull { genreFr[it] }.distinct().take(7)
        return MangaInfo(o.optString("title"), image, finished, volumes, genres)
    }

    // ------------------------------------------------------------------ Outils

    private fun targets(original: String, q: String) =
        listOf(Repository.normalize(original), Repository.normalize(q)).filter { it.isNotBlank() }.distinct()

    private fun matchScore(names: List<String>, targets: List<String>): Int {
        val norm = names.filter { it.isNotBlank() && it != "null" }.map { Repository.normalize(it) }.filter { it.isNotBlank() }
        return when {
            norm.any { n -> targets.any { it == n } } -> 100
            norm.any { n -> targets.any { n.startsWith(it) || it.startsWith(n) } } -> 60
            norm.any { n -> targets.any { n.contains(it) } } -> 40
            else -> 0
        }
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
}
