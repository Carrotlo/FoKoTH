package me.foesio.foKoTH.command;

import me.foesio.core.command.FoAdminArguments;
import me.foesio.foKoTH.FoKoTH;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final String ROOT_USAGE = "/fokothadmin <version|reload|editor|list|info|start|stop>";
    private static final String UNKNOWN_COMMAND_FALLBACK = "{prefix}{bad}Unknown command. {muted}Use {theme}{usage}";
    private static final List<String> ROOT_SUBCOMMANDS = merge(List.of("version"), KothCommand.SUBCOMMANDS);

    private final FoKoTH plugin;
    private final KothCommand kothCommand;

    public AdminCommand(FoKoTH plugin) {
        this.plugin = plugin;
        this.kothCommand = new KothCommand(plugin);
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
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "version" -> version(sender);
            default -> {
                if (KothCommand.hasSubcommand(subcommand)) {
                    return kothCommand.onCommand(sender, command, label, args);
                }
                sendUsage(sender, ROOT_USAGE);
            }
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String usage) {
        plugin.messages().send(sender, "general.unknown-command", UNKNOWN_COMMAND_FALLBACK, Map.of("usage", usage));
    }

    private void version(CommandSender sender) {
        plugin.messages().send(sender, "admin.version", Map.of("version", plugin.getDescription().getVersion()));
        plugin.updateNotices().checkAndSendVersion(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("fokoth.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(ROOT_SUBCOMMANDS, args[0]);
        }
        if (KothCommand.hasSubcommand(args[0])) {
            return kothCommand.onTabComplete(sender, command, alias, args);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        return FoAdminArguments.completeOptions(values, prefix);
    }

    private static List<String> merge(List<String> first, List<String> second) {
        List<String> out = new ArrayList<>(first);
        out.addAll(second);
        return List.copyOf(out);
    }
}
