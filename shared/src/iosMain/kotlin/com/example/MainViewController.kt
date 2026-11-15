package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.example.data.JayhawksDatabase
import com.example.data.createDatabase
import com.example.platform.Device
import com.example.platform.IosDevice
import com.example.ui.JayhawksApp
import com.example.ui.LocalDevice
import com.example.ui.theme.MyApplicationTheme
import platform.UIKit.UIViewController

// The iPhone app's way in. Swift calls MainViewController() — as
// MainViewControllerKt.MainViewController(), after this file's name — and
// shows what it returns. Everything on screen from there is the shared code:
// the same screens, the same database and the same season sync the Android
// app runs, with the Keychain and iPhone's own SQLite behind them.

internal val iosDevice: Device by lazy { IosDevice() }
private val database: JayhawksDatabase by lazy { createDatabase() }

fun MainViewController(): UIViewController = ComposeUIViewController {
    // One view model for the life of the app, which is what Android's
    // ViewModel gives it there.
    val viewModel = remember { JayhawksViewModel(database.dao(), iosDevice) }
    CompositionLocalProvider(LocalDevice provides iosDevice) {
        MyApplicationTheme {
            JayhawksApp(viewModel)
        }
    }
}
