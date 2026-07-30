package me.foesio.foKoTH.hook;

import me.foesio.foKoTH.config.ConfigService;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.text.FoText;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class TeamService {

    private final JavaPlugin plugin;
    private final FoMessageService messages;
    private final ConfigService config;
    private String hook;

    public TeamService(JavaPlugin plugin, FoMessageService messages, ConfigService config) {
        this.plugin = plugin;
        this.messages = messages;
        this.config = config;
        reload();
    }

    public void reload() {
        this.hook = config.teamHook();
        plugin.getLogger().info(FoText.plain(messages.render("console.hook-selected", "{prefix}team hook: {hook}", Map.of("hook", hook))));
    }

    public String teamName(Player player) {
        if (!config.features().teamHooks() || player == null || hook == null || hook.equalsIgnoreCase("none")) {
            return "";
        }
        if (!plugin.getConfig().getBoolean("hook-settings." + hook, true)) {
            return "";
        }
        String normalized = hook.toLowerCase(Locale.ROOT);
        if (normalized.equals("bukkit")) {
            return bukkitTeam(player);
        }
        String reflected = reflectedTeam(player, normalized);
        if (!reflected.isEmpty()) {
            return reflected;
        }
        return bukkitTeam(player);
    }

    public String display(Player player) {
        String team = teamName(player);
        return team.isEmpty() ? player.getName() : team + " " + player.getName();
    }

    private String bukkitTeam(Player player) {
        Team team = player.getScoreboard().getEntryTeam(player.getName());
        return team == null ? "" : team.getName();
    }

    private String reflectedTeam(Player player, String normalized) {
        try {
            return switch (normalized) {
                case "betterteams" -> betterTeams(player);
                case "towny advanced", "towny" -> towny(player);
                case "kingdomsx" -> kingdoms(player);
                case "parties" -> parties(player);
                case "foteams" -> foTeams(player);
                default -> "";
            };
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String betterTeams(Player player) throws Exception {
        Class<?> teamClass = Class.forName("com.booksaw.betterTeams.Team");
        Method getTeam = teamClass.getMethod("getTeam", Player.class);
        Object team = getTeam.invoke(null, player);
        return nameFrom(team);
    }

    private String towny(Player player) throws Exception {
        Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
        Object api = apiClass.getMethod("getInstance").invoke(null);
        Object resident = apiClass.getMethod("getResident", Player.class).invoke(api, player);
        if (resident == null) {
            return "";
        }
        Object town = resident.getClass().getMethod("getTownOrNull").invoke(resident);
        return nameFrom(town);
    }

    private String kingdoms(Player player) throws Exception {
        Class<?> playerClass = Class.forName("org.kingdoms.constants.player.KingdomPlayer");
        Object kingdomPlayer = playerClass.getMethod("getKingdomPlayer", Player.class).invoke(null, player);
        Object kingdom = kingdomPlayer.getClass().getMethod("getKingdom").invoke(kingdomPlayer);
        return nameFrom(kingdom);
    }

    private String parties(Player player) throws Exception {
        Class<?> apiClass = Class.forName("com.alessiodp.parties.api.Parties");
        Object api = apiClass.getMethod("getApi").invoke(null);
        Object partyPlayer = api.getClass().getMethod("getPartyPlayer", java.util.UUID.class).invoke(api, player.getUniqueId());
        Object party = partyPlayer.getClass().getMethod("getParty").invoke(partyPlayer);
        return nameFrom(party);
    }

    private String foTeams(Player player) throws Exception {
        org.bukkit.plugin.Plugin foTeams = plugin.getServer().getPluginManager().getPlugin("FoTeams");
        if (foTeams == null || !foTeams.isEnabled()) {
            return "";
        }
        Object teamService = foTeams.getClass().getMethod("getTeamService").invoke(foTeams);
        if (teamService == null) {
            return "";
        }
        Object optionalTeam = teamService.getClass().getMethod("teamOf", java.util.UUID.class).invoke(teamService, player.getUniqueId());
        Object team = optionalTeam instanceof Optional<?> optional ? optional.orElse(null) : optionalTeam;
        return nameFrom(team);
    }

    private String nameFrom(Object object) throws Exception {
        if (object == null) {
            return "";
        }
        for (String methodName : new String[]{"getName", "getTag", "getId"}) {
            try {
                Object name = object.getClass().getMethod(methodName).invoke(object);
                if (name != null && !name.toString().isBlank()) {
                    return name.toString();
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return "";
    }
}
