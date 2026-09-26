package com.willfp.ecopets.integrations

import com.willfp.ecopets.pets.Pet
import com.willfp.ecopets.pets.Pets
import com.willfp.ecopets.pets.activePet
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.lang.reflect.Method

/**
 * Reads the active pets from WhitePetsAddon when it is installed.
 *
 * The service class is obtained from the Bukkit services manager rather than by name, so no
 * dependency has to be declared between the two plugins: WhitePetsAddon already depends on
 * EcoPets, and declaring the reverse would create a cycle.
 *
 * Without the addon every method falls back to the single active pet, which is the stock behaviour.
 */
object ActivePetsBridge {
    private const val SERVICE_NAME = "fr.brokeos.whitepetsaddon.pets.IActivePetsService"

    private var serviceClass: Class<*>? = null
    private var getActivePetIds: Method? = null
    private var getMaxActivePets: Method? = null
    private var removeExtra: Method? = null

    val isAvailable: Boolean
        get() = provider() != null

    fun getActivePets(player: Player): List<Pet> {
        val ids = activePetIds(player) ?: return listOfNotNull(player.activePet)
        return ids.mapNotNull { Pets.getByID(it) }
    }

    fun isActive(player: Player, pet: Pet): Boolean {
        val ids = activePetIds(player) ?: return player.activePet == pet
        return ids.contains(pet.id)
    }

    fun getSlotCount(player: Player): Int {
        val service = provider() ?: return 1
        val method = getMaxActivePets ?: return 1

        val value = runCatching { method.invoke(service, player) }.getOrNull() as? Int ?: return 1
        return value.coerceAtLeast(1)
    }

    fun deactivate(player: Player, pet: Pet) {
        if (player.activePet == pet) {
            player.activePet = null
            return
        }

        val service = provider() ?: return
        val method = removeExtra ?: return
        runCatching { method.invoke(service, player, pet.id) }
    }

    private fun activePetIds(player: Player): List<String>? {
        val service = provider() ?: return null
        val method = getActivePetIds ?: return null

        @Suppress("UNCHECKED_CAST")
        return runCatching { method.invoke(service, player) as? List<String> }.getOrNull()
    }

    private fun provider(): Any? {
        val clazz = resolveServiceClass() ?: return null

        @Suppress("UNCHECKED_CAST")
        val registration = Bukkit.getServicesManager().getRegistration(clazz as Class<Any>)

        // Reloading the addon re-registers the service under a class from a new classloader,
        // so a cached class that no longer resolves has to be dropped instead of failing silently.
        if (registration == null) {
            serviceClass = null
            return null
        }

        return registration.provider
    }

    private fun resolveServiceClass(): Class<*>? {
        serviceClass?.let { return it }

        val found = Bukkit.getServicesManager().knownServices
            .firstOrNull { it.name == SERVICE_NAME } ?: return null

        getActivePetIds = runCatching { found.getMethod("getActivePets", Player::class.java) }.getOrNull()
        getMaxActivePets = runCatching { found.getMethod("getMaxActivePets", Player::class.java) }.getOrNull()
        removeExtra = runCatching { found.getMethod("removeExtra", Player::class.java, String::class.java) }.getOrNull()

        serviceClass = found
        return found
    }
}
