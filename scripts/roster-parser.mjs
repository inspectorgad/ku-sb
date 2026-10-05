// The roster, read out of the page's visible text.
//
// Split from the scraper so it can be tested against a committed copy of that
// text without a browser, which is the only way to check it from a machine
// that cannot reach kuathletics.com.
//
// The page renders each player as a run of label/value lines:
//
//     Jersey Number
//     1
//     Tehya Pitts
//     Position
//     OF
//     Academic Year
//     Sr.
//     Height
//     5' 4''
//     Custom Field 1
//     L/L
//     Hometown
//     Corinth, Texas
//     Last School
//     McLennan Community College
//
// Not every player has every label — height is on 16 of 26, bats/throws on 17
// — so the fields are read by their labels rather than at fixed offsets from
// the jersey number. The previous parser took i+3 and i+4 and therefore could
// only ever find the position, which is why the roster carried three fields
// while the page carried seven.

/** Labels the page uses, mapped to the names the seed uses. */
const FIELDS = {
  'Position': 'position',
  'Academic Year': 'academicYear',
  'Height': 'height',
  // The site's own label for bats/throws, as a generic custom field. Its
  // values are L/L, L/R and R/R — which hand they bat and throw with.
  'Custom Field 1': 'batsThrows',
  'Hometown': 'hometown',
  'Last School': 'lastSchool',
};

const NUMBER = /^\d{1,2}$/;
const NAME = /^[A-Za-z'.-]+( [A-Za-z'.-]+)+$/;

/**
 * Every player on the page, in roster order.
 *
 * A block runs from one "Jersey Number" to the next, so a missing label simply
 * leaves its field empty rather than shifting every later value up by one.
 */
export function parseRoster(text) {
  const lines = String(text || '').split('\n').map((l) => l.trim());
  const starts = [];
  for (let i = 0; i < lines.length; i++) {
    if (lines[i] === 'Jersey Number') starts.push(i);
  }

  const roster = [];
  for (let b = 0; b < starts.length; b++) {
    const from = starts[b];
    const to = b + 1 < starts.length ? starts[b + 1] : lines.length;
    const jerseyNumber = lines[from + 1] || '';
    const name = lines[from + 2] || '';
    if (!NUMBER.test(jerseyNumber) || !NAME.test(name)) continue;

    const player = { name, jerseyNumber, position: '' };
    for (let i = from + 3; i < to; i++) {
      // "Full Bio" ends the player's own fields; anything after it belongs to
      // the card's links, and it appears more often than once per player.
      if (lines[i].startsWith('Full Bio')) break;
      const field = FIELDS[lines[i]];
      if (field) {
        const value = (lines[i + 1] || '').trim();
        // A label immediately followed by another label means the value is
        // absent, not that the next label is the value.
        if (value && !(value in FIELDS) && value !== 'Jersey Number') {
          player[field] = value;
        }
      }
    }
    roster.push(player);
  }
  return roster;
}
