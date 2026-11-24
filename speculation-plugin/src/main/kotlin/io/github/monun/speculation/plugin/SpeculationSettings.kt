package io.github.monun.speculation.plugin

import io.github.Shai0717.kolor.KolorConfig
import io.github.Shai0717.kolor.KolorSchema
import io.github.monun.speculation.game.GameRules
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.io.File
import java.io.IOException

data class PropertyVisual(
    val modelId: Int,
    val name: String,
    val landmarkName: String,
    val festivalName: String,
    val author: String
)

data class SpecialVisual(val modelId: Int, val name: String, val author: String)

class SpeculationSettings private constructor(
    val commandPermission: String,
    val startDelayMillis: Int,
    val rules: GameRules,
    val boardY: Double,
    val boardOriginX: Int,
    val boardOriginZ: Int,
    val boardTileSize: Int,
    val boardTileStep: Int,
    center: Vector,
    private val centerBoxSize: Vector,
    val propertyVisuals: List<PropertyVisual>,
    val specialVisuals: List<SpecialVisual>,
    val resourcePackUrl: String,
    val resourcePackSha1: String
) {
    private val configuredCenter = center.clone()
    val center: Vector
        get() = configuredCenter.clone()

    init {
        require(boardTileStep >= boardTileSize) { "board.tile-step must not be smaller than board.tile-size" }
    }

    val centerBox: BoundingBox
        get() = BoundingBox.of(center, centerBoxSize.x, centerBoxSize.y, centerBoxSize.z)

    companion object {
        fun load(dataFolder: File, resourceOwner: Class<*>): SpeculationSettings {
            val configFile = File(dataFolder, "config.yml")
            val yaml = KolorConfig.load(configFile, resourceOwner, "config.yml")
            yaml.options().copyDefaults(true)
            try {
                yaml.save(configFile)
            } catch (exception: IOException) {
                throw IllegalStateException("Cannot save configuration: $configFile", exception)
            }
            val schema = KolorSchema(yaml)
            val rulesSchema = schema.section("game")
            val boardSchema = schema.section("board")
            val center = Vector(
                boardSchema.double("center.x"),
                boardSchema.double("center.y"),
                boardSchema.double("center.z")
            )
            val centerBox = Vector(
                boardSchema.double("center-box.width", range = Double.MIN_VALUE..Double.MAX_VALUE),
                boardSchema.double("center-box.height", range = Double.MIN_VALUE..Double.MAX_VALUE),
                boardSchema.double("center-box.depth", range = Double.MIN_VALUE..Double.MAX_VALUE)
            )
            val baseTolls = rulesSchema.read("property-base-tolls", emptyList()) { parseIntList("game.property-base-tolls", it) }
            val visuals = schema.read("board.properties", emptyList()) { parsePropertyVisuals(it) }
            val specials = schema.read("board.specials", emptyList()) { parseSpecialVisuals(it) }

            val rules = GameRules(
                startingBalance = rulesSchema.int("starting-balance", range = 0..Int.MAX_VALUE),
                defaultDiceCount = rulesSchema.int("default-dice-count", range = 1..16),
                startPassReward = rulesSchema.int("start-pass-reward", range = 0..Int.MAX_VALUE),
                startArrivalReward = rulesSchema.int("start-arrival-reward", range = 0..Int.MAX_VALUE),
                gambleBaseBet = rulesSchema.int("gamble-base-bet", range = 0..Int.MAX_VALUE),
                gambleBetIncrement = rulesSchema.int("gamble-bet-increment", range = 0..Int.MAX_VALUE),
                gambleDiceCount = rulesSchema.int("gamble-dice-count", range = 1..16),
                jailTurns = rulesSchema.int("jail-turns", range = 0..100),
                moonwalkDiceCount = rulesSchema.int("moonwalk-dice-count", range = 1..16),
                pickpocketDiceCount = rulesSchema.int("pickpocket-dice-count", range = 1..16),
                singleDiceCount = rulesSchema.int("single-dice-count", range = 1..16),
                tripleDiceCount = rulesSchema.int("triple-dice-count", range = 1..16),
                quadrupleDiceCount = rulesSchema.int("quadruple-dice-count", range = 1..16),
                propertyBaseTolls = baseTolls
            )
            require(visuals.size == 23) { "Configuration 'board.properties' must contain 23 entries" }
            require(specials.size == 9) { "Configuration 'board.specials' must contain 9 entries" }

            return SpeculationSettings(
                commandPermission = schema.string("command.permission"),
                startDelayMillis = rulesSchema.int("start-delay-ms", range = 0..3_600_000),
                rules = rules,
                boardY = boardSchema.double("y"),
                boardOriginX = boardSchema.int("origin.x"),
                boardOriginZ = boardSchema.int("origin.z"),
                boardTileSize = boardSchema.int("tile-size", range = 1..64),
                boardTileStep = boardSchema.int("tile-step", range = 1..128),
                center = center,
                centerBoxSize = centerBox,
                propertyVisuals = visuals,
                specialVisuals = specials,
                resourcePackUrl = schema.string("resource-pack.url"),
                resourcePackSha1 = schema.string("resource-pack.sha1")
            )
        }

        private fun parseIntList(path: String, raw: Any): List<Int> =
            (raw as? List<*>)?.mapIndexed { index, value ->
                val number = value as? Number
                    ?: throw IllegalArgumentException("Configuration '$path[$index]' must be an integer")
                val converted = number.toInt()
                require(number.toDouble() == converted.toDouble()) {
                    "Configuration '$path[$index]' must be an integer"
                }
                converted
            } ?: throw IllegalArgumentException("Configuration '$path' must be a list of integers")

        private fun parsePropertyVisuals(raw: Any): List<PropertyVisual> =
            (raw as? List<*>)?.mapIndexed { index, value ->
                val entry = value as? Map<*, *>
                    ?: throw IllegalArgumentException("Configuration 'board.properties[$index]' must be a map")
                PropertyVisual(
                    modelId = mapInt(entry, "model-id", "board.properties[$index]"),
                    name = mapString(entry, "name", "board.properties[$index]"),
                    landmarkName = mapString(entry, "landmark", "board.properties[$index]"),
                    festivalName = mapString(entry, "festival", "board.properties[$index]"),
                    author = mapString(entry, "author", "board.properties[$index]")
                )
            } ?: throw IllegalArgumentException("Configuration 'board.properties' must be a list")

        private fun parseSpecialVisuals(raw: Any): List<SpecialVisual> =
            (raw as? List<*>)?.mapIndexed { index, value ->
                val entry = value as? Map<*, *>
                    ?: throw IllegalArgumentException("Configuration 'board.specials[$index]' must be a map")
                SpecialVisual(
                    modelId = mapInt(entry, "model-id", "board.specials[$index]"),
                    name = mapString(entry, "name", "board.specials[$index]"),
                    author = mapString(entry, "author", "board.specials[$index]")
                )
            } ?: throw IllegalArgumentException("Configuration 'board.specials' must be a list")

        private fun mapString(entry: Map<*, *>, key: String, path: String): String =
            entry[key] as? String
                ?: throw IllegalArgumentException("Configuration '$path.$key' must be a string")

        private fun mapInt(entry: Map<*, *>, key: String, path: String): Int {
            val number = entry[key] as? Number
                ?: throw IllegalArgumentException("Configuration '$path.$key' must be an integer")
            val converted = number.toInt()
            require(number.toDouble() == converted.toDouble() && converted >= 0) {
                "Configuration '$path.$key' must be a non-negative integer"
            }
            return converted
        }
    }
}
