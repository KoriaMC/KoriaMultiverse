package fr.koriamc.multiverse.gui;

import fr.koriamc.multiverse.KoriaMultiverse;
import fr.koriamc.multiverse.command.SubCommand;
import fr.koriamc.multiverse.model.WorldSettings;
import fr.koriamc.multiverse.util.Items;
import fr.koriamc.multiverse.util.Text;
import fr.koriamc.multiverse.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /mv menu : liste des mondes gérés, chargés ou non. L'apparence et l'effet des
 * clics viennent de menus.json (liste). Actions propres : TELEPORTER, EDITER,
 * CHARGER, DECHARGER.
 */
final class WorldListMenu extends Menu {

    WorldListMenu(KoriaMultiverse plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected ConfigurationSection section() {
        return plugin.menus().section("liste");
    }

    @Override
    protected void render() {
        ConfigurationSection s = section();
        List<WorldSettings> worlds = new ArrayList<WorldSettings>(plugin.catalog().all());
        List<Integer> slots = s.getIntegerList("cases");
        int perPage = Math.max(1, slots.size());
        pages = Math.max(1, (worlds.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < slots.size() && page * perPage + i < worlds.size(); i++) {
            item(slots.get(i), worlds.get(page * perPage + i));
        }
        Object[] vars = vars();
        ConfigurationSection items = s.getConfigurationSection("objets");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                setConfigured(items.getConfigurationSection(key), vars);
            }
        }
        if (page > 0) {
            setConfigured(s.getConfigurationSection("precedent"), vars);
        }
        if (page < pages - 1) {
            setConfigured(s.getConfigurationSection("suivant"), vars);
        }
    }

    private void item(int slot, final WorldSettings s) {
        final World world = Bukkit.getWorld(s.getName());
        ConfigurationSection template = section().getConfigurationSection(world != null ? "mondes.charge" : "mondes.decharge");
        if (template == null) {
            return;
        }
        String icon = s.getIcon().isEmpty() ? defaultIcon(s.getEnvironment()) : s.getIcon();
        String env = s.getEnvironment().name().toLowerCase(Locale.ROOT);
        Object[] kv = {
                "monde", Text.color(s.getAlias()),
                "id", s.getName(),
                "icone", icon,
                "environnement", plugin.msg().text("values.environment-" + env),
                "type", s.getType().name(),
                "generateur", s.getGenerator().isEmpty() ? plugin.msg().text("values.default") : s.getGenerator(),
                "graine", s.getSeed().isEmpty() ? plugin.msg().text("values.none") : s.getSeed(),
                "joueurs", world == null ? 0 : world.getPlayers().size(),
                "limite", s.getPlayerLimit() == 0 ? plugin.msg().text("values.unlimited") : String.valueOf(s.getPlayerLimit()),
                "pvp", plugin.msg().yesNo(s.isPvp()),
                "difficulte", plugin.msg().text("values.difficulty-" + s.getDifficulty().name().toLowerCase(Locale.ROOT)),
                "mode", s.getGameMode() == null ? plugin.msg().text("values.free")
                        : plugin.msg().text("values.gamemode-" + s.getGameMode().name().toLowerCase(Locale.ROOT)),
                "acces", s.getPermission().isEmpty() ? plugin.msg().text("values.free") : s.getPermission(),
                "prix", s.getPrice() <= 0 ? plugin.msg().text("values.free") : Text.number(s.getPrice(), 2),
                "principal", world != null && WorldManager.isMainWorld(world) ? plugin.msg().text("values.main-world") : "",
                "label", plugin.commands().getLabel()
        };
        set(slot, Items.fromSection(template, kv), clickHandler(template, new CustomAction() {
            @Override
            public boolean run(String type, String value) {
                return action(type, s);
            }
        }, kv));
    }

    private static String defaultIcon(World.Environment environment) {
        if (environment == World.Environment.NETHER) {
            return "NETHERRACK";
        }
        if (environment == World.Environment.THE_END) {
            return "ENDER_STONE";
        }
        return "GRASS";
    }

    private boolean action(String type, WorldSettings s) {
        if (type.equals("TELEPORTER")) {
            if (!allowed("tp")) {
                return true;
            }
            World world = Bukkit.getWorld(s.getName());
            if (world == null) {
                plugin.msg().send(player, "world.not-loaded", "monde", Text.color(s.getAlias()), "id", s.getName());
                return true;
            }
            player.closeInventory();
            plugin.worlds().teleport(player, world);
            return true;
        }
        if (type.equals("EDITER")) {
            plugin.menus().openWorldEditor(player, s.getName());
            return true;
        }
        if (type.equals("CHARGER") || type.equals("DECHARGER")) {
            boolean load = type.equals("CHARGER");
            if (!allowed(load ? "charger" : "decharger")) {
                return true;
            }
            WorldManager.Result result = load ? plugin.worlds().load(s.getName()) : plugin.worlds().unload(s.getName(), true);
            plugin.report(player, result, load ? "admin.loaded" : "admin.unloaded", "monde", s.getName());
            refreshLater();
            return true;
        }
        return false;
    }

    private boolean allowed(String commandId) {
        SubCommand command = plugin.commands().get(commandId);
        if (command == null || command.hasPermission(player)) {
            return true;
        }
        plugin.msg().send(player, "general.no-permission");
        return false;
    }
}
