package com.willfp.ecopets.integrations

import com.willfp.ecopets.plugin
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

object ItemsAdderLoadListener : Listener {
    @EventHandler
    fun onLoad(event: ItemsAdderLoadDataEvent) {
        plugin.reload()
    }
}
