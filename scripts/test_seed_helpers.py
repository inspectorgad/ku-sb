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
from seed_helpers import decision, to_int, umpire_crew  # noqa: E402

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

if failures:
    print(f"\n{len(failures)} failing: {', '.join(failures)}")
    sys.exit(1)
print("\nall update_seed tests passed")
