package me.foesio.foKoTH.hook;

import me.foesio.foKoTH.api.TeamResolver;
import me.foesio.foKoTH.config.ConfigService;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.text.FoText;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TeamService {

    private final JavaPlugin plugin;
    private final FoMessageService messages;
    private final ConfigService config;
    private final Map<String, RegisteredResolver> resolvers = new HashMap<>();
    private final Set<String> failedResolvers = new HashSet<>();
    private String hook;

    private record RegisteredResolver(Plugin owner, TeamResolver resolver) {}

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
        RegisteredResolver custom = resolvers.get(normalized);
        if (custom != null) {
            if (!custom.owner().isEnabled()) {
                resolvers.remove(normalized);
            } else {
                try {
                    String team = custom.resolver().teamName(player);
                    return team == null ? "" : team.trim();
                } catch (Throwable error) {
                    if (failedResolvers.add(normalized)) {
                        plugin.getLogger().warning("Team resolver " + normalized + " failed: " + error);
                    }
                }
            }
            return bukkitTeam(player);
        }
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

    public boolean registerResolver(Plugin owner, String id, TeamResolver resolver) {
        java.util.Objects.requireNonNull(owner, "owner");
        java.util.Objects.requireNonNull(resolver, "resolver");
        if (!owner.isEnabled() || id == null || !id.matches("[a-zA-Z0-9_-]+")) {
            return false;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (Set.of("none", "bukkit", "betterteams", "factionsuuid", "gangsplus", "guilds",
            "kingdomsx", "saberfactions", "superiorskyblock2", "towny", "ultimateclans",
            "parties", "axparties", "zelteams", "foteams").contains(key)) {
            return false;
        }
        RegisteredResolver existing = resolvers.get(key);
        if (existing != null && existing.owner() != owner && existing.owner().isEnabled()) {
            return false;
        }
        resolvers.put(key, new RegisteredResolver(owner, resolver));
        failedResolvers.remove(key);
        return true;
    }

    public void unregisterResolver(Plugin owner, String id) {
        if (owner == null || id == null) {
            return;
        }
        String key = id.toLowerCase(Locale.ROOT);
        RegisteredResolver registered = resolvers.get(key);
        if (registered != null && registered.owner() == owner) {
            resolvers.remove(key);
            failedResolvers.remove(key);
        }
    }

    public void unregisterOwner(Plugin owner) {
        resolvers.entrySet().removeIf(entry -> {
            if (entry.getValue().owner() != owner) {
                return false;
            }
            failedResolvers.remove(entry.getKey());
            return true;
        });
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
