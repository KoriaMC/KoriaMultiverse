package fr.koriamc.multiverse;

import fr.koriamc.multiverse.command.CommandManager;
import fr.koriamc.multiverse.config.ConfigDocs;
import fr.koriamc.multiverse.config.JsonFile;
import fr.koriamc.multiverse.config.Messages;
import fr.koriamc.multiverse.config.PortalCatalog;
import fr.koriamc.multiverse.config.Settings;
import fr.koriamc.multiverse.config.WorldCatalog;
import fr.koriamc.multiverse.gui.Menu;
import fr.koriamc.multiverse.gui.MenuManager;
import fr.koriamc.multiverse.hook.PapiHook;
import fr.koriamc.multiverse.hook.VaultHook;
import fr.koriamc.multiverse.listener.DimensionListener;
import fr.koriamc.multiverse.listener.PortalListener;
import fr.koriamc.multiverse.listener.WorldListener;
import fr.koriamc.multiverse.world.PortalManager;
import fr.koriamc.multiverse.world.VoidGenerator;
import fr.koriamc.multiverse.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * KoriaMultiverse : gestion des mondes pour KoriaMC (Spigot 1.8.8), dans l'esprit de
 * Multiverse-Core : création, import, chargement, téléportation, propriétés par monde,
 * portails et monde vide.
 * Voilà le rajout
 */
public final class KoriaMultiverse extends JavaPlugin {

    private static KoriaMultiverse instance;

    // Fichiers
    private JsonFile configFile;
    private JsonFile worldsFile;
    private JsonFile portalsFile;
    private JsonFile messagesFile;
    private JsonFile menusFile;
    private Configuration config = new MemoryConfiguration();
    private Configuration messagesConfig = new MemoryConfiguration();
    private Configuration menusConfig = new MemoryConfiguration();

    // Réglages
    private final Settings settings = new Settings();
    private final WorldCatalog catalog = new WorldCatalog();
    private final PortalCatalog portalCatalog = new PortalCatalog();
    private final Messages messages = new Messages();
    private final ConfigDocs configDocs = new ConfigDocs();

    // Services
    private WorldManager worlds;
    private PortalManager portalManager;
    private MenuManager menus;
    private CommandManager commands;
    private PapiHook papi;
    private VaultHook vault;
    private boolean vaultChecked;

    public static KoriaMultiverse get() {
        return instance;
    }

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        instance = this;

        configFile = new JsonFile(this, "config.json");
        worldsFile = new JsonFile(this, "worlds.json", "*", "*.regles");
        portalsFile = new JsonFile(this, "portals.json", "*");
        messagesFile = new JsonFile(this, "messages.json");
        menusFile = new JsonFile(this, "menus.json", "liste.objets", "editeur.fichiers");

        worlds = new WorldManager(this);
        portalManager = new PortalManager(this);
        menus = new MenuManager(this);
        commands = new CommandManager(this);

        for (String problem : loadConfigs()) {
            getLogger().warning(problem);
        }
        configDocs.load(this);
        configDocs.writeReadme(this, configFiles());
        commands.load(config, messagesConfig);

        int added = worlds.registerLoadedWorlds();
        if (added > 0) {
            getLogger().info(added + " monde(s) du serveur ajouté(s) à worlds.json.");
        }
        worlds.loadAll();
        worlds.applyAll();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            portalManager.refill(world);
        }

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new WorldListener(this), this);
        pm.registerEvents(new PortalListener(this), this);
        pm.registerEvents(new DimensionListener(this), this);
        // règle doWeatherCycle (absente de la 1.8) : météo figée dans les mondes concernés
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                worlds.tickWeather();
            }
        }, 100L, 100L);
        pm.registerEvents(menus, this);
        pm.registerEvents(menus.prompt(), this);
        commands.registerBukkitCommand();

        if (pm.getPlugin("PlaceholderAPI") != null) {
            try {
                papi = new PapiHook(this);
                getLogger().info("PlaceholderAPI détecté : variables %koriamultiverse_...% enregistrées.");
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Impossible de s'accrocher à PlaceholderAPI", t);
                papi = null;
            }
        }
        getLogger().info("KoriaMultiverse activé en " + (System.currentTimeMillis() - start) + " ms ("
                + Bukkit.getWorlds().size() + " monde(s) chargé(s), " + catalog.count() + " géré(s)).");
    }

    @Override
    public void onDisable() {
        if (commands != null) {
            commands.unregisterBukkitCommand();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
        if (papi != null) {
            papi.unregister();
            papi = null;
        }
        instance = null;
    }

    /** Générateur pour bukkit.yml : "generator: KoriaMultiverse:VIDE". */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (id == null || id.isEmpty() || WorldManager.isVoid(id)) {
            // réglages pas encore lus si l'appel arrive avant l'activation
            return new VoidGenerator(settings.voidSpawnHeight > 0 ? settings.voidSpawnHeight : 64);
        }
        getLogger().warning("Générateur inconnu pour " + worldName + " : " + id + " (VIDE disponible).");
        return null;
    }

    /** @return les problèmes à signaler (erreurs JSON, mondes invalides) */
    private List<String> loadConfigs() {
        List<String> problems = new ArrayList<String>();
        for (JsonFile file : configFiles()) {
            String error = file.load();
            if (error != null) {
                problems.add(file.getName() + " : " + error);
            }
        }
        config = configFile.toConfiguration();
        messagesConfig = messagesFile.toConfiguration();
        menusConfig = menusFile.toConfiguration();

        settings.load(config, getLogger());
        messages.load(messagesConfig, settings);
        catalog.load(worldsFile.toConfiguration());
        problems.addAll(catalog.getProblems());
        portalCatalog.load(portalsFile.toConfiguration());
        problems.addAll(portalCatalog.getProblems());
        return problems;
    }

    /**
     * Recharge toute la configuration et réapplique les propriétés des mondes chargés
     * (/mv reload, éditeur, commandes qui modifient worlds.json).
     *
     * @return les problèmes détectés, à afficher à l'auteur de la commande
     */
    public List<String> reload() {
        List<String> problems = loadConfigs();
        if (commands != null) {
            commands.load(config, messagesConfig);
        }
        if (worlds != null) {
            worlds.applyAll();
        }
        return problems;
    }

    /**
     * Message de résultat d'une opération : {@code okKey} en cas de succès, sinon le
     * message d'erreur commun result.&lt;code&gt;.
     */
    public void report(CommandSender to, WorldManager.Result result, String okKey, Object... kv) {
        String key = result == WorldManager.Result.OK ? okKey : "result." + result.name().toLowerCase(Locale.ROOT);
        Object[] all = Arrays.copyOf(kv, kv.length + 2);
        all[kv.length] = "label";
        all[kv.length + 1] = commands.getLabel();
        messages.send(to, key, all);
    }

    /** Économie Vault pour les prix d'entrée, ou null si Vault ou une économie manque. */
    public VaultHook economy() {
        if (vault == null && !vaultChecked && Bukkit.getPluginManager().getPlugin("Vault") != null) {
            vaultChecked = true;
            vault = VaultHook.create();
        }
        return vault;
    }

    // ============================================================ Accesseurs

    public Settings settings() {
        return settings;
    }

    public WorldCatalog catalog() {
        return catalog;
    }

    public Messages msg() {
        return messages;
    }

    /** Explication des options (éditeur /mv config et LISEZMOI.txt). */
    public ConfigDocs configDocs() {
        return configDocs;
    }

    public Configuration config() {
        return config;
    }

    public Configuration menusConfig() {
        return menusConfig;
    }

    /** Fichiers de configuration, dans l'ordre de l'éditeur. */
    public List<JsonFile> configFiles() {
        return Arrays.asList(configFile, worldsFile, portalsFile, messagesFile, menusFile);
    }

    public JsonFile configFile() {
        return configFile;
    }

    /** Mondes gérés, modifié par les commandes et l'éditeur. */
    public JsonFile worldsFile() {
        return worldsFile;
    }

    public WorldManager worlds() {
        return worlds;
    }

    /** Portails personnalisés de portals.json. */
    public PortalCatalog portals() {
        return portalCatalog;
    }

    public PortalManager portalManager() {
        return portalManager;
    }

    /** Portails personnalisés, modifié par /mv portal et l'éditeur. */
    public JsonFile portalsFile() {
        return portalsFile;
    }

    public MenuManager menus() {
        return menus;
    }

    public CommandManager commands() {
        return commands;
    }
}
