package fr.koriamc.multiverse.command.impl;

import fr.koriamc.multiverse.KoriaMultiverse;
import fr.koriamc.multiverse.command.CommandContext;
import fr.koriamc.multiverse.command.SubCommand;
import fr.koriamc.multiverse.model.Portal;
import fr.koriamc.multiverse.util.Text;
import fr.koriamc.multiverse.world.PortalManager;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /mv portal &lt;action&gt; : portails personnalisés.
 * <pre>
 * /mv portal wand                            baguette de sélection (clic gauche / droit)
 * /mv portal pos1 | pos2                     coin à la position du joueur
 * /mv portal create &lt;name&gt; [destination]
 * /mv portal destination &lt;name&gt; &lt;destination&gt;   world:&lt;monde&gt;, portal:&lt;nom&gt;, position:..., here
 * /mv portal exit &lt;name&gt;                      point d'arrivée quand un autre portail mène ici
 * /mv portal fill &lt;name&gt; &lt;PORTAL|ENDER_PORTAL|STATIONARY_WATER|AIR...&gt;
 * /mv portal option &lt;name&gt; &lt;permission|price|cooldown|enabled|message&gt; &lt;value&gt;
 * /mv portal list | info &lt;name&gt; | tp &lt;name&gt; | delete &lt;name&gt;
 * </pre>
 */
public final class PortalCommand extends SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("wand", "pos1", "pos2", "create", "destination", "exit",
            "fill", "option", "list", "info", "tp", "delete");
    private static final List<String> OPTIONS = Arrays.asList("permission", "price", "cooldown", "enabled", "message");
    private static final List<String> FILLS = Arrays.asList("PORTAL", "ENDER_PORTAL", "STATIONARY_WATER",
            "STATIONARY_LAVA", "WEB", "AIR");
    private static final long CONFIRM_MS = 10000L;
    private static final Map<String, String> CONFIRM = new HashMap<String, String>();
    private static final Map<String, Long> CONFIRM_TIME = new HashMap<String, Long>();

    public PortalCommand(KoriaMultiverse plugin) {
        super(plugin, "portal", false, 0, true);
    }

    @Override
    public void execute(CommandContext ctx) {
        String action = ctx.size() == 0 ? "help" : ctx.arg(0).toLowerCase(Locale.ROOT);
        if (action.equals("help") || !ACTIONS.contains(action)) {
            ctx.msg("portal.help");
            return;
        }
        if (action.equals("list")) {
            list(ctx);
            return;
        }
        boolean needsPlayer = action.equals("wand") || action.equals("pos1") || action.equals("pos2")
                || action.equals("create") || action.equals("exit") || action.equals("tp");
        if (needsPlayer && ctx.player == null) {
            ctx.msg("general.players-only");
            return;
        }
        if (action.equals("wand")) {
            ItemStack wand = plugin.portalManager().wand();
            if (!ctx.player.getInventory().addItem(wand).isEmpty()) {
                ctx.player.getWorld().dropItem(ctx.player.getLocation(), wand);
            }
            ctx.msg("portal.wand-given");
            return;
        }
        if (action.equals("pos1") || action.equals("pos2")) {
            PortalManager.Selection selection = plugin.portalManager().selection(ctx.player);
            Location at = ctx.player.getLocation().getBlock().getLocation();
            if (action.equals("pos1")) {
                selection.first = at;
            } else {
                selection.second = at;
            }
            ctx.msg(action.equals("pos1") ? "portal.pos1" : "portal.pos2", "x", at.getBlockX(), "y", at.getBlockY(),
                    "z", at.getBlockZ(), "blocs", volume(selection));
            return;
        }
        if (ctx.size() < 2) {
            ctx.msg("portal.help");
            return;
        }
        String name = ctx.arg(1).toLowerCase(Locale.ROOT);
        if (action.equals("create")) {
            create(ctx, name);
            return;
        }
        Portal portal = plugin.portals().get(name);
        if (portal == null) {
            ctx.msg("portal.unknown", "portail", name);
            return;
        }
        if (action.equals("destination")) {
            destination(ctx, portal);
        } else if (action.equals("exit")) {
            plugin.portalManager().setExit(portal, ctx.player.getLocation());
            ctx.msg("portal.exit-set", "portail", portal.getName());
        } else if (action.equals("fill")) {
            fill(ctx, portal);
        } else if (action.equals("option")) {
            option(ctx, portal);
        } else if (action.equals("info")) {
            info(ctx, portal);
        } else if (action.equals("tp")) {
            Location exit = portal.getExit();
            if (exit == null) {
                ctx.msg("world.not-loaded", "monde", portal.getWorldName(), "id", portal.getWorldName());
                return;
            }
            ctx.player.teleport(plugin.worlds().safe(exit));
        } else if (action.equals("delete")) {
            String who = ctx.sender.getName();
            Long time = CONFIRM_TIME.get(who);
            if (!portal.getName().equals(CONFIRM.get(who)) || time == null || System.currentTimeMillis() - time > CONFIRM_MS) {
                CONFIRM.put(who, portal.getName());
                CONFIRM_TIME.put(who, System.currentTimeMillis());
                ctx.msg("portal.confirm-delete", "portail", portal.getName());
                return;
            }
            CONFIRM.remove(who);
            CONFIRM_TIME.remove(who);
            plugin.portalManager().delete(portal);
            ctx.msg("portal.deleted", "portail", portal.getName());
        }
    }

    private void create(CommandContext ctx, String name) {
        String destination = ctx.size() > 2 ? Text.join(ctx.args, 2, " ") : "";
        PortalManager.Result result = plugin.portalManager().create(ctx.player, name, destination);
        if (result != PortalManager.Result.OK) {
            ctx.msg("portal.create-" + result.name().toLowerCase(Locale.ROOT), "portail", name,
                    "max", plugin.settings().maxPortalVolume, "destination", destination);
            return;
        }
        Portal portal = plugin.portals().get(name);
        ctx.msg("portal.created", "portail", name, "zone", portal == null ? "?" : portal.describeRegion(),
                "destination", portal == null || portal.getDestination().isEmpty()
                        ? plugin.msg().text("values.none") : portal.getDestination());
    }

    private void destination(CommandContext ctx, Portal portal) {
        if (ctx.size() < 3) {
            ctx.msg("portal.help");
            return;
        }
        String raw = Text.join(ctx.args, 2, " ");
        String destination = plugin.portalManager().normalize(raw, ctx.player);
        if (destination == null) {
            ctx.msg("portal.create-destination_invalide", "destination", raw);
            return;
        }
        if (destination.equals("portail:" + portal.getName())) {
            ctx.msg("portal.self-destination");
            return;
        }
        plugin.portalManager().set(portal, "destination", destination);
        ctx.msg("portal.destination-set", "portail", portal.getName(), "destination", destination);
    }

    private void fill(CommandContext ctx, Portal portal) {
        if (ctx.size() < 3) {
            ctx.msg("portal.help");
            return;
        }
        int count = plugin.portalManager().fill(portal, ctx.arg(2));
        if (count < 0) {
            ctx.msg("portal.unknown-material", "materiau", ctx.arg(2));
            return;
        }
        ctx.msg("portal.filled", "portail", portal.getName(), "materiau", ctx.arg(2).toUpperCase(Locale.ROOT), "blocs", count);
    }

    private void option(CommandContext ctx, Portal portal) {
        if (ctx.size() < 4) {
            ctx.msg("portal.help");
            return;
        }
        String option = ctx.arg(2).toLowerCase(Locale.ROOT);
        String raw = Text.join(ctx.args, 3, " ");
        Object value;
        String key;
        if (option.equals("permission") || option.equals("message")) {
            key = option;
            value = raw.equalsIgnoreCase(plugin.msg().text("config-editor.empty-word")) ? "" : raw;
        } else if (option.equals("price")) {
            Double price = Text.parseDouble(raw);
            if (price == null || price < 0) {
                ctx.msg("general.invalid-number", "input", raw);
                return;
            }
            key = "prix";
            value = price;
        } else if (option.equals("cooldown")) {
            Integer seconds = Text.parseInt(raw);
            if (seconds == null || seconds < 0) {
                ctx.msg("general.invalid-number", "input", raw);
                return;
            }
            key = "delai-secondes";
            value = seconds;
        } else if (option.equals("enabled")) {
            key = "actif";
            value = Text.parseBoolean(raw, true);
        } else {
            ctx.msg("portal.help");
            return;
        }
        plugin.portalManager().set(portal, key, value);
        ctx.msg("portal.option-set", "portail", portal.getName(), "option", key, "valeur", String.valueOf(value));
    }

    private void info(CommandContext ctx, Portal portal) {
        String none = plugin.msg().text("values.none");
        Location target = plugin.portalManager().resolve(portal.getDestination());
        ctx.msg("portal.info",
                "portail", portal.getName(),
                "zone", portal.describeRegion(),
                "blocs", portal.getVolume(),
                "destination", portal.getDestination().isEmpty() ? none : portal.getDestination(),
                "etat", plugin.msg().text(target == null || target.getWorld() == null
                        ? "portal.state-unavailable" : "portal.state-ok"),
                "sortie", portal.hasExit() ? plugin.msg().text("portal.exit-custom") : plugin.msg().text("portal.exit-auto"),
                "remplissage", portal.getFill().isEmpty() ? none : portal.getFill(),
                "permission", portal.getPermission().isEmpty() ? plugin.msg().text("values.free") : portal.getPermission(),
                "prix", portal.getPrice() <= 0 ? plugin.msg().text("values.free") : Text.number(portal.getPrice(), 2),
                "delai", portal.getCooldownSeconds(),
                "actif", plugin.msg().yesNo(portal.isEnabled()));
    }

    private void list(CommandContext ctx) {
        if (plugin.portals().count() == 0) {
            ctx.msg("portal.none");
            return;
        }
        ctx.msg("portal.list-header", "total", plugin.portals().count());
        for (Portal portal : plugin.portals().all()) {
            ctx.msg(portal.isEnabled() ? "portal.list-line" : "portal.list-line-disabled",
                    "portail", portal.getName(), "monde", portal.getWorldName(),
                    "destination", portal.getDestination().isEmpty() ? plugin.msg().text("values.none") : portal.getDestination());
        }
    }

    private static String volume(PortalManager.Selection s) {
        if (!s.isComplete() || !s.first.getWorld().equals(s.second.getWorld())) {
            return "?";
        }
        return String.valueOf((long) (Math.abs(s.first.getBlockX() - s.second.getBlockX()) + 1)
                * (Math.abs(s.first.getBlockY() - s.second.getBlockY()) + 1)
                * (Math.abs(s.first.getBlockZ() - s.second.getBlockZ()) + 1));
    }

    @Override
    public List<String> complete(CommandContext ctx) {
        if (ctx.size() == 1) {
            return Text.complete(ctx.arg(0), ACTIONS);
        }
        String action = ctx.arg(0).toLowerCase(Locale.ROOT);
        if (ctx.size() == 2 && !Arrays.asList("wand", "pos1", "pos2", "create", "list").contains(action)) {
            return Text.complete(ctx.arg(1), plugin.portals().names());
        }
        if ((action.equals("destination") && ctx.size() == 3) || (action.equals("create") && ctx.size() == 3)) {
            List<String> options = new ArrayList<String>();
            options.add("here");
            for (String world : plugin.catalog().names()) {
                options.add("world:" + world);
            }
            for (String portal : plugin.portals().names()) {
                options.add("portal:" + portal);
            }
            return Text.complete(ctx.arg(2), options);
        }
        if (action.equals("fill") && ctx.size() == 3) {
            return Text.complete(ctx.arg(2), FILLS);
        }
        if (action.equals("option") && ctx.size() == 3) {
            return Text.complete(ctx.arg(2), OPTIONS);
        }
        if (action.equals("option") && ctx.size() == 4 && "enabled".equalsIgnoreCase(ctx.arg(2))) {
            return Text.complete(ctx.arg(3), Arrays.asList("yes", "no"));
        }
        return Collections.emptyList();
    }
}
