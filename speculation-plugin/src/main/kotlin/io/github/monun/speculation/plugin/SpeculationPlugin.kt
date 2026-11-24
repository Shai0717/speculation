package io.github.monun.speculation.plugin

import io.github.Shai0717.kolor.KolorPlugin
import io.github.Shai0717.kolor.KolorScheduler
import io.github.Shai0717.kolor.KolorDisplay
import io.github.Shai0717.kolor.kolorCommand
import io.github.monun.speculation.paper.KolorCoroutineDispatcher
import io.github.monun.speculation.paper.PaperGameProcess
import io.github.monun.speculation.paper.PieceColor
import io.github.monun.speculation.paper.util.setSpeculationResourcePack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SpeculationPlugin : KolorPlugin() {
    lateinit var settings: SpeculationSettings
        private set

    var process: PaperGameProcess? = null
        private set

    val kolorScheduler by lazy { KolorScheduler(this) }
    val kolorDisplay by lazy { KolorDisplay(this) }
    val kolorDispatcher by lazy { KolorCoroutineDispatcher(kolorScheduler) }

    override fun onEnable() {
        settings = SpeculationSettings.load(dataFolder, javaClass)

        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        for (color in PieceColor.values()) {
            val name = color.textColor.toString()
            val team = scoreboard.getTeam(name) ?: scoreboard.registerNewTeam(name)
            team.color(color.textColor)
            logger.info("Team ${team.name} generated")
        }

        kolorCommand("speculation") {
            requires { it.hasPermission(settings.commandPermission) }
            literal("start") {
                stringArg("world") {
                    stringArg("players") {
                        stringArg("teamMatch") {
                            executes { context ->
                                val sender = context.sender
                                val world = Bukkit.getWorld(context.string("world"))
                                val teamMatch = context.string("teamMatch").toBooleanStrictOrNull()
                                val players = try {
                                    resolvePlayers(sender, context.string("players"))
                                } catch (exception: IllegalArgumentException) {
                                    sender.sendMessage(
                                        Component.text(exception.message ?: "잘못된 플레이어 선택자입니다.")
                                            .color(NamedTextColor.RED)
                                    )
                                    return@executes 0
                                }
                                if (world == null || teamMatch == null || players.isEmpty()) {
                                    sender.sendMessage(
                                        Component.text("월드, 플레이어 선택자 또는 팀 매치 값이 올바르지 않습니다.")
                                            .color(NamedTextColor.RED)
                                    )
                                    return@executes 0
                                }

                                kolorScheduler.runGlobal {
                                    try {
                                        startProcess(world, players, teamMatch)
                                    } catch (exception: IllegalStateException) {
                                        sender.sendMessage(
                                            Component.text(exception.message ?: "").color(NamedTextColor.RED)
                                        )
                                    }
                                }
                                1
                            }
                        }
                    }
                }
            }
            literal("stop") {
                executes { context ->
                    kolorScheduler.runGlobal {
                        if (process == null) {
                            context.sender.sendMessage(
                                Component.text("process is not running").color(NamedTextColor.RED)
                            )
                        } else {
                            stopProcess()
                        }
                    }
                    1
                }
            }
            literal("resourcepack") {
                executesOnEntity { player, _ ->
                    if (player.setSpeculationResourcePack(this@SpeculationPlugin)) {
                        player.sendMessage(Component.text("게임 리소스팩을 부여합니다. 화면에 표시된 \"예\" 버튼을 눌러주세요."))
                        player.sendMessage(Component.text("리소스팩 적용 화면이 표시되지 않았을 시에 서버 리소스팩 여부를 \"사용\" 혹은 \"물어보기\"로 설정해주세요."))
                    } else {
                        player.sendMessage(
                            Component.text("서버 관리자에게 리소스팩 URL과 SHA-1 설정을 요청하세요.")
                                .color(NamedTextColor.RED)
                        )
                    }
                }
            }
        }
    }

    override fun onKolorDisable() {
        if (process != null) stopProcess()
    }

    private fun resolvePlayers(sender: CommandSender, selector: String): Set<Player> =
        if (selector.startsWith("@")) {
            Bukkit.selectEntities(sender, selector).filterIsInstance<Player>().toSet()
        } else {
            listOfNotNull(Bukkit.getPlayerExact(selector)).toSet()
        }

    fun startProcess(world: World, players: Set<Player>, teamMatch: Boolean): PaperGameProcess {
        check(process == null) { "process already running" }

        return PaperGameProcess(this, world).apply {
            register(players, teamMatch)
        }.also {
            process = it
        }
    }

    fun stopProcess(): PaperGameProcess {
        val process = requireNotNull(process) { "process is not running" }
        process.unregister()
        this.process = null

        return process
    }
}
