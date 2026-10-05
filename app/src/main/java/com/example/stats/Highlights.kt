package com.example.stats

import com.example.data.Game
import com.example.data.Player
import com.example.data.StatLine

/**
 * One notable fact about a season, in the form a person would say it out loud.
 *
 * [detail] carries the where and when, so the headline can stay short.
 */
data class Highlight(
    val title: String,
    val headline: String,
    val detail: String
)

/**
 * The facts worth knowing about a season, each one derived rather than
 * written down: biggest win, best day at the plate, the crowd record.
 *
 * Every entry is skipped when the season has nothing to say about it, so an
 * empty list is the correct answer in February rather than a screen of zeroes.
 */
fun seasonHighlights(
    games: List<Game>,
    players: List<Player>,
    lines: List<StatLine>
): List<Highlight> {
    val played = games.filter { it.teamScore != null && it.opponentScore != null }
    if (played.isEmpty()) return emptyList()

    val byId = players.associateBy { it.id }
    val gameById = played.associateBy { it.id }
    // Only lines belonging to a played game in this set, so a highlight can
    // never cite a game the caller did not ask about.
    val relevant = lines.mapNotNull { line ->
        gameById[line.gameId]?.let { it to line }
    }

    fun who(line: StatLine) = byId[line.playerId]?.name ?: "Unknown"
    fun where(game: Game) =
        "${if (game.site == "A") "at" else "vs"} ${opponentName(game.opponent)} · ${game.date}"

    val out = mutableListOf<Highlight>()

    played.maxByOrNull { it.teamScore ?: 0 }?.let { game ->
        out += Highlight(
            "Most runs scored",
            "${game.teamScore} runs",
            where(game)
        )
    }
    played.maxByOrNull { (it.teamScore ?: 0) - (it.opponentScore ?: 0) }
        ?.takeIf { (it.teamScore ?: 0) > (it.opponentScore ?: 0) }
        ?.let { game ->
            out += Highlight(
                "Biggest win",
                "${game.teamScore}-${game.opponentScore}",
                where(game)
            )
        }
    relevant.filter { it.second.hits > 0 }
        .maxWithOrNull(compareBy({ it.second.hits }, { it.second.runsBattedIn }))
        ?.let { (game, line) ->
            out += Highlight(
                "Most hits in a game",
                "${who(line)} — ${line.hits}-for-${line.atBats}",
                where(game)
            )
        }
    relevant.filter { it.second.runsBattedIn > 0 }
        .maxByOrNull { it.second.runsBattedIn }
        ?.let { (game, line) ->
            out += Highlight(
                "Most RBI in a game",
                "${who(line)} — ${line.runsBattedIn} RBI",
                where(game)
            )
        }
    relevant.filter { it.second.pitched && it.second.pitcherStrikeouts > 0 }
        .maxByOrNull { it.second.pitcherStrikeouts }
        ?.let { (game, line) ->
            out += Highlight(
                "Most strikeouts in a game",
                "${who(line)} — ${line.pitcherStrikeouts} K",
                where(game)
            )
        }
    played.filter { it.attendance > 0 }.maxByOrNull { it.attendance }?.let { game ->
        out += Highlight(
            "Biggest crowd",
            "%,d".format(game.attendance),
            where(game)
        )
    }
    longestStreak(played, wins = true).takeIf { it >= 2 }?.let {
        out += Highlight("Longest winning streak", "$it straight", "")
    }
    return out
}
