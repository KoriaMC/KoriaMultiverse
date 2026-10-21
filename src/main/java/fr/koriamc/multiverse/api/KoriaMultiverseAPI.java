package fr.koriamc.multiverse.api;

import fr.koriamc.multiverse.KoriaMultiverse;
import fr.koriamc.multiverse.model.WorldSettings;
import fr.koriamc.multiverse.world.WorldManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Accès pour les autres plugins KoriaMC (évènements, hubs, quêtes...).
 * <pre>
 * World event = KoriaMultiverseAPI.getWorld("event");
 * if (event != null) KoriaMultiverseAPI.teleport(player, event);
 * </pre>
 * Évènements disponibles dans fr.koriamc.multiverse.api.event.
 */
public final class KoriaMultiverseAPI {

    private KoriaMultiverseAPI() {
    }

    private static KoriaMultiverse plugin() {
        KoriaMultiverse plugin = KoriaMultiverse.get();
        if (plugin == null) {
            throw new IllegalStateException("KoriaMultiverse n'est pas activé");
        }
        return plugin;
    }

    public static boolean isAvailable() {
        return KoriaMultiverse.get() != null;
    }

    /** Noms des mondes gérés (chargés ou non). */
    public static List<String> getWorldNames() {
        return plugin().catalog().names();
    }

    /** @return les propriétés du monde, ou null s'il n'est pas géré */
    public static WorldSettings getSettings(String name) {
        return plugin().catalog().get(name);
    }

    /** @return le monde chargé (nom sans tenir compte de la casse), ou null */
    public static World getWorld(String name) {
        return plugin().worlds().findWorld(name);
    }

    /** Nom affiché du monde (alias coloré). */
    public static String getAlias(String name) {
        return plugin().worlds().alias(name);
    }

    /** Spawn du monde (position enregistrée avec /mv setspawn, sinon celle de Minecraft). */
    public static Location getSpawn(World world) {
        return plugin().worlds().spawn(world);
    }

    /** Position sûre la plus proche (pieds et tête libres, sol solide). */
    public static Location getSafeLocation(Location location) {
        return plugin().worlds().safe(location);
    }

    /** Téléporte au spawn du monde, avec les vérifications d'accès. @return false si refusé */
    public static boolean teleport(Player player, World world) {
        return plugin().worlds().teleport(player, world);
    }

    /**
     * Crée un monde et l'ajoute à worlds.json.
     *
     * @param seed      graine (vide = aléatoire)
     * @param generator "VIDE", "Plugin:id" ou vide
     */
    public static WorldManager.Result createWorld(String name, World.Environment environment, WorldType type, String seed,
                                                  String generator) {
        return plugin().worlds().create(name, environment, type, seed, generator, true);
    }

    public static WorldManager.Result loadWorld(String name) {
        return plugin().worlds().load(name);
    }

    public static WorldManager.Result unloadWorld(String name) {
        return plugin().worlds().unload(name, true);
    }
}
