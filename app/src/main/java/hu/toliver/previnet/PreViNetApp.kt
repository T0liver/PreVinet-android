package hu.toliver.previnet

import android.app.Application
import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import hu.toliver.previnet.data.DiseaseRepository
import hu.toliver.previnet.data.ImageStore
import hu.toliver.previnet.data.PrefsRepository
import hu.toliver.previnet.data.SubmissionRepository
import hu.toliver.previnet.data.api.ApiClient
import hu.toliver.previnet.data.db.AppDatabase
import hu.toliver.previnet.util.ConnectivityObserver
import hu.toliver.previnet.work.NotificationChannels

class PreViNetApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.create(this)
    }

    /** Coil 3 loader: explicit OkHttp fetcher so masks/thumbnails load (and disk-cache) offline-first. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
}

class AppContainer(app: Application) {
    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "previNet.db").build()
    val api = ApiClient(BuildConfig.API_BASE)
    val imageStore = ImageStore(app)
    val prefs = PrefsRepository(app)
    val connectivity = ConnectivityObserver(app)
    val submissions = SubmissionRepository(app, db, api, imageStore)
    val diseases = DiseaseRepository(api, prefs)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PreViNetApp).container
