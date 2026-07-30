package me.foesio.foKoTH.storage;

import me.foesio.foKoTH.config.ConfigService;
import me.foesio.foKoTH.config.StorageSettings;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.text.FoText;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class JdbcUserDataStore {

    private final JavaPlugin plugin;
    private final FoMessageService messages;
    private final ConfigService config;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FoKoTH-UserData");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<UUID, Integer> playerWins = new ConcurrentHashMap<>();
    private final Map<String, Integer> teamWins = new ConcurrentHashMap<>();
    private volatile Connection connection;

    public JdbcUserDataStore(JavaPlugin plugin, FoMessageService messages, ConfigService config) {
        this.plugin = plugin;
        this.messages = messages;
        this.config = config;
    }

    public void start() {
        CompletableFuture.runAsync(() -> {
            try {
                connect();
                createTables();
                loadCache();
                logInfo("console.storage-ready", "{prefix}storage ready: {type}", Map.of("type", config.storage().type()));
            } catch (Exception ex) {
                logWarning("console.storage-failed", "{prefix}storage failed: {error}", Map.of("error", ex.getMessage()));
            }
        }, executor);
    }

    public void close() {
        executor.shutdownNow();
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
    }

    public int playerWins(UUID uuid) {
        return playerWins.getOrDefault(uuid, 0);
    }

    public int teamWins(String team) {
        return teamWins.getOrDefault(team == null ? "" : team.toLowerCase(java.util.Locale.ROOT), 0);
    }

    public void recordWin(Player player, String team, String koth, String mode) {
        UUID uuid = player.getUniqueId();
        playerWins.merge(uuid, 1, Integer::sum);
        if (team != null && !team.isBlank()) {
            teamWins.merge(team.toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
        }
        CompletableFuture.runAsync(() -> {
            try {
                upsertWin("PLAYER", uuid.toString(), player.getName(), playerWins(uuid));
                if (team != null && !team.isBlank()) {
                    upsertWin("TEAM", team.toLowerCase(java.util.Locale.ROOT), team, teamWins(team));
                }
                insertHistory(uuid, player.getName(), team, koth, mode);
            } catch (Exception ex) {
                logWarning("console.storage-failed", "{prefix}storage failed: {error}", Map.of("error", ex.getMessage()));
            }
        }, executor);
    }

    public void resetWins(OfflinePlayer player) {
        if (player.getUniqueId() != null) {
            playerWins.remove(player.getUniqueId());
        }
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM fokoth_wins WHERE scope = ? AND subject = ?")) {
                statement.setString(1, "PLAYER");
                statement.setString(2, player.getUniqueId().toString());
                statement.executeUpdate();
            } catch (Exception ex) {
                logWarning("console.storage-failed", "{prefix}storage failed: {error}", Map.of("error", ex.getMessage()));
            }
        }, executor);
    }

    private void logInfo(String path, String fallback, Map<String, String> placeholders) {
        plugin.getLogger().info(FoText.plain(messages.render(path, fallback, placeholders)));
    }

    private void logWarning(String path, String fallback, Map<String, String> placeholders) {
        plugin.getLogger().warning(FoText.plain(messages.render(path, fallback, placeholders)));
    }

    private void connect() throws Exception {
        StorageSettings settings = config.storage();
        String type = settings.type();
        if (type.equals("sqlite")) {
            File file = new File(plugin.getDataFolder(), "userdata.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            optimizeSqlite();
            return;
        }
        if (type.equals("h2")) {
            connection = DriverManager.getConnection("jdbc:h2:" + new File(plugin.getDataFolder(), "userdata").getAbsolutePath());
            return;
        }
        if (type.equals("mysql")) {
            connection = DriverManager.getConnection(
                "jdbc:mysql://" + settings.host() + ":" + settings.port() + "/" + settings.database() + "?useSSL=" + settings.useSsl(),
                settings.username(),
                settings.password());
            return;
        }
        if (type.equals("postgresql") || type.equals("postgres")) {
            connection = DriverManager.getConnection(
                "jdbc:postgresql://" + settings.host() + ":" + settings.port() + "/" + settings.database(),
                settings.username(),
                settings.password());
            return;
        }
        throw new IllegalArgumentException("unknown storage type " + type);
    }

    private void optimizeSqlite() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    private void createTables() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS fokoth_wins (scope VARCHAR(16) NOT NULL, subject VARCHAR(128) NOT NULL, name VARCHAR(128) NOT NULL, wins INTEGER NOT NULL, PRIMARY KEY(scope, subject))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS fokoth_win_history (player_uuid VARCHAR(64) NOT NULL, player_name VARCHAR(128) NOT NULL, team_name VARCHAR(128), koth VARCHAR(64) NOT NULL, mode VARCHAR(16) NOT NULL, won_at BIGINT NOT NULL)");
        }
    }

    private void loadCache() throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT scope, subject, name, wins FROM fokoth_wins")) {
            while (rs.next()) {
                if (rs.getString("scope").equalsIgnoreCase("PLAYER")) {
                    playerWins.put(UUID.fromString(rs.getString("subject")), rs.getInt("wins"));
                } else if (rs.getString("scope").equalsIgnoreCase("TEAM")) {
                    teamWins.put(rs.getString("subject").toLowerCase(java.util.Locale.ROOT), rs.getInt("wins"));
                }
            }
        }
    }

    private void upsertWin(String scope, String subject, String name, int wins) throws Exception {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM fokoth_wins WHERE scope = ? AND subject = ?")) {
            delete.setString(1, scope);
            delete.setString(2, subject);
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO fokoth_wins(scope, subject, name, wins) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, scope);
            insert.setString(2, subject);
            insert.setString(3, name);
            insert.setInt(4, wins);
            insert.executeUpdate();
        }
    }

    private void insertHistory(UUID uuid, String playerName, String team, String koth, String mode) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO fokoth_win_history(player_uuid, player_name, team_name, koth, mode, won_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, playerName);
            insert.setString(3, team);
            insert.setString(4, koth);
            insert.setString(5, mode);
            insert.setLong(6, System.currentTimeMillis());
            insert.executeUpdate();
        }
    }
}
