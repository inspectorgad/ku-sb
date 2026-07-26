# Data validation — KU Softball, Spring 2026

Result: **PASS**. The full 2026 season (57 games, Feb 6 – May 16, 36-21)
is retrievable, and per-player batting AND pitching are available with
clean, internally consistent data — but unlike the ku-wbb app, the primary
per-game source must be **kuathletics.com box scores**, with the NCAA API
used for game discovery and cross-validation. Every game has a Sidearm box
score; two never reach the NCAA feed (the May 7 Big 12 tournament game is
stuck in "pre" state, and game 2 of the Apr 10 Baylor doubleheader is
absent), and the Mar 1 Arkansas box is dated **3/1/1926** in Sidearm's own
payload — a century typo the seed builder corrects (before the fix it
produced a phantom 1926 duplicate alongside the NCAA result, which is why
early builds showed 58 games and a 36-22 record). Raw evidence lives in
`probe/` (NCAA season sweep) and `probe2/` (Sidearm box scores + NCAA
gaps), captured by the `probe-data.yml` / `probe2-data.yml` workflows on
2026-07-19.

## Source 1: NCAA API (`ncaa-api.henrygd.me`)

- Scanned the daily `softball/d1` scoreboard from 2026-02-01 to
  2026-06-15: **57 Kansas games found, 0 scan errors** — the regular
  season, 2 Big 12 tournament games, and a 3-game NCAA regional run
  ending 2026-05-16.
- **56/56 box scores captured** for games the feed marks final
  (`probe/games/`).
- Game results and **inning-by-inning line scores are reliable**
  (numbered `linescores` periods + contest team scores). The summary
  "R" linescore row is NOT (matches finals in only 21/56 games) — use the
  numbered innings and team scores instead.

### Quirks found (all with evidence in `probe/`)

1. **One game is missing entirely**: the May 7 Big 12 tournament win over
   UCF (6-5) is permanently stuck in `"pre"` state (game 6602297,
   `hasBoxscore: false` — `probe2/game-6602297-info.json`). It exists only
   on kuathletics.com.
2. **Per-player pitching lines exist in only 3 of 56 games** (the NCAA
   regional games). Regular-season games have `pitcherStats: null` for
   every player.
3. **Pitcher batting rows are polluted**: in regular-season games, a
   pitcher's `batterStats.runsScored` holds *runs allowed* (and
   `strikeouts` holds pitching Ks). With that model, non-pitcher runs sum
   to the team's final score in 105/112 team-games; the remainder are
   dual-role "dp/p" players whose batting and pitching numbers can't be
   split apart from this feed alone. (In the 3 tournament games,
   `batterStats` is clean and pitching lives in `pitcherStats`.)
4. **`hittingSeason` is game-level in regular-season games but
   season-cumulative in tournament games** — don't use it as a game line.
5. **Team `batterTotals` are unreliable** (match finals in only 72/112
   team-games; runs-allowed cross-pollution again).
6. **Player names arrive mangled** in many games: truncated at ~12 chars
   ("Lila Partrid"), initial forms ("A. Soles"), last-name-only, and
   "BarberB"/"BarberC" for the Barber sisters. All 74 raw KU name variants
   resolve unambiguously against the kuathletics roster with a
   roster-anchored matcher.

## Source 2: kuathletics.com (Sidearm, headless Chromium)

- **Roster page**: parsed cleanly — 26 players with name / jersey /
  position; every 2026 box-score name resolves to it (`probe/roster.json`).
- **Season stats page** (`/sports/softball/stats/2026`): carries links to
  **all 57 game box scores** (including id 20610 for the May 7 UCF game
  the NCAA feed is missing, and id 20446 for the Mar 1 Arkansas game —
  which Sidearm serves dated 3/1/1926; see above).
- **Box score pages embed complete structured data** in the
  `__NUXT_DATA__` JSON payload (devalue format, parsed successfully in
  `probe2/boxscore-*.html`): per-player hitting (AB, R, H, RBI, 2B, 3B,
  HR, BB, IBB, SO, HBP, SB, CS, SH, SF), **full per-pitcher lines** (IP,
  H, R, ER, BB, SO, W/L/S, GS, CG, SHO, batters faced, pitch count, …),
  fielding, team totals, inning line scores, W/L/S pitchers, venue,
  attendance, and play-by-play — with clean "Last, First" player names.

### Cross-validation

For the 2026-04-17 UCF game (NCAA 6544381 = Sidearm 20500), all 13 KU
batting lines match exactly (AB/H/RBI) between the two sources, and team
totals agree with the final score. Two independent sources, same numbers.

## Conclusion

Green light to build on the ku-wbb/ku-volleyball architecture with one
substitution in the pipeline:

- **Primary per-game stats**: kuathletics.com box score pages (Sidearm
  `__NUXT_DATA__` payload) — complete batting + pitching, all 57 games.
- **Game discovery / results cross-check**: NCAA API scoreboard sweep.
- **Roster**: kuathletics.com roster page (same scrape as ku-wbb).
- Softball is a single-calendar-year season: label seasons "2026", not
  "2025-26" (note: NCAA `seasonYear` is 2025 for spring 2026 — academic
  year start).

## Fall exhibitions (probe3, 2026-07-19)

KU plays fall pre-season exhibition games, but **no structured data for
them is published anywhere the pipeline can reach**:

- **NCAA API**: zero D1 softball games — for any team — on the scoreboard
  across the whole Sep 1 – Nov 30, 2025 window. The NCAA feed simply does
  not carry fall ball.
- **kuathletics.com**: the 2026 season schedule page starts with the
  Feb 6 openers — the Fall 2025 exhibitions were never listed, and no
  box scores exist for them (Sidearm ids 20426–20616 are all spring
  games). `/schedule/season/2027` and a fall-labeled schedule URL both
  404 (nothing posted yet as of July 2026), and `/stats/2027` is an
  empty shell.

Consequence: fall games cannot be auto-scraped; if fall results/stats are
wanted in the app they must be entered by hand (the Games tab supports
manual games + stat lines, and the sync merge never overwrites them). If
KU ever starts posting fall box scores, the scraper's stats-page sweep
picks up a new season by adding its year to `SEASONS` in scrape-data.yml.

## Big 12 standings & national rankings (probe4/probe5, 2026-07-26)

Ported from ku-wbb; softball differences validated before building:

- **Conference tags**: every softball scoreboard side carries
  `conferences[].conferenceSeo` ("big-12"), so conference membership and
  standings can be computed from the sweep, never a hardcoded team list.
- **Tournament fencing**: NCAA-tournament games are bracket-flagged on the
  scoreboard, but **Big 12 tournament games are NOT** (probe4, May 7-8) —
  so the conference tournament is fenced out of conference records by
  date: conference games within the 10 days before the season's first
  bracketed game don't count. Until regionals appear, tournament games
  briefly count as conference games; standings regenerate nightly, so
  this self-corrects within days.
- **Rankings**: softball has no AP poll on ncaa.com. The two sources are
  the **NCAA RPI** (API slug `ncaa-womens-softball-rpi`, 308 rows,
  final-2026 snapshot "Through Games Jun. 04 2026") and the
  **ESPN.com/USA Softball Top 25** — whose JSON endpoint 404s, but whose
  page (`/rankings/softball/d1/espncom/usa-softball`) is server-rendered
  HTML with a stable table (probe5), so the scraper parses that. Both
  serve only the current snapshot; each is keyed by the season in its
  "Through Games ..." label. RPI also carries official overall records,
  used as a cross-check on the computed standings — it is what confirmed
  KU's true 2026 record is 36-21 over 57 games.
