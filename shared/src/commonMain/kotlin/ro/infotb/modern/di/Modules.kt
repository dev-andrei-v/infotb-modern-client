package ro.infotb.modern.di

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ro.infotb.modern.createSettings
import ro.infotb.modern.data.api.AuthManager
import ro.infotb.modern.data.api.StbApi
import ro.infotb.modern.data.backup.BackupManager
import ro.infotb.modern.data.db.StbDatabase
import ro.infotb.modern.data.db.buildDatabase
import ro.infotb.modern.data.db.createDatabaseBuilder
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.data.store.DashboardStore
import ro.infotb.modern.data.store.FavoritesStore
import ro.infotb.modern.data.store.NotificationStore
import ro.infotb.modern.data.store.PreferencesStore
import ro.infotb.modern.notification.NotificationEngine
import ro.infotb.modern.notification.createPlatformNotifier

val sharedModule: Module = module {

    single(named("appScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single {
        createSettings()
    }

    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    single {
        HttpClient {
            install(ContentNegotiation) {
                json(get())
            }
        }
    }

    single {
        AuthManager(settings = get())
    }

    single {
        StbApi(client = get(), authManager = get())
    }

    single {
        TransitRepository(api = get())
    }

    // Room Database
    single<StbDatabase> {
        buildDatabase(createDatabaseBuilder())
    }

    // DAOs
    single { get<StbDatabase>().widgetDao() }
    single { get<StbDatabase>().favoriteDao() }
    single { get<StbDatabase>().notificationDao() }
    single { get<StbDatabase>().groupDao() }

    // Stores
    single {
        PreferencesStore(settings = get())
    }

    single {
        DashboardStore(
            widgetDao = get(),
            groupDao = get(),
            scope = get(named("appScope"))
        )
    }

    single {
        FavoritesStore(
            dao = get(),
            scope = get(named("appScope"))
        )
    }

    single {
        NotificationStore(
            dao = get(),
            scope = get(named("appScope"))
        )
    }

    single {
        BackupManager(
            widgetDao = get(),
            groupDao = get(),
            favoriteDao = get(),
            notificationDao = get(),
            preferencesStore = get(),
            json = get()
        )
    }

    single {
        NotificationEngine(
            notificationStore = get(),
            repository = get(),
            scope = get(named("appScope")),
            platformNotifier = createPlatformNotifier()
        )
    }
}
