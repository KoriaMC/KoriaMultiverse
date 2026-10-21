package fr.koriamc.multiverse.hook;

import fr.koriamc.multiverse.KoriaMultiverse;

/**
 * Pont vers PlaceholderAPI, chargé uniquement si le plugin est installé.
 */
public final class PapiHook {

    private final WorldsExpansion expansion;

    public PapiHook(KoriaMultiverse plugin) {
        this.expansion = new WorldsExpansion(plugin);
        this.expansion.register();
    }

    public void unregister() {
        try {
            expansion.unregister();
        } catch (Throwable ignored) {
            // PlaceholderAPI déjà arrêté
        }
    }
}
