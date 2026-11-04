package fr.mangatheque.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LookupTest {

    @Test fun titres_decoupes_en_serie_et_numero() {
        assertEquals("One Piece" to 12, TitleParser.parse("One Piece, Tome 12"))
        assertEquals("One Piece" to 1, TitleParser.parse("One Piece - Édition originale - Tome 01"))
        assertEquals("Naruto" to 72, TitleParser.parse("Naruto 72"))
        assertEquals("Jujutsu kaisen" to 3, TitleParser.parse("Jujutsu kaisen. 3, Le fléau"))
        assertEquals("Blue Lock" to 30, TitleParser.parse("Blue Lock T30"))
        assertEquals("Kaiju No. 8" to 3, TitleParser.parse("Kaiju No. 8 T03"))
        assertEquals("Spy x Family" to 5, TitleParser.parse("Spy x Family Vol. 5"))
        assertEquals("Monster" to null, TitleParser.parse("Monster"))
    }

    @Test fun isbn_valides_et_conversions() {
        assertTrue(BookLookup.isValidIsbn("9782723488525"))
        assertFalse(BookLookup.isValidIsbn("9782723488526"))
        assertEquals("2723488527", BookLookup.toIsbn10("9782723488525"))
        assertEquals("9782723488525", BookLookup.toIsbn13("2723488527"))
        assertEquals("9782723488525", BookLookup.normalizeIsbn("2-7234-8852-7"))
    }

    @Test fun notice_bnf_lue_correctement() {
        val xml = javaClass.getResource("/bnf_onepiece_1.xml")!!.readText()
        val b = BookLookup.parseBnf(xml, "9782723488525")
        assertNotNull(b)
        assertEquals("One Piece", b!!.seriesTitle)
        assertEquals(1, b.volumeNumber)
        assertEquals("Glénat", b.publisher)
        assertTrue(b.authors, b.authors.contains("Oda"))
        println("BnF -> ${b.rawTitle} | ${b.authors} | ${b.publisher}")
    }

    @Test fun majuscules_des_titres_bnf() {
        assertEquals("One Piece", BookLookup.titleCase("One piece"))
        assertEquals("L'Attaque des Titans", BookLookup.titleCase("L'attaque des titans"))
        assertEquals("Spy x Family", BookLookup.titleCase("Spy x family"))
    }

    @Test fun terme_de_recherche_nettoye() {
        assertEquals("One Piece", MangaDb.searchTerm("One Piece - Édition originale"))
        assertEquals("Naruto", MangaDb.searchTerm("Naruto (Kana)"))
    }

    /** Tests réseau réels : lancés seulement avec la variable NET_TESTS=1. */
    @Test fun reseau_recherche_isbn_reelle() = runBlocking {
        assumeTrue(System.getenv("NET_TESTS") != null)
        val stages = mutableListOf<Int>()
        val t0 = System.currentTimeMillis()
        val b = BookLookup.lookup("9782723488525") { stages += it }
        println("ISBN One Piece 1 -> $b (étapes $stages, ${System.currentTimeMillis() - t0} ms)")
        assertNotNull(b)
        assertEquals("One Piece", b!!.seriesTitle)
        assertEquals(1, b.volumeNumber)
    }

    @Test fun reseau_infos_serie_reelles() = runBlocking {
        assumeTrue(System.getenv("NET_TESTS") != null)
        for (title in listOf("One Piece", "Naruto", "Jujutsu Kaisen", "L'Attaque des Titans", "Frieren")) {
            val r = MangaDb.search(title)
            val info = r.getOrNull()
            println("$title -> $info ${r.exceptionOrNull() ?: ""}")
            assertNotNull(title, info)
            assertNotNull(title, info!!.imageUrl)
        }
        val naruto = MangaDb.search("Naruto").getOrNull()!!
        assertEquals(true, naruto.finished)
        assertNull(MangaDb.search("zzqxw introuvable 12345").getOrNull())
    }
}
