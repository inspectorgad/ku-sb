#!/usr/bin/env python3
"""Regenerates app/src/main/assets/seed.json from scraped/ KU Softball data.

Inputs (all optional, produced by scrape-ku-sb.mjs):
  scraped/sidearm-game-*.json  one per game from kuathletics.com box scores:
                               full batting + pitching lines (PRIMARY source)
  scraped/ku-index.json        NCAA scoreboard index (results fallback only —
                               the NCAA softball stat lines are unreliable,
                               see DATA-VALIDATION.md)
  scraped/roster.json          current roster from kuathletics.com
  scraped/upcoming.json        upcoming games from kuathletics.com

The seed is regenerated in full on every run — all data is scraper-owned, and
the app's Seeder merge is what protects user edits on-device.
"""
import glob
import json
import os
import re
from datetime import datetime, timedelta, timezone

SEED_PATH = "app/src/main/assets/seed.json"


def load_json(path, default):
    try:
        with open(path) as f:
            return json.load(f)
    except (OSError, json.JSONDecodeError):
        return default


def to_int(value):
    try:
        return int(str(value).strip() or 0)
    except ValueError:
        return 0


def decision(value):
    """Sidearm marks a pitching decision by putting the pitcher's updated
    record in the wins/losses field ("8-5"); "0" (or blank) means no
    decision. Saves are a plain count."""
    return 0 if str(value or "0").strip() in ("", "0") else 1


def innings_to_outs(value):
    """Sidearm reports innings like "6.0"/"7.2"; store as outs (7.2 -> 23)."""
    text = str(value or "0").strip()
    parts = text.split(".")
    whole = to_int(parts[0])
    frac = to_int(parts[1][:1]) if len(parts) > 1 else 0
    return whole * 3 + min(frac, 2)


def season_of(date):
    """Season label for an ISO date.

    D1 softball's championship season runs February to June inside one
    calendar year, so those games are labeled by that year ("2026"). Fall
    games (August-December) are pre-season exhibitions belonging to the
    NEXT spring season and do not count toward any official record, so they
    get their own label ("Fall 2026") — that keeps them out of the season's
    W-L, batting, and pitching totals while still showing up in the app as
    their own selectable season.
    """
    year, month = int(date[:4]), int(date[5:7])
    return f"Fall {year}" if month >= 8 else str(year)


def iso_date(mdy):
    """Sidearm dates are M/D/YYYY. Guard against century typos in the source
    (the 2026 Arkansas box was posted dated 3/1/1926)."""
    try:
        m, d, y = str(mdy).strip().split("/")
        year = int(y)
        while year < 2000:
            year += 100
        return f"{year}-{int(m):02d}-{int(d):02d}"
    except (ValueError, AttributeError):
        return None


def flip_name(last_first):
    """Sidearm names are "Last, First" -> "First Last"."""
    if "," in (last_first or ""):
        last, first = last_first.split(",", 1)
        return f"{first.strip()} {last.strip()}".strip()
    return (last_first or "").strip()


def norm_team(name):
    """Canonical key for cross-source name matching ('Iowa State'/'Iowa St.')."""
    n = re.sub(r"\s*\(G\d\)\s*$", "", name or "")     # doubleheader marker
    n = re.sub(r"\s*\(\d+\)\s*$", "", n).lower()      # poll first-place votes
    n = n.replace(".", "")
    n = re.sub(r"\bstate\b", "st", n)
    return re.sub(r"\s+", " ", n).strip()


players = {}  # name.lower() -> {name, jerseyNumber, position}
games = {}  # (date, opponent.lower()) -> game dict


# Roster detail beyond name, number and position. Only the current roster page
# carries these, so a player who last appeared in an earlier season keeps
# whatever was recorded then and gains nothing new — which is correct: her
# class year was what it was.
ROSTER_DETAIL = ("academicYear", "height", "batsThrows", "hometown", "lastSchool")


def add_player(name, jersey, position, prefer=False, detail=None):
    if not name:
        return
    existing = players.get(name.lower())
    if existing is None:
        existing = {"name": name, "jerseyNumber": jersey, "position": position}
        players[name.lower()] = existing
    elif prefer:
        existing["name"] = name
        if jersey:
            existing["jerseyNumber"] = jersey
        if position:
            existing["position"] = position
    for key in ROSTER_DETAIL:
        value = ((detail or {}).get(key) or "").strip()
        if value:
            existing[key] = value


# --- Current roster first (canonical names, numbers, positions) --------------
roster = load_json("scraped/roster.json", [])
roster_names = set()
for entry in roster:
    name = (entry.get("name") or "").strip()
    roster_names.add(name)
    add_player(
        name,
        str(entry.get("jerseyNumber") or ""),
        (entry.get("position") or "").strip(),
        prefer=True,
        detail=entry,
    )


def canonical_name(name):
    """Aligns a box-score name with the roster spelling where possible:
    exact (case-insensitive) match first, then unique last-name +
    first-initial match ("Samantha Claire" -> roster "Sam Claire")."""
    if name.lower() in players:
        return players[name.lower()]["name"]
    parts = name.split()
    if len(parts) >= 2:
        first, last = parts[0], parts[-1]
        matches = [
            r for r in roster_names
            if r.split()[-1].lower() == last.lower()
            and r.split()[0][:1].lower() == first[:1].lower()
        ]
        if len(matches) == 1:
            return matches[0]
    return name


# --- Games from Sidearm box scores (primary source) --------------------------
def parse_sidearm_game(data):
    home, visit = data.get("homeTeam"), data.get("visitingTeam")
    if not home or not visit:
        return None
    if home.get("isTenantTeam") or home.get("name") == "Kansas":
        ku, opp, ku_home = home, visit, True
    elif visit.get("isTenantTeam") or visit.get("name") == "Kansas":
        ku, opp, ku_home = visit, home, False
    else:
        return None
    date = iso_date(data.get("gameDate")) or iso_date((data.get("venue") or {}).get("date"))
    if not date:
        return None

    ku_sum = ku.get("scoringSummary") or {}
    opp_sum = opp.get("scoringSummary") or {}
    ku_innings = [s.strip() for s in str(ku_sum.get("scoreByInnings") or "").split(",") if s.strip()]
    opp_innings = [s.strip() for s in str(opp_sum.get("scoreByInnings") or "").split(",") if s.strip()]
    n = max(len(ku_innings), len(opp_innings))
    inning_scores = ", ".join(
        f"{ku_innings[i] if i < len(ku_innings) else 'x'}-"
        f"{opp_innings[i] if i < len(opp_innings) else 'x'}"
        for i in range(n)
    )

    game = {
        "date": date,
        "opponent": (opp.get("name") or "Unknown").strip(),
        "season": season_of(date),
        "teamScore": to_int(ku_sum.get("runs") if ku_sum.get("runs") is not None else ku.get("score")),
        "opponentScore": to_int(opp_sum.get("runs") if opp_sum.get("runs") is not None else opp.get("score")),
        "inningScores": inning_scores,
        "teamHits": to_int(ku_sum.get("hits")),
        "opponentHits": to_int(opp_sum.get("hits")),
        "teamErrors": to_int(ku_sum.get("errors")),
        "opponentErrors": to_int(opp_sum.get("errors")),
        "lines": [],
        "_sidearmId": to_int(data.get("sidearmId")),
        "_dh": to_int((data.get("venue") or {}).get("doubleHeaderGame")),
    }

    # Game context the payload always carried: where it was played, how many
    # people saw it, and a link to the official box score.
    venue = data.get("venue") or {}
    where = (venue.get("location") or "").strip()
    if where:
        game["venue"] = where
    attendance = to_int(venue.get("attendance"))
    if attendance:
        game["attendance"] = attendance
    if data.get("url"):
        game["boxScoreUrl"] = data["url"]
    if data.get("pdfDoc"):
        game["pdfUrl"] = data["pdfDoc"]

    # The game-info panel: what it was like to be there. All of this was
    # already captured and none of it was kept.
    stadium = (venue.get("stadium") or "").strip()
    if stadium:
        game["stadium"] = stadium
    for key, src in (("firstPitch", "start"), ("duration", "duration"),
                     ("weather", "weather")):
        v = (venue.get(src) or "").strip()
        if v:
            game[key] = v
    # Umpires arrive as {"Home Plate": "...", "First": "..."}; flattened to
    # "Home Plate: Craig Hyde · First: Joshua Fo..." in the order a box score
    # prints them, since nothing needs them separately.
    crew = venue.get("umpires") or {}
    if isinstance(crew, dict):
        order = ["Home Plate", "First", "Second Base", "Third Base",
                 "Left Field", "Right Field"]
        named = [f"{k}: {crew[k]}" for k in order if (crew.get(k) or "").strip()]
        named += [f"{k}: {v}" for k, v in crew.items()
                  if k not in order and (v or "").strip()]
        if named:
            game["umpires"] = " · ".join(named)

    # How long the game was scheduled to be, which is the only way to know a
    # short game was a run-rule rather than one that was called. Softball ends
    # after five innings when a team leads by eight, and the app could not
    # tell that from a rain-shortened game.
    scheduled = to_int(venue.get("scheduledInnings"))
    if scheduled:
        game["scheduledInnings"] = scheduled

    # Who the opponent was at the time, which is the half of a result that the
    # score alone never tells you: beating a 36-13 team is not the same game as
    # beating a 9-40 one. Both fields are as the box score reported them on the
    # day, so they describe THIS game rather than how the opponent's season
    # finished — which is the version worth keeping, and the only one available.
    #
    # The record field packs a conference record after a comma, and that half is
    # unreliable: it comes through duplicated ("29-11, 29-11") and as a bare "0".
    # Only the overall record ahead of the comma is kept, and only when it really
    # looks like one.
    overall = (opp.get("record") or "").split(",")[0].strip()
    if re.fullmatch(r"\d+-\d+(-\d+)?", overall):
        game["opponentRecord"] = overall
    opp_rank = to_int(opp.get("rank"))
    if opp_rank:
        game["opponentRank"] = opp_rank

    # How every run scored. Stored compactly: inning, whether KU scored it, the
    # narrative, and the score after the play from KU's perspective.
    scoring = []
    for play in data.get("scoringSummaryPlays") or []:
        narrative = (play.get("playNarrative") or "").strip()
        if not narrative:
            continue
        visiting = to_int(play.get("visitingScore"))
        home = to_int(play.get("homeScore"))
        scoring.append({
            "inn": to_int(play.get("inningNumber")),
            "ku": bool(play.get("isVisitingTeam")) != bool(ku_home),
            "text": narrative,
            "us": home if ku_home else visiting,
            "them": visiting if ku_home else home,
        })
    if scoring:
        game["scoring"] = scoring

    for p in ku.get("players") or []:
        hitting = p.get("hitting")
        pitching = p.get("pitching")
        if not hitting and not pitching:
            continue
        name = flip_name(p.get("name"))
        add_player(name, str(p.get("uniform") or ""), "")
        line = {"player": name, "gs": 1 if to_int(p.get("gameStarted")) else 0}
        # Lineup slot and the position played in THIS game, so a box score can
        # be shown in batting order with starters separated from the bench.
        spot = to_int(p.get("spot"))
        if spot:
            line["spot"] = spot
        pos = (p.get("position") or "").strip()
        if pos:
            line["pos"] = pos
        # The payload's "substitute" is a substitution SEQUENCE, not a flag:
        # "0" for a starter, "1" for the first player into that slot, "2" for
        # the next. It arrives as a string, so a plain truth test called every
        # one of them a substitute — which it did, for all 816 lines. Anything
        # above zero is a substitute; which substitute is recoverable from row
        # order, since the payload lists them in the order they entered.
        if to_int(p.get("substitute")) > 0:
            line["sub"] = 1
        h = hitting or {}
        line.update({
            "ab": to_int(h.get("atBats")),
            "r": to_int(h.get("runsScored")),
            "h": to_int(h.get("hits")),
            "2b": to_int(h.get("doubles")),
            "3b": to_int(h.get("triples")),
            "hr": to_int(h.get("homeRuns")),
            "rbi": to_int(h.get("runsBattedIn")),
            "bb": to_int(h.get("walks")),
            "so": to_int(h.get("strikeouts")),
            "hbp": to_int(h.get("hitByPitch")),
            "sb": to_int(h.get("stolenBases")),
            "cs": to_int(h.get("caughtStealing")),
            "sf": to_int(h.get("sacrificeFlies")),
            "sh": to_int(h.get("sacrificeHits")),
        })
        # Advanced hitting detail the payload always carried and the seed threw
        # away. Sparse by design: a zero is the overwhelming common case, so
        # only non-zero values are written.
        for key, src in (
            ("kl", "strikeoutsLooking"), ("roe", "reachedOnError"),
            ("fc", "reachesOnAFieldersChoice"), ("go", "groundOuts"),
            ("ao", "flyOuts"), ("gidp", "groundedIntoDoublePlay"),
            ("ibb", "intentionalWalks"), ("pko", "pickedOff"),
        ):
            v = to_int(h.get(src))
            if v:
                line[key] = v
        f = p.get("fielding") or {}
        for key, src in (
            ("po", "putouts"), ("a", "assists"), ("e", "errors"),
            ("pb", "passedBalls"), ("sba", "stolenBasesAgainst"),
            ("csb", "caughtStealingBy"), ("dp", "involvedInDoublePlays"),
        ):
            v = to_int(f.get(src))
            if v:
                line[key] = v
        if pitching:
            line.update({
                "p": 1,
                "outs": innings_to_outs(pitching.get("inningsPitched")),
                "ha": to_int(pitching.get("hitsAllowed")),
                "ra": to_int(pitching.get("runsAllowed")),
                "er": to_int(pitching.get("earnedRunsAllowed")),
                "bba": to_int(pitching.get("walksAllowed")),
                "ks": to_int(pitching.get("strikeouts")),
                "hra": to_int(pitching.get("homerunsAllowed")),
                "w": decision(pitching.get("wins")),
                "l": decision(pitching.get("losses")),
                "sv": decision(pitching.get("saves")),
            })
            # Workload and efficiency detail: pitch count and batters faced are
            # what make rest-day and pitches-per-batter analysis possible.
            for key, src in (
                ("np", "pitches"), ("bf", "battersFaced"), ("wp", "wildPitches"),
                ("hbA", "hitBatters"), ("bk", "balks"), ("ir", "inheritedRunners"),
                ("irs", "inheritedRunnersThatScored"), ("cg", "gamesCompleted"),
                ("sho", "shutouts"), ("gsp", "gamesStarted"),
                ("ksl", "strikeoutsLooking"), ("oab", "opponentAtBats"),
            ):
                v = to_int(pitching.get(src))
                if v:
                    line[key] = v
        game["lines"].append(line)

    # The other side's box score, downloaded with every game and until now
    # thrown away. Stored flat rather than through the player table: these are
    # not KU players and must never reach the roster, the leaderboards or a
    # career total.
    #
    # Deliberately a smaller set of columns than KU's lines carry. This exists
    # so an opposing box score can be read — who hit, who pitched, who made the
    # plays — not so anyone can compute an opponent's season advanced metrics
    # from the handful of games they played against Kansas, which would be a
    # sample of two or three and worse than no number at all.
    opponent_lines = []
    for p in opp.get("players") or []:
        hitting = p.get("hitting")
        pitching = p.get("pitching")
        fielding = p.get("fielding") or {}
        if not hitting and not pitching:
            continue
        name = flip_name(p.get("name"))
        if not name:
            continue
        row = {"player": name, "number": str(p.get("uniform") or "").strip()}
        pos = (p.get("position") or "").strip()
        if pos:
            row["pos"] = pos
        spot = to_int(p.get("spot"))
        if spot:
            row["spot"] = spot
        if to_int(p.get("gameStarted")):
            row["gs"] = 1
        if to_int(p.get("substitute")) > 0:
            row["sub"] = 1
        h = hitting or {}
        for key, src in (
            ("ab", "atBats"), ("r", "runsScored"), ("h", "hits"),
            ("2b", "doubles"), ("3b", "triples"), ("hr", "homeRuns"),
            ("rbi", "runsBattedIn"), ("bb", "walks"), ("so", "strikeouts"),
            ("hbp", "hitByPitch"), ("sb", "stolenBases"),
        ):
            v = to_int(h.get(src))
            if v:
                row[key] = v
        for key, src in (("po", "putouts"), ("a", "assists"), ("e", "errors")):
            v = to_int(fielding.get(src))
            if v:
                row[key] = v
        if pitching:
            row["p"] = 1
            row["outs"] = innings_to_outs(pitching.get("inningsPitched"))
            for key, src in (
                ("ha", "hitsAllowed"), ("ra", "runsAllowed"),
                ("er", "earnedRunsAllowed"), ("bba", "walksAllowed"),
                ("ks", "strikeouts"), ("hra", "homerunsAllowed"),
                ("np", "pitches"), ("bf", "battersFaced"),
            ):
                v = to_int(pitching.get(src))
                if v:
                    row[key] = v
            for key, src in (("w", "wins"), ("l", "losses"), ("sv", "saves")):
                if decision(pitching.get(src)):
                    row[key] = 1
        opponent_lines.append(row)
    if opponent_lines:
        game["opponentLines"] = opponent_lines
    return game


sidearm_games = []
for path in sorted(glob.glob("scraped/sidearm-game-*.json")):
    data = load_json(path, None)
    if not data:
        continue
    game = parse_sidearm_game(data)
    if game:
        sidearm_games.append(game)

# Doubleheaders: two games can share date + opponent; the app keys games by
# that pair, so the second game gets a visible "(G2)" marker. Order within
# the day by Sidearm's doubleheader flag, then box score id.
sidearm_games.sort(key=lambda g: (g["date"], g["opponent"].lower(), g["_dh"], g["_sidearmId"]))
day_counts = {}
for game in sidearm_games:
    key = (game["date"], game["opponent"].lower())
    day_counts[key] = day_counts.get(key, 0) + 1
    if day_counts[key] > 1:
        game["opponent"] = f"{game['opponent']} (G{day_counts[key]})"
    # _sidearmId is kept until the schedule pass below can match on it, then
    # stripped along with _dh before the seed is written.
    games[(game["date"], game["opponent"].lower())] = game

# --- NCAA results as a fallback for games with no Sidearm box ----------------
# Matched by date + final score so differing team-name styles can't duplicate
# a game ("Iowa St." vs "Iowa State").
sidearm_results = {}
for game in games.values():
    key = (game["date"], game["teamScore"], game["opponentScore"])
    sidearm_results[key] = sidearm_results.get(key, 0) + 1

index = load_json("scraped/ku-index.json", {})
for game_id, meta in sorted((index.get("ncaaGames") or {}).items()):
    if not meta.get("final"):
        continue
    date = meta.get("date") or ""
    key = (date, to_int(meta.get("kuScore")), to_int(meta.get("oppScore")))
    if sidearm_results.get(key):
        sidearm_results[key] -= 1
        continue
    opponent = (meta.get("opponent") or "Unknown").strip()
    gkey = (date, opponent.lower())
    if gkey in games:
        continue
    games[gkey] = {
        "date": date,
        "opponent": opponent,
        "season": season_of(date),
        "teamScore": to_int(meta.get("kuScore")),
        "opponentScore": to_int(meta.get("oppScore")),
    }

# --- Roster-derived active flags ---------------------------------------------
# active = on the current scraped roster. A failed/empty roster scrape must
# not mass-retire the team, so with an implausibly small roster the previous
# seed's flags are carried forward instead.
previous_seed = load_json(SEED_PATH, {})
previous_active = {
    (p.get("name") or "").lower(): p.get("active", True)
    for p in previous_seed.get("players", [])
}
roster_keys = {n.lower() for n in roster_names}
roster_valid = len(roster_keys) >= 8
for key, player in players.items():
    if roster_valid:
        player["active"] = key in roster_keys
    else:
        player["active"] = previous_active.get(key, True)

# Align stat-line names with canonical roster spellings so the app can match
# them up, folding any variant-name player entries into the canonical one.
for game in games.values():
    for line in game.get("lines", []):
        line["player"] = canonical_name(line["player"])
for key in list(players):
    canon = canonical_name(players[key]["name"])
    if canon.lower() != key:
        variant = players.pop(key)
        target = players.get(canon.lower())
        if target is not None and not target.get("jerseyNumber"):
            target["jerseyNumber"] = variant.get("jerseyNumber", "")

# --- The posted schedule: home/away for every game, plus unplayed ones -------
# scraped/schedule-<season>.json holds the full slate as KU publishes it, so a
# newly-posted season shows up complete (home AND away) before a pitch is
# thrown. Played games are matched to their box-score-derived entry — by
# Sidearm id where possible, since that is literally the box score id, and by
# date + opponent otherwise — and annotated rather than duplicated.
by_sidearm_id = {}
for game in games.values():
    if game.get("_sidearmId"):
        by_sidearm_id[str(game["_sidearmId"])] = game

schedule_entries = []
for path in sorted(glob.glob("scraped/schedule-*.json")):
    schedule_entries.extend(load_json(path, []))

annotated = added = 0
# A doubleheader publishes two schedule rows with the same date and opponent
# (KU's Sept 26 2026 trip to Nebraska is 12:00 and 2:30). Each row must land
# on its own game: without tracking what a row has already claimed, the
# second row re-matches the first row's game and overwrites its start time,
# silently losing a game from the schedule.
claimed = set()
for entry in schedule_entries:
    date = (entry.get("date") or "").strip()
    opponent = (entry.get("opponent") or "").strip()
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", date) or not opponent:
        continue
    site = (entry.get("site") or "").strip().upper()[:1]
    start = (entry.get("time") or "").strip()
    # The event a game belonged to, named by the schedule rather than guessed
    # from its date. Trailing space is in the source: "USF-Rawlings
    # Invitational ".
    tournament = ((entry.get("tournament") or {}).get("title") or "").strip() \
        if isinstance(entry.get("tournament"), dict) else ""

    game = by_sidearm_id.get(str(entry.get("sidearmId") or ""))
    if game is None:
        # Fall back to date + opponent, preferring a game no schedule row has
        # claimed yet and that isn't already annotated.
        candidates = [
            g for g in games.values()
            if g["date"] == date and norm_team(g["opponent"]) == norm_team(opponent)
        ]
        unclaimed = [g for g in candidates if id(g) not in claimed]
        game = next((g for g in unclaimed if not g.get("site")), None) or \
            (unclaimed[0] if unclaimed else None)
    elif id(game) in claimed:
        game = None

    if game is not None:
        claimed.add(id(game))
        if site:
            game["site"] = site
        if start:
            game["startTime"] = start
        if tournament:
            game["event"] = tournament
        annotated += 1
        continue

    # Not in the seed: an unplayed game (or one whose box score isn't posted).
    # Later games of a doubleheader take the same "(G2)" suffix the played
    # box-score path uses, so both survive as distinct games.
    label = opponent
    key = (date, label.lower())
    dup = 1
    while key in games:
        dup += 1
        label = f"{opponent} (G{dup})"
        key = (date, label.lower())
    games[key] = {
        "date": date,
        "opponent": label,
        "season": season_of(date),
        "site": site,
        "startTime": start,
    }
    if tournament:
        games[key]["event"] = tournament
    claimed.add(id(games[key]))
    added += 1

if schedule_entries:
    print(
        f"schedule: {len(schedule_entries)} posted games — "
        f"{annotated} matched to existing entries, {added} added as unplayed"
    )

# --- Results KU never publishes (fall exhibitions) ---------------------------
# Fall ball results don't appear anywhere on kuathletics (no box score, no
# schedule result, no recap) and the NCAA feed carries no fall softball at
# all — but opponents post them. scraped/fall-results.json records those,
# each with its source, and they are applied here by date + scheduled start,
# which is what tells the halves of a doubleheader apart. A game that later
# gains a real box score keeps the scraped version: this only fills a score
# that is still missing.
fall_results = load_json("scraped/fall-results.json", [])
applied = unmatched = 0
for entry in fall_results:
    date = (entry.get("date") or "").strip()
    start = (entry.get("startTime") or "").strip()
    opponent = (entry.get("opponent") or "").strip()
    match = next(
        (
            g for g in games.values()
            if g["date"] == date
            and g.get("startTime", "") == start
            and norm_team(g["opponent"]) == norm_team(opponent)
        ),
        None,
    )
    if match is None:
        unmatched += 1
        print(f"  fall result unmatched (no scheduled game): {date} {start} vs {opponent}")
        continue
    if match.get("teamScore") is not None:
        continue  # a published box score won; never overwrite it
    match["teamScore"] = to_int(entry.get("teamScore"))
    match["opponentScore"] = to_int(entry.get("opponentScore"))
    applied += 1
if fall_results:
    print(
        f"fall results: {applied} applied from opponent sources"
        + (f", {unmatched} unmatched" if unmatched else "")
    )

# Legacy rotator fallback, used only when no schedule payload was captured.
today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
if not schedule_entries:
    for entry in load_json("scraped/upcoming.json", []):
        date = entry.get("date", "")
        opponent = (entry.get("opponent") or "").strip()
        if not date or not opponent or date < today:
            continue
        key = (date, opponent.lower())
        if key in games:
            continue
        games[key] = {
            "date": date,
            "opponent": opponent,
            "season": season_of(date),
            "site": (entry.get("site") or "").strip().upper()[:1],
        }

# Internal matching keys never reach the seed.
for game in games.values():
    game.pop("_sidearmId", None)
    game.pop("_dh", None)

# --- Big 12 standings, computed from the scoreboard sweep -------------------
# Conference records are derived from the Big 12 games the sweep collects
# (every scoreboard team carries a conference tag). This also means any season
# can be rebuilt retroactively, which a live standings endpoint could not do.
index = load_json("scraped/ku-index.json", {})
big12_games = list(index.get("big12Games", {}).values())

# Conference records are regular-season only. Softball's Big 12 tournament
# carries NO bracket fields on the scoreboard (probe4), so it is fenced by
# date instead: NCAA-tournament games ARE bracket-flagged, and the conference
# tournament always sits in the ~10 days before regionals start. Until a
# season's regionals appear, tournament games briefly count as conference
# games — standings regenerate nightly, so this self-corrects within days.
ncaa_start = {}  # season -> date of first bracketed game involving a Big 12 team
for game in big12_games:
    if game.get("bracket"):
        season = game.get("season") or game.get("date", "")[:4]
        date = game.get("date", "")
        if season not in ncaa_start or date < ncaa_start[season]:
            ncaa_start[season] = date


def conf_fence(season):
    start = ncaa_start.get(season)
    if not start:
        return None
    return (datetime.strptime(start, "%Y-%m-%d") - timedelta(days=10)).strftime("%Y-%m-%d")


records = {}  # (season, key) -> record dict
for game in big12_games:
    season = game.get("season") or game.get("date", "")[:4]
    fence = conf_fence(season)
    conf_game = (
        bool(game.get("conferenceGame"))
        and not game.get("bracket")
        and (fence is None or game.get("date", "") < fence)
    )
    for side in ("home", "away"):
        s = game.get(side) or {}
        if not s.get("inConference") or not s.get("name"):
            continue  # non-conference opponents get no standings row
        rec = records.setdefault(
            (season, norm_team(s["name"])),
            {
                "season": season,
                "team": s["name"],
                "seo": s.get("seo", ""),
                "confW": 0, "confL": 0, "overallW": 0, "overallL": 0,
                "_rankDate": "", "nationalRank": None,
            },
        )
        won = bool(s.get("winner"))
        rec["overallW" if won else "overallL"] += 1
        if conf_game:
            rec["confW" if won else "confL"] += 1
        # Keep the most recent rank the scoreboard reported that season.
        if s.get("rank") and game.get("date", "") >= rec["_rankDate"]:
            rec["_rankDate"] = game["date"]
            rec["nationalRank"] = s["rank"]

# The NCAA feed misses two KU games the Sidearm seed has (May 7 UCF, the
# Apr 10 Baylor doubleheader game 2 — see DATA-VALIDATION.md), so records
# touching KU are patched from the seed, which is authoritative for KU.
ncaa_ku_keys = set()
for game in big12_games:
    for side, other in (("home", "away"), ("away", "home")):
        if (game.get(side) or {}).get("seo") == "kansas":
            us, them = game[side], game[other]
            ncaa_ku_keys.add((game.get("date"), us.get("score"), them.get("score")))
for game in games.values():
    if game.get("teamScore") is None:
        continue
    key = (game["date"], game["teamScore"], game["opponentScore"])
    if key in ncaa_ku_keys:
        continue
    season = game["season"]
    fence = conf_fence(season)
    ku = records.get((season, norm_team("Kansas")))
    if ku is None:
        continue  # sweep hasn't run for this season yet
    opp = records.get((season, norm_team(game["opponent"])))
    won = game["teamScore"] > game["opponentScore"]
    conf_game = opp is not None and (fence is None or game["date"] < fence)
    print(
        f"  patching NCAA-missing game into standings: {game['date']} vs "
        f"{game['opponent']} ({'W' if won else 'L'}, {'conference' if conf_game else 'overall only'})"
    )
    ku["overallW" if won else "overallL"] += 1
    if opp is not None:
        opp["overallL" if won else "overallW"] += 1
    if conf_game:
        ku["confW" if won else "confL"] += 1
        opp["confL" if won else "confW"] += 1

# --- Rankings snapshots (ESPN.com/USA Softball poll + NCAA RPI) --------------
# Both sources serve only the current snapshot, so each is keyed by the season
# in its "Through Games JUN. 5, 2026" label (softball seasons sit inside one
# calendar year; an Aug-Dec label would be a preseason poll for the next one).
MONTHS = "JAN FEB MAR APR MAY JUN JUL AUG SEP OCT NOV DEC".split()


def snapshot_season(payload):
    label = (payload.get("updated", "") or "").upper()
    m = re.search(r"\b(" + "|".join(MONTHS) + r")[A-Z]*\.?\s+(\d{1,2}),?\s+(20\d{2})", label)
    if not m:
        return None
    month = MONTHS.index(m.group(1)) + 1
    year = int(m.group(3))
    return str(year if month <= 7 else year + 1)


def row_value(row, *candidates):
    """First matching column: sources vary in header capitalization."""
    lowered = {k.strip().lower(): v for k, v in row.items()}
    for c in candidates:
        if c in lowered:
            return lowered[c]
    return None


polls = []
poll = load_json("scraped/rankings-poll.json", {})
rpi = load_json("scraped/rankings-rpi.json", {})

rpi_season = snapshot_season(rpi)
rpi_by_team = {}
for row in rpi.get("data", []):
    if (row_value(row, "conf", "conference") or "") == "Big 12":
        rpi_by_team[norm_team(row_value(row, "school", "team") or "")] = row

# RPI carries each team's official overall record — fold in the RPI rank and
# cross-check our computed record against it (a warning, never a failure: the
# snapshot and our sweep can legitimately sit a game apart mid-season).
for (season, key), rec in records.items():
    row = rpi_by_team.get(key)
    if not row or season != rpi_season:
        continue
    rank = str(row_value(row, "rank") or "")
    rec["rpiRank"] = int(rank) if rank.isdigit() else None
    official = (row_value(row, "record") or "").strip()
    ours = f"{rec['overallW']}-{rec['overallL']}"
    if official and official != ours:
        print(f"  cross-check: {rec['team']} computed {ours} vs RPI {official}")

poll_season = snapshot_season(poll)
if poll.get("data") and poll_season:
    b12_keys = {k for (s, k) in records if s == poll_season}
    rows = []
    for row in poll["data"]:
        label = str(row_value(row, "rank") or "").strip()  # can be a tie, e.g. "T-22"
        digits = re.search(r"\d+", label)
        raw_team = (row_value(row, "school", "college", "team") or "").strip()
        team = re.sub(r"\s*\(\d+\)\s*$", "", raw_team).strip()
        votes = re.search(r"\((\d+)\)\s*$", raw_team)
        rows.append({
            "rank": int(digits.group()) if digits else 0,
            "rankLabel": label.rstrip("."),
            "team": team,
            "record": (row_value(row, "record") or "").strip(),
            "points": (row_value(row, "points", "total points") or "").strip(),
            "previous": (row_value(row, "previous", "previous rank", "prev") or "").strip(),
            "firstPlaceVotes": int(votes.group(1)) if votes else 0,
            "big12": norm_team(team) in b12_keys,
        })
    polls.append({
        "season": poll_season,
        "name": poll.get("title") or "ESPN.com/USA Softball Top 25",
        "updated": (poll.get("updated") or "").strip(),
        "rows": rows,
    })

    # The poll is the authoritative ranking. The scoreboard's per-game rank is
    # only "the rank this team carried in that game", so a team that fell out
    # of the top 25 would otherwise keep a stale number forever. Where we hold
    # a poll for the season, it decides: absent from the poll means unranked.
    poll_rank = {norm_team(r["team"]): r["rank"] for r in rows}
    restated = 0
    for (season, key), rec in records.items():
        if season != poll_season:
            continue
        fresh = poll_rank.get(key)
        if fresh != rec["nationalRank"]:
            restated += 1
        rec["nationalRank"] = fresh
    if restated:
        print(f"  national ranks restated from the poll for {restated} teams")
    print(
        f"  poll {poll_season}: {len(rows)} teams, "
        f"{sum(1 for r in rows if r['big12'])} from the Big 12"
    )

# --- Ranking history --------------------------------------------------------
# Neither source serves a past week, so the scrape files a dated copy of every
# snapshot it sees under scraped/rankings/ and the history is read back from
# there. A rank on its own says little; a rank next to the eight before it is
# the story of a season, and a week not captured while it was current cannot
# be recovered later.
#
# Only Kansas and the Big 12 are carried through to the seed. A full top-25
# poll plus a 300-row RPI table every week would outgrow the rest of the seed
# within a season, and nothing in the app or dashboard asks about the others.
def ranking_history():
    history = []
    b12_keys = {key for (_season, key) in records}
    wanted = b12_keys | {norm_team("Kansas")}
    for path in sorted(glob.glob("scraped/rankings/*.json")):
        name = os.path.basename(path)
        match = re.match(r"(poll|rpi)-(\d{4}-\d{2}-\d{2})\.json$", name)
        if not match:
            print(f"  skipping unrecognised ranking archive: {name}")
            continue
        source, date = match.group(1), match.group(2)
        snap = load_json(path, {})
        teams = []
        for row in snap.get("data", []):
            raw = (row_value(row, "school", "college", "team") or "").strip()
            # The poll writes first-place votes into the team cell, e.g.
            # "Texas (25)"; RPI does not. Strip before matching either way.
            team = re.sub(r"\s*\(\d+\)\s*$", "", raw).strip()
            if norm_team(team) not in wanted:
                continue
            label = str(row_value(row, "rank") or "").strip()
            digits = re.search(r"\d+", label)
            entry = {
                "team": team,
                "rank": int(digits.group()) if digits else None,
                "record": (row_value(row, "record") or "").strip(),
            }
            if source == "poll":
                votes = re.search(r"\((\d+)\)\s*$", raw)
                entry["points"] = (row_value(row, "points", "total points") or "").strip()
                entry["firstPlaceVotes"] = int(votes.group(1)) if votes else 0
            teams.append(entry)
        if not teams:
            continue
        history.append({
            "date": date,
            "source": source,
            "season": snapshot_season(snap) or date[:4],
            "label": (snap.get("updated") or "").strip(),
            "teams": sorted(teams, key=lambda t: (t["rank"] is None, t["rank"] or 0, t["team"])),
        })
    history.sort(key=lambda h: (h["date"], h["source"]))
    return history


rankings = ranking_history()
if rankings:
    weeks = len({h["date"] for h in rankings})
    ku = sum(1 for h in rankings
             if any(norm_team(t["team"]) == norm_team("Kansas") for t in h["teams"]))
    print(f"ranking history: {len(rankings)} snapshots over {weeks} dates "
          f"({ku} carrying a Kansas row)")

# Sorted by conference win %, then conference wins, then overall win % — NOT
# official Big 12 tiebreakers (those use head-to-head); the UI says as much.
def standing_sort(rec):
    conf_games = rec["confW"] + rec["confL"]
    overall = rec["overallW"] + rec["overallL"]
    return (
        -(rec["confW"] / conf_games if conf_games else 0),
        -rec["confW"],
        -(rec["overallW"] / overall if overall else 0),
        rec["team"],
    )


standings = []
for rec in sorted(records.values(), key=standing_sort):
    standings.append({k: v for k, v in rec.items() if not k.startswith("_")})
if standings:
    seasons = sorted({r["season"] for r in standings})
    print(f"standings computed for seasons {', '.join(seasons)}: {len(standings)} team rows")

seed = {
    "formatVersion": 1,
    "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "team": "Kansas Jayhawks Softball",
    "players": sorted(players.values(), key=lambda p: p["name"]),
    "games": [games[k] for k in sorted(games)],
}
# Additive only: formatVersion stays 1 so already-installed APKs (which reject
# anything newer) keep syncing, and older seeds without these keys stay valid.
if standings:
    seed["standings"] = standings
if polls:
    seed["polls"] = polls
if rankings:
    seed["rankingHistory"] = rankings

os.makedirs(os.path.dirname(SEED_PATH), exist_ok=True)

# Skip the write when nothing but the timestamp would change, so the nightly
# job doesn't commit (and rebuild the APK) on quiet days.
previous = load_json(SEED_PATH, {})
current_cmp = {k: v for k, v in seed.items() if k != "generatedAt"}
previous_cmp = {k: v for k, v in previous.items() if k != "generatedAt"}
if current_cmp == previous_cmp:
    print("seed.json unchanged (ignoring timestamp); not rewriting")
else:
    with open(SEED_PATH, "w") as f:
        json.dump(seed, f, indent=1)
        f.write("\n")
    print(
        f"seed.json written: {len(seed['players'])} players, "
        f"{len(seed['games'])} games "
        f"({sum(1 for g in seed['games'] if 'teamScore' in g)} with results)"
    )
