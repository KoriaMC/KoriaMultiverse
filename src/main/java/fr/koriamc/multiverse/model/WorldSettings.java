package fr.koriamc.multiverse.model;

import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Propriétés d'un monde géré, lues dans worlds.json. Le nom de l'entrée est le nom
 * du dossier du monde (sensible à la casse).
 */
public final class WorldSettings {

    /** Position de spawn précise (avec l'orientation), absente = spawn du monde. */
    public static final class Spawn {
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;

        Spawn(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private final String name;
    private String alias;
    private String icon;
    private World.Environment environment;
    private WorldType type;
    private String generator;
    private String seed;
    private boolean structures;
    private boolean autoLoad;
    private boolean keepSpawnLoaded;
    private boolean autoSave;
    private boolean pvp;
    private Difficulty difficulty;
    private GameMode gameMode;
    private boolean allowFlight;
    private boolean weather;
    private long time;
    private boolean hunger;
    private boolean monsters;
    private boolean animals;
    private String permission;
    private double price;
    private int playerLimit;
    private String respawnWorld;
    private String netherTarget;
    private String endTarget;
    private double scale;
    private double borderSize;
    private double borderX;
    private double borderZ;
    private Spawn spawn;
    private Map<String, String> gameRules = new LinkedHashMap<String, String>();

    private WorldSettings(String name) {
        this.name = name;
    }

    /**
     * Lit un monde de worlds.json.
     *
     * @param problems liste alimentée avec les erreurs de configuration
     */
    public static WorldSettings read(String name, ConfigurationSection s, List<String> problems) {
        String where = "worlds.json : " + name;
        WorldSettings w = new WorldSettings(name);
        w.alias = s.getString("alias", "").trim();
        if (w.alias.isEmpty()) {
            w.alias = name;
        }
        w.icon = s.getString("icone", "");
        w.environment = parseEnvironment(s.getString("environnement", "NORMAL"));
        if (w.environment == null) {
            problems.add(where + ".environnement inconnu (NORMAL, NETHER ou THE_END)");
            w.environment = World.Environment.NORMAL;
        }
        w.type = parseType(s.getString("type", "NORMAL"));
        if (w.type == null) {
            problems.add(where + ".type inconnu (NORMAL, FLAT, LARGE_BIOMES ou AMPLIFIED)");
            w.type = WorldType.NORMAL;
        }
        w.generator = s.getString("generateur", "").trim();
        w.seed = s.getString("graine", "").trim();
        w.structures = s.getBoolean("structures", true);
        w.autoLoad = s.getBoolean("charger-au-demarrage", true);
        w.keepSpawnLoaded = s.getBoolean("garder-spawn-en-memoire", true);
        w.autoSave = s.getBoolean("sauvegarde-auto", true);
        w.pvp = s.getBoolean("pvp", true);
        w.difficulty = parseDifficulty(s.getString("difficulte", "NORMAL"));
        if (w.difficulty == null) {
            problems.add(where + ".difficulte inconnue (PEACEFUL, EASY, NORMAL ou HARD)");
            w.difficulty = Difficulty.NORMAL;
        }
        String mode = s.getString("mode-de-jeu", "").trim();
        w.gameMode = mode.isEmpty() ? null : parseGameMode(mode);
        if (!mode.isEmpty() && w.gameMode == null) {
            problems.add(where + ".mode-de-jeu inconnu (SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR ou vide)");
        }
        w.allowFlight = s.getBoolean("vol-autorise", false);
        w.weather = s.getBoolean("meteo", true);
        w.time = s.getLong("heure", -1);
        w.hunger = s.getBoolean("faim", true);
        w.monsters = s.getBoolean("monstres", true);
        w.animals = s.getBoolean("animaux", true);
        w.permission = s.getString("acces.permission", "").trim();
        w.price = Math.max(0, s.getDouble("acces.prix", 0));
        w.playerLimit = Math.max(0, s.getInt("acces.limite-joueurs", 0));
        w.respawnWorld = s.getString("reapparition", "").trim();
        w.netherTarget = s.getString("portails.nether", "").trim();
        w.endTarget = s.getString("portails.end", "").trim();
        w.scale = s.getDouble("portails.echelle", w.environment == World.Environment.NETHER ? 8.0 : 1.0);
        if (w.scale <= 0) {
            problems.add(where + ".portails.echelle doit être supérieure à 0");
            w.scale = 1.0;
        }
        w.borderSize = Math.max(0, s.getDouble("bordure.taille", 0));
        w.borderX = s.getDouble("bordure.centre-x", 0);
        w.borderZ = s.getDouble("bordure.centre-z", 0);
        ConfigurationSection spawn = s.getConfigurationSection("spawn");
        if (spawn != null && spawn.contains("x") && spawn.contains("y") && spawn.contains("z")) {
            w.spawn = new Spawn(spawn.getDouble("x"), spawn.getDouble("y"), spawn.getDouble("z"),
                    (float) spawn.getDouble("yaw"), (float) spawn.getDouble("pitch"));
        }
        ConfigurationSection rules = s.getConfigurationSection("regles");
        if (rules != null) {
            for (String rule : rules.getKeys(false)) {
                w.gameRules.put(rule, String.valueOf(rules.get(rule)));
            }
        }
        w.gameRules = Collections.unmodifiableMap(w.gameRules);
        return w;
    }

    // ============================================================== Lecture

    public static World.Environment parseEnvironment(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (v.equals("END") || v.equals("FIN")) {
            return World.Environment.THE_END;
        }
        if (v.equals("NORMAL") || v.equals("OVERWORLD")) {
            return World.Environment.NORMAL;
        }
        if (v.equals("NETHER") || v.equals("ENFER")) {
            return World.Environment.NETHER;
        }
        try {
            return World.Environment.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static WorldType parseType(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (v.equals("PLAT")) {
            return WorldType.FLAT;
        }
        if (v.equals("LARGEBIOMES") || v.equals("GRANDS_BIOMES")) {
            return WorldType.LARGE_BIOMES;
        }
        if (v.equals("AMPLIFIE")) {
            return WorldType.AMPLIFIED;
        }
        try {
            return WorldType.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Difficulty parseDifficulty(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (v.equals("PAISIBLE")) {
            return Difficulty.PEACEFUL;
        }
        if (v.equals("FACILE")) {
            return Difficulty.EASY;
        }
        if (v.equals("DIFFICILE")) {
            return Difficulty.HARD;
        }
        try {
            return Difficulty.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static GameMode parseGameMode(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (v.equals("SURVIE") || v.equals("0")) {
            return GameMode.SURVIVAL;
        }
        if (v.equals("CREATIF") || v.equals("1")) {
            return GameMode.CREATIVE;
        }
        if (v.equals("AVENTURE") || v.equals("2")) {
            return GameMode.ADVENTURE;
        }
        if (v.equals("SPECTATEUR") || v.equals("3")) {
            v = "SPECTATOR";
        }
        try {
            return GameMode.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ============================================================ Accesseurs

    /** Nom du monde (dossier). */
    public String getName() {
        return name;
    }

    /** Nom affiché (codes couleur non traduits). */
    public String getAlias() {
        return alias;
    }

    /** Matériau de l'icône dans les menus (vide = selon l'environnement). */
    public String getIcon() {
        return icon;
    }

    public World.Environment getEnvironment() {
        return environment;
    }

    public WorldType getType() {
        return type;
    }

    /** Générateur ("VIDE", "Plugin:id" ou vide pour le générateur de Minecraft). */
    public String getGenerator() {
        return generator;
    }

    /** Graine sous forme de texte (vide = aléatoire à la création). */
    public String getSeed() {
        return seed;
    }

    public boolean isStructures() {
        return structures;
    }

    public boolean isAutoLoad() {
        return autoLoad;
    }

    public boolean isKeepSpawnLoaded() {
        return keepSpawnLoaded;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public boolean isPvp() {
        return pvp;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    /** Mode de jeu imposé à l'arrivée, ou null pour ne rien imposer. */
    public GameMode getGameMode() {
        return gameMode;
    }

    public boolean isAllowFlight() {
        return allowFlight;
    }

    /** Pluie et orages autorisés. */
    public boolean isWeather() {
        return weather;
    }

    /** Heure figée en ticks (0 à 23999), ou -1 pour le cycle normal. */
    public long getTime() {
        return time;
    }

    public boolean isHunger() {
        return hunger;
    }

    public boolean isMonsters() {
        return monsters;
    }

    public boolean isAnimals() {
        return animals;
    }

    /** Permission requise pour entrer (vide = libre). */
    public String getPermission() {
        return permission;
    }

    /** Prix d'entrée via Vault (0 = gratuit). */
    public double getPrice() {
        return price;
    }

    /** Nombre maximum de joueurs (0 = illimité). */
    public int getPlayerLimit() {
        return playerLimit;
    }

    /** Monde où réapparaître après une mort sans lit (vide = ce monde). */
    public String getRespawnWorld() {
        return respawnWorld;
    }

    /** Monde de destination des portails du Nether (vide = comportement de Minecraft). */
    public String getNetherTarget() {
        return netherTarget;
    }

    /** Monde de destination des portails de l'End (vide = comportement de Minecraft). */
    public String getEndTarget() {
        return endTarget;
    }

    /** Échelle des coordonnées pour les portails (1 pour un monde normal, 8 pour le Nether). */
    public double getScale() {
        return scale;
    }

    /** Diamètre de la bordure (0 = pas de bordure). */
    public double getBorderSize() {
        return borderSize;
    }

    public double getBorderX() {
        return borderX;
    }

    public double getBorderZ() {
        return borderZ;
    }

    /** Spawn précis enregistré avec /mv setspawn, ou null. */
    public Spawn getSpawn() {
        return spawn;
    }

    /** Règles du jeu appliquées au chargement (doFireTick, keepInventory...). */
    public Map<String, String> getGameRules() {
        return gameRules;
    }
}
