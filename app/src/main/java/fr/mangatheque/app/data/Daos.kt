package fr.mangatheque.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    @Transaction
    @Query("SELECT * FROM series ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<SeriesWithVolumes>>

    @Transaction
    @Query("SELECT * FROM series WHERE id = :id")
    fun observeOne(id: Long): Flow<SeriesWithVolumes?>

    @Transaction
    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getOne(id: Long): SeriesWithVolumes?

    @Query("SELECT * FROM series ORDER BY title COLLATE NOCASE")
    suspend fun listAll(): List<Series>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(series: Series): Long

    @Update
    suspend fun update(series: Series)

    @Delete
    suspend fun delete(series: Series)

    @Query("DELETE FROM series")
    suspend fun deleteAll()
}

@Dao
interface VolumeDao {
    @Query("SELECT * FROM volumes WHERE id = :id")
    fun observeOne(id: Long): Flow<Volume?>

    @Query("SELECT * FROM volumes WHERE id = :id")
    suspend fun getOne(id: Long): Volume?

    @Query("SELECT * FROM volumes WHERE isbn = :isbn LIMIT 1")
    suspend fun findByIsbn(isbn: String): Volume?

    @Query("SELECT * FROM volumes WHERE seriesId = :seriesId ORDER BY number")
    suspend fun listForSeries(seriesId: Long): List<Volume>

    @Query("SELECT * FROM volumes")
    suspend fun listAll(): List<Volume>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(volume: Volume): Long

    @Update
    suspend fun update(volume: Volume)

    @Delete
    suspend fun delete(volume: Volume)
}

@Dao
interface MomentDao {
    @Query("SELECT * FROM moments WHERE volumeId = :volumeId ORDER BY createdAt DESC")
    fun observeForVolume(volumeId: Long): Flow<List<Moment>>

    @Query(
        """SELECT moments.*, volumes.number AS volumeNumber, series.id AS seriesId, series.title AS seriesTitle
           FROM moments JOIN volumes ON volumes.id = moments.volumeId JOIN series ON series.id = volumes.seriesId
           ORDER BY moments.createdAt DESC"""
    )
    fun observeAll(): Flow<List<MomentWithContext>>

    @Query("SELECT * FROM moments")
    suspend fun listAll(): List<Moment>

    @Insert
    suspend fun insert(moment: Moment): Long

    @Update
    suspend fun update(moment: Moment)

    @Delete
    suspend fun delete(moment: Moment)
}
