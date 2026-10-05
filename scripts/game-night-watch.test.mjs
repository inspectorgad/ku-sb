// The watcher decides, from a clock and a schedule, whether to spend a job
// waiting on a game — and when to stop waiting. Both halves are easy to get
// subtly wrong: the season straddles the March clock change, the schedule
// dates are Central while everything in Actions is UTC, and softball plays
// two games in a day often enough that the single-game assumption behind the
// basketball version is wrong rather than merely incomplete.
//
// There is no live game to test against until February, so these are the only
// check this code gets before it runs for real.
//
// Run with:  node --test scripts/*.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  centralToUtc, centralDate, parseFirstPitch, plan, kuGames, allFinal,
  isExhibition, giveUpAfter, PLAN_WINDOW,
} from './game-night-watch.mjs';

const iso = (t) => new Date(t).toISOString();
const HOUR = 3600_000;

// A softball season opens in February on CST and ends in June on CDT, so both
// offsets fall inside one season and the change lands mid-conference-play.
test('Central wall time converts either side of the clock change', () => {
  assert.equal(iso(centralToUtc('2026-02-06', '18:00')), '2026-02-07T00:00:00.000Z'); // CST
  assert.equal(iso(centralToUtc('2026-03-07', '13:00')), '2026-03-07T19:00:00.000Z'); // CST
  assert.equal(iso(centralToUtc('2026-03-08', '13:00')), '2026-03-08T18:00:00.000Z'); // CDT
  assert.equal(iso(centralToUtc('2026-04-17', '18:00')), '2026-04-17T23:00:00.000Z'); // CDT
  assert.equal(iso(centralToUtc('2026-06-04', '11:00')), '2026-06-04T16:00:00.000Z'); // CDT
});

test('the date is Central, not UTC', () => {
  // 02:00 UTC on the 18th is still the evening of the 17th in Lawrence —
  // which is exactly when a night game goes final.
  assert.equal(centralDate(Date.parse('2026-04-18T02:00:00Z')), '2026-04-17');
  assert.equal(centralDate(Date.parse('2026-04-17T18:00:00Z')), '2026-04-17');
});

test('first pitch is parsed from the way the schedule writes it', () => {
  assert.equal(parseFirstPitch('5:00 pm'), '17:00');
  assert.equal(parseFirstPitch('12:00 pm'), '12:00');
  assert.equal(parseFirstPitch('2:30 pm'), '14:30');
  assert.equal(parseFirstPitch('6 p.m. CT'), '18:00');
  assert.equal(parseFirstPitch('11:00 am'), '11:00');
  assert.equal(parseFirstPitch('12:00 am'), '00:00');
  assert.equal(parseFirstPitch('1 PM'), '13:00');
  // Most of the schedule has no time until the week of the game.
  assert.equal(parseFirstPitch(''), null);
  assert.equal(parseFirstPitch(undefined), null);
  assert.equal(parseFirstPitch('TBA'), null);
  assert.equal(parseFirstPitch('noon'), null);
});

const game = (over) => ({
  date: '2026-04-17', opponent: 'UCF', season: '2026', site: 'H',
  startTime: '5:00 pm', teamScore: null, opponentScore: null, ...over,
});

test('a scrape plans a watch when first pitch is close enough', () => {
  const games = [game()];
  const firstPitch = centralToUtc('2026-04-17', '17:00');
  const p = plan(games, firstPitch - 2 * HOUR);
  assert.equal(p.watch, true);
  assert.equal(p.date, '2026-04-17');
  assert.equal(p.start, firstPitch);
});

test('a game further off than the window is left for a later scrape', () => {
  const firstPitch = centralToUtc('2026-04-17', '17:00');
  const p = plan([game()], firstPitch - PLAN_WINDOW - HOUR);
  assert.equal(p.watch, false);
  assert.match(p.reason, /more than/);
});

test('a game already under way is still worth watching', () => {
  const firstPitch = centralToUtc('2026-04-17', '17:00');
  const p = plan([game()], firstPitch + 30 * 60_000);
  assert.equal(p.watch, true);
});

test('a game long finished is not', () => {
  // A noon game, checked at seven in the evening — still the same Central
  // date, so this reaches the give-up branch rather than falling out earlier
  // as "no game today", which is what a later hour would have tested instead.
  const firstPitch = centralToUtc('2026-04-17', '12:00');
  const p = plan([game({ startTime: '12:00 pm' })], firstPitch + 7 * HOUR);
  assert.equal(p.watch, false);
  assert.match(p.reason, /too long ago/);
});

test('a game with a result is nothing to wait for', () => {
  const firstPitch = centralToUtc('2026-04-17', '17:00');
  const p = plan([game({ teamScore: 3, opponentScore: 6 })], firstPitch - HOUR);
  assert.equal(p.watch, false);
  assert.match(p.reason, /no unplayed/);
});

test('nothing on the schedule means nothing to watch', () => {
  const p = plan([], Date.parse('2026-04-17T20:00:00Z'));
  assert.equal(p.watch, false);
});

// --- fall ball ------------------------------------------------------------

test('a fall exhibition is never watched', () => {
  assert.equal(isExhibition({ season: 'Fall 2026' }), true);
  assert.equal(isExhibition({ season: '2026' }), false);
  const fall = game({ date: '2026-09-26', season: 'Fall 2026', opponent: 'Nebraska' });
  const firstPitch = centralToUtc('2026-09-26', '17:00');
  const p = plan([fall], firstPitch - HOUR);
  assert.equal(p.watch, false);
  // Said in as many words, because the reason is not "no game today" — there
  // is a game, the D1 scoreboard simply does not carry it.
  assert.match(p.reason, /fall exhibition/);
});

// --- doubleheaders --------------------------------------------------------

test('a doubleheader is watched as one stretch, from the earlier start', () => {
  const games = [
    game({ opponent: 'Baylor', startTime: '12:00 pm' }),
    game({ opponent: 'Baylor (G2)', startTime: '2:30 pm' }),
  ];
  const noon = centralToUtc('2026-04-17', '12:00');
  const p = plan(games, noon - HOUR);
  assert.equal(p.watch, true);
  assert.equal(p.games.length, 2);
  assert.equal(p.start, noon, 'starts from game one, not game two');
});

test('a doubleheader gets longer before giving up than a single game does', () => {
  assert.ok(giveUpAfter(2) > giveUpAfter(1));
  // Game two starts about two and a half hours after game one and takes two
  // more, so five hours from the first pitch is not enough.
  assert.ok(giveUpAfter(2) >= 6 * HOUR);
});

test('the second game of a doubleheader keeps the watch alive', () => {
  const games = [
    game({ opponent: 'Baylor', startTime: '12:00 pm', teamScore: 5, opponentScore: 1 }),
    game({ opponent: 'Baylor (G2)', startTime: '2:30 pm' }),
  ];
  const p = plan(games, centralToUtc('2026-04-17', '14:00'));
  assert.equal(p.watch, true, 'game one being final does not end the day');
  assert.equal(p.games.length, 1);
});

// --- reading the scoreboard ----------------------------------------------

const scoreboard = (...entries) => ({
  games: entries.map((g) => ({ game: g })),
});
const ku = (state, score = 4) => ({
  gameState: state,
  home: { names: { seo: 'kansas', short: 'Kansas' }, score: String(score) },
  away: { names: { seo: 'baylor', short: 'Baylor' }, score: '1' },
});
const other = { gameState: 'final', home: { names: { seo: 'texas' } }, away: { names: { seo: 'byu' } } };

test('every KU game on the scoreboard is found, not just the first', () => {
  const found = kuGames(scoreboard(ku('final'), other, ku('live')));
  assert.equal(found.length, 2);
  assert.equal(kuGames(scoreboard(other)).length, 0);
  assert.equal(kuGames(null).length, 0);
  assert.equal(kuGames({}).length, 0);
});

test('the day is done only when every expected game is final', () => {
  assert.equal(allFinal([ku('final')], 1), true);
  assert.equal(allFinal([ku('final'), ku('final')], 2), true);
  assert.equal(allFinal([ku('final'), ku('live')], 2), false);
  assert.equal(allFinal([], 1), false);
});

/**
 * The trap that makes a doubleheader dangerous. The scoreboard publishes game
 * two only once it exists, so in the early afternoon it lists one KU game —
 * and "everything I can see is final" is true the moment game one ends, which
 * is the worst possible moment to scrape.
 */
test('one final game does not end a doubleheader the scoreboard has not posted yet', () => {
  assert.equal(allFinal([ku('final')], 2), false);
});

test('a scoreboard listing more games than expected is still judged on their state', () => {
  // Defensive: if the feed ever splits a game, do not call the day done while
  // one of them is live.
  assert.equal(allFinal([ku('final'), ku('final'), ku('live')], 2), false);
  assert.equal(allFinal([ku('final'), ku('final'), ku('final')], 2), true);
});
