package fr.koriamc.multiverse.world;

import fr.koriamc.multiverse.KoriaMultiverse;
import fr.koriamc.multiverse.hook.VaultHook;
import fr.koriamc.multiverse.model.Portal;
import fr.koriamc.multiverse.util.Items;
import fr.koriamc.multiverse.util.MaterialSet;
import fr.koriamc.multiverse.util.Sounds;
import fr.koriamc.multiverse.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Portails personnalisés : sélection de la zone à la baguette, création,
 * remplissage, destinations et passage des joueurs.
 */
@SuppressWarnings("deprecation")
public final class PortalManager {

    /** Résultat de la création d'un portail. */
    public enum Result {
        OK,
        /** Nom refusé (lettres, chiffres, - et _). */
        NOM_INVALIDE,
        EXISTE_DEJA,
        /** Les deux coins ne sont pas sélectionnés. */
        SELECTION_INCOMPLETE,
        /** Les deux coins sont dans des mondes différents. */
        MONDES_DIFFERENTS,
        /** Zone plus grande que portails-personnalises.volume-maximum. */
        TROP_GRAND,
        /** Destination illisible. */
        DESTINATION_INVALIDE
    }

    /** Coins sélectionnés par un joueur. */
    public static final class Selection {
        public Location first;
        public Location second;

        public boolean isComplete() {
            return first != null && second != null;
        }
    }

    /** Matériaux qui remplissent un portail : leur physique est bloquée dans la zone. */
    public static final Material[] FILLS = {Material.PORTAL, Material.ENDER_PORTAL, Material.STATIONARY_WATER,
            Material.STATIONARY_LAVA, Material.WEB};

    private final KoriaMultiverse plugin;
    private final Map<UUID, Selection> selections = new HashMap<UUID, Selection>();
    private final Map<UUID, Long> lastUse = new HashMap<UUID, Long>();
    private final Map<String, Long> cooldowns = new HashMap<String, Long>();

    public PortalManager(KoriaMultiverse plugin) {
        this.plugin = plugin;
    }

    // ============================================================== Baguette

    /** Baguette de sélection : matériau de portails-personnalises.baguette, nommée. */
    public ItemStack wand() {
        ItemStack base = Items.parse(plugin.settings().wandMaterial);
        return Items.build(base, plugin.msg().text("portal.wand-name"),
                Arrays.asList(plugin.msg().text("portal.wand-lore-1"), plugin.msg().text("portal.wand-lore-2")), true);
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Items.parse(plugin.settings().wandMaterial).getType()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().equals(Text.color(plugin.msg().text("portal.wand-name")));
    }

    public Selection selection(Player player) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null) {
            selection = new Selection();
            selections.put(player.getUniqueId(), selection);
        }
        return selection;
    }

    public void forget(Player player) {
        selections.remove(player.getUniqueId());
        lastUse.remove(player.getUniqueId());
    }

    // ============================================================== Création

    /**
     * Crée un portail sur la sélection du joueur.
     *
     * @param destination destination (peut être vide : à définir ensuite)
     */
    public Result create(Player player, String name, String destination) {
        String id = name.toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,32}")) {
            return Result.NOM_INVALIDE;
        }
        if (plugin.portals().get(id) != null) {
            return Result.EXISTE_DEJA;
        }
        Selection s = selection(player);
        if (!s.isComplete()) {
            return Result.SELECTION_INCOMPLETE;
        }
        if (!s.first.getWorld().equals(s.second.getWorld())) {
            return Result.MONDES_DIFFERENTS;
        }
        long volume = (long) (Math.abs(s.first.getBlockX() - s.second.getBlockX()) + 1)
                * (Math.abs(s.first.getBlockY() - s.second.getBlockY()) + 1)
                * (Math.abs(s.first.getBlockZ() - s.second.getBlockZ()) + 1);
        if (volume > plugin.settings().maxPortalVolume) {
            return Result.TROP_GRAND;
        }
        String dest = destination == null ? "" : normalize(destination, player);
        if (dest == null) {
            return Result.DESTINATION_INVALIDE;
        }
        Map<String, Object> region = new LinkedHashMap<String, Object>();
        region.put("x1", s.first.getBlockX());
        region.put("y1", s.first.getBlockY());
        region.put("z1", s.first.getBlockZ());
        region.put("x2", s.second.getBlockX());
        region.put("y2", s.second.getBlockY());
        region.put("z2", s.second.getBlockZ());
        Map<String, Object> portal = new LinkedHashMap<String, Object>();
        portal.put("monde", s.first.getWorld().getName());
        portal.put("region", region);
        portal.put("destination", dest);
        portal.put("permission", "");
        portal.put("prix", 0.0);
        portal.put("delai-secondes", 0);
        portal.put("actif", Boolean.TRUE);
        portal.put("message", "");
        portal.put("remplissage", "");
        plugin.portalsFile().set(Collections.singletonList(id), portal);
        save();
        return Result.OK;
    }

    /**
     * Destination saisie par un joueur, mise au format du fichier :
     * "ici" devient la position du joueur, un nom de monde seul devient "monde:nom".
     *
     * @return la destination, ou null si elle est illisible
     */
    public String normalize(String raw, Player player) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        if ((value.equalsIgnoreCase("here") || value.equalsIgnoreCase("ici")) && player != null) {
            Location l = player.getLocation();
            return "position:" + l.getWorld().getName() + "," + round(l.getX()) + "," + round(l.getY()) + ","
                    + round(l.getZ()) + "," + round(l.getYaw()) + "," + round(l.getPitch());
        }
        int colon = value.indexOf(':');
        if (colon < 0) {
            return plugin.catalog().contains(value) ? "monde:" + value : null;
        }
        String type = value.substring(0, colon).toLowerCase(Locale.ROOT);
        if (type.equals("monde") || type.equals("world")) {
            return "monde:" + value.substring(colon + 1).trim();
        }
        if (type.equals("portail") || type.equals("portal")) {
            return "portail:" + value.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
        }
        if (type.equals("position") || type.equals("pos")) {
            return parsePosition(value.substring(colon + 1)) == null ? null : "position:" + value.substring(colon + 1).trim();
        }
        return null;
    }

    /**
     * Position d'arrivée d'une destination.
     *
     * @return la position, ou null si la destination est vide, inconnue ou non chargée
     */
    public Location resolve(String destination) {
        if (destination == null || destination.isEmpty()) {
            return null;
        }
        int colon = destination.indexOf(':');
        String type = colon < 0 ? "monde" : destination.substring(0, colon).toLowerCase(Locale.ROOT);
        String value = colon < 0 ? destination : destination.substring(colon + 1).trim();
        if (type.equals("monde")) {
            World world = plugin.worlds().findWorld(value);
            return world == null ? null : plugin.worlds().safe(plugin.worlds().spawn(world));
        }
        if (type.equals("portail")) {
            Portal target = plugin.portals().get(value);
            return target == null ? null : target.getExit();
        }
        if (type.equals("position")) {
            return parsePosition(value);
        }
        return null;
    }

    /** "monde,x,y,z[,yaw,pitch]" */
    private Location parsePosition(String raw) {
        String[] parts = raw.split(",");
        if (parts.length < 4) {
            return null;
        }
        World world = plugin.worlds().findWorld(parts[0].trim());
        Double x = Text.parseDouble(parts[1].trim());
        Double y = Text.parseDouble(parts[2].trim());
        Double z = Text.parseDouble(parts[3].trim());
        if (x == null || y == null || z == null) {
            return null;
        }
        Double yaw = parts.length > 4 ? Text.parseDouble(parts[4].trim()) : null;
        Double pitch = parts.length > 5 ? Text.parseDouble(parts[5].trim()) : null;
        if (world == null) {
            // monde non chargé : la destination reste valide, elle sera résolue plus tard
            return plugin.catalog().contains(parts[0].trim()) ? new Location(null, x, y, z) : null;
        }
        return new Location(world, x, y, z, yaw == null ? 0f : yaw.floatValue(), pitch == null ? 0f : pitch.floatValue());
    }

    /** Change une option d'un portail puis recharge. */
    public void set(Portal portal, String option, Object value) {
        plugin.portalsFile().set(Arrays.asList(portal.getName(), option), value);
        save();
    }

    /** Point d'arrivée de ce portail quand un autre portail y mène : la position du joueur. */
    public void setExit(Portal portal, Location location) {
        Map<String, Object> exit = new LinkedHashMap<String, Object>();
        exit.put("x", round(location.getX()));
        exit.put("y", round(location.getY()));
        exit.put("z", round(location.getZ()));
        exit.put("yaw", round(location.getYaw()));
        exit.put("pitch", round(location.getPitch()));
        set(portal, "sortie", exit);
    }

    public void delete(Portal portal) {
        if (!portal.getFill().isEmpty()) {
            fill(portal, "AIR");
        }
        plugin.portalsFile().remove(Collections.singletonList(portal.getName()));
        save();
    }

    private void save() {
        try {
            plugin.portalsFile().save();
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Impossible d'écrire portals.json : " + e.getMessage());
        }
        for (String problem : plugin.reload()) {
            plugin.getLogger().warning(problem);
        }
    }

    // ============================================================ Remplissage

    /**
     * Remplit la zone du portail : les blocs d'air (et l'ancien remplissage) deviennent
     * le matériau donné, sans physique pour que les blocs de portail tiennent.
     * "AIR" vide la zone de son remplissage.
     *
     * @return le nombre de blocs posés, ou -1 si le matériau est inconnu
     */
    public int fill(Portal portal, String spec) {
        World world = portal.getWorld();
        int[] material = spec.equalsIgnoreCase("AIR") || spec.equalsIgnoreCase("VIDE") ? new int[]{0, 0} : MaterialSet.parse(spec);
        if (world == null || material == null) {
            return -1;
        }
        int[] previous = portal.getFill().isEmpty() ? null : MaterialSet.parse(portal.getFill());
        int data = material[1] < 0 ? 0 : material[1];
        if (material[0] == Material.PORTAL.getId() && material[1] < 0) {
            // orientation des blocs de portail : 1 = le long de X, 2 = le long de Z
            data = portal.getMaxX() - portal.getMinX() >= portal.getMaxZ() - portal.getMinZ() ? 1 : 2;
        }
        int count = 0;
        for (int x = portal.getMinX(); x <= portal.getMaxX(); x++) {
            for (int y = portal.getMinY(); y <= portal.getMaxY(); y++) {
                for (int z = portal.getMinZ(); z <= portal.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    boolean replaceable = block.getType() == Material.AIR
                            || (previous != null && block.getTypeId() == previous[0]);
                    if (replaceable && (block.getTypeId() != material[0] || block.getData() != data)) {
                        block.setTypeIdAndData(material[0], (byte) data, false);
                        count++;
                    }
                }
            }
        }
        String saved = material[0] == 0 ? "" : spec.toUpperCase(Locale.ROOT);
        if (!saved.equals(portal.getFill())) {
            set(portal, "remplissage", saved);
        }
        return count;
    }

    /** Remet le remplissage des portails d'un monde (blocs de portail cassés par une mise à jour...). */
    public void refill(World world) {
        for (Portal portal : plugin.portals().inWorld(world.getName())) {
            if (portal.getFill().isEmpty()) {
                continue;
            }
            int[] material = MaterialSet.parse(portal.getFill());
            if (material == null) {
                continue;
            }
            int data = material[1] < 0 ? 0 : material[1];
            if (material[0] == Material.PORTAL.getId() && material[1] < 0) {
                data = portal.getMaxX() - portal.getMinX() >= portal.getMaxZ() - portal.getMinZ() ? 1 : 2;
            }
            for (int x = portal.getMinX(); x <= portal.getMaxX(); x++) {
                for (int y = portal.getMinY(); y <= portal.getMaxY(); y++) {
                    for (int z = portal.getMinZ(); z <= portal.getMaxZ(); z++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType() == Material.AIR) {
                            block.setTypeIdAndData(material[0], (byte) data, false);
                        }
                    }
                }
            }
        }
    }

    public static boolean isFill(Material type) {
        for (Material fill : FILLS) {
            if (fill == type) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ Passage

    /** Le joueur entre dans la zone d'un portail : conditions, puis téléportation. */
    public void use(Player player, Portal portal) {
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < plugin.settings().antiLoopSeconds * 1000L) {
            return;
        }
        if (!portal.isEnabled()) {
            return;
        }
        String vars = portal.getName();
        if (!portal.getPermission().isEmpty() && !player.hasPermission(portal.getPermission())
                && !player.hasPermission("koriamultiverse.portail.*")) {
            refuse(player, "portal.no-permission", portal);
            return;
        }
        String cooldownKey = player.getUniqueId() + ":" + vars;
        Long until = cooldowns.get(cooldownKey);
        if (until != null && now < until) {
            refuse(player, "portal.cooldown", portal, "temps", plugin.msg().duration(until - now));
            return;
        }
        Location target = resolve(portal.getDestination());
        if (target == null || target.getWorld() == null) {
            refuse(player, portal.getDestination().isEmpty() ? "portal.no-destination" : "portal.destination-unavailable", portal);
            return;
        }
        boolean pays = portal.getPrice() > 0 && !player.hasPermission("koriamultiverse.bypass.prix");
        VaultHook economy = pays ? plugin.economy() : null;
        if (pays && economy != null && !economy.has(player, portal.getPrice())) {
            refuse(player, "portal.too-poor", portal);
            return;
        }
        lastUse.put(player.getUniqueId(), now);
        if (!player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            return;
        }
        if (pays && economy != null && economy.withdraw(player, portal.getPrice())) {
            plugin.msg().send(player, "portal.paid", "portail", portal.getName(), "prix", Text.number(portal.getPrice(), 2));
        }
        if (portal.getCooldownSeconds() > 0) {
            cooldowns.put(cooldownKey, now + portal.getCooldownSeconds() * 1000L);
        }
        if (!portal.getMessage().isEmpty()) {
            plugin.msg().sendLine(player, plugin.msg().format(portal.getMessage(), "portail", portal.getName(),
                    "joueur", player.getName(), "monde", plugin.worlds().alias(target.getWorld().getName())));
        }
        Sounds.play(player, plugin.settings().sound("portail"));
    }

    private void refuse(Player player, String key, Portal portal, Object... extra) {
        Object[] kv = new Object[extra.length + 6];
        kv[0] = "portail";
        kv[1] = portal.getName();
        kv[2] = "prix";
        kv[3] = Text.number(portal.getPrice(), 2);
        kv[4] = "destination";
        kv[5] = portal.getDestination();
        System.arraycopy(extra, 0, kv, 6, extra.length);
        plugin.msg().send(player, key, kv);
        Sounds.play(player, plugin.settings().sound("refus"));
        // pas de nouveau message avant de ressortir et rentrer
        lastUse.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
