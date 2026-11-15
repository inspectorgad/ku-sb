package com.example.data

/**
 * Folding two spellings of one player back into one player.
 *
 * Box scores abbreviate: the same player is "Presley Limbaugh" in one game
 * and "P. Limbaugh" in the next. The seed builder resolves that by matching
 * the roster, which works right up until the player leaves — and eight of
 * 2026's players were gone from the roster by the following October, so the
 * app carried them as sixteen people with one season divided between them.
 *
 * The seed is fixed now, but that does not reach a phone that has already
 * synced: [Seeder] skips any game that already has stat lines, so the old
 * split survives every later sync. Hence this, which repairs what is already
 * on the device.
 *
 * It runs on every sync and is a no-op once there is nothing left to fold,
 * which also means a recurrence is repaired rather than needing this again.
 */

/** "P." is an initial; "Presley" is a name. */
private fun isInitial(first: String) = first.trimEnd('.').length <= 1

private fun surname(name: String) = name.trim().split(" ").last().lowercase()
private fun forename(name: String) = name.trim().split(" ").first()

/**
 * Which player rows are the same person, as variant id -> the id to keep.
 *
 * The evidence is what both rows carry anyway: the same surname, the same
 * first initial and the same jersey number, with exactly one spelling giving
 * a real first name rather than an initial — so there is one obvious name to
 * keep and no guess about which of two real first names was meant.
 *
 * Deliberately strict. Chloe and Blakely Barber share a surname and must not
 * fold; they differ by jersey and by initial, and either alone is enough to
 * keep them apart. A blank jersey is not evidence either, since two unnumbered
 * players sharing a surname say nothing about each other.
 *
 * Mirrors fold_variant_names in scripts/seed_helpers.py. The two have to agree
 * or the app and the seed would disagree about who played.
 */
fun duplicatePlayerFolds(players: List<Player>): Map<Long, Long> {
    val groups = players
        .filter { it.name.trim().contains(" ") && it.jerseyNumber.isNotBlank() }
        .groupBy {
            Triple(surname(it.name), forename(it.name).take(1).lowercase(), it.jerseyNumber.trim())
        }

    val folds = mutableMapOf<Long, Long>()
    for ((_, group) in groups) {
        if (group.size < 2) continue
        val named = group.filter { !isInitial(forename(it.name)) }
        // Two real first names on one jersey are two players, or a mistake
        // worth leaving visible. Either way, not something to merge.
        if (named.size != 1) continue
        val keep = named.first()
        for (p in group) if (p.id != keep.id) folds[p.id] = keep.id
    }
    return folds
}

/** What a fold did, so a sync can say so rather than changing things silently. */
data class FoldResult(
    val playersRemoved: Int = 0,
    val linesMoved: Int = 0,
    /** Lines dropped because the player kept already had that game. */
    val linesDropped: Int = 0,
    val names: List<String> = emptyList()
) {
    val changed: Boolean get() = playersRemoved > 0
}

/**
 * Applies [duplicatePlayerFolds] to the database.
 *
 * Stat lines move to the player being kept before her duplicate is deleted,
 * because deleting a player cascades to her lines. Where both rows somehow
 * hold a line for the same game, the kept player's own line stands and the
 * duplicate's is counted in [FoldResult.linesDropped] rather than silently
 * replacing it — the unique index on (playerId, gameId) means one of the two
 * has to go, and overwriting the row that was already right is the worse
 * choice.
 */
suspend fun foldDuplicatePlayers(dao: JayhawksDao): FoldResult {
    val players = dao.playersOnce()
    val folds = duplicatePlayerFolds(players)
    if (folds.isEmpty()) return FoldResult()

    val byId = players.associateBy { it.id }
    val lines = dao.statLinesOnce()
    // Games the kept player already has a line for, so a move cannot collide.
    val taken = lines
        .filter { it.playerId !in folds.keys }
        .map { it.playerId to it.gameId }
        .toHashSet()

    var moved = 0
    var dropped = 0
    for (line in lines) {
        val keepId = folds[line.playerId] ?: continue
        if ((keepId to line.gameId) in taken) {
            dropped++
            continue
        }
        dao.upsertStatLine(line.copy(playerId = keepId))
        taken.add(keepId to line.gameId)
        moved++
    }

    val names = mutableListOf<String>()
    for ((variantId, keepId) in folds) {
        val variant = byId[variantId] ?: continue
        val keep = byId[keepId] ?: continue
        // The duplicate can be the row that happens to carry a detail the
        // kept one is missing, so fill blanks from her before she goes.
        val merged = keep.copy(
            position = keep.position.ifBlank { variant.position },
            academicYear = keep.academicYear.ifBlank { variant.academicYear },
            height = keep.height.ifBlank { variant.height },
            batsThrows = keep.batsThrows.ifBlank { variant.batsThrows },
            hometown = keep.hometown.ifBlank { variant.hometown },
            lastSchool = keep.lastSchool.ifBlank { variant.lastSchool }
        )
        if (merged != keep) dao.updatePlayer(merged)
        dao.deletePlayer(variant)
        names += "${variant.name} -> ${keep.name}"
    }

    return FoldResult(folds.size, moved, dropped, names.sorted())
}
