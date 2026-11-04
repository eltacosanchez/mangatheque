package fr.mangatheque.app.data

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Traduction anglais → français directement sur le téléphone (Google ML Kit).
 * Le modèle (~30 Mo) se télécharge une seule fois, ensuite tout marche hors connexion.
 */
object FrenchTranslator {
    private val translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.FRENCH)
                .build()
        )
    }

    /** Retourne le texte traduit, ou null si la traduction est impossible (pas de réseau au premier usage…). */
    suspend fun toFrench(text: String): String? {
        if (text.isBlank()) return null
        return withTimeoutOrNull(60_000) {
            if (!translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).succeeded()) return@withTimeoutOrNull null
            // Paragraphe par paragraphe : meilleure qualité qu'un seul gros bloc.
            text.split("\n").map { line ->
                if (line.isBlank()) line else translator.translate(line).textOrNull() ?: line
            }.joinToString("\n")
        }
    }

    private suspend fun Task<*>.succeeded(): Boolean = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(true) }
        addOnFailureListener { cont.resume(false) }
    }

    private suspend fun Task<String>.textOrNull(): String? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
    }
}
