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
import app.railcast.core.analytics.Analytics
import app.railcast.core.analytics.AnalyticsConsentStore
import app.railcast.core.analytics.ConsentGatedAnalytics
import app.railcast.core.analytics.NoopAnalytics
import app.railcast.core.net.AndroidConnectivity
import app.railcast.core.net.ApiResult
import app.railcast.core.net.Connectivity
import app.railcast.core.net.PushTokenRegistrar
import app.railcast.core.net.RailcastApi
import app.railcast.core.poll.PollController
import app.railcast.directory.Directory
import app.railcast.feature.alerts.AlertPrefsStore
import app.railcast.feature.alerts.AlertsViewModel
import app.railcast.feature.alerts.NotificationPoster
import app.railcast.feature.ambient.WidgetAmbientSink
import app.railcast.feature.find.FindViewModel
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

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

    val api: RailcastApi = NetworkModule.railcastApi(BuildConfig.BASE_URL, session)

    // FCM token upload (contracts §5): called by the messaging service on token
    // rotation and by PushBootstrap once per app start.
    val pushTokens = PushTokenRegistrar(api, session)

    private val pnrKeySalt = app.railcast.core.data.PnrKeySalt(appContext)

    val screens: ScreenRepository = ScreenRepository(
        api = api,
        cache = RoomScreenCache(database.screenCacheDao()),
        pnrKeySalt = { pnrKeySalt.value },
    )

    // Bundled train/station directory: offline fuzzy search, name→code/number
    // resolution before any API call (FR-1.1). Index loads lazily off-main.
    val directory: Directory = Directory(appContext)

    // The one poll controller for the whole app (PRD §6.4). Main-confined so
    // register/foreground/background and loop mutations never race.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val poller: PollController = PollController(appScope)

    // One saved-trains store shared by Home (the cards) and Track (the board's
    // Pin action), so pinning from the board and the Home list stay in sync.
    private val savedTrains = SavedStore(appContext)

    // Home: directory search + saved live cards (backlog 4.2). Saved-card refresh
    // is owned by `poller` like every other loop — no per-card timers.
    val home: HomeViewModel = HomeViewModel(
        search = directory,
        saved = savedTrains,
        trainScreen = { trainNo -> screens.trainScreen(trainNo) },
        poller = poller,
        scope = appScope,
        // Feeds the home-screen widget from data the app already fetched, so
        // the ambient surface never polls on its own (NFR-3).
        ambient = WidgetAmbientSink(appContext),
    )

    // Find: one omni-input over trains, PNRs, stations and routes (W8).
    val find: FindViewModel = FindViewModel(search = directory, scope = appScope)

    // Track: search → live train screen, refreshed by the same poller (4.3).
    val track: TrackViewModel = TrackViewModel(
        search = directory,
        trainScreen = { trainNo, run -> screens.trainScreen(trainNo, run) },
        saved = savedTrains,
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
        nearestStations = { lat, lng -> directory.nearest(lat, lng) },
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
        createTatkalWatch = { no, date, band -> screens.createTatkalWatch(no, date, band) is ApiResult.Ok },
    )

    // Alerts: local notification prefs + quiet hours (4.8). NotificationPoster is
    // the Firebase-agnostic binding point the FCM service will call.
    val notifications: NotificationPoster = NotificationPoster(appContext)

    // Drives the offline banner (4.9); the SWR cache still serves data offline.
    val connectivity: Connectivity = AndroidConnectivity(appContext)

    // Privacy-respecting analytics (5.5, FR-11.3): numeric-only events, gated by
    // a local opt-out. Sink is Noop until a backend is wired.
    private val analyticsConsent = AnalyticsConsentStore(appContext)
    val analyticsEnabled: StateFlow<Boolean> =
        analyticsConsent.enabled.stateIn(appScope, SharingStarted.Eagerly, true)
    val analytics: Analytics = ConsentGatedAnalytics(NoopAnalytics) { analyticsEnabled.value }

    // Alerts settings: notification prefs (4.8) + analytics opt-out (5.5). The
    // store is shared with the FCM service, which applies the same prefs to
    // incoming pushes (NotificationPolicy).
    val alertPrefs = AlertPrefsStore(appContext)
    val alerts: AlertsViewModel = AlertsViewModel(alertPrefs, analyticsConsent, appScope)
}
