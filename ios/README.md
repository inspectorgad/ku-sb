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

## Getting it onto a phone (TestFlight)

This is the one part that needs something from you. None of it is pasted into
a chat — the values go into GitHub's own secrets screen, which shows them to
nobody afterwards, not even you.

### 1. In App Store Connect

1. **Register the app.** Apps → **+** → New App. Pick iOS, give it a name, and
   for **Bundle ID** choose `io.github.inspectorgad.kusb`. If it is not in the
   list, create it first in
   [Certificates, Identifiers & Profiles → Identifiers](https://developer.apple.com/account/resources/identifiers/list)
   as an App ID with that exact string. No capabilities need ticking: this app
   asks for none.
2. **Make an API key.** Users and Access → **Integrations** → App Store Connect
   API → **+**. Name it anything; give it the **App Manager** role. Download
   the `.p8` file — Apple lets you download it **once**. Note the **Key ID**
   beside it and the **Issuer ID** above the list.
3. **Find your Team ID**: [Membership details](https://developer.apple.com/account#MembershipDetailsCard),
   ten characters.

### 2. In GitHub

Repository → Settings → Secrets and variables → **Actions** → New repository
secret, four times:

| Secret | What to paste |
| --- | --- |
| `APPLE_TEAM_ID` | The ten-character Team ID |
| `ASC_ISSUER_ID` | The Issuer ID (looks like `69a6de7e-…`) |
| `ASC_KEY_ID` | The ten-character Key ID |
| `ASC_KEY_P8` | The **whole** `.p8` file, `BEGIN`/`END` lines included |

### 3. Check before building

Run **Apple keys check** from the Actions tab. It answers in under a minute
and names the one that is wrong, where a failed TestFlight build would take
twenty minutes to say the same thing. It prints no secret.

### 4. Build

Run **iOS TestFlight build** from the Actions tab. It builds on Xcode 27.1 and
uploads for **internal testing only** — the build reaches testers on your own
team and never goes to Apple's beta review. Add yourself as an internal tester
in App Store Connect → TestFlight, and it appears in the TestFlight app on
your phone.

Nothing here stores a certificate in this repository. The archive is built
unsigned and Apple signs it at upload with a distribution certificate it holds
for the team, which is also why no device has to be registered first.
