package fr.koriamc.multiverse.config;

import org.bukkit.configuration.ConfigurationSection;

import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Réglages typés de config.json, relus à chaque rechargement.
 */
public final class Settings {

    public boolean debug;

    // Arrivée sur le serveur
    /** Monde où envoyer les joueurs à leur première connexion (vide = comportement de Minecraft). */
    public String firstWorld;
    /** Renvoie les joueurs au spawn de ce monde à chaque connexion. */
    public boolean alwaysFirstWorld;

    // Téléportation
    public boolean safeTeleport;
    public boolean welcomeMessage;
    /** Réapparition au spawn du monde de la mort plutôt que dans le monde principal. */
    public boolean respawnInWorld;
    public boolean portals;

    // Dimensions
    /** Les joueurs peuvent aller dans le Nether (portails, téléportations). */
    public boolean netherAllowed;
    /** Les joueurs peuvent aller dans l'End. */
    public boolean endAllowed;

    // Apparition des créatures
    /** Bloque les apparitions naturelles (seuls les spawners, oeufs, plugins... font apparaître des créatures). */
    public boolean blockNaturalSpawns;
    public Set<CreatureSpawnEvent.SpawnReason> blockedReasons = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
    /** Mondes où les créatures apparaissent normalement (noms en minuscules). */
    public Set<String> naturalSpawnWorlds = new HashSet<String>();

    // Portails personnalisés
    public boolean customPortals;
    public String wandMaterial;
    public int antiLoopSeconds;
    public boolean protectPortals;
    public int maxPortalVolume;

    // Monde vide
    public String voidSpawnBlock;
    public int voidSpawnHeight;

    // Titres
    public int titleFadeIn;
    public int titleStay;
    public int titleFadeOut;

    private final Map<String, String> sounds = new HashMap<String, String>();

    public void load(ConfigurationSection c, Logger logger) {
        debug = c.getBoolean("debug");
        firstWorld = str(c, "arrivee.premier-monde", "").trim();
        alwaysFirstWorld = c.getBoolean("arrivee.toujours-au-spawn");
        safeTeleport = c.getBoolean("teleportation.position-sure", true);
        welcomeMessage = c.getBoolean("teleportation.message-arrivee", true);
        respawnInWorld = c.getBoolean("teleportation.reapparition-dans-le-monde", true);
        portals = c.getBoolean("portails.actif", true);
        netherAllowed = c.getBoolean("dimensions.nether", false);
        endAllowed = c.getBoolean("dimensions.end", false);
        blockNaturalSpawns = c.getBoolean("creatures.bloquer-apparition-naturelle", true);
        blockedReasons = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        for (String name : c.getStringList("creatures.raisons-bloquees")) {
            try {
                blockedReasons.add(CreatureSpawnEvent.SpawnReason.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.warning("config.json : creatures.raisons-bloquees contient une raison inconnue (" + name + ").");
            }
        }
        naturalSpawnWorlds = new HashSet<String>();
        for (String world : c.getStringList("creatures.mondes-exceptes")) {
            naturalSpawnWorlds.add(world.trim().toLowerCase(Locale.ROOT));
        }
        customPortals = c.getBoolean("portails-personnalises.actif", true);
        wandMaterial = str(c, "portails-personnalises.baguette", "BLAZE_ROD").trim();
        antiLoopSeconds = Math.max(0, c.getInt("portails-personnalises.delai-anti-boucle-secondes", 2));
        protectPortals = c.getBoolean("portails-personnalises.proteger", true);
        maxPortalVolume = Math.max(1, c.getInt("portails-personnalises.volume-maximum", 4096));
        voidSpawnBlock = str(c, "monde-vide.bloc-spawn", "BEDROCK").trim();
        voidSpawnHeight = Math.max(1, Math.min(250, c.getInt("monde-vide.hauteur-spawn", 64)));
        titleFadeIn = c.getInt("titres.apparition", 5);
        titleStay = c.getInt("titres.duree", 40);
        titleFadeOut = c.getInt("titres.disparition", 10);
        sounds.clear();
        ConfigurationSection soundSection = c.getConfigurationSection("sons");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                sounds.put(key, soundSection.getString(key, ""));
            }
        }
    }

    /** Son de la section sons (chaîne vide si absent). */
    public String sound(String key) {
        String value = sounds.get(key);
        return value == null ? "" : value;
    }

    private static String str(ConfigurationSection c, String path, String def) {
        String value = c.getString(path);
        return value == null ? def : value;
    }
}
