package app.railcast.core

import android.content.Context
import androidx.room.Room
import app.railcast.BuildConfig
import app.railcast.core.data.ScreenRepository
import app.railcast.core.db.RailcastDatabase
import app.railcast.core.db.RoomScreenCache
import app.railcast.core.net.DeviceSession
import app.railcast.core.net.DeviceTokenStore
import app.railcast.core.net.NetworkModule
import app.railcast.core.net.RailcastApi
import app.railcast.core.poll.PollController
import app.railcast.directory.Directory
import app.railcast.feature.home.HomeViewModel
import app.railcast.feature.home.SavedStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual composition root (no DI framework — keeps the app lean, NFR-1). Holds
 * the singletons the app needs: device session, API client, Room cache, and
 * the SWR repository. One per process.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: RailcastDatabase = Room.databaseBuilder(
        appContext,
        RailcastDatabase::class.java,
        "railcast.db",
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    val session: DeviceSession = DeviceSession(
        store = DeviceTokenStore(appContext),
        appVersion = BuildConfig.VERSION_NAME,
    )

    val api: RailcastApi = NetworkModule.railcastApi(BuildConfig.BASE_URL, session.tokenProvider)

    val screens: ScreenRepository = ScreenRepository(
        api = api,
        cache = RoomScreenCache(database.screenCacheDao()),
    )

    // Bundled train/station directory: offline fuzzy search, name→code/number
    // resolution before any API call (FR-1.1). Index loads lazily off-main.
    val directory: Directory = Directory(appContext)

    // The one poll controller for the whole app (PRD §6.4). Main-confined so
    // register/foreground/background and loop mutations never race.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val poller: PollController = PollController(appScope)

    // Home: directory search + saved live cards (backlog 4.2). Saved-card refresh
    // is owned by `poller` like every other loop — no per-card timers.
    val home: HomeViewModel = HomeViewModel(
        search = directory,
        saved = SavedStore(appContext),
        trainScreen = { trainNo -> screens.trainScreen(trainNo) },
        poller = poller,
        scope = appScope,
    )
}
