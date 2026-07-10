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
import app.railcast.core.net.ApiResult
import app.railcast.core.net.RailcastApi
import app.railcast.core.poll.PollController
import app.railcast.directory.Directory
import app.railcast.feature.alerts.AlertPrefsStore
import app.railcast.feature.alerts.AlertsViewModel
import app.railcast.feature.alerts.NotificationPoster
import app.railcast.feature.home.HomeViewModel
import app.railcast.feature.home.SavedStore
import app.railcast.feature.pnr.PnrViewModel
import app.railcast.feature.plan.PlanDates
import app.railcast.feature.plan.PlanViewModel
import app.railcast.feature.station.StationViewModel
import app.railcast.feature.track.TrackViewModel
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

    // Track: search → live train screen, refreshed by the same poller (4.3).
    val track: TrackViewModel = TrackViewModel(
        search = directory,
        trainScreen = { trainNo, run -> screens.trainScreen(trainNo, run) },
        poller = poller,
        scope = appScope,
    )

    // PNR: masked lookup + chart-prepared watch (4.5). The raw PNR reaches the
    // repository only for the request/watch; it is never persisted here.
    val pnr: PnrViewModel = PnrViewModel(
        pnrScreen = { p -> screens.pnrScreen(p) },
        createChartWatch = { p -> screens.createChartWatch(p) is ApiResult.Ok },
        poller = poller,
        scope = appScope,
    )

    // Station board: search → live 2/4/8-hr board, same poller (4.6).
    val station: StationViewModel = StationViewModel(
        search = directory,
        stationScreen = { code, hrs -> screens.stationScreen(code, hrs) },
        poller = poller,
        scope = appScope,
    )

    // Plan: A→B list with progressive per-row hydration (4.7).
    val plan: PlanViewModel = PlanViewModel(
        search = directory,
        planScreen = { from, to, date, quota -> screens.planScreen(from, to, date, quota) },
        planRow = { no, from, to, date, cls, quota ->
            (screens.planRow(no, from, to, date, cls, quota) as? ApiResult.Ok)?.data
        },
        scope = appScope,
        initialDate = PlanDates.today(),
    )

    // Alerts: local notification prefs + quiet hours (4.8). NotificationPoster is
    // the Firebase-agnostic binding point the FCM service will call.
    val alerts: AlertsViewModel = AlertsViewModel(AlertPrefsStore(appContext), appScope)
    val notifications: NotificationPoster = NotificationPoster(appContext)
}
