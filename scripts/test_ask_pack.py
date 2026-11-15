"""Tests for the Ask data pack.

Weighted toward the claims the system prompt makes. The prompt tells Claude
that the player lines add up, that an unbatted half-inning is not a zero, and
that fall games count for nothing — and Claude will act on all three. A prompt
that asserts something the data does not support is worse than no prompt, so
those statements are tested against the pack the same way any other output is.
"""
import collections
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ask_pack import (  # noqa: E402
    DEFINITIONS, base_opponent, build_pack, norm_team, system_prompt, team_key,
    _without_timestamp,
)

failures = []


def check(label, cond, detail=""):
    if cond:
        print(f"  ok   {label}")
    else:
        failures.append(f"{label} {detail}")
        print(f"  FAIL {label} {detail}")


def line(player, **kw):
    return {"player": player, **kw}


SEED = {
    "generatedAt": "2026-10-05T12:00:00Z",
    "players": [
        {"name": "Ada Alpha", "jerseyNumber": "1", "position": "SS", "active": True},
        {"name": "Bea Beta", "jerseyNumber": "2", "position": "P", "active": True},
        {"name": "Cal Gamma", "jerseyNumber": "3", "position": "OF", "active": True},
    ],
    "standings": [
        {"season": "2026", "team": "Kansas", "confW": 10, "confL": 8,
         "overallW": 36, "overallL": 21},
        {"season": "2026", "team": "Iowa St.", "confW": 12, "confL": 6,
         "overallW": 30, "overallL": 20, "nationalRank": 18, "rpiRank": 22},
    ],
    "polls": [{"season": "2026", "name": "ESPN.com/USA Softball Top 25",
               "updated": "Through Games JUN. 5, 2026",
               "rows": [{"rank": 1, "team": "Texas", "record": "53-12", "points": "625"}]}],
    "rankingHistory": [
        {"date": "2026-06-04", "source": "rpi", "season": "2026",
         "teams": [{"team": "Kansas", "rank": 37, "record": "36-21"}]}
    ],
    "games": [
        {   # A home win, KU leading so it never bats in the 7th.
            "date": "2026-03-01", "opponent": "Iowa State", "season": "2026",
            "teamScore": 4, "opponentScore": 1, "site": "H",
            "teamHits": 3, "opponentHits": 2, "teamErrors": 1, "opponentErrors": 0,
            "inningScores": "1-0, 0-1, 3-0, 0-0, 0-0, 0-0, x-0",
            "opponentRank": 18, "opponentRecord": "30-20", "attendance": 900,
            "scoring": [{"inn": 1, "ku": True, "text": "Alpha homered.", "us": 1, "them": 0}],
            "lines": [
                line("Ada Alpha", gs=1, spot=1, pos="ss", ab=4, r=3, h=3, hr=1,
                     po=2, a=1, e=1),
                # The FLEX pitcher: she fields and pitches and never bats.
                line("Bea Beta", gs=1, spot=10, pos="p", p=1, outs=21, ra=1, er=1,
                     ks=8, w=1, np=95, bf=25, po=1),
                # A pinch runner: no plate appearance, but she scored, so she
                # did take a turn and belongs in the batting table.
                line("Cal Gamma", spot=1, sub=1, pos="pr", r=1),
            ],
        },
        {   # A fall exhibition: no lines, counts for nothing.
            "date": "2026-09-26", "opponent": "Nebraska", "season": "Fall 2026",
            "teamScore": 0, "opponentScore": 6, "site": "A",
        },
        {   # Scheduled, not played.
            "date": "2027-02-12", "opponent": "Omaha", "season": "2027",
            "site": "H", "startTime": "5:00 pm",
        },
    ],
}

# The other teams' data, as the three scraped files shape it. Each row is here
# to exercise one of the cleaning rules in _big12_results; the comments say
# which, because a row that looks redundant is the one holding a rule up.
INDEX = {
    "big12Games": {
        # An ordinary conference game, well before the tournament window.
        "100": {"date": "2026-04-01", "season": "2026", "conferenceGame": True,
                "bracket": False,
                "home": {"name": "Kansas", "score": 5, "winner": True, "inConference": True},
                "away": {"name": "Iowa St.", "score": 2, "winner": False, "inConference": True}},
        # A conference game inside the window: the conference tournament, which
        # the official standings exclude and the scoreboard's flag does not.
        "101": {"date": "2026-05-07", "season": "2026", "conferenceGame": True,
                "bracket": False,
                "home": {"name": "Iowa St.", "score": 3, "winner": True, "inConference": True},
                "away": {"name": "Kansas", "score": 1, "winner": False, "inConference": True}},
        # The NCAA tournament, which sets the window: fence = this minus 10 days.
        "102": {"date": "2026-05-15", "season": "2026", "conferenceGame": False,
                "bracket": True,
                "home": {"name": "Kansas", "score": 4, "winner": True, "inConference": True},
                "away": {"name": "Duke", "score": 0, "winner": False, "inConference": False}},
        # A placeholder for a game that was never played.
        "103": {"date": "2026-05-09", "season": "2026", "conferenceGame": False,
                "bracket": False,
                "home": {"name": "Kansas", "score": 0, "winner": False, "inConference": True},
                "away": {"name": "TBA", "score": 0, "winner": False, "inConference": False}},
        # Both scores 0 and no winner: a score the NCAA never filled in.
        "104": {"date": "2026-03-10", "season": "2026", "conferenceGame": False,
                "bracket": False,
                "home": {"name": "Kansas", "score": 0, "winner": False, "inConference": True},
                "away": {"name": "Omaha", "score": 0, "winner": False, "inConference": False}},
        # One game listed twice, exactly one listing bracket-flagged: a copy.
        "105": {"date": "2026-05-17", "season": "2026", "conferenceGame": False,
                "bracket": True,
                "home": {"name": "Kansas", "score": 6, "winner": False, "inConference": True},
                "away": {"name": "Duke", "score": 8, "winner": True, "inConference": False}},
        "106": {"date": "2026-05-17", "season": "2026", "conferenceGame": False,
                "bracket": False,
                "home": {"name": "Kansas", "score": 6, "winner": False, "inConference": True},
                "away": {"name": "Duke", "score": 8, "winner": True, "inConference": False}},
        # A real doubleheader: same teams, same score, twice, neither flagged.
        "107": {"date": "2026-02-05", "season": "2026", "conferenceGame": False,
                "bracket": False,
                "home": {"name": "Kansas", "score": 8, "winner": True, "inConference": True},
                "away": {"name": "Southern Utah", "score": 0, "winner": False, "inConference": False}},
        "108": {"date": "2026-02-05", "season": "2026", "conferenceGame": False,
                "bracket": False,
                "home": {"name": "Kansas", "score": 8, "winner": True, "inConference": True},
                "away": {"name": "Southern Utah", "score": 0, "winner": False, "inConference": False}},
    }
}

RPI = {
    "updated": "Through Games Jun. 04 2026",
    "data": [
        {"Rank": "1", "School": "Texas", "Record": "53-12", "Conf": "SEC",
         "Road": "14-3", "Neutral": "15-2", "Home": "24-7", "Non-Div I": "0-0", "Prev": "4"},
        {"Rank": "37", "School": "Kansas", "Record": "36-21", "Conf": "Big 12",
         "Road": "9-10", "Neutral": "14-6", "Home": "13-5", "Non-Div I": "0-0", "Prev": "NR"},
        {"Rank": "40", "School": "Iowa St.", "Record": "32-23", "Conf": "Big 12",
         "Road": "8-9", "Neutral": "10-6", "Home": "14-8", "Non-Div I": "0-0", "Prev": "41"},
    ],
}

POLL = {
    "title": "ESPN.com/USA Softball Top 25",
    "updated": "Through Games JUN. 5, 2026",
    "data": [{"Rank": "1", "School": "Texas (25)", "Record": "53-12",
              "Points": "625", "Previous": "3"},
             {"Rank": "T25", "School": "Iowa St.", "Record": "32-23",
              "Points": "30", "Previous": "NR"}],
}

PACK = build_pack(SEED)
OTHERS = build_pack(SEED, INDEX, RPI, POLL)


print("team name keys agree with the rest of the project")
check("'Iowa State' and 'Iowa St.' are one team",
      norm_team("Iowa State") == norm_team("Iowa St."))
check("a doubleheader marker is stripped", norm_team("Baylor (G2)") == norm_team("Baylor"))
check("Utah State is not Utah", norm_team("UTAH STATE") != norm_team("Utah"))
check("base_opponent drops the marker", base_opponent("Baylor (G2)") == "Baylor")

print()
print("tables are built from the seed")
check("played games only", len(PACK["games"]) == 2, f"got {len(PACK['games'])}")
check("the unplayed game is scheduled, not played", len(PACK["upcoming"]) == 1)
check("scheduled game keeps its first pitch",
      PACK["upcoming"][0]["first_pitch_local"] == "5:00 pm")
check("conference flag comes from the standings",
      PACK["games"][0]["conference"] is True)
check("a non-member is not a conference game",
      PACK["upcoming"][0]["conference"] is False)
check("rankings history is carried", len(PACK["rankings"]) == 1)
check("scoring plays are carried", len(PACK["scoring_plays"]) == 1)

print()
print("the prompt's claim that the player lines add up")
by_game = collections.defaultdict(collections.Counter)
for row in PACK["batting"]:
    by_game[(row["date"], row["opponent"])]["r"] += row["r"]
    by_game[(row["date"], row["opponent"])]["h"] += row["h"]
errors = collections.Counter()
for row in PACK["fielding"]:
    errors[(row["date"], row["opponent"])] += row["e"]
allowed = collections.Counter()
for row in PACK["pitching"]:
    allowed[(row["date"], row["opponent"])] += row["ra"]
played = [g for g in PACK["games"] if not g["exhibition"]]
for g in played:
    k = (g["date"], g["opponent"])
    check(f"{g['opponent']}: batting runs == final score",
          by_game[k]["r"] == g["ku_runs"], f"{by_game[k]['r']} vs {g['ku_runs']}")
    check(f"{g['opponent']}: batting hits == team hits",
          by_game[k]["h"] == g["ku_hits"], f"{by_game[k]['h']} vs {g['ku_hits']}")
    check(f"{g['opponent']}: fielding errors == team errors",
          errors[k] == g["ku_errors"], f"{errors[k]} vs {g['ku_errors']}")
    check(f"{g['opponent']}: runs allowed == opponent score",
          allowed[k] == g["opp_runs"], f"{allowed[k]} vs {g['opp_runs']}")

print()
print("who counts as a batter")
names = [r["player"] for r in PACK["batting"]]
check("the FLEX pitcher is left out of batting", "Bea Beta" not in names, names)
check("she is in pitching", any(r["player"] == "Bea Beta" for r in PACK["pitching"]))
check("she is still in fielding", any(r["player"] == "Bea Beta" for r in PACK["fielding"]))
# A pinch runner has no at-bat but is not absent from the game.
check("a pinch runner who scored is a batting row", "Cal Gamma" in names, names)
check("and her at-bats are zero, not missing",
      next(r for r in PACK["batting"] if r["player"] == "Cal Gamma")["ab"] == 0)
check("outs are carried, not innings",
      PACK["pitching"][0]["outs"] == 21, PACK["pitching"][0]["outs"])

print()
print("an unbatted half-inning is not a scoreless one")
seventh = [r for r in PACK["innings"] if r["inning"] == 7]
check("the seventh exists", len(seventh) == 1)
check("KU is marked as not having batted", seventh[0]["ku_batted"] is False)
check("and carries no runs rather than a zero", seventh[0]["ku_runs"] is None,
      seventh[0]["ku_runs"])
check("the opponent did bat", seventh[0]["opp_batted"] is True)
check("every other inning is marked batted",
      all(r["ku_batted"] for r in PACK["innings"] if r["inning"] < 7))

print()
print("fall games are exhibitions")
fall = [g for g in PACK["games"] if g["exhibition"]]
check("the fall game is flagged", len(fall) == 1 and fall[0]["season"] == "Fall 2026")
check("it contributes no batting lines",
      not any(r["season"].startswith("Fall") for r in PACK["batting"]))

print()
print("results allow for a tie")
tie_seed = json.loads(json.dumps(SEED))
tie_seed["games"][0]["teamScore"] = 1
tie_seed["games"][0]["opponentScore"] = 1
check("a level game is T", build_pack(tie_seed)["games"][0]["result"] == "T")

print()
print("the system prompt describes this data")
prompt = PACK["system_prompt"]
check("names the current season as 2026", "2026 is the current season" in prompt)
check("says the fall slate is exhibition", "exhibition" in prompt.lower())
check("tells Claude the lines add up", "player lines add up" in prompt)
check("warns about reading innings as decimals", "7.67" in prompt)
check("carries every definition",
      all(k in prompt for k in DEFINITIONS), "a definition is missing from the prompt")
check("includes the roster", "Ada Alpha" in prompt)
check("includes the standings", "Iowa St." in prompt)
check("is a sensible size", 4000 < len(prompt) < 40000, f"{len(prompt)} chars")

print()
print("rebuilding without a data change is not a rewrite")
later = json.loads(json.dumps(SEED))
later["generatedAt"] = "2026-10-06T09:00:00Z"
check("only the timestamp differs",
      _without_timestamp(build_pack(later)) == _without_timestamp(PACK))
changed = json.loads(json.dumps(SEED))
changed["games"][0]["teamScore"] = 9
check("a real change is a change",
      _without_timestamp(build_pack(changed)) != _without_timestamp(PACK))

print()
print("an empty season does not throw")
empty = build_pack({"generatedAt": "2026-10-05T12:00:00Z", "players": [], "games": []})
check("builds", empty["games"] == [] and empty["batting"] == [])
check("still has a prompt", len(system_prompt(empty)) > 100)

print()
print("one key per school, across sources that spell them differently")
check("the seed's 'Oklahoma Sooners' is the RPI's 'Oklahoma'",
      team_key("Oklahoma Sooners") == team_key("Oklahoma"))
check("'South Florida' is the scoreboard's 'South Fla.'",
      team_key("South Florida") == team_key("South Fla."))
check("'Florida Atlantic' is 'Fla. Atlantic'",
      team_key("Florida Atlantic") == team_key("Fla. Atlantic"))
check("a doubleheader marker does not make a second team",
      team_key("Baylor (G2)") == team_key("Baylor"))
check("the poll's first-place votes are not part of the name",
      team_key("Texas (25)") == team_key("Texas"))
check("different schools stay different", team_key("Utah St.") != team_key("Utah"))
check("per-game rows carry the opponent key",
      PACK["games"][0]["opponent_key"] == team_key("Iowa State"))
check("so do the scheduled ones", PACK["upcoming"][0]["opponent_key"] == team_key("Omaha"))
check("the standings carry a team key", PACK["standings"][0]["team_key"] == team_key("Kansas"))
check("the poll rows do too", PACK["poll"]["rows"][0]["team_key"] == team_key("Texas"))
check("the weekly rankings do too", PACK["rankings"][0]["team_key"] == team_key("Kansas"))

print()
print("an exhibition against a conference school is not a conference game")
fall_conf = json.loads(json.dumps(SEED))
fall_conf["games"][1]["opponent"] = "Iowa State"   # a Big 12 school, in the fall
fall_pack = build_pack(fall_conf)
fall_game = next(g for g in fall_pack["games"] if g["exhibition"])
check("the fall game against a Big 12 school is not conference",
      fall_game["conference"] is False)
check("the same school in the spring still is",
      next(g for g in fall_pack["games"] if not g["exhibition"])["conference"] is True)

print()
print("the scoreboard is cleaned before anything is counted from it")
R = OTHERS["big12_results"]
ids = [r["game_id"] for r in R]
check("a TBA placeholder is dropped", "103" not in ids, ids)
check("a score the NCAA never filled in is kept but flagged",
      next(r for r in R if r["game_id"] == "104")["score_missing"] is True)
check("an ordinary game is not flagged",
      next(r for r in R if r["game_id"] == "100")["score_missing"] is False)
check("one game listed twice keeps the bracket-flagged listing",
      "105" in ids and "106" not in ids, ids)
check("a real doubleheader keeps both games",
      "107" in ids and "108" in ids, ids)
check("a conference game before the window is not the tournament",
      next(r for r in R if r["game_id"] == "100")["big12_tournament"] is False)
check("a conference game inside it is",
      next(r for r in R if r["game_id"] == "101")["big12_tournament"] is True)
check("an NCAA tournament game is not the conference tournament",
      next(r for r in R if r["game_id"] == "102")["big12_tournament"] is False)
check("rows carry both teams' keys",
      all(r["home_key"] and r["away_key"] for r in R))
check("KU's missing games are filled in from the box scores",
      any(r["source"] == "ku_box" for r in R),
      "the seed's 2026-03-01 game is absent from the fixture scoreboard")
ku_box = [r for r in R if r["source"] == "ku_box"]
check("and the filled-in game has its score",
      ku_box and {ku_box[0]["home_runs"], ku_box[0]["away_runs"]} == {4, 1},
      ku_box)

print()
print("team scoring is counted from the cleaned games")
S = {r["team_key"]: r for r in OTHERS["team_scoring"]}
check("only Big 12 teams get a row", set(S) == {team_key("Kansas"), team_key("Iowa St.")},
      sorted(S))
ku = S[team_key("Kansas")]
check("the unfilled game is left out of the count",
      ku["games"] == len([r for r in R if not r["score_missing"]
                          and team_key("Kansas") in (r["home_key"], r["away_key"])]),
      ku["games"])
check("runs for and against are totalled",
      ku["runs_for"] > 0 and ku["runs_against"] > 0, ku)
check("run differential is the difference",
      ku["run_diff"] == ku["runs_for"] - ku["runs_against"])
check("wins, losses and ties account for every game",
      ku["w"] + ku["l"] + ku["t"] == ku["games"], ku)
check("the final-poll record counts only teams in the final poll",
      ku["vs_final_top25_w"] + ku["vs_final_top25_l"] ==
      len([r for r in R if not r["score_missing"]
           and team_key("Kansas") in (r["home_key"], r["away_key"])
           and team_key("Iowa St.") in (r["home_key"], r["away_key"])]),
      ku)

print()
print("every Division I team's RPI row is carried")
T = OTHERS["team_records"]
check("one row per team", len(T) == 3, len(T))
check("the rank is a number", T[0]["rank"] == 1, T[0])
check("a previous rank of 'NR' is not a number",
      next(r for r in T if r["team"] == "Kansas")["previous_rank"] is None)
check("each row says which season", all(r["season"] == "2026" for r in T), T)
check("and when it runs through", all(r["as_of"] for r in T))
check("and carries a key to match on", T[0]["team_key"] == team_key("Texas"))

print()
print("the other-team tables are always there, even with nothing to put in them")
for t in ("big12_results", "team_scoring", "team_records"):
    check(f"{t} is a list when no scrape was passed in", isinstance(PACK[t], list))
    check(f"{t} is empty rather than missing", PACK[t] == [])

print()
print("the prompt describes web search and the other teams")
op = OTHERS["system_prompt"]
check("the web_search tool is named", "web_search" in op)
check("it no longer says the data is all there is",
      "using only the season data" not in op, "the A14 opening line was not changed")
check("it says to look things up on the web instead",
      "with web search on, look for it on the web instead" in op)
check("it names the bodies to prefer", "NFCA" in op and "D1Softball" in op)
check("it does not name volleyball's body", "AVCA" not in op)
check("it says to join on the keys", "team_key" in op)
check("it warns that team_scoring's W-L is approximate",
      "team_records" in op and "approximate" in op)

print()
print("the clients and the pack agree on what tables exist")
# The table list is written in three places — the pack, the dashboard's
# ask.js, and the app's AskEngine. A name that drifts does not fail loudly:
# Claude is simply offered a table that does not exist, or never told about
# one that does, and quietly answers worse.
import re as _re  # noqa: E402

PACK_TABLES = sorted(k for k, v in PACK.items()
                     if k not in ("about", "generated_at", "system_prompt"))


def listed(path, pattern):
    try:
        with open(path) as f:
            text = f.read()
    except OSError:
        return None
    m = _re.search(pattern, text, _re.S)
    # Digits allowed: big12_results has them, and a name class of [a-z_] only
    # would quietly drop it from the file's side of this comparison while the
    # pack's side still had it, reporting a drift that was not there.
    return sorted(_re.findall(r'"([a-z0-9_]+)"', m.group(1))) if m else None


for path, pattern in [
    ("docs/ask.js", r"const TABLES = \[(.*?)\];"),
    ("app/src/main/java/com/example/data/AskEngine.kt", r"val ASK_TABLES = listOf\((.*?)\)"),
]:
    found = listed(path, pattern)
    if found is None:
        print(f"  --   {path} not present yet; skipped")
        continue
    check(f"{path} lists the pack's tables", found == PACK_TABLES,
          f"\n       pack: {PACK_TABLES}\n       file: {found}")
# The prompt must name them too, or Claude never learns they are there.
for t in PACK_TABLES:
    check(f"the prompt mentions {t}", t in PACK["system_prompt"])

print()
print("the real scraped data, as warnings only")
# These read the committed scrape rather than a fixture, so a new spelling or
# a gap in the NCAA feed would make them fail. This script runs in the nightly
# scrape before the data commit, so a failure here would block that night's
# data over something that is not a fault in this code. They warn instead.


def _read(path):
    try:
        with open(os.path.join(ROOT, path)) as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REAL_SEED = _read("app/src/main/assets/seed.json")
REAL_INDEX = _read("scraped/ku-index.json")
REAL_RPI = _read("scraped/rankings-rpi.json")
REAL_POLL = _read("scraped/rankings-poll.json")

if not all((REAL_SEED, REAL_INDEX, REAL_RPI)):
    print("  --   the scraped files are not all here; skipped")
else:
    REAL = build_pack(REAL_SEED, REAL_INDEX, REAL_RPI, REAL_POLL)
    known = {r["team_key"] for r in REAL["team_records"]}
    played = {g["opponent_key"]: g["opponent"] for g in REAL["games"]
              if not g["exhibition"]}
    unknown = {k: v for k, v in played.items() if k not in known}
    if unknown:
        print(f"  WARN {len(unknown)} opponent(s) have no RPI row, so joins on the "
              f"key will drop them: {sorted(unknown.values())}")
        print("       add the spelling to _ALIASES in ask_pack.py")
    else:
        print(f"  ok   all {len(played)} played opponents match an RPI row")

    # team_scoring counts the NCAA scoreboard, which is short a few games, so
    # a difference here is the feed's and not this code's. It is printed so
    # that it is known rather than discovered inside an answer.
    official = {r["team_key"]: r["record"] for r in REAL["team_records"]}
    off_by = []
    for row in REAL["team_scoring"]:
        mine = f"{row['w']}-{row['l']}"
        theirs = official.get(row["team_key"])
        if theirs and mine != theirs:
            off_by.append(f"{row['team']} counted {mine}{'-' + str(row['t']) if row['t'] else ''}, RPI {theirs}")
    if off_by:
        print(f"  WARN {len(off_by)} of {len(REAL['team_scoring'])} Big 12 records differ "
              f"from the RPI, which is why the prompt sends Claude to team_records:")
        for b in off_by:
            print(f"       {b}")
    else:
        print(f"  ok   all {len(REAL['team_scoring'])} Big 12 records match the RPI")

print()
if failures:
    print(f"{len(failures)} FAILED")
    sys.exit(1)
print("all ask_pack tests passed")
