package fr.mangatheque.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Series::class, Volume::class, Moment::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun volumeDao(): VolumeDao
    abstract fun momentDao(): MomentDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Version 1 → 2 : ajout de l'image de fond des séries. Les données existantes sont conservées. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE series ADD COLUMN bannerPath TEXT")
            }
        }

        /** Version 2 → 3 : résumé du dos du livre pour chaque tome. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE volumes ADD COLUMN summary TEXT")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "mangatheque.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
                .also { instance = it }
        }
    }
}
