package io.github.monun.speculation.paper.util

import org.bukkit.FireworkEffect
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Firework
import kotlin.math.ceil

fun World.playFirework(location: Location, effect: FireworkEffect) {
    spawn(location, Firework::class.java).apply {
        fireworkMeta = fireworkMeta.apply {
            addEffect(effect)
            power = 0
        }
        detonate()
    }
}

fun trail(
    from: Location,
    to: Location,
    spacing: Double,
    action: (World, Double, Double, Double) -> Unit
) {
    require(spacing.isFinite() && spacing > 0.0) { "Trail spacing must be finite and positive" }
    val world = requireNotNull(from.world) { "Trail start must have a world" }
    require(to.world == world) { "Trail locations must be in the same world" }

    val delta = to.toVector().subtract(from.toVector())
    val steps = ceil(delta.length() / spacing).toInt().coerceAtLeast(1)
    for (step in 0..steps) {
        val point = from.clone().add(delta.clone().multiply(step.toDouble() / steps))
        action(world, point.x, point.y, point.z)
    }
}
