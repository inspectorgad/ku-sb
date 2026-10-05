"""Tests for the iCalendar feed, concentrated on the softball-specific traps."""
import sys
import os
from datetime import date, datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ics_feed import (  # noqa: E402
    build_ics, parse_start, select_games, same_apart_from_stamp, _uid,
)

STAMP = datetime(2026, 10, 5, 12, 0, 0, tzinfo=timezone.utc)
failures = []


def check(label, cond, detail=""):
    if cond:
        print(f"  ok   {label}")
    else:
        failures.append(f"{label} {detail}")
        print(f"  FAIL {label} {detail}")


print("parse_start handles the formats the schedule actually publishes")
for text, want in [
    ("5:00 pm", (17, 0)), ("12:00 pm", (12, 0)), ("2:30 pm", (14, 30)),
    ("6 p.m. CT", (18, 0)), ("11:00 am", (11, 0)), ("12:00 am", (0, 0)),
    ("8 a.m. CT", (8, 0)), ("1 PM", (13, 0)),
    ("", None), (None, None), ("TBA", None), ("noon", None),
]:
    check(f"{text!r} -> {want}", parse_start(text) == want, f"got {parse_start(text)}")

print("\na doubleheader produces two distinct events")
dh = [
    {"date": "2026-09-26", "opponent": "Nebraska", "season": "Fall 2026",
     "site": "A", "startTime": "12:00 pm"},
    {"date": "2026-09-26", "opponent": "Nebraska (G2)", "season": "Fall 2026",
     "site": "A", "startTime": "2:30 pm"},
]
check("UIDs differ", _uid(dh[0]) != _uid(dh[1]), f"{_uid(dh[0])} vs {_uid(dh[1])}")
# Even when the opponent string is identical, first pitch must separate them.
same_name = [dict(dh[0]), dict(dh[1], opponent="Nebraska")]
check("UIDs differ even with an identical opponent name",
      _uid(same_name[0]) != _uid(same_name[1]))
feed = build_ics(dh, STAMP)
check("two VEVENTs", feed.count("BEGIN:VEVENT") == 2, str(feed.count("BEGIN:VEVENT")))
check("first pitch 12:00 kept", "DTSTART;TZID=America/Chicago:20260926T120000" in feed)
check("second game 14:30 kept", "DTSTART;TZID=America/Chicago:20260926T143000" in feed)
check("games do not overlap",
      "DTEND;TZID=America/Chicago:20260926T140000" in feed)

print("\nseason selection is by date, not by label order")
games = [
    {"date": "2026-04-17", "opponent": "UCF", "season": "2026"},
    {"date": "2026-09-26", "opponent": "Nebraska", "season": "Fall 2026"},
    {"date": "2027-03-12", "opponent": "Texas Tech", "season": "2027"},
]
picked = select_games(games, date(2027, 2, 1))
check("old seasons dropped", [g["season"] for g in picked] == ["2027"],
      str([g["season"] for g in picked]))
picked2 = select_games(games, date(2026, 10, 1))
check("recent results retained", {g["season"] for g in picked2} == {"Fall 2026", "2027"},
      str({g["season"] for g in picked2}))

print("\nevent content")
played = [{"date": "2026-04-17", "opponent": "UCF", "season": "2026", "site": "A",
           "startTime": "6 p.m. CT", "teamScore": 3, "opponentScore": 6,
           "venue": "Orlando, Fla.", "attendance": 1807,
           "inningScores": "0-1, 2-0", "boxScoreUrl": "https://example/box"}]
f2 = build_ics(played, STAMP)
check("result in the summary", "SUMMARY:KU L 3-6 at UCF" in f2)
check("venue becomes LOCATION", "LOCATION:Orlando\\, Fla." in f2)
check("attendance in description", "Attendance: 1\\,807" in f2)

unscheduled = [{"date": "2027-03-12", "opponent": "Texas Tech", "season": "2027", "site": "A"}]
f3 = build_ics(unscheduled, STAMP)
check("no time -> all-day event", "DTSTART;VALUE=DATE:20270312" in f3)
check("all-day events are transparent", "TRANSP:TRANSPARENT" in f3)
check("says the time is unannounced", "First pitch to be announced" in f3)

fall = [{"date": "2026-10-01", "opponent": "Rockhurst", "season": "Fall 2026",
         "site": "H", "startTime": "5:00 pm"}]
check("fall games are flagged as exhibitions",
      "Fall exhibition" in build_ics(fall, STAMP))

print("\nhousekeeping")
a = build_ics(played, STAMP)
b = build_ics(played, datetime(2026, 10, 6, 9, 0, 0, tzinfo=timezone.utc))
check("timestamp-only changes are not a rewrite", same_apart_from_stamp(a, b))
check("a real change is a rewrite",
      not same_apart_from_stamp(a, build_ics(unscheduled, STAMP)))
check("CRLF line endings", a.endswith("\r\n") and "\r\n" in a)
check("calendar is closed", a.rstrip().endswith("END:VCALENDAR"))
check("timezone is declared", "BEGIN:VTIMEZONE" in a and "TZID:America/Chicago" in a)
long_name = [{"date": "2026-04-17", "season": "2026", "site": "H",
              "opponent": "A Very Long Opponent Name That Forces RFC 5545 Line Folding Behaviour"}]
check("long lines are folded under 75 octets",
      all(len(l.encode()) <= 75 for l in build_ics(long_name, STAMP).split("\r\n")))

print()
if failures:
    print(f"{len(failures)} FAILED")
    sys.exit(1)
print("all ics_feed tests passed")
