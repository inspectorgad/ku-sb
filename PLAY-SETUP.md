# Publishing KU Softball to Play Store internal testing

Internal testing distributes the app through the Play Store itself to up to
100 named testers — no sideloading, so Advanced Protection and Play Protect
never interfere, and updates install automatically.

**Before any of this, you need an upload key.** CI no longer builds the Play
bundle and the repository holds no signing key, because the app has never been
published and the key it used to carry sat in a public repository signing an
AAB nobody downloaded. Step 0 below creates a fresh one.

## Step 0 — create an upload key and give it to CI

On your own machine, with a JDK installed:

    keytool -genkeypair -v \
      -keystore upload.keystore -storetype PKCS12 \
      -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
      -dname "CN=KU Softball, O=SPST, C=US" \
      -storepass 'A-LONG-RANDOM-PASSWORD' -keypass 'THE-SAME-PASSWORD'

Use a generated password, not a memorable one — it only ever lives in GitHub's
secret store. Then encode the keystore:

    base64 -w0 upload.keystore            # Linux
    base64 -i upload.keystore | tr -d '\n'   # macOS

In the repository: **Settings → Secrets and variables → Actions → New
repository secret**, three of them:

| Secret | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | the base64 output above |
| `UPLOAD_STORE_PASSWORD` | the password |
| `UPLOAD_KEY_PASSWORD` | the same password |

Keep `upload.keystore` itself somewhere safe and off the repository. Losing it
is recoverable once the app is on Play (Google can reset an upload key), but
only then.

Finally, put the bundle back into `.github/workflows/build-apk.yml`: restore
the keystore from the secret before the build, add `:app:bundleRelease` to the
Gradle call, and attach `app-release.aab` to the release.

    - name: Restore upload keystore
      env:
        UPLOAD_KEYSTORE_BASE64: ${{ secrets.UPLOAD_KEYSTORE_BASE64 }}
      run: echo "$UPLOAD_KEYSTORE_BASE64" | base64 -d > upload.keystore

The build reads `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD` from the
environment; with none of them set it simply builds the release unsigned,
which is why CI is happy today.

## One-time setup (about 30 minutes, $25)

1. **Create a Google Play developer account** at
   https://play.google.com/console/signup with your personal Google account
   (one-time $25 fee). Choose a "personal" account type. Google requires an
   identity-verification step (usually a driver's license photo) that can
   take a day or two to clear.

2. **Create the app**: Play Console → All apps → **Create app**.
   - App name: `KU Softball` (internal testing isn't publicly listed)
   - Default language: English (US) · App or game: App · Free
   - Declarations: accept.

3. **Accept Play App Signing** when prompted (the default). Google holds the
   real signing key; the repo's committed `upload.keystore` is only the
   upload key and can be reset from the Console if ever needed
   (Setup → App signing → Request upload key reset).

4. **Set up internal testing**: Testing → **Internal testing** →
   **Create new release**.
   - Upload `app-release.aab` (downloaded from the link above).
   - Release name/notes: anything (e.g. "2025 season baked in").
   - Click **Next**, resolve any warnings, then **Save and publish** —
     internal testing releases go live in minutes, no review.

5. **Add testers**: still under Internal testing → **Testers** tab →
   create an email list containing your personal Gmail (and any friends'
   or family's) → save → copy the **opt-in link**.

6. **On the phone**: open the opt-in link, tap **Accept invite**, then
   **Download it on Google Play**. Installs like any Play Store app.

7. A few Console sections must be filled in before the release can publish
   (the Dashboard shows a checklist): Privacy policy (a GitHub Pages or
   even a repo README URL describing "no data collected" is fine), App
   access (all functionality available without login), Ads (no), Content
   rating questionnaire (utility, no objectionable content), Target
   audience (18+ keeps it simplest), Data safety (no data collected or
   shared — the app only downloads public stats).

## Updating the app later

Season *data* updates never need a new upload — the app syncs stats itself.
Only app *code* changes need one: download the newest `app-release.aab`
from the release link and upload it as a new internal-testing release
(two minutes). CI stamps each build with an increasing `versionCode`
(the workflow run number), so a newer AAB always uploads cleanly.

## Notes

- The upload keystore used to be committed to this public repo, with its
  password in `app/build.gradle.kts`. Both are gone. It was tolerable only
  while Play App Signing held the real key, and since the app was never
  published there was no such arrangement — it signed an AAB that was built
  on every CI run and downloaded zero times. Step 0 above generates a fresh
  one if and when it is needed.
- The debug keystore (`debug.keystore.base64`) stays committed, and that is
  deliberate. Its password is `android`, the value every Android debug
  keystore uses, so there is no secret in it; and a fixed debug identity is
  what lets `adb install -r` replace an installed build rather than
  demanding an uninstall that would delete any hand-entered stat lines.
- "KU"/Jayhawks trademarks: fine for a private internal-testing app;
  a public Play listing would need a rename (e.g. "Rock Chalk Volleyball
  Stats") to survive review.
