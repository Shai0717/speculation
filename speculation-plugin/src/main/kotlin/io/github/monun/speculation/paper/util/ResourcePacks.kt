package io.github.monun.speculation.paper.util

import org.bukkit.entity.Player
import io.github.monun.speculation.plugin.SpeculationPlugin

fun Player.setSpeculationResourcePack(plugin: SpeculationPlugin): Boolean {
    val url = plugin.settings.resourcePackUrl.trim()
    val sha1 = plugin.settings.resourcePackSha1.trim()
    if (url.isEmpty() || !sha1.matches(Regex("(?i)[0-9a-f]{40}"))) return false

    setResourcePack(url, sha1, false)
    return true
}
