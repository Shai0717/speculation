package io.github.monun.speculation.paper

import io.github.monun.speculation.game.dialog.GameDialogTax
import io.github.monun.speculation.paper.util.playSound
import kotlinx.coroutines.delay
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.time.Duration
import java.util.logging.Level
import kotlin.math.roundToInt

class Dialog(val piece: PaperPiece) {
    private var message: (() -> Component)? = null
    private var actionMessage: (() -> Component)? = null

    private var terminal: ((String) -> Unit)? = null
    private val buttons = arrayListOf<Button>()

    var timeout: Timeout? = null
        private set

    var isDisposed = false
        private set

    fun message(message: () -> Component) {
        if (isDisposed) return
        this.message = message
    }

    fun actionMessage(actionMessage: () -> Component) {
        if (isDisposed) return
        this.actionMessage = actionMessage
    }

    fun terminal(terminal: (String) -> Unit) {
        this.terminal = terminal
    }

    fun button(box: BoundingBox, init: Button.() -> Unit) {
        if (isDisposed) return

        Button(box).apply(init).also {
            buttons += it
        }
    }

    fun timeout(message: Component, duration: Long, action: (Dialog) -> Unit) {
        if (isDisposed) return
        this.timeout = Timeout(piece, message, duration, action).also {
            it.showProgressBar(Bukkit.getOnlinePlayers())
        }
    }

    private fun findButton(start: Vector, direction: Vector, maxDistance: Double = 96.0): Button? {
        var found: Button? = null
        var foundDistance = 0.0

        for (button in buttons.toList()) {
            if (button.isDisposed) continue

            button.box.rayTrace(start, direction, maxDistance)?.let { result ->
                val distance = start.distance(result.hitPosition)

                if (found == null || distance < foundDistance) {
                    found = button
                    foundDistance = distance
                }
            }
        }

        return found
    }

    fun interact(player: Player, start: Vector, direction: Vector, maxDistance: Double = 96.0) {
        if (isDisposed) return

        findButton(start, direction, maxDistance)?.let { button ->
            button.invoke(player)
            buttons.removeIf { it.isDisposed }
        }
    }

    fun interact(text: String) {
        terminal?.invoke(text)
    }

    fun dispose() {
        if (isDisposed) return

        isDisposed = true
        buttons.onEach { it.dispose() }.clear()
        timeout?.run { hideProgressBar() }

        piece.player?.let {
            it.clearTitle()
            it.sendActionBar(Component.empty())
        }
    }

    fun onUpdate() {
        piece.player?.let { player ->
            message?.invoke()?.let { message ->
                player.showTitle(
                    Title.title(
                        Component.empty(),
                        message,
                        Title.Times.times(Duration.ofMillis(0), Duration.ofSeconds(1), Duration.ofMillis(100))
                    )
                )
            }

            val text = Component.text().color(NamedTextColor.WHITE)
            var isNotEmpty = false

            actionMessage?.invoke()?.let {
                text.append(it)
                isNotEmpty = true
            }

            val location = player.eyeLocation
            val start = location.toVector()
            val direction = location.direction

            findButton(start, direction)?.actionMessage?.invoke()?.let {
                if (isNotEmpty) text.append(Component.text(" / "))
                text.append(it)
            }

            player.sendActionBar(text.build())
        }
        timeout?.let {
            // 반환값: 유효할때
            if (!it.update()) {
                dispose()
            }
        }
    }

    inner class Button(val box: BoundingBox) {
        var isDisposed = false

        var actionMessage: (() -> Component)? = null
            private set
        private val displays = arrayListOf<TextDisplay>()
        private var onClick: ((Player, Dialog, Button) -> Unit)? = null

        fun actionMessage(actionMessage: () -> Component) {
            if (isDisposed) return
            this.actionMessage = actionMessage
        }

        fun display(location: Location, text: Component) {
            if (isDisposed) return

            val plugin = piece.process.plugin
            plugin.kolorDisplay.text(location, text).whenComplete { entity, exception ->
                if (exception != null) {
                    plugin.logger.log(Level.WARNING, "Could not create dialog text display", exception)
                } else if (isDisposed) {
                    plugin.kolorDisplay.remove(entity)
                } else {
                    displays += entity
                }
            }
        }

        fun onClick(onClick: (Player, Dialog, Button) -> Unit) {
            this.onClick = onClick
        }

        internal fun invoke(player: Player) {
            onClick?.invoke(player, this@Dialog, this)
        }

        fun dispose() {
            if (isDisposed) return

            isDisposed = true
            displays.forEach { piece.process.plugin.kolorDisplay.remove(it) }
            displays.clear()
        }
    }

    inner class Timeout(
        piece: PaperPiece,
        message: Component,
        duration: Long,
        val action: (Dialog) -> Unit
    ) {
        fun currentTime() = System.nanoTime()

        private val duration = duration * 1000000L
        private val time: Long = currentTime() + duration * 1000000L

        private val progressBar =
            BossBar.bossBar(
                piece.name.append(Component.text(": ")).append(message.colorIfAbsent(NamedTextColor.WHITE)),
                1.0F,
                piece.color.group.barColor,
                BossBar.Overlay.PROGRESS
            )

        fun showProgressBar(players: Collection<Player> = Bukkit.getOnlinePlayers()) {
            players.forEach { it.showBossBar(progressBar) }
        }

        fun hideProgressBar() {
            Bukkit.getOnlinePlayers().forEach { it.hideBossBar(progressBar) }
        }

        fun update(): Boolean {
            val currentTime = currentTime()

            return if (currentTime < time) {
                val remaining = time - currentTime
                val progress = (remaining.toDouble() / duration.toDouble()).toFloat()
                progressBar.progress(progress)
                true
            } else {
                // timeout
                action(this@Dialog)
                progressBar.progress(0.0F)
                false
            }
        }
    }


}
