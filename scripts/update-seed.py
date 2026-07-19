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
from datetime import datetime, timezone

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


def iso_date(mdy):
    """Sidearm dates are M/D/YYYY."""
    try:
        m, d, y = str(mdy).strip().split("/")
        return f"{y}-{int(m):02d}-{int(d):02d}"
    except (ValueError, AttributeError):
        return None


def flip_name(last_first):
    """Sidearm names are "Last, First" -> "First Last"."""
    if "," in (last_first or ""):
        last, first = last_first.split(",", 1)
        return f"{first.strip()} {last.strip()}".strip()
    return (last_first or "").strip()


players = {}  # name.lower() -> {name, jerseyNumber, position}
games = {}  # (date, opponent.lower()) -> game dict


def add_player(name, jersey, position, prefer=False):
    if not name:
        return
    existing = players.get(name.lower())
    if existing is None:
        players[name.lower()] = {"name": name, "jerseyNumber": jersey, "position": position}
    elif prefer:
        existing["name"] = name
        if jersey:
            existing["jerseyNumber"] = jersey
        if position:
            existing["position"] = position


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
        "season": date[:4],
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

    for p in ku.get("players") or []:
        hitting = p.get("hitting")
        pitching = p.get("pitching")
        if not hitting and not pitching:
            continue
        name = flip_name(p.get("name"))
        add_player(name, str(p.get("uniform") or ""), "")
        line = {"player": name, "gs": 1 if to_int(p.get("gameStarted")) else 0}
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
        game["lines"].append(line)
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
    game.pop("_sidearmId", None)
    game.pop("_dh", None)
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
        "season": date[:4],
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

# --- Upcoming games (no results yet) -----------------------------------------
today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
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
        "season": date[:4],
    }

seed = {
    "formatVersion": 1,
    "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "team": "Kansas Jayhawks Softball",
    "players": sorted(players.values(), key=lambda p: p["name"]),
    "games": [games[k] for k in sorted(games)],
}

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
