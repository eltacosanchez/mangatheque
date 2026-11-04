package fr.mangatheque.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Statut de lecture d'un tome, coché par l'utilisateur. */
enum class ReadStatus(val label: String) {
    A_LIRE("À lire"),
    EN_COURS("En cours"),
    LU("Lu");

    companion object {
        fun from(s: String?): ReadStatus = entries.firstOrNull { it.name == s } ?: A_LIRE
    }
}

@Entity(tableName = "series")
data class Series(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val publisher: String = "",
    /** Genres séparés par des virgules. */
    val genres: String = "",
    /** true = série en cours de publication, false = terminée. */
    val ongoing: Boolean = true,
    /** Nombre de tomes parus (si connu). Sert à calculer les tomes manquants. */
    val totalVolumes: Int? = null,
    val synopsis: String = "",
    /** Chemin local d'une couverture de série (sinon on utilise celle du 1er tome). */
    val coverPath: String? = null,
    /** Préfixe ISBN commun aux tomes (aide à reconnaître les tomes suivants). */
    val isbnPrefix: String = "",
    /** Graine pour la couleur de couverture générée. */
    val colorSeed: Int = 0,
    /** Image de fond de la fiche : null = à chercher automatiquement, "" = aucune, sinon chemin du fichier. */
    val bannerPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val genreList: List<String> get() = genres.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(
    tableName = "volumes",
    foreignKeys = [ForeignKey(
        entity = Series::class,
        parentColumns = ["id"],
        childColumns = ["seriesId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("seriesId"), Index("isbn")]
)
data class Volume(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val number: Int,
    val isbn: String = "",
    /** Titre spécifique du tome (facultatif). */
    val title: String = "",
    /** Chemin local (fichier) ou URL http de la couverture. */
    val coverPath: String? = null,
    val readStatus: String = ReadStatus.A_LIRE.name,
    /** Note perso de 0 (aucune) à 5 étoiles. */
    val rating: Int = 0,
    val note: String = "",
    val readAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
) {
    val status: ReadStatus get() = ReadStatus.from(readStatus)
}

/** Photo d'un passage préféré, avec un petit mot et le numéro de page. */
@Entity(
    tableName = "moments",
    foreignKeys = [ForeignKey(
        entity = Volume::class,
        parentColumns = ["id"],
        childColumns = ["volumeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("volumeId")]
)
data class Moment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val volumeId: Long,
    val photoPath: String,
    val caption: String = "",
    val page: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class SeriesWithVolumes(
    @Embedded val series: Series,
    @Relation(parentColumn = "id", entityColumn = "seriesId")
    val volumes: List<Volume>
) {
    val sortedVolumes: List<Volume> get() = volumes.sortedBy { it.number }
    val ownedNumbers: Set<Int> get() = volumes.map { it.number }.toSet()
    val readCount: Int get() = volumes.count { it.status == ReadStatus.LU }
    val readingCount: Int get() = volumes.count { it.status == ReadStatus.EN_COURS }

    /** Nombre de tomes parus : la valeur saisie, ou au minimum le plus grand tome possédé. */
    val published: Int get() = maxOf(series.totalVolumes ?: 0, volumes.maxOfOrNull { it.number } ?: 0)

    val missingNumbers: List<Int> get() = (1..published).filter { it !in ownedNumbers }

    /** Statut global de la série, calculé. */
    val overallStatus: ReadStatus
        get() = when {
            volumes.isNotEmpty() && readCount == volumes.size -> ReadStatus.LU
            readCount > 0 || readingCount > 0 -> ReadStatus.EN_COURS
            else -> ReadStatus.A_LIRE
        }

    /** Couverture à afficher pour la série : la sienne, sinon celle du 1er tome qui en a une. */
    val displayCover: String? get() = series.coverPath ?: sortedVolumes.firstOrNull { it.coverPath != null }?.coverPath

    /** Image de fond estompée de la fiche : l'image de l'œuvre, sinon la couverture, sinon rien. */
    val backdrop: String?
        get() = when (val b = series.bannerPath) {
            "" -> null
            null -> displayCover
            else -> b
        }
}

data class MomentWithContext(
    @Embedded val moment: Moment,
    val volumeNumber: Int,
    val seriesId: Long,
    val seriesTitle: String
)
