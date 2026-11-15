package com.example.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.platform.Device

/**
 * The phone's own storage, handed to the screens that keep something of their
 * own — which here is just Ask, holding the reader's API key and chosen model.
 *
 * Provided once at the top of the screen tree, so no screen reaches for
 * Android (or iPhone) directly and the same screen code runs on both.
 */
val LocalDevice = staticCompositionLocalOf<Device> {
    error("No Device provided. Wrap the screens in CompositionLocalProvider(LocalDevice provides …).")
}
