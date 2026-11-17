"""The season as flat tables, for "Ask about the team".

The seed is shaped for the app: nested, merged, full of display details. A
question put to Claude is better answered from plain tables it can load into
pandas and compute on, so the numbers come from code rather than from reading
a long document. This writes docs/ask-data.json: one list of flat records per
table, a data dictionary saying what every column means, and the few facts a
reader needs to interpret them (what a fall game counts for, why the last
inning is often missing, how ERA is scaled in a seven-inning sport).

Everything here is derived from the seed that was just validated against the
box scores; nothing is recomputed or estimated.

Adapted from the basketball pack, and the differences are not cosmetic. Three
of its load-bearing claims are false in softball and had to be re-established
against this data rather than carried over:

  * Basketball warns that team totals exceed the sum of the player lines,
    because team rebounds and team turnovers belong to no individual. Softball
    has no such bucket. Across all 57 played games of 2026 the player lines sum
    to the game totals exactly — hits, errors, runs scored, and runs allowed by
    the pitchers — so summing the batting table is the right way to answer a
    team question, and saying otherwise would send Claude looking for a
    discrepancy that is not there.
  * Quarters become innings, and an inning is not a quarter: the home team does
    not bat in the last one when it is already ahead, and a run-rule game
    simply stops. A missing half-inning has to be distinguishable from a
    scoreless one or every late-inning question is answered wrongly.
  * Basketball cannot tie and does not play two games in a day. Softball does
    both.
"""

import json
import re
from datetime import datetime, timedelta

# One row per batter per game. The first fifteen are the box score as it is
# published; the rest are the detail the scrape keeps and the printed box
# usually drops.
BATTING_COLS = [
    "ab", "r", "h", "2b", "3b", "hr", "rbi", "bb", "so", "hbp", "sb", "cs", "sf", "sh",
    "kl", "roe", "fc", "go", "ao", "gidp", "ibb", "pko",
]
PITCHING_COLS = [
    "outs", "ha", "ra", "er", "bba", "ks", "hra", "w", "l", "sv",
    "np", "bf", "wp", "hbA", "bk", "ir", "irs", "cg", "sho", "gsp", "ksl", "oab",
]
FIELDING_COLS = ["po", "a", "e", "pb", "sba", "csb", "dp"]

DEFINITIONS = {
    # Batting
    "ab": "at-bats — plate appearances that ended in a hit or an out; walks, "
          "hit-by-pitches and sacrifices are not at-bats",
    "r": "runs scored by this batter",
    "h": "hits", "2b": "doubles", "3b": "triples", "hr": "home runs",
    "rbi": "runs batted in", "bb": "walks", "so": "strikeouts",
    "hbp": "hit by pitch", "sb": "stolen bases", "cs": "caught stealing",
    "sf": "sacrifice flies", "sh": "sacrifice hits (bunts)",
    "kl": "strikeouts looking — called third strikes, a subset of so",
    "roe": "times reached on an error", "fc": "times reached on a fielder's choice",
    "go": "ground outs", "ao": "fly outs", "gidp": "grounded into a double play",
    "ibb": "intentional walks, a subset of bb", "pko": "picked off",
    "spot": "place in the batting order, 1-9; 10 is the FLEX, who fields and does "
            "not bat, and in this data is always the pitcher",
    "sub": "true if she entered the game in that lineup spot rather than starting it",
    "pos": "the position played in that game",
    "gs": "started the game",
    # Pitching
    "outs": "outs recorded — innings pitched times three, so partial innings add up; "
            "7.2 innings is 23 outs, not 7.67",
    "ha": "hits allowed", "ra": "runs allowed", "er": "earned runs",
    "bba": "walks allowed", "ks": "strikeouts by the pitcher",
    "hra": "home runs allowed", "w": "credited with the win",
    "l": "charged with the loss", "sv": "credited with a save",
    "np": "number of pitches thrown", "bf": "batters faced",
    "wp": "wild pitches", "hbA": "batters hit by a pitch she threw", "bk": "balks",
    "ir": "inherited runners", "irs": "inherited runners who scored",
    "cg": "complete game", "sho": "shutout", "gsp": "started the game as pitcher",
    "ksl": "strikeouts looking by the pitcher, a subset of ks",
    "oab": "at-bats against her, the denominator for opponent batting average",
    # Fielding
    "po": "putouts — outs this fielder recorded personally",
    "a": "assists", "e": "errors", "pb": "passed balls",
    "sba": "stolen bases allowed while she was catching",
    "csb": "runners she threw out stealing", "dp": "double plays taken part in",
    # Rates a question is likely to ask for
    "avg": "batting average, h / ab",
    "obp": "on-base percentage, (h + bb + hbp) / (ab + bb + hbp + sf) — sacrifice "
           "bunts are left out of the denominator",
    "slg": "slugging, total bases / ab, where total bases is h + 2b + 2*3b + 3*hr",
    "ops": "obp + slg",
    "era": "earned run average, er * 21 / outs — scaled to seven innings, because "
           "a regulation softball game is seven, not nine",
    "whip": "walks and hits per inning pitched, (bba + ha) * 3 / outs",
    # Game context
    "site": "H home, A away, N neutral — softball plays many tournaments, so "
            "neutral is common and is counted separately from both",
    "conference": "true for a game against a Big 12 member. Note this includes the "
                  "conference tournament, which the official Big 12 standings record "
                  "does not count",
    "exhibition": "a fall game. Fall softball is preseason exhibition play: it counts "
                  "toward no official record and is kept as its own season, labelled "
                  "'Fall' and the year — 'Fall 2026' for the autumn before the 2027 "
                  "spring season",
    "result": "W, L, or T — softball can tie, when weather ends a game level",
    "opp_rank": "the opponent's national rank at the time of the game (null = unranked)",
    "opp_record": "the opponent's won-lost record including this game, as the box "
                  "score reported it on the day",
    "inning_scores": "runs by inning from KU's side, e.g. '0-1, 2-0, 1-0'; the innings "
                     "table has the same thing one row per inning",
    "batted": "whether that side came to bat in that half-inning. The home team does "
              "not bat in the last inning of a game it is already winning, and a "
              "run-rule game ends early — so a missing half-inning is not a scoreless "
              "one and must not be averaged as a zero",
    "doubleheader": "two games against the same opponent on the same date. The second "
                    "is marked '(G2)' in the opponent name; strip that suffix to group "
                    "a series",
    "attendance": "announced crowd, 0 when not reported",
    "scheduled_innings / innings_played": "how long the game was meant to be and how "
        "long it was. A game short of its scheduled length that was won by eight or "
        "more from the fifth inning on ended by the run rule, which is ordinary in "
        "softball — 19 of the 57 games in 2026. One short of its length with a close "
        "score was called for weather or darkness instead. Do not treat a run-rule "
        "game as an anomaly or a blowout worth remarking on by itself",
    "event": "the tournament or series the game belonged to, as the schedule names it",
    "duration": "how long the game took, as h:mm",
    "batsThrows": "which hand the player bats with and which she throws with, as L/R",
    "opponent_batting / opponent_pitching": "the other side's box score lines. These "
        "cover only the games that team played against Kansas — two or three in a "
        "weekend series — so they are a record of those meetings and not of that "
        "opponent's season. Never present them as season figures",
    # Other teams
    "team_key / opponent_key / home_key / away_key": "one normalized spelling per school "
        "('Oklahoma Sooners' and 'Oklahoma', 'Iowa State' and 'Iowa St.' each reduce to "
        "one key). Each table spells schools as its own source does, so join on these "
        "keys, never on the display names",
    "big12_results": "every final game this season with a Big 12 side, from the NCAA "
        "scoreboard. Only games with a Big 12 side, so it is not complete coverage of "
        "any other team's season. source is 'ncaa', or 'ku_box' for a Kansas game the "
        "scoreboard never finalized and that was filled in from KU's box score",
    "score_missing": "a game the NCAA never filled in — both scores 0 and no winner. Not "
        "a 0-0 tie; left out of team_scoring",
    "big12_tournament": "a conference-tournament game. The scoreboard's conference flag "
        "counts these and the official Big 12 standings do not, so this reconciles them",
    "team_scoring": "per Big 12 team and season, counted from big12_results: games, "
        "W-L-T, runs for and against, run differential, record against the final poll. "
        "The runs are why it exists — the RPI has no scoring. Its W-L is approximate, "
        "the scoreboard being short a few games; take records from team_records",
    "vs_final_top25_w / vs_final_top25_l": "against teams in the FINAL poll, not teams "
        "ranked on the day — the scoreboard carries no rank for any game. For KU's own "
        "at-the-time figure use games.opp_rank",
    "team_records": "every Division I team's NCAA RPI row: rank, previous rank, record, "
        "conference, and home, road, neutral and non-Division I records, with the date "
        "it runs through. The official record for any team",
    "d1_results": "every final Division I game this season: date, season, the two teams "
        "with their keys, and the runs. Thin on purpose, being much the largest table. "
        "It is how to find opponents two teams have in common, or any team's season, "
        "when neither is in the Big 12 — big12_results holds only games with a Big 12 "
        "side, but carries richer flags for those",
}


# The instructions Claude is given, shared by the dashboard (docs/ask.js) and
# the app (AskEngine.kt) so both ask the same way. The tables are fetched by
# Claude's own code through the get_table tool; this only says how.
SYSTEM_RULES = """You are the analyst behind the Kansas Jayhawks softball app and dashboard. You answer questions from coaches and fans about the team and the teams it plays, using the season data you are given - and the web, when the reader turns web search on for a question.

The complete data is available to your Python code through the get_table tool: tables games, innings (one row per half-inning), batting (one row per KU batter per game), pitching, fielding, scoring_plays (how each run scored), opponent_batting and opponent_pitching (the other side of every box score), upcoming, standings, poll, rankings, roster, and definitions. Call it from inside code execution, for example: import json, pandas as pd; bat = pd.DataFrame(json.loads(await get_table({'table': 'batting'}))). Join tables on (season, date, opponent). There are also four tables about other teams: big12_results, team_scoring, team_records and d1_results. A summary of the smaller tables is below for orientation.

How to answer:
- Compute every number with code from the tables. Do not estimate, recall, or do arithmetic in your head, even for a simple total.
- The player lines add up. Unlike basketball, softball has no team-level bucket that belongs to no individual: across every played game the Kansas batting lines sum exactly to the game's runs and hits, the fielding lines to its errors, and the pitching lines to the opponent's runs. So a team question can be answered by summing the player tables, and you should not go looking for a discrepancy. One exception, on the opponent's side only: softball scoring allows an error charged to the team rather than to a fielder, and that happened once in 2026 (Houston on March 13, three errors reported and two charged to players), so opponent_batting and the game's own opp_errors can differ by one. Prefer the game row for an opponent's error total.
- opponent_batting and opponent_pitching are only the games that team played against Kansas. Two or three meetings is not a season, so say what the sample is and never call a figure from them that opponent's season average.
- Innings pitched are stored as outs. Divide by three for innings, and never read "7.2 innings" as 7.67 — it is 7 innings and 2 outs, which is 23 outs. ERA is scaled to seven innings (er * 21 / outs), because a regulation game is seven.
- Fall games are exhibitions. They count toward no record, and are a separate season labelled "Fall" and the year — "Fall 2026" is the autumn before the 2027 spring season, so it is not part of the 2026 season at all. Leave them out unless the question asks about the fall, and say so when it matters.
- A missing half-inning is not a scoreless one. The home team does not bat in the last inning of a game it leads, and a run-rule game ends early; the innings table marks this with batted. Never average an unbatted half-inning as a zero.
- Doubleheaders share a date and an opponent; the second game is marked "(G2)". Strip that suffix before grouping a series, and keep the two games distinct when counting.
- Softball can tie, so a record is not simply wins and games-minus-wins. Check for result "T".
- A game shorter than its scheduled_innings usually ended by the run rule, not early for weather. That is routine — a third of the 2026 season — so do not present it as remarkable without being asked.
- Use softball conventions: averages as .328, ERA to two decimals, records as 36-21, innings as 7.2.
- The Big 12 record in the standings table excludes the conference tournament; the conference flag on games does not. Say which you used when it changes the answer.
- Name small samples plainly (for example "only 3 games", or "one weekend series").
- If the data cannot answer the question — injuries, practice, recruiting, pitch-by-pitch detail, anything not in the box scores — say so in a sentence instead of guessing; with web search on, look for it on the web instead.

Other teams: big12_results (every final game this season with a Big 12 side), team_scoring (each Big 12 team's runs for and against, run differential, and record against teams in the final poll), team_records (every Division I team's RPI row) and d1_results (every final Division I game this season) let you analyse opponents too — results against common opponents, records against ranked teams, scoring, RPI. Match teams across tables on the team_key, opponent_key, home_key and away_key columns, never on the display names, because each table spells schools as its own source does. Take any team's record from team_records, which is official: team_scoring counts the scoreboard, which is missing a few games, so its W-L can be a game or two out. big12_results holds only games with a Big 12 side, so for a team outside the Big 12 — its season, or opponents it has in common with another non-conference team — use d1_results, which has every Division I game. It is large, so filter it in code rather than reading it whole.

Web search: the reader can turn on web search for a question, and each question says whether it is on. When it is off, answer from the data only, and say plainly when the data cannot answer. When it is on, you may use the web_search tool for what the tables cannot tell you — previews, recaps, injuries and line-up news, coaches' comments, transfers, rankings commentary — preferring official athletics sites, the Big 12, the NCAA, the NFCA, D1Softball, Softball America and established news outlets. Even then, take every statistic from the tables when they have it, and keep web material for context. Make clear which statements come from the web, and give each article's date when it matters, since news goes out of date. Search only as much as the question needs.
"""


def _cell(v):
    if v is None:
        return ""
    if isinstance(v, bool):
        return "true" if v else "false"
    s = str(v)
    return '"' + s.replace('"', '""') + '"' if any(ch in s for ch in '",\n') else s


def _csv(rows, cols):
    return "\n".join([",".join(cols)] + [",".join(_cell(r.get(c)) for c in cols) for r in rows])


def norm_team(name):
    """Canonical key for matching a school across sources. Mirrors the same
    function in update-seed.py and teamKey() in the app; all three have to
    agree or they would disagree about who KU played."""
    n = re.sub(r"\s*\(G\d\)\s*$", "", name or "")
    n = re.sub(r"\s*\(\d+\)\s*$", "", n).lower().replace(".", "")
    n = re.sub(r"\bstate\b", "st", n)
    return re.sub(r"\s+", " ", n).strip()


# The tables spell schools as their sources do, and three disagree on the name
# itself: the seed says "Oklahoma Sooners", "South Florida" and "Florida
# Atlantic" where the RPI and the NCAA scoreboard say "Oklahoma", "South Fla."
# and "Fla. Atlantic". Joining on the raw names would silently drop those games.
_ALIASES = {
    "oklahoma sooners": "oklahoma",
    "south florida": "south fla",
    "florida atlantic": "fla atlantic",
}


def team_key(name):
    """One spelling per school, for matching a team across tables.

    norm_team already strips a doubleheader's "(G2)" and the poll's
    first-place-vote "(25)", lowercases, and shortens "State" to "st"; this
    adds the handful of schools whose sources disagree on the name itself.
    norm_team itself is left alone: update-seed.py and the app's teamKey()
    mirror it, and all three have to keep agreeing.
    """
    key = norm_team(name)
    return _ALIASES.get(key, key)


def base_opponent(name):
    """'Baylor (G2)' -> 'Baylor'."""
    return re.sub(r"\s*\(G\d\)\s*$", "", name or "").strip()


def _int(v):
    """'12' -> 12; '', None and 'NR' -> None."""
    try:
        return int(str(v).strip())
    except (TypeError, ValueError):
        return None


def _ncaa_fences(games):
    """Ten days before each season's first NCAA tournament game.

    The same window update-seed.py's conf_fence draws, so that a conference
    game this side of it counts toward the official Big 12 standings and one
    beyond it is the conference tournament, which the standings exclude.
    """
    start = {}
    for g in games.values():
        if not g.get("bracket"):
            continue
        season = g.get("season") or (g.get("date") or "")[:4]
        date = g.get("date") or ""
        if date and (season not in start or date < start[season]):
            start[season] = date
    return {s: (datetime.strptime(d, "%Y-%m-%d") - timedelta(days=10)).strftime("%Y-%m-%d")
            for s, d in start.items()}


def _big12_results(index, fences):
    """Every scoreboard game with a Big 12 side, cleaned.

    Four corrections, each of which changes a count if it is skipped:

      * A "TBA" side is a placeholder for a game that was never played.
      * Both scores 0 with neither side marked the winner is a game the NCAA
        never filled in, not a 0-0 tie. The row is kept, flagged
        score_missing, and left out of team_scoring.
      * The scoreboard sometimes lists one game under two ids. Keyed by id
        alone nothing looks duplicated, but counting both gave Arizona 37-20
        where the RPI says 37-18. Where ids share a date, both teams and both
        scores and exactly one is bracket-flagged, that is the real one and
        the rest are copies. Where none is flagged it is a real doubleheader
        -- Southern Utah at Arizona, 8-0 twice on 2026-02-05 -- and the RPI
        counts both, so both are kept.
      * The scoreboard's conferenceGame flag includes the conference
        tournament; big12_tournament separates it so Claude can reconcile
        with the standings, which exclude it.
    """
    games = (index or {}).get("big12Games") or {}
    kept = {}
    for gid, g in games.items():
        home, away = g.get("home") or {}, g.get("away") or {}
        if not home.get("name") or not away.get("name"):
            continue
        if home["name"] == "TBA" or away["name"] == "TBA":
            continue
        kept[gid] = g

    shapes = {}
    for gid, g in kept.items():
        shape = (g.get("date"), g["home"]["name"], g["away"]["name"],
                 g["home"].get("score"), g["away"].get("score"))
        shapes.setdefault(shape, []).append(gid)
    for ids in shapes.values():
        flagged = [i for i in ids if kept[i].get("bracket")]
        if len(ids) > 1 and len(flagged) == 1:
            for i in ids:
                if i not in flagged:
                    kept.pop(i, None)

    rows = []
    for gid, g in sorted(kept.items(), key=lambda kv: ((kv[1].get("date") or ""), kv[0])):
        home, away = g["home"], g["away"]
        season = g.get("season") or (g.get("date") or "")[:4]
        hs, as_ = home.get("score"), away.get("score")
        missing = (not home.get("winner") and not away.get("winner")
                   and not (hs or 0) and not (as_ or 0))
        fence = fences.get(season)
        conf = bool(g.get("conferenceGame"))
        rows.append({
            "game_id": gid, "date": g.get("date"), "season": season,
            "home": home["name"], "home_key": team_key(home["name"]), "home_runs": hs,
            "away": away["name"], "away_key": team_key(away["name"]), "away_runs": as_,
            "conference_game": conf,
            "big12_tournament": bool(conf and not g.get("bracket") and fence
                                     and (g.get("date") or "") >= fence),
            "score_missing": missing,
            "source": "ncaa",
        })
    return rows


def _add_ku_games(rows, seed, conference, fences):
    """KU's own played games that the scoreboard never finalized.

    The 6-5 Big 12 tournament win over UCF on 2026-05-07 is in KU's box
    scores and absent from the feed, so counting from the feed alone leaves
    Kansas a win short and disagreeing with the RPI. update-seed.py fills the
    standings from the seed the same way. A neutral-site game has no real home
    side, so Kansas is written as the away team there; source says the row
    came from the box scores rather than the scoreboard.
    """
    seen = {(r["date"], frozenset((r["home_key"], r["away_key"]))) for r in rows}
    ku = team_key("Kansas")
    added = []
    for g in seed.get("games", []):
        if g.get("teamScore") is None or g["season"].startswith("Fall"):
            continue
        key = (g["date"], frozenset((ku, team_key(g["opponent"]))))
        if key in seen:
            continue
        seen.add(key)
        opponent, us, them = base_opponent(g["opponent"]), g["teamScore"], g["opponentScore"]
        at_home = (g.get("site") or "").upper() == "H"
        conf = norm_team(g["opponent"]) in conference
        fence = fences.get(g["season"])
        added.append({
            "game_id": None, "date": g["date"], "season": g["season"],
            "home": "Kansas" if at_home else opponent,
            "home_key": ku if at_home else team_key(g["opponent"]),
            "home_runs": us if at_home else them,
            "away": opponent if at_home else "Kansas",
            "away_key": team_key(g["opponent"]) if at_home else ku,
            "away_runs": them if at_home else us,
            "conference_game": conf,
            "big12_tournament": bool(conf and fence and g["date"] >= fence),
            "score_missing": False,
            "source": "ku_box",
        })
    return sorted(rows + added, key=lambda r: (r["date"] or "", r["home"], r["away"]))


def _team_scoring(results, big12, top25):
    """One row per Big 12 team per season, from the cleaned results.

    Softball can tie, so a record is wins, losses and ties rather than wins
    and games-minus-wins. The Top 25 columns are against teams in the *final*
    poll, not teams ranked on the day: the scoreboard carries no rank at all
    -- every one is null -- so there is nothing else to go on. KU's own
    at-the-time figure stays games.opp_rank.
    """
    acc = {}
    for r in results:
        if r["score_missing"]:
            continue
        for side, other in (("home", "away"), ("away", "home")):
            key = r[f"{side}_key"]
            if key not in big12:
                continue
            mine, theirs = r[f"{side}_runs"] or 0, r[f"{other}_runs"] or 0
            row = acc.setdefault((r["season"], key), {
                "season": r["season"], "team": r[side], "team_key": key,
                "games": 0, "w": 0, "l": 0, "t": 0,
                "runs_for": 0, "runs_against": 0, "run_diff": 0,
                "vs_final_top25_w": 0, "vs_final_top25_l": 0,
            })
            row["games"] += 1
            row["runs_for"] += mine
            row["runs_against"] += theirs
            row["run_diff"] = row["runs_for"] - row["runs_against"]
            won = mine > theirs
            row["w" if won else "l" if mine < theirs else "t"] += 1
            if r[f"{other}_key"] in top25 and mine != theirs:
                row["vs_final_top25_w" if won else "vs_final_top25_l"] += 1
    return [acc[k] for k in sorted(acc)]


def _d1_results(d1):
    """Every final Division I game this season, from scraped/d1-results.json.

    The same scoreboard big12_results is built from, kept whole rather than
    filtered to the Big 12, so a question about any team's season or about
    opponents two teams have in common can be answered. Deliberately thin --
    the two sides, their runs, and whether the score is real -- because this
    is the largest table in the pack by a wide margin and it is downloaded by
    the app. big12_results keeps the richer flags for the teams KU plays.

    Cleaned the same way: a TBA placeholder is dropped, a game with no winner
    and no runs is one the NCAA never filled in rather than a 0-0 tie, and one
    game listed under two ids with exactly one of them bracket-flagged is a
    copy of the other.
    """
    games = (d1 or {}).get("games") or {}
    kept = {gid: g for gid, g in games.items()
            if g.get("home") and g.get("away")
            and g["home"] != "TBA" and g["away"] != "TBA"}

    shapes = {}
    for gid, g in kept.items():
        shapes.setdefault(
            (g.get("date"), g["home"], g["away"], g.get("homeScore"), g.get("awayScore")), []
        ).append(gid)
    for ids in shapes.values():
        flagged = [i for i in ids if kept[i].get("bracket")]
        if len(ids) > 1 and len(flagged) == 1:
            for i in ids:
                if i not in flagged:
                    kept.pop(i, None)

    rows = []
    for gid, g in sorted(kept.items(), key=lambda kv: ((kv[1].get("date") or ""), kv[0])):
        hs, as_ = g.get("homeScore"), g.get("awayScore")
        rows.append({
            "date": g.get("date"), "season": g.get("season") or (g.get("date") or "")[:4],
            "home": g["home"], "home_key": team_key(g["home"]), "home_runs": hs,
            "away": g["away"], "away_key": team_key(g["away"]), "away_runs": as_,
            "score_missing": (not g.get("homeWon") and not g.get("awayWon")
                              and not (hs or 0) and not (as_ or 0)),
        })
    return rows


def _team_records(rpi):
    """Every Division I team's RPI row, as the NCAA published it."""
    updated = (rpi or {}).get("updated") or None
    year = re.search(r"(\d{4})", updated or "")
    season = year.group(1) if year else None
    return [{
        "season": season, "as_of": updated,
        "rank": _int(r.get("Rank")), "previous_rank": _int(r.get("Prev")),
        "team": r.get("School"), "team_key": team_key(r.get("School")),
        "record": r.get("Record"), "conference": r.get("Conf"),
        "home": r.get("Home"), "road": r.get("Road"), "neutral": r.get("Neutral"),
        "non_div1": r.get("Non-Div I"),
    } for r in (rpi or {}).get("data") or []]


def _season_line(pack):
    """Which season is which, counted from the data rather than assumed."""
    seasons = sorted({g["season"] for g in pack["games"]} |
                     {u["season"] for u in pack["upcoming"]})
    played = {s: sum(1 for g in pack["games"] if g["season"] == s) for s in seasons}
    scheduled = {s: sum(1 for u in pack["upcoming"] if u["season"] == s) for s in seasons}
    # The spring season is the real one. Sorting puts "Fall 2026" after "2027",
    # so the current season is chosen by what has actually been played rather
    # than by where a label lands in an alphabet.
    spring = [s for s in seasons if not s.startswith("Fall")]
    current = max((s for s in spring if played.get(s)), default=None) or \
        (max(spring) if spring else None)
    parts = []
    for s in seasons:
        bit = f"{s}: {played[s]} games played"
        if scheduled[s]:
            bit += f", {scheduled[s]} still scheduled"
        if s.startswith("Fall"):
            bit += " (exhibition, counts toward no record)"
        parts.append(bit)
    line = "Seasons in the data — " + "; ".join(parts) + "."
    if current:
        line += (f" {current} is the current season, and \"this season\" means {current} "
                 f"unless the reader says otherwise")
        if not played.get(current):
            line += ("; it has not been played yet, so say that rather than answering "
                     "from an earlier season")
        line += "."
    return line


def system_prompt(p):
    """The rules plus a compact summary of the smaller tables, in fixed column
    order so the text — and the prompt cache — changes only with the data."""
    current = None
    for g in p["games"]:
        if not g["season"].startswith("Fall"):
            current = g["season"]
    parts = [
        f"Data generated {p['generated_at']}.",
        _season_line(p),
        "Definitions:\n" + "\n".join(f"- {k}: {v}" for k, v in p["definitions"].items()),
        "Played games:\n" + _csv(p["games"], [
            "season", "date", "opponent", "site", "conference", "exhibition", "result",
            "ku_runs", "opp_runs", "opp_rank", "opp_record", "inning_scores"]),
        "Scheduled games:\n" + _csv(p["upcoming"], [
            "season", "date", "opponent", "site", "conference", "exhibition",
            "first_pitch_local", "venue"]),
        (f"Big 12 standings {current}:\n" + _csv(
            [s for s in p["standings"] if s["season"] == current],
            ["team", "confW", "confL", "overallW", "overallL", "nationalRank", "rpiRank"]))
        if current else "",
        (f"{p['poll']['name']} ({p['poll']['updated']}, {p['poll']['season']}):\n" +
         _csv(p["poll"]["rows"], ["rank", "team", "record", "points", "previous"]))
        if p.get("poll") else "",
        "Roster:\n" + _csv(p["roster"], ["name", "jerseyNumber", "position", "active"]),
    ]
    return SYSTEM_RULES + "\n" + "\n\n".join(x for x in parts if x)


def _innings(game_base, inning_scores, regulation=7):
    """One row per inning. An 'x' half means that side never came to bat, which
    is a different fact from being held scoreless and is carried as batted =
    false with no runs rather than as a zero."""
    rows = []
    for i, part in enumerate((inning_scores or "").split(",")):
        halves = part.strip().split("-")
        if len(halves) != 2:
            continue
        n = i + 1

        def half(text):
            try:
                return int(text.strip()), True
            except ValueError:
                return None, False

        ku, ku_batted = half(halves[0])
        opp, opp_batted = half(halves[1])
        if not ku_batted and not opp_batted:
            continue
        rows.append({**game_base, "inning": n, "extra": n > regulation,
                     "ku_runs": ku, "ku_batted": ku_batted,
                     "opp_runs": opp, "opp_batted": opp_batted})
    return rows


def _result(us, them):
    return "W" if us > them else "L" if us < them else "T"


def build_pack(seed, index=None, rpi=None, poll=None, d1=None):
    """The pack. [index] is scraped/ku-index.json, [rpi] rankings-rpi.json and
    [poll] rankings-poll.json and [d1] d1-results.json; without them the
    other-team tables are still emitted, empty, so the table list stays the
    same either way."""
    players = {p["name"].lower(): p for p in seed.get("players", [])}
    conference = {norm_team(s["team"]) for s in seed.get("standings", [])} - {norm_team("Kansas")}

    games, innings, batting, pitching, fielding, scoring, upcoming = [], [], [], [], [], [], []
    opponent_batting, opponent_pitching = [], []
    for g in sorted(seed.get("games", []), key=lambda x: (x["date"], x["opponent"])):
        base = {"season": g["season"], "date": g["date"], "opponent": g["opponent"],
                "opponent_key": team_key(g["opponent"])}
        is_fall = g["season"].startswith("Fall")
        # A fall game is an exhibition even against a Big 12 school, and the
        # fall opponents carry no "(Exh.)" marker to give it away — only the
        # season does. Counting one as a conference game would put a game in
        # the Big 12 record that the standings have never heard of.
        in_conf = not is_fall and norm_team(g["opponent"]) in conference
        if g.get("teamScore") is None:
            upcoming.append({**base, "team": base_opponent(g["opponent"]),
                             "site": g.get("site") or None, "conference": in_conf,
                             "exhibition": is_fall,
                             "first_pitch_local": g.get("startTime") or None,
                             "venue": g.get("venue") or None})
            continue
        us, them = g["teamScore"], g["opponentScore"]
        games.append({**base, "team": base_opponent(g["opponent"]),
                      "site": g.get("site") or None, "conference": in_conf,
                      "exhibition": is_fall, "result": _result(us, them),
                      "ku_runs": us, "opp_runs": them, "margin": us - them,
                      "ku_hits": g.get("teamHits"), "opp_hits": g.get("opponentHits"),
                      "ku_errors": g.get("teamErrors"), "opp_errors": g.get("opponentErrors"),
                      "opp_rank": g.get("opponentRank") or None,
                      "opp_record": g.get("opponentRecord") or None,
                      "inning_scores": g.get("inningScores"),
                      "venue": g.get("venue") or None,
                      "attendance": g.get("attendance") or None,
                      "stadium": g.get("stadium") or None,
                      "event": g.get("event") or None,
                      "first_pitch": g.get("firstPitch") or None,
                      "duration": g.get("duration") or None,
                      "weather": g.get("weather") or None,
                      "umpires": g.get("umpires") or None,
                      "scheduled_innings": g.get("scheduledInnings") or None,
                      "innings_played": len([x for x in (g.get("inningScores") or "").split(",")
                                             if x.strip()]) or None,
                      "box_score_url": g.get("boxScoreUrl") or None,
                      "pdf_url": g.get("pdfUrl") or None})
        innings += _innings(base, g.get("inningScores"))
        for play in g.get("scoring") or []:
            scoring.append({**base, "inning": play.get("inn"),
                            "scored_by": "KU" if play.get("ku") else "OPP",
                            "narrative": play.get("text"),
                            "ku_runs_after": play.get("us"),
                            "opp_runs_after": play.get("them")})
        for line in g.get("opponentLines") or []:
            ident = {**base, "player": line["player"],
                     "jersey": line.get("number") or None}
            if any(line.get(c) for c in ("ab", "bb", "hbp", "r", "sb")):
                opponent_batting.append({
                    **ident, "spot": line.get("spot") or None,
                    "pos": line.get("pos") or None,
                    "started": bool(line.get("gs")), "sub": bool(line.get("sub")),
                    **{c: line.get(c, 0) for c in
                       ("ab", "r", "h", "2b", "3b", "hr", "rbi", "bb", "so", "hbp", "sb")},
                    **{c: line.get(c, 0) for c in ("po", "a", "e")},
                })
            if line.get("p"):
                opponent_pitching.append({
                    **ident,
                    **{c: line.get(c, 0) for c in
                       ("outs", "ha", "ra", "er", "bba", "ks", "hra", "np", "bf",
                        "w", "l", "sv")},
                })
        for line in g.get("lines", []):
            who = players.get(line["player"].lower(), {})
            ident = {**base, "player": line["player"],
                     "jersey": who.get("jerseyNumber") or None}
            # A batter row is only written for someone who actually took a turn
            # at the plate; the FLEX pitcher who never bats would otherwise
            # appear as an 0-for-0 and drag every rate down.
            took_a_turn = any(line.get(c) for c in
                              ("ab", "bb", "hbp", "sf", "sh", "r", "sb"))
            if took_a_turn:
                batting.append({**ident, "spot": line.get("spot") or None,
                                "pos": line.get("pos") or None,
                                "started": bool(line.get("gs")),
                                "sub": bool(line.get("sub")),
                                **{c: line.get(c, 0) for c in BATTING_COLS}})
            if line.get("p"):
                pitching.append({**ident, "started": bool(line.get("gsp")),
                                 **{c: line.get(c, 0) for c in PITCHING_COLS}})
            if any(line.get(c) for c in FIELDING_COLS):
                fielding.append({**ident, "pos": line.get("pos") or None,
                                 **{c: line.get(c, 0) for c in FIELDING_COLS}})

    polls = seed.get("polls") or []
    current_poll = polls[-1] if polls else None

    fences = _ncaa_fences((index or {}).get("big12Games") or {})
    big12_results = _big12_results(index, fences)
    if big12_results:
        big12_results = _add_ku_games(big12_results, seed, conference, fences)
    big12_keys = {team_key(s["team"]) for s in seed.get("standings", [])}
    final_top25 = {team_key(r.get("School")) for r in (poll or {}).get("data") or []}
    team_scoring = _team_scoring(big12_results, big12_keys, final_top25)
    team_records = _team_records(rpi)
    d1_results = _d1_results(d1)

    pack = {
        "about": "Kansas Jayhawks softball, from kuathletics.com box scores and the NCAA "
                 "feeds. One record per row; join tables on (season, date, opponent).",
        "generated_at": seed.get("generatedAt"),
        "definitions": DEFINITIONS,
        "games": games,
        "innings": innings,
        "batting": batting,
        "pitching": pitching,
        "fielding": fielding,
        "scoring_plays": scoring,
        "opponent_batting": opponent_batting,
        "opponent_pitching": opponent_pitching,
        "upcoming": upcoming,
        "standings": [{**{k: st.get(k) for k in ("season", "team", "confW", "confL", "overallW",
                                                 "overallL", "nationalRank", "rpiRank")},
                       "team_key": team_key(st.get("team"))}
                      for st in seed.get("standings", [])],
        "poll": None if not current_poll else {
            "season": current_poll["season"], "name": current_poll.get("name") or "Poll",
            "updated": current_poll.get("updated"),
            "rows": [{**{k: r.get(k) for k in ("rank", "rankLabel", "team", "record", "points",
                                               "previous", "firstPlaceVotes")},
                      "team_key": team_key(r.get("team"))}
                     for r in current_poll.get("rows", [])]},
        # Every weekly ranking snapshot kept, so "when did KU peak in the RPI"
        # is answerable rather than only "where are they now".
        "rankings": [{"date": h.get("date"), "source": h.get("source"),
                      "season": h.get("season"), "team": t.get("team"),
                      "team_key": team_key(t.get("team")),
                      "rank": t.get("rank"), "record": t.get("record")}
                     for h in seed.get("rankingHistory", []) for t in h.get("teams", [])],
        # Other teams. Always present, empty when the scraped files were not
        # passed in, so the table list agrees either way.
        "big12_results": big12_results,
        "team_scoring": team_scoring,
        "team_records": team_records,
        "d1_results": d1_results,
        "roster": [{k: p.get(k) for k in
                    ("name", "jerseyNumber", "position", "academicYear", "height",
                     "batsThrows", "hometown", "lastSchool", "active")}
                   for p in seed.get("players", [])],
    }
    pack["system_prompt"] = system_prompt(pack)
    return pack


def _without_timestamp(pack):
    """The pack with every trace of when it was built removed.

    Dropping the `generated_at` key is not enough: the same timestamp is
    written into the system prompt, so a pack whose data had not changed at all
    still compared as different and the file was rewritten on every scrape.
    That is a commit, an APK rebuild and a Pages deploy, nightly, for nothing.
    """
    out = {k: v for k, v in (pack or {}).items() if k != "generated_at"}
    if isinstance(out.get("system_prompt"), str):
        out["system_prompt"] = re.sub(
            r"^Data generated .*$", "Data generated <when>.",
            out["system_prompt"], flags=re.M)
    return out


def write_pack(seed, path, index=None, rpi=None, poll=None, d1=None):
    """Writes the pack; returns False when only the timestamp would change."""
    pack = build_pack(seed, index, rpi, poll, d1)
    try:
        with open(path) as f:
            old = json.load(f)
    except (OSError, ValueError):
        old = None
    if old is not None and _without_timestamp(old) == _without_timestamp(pack):
        return False
    with open(path, "w") as f:
        json.dump(pack, f, separators=(",", ":"))
        f.write("\n")
    return True


def _load(path):
    """A scraped file, or None when the sweep has not written it yet."""
    try:
        with open(path) as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


if __name__ == "__main__":
    # Rebuilding straight from the committed seed, so the published page can
    # never serve an ask-data.json older than the data the rest of it shows.
    with open("app/src/main/assets/seed.json") as f:
        SEED = json.load(f)
    # The other teams' data is not in the seed — it is far larger than KU's own
    # season and would bloat the APK — so it is read from the committed scrape.
    INDEX = _load("scraped/ku-index.json")
    RPI = _load("scraped/rankings-rpi.json")
    POLL = _load("scraped/rankings-poll.json")
    D1 = _load("scraped/d1-results.json")
    P = build_pack(SEED, INDEX, RPI, POLL, D1)
    CHANGED = write_pack(SEED, "docs/ask-data.json", INDEX, RPI, POLL, D1)
    print(f"ask data: {len(P['games'])} games, {len(P['batting'])} batting lines, "
          f"{len(P['pitching'])} pitching, {len(P['fielding'])} fielding, "
          f"{len(P['innings'])} half-innings, {len(P['scoring_plays'])} scoring plays, "
          f"{len(P['opponent_batting'])} opponent batting, "
          f"{len(P['opponent_pitching'])} opponent pitching, "
          f"{len(P['upcoming'])} scheduled, "
          f"{len(P['big12_results'])} Big 12 results, "
          f"{len(P['team_scoring'])} team scoring, {len(P['team_records'])} RPI rows, "
          f"{len(P['d1_results'])} D1 results, "
          f"{len(P['system_prompt'])}-char prompt"
          + ("" if CHANGED else " (unchanged; not rewritten)"))
