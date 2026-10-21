package fr.koriamc.multiverse.world;

import fr.koriamc.multiverse.KoriaMultiverse;
import fr.koriamc.multiverse.api.event.WorldCreatedEvent;
import fr.koriamc.multiverse.config.JsonFile;
import fr.koriamc.multiverse.model.WorldSettings;
import fr.koriamc.multiverse.util.MaterialSet;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

/**
 * Création, import, chargement, déchargement, suppression, clonage et
 * régénération des mondes, application de leurs propriétés et téléportation.
 */
@SuppressWarnings("deprecation")
public final class WorldManager {

    /** Résultat d'une opération sur un monde. */
    public enum Result {
        OK,
        /** Nom de monde refusé (lettres, chiffres, - et _ uniquement). */
        NOM_INVALIDE,
        /** Un monde ou un dossier porte déjà ce nom. */
        EXISTE_DEJA,
        /** Aucun dossier de monde à ce nom. */
        DOSSIER_ABSENT,
        /** Le monde n'est pas dans worlds.json. */
        INCONNU,
        DEJA_CHARGE,
        NON_CHARGE,
        /** Le monde principal du serveur ne peut pas être déchargé ni supprimé. */
        MONDE_PRINCIPAL,
        /** Le générateur demandé n'existe pas. */
        GENERATEUR_INCONNU,
        /** Minecraft a refusé l'opération (voir la console). */
        ECHEC
    }

    /** Fin d'une opération asynchrone (copie ou suppression de dossier). */
    public interface Callback {
        void done(Result result);
    }

    /** Fichiers qui ne doivent jamais être copiés avec un monde. */
    private static final Set<String> SKIPPED_FILES = new HashSet<String>(Arrays.asList("uid.dat", "session.lock"));

    private final KoriaMultiverse plugin;
    /** Mondes dont l'heure a été figée par le plugin (pour relancer le cycle si l'option est retirée). */
    private final Set<String> frozen = new HashSet<String>();
    /** Mondes dont la bordure a été posée par le plugin. */
    private final Set<String> bordered = new HashSet<String>();
    /** Mondes où la règle doWeatherCycle vaut false (météo figée). */
    private final Set<String> weatherStopped = new HashSet<String>();

    /**
     * Règle doWeatherCycle : elle n'existe qu'à partir de Minecraft 1.11, le plugin la gère
     * lui-même (false = la météo actuelle ne change plus d'elle-même ; /weather marche toujours).
     */
    public static final String WEATHER_CYCLE = "doWeatherCycle";

    /** Règles ajoutées par le plugin, absentes de Minecraft 1.8. */
    public static final List<String> VIRTUAL_RULES = Collections.singletonList(WEATHER_CYCLE);

    public WorldManager(KoriaMultiverse plugin) {
        this.plugin = plugin;
    }

    /** Nom exact d'une règle gérée par le plugin (doWeatherCycle), ou null. */
    public static String virtualRule(String rule) {
        for (String virtual : VIRTUAL_RULES) {
            if (virtual.equalsIgnoreCase(rule)) {
                return virtual;
            }
        }
        return null;
    }

    /** Règles connues d'un monde, y compris celles gérées par le plugin. */
    public static List<String> ruleNames(World world) {
        List<String> names = new ArrayList<String>(Arrays.asList(world.getGameRules()));
        names.addAll(VIRTUAL_RULES);
        return names;
    }

    /**
     * Garde la météo actuelle des mondes où doWeatherCycle vaut false : le compte à rebours
     * avant le prochain changement de météo est repoussé en permanence (appelé toutes les 5 s).
     */
    public void tickWeather() {
        for (String name : weatherStopped) {
            World world = Bukkit.getWorld(name);
            if (world != null) {
                freezeWeather(world);
            }
        }
    }

    private static void freezeWeather(World world) {
        int far = 20 * 60 * 60 * 24;
        if (world.getWeatherDuration() < far / 2) {
            world.setWeatherDuration(far);
        }
        if (world.getThunderDuration() < far / 2) {
            world.setThunderDuration(far);
        }
    }

    // ================================================================ Démarrage

    /**
     * Ajoute à worlds.json les mondes chargés par le serveur (monde principal, Nether,
     * End, mondes d'autres plugins) avec leurs propriétés actuelles.
     *
     * @return le nombre de mondes ajoutés
     */
    public int registerLoadedWorlds() {
        int added = 0;
        for (World world : Bukkit.getWorlds()) {
            if (plugin.catalog().contains(world.getName())) {
                continue;
            }
            Map<String, Object> entry = entry(world.getEnvironment(), world.getWorldType(), "",
                    String.valueOf(world.getSeed()), world.canGenerateStructures());
            entry.put("pvp", world.getPVP());
            entry.put("difficulte", world.getDifficulty().name());
            entry.put("garder-spawn-en-memoire", world.getKeepSpawnInMemory());
            entry.put("sauvegarde-auto", world.isAutoSave());
            entry.put("monstres", world.getAllowMonsters());
            entry.put("animaux", world.getAllowAnimals());
            plugin.worldsFile().set(Collections.singletonList(world.getName()), entry);
            added++;
        }
        if (added > 0) {
            saveConfig();
        }
        return added;
    }

    /** Charge les mondes de worlds.json marqués charger-au-demarrage. */
    public void loadAll() {
        for (WorldSettings settings : new ArrayList<WorldSettings>(plugin.catalog().all())) {
            if (!settings.isAutoLoad() || Bukkit.getWorld(settings.getName()) != null) {
                continue;
            }
            if (!folder(settings.getName()).isDirectory()) {
                plugin.getLogger().warning("Monde " + settings.getName() + " introuvable (dossier absent) : il n'est pas chargé.");
                continue;
            }
            Result result = load(settings.getName());
            if (result != Result.OK) {
                plugin.getLogger().warning("Monde " + settings.getName() + " non chargé : " + result.name());
            }
        }
    }

    /** Applique les propriétés de worlds.json à tous les mondes chargés et aux joueurs qui s'y trouvent. */
    public void applyAll() {
        for (World world : Bukkit.getWorlds()) {
            WorldSettings settings = plugin.catalog().get(world);
            if (settings == null) {
                continue;
            }
            apply(world, settings);
            for (Player player : world.getPlayers()) {
                applyPlayer(player, settings);
            }
        }
    }

    /** Propriétés du monde : PvP, difficulté, apparition des créatures, météo, heure, règles, bordure, spawn. */
    public void apply(World world, WorldSettings s) {
        world.setPVP(s.isPvp());
        world.setDifficulty(s.getDifficulty());
        world.setSpawnFlags(s.isMonsters(), s.isAnimals());
        world.setKeepSpawnInMemory(s.isKeepSpawnLoaded());
        world.setAutoSave(s.isAutoSave());
        if (!s.isWeather()) {
            world.setStorm(false);
            world.setThundering(false);
        }
        if (s.getTime() >= 0) {
            world.setGameRuleValue("doDaylightCycle", "false");
            world.setTime(s.getTime() % 24000L);
            frozen.add(world.getName());
        } else if (frozen.remove(world.getName())) {
            world.setGameRuleValue("doDaylightCycle", "true");
        }
        String weatherCycle = null;
        for (Map.Entry<String, String> rule : s.getGameRules().entrySet()) {
            if (virtualRule(rule.getKey()) != null) {
                weatherCycle = rule.getValue();
                continue;
            }
            if (!world.isGameRule(rule.getKey())) {
                plugin.getLogger().warning("worlds.json : " + world.getName() + ".regles." + rule.getKey()
                        + " n'est pas une règle du jeu connue.");
                continue;
            }
            world.setGameRuleValue(rule.getKey(), rule.getValue());
        }
        if ("false".equalsIgnoreCase(weatherCycle)) {
            weatherStopped.add(world.getName());
            freezeWeather(world);
        } else {
            weatherStopped.remove(world.getName());
        }
        WorldBorder border = world.getWorldBorder();
        if (s.getBorderSize() > 0) {
            border.setCenter(s.getBorderX(), s.getBorderZ());
            border.setSize(s.getBorderSize());
            bordered.add(world.getName());
        } else if (bordered.remove(world.getName())) {
            border.reset();
        }
        if (s.getSpawn() != null) {
            world.setSpawnLocation((int) Math.floor(s.getSpawn().x), (int) Math.floor(s.getSpawn().y),
                    (int) Math.floor(s.getSpawn().z));
        }
    }

    /** Mode de jeu imposé et vol, appliqués à l'arrivée dans le monde. */
    public void applyPlayer(Player player, WorldSettings s) {
        GameMode mode = s.getGameMode();
        if (mode != null && player.getGameMode() != mode && !player.hasPermission("koriamultiverse.bypass.modedejeu")) {
            player.setGameMode(mode);
        }
        boolean creative = player.getGameMode() == GameMode.CREATIVE || player.getGameMode().name().equals("SPECTATOR");
        if (!s.isAllowFlight() && !creative && !player.hasPermission("koriamultiverse.bypass.vol") && player.getAllowFlight()) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    // ========================================================= Création / import

    /**
     * Crée un nouveau monde et l'ajoute à worlds.json.
     *
     * @param seed      graine (vide = aléatoire)
     * @param generator "VIDE", "Plugin:id" ou vide
     */
    public Result create(String name, World.Environment environment, WorldType type, String seed, String generator,
                         boolean structures) {
        if (!isValidName(name)) {
            return Result.NOM_INVALIDE;
        }
        if (Bukkit.getWorld(name) != null || folder(name).exists() || plugin.catalog().contains(name)) {
            return Result.EXISTE_DEJA;
        }
        WorldCreator creator = new WorldCreator(name).environment(environment).type(type).generateStructures(structures);
        if (seed != null && !seed.isEmpty()) {
            creator.seed(parseSeed(seed));
        }
        if (generator != null && !generator.isEmpty()) {
            ChunkGenerator chunkGenerator = generator(generator, name);
            if (chunkGenerator == null) {
                return Result.GENERATEUR_INCONNU;
            }
            creator.generator(chunkGenerator);
        }
        World world = createWorld(creator);
        if (world == null) {
            return Result.ECHEC;
        }
        if (isVoid(generator)) {
            prepareVoidSpawn(world);
        }
        Map<String, Object> entry = entry(environment, type, generator == null ? "" : generator,
                String.valueOf(world.getSeed()), structures);
        plugin.worldsFile().set(Collections.singletonList(name), entry);
        saveConfig();
        WorldSettings settings = plugin.catalog().get(name);
        if (settings != null) {
            apply(world, settings);
        }
        Bukkit.getPluginManager().callEvent(new WorldCreatedEvent(world, false));
        return Result.OK;
    }

    /** Ajoute à worlds.json un monde dont le dossier existe déjà, puis le charge. */
    public Result importWorld(String name, World.Environment environment, String generator) {
        if (!isValidName(name)) {
            return Result.NOM_INVALIDE;
        }
        if (plugin.catalog().contains(name)) {
            return Result.EXISTE_DEJA;
        }
        World world = Bukkit.getWorld(name);
        if (world == null) {
            if (!isWorldFolder(folder(name))) {
                return Result.DOSSIER_ABSENT;
            }
            WorldCreator creator = new WorldCreator(name).environment(environment);
            if (generator != null && !generator.isEmpty()) {
                ChunkGenerator chunkGenerator = generator(generator, name);
                if (chunkGenerator == null) {
                    return Result.GENERATEUR_INCONNU;
                }
                creator.generator(chunkGenerator);
            }
            world = createWorld(creator);
            if (world == null) {
                return Result.ECHEC;
            }
        }
        Map<String, Object> entry = entry(world.getEnvironment(), world.getWorldType(), generator == null ? "" : generator,
                String.valueOf(world.getSeed()), world.canGenerateStructures());
        plugin.worldsFile().set(Collections.singletonList(name), entry);
        saveConfig();
        WorldSettings settings = plugin.catalog().get(name);
        if (settings != null) {
            apply(world, settings);
        }
        Bukkit.getPluginManager().callEvent(new WorldCreatedEvent(world, true));
        return Result.OK;
    }

    // ===================================================== Chargement / déchargement

    /** Charge un monde de worlds.json. */
    public Result load(String name) {
        WorldSettings s = plugin.catalog().get(name);
        if (s == null) {
            return Result.INCONNU;
        }
        if (Bukkit.getWorld(s.getName()) != null) {
            return Result.DEJA_CHARGE;
        }
        if (!folder(s.getName()).isDirectory()) {
            return Result.DOSSIER_ABSENT;
        }
        WorldCreator creator = creator(s);
        if (creator == null) {
            return Result.GENERATEUR_INCONNU;
        }
        World world = createWorld(creator);
        if (world == null) {
            return Result.ECHEC;
        }
        apply(world, s);
        return Result.OK;
    }

    /** Décharge un monde après avoir renvoyé ses joueurs au spawn du monde principal. */
    public Result unload(String name, boolean save) {
        World world = findWorld(name);
        if (world == null) {
            return Result.NON_CHARGE;
        }
        if (isMainWorld(world)) {
            return Result.MONDE_PRINCIPAL;
        }
        evacuate(world);
        return Bukkit.unloadWorld(world, save) ? Result.OK : Result.ECHEC;
    }

    /** Retire un monde de worlds.json (et le décharge) sans toucher à ses fichiers. */
    public Result remove(String name) {
        WorldSettings s = plugin.catalog().get(name);
        if (s == null) {
            return Result.INCONNU;
        }
        World world = Bukkit.getWorld(s.getName());
        if (world != null && isMainWorld(world)) {
            return Result.MONDE_PRINCIPAL;
        }
        if (world != null && unload(s.getName(), true) != Result.OK) {
            return Result.ECHEC;
        }
        plugin.worldsFile().remove(Collections.singletonList(s.getName()));
        saveConfig();
        return Result.OK;
    }

    // ================================================= Suppression / copie

    /** Décharge le monde, le retire de worlds.json et supprime son dossier (en arrière-plan). */
    public Result delete(String name, final Callback callback) {
        if (!isValidName(name)) {
            return Result.NOM_INVALIDE;
        }
        WorldSettings s = plugin.catalog().get(name);
        final String worldName = s != null ? s.getName() : name;
        World world = Bukkit.getWorld(worldName);
        if (world != null && isMainWorld(world)) {
            return Result.MONDE_PRINCIPAL;
        }
        final File folder = folder(worldName);
        // Hors worlds.json, seul un vrai dossier de monde (avec level.dat) peut être supprimé.
        if (s == null && world == null && !isWorldFolder(folder)) {
            return Result.INCONNU;
        }
        if (world != null && unload(worldName, false) != Result.OK) {
            return Result.ECHEC;
        }
        if (s != null) {
            plugin.worldsFile().remove(Collections.singletonList(worldName));
            saveConfig();
        }
        async(new Runnable() {
            @Override
            public void run() {
                final boolean ok = deleteFolder(folder);
                sync(new Runnable() {
                    @Override
                    public void run() {
                        callback.done(ok ? Result.OK : Result.ECHEC);
                    }
                });
            }
        });
        return Result.OK;
    }

    /** Copie un monde sous un nouveau nom (mêmes propriétés), puis charge la copie. */
    public Result cloneWorld(String source, final String target, final Callback callback) {
        final WorldSettings s = plugin.catalog().get(source);
        if (s == null) {
            return Result.INCONNU;
        }
        if (!isValidName(target)) {
            return Result.NOM_INVALIDE;
        }
        if (Bukkit.getWorld(target) != null || folder(target).exists() || plugin.catalog().contains(target)) {
            return Result.EXISTE_DEJA;
        }
        final World world = Bukkit.getWorld(s.getName());
        final boolean autoSave = world != null && world.isAutoSave();
        if (world != null) {
            world.save();
            world.setAutoSave(false);
        }
        final File from = folder(s.getName());
        final File to = folder(target);
        async(new Runnable() {
            @Override
            public void run() {
                final boolean ok = copyFolder(from, to);
                sync(new Runnable() {
                    @Override
                    public void run() {
                        if (world != null) {
                            world.setAutoSave(autoSave);
                        }
                        if (!ok) {
                            callback.done(Result.ECHEC);
                            return;
                        }
                        Object original = plugin.worldsFile().getEffective(Collections.singletonList(s.getName()));
                        Map<String, Object> entry = original instanceof Map ? deepCopy(JsonFile.castMap(original))
                                : entry(s.getEnvironment(), s.getType(), s.getGenerator(), s.getSeed(), s.isStructures());
                        entry.put("alias", target);
                        entry.remove("spawn");
                        plugin.worldsFile().set(Collections.singletonList(target), entry);
                        saveConfig();
                        callback.done(load(target));
                    }
                });
            }
        });
        return Result.OK;
    }

    /**
     * Régénère un monde : même nom et mêmes propriétés, terrain neuf.
     *
     * @param seed graine à utiliser, "aleatoire" pour une nouvelle graine, null pour garder la même
     */
    public Result regenerate(String name, final String seed, final Callback callback) {
        final WorldSettings s = plugin.catalog().get(name);
        if (s == null) {
            return Result.INCONNU;
        }
        World world = Bukkit.getWorld(s.getName());
        if (world != null && isMainWorld(world)) {
            return Result.MONDE_PRINCIPAL;
        }
        if (world != null && unload(s.getName(), false) != Result.OK) {
            return Result.ECHEC;
        }
        final File folder = folder(s.getName());
        async(new Runnable() {
            @Override
            public void run() {
                final boolean ok = deleteFolder(folder);
                sync(new Runnable() {
                    @Override
                    public void run() {
                        if (!ok) {
                            callback.done(Result.ECHEC);
                            return;
                        }
                        String newSeed = seed == null ? s.getSeed()
                                : (seed.equalsIgnoreCase("random") || seed.equalsIgnoreCase("aleatoire")) ? String.valueOf(new Random().nextLong()) : seed;
                        plugin.worldsFile().set(Arrays.asList(s.getName(), "graine"), newSeed);
                        plugin.worldsFile().remove(Arrays.asList(s.getName(), "spawn"));
                        saveConfig();
                        folder.mkdirs();
                        Result result = load(s.getName());
                        World created = Bukkit.getWorld(s.getName());
                        if (result == Result.OK && created != null && isVoid(s.getGenerator())) {
                            prepareVoidSpawn(created);
                        }
                        callback.done(result);
                    }
                });
            }
        });
        return Result.OK;
    }

    // ================================================================== Spawn

    /** Enregistre la position comme spawn du monde (avec l'orientation). */
    public void setSpawn(Location location) {
        World world = location.getWorld();
        world.setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Map<String, Object> spawn = new LinkedHashMap<String, Object>();
        spawn.put("x", round(location.getX()));
        spawn.put("y", round(location.getY()));
        spawn.put("z", round(location.getZ()));
        spawn.put("yaw", round(location.getYaw()));
        spawn.put("pitch", round(location.getPitch()));
        if (!plugin.catalog().contains(world.getName())) {
            registerLoadedWorlds();
        }
        WorldSettings s = plugin.catalog().get(world);
        plugin.worldsFile().set(Arrays.asList(s == null ? world.getName() : s.getName(), "spawn"), spawn);
        saveConfig();
    }

    /** Spawn du monde : position enregistrée avec /mv setspawn, sinon celle de Minecraft. */
    public Location spawn(World world) {
        WorldSettings s = plugin.catalog().get(world);
        if (s != null && s.getSpawn() != null) {
            return new Location(world, s.getSpawn().x, s.getSpawn().y, s.getSpawn().z, s.getSpawn().yaw, s.getSpawn().pitch);
        }
        Location spawn = world.getSpawnLocation();
        return new Location(world, spawn.getBlockX() + 0.5, spawn.getY(), spawn.getBlockZ() + 0.5);
    }

    /** Position sûre la plus proche (pieds et tête libres, sol solide), ou la position d'origine. */
    public Location safe(Location location) {
        if (!plugin.settings().safeTeleport || location.getWorld() == null) {
            return location;
        }
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int start = Math.max(1, Math.min(254, location.getBlockY()));
        for (int dy = 0; dy < 255; dy++) {
            int[] candidates = dy == 0 ? new int[]{start} : new int[]{start + dy, start - dy};
            for (int y : candidates) {
                if (y >= 1 && y <= 254 && isSafe(world, x, y, z)) {
                    return new Location(world, location.getX(), y, location.getZ(), location.getYaw(), location.getPitch());
                }
            }
        }
        return location;
    }

    private static boolean isSafe(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        return passable(feet) && passable(head) && ground.getType().isSolid() && !dangerous(ground.getType());
    }

    private static boolean passable(Block block) {
        Material type = block.getType();
        return !type.isSolid() && !dangerous(type);
    }

    private static boolean dangerous(Material type) {
        return type == Material.LAVA || type == Material.STATIONARY_LAVA || type == Material.FIRE
                || type == Material.CACTUS || type == Material.WEB;
    }

    /** Téléporte le joueur au spawn du monde (les conditions d'accès sont vérifiées par le listener). */
    public boolean teleport(Player player, World world) {
        return player.teleport(safe(spawn(world)));
    }

    /** Renvoie tous les joueurs d'un monde au spawn du monde principal. */
    public void evacuate(World world) {
        World main = Bukkit.getWorlds().get(0);
        for (Player player : new ArrayList<Player>(world.getPlayers())) {
            player.teleport(safe(spawn(main)));
            plugin.msg().send(player, "world.evacuated", "monde", alias(world.getName()));
        }
    }

    // ================================================================ Outils

    /** Nom affiché d'un monde (alias coloré, sinon son nom). */
    public String alias(String name) {
        WorldSettings s = plugin.catalog().get(name);
        return s == null ? name : fr.koriamc.multiverse.util.Text.color(s.getAlias());
    }

    /** Monde chargé à ce nom (sans tenir compte de la casse), ou null. */
    public World findWorld(String name) {
        if (name == null) {
            return null;
        }
        World exact = Bukkit.getWorld(name);
        if (exact != null) {
            return exact;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equalsIgnoreCase(name)) {
                return world;
            }
        }
        return null;
    }

    public static boolean isMainWorld(World world) {
        return !Bukkit.getWorlds().isEmpty() && Bukkit.getWorlds().get(0).equals(world);
    }

    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,40}");
    }

    public static File folder(String name) {
        return new File(Bukkit.getWorldContainer(), name);
    }

    /** Un dossier de monde contient level.dat. */
    public static boolean isWorldFolder(File folder) {
        return folder.isDirectory() && new File(folder, "level.dat").isFile();
    }

    /** Dossiers de mondes présents sur le disque mais absents de worlds.json. */
    public List<String> importableFolders() {
        List<String> names = new ArrayList<String>();
        File[] files = Bukkit.getWorldContainer().listFiles();
        if (files == null) {
            return names;
        }
        for (File file : files) {
            if (isWorldFolder(file) && !plugin.catalog().contains(file.getName())) {
                names.add(file.getName());
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** "VIDE", "Plugin:id" ou "Plugin". @return le générateur, ou null s'il est inconnu */
    public ChunkGenerator generator(String spec, String worldName) {
        if (isVoid(spec)) {
            return new VoidGenerator(plugin.settings().voidSpawnHeight);
        }
        return WorldCreator.getGeneratorForName(worldName, spec, Bukkit.getConsoleSender());
    }

    public static boolean isVoid(String spec) {
        if (spec == null) {
            return false;
        }
        String v = spec.trim().toUpperCase(Locale.ROOT);
        return v.equals("VIDE") || v.equals("VOID") || v.equals("KORIAMULTIVERSE:VIDE") || v.equals("KORIAMULTIVERSE:VOID");
    }

    /** Graine de Minecraft : un nombre, sinon le hash du texte. */
    public static long parseSeed(String seed) {
        try {
            return Long.parseLong(seed.trim());
        } catch (NumberFormatException e) {
            return seed.hashCode();
        }
    }

    private WorldCreator creator(WorldSettings s) {
        WorldCreator creator = new WorldCreator(s.getName()).environment(s.getEnvironment()).type(s.getType())
                .generateStructures(s.isStructures());
        if (!s.getSeed().isEmpty()) {
            creator.seed(parseSeed(s.getSeed()));
        }
        if (!s.getGenerator().isEmpty()) {
            ChunkGenerator generator = generator(s.getGenerator(), s.getName());
            if (generator == null) {
                return null;
            }
            creator.generator(generator);
        }
        return creator;
    }

    private World createWorld(WorldCreator creator) {
        try {
            return Bukkit.createWorld(creator);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de charger le monde " + creator.name(), e);
            return null;
        }
    }

    /** Bloc sous le spawn d'un monde vide, pour ne pas tomber dans le vide. */
    private void prepareVoidSpawn(World world) {
        int y = plugin.settings().voidSpawnHeight;
        int[] material = MaterialSet.parse(plugin.settings().voidSpawnBlock);
        if (material != null && material[0] > 0) {
            world.getBlockAt(0, y, 0).setTypeIdAndData(material[0], (byte) Math.max(0, material[1]), false);
        }
        world.setSpawnLocation(0, y + 1, 0);
    }

    /** Entrée de worlds.json d'un nouveau monde : modèle nouveaux-mondes de config.json + propriétés de création. */
    private Map<String, Object> entry(World.Environment environment, WorldType type, String generator, String seed,
                                      boolean structures) {
        Map<String, Object> entry = new LinkedHashMap<String, Object>();
        entry.put("alias", "");
        entry.put("icone", "");
        entry.put("environnement", environment.name());
        entry.put("type", type == null ? "NORMAL" : type.name());
        entry.put("generateur", generator == null ? "" : generator);
        entry.put("graine", seed == null ? "" : seed);
        entry.put("structures", structures);
        Object template = plugin.configFile().getEffective(Collections.singletonList("nouveaux-mondes"));
        if (template instanceof Map) {
            entry.putAll(deepCopy(JsonFile.castMap(template)));
        }
        if (environment == World.Environment.NETHER) {
            Object portals = entry.get("portails");
            if (portals instanceof Map) {
                JsonFile.castMap(portals).put("echelle", 8.0);
            }
        }
        return entry;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> map) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object value = e.getValue();
            if (value instanceof Map) {
                value = deepCopy((Map<String, Object>) value);
            } else if (value instanceof List) {
                value = new ArrayList<Object>((List<Object>) value);
            }
            copy.put(e.getKey(), value);
        }
        return copy;
    }

    /** Écrit worlds.json et recharge la configuration (les mondes chargés reçoivent leurs propriétés). */
    public boolean saveConfig() {
        try {
            plugin.worldsFile().save();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible d'écrire worlds.json", e);
            return false;
        }
        for (String problem : plugin.reload()) {
            plugin.getLogger().warning(problem);
        }
        return true;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void async(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    private void sync(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private boolean deleteFolder(File folder) {
        if (!folder.exists()) {
            return true;
        }
        File[] children = folder.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!deleteFolder(child)) {
                    return false;
                }
            }
        }
        if (!folder.delete()) {
            plugin.getLogger().warning("Suppression impossible : " + folder.getPath());
            return false;
        }
        return true;
    }

    private boolean copyFolder(File from, File to) {
        if (from.isDirectory()) {
            if (!to.exists() && !to.mkdirs()) {
                plugin.getLogger().warning("Création impossible : " + to.getPath());
                return false;
            }
            File[] children = from.listFiles();
            if (children == null) {
                return true;
            }
            for (File child : children) {
                if (SKIPPED_FILES.contains(child.getName())) {
                    continue;
                }
                if (!copyFolder(child, new File(to, child.getName()))) {
                    return false;
                }
            }
            return true;
        }
        try {
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Copie impossible de " + from.getPath() + " : " + e.getMessage());
            return false;
        }
    }
}
