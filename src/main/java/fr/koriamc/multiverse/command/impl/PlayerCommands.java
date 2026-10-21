package fr.koriamc.multiverse.command.impl;

import fr.koriamc.multiverse.KoriaMultiverse;
import fr.koriamc.multiverse.command.CommandContext;
import fr.koriamc.multiverse.command.SubCommand;
import fr.koriamc.multiverse.model.WorldSettings;
import fr.koriamc.multiverse.util.Text;
import fr.koriamc.multiverse.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Commandes des joueurs : aide, menu, liste des mondes, informations, joueurs par monde.
 */
public final class PlayerCommands {

    private PlayerCommands() {
    }

    /** Noms des mondes gérés pour l'autocomplétion. */
    static List<String> worldNames(KoriaMultiverse plugin, String input) {
        return Text.complete(input, plugin.catalog().names());
    }

    /** Noms des mondes chargés pour l'autocomplétion. */
    static List<String> loadedNames(String input) {
        List<String> names = new ArrayList<String>();
        for (World world : Bukkit.getWorlds()) {
            names.add(world.getName());
        }
        return Text.complete(input, names);
    }

    /** /mv help */
    public static final class Help extends SubCommand {
        public Help(KoriaMultiverse plugin) {
            super(plugin, "help", false, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            ctx.msg("help.header");
            for (SubCommand sub : plugin.commands().visible(ctx.sender)) {
                ctx.msg("help.line", "command", sub.getId(), "usage", sub.getUsage(), "description", sub.getDescription());
            }
            ctx.msg("help.footer");
        }
    }

    /** /mv menu */
    public static final class OpenMenu extends SubCommand {
        public OpenMenu(KoriaMultiverse plugin) {
            super(plugin, "menu", true, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.menus().openList(ctx.player);
        }
    }

    /** /mv list : mondes gérés, chargés ou non, et dossiers importables */
    public static final class ListWorlds extends SubCommand {
        public ListWorlds(KoriaMultiverse plugin) {
            super(plugin, "list", false, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            ctx.msg("world.list-header", "total", plugin.catalog().count());
            for (WorldSettings s : plugin.catalog().all()) {
                World world = Bukkit.getWorld(s.getName());
                ctx.msg(world == null ? "world.list-line-unloaded" : "world.list-line",
                        "monde", Text.color(s.getAlias()), "id", s.getName(),
                        "environnement", plugin.msg().text("values.environment-" + s.getEnvironment().name().toLowerCase(Locale.ROOT)),
                        "joueurs", world == null ? 0 : world.getPlayers().size());
            }
            List<String> importable = plugin.worlds().importableFolders();
            if (!importable.isEmpty() && ctx.sender.hasPermission("koriamultiverse.admin")) {
                ctx.msg("world.list-importable", "dossiers", Text.join(importable, ", "));
            }
        }
    }

    /** /mv info [monde] */
    public static final class Info extends SubCommand {
        public Info(KoriaMultiverse plugin) {
            super(plugin, "info", false, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            String name = ctx.arg(0) != null ? ctx.arg(0) : ctx.player != null ? ctx.player.getWorld().getName() : null;
            WorldSettings s = plugin.catalog().get(name);
            if (s == null) {
                ctx.msg("world.unknown", "monde", name == null ? "?" : name);
                return;
            }
            World world = Bukkit.getWorld(s.getName());
            String none = plugin.msg().text("values.none");
            ctx.msg("world.info",
                    "monde", Text.color(s.getAlias()),
                    "id", s.getName(),
                    "etat", plugin.msg().text(world == null ? "values.unloaded" : "values.loaded"),
                    "environnement", plugin.msg().text("values.environment-" + s.getEnvironment().name().toLowerCase(Locale.ROOT)),
                    "type", s.getType().name(),
                    "generateur", s.getGenerator().isEmpty() ? plugin.msg().text("values.default") : s.getGenerator(),
                    "graine", world != null ? String.valueOf(world.getSeed()) : s.getSeed().isEmpty() ? none : s.getSeed(),
                    "joueurs", world == null ? 0 : world.getPlayers().size(),
                    "limite", s.getPlayerLimit() == 0 ? plugin.msg().text("values.unlimited") : String.valueOf(s.getPlayerLimit()),
                    "pvp", plugin.msg().yesNo(s.isPvp()),
                    "difficulte", plugin.msg().text("values.difficulty-" + s.getDifficulty().name().toLowerCase(Locale.ROOT)),
                    "mode", s.getGameMode() == null ? plugin.msg().text("values.free")
                            : plugin.msg().text("values.gamemode-" + s.getGameMode().name().toLowerCase(Locale.ROOT)),
                    "meteo", plugin.msg().yesNo(s.isWeather()),
                    "heure", s.getTime() < 0 ? plugin.msg().text("values.cycle") : String.valueOf(s.getTime()),
                    "faim", plugin.msg().yesNo(s.isHunger()),
                    "monstres", plugin.msg().yesNo(s.isMonsters()),
                    "animaux", plugin.msg().yesNo(s.isAnimals()),
                    "acces", s.getPermission().isEmpty() ? plugin.msg().text("values.free") : s.getPermission(),
                    "prix", s.getPrice() <= 0 ? plugin.msg().text("values.free") : Text.number(s.getPrice(), 2),
                    "bordure", s.getBorderSize() <= 0 ? none : Text.number(s.getBorderSize(), 0),
                    "nether", s.getNetherTarget().isEmpty() ? plugin.msg().text("values.default") : s.getNetherTarget(),
                    "end", s.getEndTarget().isEmpty() ? plugin.msg().text("values.default") : s.getEndTarget(),
                    "principal", world != null && WorldManager.isMainWorld(world) ? plugin.msg().text("values.main-world") : "");
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? worldNames(plugin, ctx.arg(0)) : Collections.<String>emptyList();
        }
    }

    /** /mv who [monde] : joueurs de chaque monde */
    public static final class Who extends SubCommand {
        public Who(KoriaMultiverse plugin) {
            super(plugin, "who", false, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            List<World> worlds = new ArrayList<World>();
            if (ctx.size() > 0) {
                World world = plugin.worlds().findWorld(ctx.arg(0));
                if (world == null) {
                    ctx.msg("world.not-loaded", "monde", ctx.arg(0), "id", ctx.arg(0));
                    return;
                }
                worlds.add(world);
            } else {
                worlds.addAll(Bukkit.getWorlds());
            }
            for (World world : worlds) {
                List<String> names = new ArrayList<String>();
                for (Player player : world.getPlayers()) {
                    if (ctx.player == null || ctx.player.canSee(player)) {
                        names.add(player.getName());
                    }
                }
                if (names.isEmpty() && ctx.size() == 0) {
                    continue;
                }
                ctx.msg("world.who", "monde", plugin.worlds().alias(world.getName()), "nombre", names.size(),
                        "joueurs", names.isEmpty() ? plugin.msg().text("values.nobody") : Text.join(names, ", "));
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? loadedNames(ctx.arg(0)) : Collections.<String>emptyList();
        }
    }
}
