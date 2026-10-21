package fr.koriamc.multiverse.gui;

import fr.koriamc.multiverse.KoriaMultiverse;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * /mv config : choix du fichier. Objets de menus.json (editeur.fichiers), en
 * général avec l'action MENU:config:&lt;fichier&gt;.
 */
final class ConfigFilesMenu extends Menu {

    ConfigFilesMenu(KoriaMultiverse plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected ConfigurationSection section() {
        return plugin.menus().section("editeur");
    }

    @Override
    protected String titleTemplate() {
        return section().getString("titre-fichiers", "");
    }

    @Override
    protected int rows() {
        return section().getInt("lignes-fichiers", 3);
    }

    @Override
    protected void render() {
        ConfigurationSection items = section().getConfigurationSection("fichiers");
        if (items == null) {
            return;
        }
        Object[] kv = vars();
        for (String key : items.getKeys(false)) {
            setConfigured(items.getConfigurationSection(key), kv);
        }
    }
}
