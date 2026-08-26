package me.foesio.foKoTH.command;

import me.foesio.core.command.FoAdminArguments;
import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.foKoTH.FoKoTH;
import me.foesio.foKoTH.config.KothConfig;
import me.foesio.foKoTH.koth.ActiveKoth;
import me.foesio.foKoTH.koth.KothManager;
import me.foesio.foKoTH.koth.KothMode;
import me.foesio.foKoTH.koth.Selection;
import me.foesio.foKoTH.util.TimeUtil;
import me.foesio.foKoTH.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class KothCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_STARTER_GIVE_AMOUNT = 64 * 36;
    private static final String UNKNOWN_COMMAND_FALLBACK = "{prefix}{bad}Unknown command. {muted}Use {theme}{usage}";
    static final List<String> SUBCOMMANDS = List.of(
        "wand",
        "create",
        "delete",
        "start",
        "stop",
        "stopall",
        "editor",
        "tp",
        "list",
        "info",
        "givestarter",
        "reload",
        "resetwins"
    );
    private static final String ROOT_USAGE = "/fokothadmin <version|reload|editor|list|info|start|stop>";

    private final FoKoTH plugin;
    private final InventoryDepositService inventoryDeposit = InventoryDepositService.create();

    public KothCommand(FoKoTH plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fokoth.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender, ROOT_USAGE);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "wand" -> wand(sender);
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "start" -> start(sender, args);
            case "stop" -> stop(sender, args);
            case "stopall" -> {
                plugin.kothManager().stopAll(true);
                plugin.messages().send(sender, "koth.stopped-all");
            }
            case "editor" -> editor(sender, args);
            case "tp" -> teleport(sender, args);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "givestarter" -> giveStarter(sender, args);
            case "reload" -> {
                FoReloadResult result = plugin.reloadFoKoTH();
                if (result.successful()) {
                    plugin.messages().send(sender, "general.reloaded");
                    if (sender instanceof Player player) {
                        plugin.adminSounds().reload(player);
                    }
                } else {
                    plugin.messages().send(sender, "general.reload-failed", Map.of(
                        "step", result.failedStep(),
                        "error", result.errorMessage()
                    ));
                    if (sender instanceof Player player) {
                        plugin.adminSounds().reloadError(player);
                    }
                }
            }
            case "resetwins" -> resetWins(sender, args);
            default -> sendUsage(sender, ROOT_USAGE);
        }
        return true;
    }

    static boolean hasSubcommand(String input) {
        return SUBCOMMANDS.contains(input.toLowerCase(Locale.ROOT));
    }

    private void wand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        plugin.kothManager().giveWand(player);
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        if (args.length < 3 || !TextUtil.validId(args[1])) {
            sendUsage(sender, "/fokothadmin create <name> <CAPTURE/SCORE>");
            return;
        }
        KothMode mode;
        try {
            mode = KothMode.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.messages().send(sender, "general.invalid-mode");
            return;
        }
        Selection selection = plugin.kothManager().selection(player);
        if (!selection.complete()) {
            plugin.messages().send(player, "koth.wand-missing");
            return;
        }
        String id = TextUtil.id(args[1]);
        if (plugin.kothManager().koth(id) != null) {
            plugin.messages().send(player, "editor.already-exists", Map.of("entry", id));
            return;
        }
        plugin.kothManager().create(id, mode, selection.zone());
        plugin.messages().send(player, "koth.created", Map.of("koth", id));
        plugin.sounds().play(player, "koth.created");
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/fokothadmin delete <name>");
            return;
        }
        if (plugin.kothManager().delete(args[1])) {
            plugin.messages().send(sender, "koth.deleted", Map.of("koth", args[1]));
            if (sender instanceof Player player) {
                plugin.sounds().play(player, "koth.deleted");
            }
        } else {
            plugin.messages().send(sender, "general.invalid-koth", Map.of("koth", args[1]));
        }
    }

    private void start(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/fokothadmin start <name>");
            return;
        }
        if (plugin.kothManager().koth(args[1]) == null) {
            plugin.messages().send(sender, "general.invalid-koth", Map.of("koth", args[1]));
            return;
        }
        if (plugin.kothManager().isActive(args[1])) {
            plugin.messages().send(sender, "koth.already-running", Map.of("koth", args[1]));
            return;
        }
        if (plugin.kothManager().start(args[1], null, KothManager.StartReason.COMMAND)) {
            plugin.messages().send(sender, "koth.started", Map.of("koth", args[1]));
        } else {
            plugin.messages().send(sender, "general.invalid-koth", Map.of("koth", args[1]));
        }
    }

    private void stop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/fokothadmin stop <name>");
            return;
        }
        if (plugin.kothManager().stop(args[1], true)) {
            plugin.messages().send(sender, "koth.stopped", Map.of("koth", args[1]));
        } else {
            plugin.messages().send(sender, "koth.not-running", Map.of("koth", args[1]));
        }
    }

    private void editor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        if (args.length >= 2 && plugin.kothManager().koth(args[1]) != null) {
            plugin.editorManager().openKoth(player, args[1]);
        } else {
            plugin.editorManager().openMain(player);
        }
    }

    private void teleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        if (args.length < 2 || plugin.kothManager().koth(args[1]) == null) {
            plugin.messages().send(sender, "general.invalid-koth", Map.of("koth", args.length < 2 ? "" : args[1]));
            return;
        }
        KothConfig koth = plugin.kothManager().koth(args[1]);
        if (koth.zone().center() != null) {
            player.teleport(koth.zone().center());
            plugin.messages().send(player, "koth.teleported", Map.of("koth", koth.id()));
            plugin.sounds().play(player, "koth.teleported");
        }
    }

    private void list(CommandSender sender) {
        if (plugin.kothManager().allKoths().isEmpty()) {
            plugin.messages().send(sender, "koth.list-empty");
            return;
        }
        String names = plugin.kothManager().allKoths().stream().map(KothConfig::id).collect(Collectors.joining(", "));
        plugin.messages().send(sender, "koth.list-header", Map.of("koths", names));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2 || plugin.kothManager().koth(args[1]) == null) {
            plugin.messages().send(sender, "general.invalid-koth", Map.of("koth", args.length < 2 ? "" : args[1]));
            return;
        }
        KothConfig koth = plugin.kothManager().koth(args[1]);
        int next = plugin.scheduleManager().secondsUntilNext(null, koth.id());
        plugin.messages().send(sender, "koth.info", Map.of(
            "display_name", koth.displayName(),
            "mode", koth.mode().name(),
            "active", String.valueOf(plugin.kothManager().isActive(koth.id())),
            "next", TimeUtil.format(next, plugin.configService().timerFormat())
        ));
    }

    private void giveStarter(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/fokothadmin givestarter <koth> <player> [amount]");
            return;
        }
        KothConfig koth = plugin.kothManager().koth(args[1]);
        Player target = Bukkit.getPlayerExact(args[2]);
        if (koth == null) {
            plugin.messages().send(sender, "general.invalid-koth", Map.of("koth", args[1]));
            return;
        }
        if (target == null) {
            plugin.messages().send(sender, "general.invalid-player", Map.of("player", args[2]));
            return;
        }
        int amount;
        try {
            amount = args.length >= 4 ? Math.min(MAX_STARTER_GIVE_AMOUNT, Math.max(1, Integer.parseInt(args[3]))) : 1;
        } catch (NumberFormatException ex) {
            plugin.messages().send(sender, "general.invalid-number", Map.of("hint", "Use item amount."));
            return;
        }
        ItemStack item = plugin.kothManager().starter(koth);
        List<ItemStack> stacks = new java.util.ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = item.clone();
            stack.setAmount(Math.min(stack.getMaxStackSize(), remaining));
            stacks.add(stack);
            remaining -= stack.getAmount();
        }
        inventoryDeposit.deposit(target, stacks, target.getLocation(), OverflowPolicy.DROP_OVERFLOW);
        plugin.messages().send(sender, "koth.starter-given", Map.of(
            "amount", String.valueOf(amount),
            "koth", koth.id(),
            "player", target.getName()
        ));
        if (sender instanceof Player player) {
            plugin.sounds().play(player, "koth.starter-given");
        }
    }

    private void resetWins(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/fokothadmin resetwins <player>");
            return;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(args[1]);
        plugin.userDataStore().resetWins(player);
        plugin.messages().send(sender, "koth.reset-wins", Map.of("player", args[1]));
        if (sender instanceof Player playerSender) {
            plugin.sounds().play(playerSender, "koth.admin-action");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("fokoth.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && List.of("delete", "start", "stop", "editor", "tp", "info", "givestarter").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(plugin.kothManager().allKoths().stream().map(KothConfig::id).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("resetwins")) {
            return onlinePlayerNames(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return filter(List.of("CAPTURE", "SCORE"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("givestarter")) {
            return onlinePlayerNames(args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("givestarter")) {
            return filter(List.of("1", "8", "16", "32", "64"), args[3]);
        }
        return List.of();
    }

    private List<String> onlinePlayerNames(String input) {
        return FoAdminArguments.onlinePlayer().complete(input);
    }

    private void sendUsage(CommandSender sender, String usage) {
        plugin.messages().send(sender, "general.unknown-command", UNKNOWN_COMMAND_FALLBACK, Map.of("usage", usage));
    }

    private List<String> filter(List<String> values, String prefix) {
        return FoAdminArguments.completeOptions(values, prefix);
    }
}
