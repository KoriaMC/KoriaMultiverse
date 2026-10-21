package fr.koriamc.multiverse.config;

import fr.koriamc.multiverse.model.Portal;
import org.bukkit.Location;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Les portails de portals.json, indexés par monde pour retrouver vite celui où
 * un joueur entre.
 */
public final class PortalCatalog {

    private final Map<String, Portal> portals = new LinkedHashMap<String, Portal>();
    private final Map<String, List<Portal>> byWorld = new HashMap<String, List<Portal>>();
    private final List<String> problems = new ArrayList<String>();

    public void load(Configuration file) {
        portals.clear();
        byWorld.clear();
        problems.clear();
        for (String key : file.getKeys(false)) {
            ConfigurationSection s = file.getConfigurationSection(key);
            if (s == null) {
                problems.add("portals.json : " + key + " doit être un bloc { ... }");
                continue;
            }
            String name = key.toLowerCase(Locale.ROOT);
            Portal portal = Portal.read(name, s, problems);
            if (portal == null) {
                continue;
            }
            portals.put(name, portal);
            List<Portal> list = byWorld.get(portal.getWorldName());
            if (list == null) {
                list = new ArrayList<Portal>();
                byWorld.put(portal.getWorldName(), list);
            }
            list.add(portal);
        }
    }

    /** @return le portail, ou null */
    public Portal get(String name) {
        return name == null ? null : portals.get(name.toLowerCase(Locale.ROOT));
    }

    /** @return le portail dont la zone contient cette position, ou null */
    public Portal at(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        List<Portal> list = byWorld.get(location.getWorld().getName());
        if (list == null) {
            return null;
        }
        for (Portal portal : list) {
            if (portal.contains(location)) {
                return portal;
            }
        }
        return null;
    }

    public List<Portal> inWorld(String world) {
        List<Portal> list = byWorld.get(world);
        return list == null ? Collections.<Portal>emptyList() : list;
    }

    public Collection<Portal> all() {
        return portals.values();
    }

    public List<String> names() {
        return new ArrayList<String>(portals.keySet());
    }

    public int count() {
        return portals.size();
    }

    public List<String> getProblems() {
        return problems;
    }
}
