package io.github.monun.speculation.game

data class GameRules(
    val startingBalance: Int = 400,
    val defaultDiceCount: Int = 2,
    val startPassReward: Int = 100,
    val startArrivalReward: Int = 200,
    val gambleBaseBet: Int = 50,
    val gambleBetIncrement: Int = 10,
    val gambleDiceCount: Int = 3,
    val jailTurns: Int = 3,
    val moonwalkDiceCount: Int = 2,
    val pickpocketDiceCount: Int = 1,
    val singleDiceCount: Int = 1,
    val tripleDiceCount: Int = 3,
    val quadrupleDiceCount: Int = 4,
    val propertyBaseTolls: List<Int> = listOf(
        10, 11, 12, 14, 16, 18, 20, 21, 22, 24, 26, 28,
        30, 31, 32, 34, 36, 38, 40, 42, 44, 47, 50
    )
) {
    init {
        require(startingBalance >= 0) { "startingBalance must be non-negative" }
        require(defaultDiceCount > 0) { "defaultDiceCount must be positive" }
        require(startPassReward >= 0 && startArrivalReward >= 0) { "Start rewards must be non-negative" }
        require(gambleBaseBet >= 0 && gambleBetIncrement >= 0) { "Gamble bets must be non-negative" }
        require(gambleDiceCount > 0) { "gambleDiceCount must be positive" }
        require(jailTurns >= 0) { "jailTurns must be non-negative" }
        require(
            moonwalkDiceCount > 0 && pickpocketDiceCount > 0 &&
                singleDiceCount > 0 && tripleDiceCount > 0 && quadrupleDiceCount > 0
        ) { "Magic dice counts must be positive" }
        require(propertyBaseTolls.size == 23 && propertyBaseTolls.all { it >= 0 }) {
            "propertyBaseTolls must contain 23 non-negative values"
        }
    }
}
