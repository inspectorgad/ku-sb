"""The schedule as an iCalendar feed, for phones to subscribe to.

An iPhone cannot install the APK, but it can subscribe to a calendar, and a
subscribed calendar refreshes itself. So the schedule is published as one:
every game, with first pitch and venue, rewritten whenever the scrape changes
the seed, so a time the conference moves reaches the phone without anybody
doing anything.

Times on the athletics schedule are Central, so events are written in
America/Chicago with the zone spelled out in a VTIMEZONE rather than converted
to UTC here. A softball season runs February to June: it opens in CST and
spends most of itself in CDT, so fixed offsets would put most of it an hour out.

Four things differ from the basketball feed this is adapted from, and each one
is a bug if copied over unchanged:
  * Doubleheaders. Two games share a date AND an opponent, so a UID built from
    those alone collides and a calendar shows one game instead of two. First
    pitch is part of the UID here.
  * Time format. The softball schedule publishes "5:00 pm", not "17:00".
  * Site codes. "H"/"A"/"N", not "home"/"away"/"neutral".
  * Season labels. Sorting ["2026", "2027", "Fall 2026"] puts the fall slate
    last, so picking the final label would publish an exhibition season as the
    current one. This selects by DATE instead.
"""

import re
from datetime import date, datetime, timedelta

# US Central since 2007: CDT from the second Sunday of March, CST from the first
# Sunday of November, both at 02:00 local.
VTIMEZONE = [
    "BEGIN:VTIMEZONE",
    "TZID:America/Chicago",
    "BEGIN:DAYLIGHT",
    "TZOFFSETFROM:-0600",
    "TZOFFSETTO:-0500",
    "TZNAME:CDT",
    "DTSTART:19700308T020000",
    "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU",
    "END:DAYLIGHT",
    "BEGIN:STANDARD",
    "TZOFFSETFROM:-0500",
    "TZOFFSETTO:-0600",
    "TZNAME:CST",
    "DTSTART:19701101T020000",
    "RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU",
    "END:STANDARD",
    "END:VTIMEZONE",
]

# Two hours holds a seven-inning game without running into the second half of a
# doubleheader, which typically starts two and a half hours after the first.
GAME_LENGTH = timedelta(hours=2)

# How far back the feed still carries finished games, so a subscriber opening
# the calendar sees the weekend just played as well as the one coming.
LOOKBACK = timedelta(days=14)


def _escape(text):
    return (text.replace("\\", "\\\\").replace(";", "\\;")
            .replace(",", "\\,").replace("\n", "\\n"))


def _fold(line):
    """RFC 5545 folding: at most 75 octets a line, continuations start with a space."""
    out, cur = [], b""
    for ch in line:
        enc = ch.encode("utf-8")
        if len(cur) + len(enc) > (75 if not out else 74):
            out.append(cur.decode("utf-8"))
            cur = b""
        cur += enc
    out.append(cur.decode("utf-8"))
    return "\r\n ".join(out)


def parse_start(text):
    """"5:00 pm" / "12 p.m. CT" / "6:30 PM" -> (hour, minute) in 24h, or None.

    The schedule writes times a dozen ways and leaves them blank until the
    conference sets one, so anything unrecognised means "no time yet" rather
    than an exception.
    """
    if not text:
        return None
    cleaned = str(text).strip().lower().replace(".", "")
    m = re.search(r"\b(\d{1,2})(?::(\d{2}))?\s*([ap])m?\b", cleaned)
    if not m:
        return None
    hour = int(m.group(1))
    minute = int(m.group(2) or 0)
    if not (1 <= hour <= 12) or not (0 <= minute <= 59):
        return None
    if m.group(3) == "p" and hour != 12:
        hour += 12
    if m.group(3) == "a" and hour == 12:
        hour = 0
    return hour, minute


def _uid(g):
    """Stable per game, so an edit updates the event rather than adding one.

    First pitch is included because a doubleheader repeats date and opponent;
    without it the two halves collide on one UID and only one shows up.
    """
    key = re.sub(r"[^a-z0-9]+", "-", (g.get("opponent") or "").lower()).strip("-")
    slot = re.sub(r"[^a-z0-9]+", "", (g.get("startTime") or "").lower())
    suffix = f"-{slot}" if slot else ""
    return f"{g['date']}-{key}{suffix}@ku-sb"


def _site_word(g):
    return {"H": "vs", "A": "at", "N": "vs"}.get((g.get("site") or "").upper(), "vs")


def _summary(g):
    site = _site_word(g)
    us, them = g.get("teamScore"), g.get("opponentScore")
    if us is not None and them is not None:
        return f"KU {'W' if us > them else 'L'} {us}-{them} {site} {g['opponent']}"
    return f"KU Softball {site} {g['opponent']}"


def _description(g):
    parts = []
    if str(g.get("season", "")).startswith("Fall"):
        parts.append("Fall exhibition — does not count toward the official record")
    if (g.get("site") or "").upper() == "N":
        parts.append("Neutral site")
    if g.get("inningScores"):
        parts.append(f"Innings: {g['inningScores']}")
    if g.get("attendance"):
        parts.append(f"Attendance: {g['attendance']:,}")
    if not g.get("startTime") and g.get("teamScore") is None:
        parts.append("First pitch to be announced")
    if g.get("boxScoreUrl"):
        parts.append(g["boxScoreUrl"])
    return "\n".join(parts)


def select_games(games, today):
    """Recent and upcoming games, chosen by date rather than by season label."""
    cutoff = (today - LOOKBACK).isoformat()
    return [g for g in games if g.get("date", "") >= cutoff]


def build_ics(games, stamp):
    """The feed as a string. `stamp` is a UTC datetime, for DTSTAMP only."""
    dtstamp = stamp.strftime("%Y%m%dT%H%M%SZ")
    lines = [
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//ku-sb//schedule//EN",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        "X-WR-CALNAME:KU Softball",
        "X-WR-TIMEZONE:America/Chicago",
        # A hint to refresh twice a day; iOS sets its own interval regardless.
        "REFRESH-INTERVAL;VALUE=DURATION:PT12H",
        "X-PUBLISHED-TTL:PT12H",
        *VTIMEZONE,
    ]
    for g in sorted(games, key=lambda g: (g["date"], g.get("startTime") or "", g.get("opponent") or "")):
        day = date.fromisoformat(g["date"])
        lines += ["BEGIN:VEVENT", f"UID:{_uid(g)}", f"DTSTAMP:{dtstamp}"]
        clock = parse_start(g.get("startTime"))
        if clock:
            start = datetime(day.year, day.month, day.day, clock[0], clock[1])
            end = start + GAME_LENGTH
            lines += [
                f"DTSTART;TZID=America/Chicago:{start:%Y%m%dT%H%M%S}",
                f"DTEND;TZID=America/Chicago:{end:%Y%m%dT%H%M%S}",
            ]
        else:
            # No first pitch announced: an all-day event, so it holds the date
            # without claiming an hour nobody has published.
            lines += [
                f"DTSTART;VALUE=DATE:{day:%Y%m%d}",
                f"DTEND;VALUE=DATE:{day + timedelta(days=1):%Y%m%d}",
                "TRANSP:TRANSPARENT",
            ]
        lines.append(f"SUMMARY:{_escape(_summary(g))}")
        if g.get("venue"):
            lines.append(f"LOCATION:{_escape(g['venue'])}")
        desc = _description(g)
        if desc:
            lines.append(f"DESCRIPTION:{_escape(desc)}")
        lines.append("END:VEVENT")
    lines.append("END:VCALENDAR")
    return "\r\n".join(_fold(line) for line in lines) + "\r\n"


def same_apart_from_stamp(a, b):
    """True when two feeds differ in DTSTAMP at most, so a rewrite would be noise."""
    def strip(s):
        return re.sub(r"^DTSTAMP:.*$", "", s or "", flags=re.M)
    return strip(a) == strip(b)


def main():
    import json
    import os
    from datetime import timezone

    with open("app/src/main/assets/seed.json") as f:
        seed = json.load(f)
    now = datetime.now(timezone.utc)
    games = select_games(seed.get("games", []), now.date())

    out = "docs/ku-sb.ics"
    feed = build_ics(games, now)
    existing = None
    if os.path.exists(out):
        # newline="" or Python translates the feed's CRLF endings to LF on the
        # way in, and the comparison below then finds every line different.
        with open(out, newline="") as f:
            existing = f.read()
    if same_apart_from_stamp(existing, feed):
        print(f"{out} unchanged (ignoring timestamp); not rewriting")
        return
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", newline="") as f:
        f.write(feed)
    timed = sum(1 for g in games if parse_start(g.get("startTime")))
    print(f"{out} written: {len(games)} events, {timed} with a first pitch")


if __name__ == "__main__":
    main()
