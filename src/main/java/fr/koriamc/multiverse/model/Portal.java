package fr.koriamc.multiverse.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Portail personnalisé de portals.json : une zone (pavé droit) qui téléporte le
 * joueur qui y entre vers sa destination.
 * <p>
 * Destinations : "monde:&lt;nom&gt;" (spawn du monde), "portail:&lt;nom&gt;" (sortie d'un
 * autre portail), "position:&lt;monde&gt;,x,y,z[,yaw,pitch]".
 */
public final class Portal {

    private final String name;
    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final String destination;
    private final double[] exit;
    private final String permission;
    private final double price;
    private final int cooldownSeconds;
    private final boolean enabled;
    private final String message;
    private final String fill;

    private Portal(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2, String destination,
                   double[] exit, String permission, double price, int cooldownSeconds, boolean enabled, String message,
                   String fill) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.destination = destination;
        this.exit = exit;
        this.permission = permission;
        this.price = price;
        this.cooldownSeconds = cooldownSeconds;
        this.enabled = enabled;
        this.message = message;
        this.fill = fill;
    }

    /**
     * Lit un portail de portals.json.
     *
     * @param problems liste alimentée avec les erreurs de configuration
     * @return le portail, ou null si sa zone est incomplète
     */
    public static Portal read(String name, ConfigurationSection s, List<String> problems) {
        String where = "portals.json : " + name;
        String world = s.getString("monde", "").trim();
        ConfigurationSection region = s.getConfigurationSection("region");
        if (world.isEmpty() || region == null) {
            problems.add(where + " : monde ou region manquant (sélection avec /mv portal baguette, puis /mv portal creer)");
            return null;
        }
        double[] exit = null;
        ConfigurationSection sortie = s.getConfigurationSection("sortie");
        if (sortie != null && sortie.contains("x") && sortie.contains("y") && sortie.contains("z")) {
            exit = new double[]{sortie.getDouble("x"), sortie.getDouble("y"), sortie.getDouble("z"),
                    sortie.getDouble("yaw"), sortie.getDouble("pitch")};
        }
        return new Portal(name, world, region.getInt("x1"), region.getInt("y1"), region.getInt("z1"),
                region.getInt("x2"), region.getInt("y2"), region.getInt("z2"),
                s.getString("destination", "").trim(), exit, s.getString("permission", "").trim(),
                Math.max(0, s.getDouble("prix", 0)), Math.max(0, s.getInt("delai-secondes", 0)),
                !s.contains("actif") || s.getBoolean("actif"), s.getString("message", ""),
                s.getString("remplissage", "").trim());
    }

    /** La position est-elle dans la zone du portail (coordonnées de bloc, bords inclus) ? */
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Point d'arrivée quand un autre portail mène ici : la sortie enregistrée, sinon devant la zone. */
    public Location getExit() {
        World w = Bukkit.getWorld(world);
        if (w == null) {
            return null;
        }
        if (exit != null) {
            return new Location(w, exit[0], exit[1], exit[2], (float) exit[3], (float) exit[4]);
        }
        // sans sortie : au pied de la zone, décalé d'un bloc sur le côté le plus fin
        double x = (minX + maxX) / 2.0 + 0.5;
        double z = (minZ + maxZ) / 2.0 + 0.5;
        if (maxX - minX <= maxZ - minZ) {
            x = maxX + 1.5;
        } else {
            z = maxZ + 1.5;
        }
        return new Location(w, x, minY, z);
    }

    public boolean hasExit() {
        return exit != null;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return world;
    }

    public World getWorld() {
        return Bukkit.getWorld(world);
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    /** Nombre de blocs de la zone. */
    public long getVolume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** Destination brute ("monde:lobby", "portail:retour", "position:world,0,64,0"). */
    public String getDestination() {
        return destination;
    }

    /** Permission requise (vide = libre). */
    public String getPermission() {
        return permission;
    }

    /** Prix d'utilisation via Vault (0 = gratuit). */
    public double getPrice() {
        return price;
    }

    /** Délai avant qu'un même joueur puisse réutiliser ce portail. */
    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Message envoyé au joueur à chaque passage (vide = message par défaut). */
    public String getMessage() {
        return message;
    }

    /** Matériau qui remplit la zone (PORTAL, ENDER_PORTAL, STATIONARY_WATER...), vide = aucun. */
    public String getFill() {
        return fill;
    }

    /** "world 10,64,5 / 12,67,5" */
    public String describeRegion() {
        return world + " " + minX + "," + minY + "," + minZ + " / " + maxX + "," + maxY + "," + maxZ;
    }
}
