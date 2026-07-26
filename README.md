# KU Softball

An Android app for following the Kansas Jayhawks Softball team: game
results with inning-by-inning R/H/E line scores, per-player batting AND
pitching box scores, season aggregates (AVG/OBP/SLG/OPS, ERA/WHIP), and
team leaderboards — automatically updated through the season. Modeled on
the [ku-wbb](https://github.com/inspectorgad/ku-wbb) and
[ku-volleyball](https://github.com/inspectorgad/ku-volleyball) apps and
seeded with the complete Spring 2026 season (57 games, 36-21, Big 12
tournament and NCAA regional included, every game with a full box
score).

## Features

- **Roster** — the Jayhawks roster (name, jersey number, position) with
  each player's career line at a glance (batters lead with AVG/HR/RBI,
  pitchers with ERA/record/strikeouts), split into current and former
  players. Tap a player for per-season batting and pitching tables,
  career totals, and a full game log.
- **Games** — the full posted schedule, home and away: played games with
  their result and an inning-by-inning R/H/E line score, upcoming ones
  with their first pitch. Opponents read "at" or "vs" (neutral sites are
  labeled), straight from KU's published schedule. Inside a game, every player's batting line (AB, R, H, 2B, 3B,
  HR, RBI, BB, SO, HBP, SB, CS, SF, SAC) and pitching line (IP, H, R,
  ER, BB, SO, HR, W/L/SV). Games and lines can also be added or edited
  by hand.
- **Leaders** — filter by season (or all-time) to see the team's record
  and run margins plus leaderboards for AVG, HR, RBI, hits, runs, SB,
  and OPS on the batting side and ERA, strikeouts, wins, saves, and
  WHIP in the circle.
- **Big 12** — computed conference standings for every Big 12 team
  (conference and overall records, poll rank, RPI) with KU's row
  highlighted, plus the ESPN.com/USA Softball Top 25 with Big 12 teams
  flagged — refreshed nightly alongside everything else.

Derived stats (AVG/OBP/SLG/OPS, ERA/WHIP, innings from outs) are computed
automatically from the raw counting stats.

All data is stored locally on the device in a Room (SQLite) database.

## Data pipeline

1. `scripts/scrape-ku-sb.mjs` (GitHub Actions, nightly) captures every
   game's box score from kuathletics.com (Sidearm `__NUXT_DATA__`
   payload — the primary source, with complete batting and pitching
   lines), parses the full published schedule (every game, home and
   away, with site and first pitch) from the same payload on the
   schedule page, sweeps the NCAA API daily scoreboard as a results
   cross-check, and pulls the current roster from kuathletics.com. The roster scrape is also what flags departed
   players as "former."
2. `scripts/update-seed.py` regenerates `app/src/main/assets/seed.json`
   from the scraped data.
3. A seed change triggers the APK build workflow, which publishes the APK
   and `season-data.json` to the rolling `latest-apk` release.
4. On launch (and via pull-to-refresh) the app downloads `season-data.json`
   and merges it — gap-filling only, never overwriting user-entered data.

Install the latest build directly on a phone:
`https://github.com/inspectorgad/ku-sb/releases/latest/download/app-debug.apk`

### If Advanced Protection blocks the install

Android's Advanced Protection mode blocks APKs downloaded in the browser but
allows installs from a computer over ADB:

1. On the phone: Settings → About phone → tap **Build number** 7 times, then
   Settings → System → Developer options → enable **USB debugging**.
2. On the computer: install
   [Android platform-tools](https://developer.android.com/tools/releases/platform-tools)
   (macOS: `brew install android-platform-tools`).
3. Plug the phone in, accept the "Allow USB debugging?" prompt, and run
   `scripts/adb-install.sh` (Mac/Linux) or `scripts\adb-install.bat` (Windows).

The scripts download the latest release APK and run `adb install -r`, which
keeps the app's data on upgrades. Turning USB debugging back off afterward is
fine — it's only needed while installing.

## Tech

- Kotlin + Jetpack Compose (Material 3), KU crimson & blue theme
- Room for persistence
- Pure-Kotlin softball stats engine in `app/src/main/java/com/example/stats/`,
  covered by unit tests in `app/src/test/`

## Data validation

The Spring 2026 source data was validated before the app was built — see
[DATA-VALIDATION.md](DATA-VALIDATION.md). Raw evidence lives in `probe/`
and `probe2/`. Key finding: the NCAA API softball feed alone is not
enough (missing tournament game, no regular-season pitching lines,
polluted pitcher batting rows), so kuathletics.com box scores are the
primary per-game source.

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project
4. Run the app on an emulator or physical device

## Season dashboard

A colorful single-page dashboard of the whole season — team tiles,
run-margin chart, leaderboards, Big 12 standings and the national poll,
every game with its line score, and a clickable roster where each player
opens their game-by-game batting and pitching log — is published on
GitHub Pages:

**https://inspectorgad.github.io/ku-sb/**

`scripts/build-dashboard.py` regenerates `docs/index.html` from
`seed.json`; the nightly scrape rebuilds and redeploys it whenever the
data changes.
