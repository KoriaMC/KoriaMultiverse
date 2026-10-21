package fr.koriamc.multiverse.hook;

import fr.koriamc.multiverse.KoriaMultiverse;
import fr.koriamc.multiverse.model.WorldSettings;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Variables PlaceholderAPI :
 * <ul>
 *   <li>%koriamultiverse_monde% (nom affiché du monde du joueur), %koriamultiverse_monde_id%,
 *       %koriamultiverse_joueurs% (joueurs dans son monde)</li>
 *   <li>%koriamultiverse_mondes% (mondes chargés)</li>
 *   <li>%koriamultiverse_alias_&lt;monde&gt;%, %koriamultiverse_joueurs_&lt;monde&gt;%,
 *       %koriamultiverse_charge_&lt;monde&gt;%</li>
 * </ul>
 */
final class WorldsExpansion extends PlaceholderExpansion {

    private final KoriaMultiverse plugin;

    WorldsExpansion(KoriaMultiverse plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "koriamultiverse";
    }

    @Override
    public String getAuthor() {
        return "KoriaMC";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("mondes")) {
            return String.valueOf(Bukkit.getWorlds().size());
        }
        if (key.startsWith("alias_")) {
            return plugin.worlds().alias(name(params, "alias_"));
        }
        if (key.startsWith("joueurs_")) {
            World world = plugin.worlds().findWorld(name(params, "joueurs_"));
            return world == null ? "0" : String.valueOf(world.getPlayers().size());
        }
        if (key.startsWith("charge_")) {
            return String.valueOf(plugin.worlds().findWorld(name(params, "charge_")) != null);
        }
        if (player == null) {
            return "";
        }
        World world = player.getWorld();
        if (key.equals("monde")) {
            return plugin.worlds().alias(world.getName());
        }
        if (key.equals("monde_id")) {
            return world.getName();
        }
        if (key.equals("joueurs")) {
            return String.valueOf(world.getPlayers().size());
        }
        if (key.equals("pvp")) {
            WorldSettings s = plugin.catalog().get(world);
            return String.valueOf(s == null ? world.getPVP() : s.isPvp());
        }
        return null;
    }

    /** Nom du monde après le préfixe, en gardant la casse d'origine (les dossiers y sont sensibles). */
    private static String name(String params, String prefix) {
        return params.substring(prefix.length());
    }
}
