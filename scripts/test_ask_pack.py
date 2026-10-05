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
    DEFINITIONS, base_opponent, build_pack, norm_team, system_prompt, _without_timestamp,
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

PACK = build_pack(SEED)


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
    return sorted(_re.findall(r'"([a-z_]+)"', m.group(1))) if m else None


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
if failures:
    print(f"{len(failures)} FAILED")
    sys.exit(1)
print("all ask_pack tests passed")
