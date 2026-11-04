package fr.mangatheque.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import fr.mangatheque.app.data.AppDatabase
import fr.mangatheque.app.data.Prefs
import fr.mangatheque.app.data.Repository

class MangaApp : Application(), ImageLoaderFactory {
    lateinit var repository: Repository
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this, AppDatabase.get(this))
        prefs = Prefs(this)
    }

    /** Les images apparaissent en fondu plutôt que d'un coup. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(250)
        .build()
}
