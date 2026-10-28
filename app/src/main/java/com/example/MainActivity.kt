package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
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
            CompositionLocalProvider(LocalDevice provides device) {
                MyApplicationTheme {
                    JayhawksApp()
                }
            }
        }
    }
}
