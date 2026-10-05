// The roster parser, against a committed copy of the real page.
//
// This exists because the previous parser read fixed offsets from the jersey
// number and so could only ever find the position — the page had seven fields
// per player and the seed carried three for months. A fixture test is the only
// way to notice that from a machine that cannot reach kuathletics.com.
//
// Run with:  node --test scripts/*.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { parseRoster } from './roster-parser.mjs';

const PAGE = readFileSync(new URL('../scraped/roster-page.txt', import.meta.url), 'utf8');

test('every player on the real page is found', () => {
  const roster = parseRoster(PAGE);
  assert.equal(roster.length, 26);
  assert.ok(roster.every((p) => p.name && p.jerseyNumber && p.position));
});

test('the fields the page actually carries are kept', () => {
  const pitts = parseRoster(PAGE).find((p) => p.name === 'Tehya Pitts');
  assert.equal(pitts.jerseyNumber, '1');
  assert.equal(pitts.position, 'OF');
  assert.equal(pitts.academicYear, 'Sr.');
  assert.equal(pitts.height, "5' 4''");
  assert.equal(pitts.batsThrows, 'L/L');
  assert.equal(pitts.hometown, 'Corinth, Texas');
  assert.equal(pitts.lastSchool, 'McLennan Community College');
});

/**
 * The reason this is label-driven. Height is on 16 of the 26 and bats/throws
 * on 17, so a parser reading fixed offsets puts the hometown in the height
 * for everyone missing one.
 */
test('a player without a height does not take the next field as one', () => {
  const hensley = parseRoster(PAGE).find((p) => p.name === 'Timber Hensley');
  assert.equal(hensley.height, undefined);
  assert.equal(hensley.batsThrows, undefined);
  assert.equal(hensley.academicYear, 'So.');
  assert.equal(hensley.hometown, 'Durant, Okla.');
  assert.equal(hensley.lastSchool, 'Texas Tech');
});

test('coverage matches what the page holds', () => {
  const roster = parseRoster(PAGE);
  const count = (f) => roster.filter((p) => p[f]).length;
  assert.equal(count('academicYear'), 26);
  assert.equal(count('hometown'), 26);
  assert.equal(count('lastSchool'), 20);
  assert.equal(count('batsThrows'), 17);
  assert.equal(count('height'), 16);
});

test('bats/throws reads as two hands', () => {
  const values = new Set(parseRoster(PAGE).map((p) => p.batsThrows).filter(Boolean));
  assert.ok([...values].every((v) => /^[LRS]\/[LRS]$/.test(v)), [...values].join(' '));
});

test('page text that is not a roster yields nothing rather than throwing', () => {
  assert.deepEqual(parseRoster(''), []);
  assert.deepEqual(parseRoster(null), []);
  assert.deepEqual(parseRoster('Jersey Number\nnot-a-number\nnot a name'), []);
});
