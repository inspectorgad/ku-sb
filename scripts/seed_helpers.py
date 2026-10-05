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


def canonical_name(name, known, roster_names):
    """
    The roster's spelling of a name that appeared in a box score.

    Box scores abbreviate: the same player is "Presley Limbaugh" in one game
    and "P. Limbaugh" in the next. Left alone those become two players
    carrying the same jersey number, with one season split between them — 26
    of 2026's 816 stat lines were filed under a short name across eight
    players.

    `known` maps a lowercased name to the entry already built for it, and is
    what made the original version of this fail: it short-circuited on any
    name it had seen before, and by the time it ran it had already seen
    "P. Limbaugh" as an entry of its own, so it returned that and never tried
    the roster. Only a roster spelling is canonical on sight.

    The fallback match is last name plus first initial, and only when exactly
    one roster player fits. Two who share both — Chloe and Blakely Barber, in
    2026 — leave it ambiguous, and an ambiguous name is left as it is rather
    than merged into the wrong player.
    """
    roster_keys = {n.lower() for n in roster_names}
    key = name.lower()
    if key in roster_keys:
        return known[key]["name"] if key in known else name

    parts = name.split()
    if len(parts) >= 2:
        first, last = parts[0], parts[-1]
        matches = [r for r in roster_names
                   if r.split()[-1].lower() == last.lower()
                   and r.split()[0][:1].lower() == first[:1].lower()]
        if len(matches) == 1:
            return matches[0]

    # Not on the roster at all — a former player, who appears in box scores
    # and nowhere else. Keep whatever spelling is already recorded for her.
    return known[key]["name"] if key in known else name


def _is_initial(first):
    """"P." is an initial; "Presley" is a name."""
    return len(first.rstrip(".")) <= 1


def fold_variant_names(known):
    """
    Which box-score spellings are the same player, for players the roster
    cannot settle.

    `canonical_name` resolves a name by matching it against the roster, which
    works right up until the player has left. Eight of 2026's players were
    gone from the roster by the following October, so "P. Limbaugh" and
    "Presley Limbaugh" sat side by side as two people with one season divided
    between them — and nothing on the current roster could say otherwise.

    What does say otherwise is on both rows already: the same jersey number
    and the same surname. Required together, plus the same first initial, and
    only where exactly one spelling gives a first name rather than an
    initial — so there is one obvious name to keep and no guess about which
    of two real first names was meant.

    Returns {variant spelling: the spelling to keep}, empty when there is
    nothing to fold. Takes the names as given: a blank jersey matches nothing,
    since two unnumbered players sharing a surname are not evidence of
    anything.
    """
    groups = {}
    for name, entry in known.items():
        parts = str(name).split()
        jersey = str((entry or {}).get("jerseyNumber") or "").strip()
        if len(parts) < 2 or not jersey:
            continue
        groups.setdefault((parts[-1].lower(), parts[0][:1].lower(), jersey), []).append(name)

    folded = {}
    for names in groups.values():
        if len(names) < 2:
            continue
        full = [n for n in names if not _is_initial(n.split()[0])]
        # Two real first names on one jersey are two players, or a mistake
        # worth leaving visible. Either way, not something to merge.
        if len(full) != 1:
            continue
        keep = full[0]
        for n in names:
            if n != keep:
                folded[n] = keep
    return folded
