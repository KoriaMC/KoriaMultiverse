package fr.koriamc.multiverse.config;

import fr.koriamc.multiverse.model.WorldSettings;
import org.bukkit.World;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les mondes gérés de worlds.json, dans l'ordre du fichier. Les noms de monde sont
 * sensibles à la casse (ce sont des dossiers), la recherche ne l'est pas.
 */
public final class WorldCatalog {

    private final Map<String, WorldSettings> worlds = new LinkedHashMap<String, WorldSettings>();
    private final List<String> problems = new ArrayList<String>();

    public void load(Configuration file) {
        worlds.clear();
        problems.clear();
        for (String name : file.getKeys(false)) {
            ConfigurationSection s = file.getConfigurationSection(name);
            if (s == null) {
                problems.add("worlds.json : " + name + " doit être un bloc { ... }");
                continue;
            }
            worlds.put(name, WorldSettings.read(name, s, problems));
        }
    }

    /** @return les réglages du monde (nom exact, sinon sans tenir compte de la casse), ou null */
    public WorldSettings get(String name) {
        if (name == null) {
            return null;
        }
        WorldSettings exact = worlds.get(name);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, WorldSettings> entry : worlds.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public WorldSettings get(World world) {
        return world == null ? null : get(world.getName());
    }

    public boolean contains(String name) {
        return get(name) != null;
    }

    public Collection<WorldSettings> all() {
        return worlds.values();
    }

    public List<String> names() {
        return new ArrayList<String>(worlds.keySet());
    }

    public int count() {
        return worlds.size();
    }

    public List<String> getProblems() {
        return problems;
    }
}
