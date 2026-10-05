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
                  "'Fall 2026'",
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
    "opponent_batting / opponent_pitching": "the other side's box score lines. These "
        "cover only the games that team played against Kansas — two or three in a "
        "weekend series — so they are a record of those meetings and not of that "
        "opponent's season. Never present them as season figures",
}


# The instructions Claude is given, shared by the dashboard (docs/ask.js) and
# the app (AskEngine.kt) so both ask the same way. The tables are fetched by
# Claude's own code through the get_table tool; this only says how.
SYSTEM_RULES = """You are the analyst behind the Kansas Jayhawks softball app and dashboard. You answer questions from coaches and fans about the team, using only the season data you are given.

The complete data is available to your Python code through the get_table tool: tables games, innings (one row per half-inning), batting (one row per KU batter per game), pitching, fielding, scoring_plays (how each run scored), opponent_batting and opponent_pitching (the other side of every box score), upcoming, standings, poll, rankings, roster, and definitions. Call it from inside code execution, for example: import json, pandas as pd; bat = pd.DataFrame(json.loads(await get_table({'table': 'batting'}))). Join tables on (season, date, opponent). A summary of the smaller tables is below for orientation.

How to answer:
- Compute every number with code from the tables. Do not estimate, recall, or do arithmetic in your head, even for a simple total.
- The player lines add up. Unlike basketball, softball has no team-level bucket that belongs to no individual: across every played game the Kansas batting lines sum exactly to the game's runs and hits, the fielding lines to its errors, and the pitching lines to the opponent's runs. So a team question can be answered by summing the player tables, and you should not go looking for a discrepancy. One exception, on the opponent's side only: softball scoring allows an error charged to the team rather than to a fielder, and that happened once in 2026 (Houston on March 13, three errors reported and two charged to players), so opponent_batting and the game's own opp_errors can differ by one. Prefer the game row for an opponent's error total.
- opponent_batting and opponent_pitching are only the games that team played against Kansas. Two or three meetings is not a season, so say what the sample is and never call a figure from them that opponent's season average.
- Innings pitched are stored as outs. Divide by three for innings, and never read "7.2 innings" as 7.67 — it is 7 innings and 2 outs, which is 23 outs. ERA is scaled to seven innings (er * 21 / outs), because a regulation game is seven.
- Fall games are exhibitions. They count toward no record, and are a separate season labelled "Fall 2026". Leave them out unless the question asks about the fall, and say so when it matters.
- A missing half-inning is not a scoreless one. The home team does not bat in the last inning of a game it leads, and a run-rule game ends early; the innings table marks this with batted. Never average an unbatted half-inning as a zero.
- Doubleheaders share a date and an opponent; the second game is marked "(G2)". Strip that suffix before grouping a series, and keep the two games distinct when counting.
- Softball can tie, so a record is not simply wins and games-minus-wins. Check for result "T".
- Use softball conventions: averages as .328, ERA to two decimals, records as 36-21, innings as 7.2.
- The Big 12 record in the standings table excludes the conference tournament; the conference flag on games does not. Say which you used when it changes the answer.
- Name small samples plainly (for example "only 3 games", or "one weekend series").
- If the data cannot answer the question — injuries, practice, recruiting, pitch-by-pitch detail, anything not in the box scores — say so in a sentence instead of guessing.
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


def base_opponent(name):
    """'Baylor (G2)' -> 'Baylor'."""
    return re.sub(r"\s*\(G\d\)\s*$", "", name or "").strip()


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


def build_pack(seed):
    players = {p["name"].lower(): p for p in seed.get("players", [])}
    conference = {norm_team(s["team"]) for s in seed.get("standings", [])} - {norm_team("Kansas")}

    games, innings, batting, pitching, fielding, scoring, upcoming = [], [], [], [], [], [], []
    opponent_batting, opponent_pitching = [], []
    for g in sorted(seed.get("games", []), key=lambda x: (x["date"], x["opponent"])):
        base = {"season": g["season"], "date": g["date"], "opponent": g["opponent"]}
        is_fall = g["season"].startswith("Fall")
        in_conf = norm_team(g["opponent"]) in conference
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
                      "box_score_url": g.get("boxScoreUrl") or None})
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
        "standings": [{k: st.get(k) for k in ("season", "team", "confW", "confL", "overallW",
                                              "overallL", "nationalRank", "rpiRank")}
                      for st in seed.get("standings", [])],
        "poll": None if not current_poll else {
            "season": current_poll["season"], "name": current_poll.get("name") or "Poll",
            "updated": current_poll.get("updated"),
            "rows": [{k: r.get(k) for k in ("rank", "rankLabel", "team", "record", "points",
                                            "previous", "firstPlaceVotes")}
                     for r in current_poll.get("rows", [])]},
        # Every weekly ranking snapshot kept, so "when did KU peak in the RPI"
        # is answerable rather than only "where are they now".
        "rankings": [{"date": h.get("date"), "source": h.get("source"),
                      "season": h.get("season"), "team": t.get("team"),
                      "rank": t.get("rank"), "record": t.get("record")}
                     for h in seed.get("rankingHistory", []) for t in h.get("teams", [])],
        "roster": [{k: p.get(k) for k in ("name", "jerseyNumber", "position", "active")}
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


def write_pack(seed, path):
    """Writes the pack; returns False when only the timestamp would change."""
    pack = build_pack(seed)
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


if __name__ == "__main__":
    # Rebuilding straight from the committed seed, so the published page can
    # never serve an ask-data.json older than the data the rest of it shows.
    with open("app/src/main/assets/seed.json") as f:
        SEED = json.load(f)
    P = build_pack(SEED)
    CHANGED = write_pack(SEED, "docs/ask-data.json")
    print(f"ask data: {len(P['games'])} games, {len(P['batting'])} batting lines, "
          f"{len(P['pitching'])} pitching, {len(P['fielding'])} fielding, "
          f"{len(P['innings'])} half-innings, {len(P['scoring_plays'])} scoring plays, "
          f"{len(P['opponent_batting'])} opponent batting, "
          f"{len(P['opponent_pitching'])} opponent pitching, "
          f"{len(P['upcoming'])} scheduled, {len(P['system_prompt'])}-char prompt"
          + ("" if CHANGED else " (unchanged; not rewritten)"))
