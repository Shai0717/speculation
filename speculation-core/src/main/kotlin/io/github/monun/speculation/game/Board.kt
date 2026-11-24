package io.github.monun.speculation.game

import io.github.monun.speculation.game.zone.*
import io.github.monun.speculation.ref.upstream

class Board(game: Game) {
    internal val game = upstream(game)

    val zoneStart = ZoneStart()
    val zoneGamble = ZoneGamble()
    val zoneJail = ZoneJail()
    val zoneFestival = ZoneFestival()
    val zonePortal = ZonePortal()
    val zoneNTS = ZoneNTS()
    val zoneMagicA = ZoneMagic()
    val zoneMagicB = ZoneMagic()
    val zoneMagicC = ZoneMagic()

    val zones = listOf(
        zoneStart,
        ZoneProperty(),
        ZoneProperty(),
        ZoneProperty(),

        zoneGamble,
        ZoneProperty(),
        ZoneProperty(),
        ZoneProperty(),


        zoneJail,
        ZoneProperty(),
        ZoneProperty(),
        ZoneProperty(),

        zoneMagicA,
        ZoneProperty(),
        ZoneProperty(),
        ZoneProperty(),


        zoneFestival,
        ZoneProperty(),
        ZoneProperty(),
        ZoneProperty(),

        zoneMagicB,
        ZoneProperty(),
        ZoneProperty(),
        ZoneProperty(),


        zonePortal,
        ZoneProperty(),
        ZoneProperty(),
        ZoneProperty(),

        zoneMagicC,
        ZoneProperty(),
        zoneNTS,
        ZoneProperty()
    ).also { list ->
        val count = list.count()
        list.forEachIndexed { index, zone ->
            zone.apply {
                board = this@Board
                previous = list[(count + index - 1) % count]
                next = list[(count + index + 1) % count]
            }
        }
    }

    val zoneProperties: List<ZoneProperty> = zones.filterIsInstance<ZoneProperty>().apply {
        forEachIndexed { index, property ->
            property.initLevels(game.rules.propertyBaseTolls[index])
        }
    }
    val zoneSpecials: List<Zone> = zones.filter { it !is ZoneProperty }

    private val _pieces = LinkedHashMap<String, Piece>()

    val pieces: List<Piece>
        get() = _pieces.values.toList()

    val survivors: List<Piece>
        get() = _pieces.values.filter { !it.isBankrupt }

    private val _teams = LinkedHashMap<String, PieceTeam>()

    fun newPiece(name: String): Piece {
        game.checkState(GameState.NEW)
        require(name !in _pieces) { "Already registered piece name '$name'" }

        return Piece(this, name, zones.first()).also {
            _pieces[name] = it
        }
    }

    fun newTeam(name: String, members: Set<Piece>): PieceTeam {
        game.checkState(GameState.NEW)
        require(name !in _teams) { "Already registered team name '$name'" }
        require(members.all { it.team == null }) { "Already piece has team" }

        return PieceTeam(name, members.toList()).also {
            _teams[name] = it

            for (member in members) {
                member.team = it
            }
        }
    }
}