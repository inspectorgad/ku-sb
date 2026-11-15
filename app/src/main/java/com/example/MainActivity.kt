package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.JayhawksViewModel
import com.example.data.JayhawksDatabase
import com.example.data.get
import com.example.platform.AndroidDevice
import com.example.ui.JayhawksApp
import com.example.ui.LocalDevice
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Android's storage, handed to the shared screens once at the top, so
        // none of them reaches for Android itself and the same code can run on
        // an iPhone with the Keychain behind it instead.
        val device = AndroidDevice(applicationContext)
        setContent {
            // Kept across rotation like any ViewModel; built here because it
            // takes this phone's database and storage rather than finding them.
            val model = viewModel {
                JayhawksViewModel(JayhawksDatabase.get(applicationContext).dao(), device)
            }
            CompositionLocalProvider(LocalDevice provides device) {
                MyApplicationTheme {
                    JayhawksApp(model)
                }
            }
        }
    }
}
