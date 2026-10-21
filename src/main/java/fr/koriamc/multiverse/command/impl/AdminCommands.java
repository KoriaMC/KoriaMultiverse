package fr.koriamc.multiverse.command.impl;

import fr.koriamc.multiverse.KoriaMultiverse;
import fr.koriamc.multiverse.command.CommandContext;
import fr.koriamc.multiverse.command.SubCommand;
import fr.koriamc.multiverse.config.JsonFile;
import fr.koriamc.multiverse.model.WorldSettings;
import fr.koriamc.multiverse.util.Text;
import fr.koriamc.multiverse.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Commandes d'administration : téléportation, création, import, chargement,
 * suppression, clonage, régénération, spawn, propriétés et règles du jeu.
 */
public final class AdminCommands {

    private static final List<String> ENVIRONMENTS = Arrays.asList("NORMAL", "NETHER", "THE_END");
    private static final List<String> FLAGS = Arrays.asList("-t", "-s", "-g", "-a");
    private static final long CONFIRM_MS = 10000L;
    /** Opérations destructrices en attente de confirmation : expéditeur -> "commande:monde" et instant. */
    private static final Map<String, String> CONFIRM_TARGET = new HashMap<String, String>();
    private static final Map<String, Long> CONFIRM_TIME = new HashMap<String, Long>();

    private AdminCommands() {
    }

    /** true si la même commande a été tapée il y a moins de 10 secondes (sinon demande confirmation). */
    private static boolean confirmed(CommandContext ctx, String token) {
        String who = ctx.sender.getName();
        Long time = CONFIRM_TIME.get(who);
        if (token.equals(CONFIRM_TARGET.get(who)) && time != null && System.currentTimeMillis() - time <= CONFIRM_MS) {
            CONFIRM_TARGET.remove(who);
            CONFIRM_TIME.remove(who);
            return true;
        }
        CONFIRM_TARGET.put(who, token);
        CONFIRM_TIME.put(who, System.currentTimeMillis());
        return false;
    }

    /** Options -t type, -s graine, -g générateur, -a structures (true/false) à partir de l'argument {@code from}. */
    private static Map<String, String> flags(CommandContext ctx, int from) {
        Map<String, String> flags = new HashMap<String, String>();
        for (int i = from; i < ctx.size(); i++) {
            String arg = ctx.arg(i).toLowerCase(Locale.ROOT);
            if (FLAGS.contains(arg) && i + 1 < ctx.size()) {
                flags.put(arg, ctx.arg(i + 1));
                i++;
            }
        }
        return flags;
    }

    /** Autocomplétion des options de création. */
    private static List<String> completeFlags(CommandContext ctx) {
        String previous = ctx.arg(ctx.size() - 2);
        String current = ctx.arg(ctx.size() - 1);
        if ("-t".equalsIgnoreCase(previous)) {
            return Text.complete(current, Arrays.asList("NORMAL", "FLAT", "LARGE_BIOMES", "AMPLIFIED"));
        }
        if ("-g".equalsIgnoreCase(previous)) {
            return Text.complete(current, Collections.singletonList("VOID"));
        }
        if ("-a".equalsIgnoreCase(previous)) {
            return Text.complete(current, Arrays.asList("yes", "no"));
        }
        if ("-s".equalsIgnoreCase(previous)) {
            return Collections.emptyList();
        }
        return Text.complete(current, FLAGS);
    }

    // ============================================================ Téléportation

    /** /mv tp &lt;world&gt; [player] */
    public static final class Teleport extends SubCommand {
        public Teleport(KoriaMultiverse plugin) {
            super(plugin, "tp", false, 1, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            World world = plugin.worlds().findWorld(ctx.arg(0));
            if (world == null) {
                ctx.msg(plugin.catalog().contains(ctx.arg(0)) ? "world.not-loaded" : "world.unknown",
                        "monde", ctx.arg(0), "id", ctx.arg(0));
                return;
            }
            Player target = ctx.size() > 1 ? Bukkit.getPlayerExact(ctx.arg(1)) : ctx.player;
            if (target == null) {
                ctx.msg(ctx.size() > 1 ? "general.unknown-player" : "general.players-only", "player", ctx.arg(1));
                return;
            }
            if (target != ctx.player && !ctx.sender.hasPermission("koriamultiverse.admin.tp.autres")) {
                ctx.msg("general.no-permission");
                return;
            }
            String alias = plugin.worlds().alias(world.getName());
            if (!plugin.worlds().teleport(target, world)) {
                if (target != ctx.player) {
                    ctx.msg("world.teleport-refused", "player", target.getName(), "monde", alias);
                }
                return;
            }
            if (target != ctx.player) {
                ctx.msg("world.teleported-other", "player", target.getName(), "monde", alias);
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() == 2) {
                List<String> names = new ArrayList<String>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return Text.complete(ctx.arg(1), names);
            }
            return ctx.size() == 1 ? PlayerCommands.loadedNames(ctx.arg(0)) : Collections.<String>emptyList();
        }
    }

    /** /mv spawn [monde] : spawn du monde actuel ou d'un autre */
    public static final class Spawn extends SubCommand {
        public Spawn(KoriaMultiverse plugin) {
            super(plugin, "spawn", true, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            World world = ctx.size() > 0 ? plugin.worlds().findWorld(ctx.arg(0)) : ctx.player.getWorld();
            if (world == null) {
                ctx.msg("world.not-loaded", "monde", ctx.arg(0), "id", ctx.arg(0));
                return;
            }
            plugin.worlds().teleport(ctx.player, world);
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? PlayerCommands.loadedNames(ctx.arg(0)) : Collections.<String>emptyList();
        }
    }

    /** /mv setspawn : le spawn du monde devient la position du joueur (avec son orientation) */
    public static final class SetSpawn extends SubCommand {
        public SetSpawn(KoriaMultiverse plugin) {
            super(plugin, "setspawn", true, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.worlds().setSpawn(ctx.player.getLocation());
            ctx.msg("admin.spawn-set", "monde", plugin.worlds().alias(ctx.player.getWorld().getName()),
                    "x", ctx.player.getLocation().getBlockX(), "y", ctx.player.getLocation().getBlockY(),
                    "z", ctx.player.getLocation().getBlockZ());
        }
    }

    // ================================================================ Création

    /** /mv create &lt;name&gt; [environment] [-t type] [-s seed] [-g generator] [-a yes|no (structures)] */
    public static final class Create extends SubCommand {
        public Create(KoriaMultiverse plugin) {
            super(plugin, "create", false, 1, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            String name = ctx.arg(0);
            World.Environment environment = World.Environment.NORMAL;
            int flagsFrom = 1;
            if (ctx.size() > 1 && !ctx.arg(1).startsWith("-")) {
                environment = WorldSettings.parseEnvironment(ctx.arg(1));
                if (environment == null) {
                    ctx.msg("admin.unknown-environment", "valeur", ctx.arg(1));
                    return;
                }
                flagsFrom = 2;
            }
            Map<String, String> flags = flags(ctx, flagsFrom);
            WorldType type = flags.containsKey("-t") ? WorldSettings.parseType(flags.get("-t")) : WorldType.NORMAL;
            if (type == null) {
                ctx.msg("admin.unknown-type", "valeur", flags.get("-t"));
                return;
            }
            boolean structures = !flags.containsKey("-a") || Text.parseBoolean(flags.get("-a"), true);
            ctx.msg("admin.creating", "monde", name);
            WorldManager.Result result = plugin.worlds().create(name, environment, type, flags.get("-s"), flags.get("-g"),
                    structures);
            plugin.report(ctx.sender, result, "admin.created", "monde", name, "label", ctx.label);
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() == 2) {
                List<String> options = new ArrayList<String>(ENVIRONMENTS);
                options.addAll(FLAGS);
                return Text.complete(ctx.arg(1), options);
            }
            return ctx.size() > 2 ? completeFlags(ctx) : Collections.<String>emptyList();
        }
    }

    /** /mv import &lt;folder&gt; [environment] [-g generator] */
    public static final class Import extends SubCommand {
        public Import(KoriaMultiverse plugin) {
            super(plugin, "import", false, 1, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            String name = ctx.arg(0);
            World.Environment environment = World.Environment.NORMAL;
            int flagsFrom = 1;
            if (ctx.size() > 1 && !ctx.arg(1).startsWith("-")) {
                environment = WorldSettings.parseEnvironment(ctx.arg(1));
                if (environment == null) {
                    ctx.msg("admin.unknown-environment", "valeur", ctx.arg(1));
                    return;
                }
                flagsFrom = 2;
            }
            ctx.msg("admin.importing", "monde", name);
            WorldManager.Result result = plugin.worlds().importWorld(name, environment, flags(ctx, flagsFrom).get("-g"));
            plugin.report(ctx.sender, result, "admin.imported", "monde", name);
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() == 1) {
                return Text.complete(ctx.arg(0), plugin.worlds().importableFolders());
            }
            if (ctx.size() == 2) {
                return Text.complete(ctx.arg(1), ENVIRONMENTS);
            }
            return completeFlags(ctx);
        }
    }

    /** /mv load &lt;monde&gt; */
    public static final class Load extends SubCommand {
        public Load(KoriaMultiverse plugin) {
            super(plugin, "load", false, 1, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.report(ctx.sender, plugin.worlds().load(ctx.arg(0)), "admin.loaded", "monde", ctx.arg(0));
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() != 1) {
                return Collections.emptyList();
            }
            List<String> unloaded = new ArrayList<String>();
            for (WorldSettings s : plugin.catalog().all()) {
                if (Bukkit.getWorld(s.getName()) == null) {
                    unloaded.add(s.getName());
                }
            }
            return Text.complete(ctx.arg(0), unloaded);
        }
    }

    /** /mv unload &lt;monde&gt; */
    public static final class Unload extends SubCommand {
        public Unload(KoriaMultiverse plugin) {
            super(plugin, "unload", false, 1, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.report(ctx.sender, plugin.worlds().unload(ctx.arg(0), true), "admin.unloaded", "monde", ctx.arg(0));
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? PlayerCommands.loadedNames(ctx.arg(0)) : Collections.<String>emptyList();
        }
    }

    // ============================================================ Suppression

    /** /mv delete &lt;monde&gt; : décharge et efface le dossier (confirmation) */
    public static final class Delete extends SubCommand {
        public Delete(KoriaMultiverse plugin) {
            super(plugin, "delete", false, 1, true);
        }

        @Override
        public void execute(final CommandContext ctx) {
            final String name = ctx.arg(0);
            if (!confirmed(ctx, "supprimer:" + name.toLowerCase(Locale.ROOT))) {
                ctx.msg("admin.confirm-delete", "monde", name);
                return;
            }
            final CommandSender sender = ctx.sender;
            WorldManager.Result result = plugin.worlds().delete(name, new WorldManager.Callback() {
                @Override
                public void done(WorldManager.Result result) {
                    plugin.report(sender, result, "admin.deleted", "monde", name);
                }
            });
            if (result != WorldManager.Result.OK) {
                plugin.report(sender, result, "admin.deleted", "monde", name);
            } else {
                ctx.msg("admin.deleting", "monde", name);
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? PlayerCommands.worldNames(plugin, ctx.arg(0)) : Collections.<String>emptyList();
        }
    }

    /** /mv remove &lt;monde&gt; : ne gère plus le monde, sans toucher à ses fichiers (confirmation) */
    public static final class Remove extends SubCommand {
        public Remove(KoriaMultiverse plugin) {
            super(plugin, "remove", false, 1, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            String name = ctx.arg(0);
            if (!confirmed(ctx, "retirer:" + name.toLowerCase(Locale.ROOT))) {
                ctx.msg("admin.confirm-remove", "monde", name);
                return;
            }
            plugin.report(ctx.sender, plugin.worlds().remove(name), "admin.removed", "monde", name);
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? PlayerCommands.worldNames(plugin, ctx.arg(0)) : Collections.<String>emptyList();
        }
    }

    /** /mv clone &lt;source&gt; &lt;nouveau nom&gt; */
    public static final class Clone extends SubCommand {
        public Clone(KoriaMultiverse plugin) {
            super(plugin, "clone", false, 2, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            final String source = ctx.arg(0);
            final String target = ctx.arg(1);
            final CommandSender sender = ctx.sender;
            WorldManager.Result result = plugin.worlds().cloneWorld(source, target, new WorldManager.Callback() {
                @Override
                public void done(WorldManager.Result result) {
                    plugin.report(sender, result, "admin.cloned", "monde", target, "source", source);
                }
            });
            if (result != WorldManager.Result.OK) {
                plugin.report(sender, result, "admin.cloned", "monde", target, "source", source);
            } else {
                ctx.msg("admin.cloning", "monde", target, "source", source);
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? PlayerCommands.worldNames(plugin, ctx.arg(0)) : Collections.<String>emptyList();
        }
    }

    /** /mv regen &lt;monde&gt; [-s graine|aleatoire] : terrain neuf (confirmation) */
    public static final class Regenerate extends SubCommand {
        public Regenerate(KoriaMultiverse plugin) {
            super(plugin, "regen", false, 1, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            final String name = ctx.arg(0);
            if (!confirmed(ctx, "regenerer:" + name.toLowerCase(Locale.ROOT))) {
                ctx.msg("admin.confirm-regen", "monde", name);
                return;
            }
            final CommandSender sender = ctx.sender;
            WorldManager.Result result = plugin.worlds().regenerate(name, flags(ctx, 1).get("-s"), new WorldManager.Callback() {
                @Override
                public void done(WorldManager.Result result) {
                    plugin.report(sender, result, "admin.regenerated", "monde", name);
                }
            });
            if (result != WorldManager.Result.OK) {
                plugin.report(sender, result, "admin.regenerated", "monde", name);
            } else {
                ctx.msg("admin.regenerating", "monde", name);
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() == 2) {
                return Text.complete(ctx.arg(1), Collections.singletonList("-s"));
            }
            if (ctx.size() == 3 && "-s".equalsIgnoreCase(ctx.arg(1))) {
                return Text.complete(ctx.arg(2), Collections.singletonList("random"));
            }
            return ctx.size() == 1 ? PlayerCommands.worldNames(plugin, ctx.arg(0)) : Collections.<String>emptyList();
        }
    }

    // ============================================================= Propriétés

    /**
     * Noms anglais des options de /mv modify, segment par segment (access.price -> acces.prix).
     * Les clés de worlds.json ne changent pas.
     */
    private static final Map<String, String> OPTION_SEGMENTS = new LinkedHashMap<String, String>();

    static {
        OPTION_SEGMENTS.put("icon", "icone");
        OPTION_SEGMENTS.put("load-on-startup", "charger-au-demarrage");
        OPTION_SEGMENTS.put("keep-spawn-loaded", "garder-spawn-en-memoire");
        OPTION_SEGMENTS.put("autosave", "sauvegarde-auto");
        OPTION_SEGMENTS.put("difficulty", "difficulte");
        OPTION_SEGMENTS.put("gamemode", "mode-de-jeu");
        OPTION_SEGMENTS.put("allow-flight", "vol-autorise");
        OPTION_SEGMENTS.put("weather", "meteo");
        OPTION_SEGMENTS.put("time", "heure");
        OPTION_SEGMENTS.put("hunger", "faim");
        OPTION_SEGMENTS.put("monsters", "monstres");
        OPTION_SEGMENTS.put("animals", "animaux");
        OPTION_SEGMENTS.put("access", "acces");
        OPTION_SEGMENTS.put("price", "prix");
        OPTION_SEGMENTS.put("player-limit", "limite-joueurs");
        OPTION_SEGMENTS.put("respawn-world", "reapparition");
        OPTION_SEGMENTS.put("portals", "portails");
        OPTION_SEGMENTS.put("scale", "echelle");
        OPTION_SEGMENTS.put("border", "bordure");
        OPTION_SEGMENTS.put("size", "taille");
        OPTION_SEGMENTS.put("center-x", "centre-x");
        OPTION_SEGMENTS.put("center-z", "centre-z");
        OPTION_SEGMENTS.put("generator", "generateur");
        OPTION_SEGMENTS.put("seed", "graine");
        OPTION_SEGMENTS.put("environment", "environnement");
    }

    /** Nom tapé (access.price) -> chemin de worlds.json (acces.prix). */
    static String optionPath(String typed) {
        StringBuilder sb = new StringBuilder();
        for (String segment : typed.toLowerCase(Locale.ROOT).split("\\.")) {
            String french = OPTION_SEGMENTS.get(segment);
            sb.append(sb.length() == 0 ? "" : ".").append(french != null ? french : segment);
        }
        return sb.toString();
    }

    /** Chemin de worlds.json (acces.prix) -> nom anglais affiché (access.price). */
    static String optionName(String path) {
        StringBuilder sb = new StringBuilder();
        for (String segment : path.split("\\.")) {
            String english = segment;
            for (Map.Entry<String, String> e : OPTION_SEGMENTS.entrySet()) {
                if (e.getValue().equals(segment)) {
                    english = e.getKey();
                }
            }
            sb.append(sb.length() == 0 ? "" : ".").append(english);
        }
        return sb.toString();
    }

    /** /mv modify &lt;world&gt; &lt;option&gt; &lt;value&gt; (ex : pvp no, access.price 500, portals.nether world_nether) */
    public static final class Modify extends SubCommand {
        public Modify(KoriaMultiverse plugin) {
            super(plugin, "modify", false, 3, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            WorldSettings s = plugin.catalog().get(ctx.arg(0));
            if (s == null) {
                ctx.msg("world.unknown", "monde", ctx.arg(0));
                return;
            }
            String option = optionPath(ctx.arg(1));
            List<String> path = new ArrayList<String>();
            path.add(s.getName());
            path.addAll(Arrays.asList(option.split("\\.")));
            Object current = plugin.worldsFile().getEffective(path);
            if (current == null) {
                current = template(option);
            }
            if (current == null || current instanceof Map || current instanceof List || option.startsWith("regles")) {
                ctx.msg("admin.unknown-option", "option", ctx.arg(1), "options", Text.join(options(), ", "));
                return;
            }
            String raw = Text.join(ctx.args, 2, " ");
            Object value;
            if (current instanceof Boolean) {
                Boolean parsed = parseBoolean(raw);
                if (parsed == null) {
                    ctx.msg("admin.invalid-value", "option", optionName(option), "valeur", raw, "attendu", "yes / no");
                    return;
                }
                value = parsed;
            } else if (current instanceof Number) {
                Double parsed = Text.parseDouble(raw);
                if (parsed == null) {
                    ctx.msg("general.invalid-number", "input", raw);
                    return;
                }
                value = current instanceof Double ? (Object) parsed : (Object) (long) Math.round(parsed);
                if (value instanceof Long && (Long) value >= Integer.MIN_VALUE && (Long) value <= Integer.MAX_VALUE) {
                    value = ((Long) value).intValue();
                }
            } else {
                value = raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase(plugin.msg().text("config-editor.empty-word")) ? "" : raw;
            }
            plugin.worldsFile().set(path, value);
            plugin.worlds().saveConfig();
            ctx.msg("admin.modified", "monde", s.getName(), "option", optionName(option), "valeur", String.valueOf(value));
            for (String problem : plugin.catalog().getProblems()) {
                if (problem.contains(s.getName())) {
                    ctx.msg("general.reload-problem", "problem", problem);
                }
            }
        }

        /** Valeur par défaut d'une option d'après nouveaux-mondes (pour une option absente du monde). */
        private Object template(String option) {
            List<String> path = new ArrayList<String>();
            path.add("nouveaux-mondes");
            path.addAll(Arrays.asList(option.split("\\.")));
            Object value = plugin.configFile().getEffective(path);
            if (value == null && Arrays.asList("alias", "icone", "generateur", "graine").contains(option)) {
                return "";
            }
            return value;
        }

        /** Options modifiables en anglais : celles de nouveaux-mondes, à plat (access.price...). */
        private List<String> options() {
            List<String> paths = new ArrayList<String>(Arrays.asList("alias", "icone", "structures"));
            Object template = plugin.configFile().getEffective(Collections.singletonList("nouveaux-mondes"));
            if (template instanceof Map) {
                flatten(JsonFile.castMap(template), "", paths);
            }
            List<String> out = new ArrayList<String>();
            for (String path : paths) {
                out.add(optionName(path));
            }
            return out;
        }

        private static void flatten(Map<String, Object> map, String prefix, List<String> out) {
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (e.getKey().equals("regles")) {
                    continue;
                }
                if (e.getValue() instanceof Map) {
                    flatten(JsonFile.castMap(e.getValue()), prefix + e.getKey() + ".", out);
                } else {
                    out.add(prefix + e.getKey());
                }
            }
        }

        private static Boolean parseBoolean(String raw) {
            String v = raw.trim().toLowerCase(Locale.ROOT);
            if (v.equals("yes") || v.equals("oui") || v.equals("true") || v.equals("on") || v.equals("1")) {
                return Boolean.TRUE;
            }
            if (v.equals("no") || v.equals("non") || v.equals("false") || v.equals("off") || v.equals("0")) {
                return Boolean.FALSE;
            }
            return null;
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() == 1) {
                return PlayerCommands.worldNames(plugin, ctx.arg(0));
            }
            if (ctx.size() == 2) {
                return Text.complete(ctx.arg(1), options());
            }
            if (ctx.size() == 3) {
                String option = optionPath(ctx.arg(1));
                if (option.equals("difficulte")) {
                    return Text.complete(ctx.arg(2), Arrays.asList("PEACEFUL", "EASY", "NORMAL", "HARD"));
                }
                if (option.equals("mode-de-jeu")) {
                    return Text.complete(ctx.arg(2), Arrays.asList("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR", "none"));
                }
                if (option.startsWith("portails.") && !option.endsWith("echelle") || option.equals("reapparition")) {
                    return PlayerCommands.worldNames(plugin, ctx.arg(2));
                }
                Object template = template(option);
                if (template instanceof Boolean) {
                    return Text.complete(ctx.arg(2), Arrays.asList("yes", "no"));
                }
            }
            return Collections.emptyList();
        }
    }

    /** /mv gamerule &lt;world&gt; &lt;rule&gt; [value] : affiche ou change une règle du jeu, gardée dans worlds.json */
    public static final class GameRule extends SubCommand {
        public GameRule(KoriaMultiverse plugin) {
            super(plugin, "gamerule", false, 2, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            WorldSettings s = plugin.catalog().get(ctx.arg(0));
            if (s == null) {
                ctx.msg("world.unknown", "monde", ctx.arg(0));
                return;
            }
            World world = Bukkit.getWorld(s.getName());
            String rule = ctx.arg(1);
            String virtual = WorldManager.virtualRule(rule);
            if (virtual != null) {
                rule = virtual;
            } else if (world != null && !world.isGameRule(rule)) {
                ctx.msg("admin.unknown-rule", "regle", rule, "regles", Text.join(WorldManager.ruleNames(world), ", "));
                return;
            }
            if (ctx.size() == 2) {
                String value = world != null && virtual == null ? world.getGameRuleValue(rule) : s.getGameRules().get(rule);
                if (value == null && virtual != null) {
                    value = "true";
                }
                ctx.msg("admin.rule-value", "monde", s.getName(), "regle", rule,
                        "valeur", value == null ? plugin.msg().text("values.default") : value);
                return;
            }
            String value = ctx.arg(2);
            if (virtual != null) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    ctx.msg("admin.rule-boolean", "regle", rule);
                    return;
                }
                value = value.toLowerCase(Locale.ROOT);
                // une ancienne entrée écrite avec une autre casse est remplacée
                for (String existing : new ArrayList<String>(s.getGameRules().keySet())) {
                    if (existing.equalsIgnoreCase(rule) && !existing.equals(rule)) {
                        plugin.worldsFile().remove(Arrays.asList(s.getName(), "regles", existing));
                    }
                }
            }
            plugin.worldsFile().set(Arrays.asList(s.getName(), "regles", rule), value);
            plugin.worlds().saveConfig();
            ctx.msg("admin.rule-set", "monde", s.getName(), "regle", rule, "valeur", value);
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() == 1) {
                return PlayerCommands.worldNames(plugin, ctx.arg(0));
            }
            World world = plugin.worlds().findWorld(ctx.arg(0));
            if (ctx.size() == 2 && world != null) {
                return Text.complete(ctx.arg(1), WorldManager.ruleNames(world));
            }
            if (ctx.size() == 3) {
                return Text.complete(ctx.arg(2), Arrays.asList("true", "false"));
            }
            return Collections.emptyList();
        }
    }

    /** /mv config [fichier|monde] */
    public static final class Config extends SubCommand {
        public Config(KoriaMultiverse plugin) {
            super(plugin, "config", true, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            if (ctx.size() > 0 && plugin.catalog().contains(ctx.arg(0))) {
                plugin.menus().openWorldEditor(ctx.player, ctx.arg(0));
                return;
            }
            if (!plugin.menus().openConfig(ctx.player, ctx.arg(0))) {
                ctx.msg("config-editor.unknown-file", "file", ctx.arg(0));
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            List<String> names = new ArrayList<String>();
            for (JsonFile file : plugin.configFiles()) {
                names.add(file.getName().replace(".json", ""));
            }
            names.addAll(plugin.catalog().names());
            return ctx.size() == 1 ? Text.complete(ctx.arg(0), names) : Collections.<String>emptyList();
        }
    }

    /** /mv reload */
    public static final class Reload extends SubCommand {
        public Reload(KoriaMultiverse plugin) {
            super(plugin, "reload", false, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            List<String> problems = plugin.reload();
            ctx.msg("general.reload-done", "mondes", plugin.catalog().count());
            if (!problems.isEmpty()) {
                ctx.msg("general.reload-problems", "count", problems.size());
                for (String problem : problems) {
                    ctx.msg("general.reload-problem", "problem", problem);
                }
            }
        }
    }
}
