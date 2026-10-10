package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/** /rime -- see what the cold is doing, learn the recipe, and (for admins) start, stop or test. */
final class RimeCommand implements TabExecutor {

    private final RimeConfig cfg;
    private final HordeDirector director;
    private final RimeItems items;
    private final SeasonBridge seasons;
    private final RealmsBridge realms;
    private final LongSupplier clock;
    private final Runnable reload;

    RimeCommand(RimeConfig cfg, HordeDirector director, RimeItems items, SeasonBridge seasons,
                RealmsBridge realms, LongSupplier clock, Runnable reload) {
        this.cfg = cfg;
        this.director = director;
        this.items = items;
        this.seasons = seasons;
        this.realms = realms;
        this.clock = clock;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> status(sender);
            case "recipe" -> recipe(sender, args);
            case "start" -> start(sender, args);
            case "stop" -> {
                if (!admin(sender)) {
                    return true;
                }
                int n = director.stopAll();
                sender.sendMessage(ColorText.translate("&7Ended " + n + " horde(s)."));
            }
            case "give" -> give(sender, args);
            case "reload" -> {
                if (!admin(sender)) {
                    return true;
                }
                reload.run();
                sender.sendMessage(ColorText.translate("&aNexusRime reloaded."));
            }
            default -> sender.sendMessage(ColorText.translate("&7/rime [status|recipe [item]]" + (sender.hasPermission("nexusrime.admin")
                    ? " &8| &7start [player] [knight|walker], stop, give [player] [item] [amount], reload" : "")));
        }
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("nexusrime.admin")) {
            return true;
        }
        sender.sendMessage(ColorText.translate("&cYou don't have permission for that."));
        return false;
    }

    private void status(CommandSender sender) {
        List<Horde> hordes = director.hordes();
        if (hordes.isEmpty()) {
            sender.sendMessage(ColorText.translate("&b" + cfg.eventName + " &7is quiet. No " + cfg.walkerName + " or " + cfg.knightName + " walks right now."));
        }
        for (Horde h : hordes) {
            World w = Bukkit.getWorld(h.worldName);
            int alive = h.wights.size();
            String where = "";
            if (sender instanceof Player p && h.walker != null && Bukkit.getEntity(h.walker) != null) {
                double d = HordeDirector.distance(p.getLocation(), Bukkit.getEntity(h.walker).getLocation());
                if (d != Double.MAX_VALUE) {
                    where = " &7(" + Math.round(d) + " blocks from you)";
                }
            }
            sender.sendMessage(ColorText.translate("&b" + director.leaderName(h) + " &7is hunting &f" + h.targetName + " &7in "
                    + (w == null ? h.worldName : w.getName()) + " with " + alive + " " + cfg.wightName + where));
        }
        if (sender.hasPermission("nexusrime.admin")) {
            SeasonStatus season = seasons.status();
            long left = GateRules.cooldownMinutesLeft(cfg, clock.getAsLong(), director.lastEventMillis());
            sender.sendMessage(ColorText.translate("&8Season: " + season.name().toLowerCase(Locale.ROOT)
                    + " | claims: " + (realms.available() ? "NexusRealms" : "spawn only")
                    + " | next event allowed in: " + (left == 0 ? "now" : left + " min")));
        }
    }

    /** Item words players can type: shard, blade, dagger, spear, arrow. */
    private static String idFrom(String word) {
        if (word == null) {
            return null;
        }
        String w = word.toLowerCase(Locale.ROOT);
        if (w.equals("sword") || w.equals("weapon")) {
            w = RimeItems.BLADE;
        } else if (w.equals("arrows")) {
            w = RimeItems.ARROW;
        } else if (w.equals("shards") || w.equals("dragonglass") || w.equals("glass")) {
            w = RimeItems.SHARD;
        }
        return RimeConfig.GEAR_IDS.contains(w) ? w : null;
    }

    private void recipe(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorText.translate("&b" + cfg.shardName + " &7and the gear made from it are crafted at a crafting table."));
            for (String id : RimeConfig.GEAR_IDS) {
                RimeConfig.Gear g = cfg.gear.get(id);
                sender.sendMessage(ColorText.translate("&8  /rime recipe " + id + " &7- " + cfg.nameOf(id)
                        + (g.recipeEnabled ? "" : " &c(no recipe on this server)")));
            }
            sender.sendMessage(ColorText.translate("&7Only dragonglass truly wounds the " + cfg.walkerName
                    + ". Fire hurts it too. Wear leather and stand by a fire to keep the cold off."));
            return;
        }
        String id = idFrom(args[1]);
        if (id == null) {
            sender.sendMessage(ColorText.translate("&cNo such item. Try: " + String.join(", ", RimeConfig.GEAR_IDS)));
            return;
        }
        RimeConfig.Gear g = cfg.gear.get(id);
        if (!g.recipeEnabled || !g.recipe.valid()) {
            sender.sendMessage(ColorText.translate("&c" + cfg.nameOf(id) + " cannot be crafted on this server."));
            return;
        }
        sender.sendMessage(ColorText.translate("&b" + cfg.nameOf(id) + " &7(makes " + g.recipe.amount + ", craft it at a crafting table):"));
        if (g.recipe.isShaped()) {
            for (String row : g.recipe.shape) {
                StringBuilder line = new StringBuilder("&8  ");
                for (char c : row.toCharArray()) {
                    if (c == ' ') {
                        line.append("[ ] ");
                    } else {
                        line.append("[").append(ingredientName(g.recipe.ingredients.getOrDefault(c, "?"))).append("] ");
                    }
                }
                sender.sendMessage(ColorText.translate(line.toString()));
            }
        } else {
            StringBuilder line = new StringBuilder("&8  Anywhere in the grid: ");
            for (String name : g.recipe.loose) {
                line.append("[").append(ingredientName(name)).append("] ");
            }
            sender.sendMessage(ColorText.translate(line.toString()));
        }
    }

    private String ingredientName(String raw) {
        return RecipeSpec.SHARD.equalsIgnoreCase(raw) ? cfg.shardName : RimeConfig.titleCase(raw);
    }

    private void start(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        Player wanted = null;
        Boolean knight = null;
        boolean named = false;
        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase(Locale.ROOT);
            if (a.equals("knight") || a.equals("frostknight")) {
                knight = Boolean.TRUE;
            } else if (a.equals("walker") || a.equals("plain")) {
                knight = Boolean.FALSE;
            } else {
                named = true;
                wanted = Bukkit.getPlayerExact(args[i]);
                if (wanted == null) {
                    sender.sendMessage(ColorText.translate("&cNo player named " + args[i] + " is online."));
                    return;
                }
            }
        }
        if (!named && sender instanceof Player p) {
            wanted = p;
        }
        if (wanted == null) {
            sender.sendMessage(ColorText.translate("&cName a player: /rime start <player> [knight|walker]"));
            return;
        }
        World world = wanted.getLocation().getWorld();
        HordeDirector.StartResult result = director.tryStart(world, wanted, true, knight);
        if (result.horde() != null) {
            sender.sendMessage(ColorText.translate("&aA " + director.leaderName(result.horde()) + " rises and marches on " + wanted.getName() + "."));
        } else {
            sender.sendMessage(ColorText.translate("&cNo horde: " + explain(result.verdict())));
        }
    }

    static String explain(GateVerdict v) {
        return switch (v) {
            case OK -> "ok";
            case DISABLED -> "events are switched off in config.yml.";
            case WORLD_NOT_ALLOWED -> "that world is not in events.worlds.";
            case WRONG_SEASON -> "it is not winter.";
            case SEASON_UNKNOWN -> "the season plugin could not be reached.";
            case TOO_MANY_ACTIVE -> "a horde is already out (max-concurrent).";
            case COOLDOWN -> "still in the cooldown.";
            case NOT_ENOUGH_PLAYERS -> "not enough eligible players.";
            case NO_TARGET -> "that player cannot be hunted (safe ground, creative, dead or elsewhere).";
            case NO_ROLL -> "the dice said no.";
            case NO_SPAWN_SITE -> "no safe place to raise the horde near them.";
            case VETOED -> "another plugin vetoed it.";
        };
    }

    /** /rime give [player] [item] [amount] -- the player and the item may come in either order. */
    private void give(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        Player to = null;
        String id = RimeItems.BLADE;
        int amount = 1;
        boolean named = false;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            String asId = idFrom(a);
            if (asId != null) {
                id = asId;
                continue;
            }
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(a)));
                continue;
            } catch (NumberFormatException notNumber) {
                // fall through: it must be a player name
            }
            named = true;
            to = Bukkit.getPlayerExact(a);
            if (to == null) {
                sender.sendMessage(ColorText.translate("&cNo player named " + a + " is online."));
                return;
            }
        }
        if (!named && sender instanceof Player p) {
            to = p;
        }
        if (to == null) {
            sender.sendMessage(ColorText.translate("&cName an online player: /rime give <player> [item] [amount]"));
            return;
        }
        to.getInventory().addItem(items.make(id, amount));
        sender.sendMessage(ColorText.translate("&aGave " + amount + " x " + cfg.nameOf(id) + " to " + to.getName() + "."));
    }

    private static void addMatching(List<String> out, List<String> options, String typed) {
        for (String o : options) {
            if (o.startsWith(typed.toLowerCase(Locale.ROOT))) {
                out.add(o);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("status", "recipe")) {
                out.add(s);
            }
            if (sender.hasPermission("nexusrime.admin")) {
                out.addAll(List.of("start", "stop", "give", "reload"));
            }
            out.removeIf(s -> !s.startsWith(args[0].toLowerCase(Locale.ROOT)));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("recipe")) {
            addMatching(out, RimeConfig.GEAR_IDS, args[1]);
        } else if (args.length >= 2 && sender.hasPermission("nexusrime.admin") && args[0].equalsIgnoreCase("give")) {
            String last = args[args.length - 1];
            addMatching(out, RimeConfig.GEAR_IDS, last);
            if (args.length == 2) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(last.toLowerCase(Locale.ROOT))) {
                        out.add(p.getName());
                    }
                }
            }
        } else if (args.length >= 2 && sender.hasPermission("nexusrime.admin") && args[0].equalsIgnoreCase("start")) {
            String last = args[args.length - 1].toLowerCase(Locale.ROOT);
            addMatching(out, List.of("knight", "walker"), last);
            if (args.length == 2) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) {
                        out.add(p.getName());
                    }
                }
            }
        }
        return out;
    }
}
