"""Tests for scripts/seed_helpers.py, the seed builder's parsing helpers.

Weighted toward the sentinel values the source uses, because those are what
has actually gone wrong here. Sidearm writes "0" where a field does not apply
rather than leaving it blank, and "0" is a truthy string in Python — which has
now twice been read as real data: once for the `substitute` sequence, making
all 816 stat lines substitutes, and once for the umpire crew, printing "0" as
the name of the umpire at second base 75 times in a season.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from seed_helpers import (  # noqa: E402
    canonical_name, decision, fold_variant_names, to_int, umpire_crew,
)

failures = []


def check(name, got, want):
    if got == want:
        print(f"  ok   {name}")
    else:
        failures.append(name)
        print(f"  FAIL {name}\n         got: {got!r}\n        want: {want!r}")


print("umpire_crew")

check(
    "a full crew keeps box-score order",
    umpire_crew({"First": "Joshua Fox", "Home Plate": "Craig Hyde",
                 "Third Base": "Dana Pruitt"}),
    "Home Plate: Craig Hyde · First: Joshua Fox · Third Base: Dana Pruitt",
)

check(
    'a position nobody worked is "0" and is dropped',
    umpire_crew({"Home Plate": "Brady Sanderson", "First": "Michael Hernandez",
                 "Second Base": "0", "Third Base": "Budda Ewald",
                 "Left Field": "0", "Right Field": "0"}),
    "Home Plate: Brady Sanderson · First: Michael Hernandez · Third Base: Budda Ewald",
)

check(
    "blank and whitespace are dropped the same way",
    umpire_crew({"Home Plate": "Craig Hyde", "First": "", "Second Base": "   "}),
    "Home Plate: Craig Hyde",
)

check(
    "an unknown position is kept, after the known ones",
    umpire_crew({"Second Base": "Dana Pruitt", "Replay": "Sam Oyler"}),
    "Second Base: Dana Pruitt · Replay: Sam Oyler",
)

check("a crew of nothing but zeroes is empty",
      umpire_crew({"Home Plate": "0", "First": "0"}), "")
check("no crew at all is empty", umpire_crew(None), "")
check("a non-dict is empty, not a crash", umpire_crew("Home Plate: Craig Hyde"), "")

# An umpire genuinely named "0" does not exist, but a name that merely starts
# with a digit must survive — the check is for the exact sentinel, not a prefix.
check("a name containing a zero survives",
      umpire_crew({"Home Plate": "0 Hyde"}), "Home Plate: 0 Hyde")

print("to_int")

# The same sentinel, read the other way round: these are counts, where "0"
# must parse as the number zero rather than be discarded as absent.
check("the string zero is the number zero", to_int("0"), 0)
check("a blank is zero", to_int(""), 0)
check("None is zero", to_int(None), 0)
check("a positive count parses", to_int("3"), 3)

print("decision")

# The same sentinel again, read a third way: Sidearm records a decision by
# putting the pitcher's updated record in the field, so "0" means she got none.
check("a record means she got the decision", decision("8-5"), 1)
check("zero means no decision", decision("0"), 0)
check("blank means no decision", decision(""), 0)
check("None means no decision", decision(None), 0)

print("canonical_name")

ROSTER = {"Presley Limbaugh", "Chloe Barber", "Blakely Barber", "Sam Claire"}
KNOWN = {n.lower(): {"name": n} for n in ROSTER}

check("a roster spelling is returned as it is",
      canonical_name("Presley Limbaugh", KNOWN, ROSTER), "Presley Limbaugh")
check("an initial resolves to the roster spelling",
      canonical_name("P. Limbaugh", KNOWN, ROSTER), "Presley Limbaugh")
check("a different first name on the same surname resolves too",
      canonical_name("Samantha Claire", KNOWN, ROSTER), "Sam Claire")

# The bug this function had: it short-circuited on any name it had seen, and
# by the time it ran it had already recorded "P. Limbaugh" as a player of her
# own — so it returned that and never looked at the roster.
check("a variant already recorded as its own player still resolves",
      canonical_name("P. Limbaugh",
                     {**KNOWN, "p. limbaugh": {"name": "P. Limbaugh"}}, ROSTER),
      "Presley Limbaugh")

check("an ambiguous surname is left alone",
      canonical_name("B. Barber", {**KNOWN, "c. barber": {"name": "C. Barber"}},
                     ROSTER | {"Bailey Barber"}), "B. Barber")
check("someone not on the roster keeps her recorded spelling",
      canonical_name("Ada Van Dyke",
                     {**KNOWN, "ada van dyke": {"name": "Ada Van Dyke"}}, ROSTER),
      "Ada Van Dyke")
check("a single-word name is left alone",
      canonical_name("Limbaugh", KNOWN, ROSTER), "Limbaugh")

print("fold_variant_names")

# The roster settles a name only while the player is on it. Eight of 2026's
# players had left by the following October, so the roster could say nothing
# and the jersey number had to.
SPLIT = {"P. Limbaugh": {"jerseyNumber": "4"},
         "Presley Limbaugh": {"jerseyNumber": "4"}}
check("one jersey, one surname, one initial folds",
      fold_variant_names(SPLIT), {"P. Limbaugh": "Presley Limbaugh"})

check("two real first names are two players, not a fold",
      fold_variant_names({"Chloe Barber": {"jerseyNumber": "26"},
                          "Carly Barber": {"jerseyNumber": "26"}}), {})
check("different jerseys are different players",
      fold_variant_names({"Chloe Barber": {"jerseyNumber": "26"},
                          "Blakely Barber": {"jerseyNumber": "3"}}), {})
check("a blank jersey is not evidence",
      fold_variant_names({"P. Limbaugh": {"jerseyNumber": ""},
                          "Presley Limbaugh": {"jerseyNumber": ""}}), {})
check("a different initial on the same jersey does not fold",
      fold_variant_names({"B. Barber": {"jerseyNumber": "26"},
                          "Chloe Barber": {"jerseyNumber": "26"}}), {})
check("nothing to fold is empty, not a crash", fold_variant_names({}), {})

if failures:
    print(f"\n{len(failures)} failing: {', '.join(failures)}")
    sys.exit(1)
print("\nall update_seed tests passed")
