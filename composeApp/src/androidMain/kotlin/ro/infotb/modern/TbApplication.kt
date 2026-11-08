package ro.infotb.modern

import android.app.Application
import org.koin.core.context.startKoin
import org.maplibre.android.MapLibre
import ro.infotb.modern.di.sharedModule

class TbApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContext.init(this)
        MapLibre.getInstance(this)
        startKoin {
            modules(sharedModule)
        }
    }
}
