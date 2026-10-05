# KU Softball — enhancement plan to reach (and pass) ku-wbb / ku-volleyball

Produced 2026-10-05 by surveying all three apps, inventorying every softball data
source, assessing 111 candidate features, and adversarially re-checking each
assessment. 60 of the 111 assessments were corrected by that second pass —
mostly downward, and several items turned out to be **already built**.

---

## 1. Where ku-sb stands

The pipeline is the strong part and is arguably ahead of its siblings: a proven
two-source scrape, computed Big 12 standings, RPI + poll snapshots, a full
published-schedule parse with home/away and first pitch, off-season cadence
throttling, and a rolling APK release. The **app** is the thin part. ku-sb has
4 tabs (Roster, Games, Leaders, Big 12). The siblings add whole analytical
surfaces on top of the same kind of data: opponents, splits, player form, season
insights, a glossary, and a natural-language Ask feature — plus distribution
ku-sb has none of (PWA install, calendar feed).

One structural oddity worth naming: **the web dashboard is ahead of the Android
app.** It already renders per-game modals and leaderboards the app doesn't have.

---

## 2. What the data supports — the enabling finding

The nightly scrape already downloads far richer box scores than the seed keeps.
`update-seed.py` extracts ~27 fields per player line and throws the rest away.
Still sitting in `scraped/sidearm-game-*.json` today:

| Dropped on the floor | Unlocks |
|---|---|
| `spot` (batting-order slot), `position`, `substitute`, `uniform` | Box score in real lineup order; starters vs bench; "who hit leadoff" |
| 25 pitching fields: `pitches`, `battersFaced`, `wildPitches`, `balks`, `hitBatters`, `inheritedRunners(+Scored)`, `shutouts`, `gamesCompleted`, `strikeoutsLooking`, `opponentAtBats` | Pitch counts & workload, pitches/BF efficiency, opponent AVG, bullpen inherited-runner strand rate |
| 11 hitting fields: `strikeoutsLooking`, `reachedOnError`, `reachesOnAFieldersChoice`, `groundOuts`, `flyOuts`, `groundedIntoDoublePlay`, `intentionalWalks`, `pickedOff` | GO/AO ratio, K-looking rate, GIDP, ROE — a genuine advanced-hitting tab |
| **The opponent's full player array** (every game) | A real two-team box score; opposing-pitcher matchup notes |
| `totals.hitting` / `totals.pitching` per team | Verified team totals without re-summing |
| `record` on each team | Opponent's record *at the time* → quality-of-win weighting |
| `venue.attendance`, `venue.location`, `doubleHeaderGame` | Venue line, attendance history, biggest-crowd facts |
| `sidearmId` + `url` | "View official box score" deep link per game |

And more exists in the **live** box payload that the scraper doesn't even save:
per-player **fielding** (putouts, assists, errors, fielding %, SB-against,
caught-stealing-by), **play-by-play** with scoring flags, **scoring summary**,
`weather`, `umpires`, game `duration`, `scheduledInnings` (run-rule detection),
opponent `rank` and `logo`, and a link to the official **PDF** box score.

The schedule payload likewise carries unused `tournament`, `conference` flag,
opponent `logo`/`website`, and media links (TV, radio, StatBroadcast live stats,
preview/recap stories).

**Proven sources:** Sidearm box scores, season stats index, schedule payload,
roster page, NCAA scoreboard sweep, NCAA RPI, the ESPN/USA Softball poll (HTML
only). **Dead ends:** no NCAA fall coverage at all, no KU-published fall
results, no working poll JSON endpoint, no AP poll for softball.

---

## 3. Phased roadmap

### Phase 1 — Cheap wins, mostly already-paid-for (effort S)

| Item | What you get | Source | Notes |
|---|---|---|---|
| **Glossary / explainable stats** | Tap any stat label for a plain-English definition | none needed | Direct port of the mechanism; new softball vocabulary |
| **PWA install + offline** | Dashboard installs to a phone home screen, works offline | none needed | `manifest.json`, `sw.js`, icons — simpler than volleyball's |
| **ICS calendar feed** | Subscribe to the schedule in any calendar app | `schedule-*.json` | Needs 4 softball tweaks (doubleheaders, TBA times, neutral sites) |
| **Streaks** | Current and longest W/L runs | existing games | Sport-neutral |
| **Rankings archive** | Keep every weekly poll/RPI snapshot, not just the latest | existing scrape | Today each run overwrites; archiving is the missing half |
| **Icon generation scripts** | Reproducible icon set | none | Sport-agnostic plumbing |
| **Room migration tests** | Catch a bad migration before it ships | none | ku-sb is at schema v4 with zero migration tests |

### Phase 2 — Harvest the discarded data (effort S–M, highest leverage)

This is the phase that makes everything later possible. One pass over
`update-seed.py` to stop discarding fields, plus a Room migration.

- **Rich pitching line** — pitch counts, BF, WP, HBP, shutouts, CG, inherited runners
- **Rich hitting line** — K-looking, ROE, FC, GO/AO, GIDP, IBB
- **Lineup order** — store `spot`/`position`, render the box score in batting order
- **Game context** — attendance, venue, doubleheader flag, official box-score link
- **Opponent-record capture** — enables quality-of-win labelling later

Implies: seed schema additions (additive, `formatVersion` stays 1), Room v4→v5
migration, `MergeSyncTest` updates.

### Phase 3 — The analytical surfaces (effort M–L)

| Item | Softball adaptation |
|---|---|
| **Splits** | Home/away/neutral, conference vs non-con, vs ranked — using AVG/OBP/SLG/OPS and ERA/WHIP |
| **Inning splits** | ku-wbb's quarter splits become innings, from `inningScores` |
| **Player form** | Rolling last-N windows, two-sided (batting and pitching) with volume gates |
| **Season highlights / notable facts** | Most runs in a game, longest hit streak, best start — pure functions over games + lines |
| **Opponents screen + detail** | Per-opponent record, run differential, who hit well against them. Fits softball *better* than basketball because of series play |
| **Season screen** | Season-at-a-glance narrative card set |

### Phase 4 — Ask (effort L–XL)

The natural-language question feature. The engine (`AskEngine.kt`,
`AskKeyStore.kt`) is sport-agnostic and ports nearly verbatim; the work is the
**data pack** — flattening the validated seed into tables plus a data dictionary
(`ask_pack.py` → `docs/ask-data.json` → `docs/ask.js`). Do this after Phase 2 so
the pack is built on the rich stat set rather than the thin one.

### Phase 5 — Live game night (effort L)

Port `game-night-watch.mjs`: on game day, poll for results and push an update
when the game goes final. Needs four softball rewrites (doubleheaders, run-rule
innings, no fall coverage, pitching decisions).

---

## 4. Softball-native opportunities — things neither sibling can do

These have **no basketball or volleyball equivalent** and the data is already there:

1. **Play-by-play screen** — the live box payload carries per-inning narratives with scoring flags. Nothing in the sibling apps comes close.
2. **Scoring summary under the line score** — "how each run scored," the cheapest high-impact addition in this document.
3. **Fielding as a third stat category** — putouts, assists, errors, fielding %, stolen-bases-against, caught-stealing-by. Hitting / pitching / **fielding** is a complete softball picture.
4. **Pitching workload tracking** — pitch counts and batters faced across a weekend series; rest days between appearances. Genuinely useful in a sport where one arm starts three games in four days.
5. **Batting-order analysis** — production by lineup slot, from `spot`.
6. **Run-rule detection** — `scheduledInnings` tells you a 5-inning game was a run-rule, which the current app can't distinguish from a short game.
7. **Game-info panel** — weather, umpires, game duration, first pitch, attendance.
8. **Ranked-opponent badges** — `rank` on each team gives "vs #20 UCF" on game rows.

---

## 5. Not possible, or not worth doing

| Item | Why |
|---|---|
| **National leaders board** | No softball national per-category leaderboard is reachable. The siblings' source has no softball equivalent — verified dead. |
| **Neutral-site overrides** | ku-sb already derives H/A/N from the schedule payload; the sibling's hand-maintained override file is strictly worse. |
| **NCAA per-player box scores** | Retrievable but untrustworthy for softball (polluted pitcher batting rows, mangled names) — documented in DATA-VALIDATION.md. Sidearm stays primary. |
| **Automated fall results** | Neither the NCAA feed nor kuathletics publishes them. Opponent sites are the only source and that stays semi-manual. |
| **Volleyball serving screen (as-is)** | No direct analogue; the transferable idea is "a tab for the signature skill," which is already what a pitching tab would be. |

Already built, despite appearing in the sibling comparison: standings
computation, season-data validator, ADB install scripts, pull-to-refresh sync,
gap-filling merge, payload validation.

---

## 6. Suggested first slice (one sitting)

**Phase 2's seed harvest + the scoring summary.** Concretely:

1. Stop discarding fields in `update-seed.py` (pitching + hitting + `spot` + venue).
2. Room v4→v5 migration with the new columns; update `MergeSyncTest`.
3. Capture `scoringSummaryPlays[]` and render it under the existing line score on the game detail overlay.

That one slice turns the thin box score into a real one, unblocks Phases 3–4,
and ships a visible feature (how every run scored) the same day.

---

## 7. Defects found along the way

Not part of the parity gap, but worth fixing:

- **Signing keystores are committed base64-encoded with their passwords** in the repo. Worth rotating and moving to GitHub secrets.
- **Roster cards show career totals, not current-season** — misleading now that 2027 players exist.
- **Doubleheader merge key is `date+opponent`** — the "(G2)" suffix is what keeps them apart; fragile if a source renames an opponent.
- **Screenshot test only captures, never compares** — no regression protection in CI.
- **`exportSchema=false`** means no schema JSON is committed, so migrations can't be verified against a golden schema.
- **Instrumented-test stub is dead weight** — fully configured dependency block, no tests.
