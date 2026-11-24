package io.github.monun.speculation.paper

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

class GameEntityRegistry(private val plugin: Plugin) {
    private val entities = linkedSetOf<Entity>()

    fun <T : Entity> spawn(location: Location, type: Class<T>): T {
        check(plugin.isEnabled) { "Cannot spawn game entities while the plugin is disabled" }
        val world = requireNotNull(location.world) { "Entity location must have a world" }
        return world.spawn(location, type).apply {
            isPersistent = false
            entities += this
        }
    }

    fun removeAll() {
        entities.toList().forEach { entity ->
            if (entity.isValid) entity.remove()
        }
        entities.clear()
    }
}
