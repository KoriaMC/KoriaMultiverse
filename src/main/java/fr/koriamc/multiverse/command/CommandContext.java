package fr.koriamc.multiverse.command;

import fr.koriamc.multiverse.KoriaMultiverse;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

/**
 * Contexte d'exécution d'une sous-commande.
 */
public final class CommandContext {

    public final KoriaMultiverse plugin;
    public final CommandSender sender;
    /** Libellé de la commande principale (mv, mondes...). */
    public final String label;
    public final String subLabel;
    public final String[] args;
    /** Joueur ayant tapé la commande, ou null (console). */
    public final Player player;

    CommandContext(KoriaMultiverse plugin, CommandSender sender, String label, String subLabel, String[] args) {
        this.plugin = plugin;
        this.sender = sender;
        this.label = label;
        this.subLabel = subLabel;
        this.args = args;
        this.player = sender instanceof Player ? (Player) sender : null;
    }

    public int size() {
        return args.length;
    }

    public String arg(int index) {
        return index >= 0 && index < args.length ? args[index] : null;
    }

    /** Envoie un message en ajoutant automatiquement la variable {label}. */
    public void msg(String key, Object... kv) {
        Object[] all = Arrays.copyOf(kv, kv.length + 2);
        all[kv.length] = "label";
        all[kv.length + 1] = label;
        plugin.msg().send(sender, key, all);
    }
}
