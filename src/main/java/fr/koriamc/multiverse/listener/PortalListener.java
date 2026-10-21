package fr.koriamc.multiverse.listener;

import fr.koriamc.multiverse.KoriaMultiverse;
import fr.koriamc.multiverse.command.SubCommand;
import fr.koriamc.multiverse.model.Portal;
import fr.koriamc.multiverse.world.PortalManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Portails personnalisés : entrée dans la zone, baguette de sélection, blocage des
 * portails de Minecraft dans la zone et maintien des blocs qui remplissent le portail.
 */
public final class PortalListener implements Listener {

    private final KoriaMultiverse plugin;

    public PortalListener(KoriaMultiverse plugin) {
        this.plugin = plugin;
    }

    /** Le joueur entre dans une zone de portail (il n'y était pas au mouvement précédent). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (!plugin.settings().customPortals || to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() == to.getWorld())) {
            return;
        }
        final Portal portal = plugin.portals().at(to);
        if (portal == null || portal == plugin.portals().at(from)) {
            return;
        }
        final Player player = event.getPlayer();
        // téléportation au tick suivant : jamais pendant l'évènement de mouvement
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && portal.contains(player.getLocation())) {
                    plugin.portalManager().use(player, portal);
                }
            }
        });
    }

    /** Baguette : clic gauche = premier coin, clic droit = second coin. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onWand(PlayerInteractEvent event) {
        Action action = event.getAction();
        if ((action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) || event.getClickedBlock() == null
                || !plugin.portalManager().isWand(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        SubCommand command = plugin.commands().get("portal");
        if (command != null && !command.hasPermission(player)) {
            return;
        }
        event.setCancelled(true);
        Block block = event.getClickedBlock();
        PortalManager.Selection selection = plugin.portalManager().selection(player);
        boolean first = action == Action.LEFT_CLICK_BLOCK;
        if (first) {
            selection.first = block.getLocation();
        } else {
            selection.second = block.getLocation();
        }
        plugin.msg().send(player, first ? "portal.pos1" : "portal.pos2", "x", block.getX(), "y", block.getY(),
                "z", block.getZ(), "blocs", volume(selection));
    }

    static String volume(PortalManager.Selection s) {
        if (!s.isComplete() || !s.first.getWorld().equals(s.second.getWorld())) {
            return "?";
        }
        return String.valueOf((long) (Math.abs(s.first.getBlockX() - s.second.getBlockX()) + 1)
                * (Math.abs(s.first.getBlockY() - s.second.getBlockY()) + 1)
                * (Math.abs(s.first.getBlockZ() - s.second.getBlockZ()) + 1));
    }

    /** Dans la zone d'un portail personnalisé, les portails du Nether et de l'End ne font rien. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (plugin.settings().customPortals && plugin.portals().at(event.getFrom()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (plugin.settings().customPortals && plugin.portals().at(event.getFrom()) != null) {
            event.setCancelled(true);
        }
    }

    /** Les blocs de portail posés sans cadre ne disparaissent pas quand un voisin change. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (PortalManager.isFill(block.getType()) && filled(block.getLocation())) {
            event.setCancelled(true);
        }
    }

    /** L'eau ou la lave d'un portail ne coule pas hors de la zone. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (filled(event.getBlock().getLocation()) && !filled(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.settings().protectPortals || plugin.portals().at(event.getBlock().getLocation()) == null) {
            return;
        }
        SubCommand command = plugin.commands().get("portal");
        if (command == null || !command.hasPermission(event.getPlayer())) {
            event.setCancelled(true);
            plugin.msg().send(event.getPlayer(), "portal.protected");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.portalManager().refill(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.portalManager().forget(event.getPlayer());
    }

    /** Position dans un portail rempli. */
    private boolean filled(Location location) {
        Portal portal = plugin.portals().at(location);
        return portal != null && !portal.getFill().isEmpty();
    }
}
