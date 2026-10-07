package com.willfp.ecopets.pets

import com.willfp.eco.core.gui.addPageChanger
import com.willfp.eco.core.gui.menu.MenuBuilder
import com.willfp.eco.core.gui.menu.MenuLayer
import com.willfp.eco.core.gui.page.PageChanger
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.sound.AbstractPlayableSound
import com.willfp.ecopets.plugin
import org.bukkit.inventory.ItemStack

fun ItemStack.withModelData(path: String): ItemStack {
    val value = plugin.configYml.getIntOrNull("custom-model-data.$path") ?: return this
    if (value <= 0) return this

    val meta = itemMeta ?: return this
    meta.setCustomModelData(value)
    itemMeta = meta
    return this
}

fun MenuBuilder.addPageChangerWithModelData(
    basePath: String,
    modelPath: String,
    direction: PageChanger.Direction,
    sound: AbstractPlayableSound<*>?
): MenuBuilder {
    val config = plugin.configYml
    val active = config.getStringOrNull("$basePath.item")
        ?.let { Items.lookup(it).item.withModelData(modelPath) }
        ?: return this

    val inactive = config.getStringOrNull("$basePath.item-inactive")
        ?.let { Items.lookup(it).item.withModelData(modelPath) }

    val row = config.getIntOrNull("$basePath.location.row") ?: config.getInt("$basePath.row")
    val column = config.getIntOrNull("$basePath.location.column") ?: config.getInt("$basePath.column")

    return addPageChanger(direction, active, inactive, sound, row, column, MenuLayer.TOP)
}
