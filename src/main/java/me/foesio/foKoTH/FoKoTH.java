package me.foesio.foKoTH;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.plugin.FoPluginTitle;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.text.FoText;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foKoTH.command.AdminCommand;
import me.foesio.foKoTH.config.ConfigService;
import me.foesio.foKoTH.editor.EditorManager;
import me.foesio.foKoTH.hook.PlaceholderHook;
import me.foesio.foKoTH.hook.TeamService;
import me.foesio.foKoTH.koth.KothManager;
import me.foesio.foKoTH.listener.PlayerStateListener;
import me.foesio.foKoTH.listener.StarterListener;
import me.foesio.foKoTH.listener.WandListener;
import me.foesio.foKoTH.schedule.ScheduleManager;
import me.foesio.foKoTH.storage.JdbcUserDataStore;
import me.foesio.foKoTH.webhook.WebhookService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class FoKoTH extends JavaPlugin {

    private FoCoreContext core;
    private FoMessageService messages;
    private ConfigService configService;
    private JdbcUserDataStore userDataStore;
    private TeamService teamService;
    private WebhookService webhookService;
    private KothManager kothManager;
    private ScheduleManager scheduleManager;
    private EditorManager editorManager;
    private UpdateNoticeService updateNotices;
    private PlaceholderHook placeholderHook;

    @Override
    public void onEnable() {
        saveDefaults();

        this.messages = FoMessageService.load(this, messageMigrations());
        this.core = FoPluginCore.create(this);
        this.core.warnIfNativeDialogsUnavailable();
        this.configService = new ConfigService(this, messages);
        this.userDataStore = new JdbcUserDataStore(this, messages, configService);
        this.teamService = new TeamService(this, messages, configService);
        this.webhookService = new WebhookService(this);
        this.kothManager = new KothManager(this, messages, configService, userDataStore, teamService, webhookService);
        this.scheduleManager = new ScheduleManager(this, messages, configService, kothManager);
        this.editorManager = new EditorManager(this, messages, configService, kothManager, scheduleManager);
        this.updateNotices = core.createUpdateNotices(messages, "fokoth").start();

        userDataStore.start();
        kothManager.start();
        scheduleManager.start();

        AdminCommand adminCommand = new AdminCommand(this);
        getCommand("fokothadmin").setExecutor(adminCommand);
        getCommand("fokothadmin").setTabCompleter(adminCommand);

        Bukkit.getPluginManager().registerEvents(editorManager, this);
        Bukkit.getPluginManager().registerEvents(new WandListener(this, messages, kothManager), this);
        Bukkit.getPluginManager().registerEvents(new StarterListener(this, messages, kothManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerStateListener(kothManager), this);

        refreshPlaceholderHook();

        logInfo("console.startup", "{prefix}enabled v{version}", Map.of("version", getDescription().getVersion()));
    }

    @Override
    public void onDisable() {
        if (placeholderHook != null) {
            placeholderHook.unregister();
        }
        if (scheduleManager != null) {
            scheduleManager.stop();
        }
        if (kothManager != null) {
            kothManager.stopAll(false);
            kothManager.shutdownTasks();
        }
        if (userDataStore != null) {
            userDataStore.close();
        }
        if (messages != null) {
            logInfo("console.shutdown", "{prefix}disabled", Map.of());
        }
        if (core != null) {
            core.close();
            core = null;
        }
    }

    public FoReloadResult reloadFoKoTH() {
        FoReloadResult result = FoReloadRegistry.create()
            .add("config", this::reloadConfigDefaults)
            .addMessages(messages)
            .add("core", this::refreshCoreContext)
            .add("settings", configService::reload)
            .add("team hooks", teamService::reload)
            .add("koths", kothManager::reload)
            .add("schedules", scheduleManager::reload)
            .add("editor", editorManager::reload)
            .add("integrations", this::refreshIntegrations)
            .reload();
        if (result.successful()) {
            logInfo("console.reload", "{prefix}reloaded config", Map.of());
        } else {
            logWarning("console.config-warning", "{prefix}{bad}{message}", Map.of(
                "message", "Reload failed at " + result.failedStep() + ": " + result.errorMessage()
            ));
        }
        return result;
    }

    public void refreshIntegrations() {
        refreshPlaceholderHook();
    }

    private void refreshPlaceholderHook() {
        boolean shouldRegister = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && configService.features().placeholderApi();
        if (shouldRegister && placeholderHook == null) {
            this.placeholderHook = new PlaceholderHook(this);
            this.placeholderHook.register();
        } else if (!shouldRegister && placeholderHook != null) {
            placeholderHook.unregister();
            placeholderHook = null;
        }
    }

    private void saveDefaults() {
        saveDefaultConfig();
        reloadConfigDefaults();
    }

    private void reloadConfigDefaults() {
        reloadConfig();
        getConfig().options().parseComments(true);
        removeLegacyPluginTitle();
        NativeDialogConfigDefaults.addDefaults(this);
        saveConfig();
    }

    private void removeLegacyPluginTitle() {
        FoPluginTitle.removeLegacyConfig(getConfig());
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
            .removeExact("editor.prompt", "{prefix}{muted}Enter {theme}{field}{muted}. Format: {format}. Type {bad}cancel{muted} to cancel.")
            .add(config -> {
                java.util.List<String> version = new java.util.ArrayList<>(config.getStringList("admin.version"));
                if (!version.remove("{prefix}{muted}Update: {theme}{update_status}")) {
                    return false;
                }
                config.set("admin.version", version);
                return true;
            })
            .remove("console.update-available")
            .remove("console.update-current")
            .remove("console.update-failed")
            .remove("admin.update-available")
            .remove("admin.update-current")
            .remove("admin.update-failed")
            .remove("admin.join-update")
            .add(this::backfillBundledMessageDefaults)
            .build();
    }

    private boolean backfillBundledMessageDefaults(FileConfiguration config) {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = getResource("messages.yml")) {
            if (stream == null) {
                return false;
            }
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            getLogger().warning("Could not load bundled message defaults: " + exception.getMessage());
            return false;
        }

        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || config.contains(key)) {
                continue;
            }
            config.set(key, defaults.get(key));
            changed = true;
        }
        return changed;
    }

    private void logInfo(String path, String fallback, Map<String, String> placeholders) {
        getLogger().info(FoText.plain(messages.render(path, fallback, placeholders)));
    }

    private void logWarning(String path, String fallback, Map<String, String> placeholders) {
        getLogger().warning(FoText.plain(messages.render(path, fallback, placeholders)));
    }

    private void refreshCoreContext() {
        FoCoreContext oldCore = core;
        this.core = FoPluginCore.create(this);
        this.core.warnIfNativeDialogsUnavailable();
        if (oldCore != null) {
            oldCore.close();
        }
    }

    public FoCoreContext core() {
        return core;
    }

    public FoMessageService messages() {
        return messages;
    }

    public ConfigService configService() {
        return configService;
    }

    public JdbcUserDataStore userDataStore() {
        return userDataStore;
    }

    public TeamService teamService() {
        return teamService;
    }

    public KothManager kothManager() {
        return kothManager;
    }

    public ScheduleManager scheduleManager() {
        return scheduleManager;
    }

    public EditorManager editorManager() {
        return editorManager;
    }

    public UpdateNoticeService updateNotices() {
        return updateNotices;
    }
}
