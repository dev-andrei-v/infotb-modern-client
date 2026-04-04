package ro.infotb.modern

import android.app.Application
import org.koin.core.context.startKoin
import ro.infotb.modern.di.sharedModule

class TbApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContext.init(this)
        startKoin {
            modules(sharedModule)
        }
    }
}
