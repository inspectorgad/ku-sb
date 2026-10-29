import SwiftUI
import Shared

// The iPhone app. Everything on screen is the shared Kotlin code — the same
// screens, database and season sync the Android app runs — built into the
// Shared framework from shared/ and shown here full screen.
// MainViewController is in
// shared/src/iosMain/kotlin/com/example/MainViewController.kt.

@main
struct KUSoftballApp: App {
    var body: some Scene {
        WindowGroup {
            // The shared screens lay themselves out around the notch, the
            // home indicator and the keyboard, so they get the whole screen.
            SharedScreens().ignoresSafeArea()
        }
    }
}

struct SharedScreens: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
