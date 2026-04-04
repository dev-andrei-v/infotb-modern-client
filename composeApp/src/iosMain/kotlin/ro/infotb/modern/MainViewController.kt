package ro.infotb.modern

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import ro.infotb.modern.di.sharedModule
import ro.infotb.modern.ui.App

fun MainViewController() = ComposeUIViewController {
    App()
}

fun initKoin() {
    startKoin {
        modules(sharedModule)
    }
}
