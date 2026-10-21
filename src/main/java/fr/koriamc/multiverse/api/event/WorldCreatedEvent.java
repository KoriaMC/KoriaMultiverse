package fr.koriamc.multiverse.api.event;

import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Appelé après la création ou l'import d'un monde par KoriaMultiverse (non annulable).
 */
public final class WorldCreatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final boolean imported;

    public WorldCreatedEvent(World world, boolean imported) {
        this.world = world;
        this.imported = imported;
    }

    public World getWorld() {
        return world;
    }

    /** true si le monde existait déjà sur le disque (/mv import). */
    public boolean isImported() {
        return imported;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
