package fr.koriamc.multiverse.listener;

import fr.koriamc.multiverse.KoriaMultiverse;
import fr.koriamc.multiverse.api.event.WorldEntryEvent;
import fr.koriamc.multiverse.hook.VaultHook;
import fr.koriamc.multiverse.model.WorldSettings;
import fr.koriamc.multiverse.util.Sounds;
import fr.koriamc.multiverse.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Fait respecter les propriétés des mondes : conditions d'entrée, mode de jeu, vol,
 * météo, faim, réapparition, portails et arrivée sur le serveur.
 */
public final class WorldListener implements Listener {

    private final KoriaMultiverse plugin;

    public WorldListener(KoriaMultiverse plugin) {
        this.plugin = plugin;
    }

    // ================================================================= Entrée

    /** Vérifie l'accès au monde de destination (permission, limite de joueurs, prix). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        World from = event.getFrom().getWorld();
        World to = event.getTo() == null ? null : event.getTo().getWorld();
        if (to == null || to.equals(from)) {
            return;
        }
        WorldSettings s = plugin.catalog().get(to);
        if (s == null) {
            return;
        }
        String refusal = refusal(player, s, to);
        if (refusal != null) {
            event.setCancelled(true);
            plugin.msg().send(player, refusal, "monde", Text.color(s.getAlias()), "limite", s.getPlayerLimit(),
                    "prix", Text.number(s.getPrice(), 2), "permission", s.getPermission());
            Sounds.play(player, plugin.settings().sound("refus"));
            return;
        }
        WorldEntryEvent entry = new WorldEntryEvent(player, from, to);
        Bukkit.getPluginManager().callEvent(entry);
        if (entry.isCancelled()) {
            event.setCancelled(true);
        }
    }

    /** Le prix d'entrée n'est prélevé qu'une fois la téléportation confirmée. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportDone(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        World to = event.getTo() == null ? null : event.getTo().getWorld();
        if (to == null || to.equals(event.getFrom().getWorld())) {
            return;
        }
        WorldSettings s = plugin.catalog().get(to);
        if (s == null || s.getPrice() <= 0 || player.hasPermission("koriamultiverse.bypass.prix")) {
            return;
        }
        VaultHook economy = plugin.economy();
        if (economy != null && economy.withdraw(player, s.getPrice())) {
            plugin.msg().send(player, "access.paid", "monde", Text.color(s.getAlias()), "prix", Text.number(s.getPrice(), 2));
        }
    }

    /**
     * Raison du refus d'entrée (clé de messages.json), ou null si le joueur peut entrer.
     */
    public String refusal(Player player, WorldSettings s, World world) {
        if (!s.getPermission().isEmpty() && !player.hasPermission(s.getPermission())
                && !player.hasPermission("koriamultiverse.acces.*")) {
            return "access.no-permission";
        }
        if (s.getPlayerLimit() > 0 && !player.hasPermission("koriamultiverse.bypass.limite")) {
            int count = 0;
            for (Player other : world.getPlayers()) {
                if (!other.equals(player)) {
                    count++;
                }
            }
            if (count >= s.getPlayerLimit()) {
                return "access.full";
            }
        }
        if (s.getPrice() > 0 && !player.hasPermission("koriamultiverse.bypass.prix")) {
            VaultHook economy = plugin.economy();
            if (economy == null) {
                plugin.getLogger().warning("Prix d'entrée de " + s.getName() + " ignoré : aucune économie Vault disponible.");
                return null;
            }
            if (!economy.has(player, s.getPrice())) {
                return "access.too-poor";
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        WorldSettings s = plugin.catalog().get(player.getWorld());
        if (s == null) {
            return;
        }
        plugin.worlds().applyPlayer(player, s);
        if (plugin.settings().welcomeMessage) {
            plugin.msg().send(player, "world.welcome", "monde", Text.color(s.getAlias()), "id", s.getName());
        }
        Sounds.play(player, plugin.settings().sound("teleportation"));
    }

    // ============================================================== Connexion

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        String first = plugin.settings().firstWorld;
        boolean send = !first.isEmpty() && (plugin.settings().alwaysFirstWorld || !player.hasPlayedBefore());
        final World target = send ? plugin.worlds().findWorld(first) : null;
        if (send && target == null) {
            plugin.getLogger().warning("arrivee.premier-monde : le monde " + first + " n'est pas chargé.");
        }
        // au tick suivant : les autres plugins ont fini de traiter la connexion
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                if (target != null) {
                    player.teleport(plugin.worlds().safe(plugin.worlds().spawn(target)),
                            PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
                WorldSettings s = plugin.catalog().get(player.getWorld());
                if (s != null) {
                    plugin.worlds().applyPlayer(player, s);
                }
            }
        });
    }

    /** Réapparition sans lit : monde de réapparition configuré, sinon spawn du monde de la mort. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn()) {
            return;
        }
        World death = event.getPlayer().getWorld();
        WorldSettings s = plugin.catalog().get(death);
        if (s == null) {
            return;
        }
        World target = null;
        if (!s.getRespawnWorld().isEmpty()) {
            target = plugin.worlds().findWorld(s.getRespawnWorld());
            if (target == null) {
                plugin.getLogger().warning("worlds.json : " + s.getName() + ".reapparition = " + s.getRespawnWorld()
                        + " n'est pas chargé.");
            }
        } else if (plugin.settings().respawnInWorld) {
            target = death;
        }
        if (target != null) {
            event.setRespawnLocation(plugin.worlds().safe(plugin.worlds().spawn(target)));
        }
    }

    // ============================================================ Propriétés

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        WorldSettings s = plugin.catalog().get(event.getWorld());
        if (s != null && !s.isWeather() && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent event) {
        WorldSettings s = plugin.catalog().get(event.getWorld());
        if (s != null && !s.isWeather() && event.toThunderState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        WorldSettings s = plugin.catalog().get(player.getWorld());
        if (s != null && !s.isHunger() && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    /** Monde chargé par un autre plugin ou par une commande : ses propriétés s'appliquent aussitôt. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        WorldSettings s = plugin.catalog().get(event.getWorld());
        if (s != null) {
            plugin.worlds().apply(event.getWorld(), s);
        }
    }

    // ================================================================ Portails

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!plugin.settings().portals) {
            return;
        }
        boolean end = event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL;
        if (!end && event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }
        Location target = portalTarget(event.getFrom(), end, event.getPlayer());
        if (target == null) {
            return;
        }
        event.setTo(target);
        event.useTravelAgent(!end);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!plugin.settings().portals) {
            return;
        }
        Entity entity = event.getEntity();
        boolean end = entity.getLocation().getBlock().getType() == Material.ENDER_PORTAL;
        Location target = portalTarget(event.getFrom(), end, null);
        if (target == null) {
            return;
        }
        event.setTo(target);
        event.useTravelAgent(!end);
    }

    /**
     * Destination d'un portail d'après portails.nether / portails.end du monde de départ,
     * ou null pour laisser faire Minecraft.
     */
    private Location portalTarget(Location from, boolean end, Player player) {
        WorldSettings s = plugin.catalog().get(from.getWorld());
        if (s == null) {
            return null;
        }
        String targetName = end ? s.getEndTarget() : s.getNetherTarget();
        if (targetName.isEmpty()) {
            return null;
        }
        World target = plugin.worlds().findWorld(targetName);
        if (target == null) {
            if (player != null) {
                plugin.msg().send(player, "world.portal-unloaded", "monde", targetName);
            }
            return null;
        }
        if (end) {
            if (target.getEnvironment() == World.Environment.THE_END) {
                return endPlatform(target);
            }
            return plugin.worlds().safe(plugin.worlds().spawn(target));
        }
        WorldSettings ts = plugin.catalog().get(target);
        double targetScale = ts != null ? ts.getScale() : target.getEnvironment() == World.Environment.NETHER ? 8.0 : 1.0;
        double ratio = s.getScale() / targetScale;
        return new Location(target, from.getX() * ratio, from.getY(), from.getZ() * ratio, from.getYaw(), from.getPitch());
    }

    /** Plateforme d'obsidienne d'arrivée dans l'End, comme celle de Minecraft (x 100, z 0). */
    @SuppressWarnings("deprecation")
    private static Location endPlatform(World end) {
        int x = 100;
        int y = 48;
        int z = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                end.getBlockAt(x + dx, y, z + dz).setType(Material.OBSIDIAN);
                for (int dy = 1; dy <= 3; dy++) {
                    end.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                }
            }
        }
        return new Location(end, x + 0.5, y + 1, z + 0.5, 90, 0);
    }
}
