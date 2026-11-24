package io.github.monun.speculation.paper

import io.github.Shai0717.kolor.KolorTask
import io.github.monun.speculation.game.Game
import io.github.monun.speculation.game.zone.ZoneFestival
import io.github.monun.speculation.paper.util.setSpeculationResourcePack
import io.github.monun.speculation.plugin.SpeculationPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.block.BlockFace
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.CompletableFuture

class PaperGameProcess(
    val plugin: SpeculationPlugin,
    val world: World
) {
    val settings = plugin.settings

    val centerLocation: Location = settings.center.toLocation(world)
        get() = field.clone()

    private var state = 0

    lateinit var game: Game
        private set

    lateinit var zoneFestival: PaperZoneFestival

    lateinit var entityRegistry: GameEntityRegistry
        private set

    lateinit var scope: CoroutineScope
        private set

    lateinit var pieceByIds: Map<UUID, PaperPiece>
    private lateinit var dices: MutableList<Dice>

    lateinit var dialogDispatcher: GameDialogDispatcher
    lateinit var eventListener: GameEventListener
    private lateinit var updateTask: KolorTask
    private lateinit var listener: PaperGameListener

    fun register(players: Set<Player>, teamMatch: Boolean) {
        check(state == 0) { "Invalid state $state" }
        state = 1

        game = Game(settings.rules)
        entityRegistry = GameEntityRegistry(plugin)
        scope = plugin.kolorDispatcher.newScope()
        listener = PaperGameListener(this).also { plugin.server.pluginManager.registerEvents(it, plugin) }
        dialogDispatcher = GameDialogDispatcher().apply { register(this@PaperGameProcess) }
        eventListener = GameEventListener(this)
        updateTask = plugin.kolorScheduler.runGlobal(periodTicks = 1) { onUpdate() }
        dices = arrayListOf()

        initializeZones()
        initializeZoneProperties()
        initializeZoneSpecials()

        registerPlayers(players)
        if (teamMatch) registerTeams()

        state = 2

        scope.launch {
            Bukkit.broadcast(Component.text("리소스팩 적용중..."))
            delay(settings.startDelayMillis.toLong())

            game.launch(scope)
        }

        Bukkit.getOnlinePlayers().forEach {
            if (!it.hasResourcePack() && it.setSpeculationResourcePack(plugin)) {
                it.sendMessage(Component.text("게임 리소스팩을 부여합니다. 화면에 표시된 \"예\" 버튼을 눌러주세요."))
                it.sendMessage(Component.text("리소스팩 적용 화면이 표시되지 않았을 시에 서버 리소스팩 여부를 \"사용\" 혹은 \"물어보기\"로 설정해주세요."))
            }
        }
    }

    private fun initializeZones() {
        val zones = game.board.zones
        val faces = Face.values()

        var x = settings.boardOriginX
        val y = settings.boardY
        var z = settings.boardOriginZ

        zones.forEachIndexed { index, zone ->
            val line = index / 8
            val number = index % 8
            val innerFace = faces[line]
            val outerFace = innerFace.rotate(2)
            val forwardFace = innerFace.rotate(-1)
            val reverseFace = innerFace.rotate(1)

            val width = settings.boardTileSize

            var box = BoundingBox(
                x.toDouble(), y - 1.0, z.toDouble(),
                (x + width).toDouble(), y, (z + width).toDouble()
            ).expandDirectional(
                -innerFace.modX.toDouble(),
                0.0,
                -innerFace.modZ.toDouble()
            )
            if (number == 0) {
                box = box.expandDirectional(
                    -forwardFace.modX.toDouble(),
                    0.0,
                    -forwardFace.modZ.toDouble()
                )
            }

            zone.attach(PaperZone.of(zone).apply {
                this.process = this@PaperGameProcess
                this.box = box
                this.isCorner = number == 0
                this.forwardFace = forwardFace
                this.reverseFace = reverseFace
                this.innerFace = innerFace
                this.outerFace = outerFace
                this.rotation = when (innerFace) {
                    Face.SOUTH -> Rotation.NONE
                    Face.WEST -> Rotation.CLOCKWISE
                    Face.NORTH -> Rotation.FLIPPED
                    Face.EAST -> Rotation.COUNTER_CLOCKWISE
                }.let {
                    if (this.isCorner) it.rotateCounterClockwise()
                    else it
                }
            })

            x += forwardFace.modX * settings.boardTileStep
            z += forwardFace.modZ * settings.boardTileStep
        }

        zoneFestival = zones.find { it is ZoneFestival }!!.attachment()
    }

    private fun initializeZoneProperties() {
        val properties = game.board.zoneProperties.map { it.attachment<PaperZoneProperty>() }

        fun PaperZoneProperty.initResources(
            resourceModelId: Int,
            name: String,
            landmarkName: String,
            festivalName: String,
            author: String
        ) {
            this.landmarkModelId = resourceModelId
            this.author = author
            this.name = name
            this.landmarkName = landmarkName
            this.festivalName = festivalName
        }

        properties.zip(settings.propertyVisuals).forEach { (property, visual) ->
            property.initResources(
                visual.modelId,
                visual.name,
                visual.landmarkName,
                visual.festivalName,
                visual.author
            )
        }

        properties.forEach { property ->
            property.apply {
                val slotLocation = location.apply {
                    val face = outerFace
                    add(face.modX * 1.5, 0.0, face.modZ * 1.5)
                }
                val rot = rotation

                fun Location.spawnSlot() = world.spawn(this, ItemFrame::class.java).apply {
                    isVisible = false
                    rotation = rot
                    setFacingDirection(BlockFace.UP)
                }

                fun Location.spawnTag(text: Component): CompletableFuture<TextDisplay> =
                    plugin.kolorDisplay.text(this, text)

                slotCenter = slotLocation.spawnSlot()
                slotLeft =
                    slotLocation.clone().add(reverseFace.modX.toDouble(), 0.0, reverseFace.modZ.toDouble()).spawnSlot()
                slotRight =
                    slotLocation.clone().add(forwardFace.modX.toDouble(), 0.0, forwardFace.modZ.toDouble()).spawnSlot()

                val tagLocation = this.location.add(innerFace.modX * 1.5, 1.25, innerFace.modZ * 1.5)
                nameTag =
                    tagLocation.spawnTag(Component.text(name).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                tollsTagLocation = tagLocation.clone().apply { y -= 0.3 }
                tollsTag = tollsTagLocation.spawnTag(Component.text(0))
            }
        }
    }

    private fun initializeZoneSpecials() {
        val specials = game.board.zoneSpecials.map { it.attachment<PaperZoneSpecial>() }

        fun PaperZoneSpecial.initResources(modelId: Int, name: String, author: String) {
            this.name = name
            this.author = author
            this.modelId = modelId
        }

        specials.zip(settings.specialVisuals).forEach { (special, visual) ->
            special.initResources(visual.modelId, visual.name, visual.author)
        }

        specials.forEach { special ->
            special.apply {
                val rot = rotation
                val location = location.add(outerFace.modX * 1.5, 0.0, outerFace.modZ * 1.5)
                if (isCorner) location.add(reverseFace.modX * 1.5, 0.0, reverseFace.modZ * 1.5)

                slot = location.world.spawn(location, ItemFrame::class.java).apply {
                    isVisible = false
                    rotation = rot
                    setFacingDirection(BlockFace.DOWN)

                    val modelId = special.modelId

                    if (modelId > 0) {
                        setItem(ItemStack(Material.PAPER).apply {
                            editMeta { it.setCustomModelData(modelId) }
                        }, false)
                    }
                }
            }
        }
    }

    private fun registerPlayers(players: Set<Player>) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        val objective = "speculation".let { objectiveName ->
            scoreboard.getObjective(objectiveName)?.unregister()
            scoreboard.registerNewObjective(
                objectiveName,
                "dummy",
                Component.text("  SPECULATION  ").decorate(TextDecoration.BOLD).color(NamedTextColor.RED)
            ).apply {
                displaySlot = DisplaySlot.SIDEBAR
            }
        }

        val colors = HashSet<PieceColor>()
        val pieceByIds = hashMapOf<UUID, PaperPiece>()

        players.map { player ->
            game.board.newPiece(player.name).also { piece ->
                val team = scoreboard.getEntryTeam(player.name)
                val teamColor = team?.color()
                var pieceColor: PieceColor? = null

                if (teamColor is NamedTextColor) {
                    pieceColor = PieceColor.textColorOf(teamColor)
                }

                if (pieceColor == null) {
                    if (colors.isEmpty()) colors.addAll(PieceColor.values())

                    pieceColor = colors.random()
                    colors.remove(pieceColor)
                }

                PaperPiece(this, piece, pieceColor, player).apply {
                    val zone = piece.zone.attachment<PaperZone>()
                    bukkitScore = objective.getScore(player.name).apply { score = piece.balance }
                    stand = entityRegistry.spawn(zone.nextPieceLocation(), ArmorStand::class.java).apply {
                        setBasePlate(false)
                        setGravity(false)
                        customName(Component.text(player.name))
                        isCustomNameVisible = true
                        isSmall = true
                        setArms(true)

                        equipment.apply {
                            val color = pieceColor.color

                            fun ItemStack.leatherMeta() {
                                editMeta {
                                    val meta = it as LeatherArmorMeta
                                    meta.setColor(color)
                                }
                            }

                            setHelmet(ItemStack(Material.PLAYER_HEAD).apply {
                                editMeta {
                                    val meta = it as SkullMeta
                                    meta.playerProfile = player.playerProfile
                                }
                            })
                            setChestplate(ItemStack(Material.LEATHER_CHESTPLATE).apply { leatherMeta() })
                            setLeggings(ItemStack(Material.LEATHER_LEGGINGS).apply { leatherMeta() })
                            setBoots(ItemStack(Material.LEATHER_BOOTS).apply { leatherMeta() })
                        }
                    }
                }.also { paperPiece ->
                    piece.attach(paperPiece)
                    pieceByIds[player.uniqueId] = paperPiece
                }
            }
        }

        this.pieceByIds = pieceByIds.toMap()
    }

    private fun registerTeams() {
        val pieces: List<PaperPiece> = game.board.pieces.map { it.attachment() }
        val groups = pieces.groupBy { it.color.group }

        for ((group, piecesByGroup) in groups) {
            game.board.newTeam(group.name, piecesByGroup.map { it.piece }.toSet())
        }
    }

    fun spawnDice(location: Location, piece: PaperPiece, offset: Vector): Dice {
        checkState()
        return Dice(entityRegistry, location, settings.boardY).apply {
            owner = piece to offset
        }.also {
            dices += it
        }
    }

    fun clearDices() {
        dices.onEach { it.remove() }.clear()
    }

    fun piece(uniqueId: UUID) = pieceByIds[uniqueId]

    fun piece(player: Player) = piece(player.uniqueId)

    private fun onUpdate() {
        dialogDispatcher.currentDialog?.onUpdate()
        game.board.zones.map { it.attachment<PaperZone>() }.forEach { it.onUpdate() }
        dices.removeIf {
            it.onUpdate()
            !it.isValid
        }


//        game.board.zones.map { it.attachment<PaperZone>() }.forEach { zone ->
//            val box = zone.box
//            world.spawnParticle(Particle.REDSTONE, box.minX, box.minY + 1, box.minZ, 0, Particle.DustOptions(Color.RED, 1.0F))
//            world.spawnParticle(Particle.REDSTONE, box.maxX, box.maxY + 1, box.maxZ, 0, Particle.DustOptions(Color.AQUA, 1.0F))
//        }
    }

    fun unregister() {
        checkState()

        dialogDispatcher.disposeCurrentDialog()
        game.board.zones.map { it.attachment<PaperZone>() }.forEach { it.destroy() }
        entityRegistry.removeAll()
        HandlerList.unregisterAll(listener)
        updateTask.cancel()
        scope.cancel()
        state = 3
    }

    private fun checkState() {
        check(state != 3)
    }
}