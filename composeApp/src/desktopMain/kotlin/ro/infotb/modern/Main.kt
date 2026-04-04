package ro.infotb.modern

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.core.context.startKoin
import ro.infotb.modern.di.sharedModule
import ro.infotb.modern.ui.App

fun main() {
    startKoin {
        modules(sharedModule)
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "InfoTB",
            icon = painterResource("icon.png"),
            state = rememberWindowState(width = 1024.dp, height = 768.dp)
        ) {
            App()
        }
    }
}
