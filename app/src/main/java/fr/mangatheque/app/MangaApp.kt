package fr.mangatheque.app

import android.app.Application
import fr.mangatheque.app.data.AppDatabase
import fr.mangatheque.app.data.Prefs
import fr.mangatheque.app.data.Repository

class MangaApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this, AppDatabase.get(this))
        prefs = Prefs(this)
    }
}
