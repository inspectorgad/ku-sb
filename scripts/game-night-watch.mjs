// Same-day results: wait for KU's games to go final, then run the scrape.
//
// The scrape runs on a fixed cadence, and a cron entry cannot know when a game
// ends — a 2 p.m. first pitch finished by 4 would otherwise sit until the next
// scheduled run, which for the nightly 09:00 UTC cron means the next morning.
// More cron slots do not fix that; they are just as blind. This watches the
// day's games instead.
//
//   node scripts/game-night-watch.mjs plan
//       Run by each scrape. Writes watch=true and date=YYYY-MM-DD to
//       $GITHUB_OUTPUT when KU plays within the window, so the scrape can
//       dispatch game-night.yml.
//   node scripts/game-night-watch.mjs watch YYYY-MM-DD
//       Run by game-night.yml. Sleeps until the games should be near their
//       end, polls the NCAA scoreboard until every one is final, then
//       dispatches scrape-data.yml.
//
// Adapted from the basketball watcher. Five things differ, and each one is a
// bug if carried across unchanged:
//
//   * Doubleheaders. Basketball plays one game a day; softball regularly plays
//     two. Watching only the first and dispatching on its final would scrape
//     while game two was still in the third inning, and the second result
//     would wait for the morning — the exact failure this script exists to
//     prevent. Every KU game on the date has to go final.
//   * The run rule. A basketball game cannot end early, so that watcher waits
//     100 minutes before its first poll. A softball game ends after five
//     innings if a team leads by eight, which happens inside about seventy
//     minutes, so polling has to start sooner.
//   * Fall ball. The NCAA's D1 scoreboard carries no fall softball at all, so
//     a watcher pointed at an October exhibition would poll a game that can
//     never turn final and burn a job finding nothing. Fall is a whole season
//     here, not a marker in an opponent's name.
//   * Pitching decisions. The win, loss and save are assigned when the box
//     score is written, not at the final out, and that lags. Scraping too
//     early captures a game with no decision on it.
//   * Published times read "5:00 pm", not "17:00" — and most of the schedule
//     has no time at all until the week of the game.

import { readFileSync, appendFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

const API = 'https://ncaa-api.henrygd.me';
const TEAM_SEO = 'kansas';
const ZONE = 'America/Chicago'; // the athletics schedule lists every time in Central

const MIN = 60_000;
const HOUR = 60 * MIN;
// A scrape dispatches the watcher when first pitch is at most this far off.
export const PLAN_WINDOW = 4.5 * HOUR;
// A seven-inning game runs about two hours, but the run rule can end one after
// five innings — roughly seventy minutes. Polling starts there rather than at
// the length of a full game, or a run-rule blowout sits unscraped for an hour.
const FIRST_POLL_AFTER = 70 * MIN;
const POLL_EVERY = 5 * MIN;
// Give up this long after first pitch: a postponed game, a rain delay that
// never resumes, or a scoreboard that never flips. The nightly scrape still
// catches it. A doubleheader needs longer — game two starts around two and a
// half hours after game one and takes two more.
const GIVE_UP_AFTER = 5 * HOUR;
const GIVE_UP_AFTER_DOUBLEHEADER = 8.5 * HOUR;
// The box score trails the final out, and the pitching decisions trail the box
// score. Longer than basketball's wait for exactly that reason.
const SETTLE = 15 * MIN;
// GitHub stops a job at six hours. A watcher dispatched early in the window
// plus a doubleheader can together need more, so a watcher that reaches this
// budget hands over to a fresh run of itself rather than being killed.
const RUN_BUDGET = 340 * MIN;
// With no published time, assume an afternoon start. Softball's common slots
// are early afternoon, and guessing early is the safe direction: polling a
// game that has not started yet costs one harmless request every five minutes,
// while guessing late can miss the final altogether.
const DEFAULT_TIME = '13:00';

/** The UTC instant of a Central wall-clock time, DST included. */
export function centralToUtc(date, time) {
  const [y, mo, d] = date.split('-').map(Number);
  const [h, mi] = time.split(':').map(Number);
  const wall = Date.UTC(y, mo - 1, d, h, mi);
  // Read the zone's offset at that moment and correct for it; a second pass
  // settles the rare case where the first guess lands across a clock change.
  let t = wall + 6 * HOUR;
  for (let i = 0; i < 2; i++) t = wall - offsetMs(t);
  return t;
}

function offsetMs(t) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', {
      timeZone: ZONE, hourCycle: 'h23',
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
    }).formatToParts(new Date(t)).map((p) => [p.type, p.value]),
  );
  const asUtc = Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute);
  return asUtc - Math.floor(t / MIN) * MIN;
}

/** Today's date in Central, as the schedule dates are. */
export function centralDate(t) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', { timeZone: ZONE, year: 'numeric', month: '2-digit', day: '2-digit' })
      .formatToParts(new Date(t)).map((p) => [p.type, p.value]),
  );
  return `${parts.year}-${parts.month}-${parts.day}`;
}

/**
 * "5:00 pm" / "6 p.m. CT" / "12:30 PM" -> "17:00" / "18:00" / "12:30", or null.
 *
 * The schedule writes times a dozen ways and leaves most of them blank until
 * the week of the game, so anything unrecognised means "no time published"
 * rather than an exception. Mirrors parse_start() in scripts/ics_feed.py.
 */
export function parseFirstPitch(text) {
  if (!text) return null;
  const m = String(text).trim().toLowerCase().replace(/\./g, '')
    .match(/\b(\d{1,2})(?::(\d{2}))?\s*([ap])m?\b/);
  if (!m) return null;
  let hour = Number(m[1]);
  const minute = Number(m[2] ?? 0);
  if (!(hour >= 1 && hour <= 12) || !(minute >= 0 && minute <= 59)) return null;
  if (m[3] === 'p' && hour !== 12) hour += 12;
  if (m[3] === 'a' && hour === 12) hour = 0;
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
}

const played = (g) => g.teamScore != null && g.opponentScore != null;

/**
 * Fall games are exhibitions and the NCAA's D1 scoreboard does not carry them,
 * so there is nothing for a watcher to wait on. This is a season, not a
 * suffix on an opponent's name as it is in basketball.
 */
export const isExhibition = (g) => String(g.season || '').startsWith('Fall');

/**
 * Whether a scrape at `now` should start a watcher: KU plays today (Central),
 * at least one of the day's games has no result yet, they are not fall
 * exhibitions, and first pitch is within the window. A game already under way
 * still counts — a late cron delivery should not lose it.
 */
export function plan(games, now) {
  const today = centralDate(now);
  const todays = games.filter((g) => g.date === today && !played(g) && !isExhibition(g));
  if (!todays.length) {
    const fall = games.some((g) => g.date === today && isExhibition(g));
    return {
      watch: false,
      reason: fall
        ? `${today} is fall exhibition play; the D1 scoreboard does not carry it`
        : `no unplayed KU game on ${today}`,
    };
  }
  // The earliest first pitch of the day decides when to begin; a doubleheader
  // is watched as one stretch rather than as two separate jobs.
  const starts = todays.map((g) => centralToUtc(today, parseFirstPitch(g.startTime) || DEFAULT_TIME));
  const start = Math.min(...starts);
  if (start - now > PLAN_WINDOW) {
    return {
      watch: false,
      reason: `first pitch ${new Date(start).toISOString()} is more than ${PLAN_WINDOW / HOUR}h away`,
    };
  }
  if (now - start > giveUpAfter(todays.length)) {
    return { watch: false, reason: 'the games started too long ago to watch' };
  }
  return { watch: true, date: today, games: todays, start };
}

export const giveUpAfter = (count) =>
  count > 1 ? GIVE_UP_AFTER_DOUBLEHEADER : GIVE_UP_AFTER;

/**
 * Every KU game on a scoreboard response, in the order the feed lists them.
 *
 * Plural on purpose: the basketball version returns the first match, which on
 * a doubleheader date is whichever game the feed happens to put first — and
 * acting on its final would scrape while the other game was still being
 * played.
 */
export function kuGames(scoreboard) {
  const out = [];
  for (const wrap of scoreboard?.games ?? []) {
    const g = wrap.game || wrap;
    if ([g.home, g.away].some((s) => s?.names?.seo === TEAM_SEO)) out.push(g);
  }
  return out;
}

/**
 * Whether the day is done: every KU game the scoreboard knows about is final,
 * and there are at least as many of them as the schedule expects.
 *
 * The count guard is what makes a doubleheader safe. The scoreboard publishes
 * game two only once it exists, so early in the afternoon it shows one KU game
 * — and "every game I can see is final" would be true the moment game one
 * ended, which is precisely the wrong moment to scrape.
 */
export function allFinal(found, expected) {
  if (!found.length) return false;
  if (found.length < expected) return false;
  return found.every((g) => g.gameState === 'final');
}

const sleep = (ms) => new Promise((r) => setTimeout(r, Math.max(0, ms)));
const stamp = () => new Date().toISOString().slice(11, 16) + 'Z';
/** The branch this run is on; asks git when not running under Actions. */
const ref = () => process.env.GITHUB_REF_NAME
  || execFileSync('git', ['rev-parse', '--abbrev-ref', 'HEAD'], { encoding: 'utf8' }).trim();

function loadGames() {
  return JSON.parse(readFileSync('app/src/main/assets/seed.json', 'utf8')).games ?? [];
}

async function watch(date) {
  const budgetEnd = Date.now() + RUN_BUDGET;
  const todays = loadGames().filter((g) => g.date === date && !isExhibition(g));
  if (!todays.length) return console.log(`no KU game to watch on ${date}`);
  const pending = todays.filter((g) => !played(g));
  if (!pending.length) {
    return console.log(`${date} already has every result; nothing to do`);
  }
  const starts = todays.map((g) => centralToUtc(date, parseFirstPitch(g.startTime) || DEFAULT_TIME));
  const start = Math.min(...starts);
  const deadline = start + giveUpAfter(todays.length);
  const untimed = todays.filter((g) => !parseFirstPitch(g.startTime)).length;
  console.log(
    `watching ${todays.length} game(s) on ${date} vs ${todays.map((g) => g.opponent).join(', ')}: ` +
    `first pitch ${new Date(start).toISOString()}` +
    (untimed ? ` (${untimed} with no published time; assumed ${DEFAULT_TIME} CT)` : ''),
  );
  await sleep(Math.min(start + FIRST_POLL_AFTER, budgetEnd) - Date.now());

  const [y, mo, d] = date.split('-');
  while (Date.now() < deadline) {
    if (Date.now() >= budgetEnd) return handOver(date);
    try {
      const resp = await fetch(`${API}/scoreboard/softball/d1/${y}/${mo}/${d}`,
        { headers: { accept: 'application/json' } });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const found = kuGames(await resp.json());
      console.log(`${stamp()} ${found.length}/${todays.length} on the scoreboard` +
        found.map((g) => ` · ${g.away?.names?.short} ${g.away?.score ?? ''} at ` +
          `${g.home?.names?.short} ${g.home?.score ?? ''} (${g.gameState})`).join(''));
      if (allFinal(found, todays.length)) {
        // The decisions are written after the box score is; give them time.
        await sleep(SETTLE);
        dispatchScrape();
        return;
      }
    } catch (e) {
      console.log(`${stamp()} scoreboard: ${e.message}`);
    }
    await sleep(POLL_EVERY);
  }
  console.log('gave up waiting; the scheduled scrape will pick the results up');
}

function handOver(date) {
  console.log('run budget spent before the final; handing over to a fresh watcher');
  // Queues behind this run in the per-date concurrency group, then starts.
  execFileSync('gh', ['workflow', 'run', 'game-night.yml', '--repo', process.env.GITHUB_REPOSITORY,
    '--ref', ref(), '-f', `date=${date}`], { stdio: 'inherit' });
}

function dispatchScrape() {
  const repo = process.env.GITHUB_REPOSITORY;
  console.log(`all final; dispatching scrape-data.yml on ${repo}`);
  execFileSync('gh', ['workflow', 'run', 'scrape-data.yml', '--repo', repo, '--ref', ref()],
    { stdio: 'inherit' });
}

async function main() {
  const [mode, arg] = process.argv.slice(2);
  if (mode === 'plan') {
    const p = plan(loadGames(), Date.now());
    console.log(p.watch
      ? `game day: ${p.games.map((g) => g.opponent).join(', ')} from ${new Date(p.start).toISOString()}`
      : `no watch: ${p.reason}`);
    if (process.env.GITHUB_OUTPUT) {
      appendFileSync(process.env.GITHUB_OUTPUT, `watch=${p.watch}\n` + (p.watch ? `date=${p.date}\n` : ''));
    }
  } else if (mode === 'watch' && /^\d{4}-\d{2}-\d{2}$/.test(arg ?? '')) {
    await watch(arg);
  } else {
    console.error('usage: game-night-watch.mjs plan | watch YYYY-MM-DD');
    process.exit(2);
  }
}

// Only run when invoked as the script, not when imported by a test — and not
// at all when there is no script path (`node -e`, a REPL), which would throw.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await main();
