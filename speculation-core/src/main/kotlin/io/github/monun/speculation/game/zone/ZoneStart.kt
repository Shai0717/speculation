package io.github.monun.speculation.game.zone

import io.github.monun.speculation.game.Journey

class ZoneStart : Zone() {
    override suspend fun onPass(journey: Journey) {
        val piece = journey.piece

        piece.level++
        piece.deposit(board.game.rules.startPassReward, this)
    }

    override suspend fun onArrive(journey: Journey) {
        val piece = journey.piece

        piece.level++
        piece.deposit(board.game.rules.startArrivalReward, this)
    }
}