"""Pure parsing helpers shared by update-seed.py and its tests.

Split out of update-seed.py for the same reason roster-parser.mjs was split
out of the scraper: so the fiddly bits can be tested on their own. The script
itself does its work at import time — reading scraped/, writing seed.json —
so a test that imported it would regenerate the seed as a side effect.

Everything here is a pure function of its arguments: no files, no network.

The recurring hazard these guard against is Sidearm's "0". The feed writes the
string "0" where a field does not apply rather than leaving it blank, and "0"
is truthy in Python. That has been read as real data twice — the `substitute`
sequence, where it made all 816 stat lines substitutes, and the umpire crew,
where it printed "0" as the umpire at second base 75 times in one season.
"""

UMPIRE_ORDER = ["Home Plate", "First", "Second Base", "Third Base",
                "Left Field", "Right Field"]


def to_int(value):
    """A count, where a blank or unparseable value is zero.

    Note this is the opposite reading of "0" from the helpers below: here it
    is a real count that must come through as the number zero.
    """
    try:
        return int(str(value).strip() or 0)
    except ValueError:
        return 0


def decision(value):
    """Did this pitcher get the decision?

    Sidearm marks one by putting the pitcher's updated record in the
    wins/losses field ("8-5"); "0" or blank means no decision. Saves are a
    plain count and do not come through here.
    """
    return 0 if str(value or "0").strip() in ("", "0") else 1


def umpire_crew(crew):
    """
    The crew as one line, in the order a box score prints it:
    "Home Plate: Craig Hyde · First: Joshua Fox". Nothing needs them apart.

    A position nobody worked arrives as "0", not as blank, and was being
    printed as the umpire's name. Dropping those leaves every 2026 crew at
    three or four, which is what college softball fields; six-person crews
    are a College World Series thing.
    """
    if not isinstance(crew, dict):
        return ""

    def worked(value):
        value = (value or "").strip()
        return "" if value in ("", "0") else value

    named = [f"{k}: {worked(crew.get(k))}" for k in UMPIRE_ORDER if worked(crew.get(k))]
    named += [f"{k}: {worked(v)}" for k, v in crew.items()
              if k not in UMPIRE_ORDER and worked(v)]
    return " · ".join(named)
