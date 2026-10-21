package fr.koriamc.multiverse.listener;

import fr.koriamc.multiverse.KoriaMultiverse;
import fr.koriamc.multiverse.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Règles du serveur : Nether et End fermés à tout le monde, OP compris (dimensions.nether /
 * dimensions.end), et apparition naturelle des créatures bloquée (seuls les spawners en font
 * apparaître). Aucune permission ne permet de passer : pour rouvrir, mettre l'option à true.
 */
public final class DimensionListener implements Listener {

    private final KoriaMultiverse plugin;
    /** Dernier message "dimension fermée" par joueur (un portail le déclenche en boucle). */
    private final Map<UUID, Long> lastWarning = new HashMap<UUID, Long>();

    public DimensionListener(KoriaMultiverse plugin) {
        this.plugin = plugin;
    }

    /** Clé du message de refus si ce monde est une dimension fermée, sinon null. */
    private String closed(World world) {
        if (world == null) {
            return null;
        }
        Settings s = plugin.settings();
        if (world.getEnvironment() == World.Environment.NETHER && !s.netherAllowed) {
            return "access.nether-closed";
        }
        if (world.getEnvironment() == World.Environment.THE_END && !s.endAllowed) {
            return "access.end-closed";
        }
        return null;
    }

    private void warn(Player player, String key) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last == null || now - last > 3000L) {
            lastWarning.put(player.getUniqueId(), now);
            plugin.msg().send(player, key);
        }
    }

    // ============================================================ Dimensions

    /** Portails : passe après la redirection des portails par monde (priorité HIGH). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        String key = null;
        if (to != null) {
            key = closed(to.getWorld());
        } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && !plugin.settings().netherAllowed) {
            key = "access.nether-closed";
        } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL && !plugin.settings().endAllowed) {
            key = "access.end-closed";
        }
        // sortir d'une dimension fermée reste possible (joueur déjà dedans)
        if (key != null && closed(player.getWorld()) == null) {
            event.setCancelled(true);
            warn(player, key);
        }
    }

    /** Téléportations (commandes, plugins) vers le Nether ou l'End. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || to.getWorld().equals(event.getFrom().getWorld())) {
            return;
        }
        String key = closed(to.getWorld());
        if (key != null) {
            event.setCancelled(true);
            warn(event.getPlayer(), key);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Location to = event.getTo();
        if (to != null && closed(to.getWorld()) != null) {
            event.setCancelled(true);
        }
    }

    /** Portails du Nether impossibles à allumer quand le Nether est fermé. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (!plugin.settings().netherAllowed && event.getReason() == PortalCreateEvent.CreateReason.FIRE) {
            event.setCancelled(true);
        }
    }

    /** Oeil de l'Ender posé sur un cadre de portail : le portail de l'End ne s'active pas. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEyeOfEnder(PlayerInteractEvent event) {
        if (plugin.settings().endAllowed || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENDER_PORTAL_FRAME
                || event.getItem() == null || event.getItem().getType() != Material.EYE_OF_ENDER) {
            return;
        }
        event.setCancelled(true);
        warn(event.getPlayer(), "access.end-closed");
    }

    /** Un joueur resté dans une dimension fermée en est sorti à la connexion. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        evacuate(event.getPlayer());
    }

    /** Filet de sécurité : arrivée dans une dimension fermée par un moyen non prévu. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        evacuate(event.getPlayer());
    }

    private void evacuate(final Player player) {
        if (closed(player.getWorld()) == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline() || closed(player.getWorld()) == null) {
                    return;
                }
                World main = Bukkit.getWorlds().get(0);
                player.teleport(plugin.worlds().safe(plugin.worlds().spawn(main)), PlayerTeleportEvent.TeleportCause.PLUGIN);
                plugin.msg().send(player, "access.dimension-evacuated");
            }
        }, 3L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastWarning.remove(event.getPlayer().getUniqueId());
    }

    // ============================================================ Créatures

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Settings s = plugin.settings();
        if (s.blockNaturalSpawns && s.blockedReasons.contains(event.getSpawnReason())
                && !s.naturalSpawnWorlds.contains(event.getLocation().getWorld().getName().toLowerCase(Locale.ROOT))) {
            event.setCancelled(true);
        }
    }
}
