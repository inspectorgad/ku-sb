# The iPhone app

Everything on screen is the shared Kotlin in `../shared` — the same screens,
database, season sync and Ask that the Android app runs. This folder holds
only what iOS needs on top: an entry point, an icon, and the description of
the Xcode project.

## What is here

| File | What it is |
| --- | --- |
| `project.yml` | The Xcode project, described for XcodeGen |
| `App/KUSoftballApp.swift` | The whole Swift app: it shows one view controller |
| `App/Assets.xcassets` | The app icon, the same softball the web dashboard uses |

`KUSoftball.xcodeproj` and `Info.plist` are **generated**, never committed —
`project.yml` is the thing to edit. The app's entry point on the Kotlin side
is `shared/src/iosMain/kotlin/com/example/MainViewController.kt`.

## Opening it on a Mac

```sh
brew install xcodegen
cd ios && xcodegen generate
open KUSoftball.xcodeproj
```

Building runs Gradle first to produce `Shared.framework`, so a plain
`xcodebuild` or pressing Run in Xcode is enough; there is no separate step.

## Why it cannot be built here

Kotlin compiles for iPhone only on macOS — Apple's toolchain is not available
on Linux, and Gradle disables the iOS targets with "cannot run on the current
host". So the shared code's *common* half is checked on every Android build,
and its iPhone half is checked only by `.github/workflows/ios-testflight.yml`,
which runs on a macOS runner. A change that compiles for Android can still
fail for iPhone, and that workflow is where it will say so.

## What differs from Android

Only what has to:

- **Storage.** The API key is a Keychain item under this app's bundle id, set
  to `AfterFirstUnlockThisDeviceOnly`, so it never reaches iCloud Keychain,
  another device, or a password manager. On Android the same key is encrypted
  with the Android Keystore.
- **The database.** Room's own bundled SQLite, in the app's Documents folder,
  created at the current version. The Android migrations are not here because
  no iPhone has an older database to migrate.
- **The back control.** iOS has none, so `SystemBackHandler` does nothing
  there; screens go back through the arrow already on them.

## Orientation

Portrait and landscape both. An iPhone Duo opened, or any iPhone turned
sideways, crosses 600dp and gets the wide layout — tabs on a rail, a game
beside its list — so landscape is allowed rather than locked out.
