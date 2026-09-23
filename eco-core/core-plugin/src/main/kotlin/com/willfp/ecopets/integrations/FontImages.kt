package com.willfp.ecopets.integrations

import dev.lone.itemsadder.api.FontImages.FontImageWrapper
import org.bukkit.Bukkit

object FontImages {
    fun replace(text: String): String {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            return text
        }
        return FontImageWrapper.replaceFontImages(text)
    }
}
