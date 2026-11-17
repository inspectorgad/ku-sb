@file:OptIn(ExperimentalTime::class)

package com.example.text

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// The clock, without java.util.Date, which iOS does not have.
//
// This file used to carry a SimpleDateFormat stand-in as well — a pattern
// engine for the letters y M d E H h m s a, with its own tokenizer, formatter
// and strict parser. It was written during the iPhone port on the assumption
// that the app formatted dates with patterns. It does not: dates are held and
// shown as "yyyy-MM-dd" text, and the web dashboard does its own formatting in
// JavaScript. The engine never gained a caller, so about 140 lines were
// compiled into both apps, never run, and never tested. It has been removed
// rather than left looking load-bearing; git history has it if a pattern is
// ever genuinely needed, and it would want a test against SimpleDateFormat
// before being wired to anything.

/** Milliseconds since 1970, now. */
fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()

/** Today's date where the phone is, "yyyy-MM-dd". */
fun todayIso(): String = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
