package me.foesio.foKoTH.editor;

import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.NativeDialogAvailability;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.CursorItemEditor;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.gui.GuiTitles;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.message.FoStyle;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.foKoTH.FoKoTH;
import me.foesio.foKoTH.config.ConfigService;
import me.foesio.foKoTH.config.ItemDefinition;
import me.foesio.foKoTH.config.KothConfig;
import me.foesio.foKoTH.koth.KothManager;
import me.foesio.foKoTH.koth.KothMode;
import me.foesio.foKoTH.koth.KothZone;
import me.foesio.foKoTH.schedule.CronExpression;
import me.foesio.foKoTH.schedule.KothSchedule;
import me.foesio.foKoTH.schedule.ScheduleManager;
import me.foesio.foKoTH.util.TextUtil;
import me.foesio.foKoTH.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EditorManager implements Listener {

    private static final int DEFAULT_INPUT_WIDTH = 300;
    private static final int MAIN_SIZE = GuiSlots.sizeForRows(3);
    private static final int MAIN_KOTHS_SLOT = 11;
    private static final int MAIN_SCHEDULES_SLOT = 13;
    private static final int MAIN_SETTINGS_SLOT = 15;
    private static final int KOTH_EDIT_ROWS = 5;
    private static final int KOTH_EDIT_SIZE = GuiSlots.sizeForRows(KOTH_EDIT_ROWS);
    private static final int KOTH_TOGGLE_SLOT = 10;
    private static final int KOTH_MODE_SLOT = 11;
    private static final int KOTH_DISPLAY_NAME_SLOT = 12;
    private static final int KOTH_TELEPORT_SLOT = 13;
    private static final int KOTH_CAPTURE_TIME_SLOT = 14;
    private static final int KOTH_MAX_TIME_SLOT = 15;
    private static final int KOTH_MINIMUM_PLAYERS_SLOT = 16;
    private static final int KOTH_REWARDS_SLOT = 19;
    private static final int KOTH_STARTER_ITEM_SLOT = 20;
    private static final int KOTH_TEAM_MODE_SLOT = 21;
    private static final int KOTH_ACTIONBAR_SLOT = 22;
    private static final int KOTH_BOSSBAR_SLOT = 23;
    private static final int KOTH_SCOREBOARD_SLOT = 24;
    private static final int KOTH_HOLOGRAM_SLOT = 25;
    private static final int KOTH_DELETE_SLOT = 31;
    private static final int KOTH_BACK_SLOT = GuiSlots.bottomMiddleSlot(KOTH_EDIT_ROWS);
    private static final int SETTINGS_ROWS = 4;
    private static final int SETTINGS_SIZE = GuiSlots.sizeForRows(SETTINGS_ROWS);
    private static final int SETTINGS_BACK_SLOT = GuiSlots.bottomMiddleSlot(SETTINGS_ROWS);
    private static final int SCHEDULE_EDIT_ROWS = 3;
    private static final int SCHEDULE_EDIT_SIZE = GuiSlots.sizeForRows(SCHEDULE_EDIT_ROWS);
    private static final int SCHEDULE_TOGGLE_SLOT = 10;
    private static final int SCHEDULE_CRON_SLOT = 12;
    private static final int SCHEDULE_RANDOM_SLOT = 14;
    private static final int SCHEDULE_KOTHS_SLOT = 16;
    private static final int SCHEDULE_BACK_SLOT = GuiSlots.bottomMiddleSlot(SCHEDULE_EDIT_ROWS);
    private static final int CONFIRM_SIZE = GuiSlots.sizeForRows(3);
    private static final int CONFIRM_DELETE_SLOT = 11;
    private static final int CONFIRM_CANCEL_SLOT = 15;

    private static final String[] SETTINGS_FEATURES = {
        "schedules",
        "starter-items",
        "scoreboards",
        "bossbars",
        "holograms",
        "webhooks",
        "placeholderapi",
        "filter",
        "team-hooks"
    };

    private final FoKoTH plugin;
    private final FoMessageService messages;
    private final ConfigService config;
    private final KothManager kothManager;
    private final ScheduleManager scheduleManager;
    private final FoSoundService sounds;
    private final FoEditorSounds editorSounds;
    private final GuiButtonConfig buttons = GuiButtonConfig.defaults();
    private final Set<UUID> fallbackWarnedPlayers = ConcurrentHashMap.newKeySet();

    public EditorManager(FoKoTH plugin, FoMessageService messages, ConfigService config, KothManager kothManager, ScheduleManager scheduleManager, FoSoundService sounds, FoEditorSounds editorSounds) {
        this.plugin = plugin;
        this.messages = messages;
        this.config = config;
        this.kothManager = kothManager;
        this.scheduleManager = scheduleManager;
        this.sounds = sounds;
        this.editorSounds = editorSounds;
        reload();
    }

    public void reload() {
        fallbackWarnedPlayers.clear();
    }

    public void openMain(Player player) {
        openMain(player, true);
    }

    private void openMain(Player player, boolean playOpen) {
        Inventory inventory = Bukkit.createInventory(new EditorHolder(EditorPage.MAIN, "", 0, ""), MAIN_SIZE, title("FoKoTH Editor"));
        fill(inventory);
        set(inventory, MAIN_KOTHS_SLOT, button(player, FoStyle.THEME, Material.BEACON, "KOTHs", List.of(
            FoStyle.WHITE + "Browse and edit events."
        ), "browse KOTHs"));
        set(inventory, MAIN_SCHEDULES_SLOT, button(player, FoStyle.THEME, Material.CLOCK, "Schedules", List.of(
            FoStyle.WHITE + "Auto-start KOTHs by time.",
            FoStyle.WHITE + "Uses server timezone."
        ), "manage schedules"));
        set(inventory, MAIN_SETTINGS_SLOT, button(player, FoStyle.THEME, Material.COMPARATOR, "Settings", List.of(
            FoStyle.WHITE + "Toggle global features."
        ), "edit settings"));
        player.openInventory(inventory);
        if (playOpen) {
            editorSounds.open(player);
        }
    }

    public void openKoths(Player player, int page, String search) {
        openKoths(player, page, search, true);
    }

    private void openKoths(Player player, int page, String search, boolean playOpen) {
        List<KothConfig> entries = kothBrowserEntries(search);
        List<EntryBrowserRequest.Entry> browserEntries = entries.stream()
                .map(koth -> EntryBrowserRequest.Entry.of(koth.id(), button(player, FoStyle.THEME, Material.BEACON, koth.id(), List.of(
                FoStyle.WHITE + "Mode: " + FoStyle.THEME + koth.mode().name(),
                FoStyle.WHITE + "Active: " + FoStyle.THEME + kothManager.isActive(koth.id()),
                "",
                FoStyle.WHITE + "Click to edit."
            ), "edit the KOTH"))).toList();
        EntryBrowserMenus.open(player, EntryBrowserRequest.builder()
                .title("KOTHs")
                .entries(browserEntries)
                .page(page)
                .filter(search)
                .buttons(buttons)
                .showBack(true)
                .addButton(button(player, FoStyle.GOOD, Material.ANVIL, "Add KOTH", List.of(
                        FoStyle.WHITE + "Create new event."), "add a KOTH"))
                .emptyItem(item(player, Material.PAPER, FoStyle.BAD + "No KOTHs", List.of(
                        FoStyle.WHITE + "No KOTHs match the current search.")))
                .context(new KothBrowserContext())
                .build());
        if (playOpen) {
            editorSounds.open(player);
        }
    }

    public void openKoth(Player player, String id) {
        openKoth(player, id, true);
    }

    private void openKoth(Player player, String id, boolean playOpen) {
        KothConfig koth = kothManager.koth(id);
        if (koth == null) {
            messages.send(player, "general.invalid-koth", Map.of("koth", id));
            openKoths(player, 0, "");
            return;
        }
        Inventory inventory = Bukkit.createInventory(new EditorHolder(EditorPage.KOTH_EDIT, koth.id(), 0, ""), KOTH_EDIT_SIZE, title("Edit KOTH"));
        fill(inventory);
        set(inventory, KOTH_TOGGLE_SLOT, toggleItem(player, koth.enabled(), "Enabled", List.of(
            FoStyle.WHITE + "Current: " + enabledColor(koth.enabled()) + enabledText(koth.enabled())
        )));
        set(inventory, KOTH_MODE_SLOT, button(player, FoStyle.THEME, Material.REPEATER, "Mode", List.of(
            FoStyle.WHITE + "Current: " + FoStyle.THEME + koth.mode().name(),
            FoStyle.WHITE + "CAPTURE",
            FoStyle.WHITE + "SCORE"
        ), "cycle KOTH mode"));
        set(inventory, KOTH_DISPLAY_NAME_SLOT, button(player, FoStyle.THEME, Material.NAME_TAG, "Display Name", List.of(
            FoStyle.WHITE + "Current:",
            FoStyle.THEME + koth.displayName()
        ), "edit the display name"));
        set(inventory, KOTH_CAPTURE_TIME_SLOT, button(player, FoStyle.THEME, Material.CLOCK, "Capture Time", List.of(
            FoStyle.WHITE + "Current: " + FoStyle.THEME + TimeUtil.format(koth.captureTimeSeconds(), config.timerFormat())
        ), "edit capture time"));
        set(inventory, KOTH_MAX_TIME_SLOT, button(player, FoStyle.THEME, Material.CLOCK, "Max Time", List.of(
            FoStyle.WHITE + "Current: " + FoStyle.THEME + TimeUtil.format(koth.maxTimeSeconds(), config.timerFormat())
        ), "edit maximum time"));
        set(inventory, KOTH_MINIMUM_PLAYERS_SLOT, button(player, FoStyle.THEME, Material.PLAYER_HEAD, "Minimum Players", List.of(
            FoStyle.WHITE + "Current: " + FoStyle.THEME + koth.minimumPlayers()
        ), "edit minimum players"));
        set(inventory, KOTH_STARTER_ITEM_SLOT, button(player, FoStyle.THEME, starterMaterial(koth), "Starter Item", List.of(
            FoStyle.WHITE + "Click with a item in your cursor."
        ), "set the starter item"));
        set(inventory, KOTH_TELEPORT_SLOT, button(player, FoStyle.THEME, Material.COMPASS, "Teleport", List.of(
            FoStyle.WHITE + "Teleport to center."
        ), "teleport to the KOTH"));
        set(inventory, KOTH_REWARDS_SLOT, button(player, FoStyle.THEME, Material.CHEST, "Rewards", List.of(
            FoStyle.WHITE + "Edit command rewards."
        ), "manage rewards"));
        set(inventory, KOTH_BOSSBAR_SLOT, button(player, enabledColor(koth.bossbar().enabled()), Material.ITEM_FRAME, "Bossbar", List.of(
            FoStyle.WHITE + "Current: " + enabledText(koth.bossbar().enabled())
        ), "toggle the bossbar"));
        set(inventory, KOTH_SCOREBOARD_SLOT, button(player, enabledColor(koth.scoreboard().enabled()), Material.MAP, "Scoreboard", List.of(
            FoStyle.WHITE + "Current: " + enabledText(koth.scoreboard().enabled())
        ), "toggle the scoreboard"));
        set(inventory, KOTH_HOLOGRAM_SLOT, button(player, enabledColor(koth.hologram().enabled()), Material.ARMOR_STAND, "Hologram", List.of(
            FoStyle.WHITE + "Current: " + enabledText(koth.hologram().enabled())
        ), "toggle the hologram"));
        set(inventory, KOTH_ACTIONBAR_SLOT, button(player, enabledColor(koth.actionbar().enabled()), Material.WRITABLE_BOOK, "Actionbar", List.of(
            FoStyle.WHITE + "Current: " + enabledText(koth.actionbar().enabled()),
            FoStyle.WHITE + "Shows while a player is capturing."
        ), "toggle the actionbar"));
        set(inventory, KOTH_TEAM_MODE_SLOT, button(player, enabledColor(koth.teamMode()), Material.SHIELD, "Team Mode", List.of(
            FoStyle.WHITE + "Current: " + enabledText(koth.teamMode())
        ), "toggle team mode"));
        set(inventory, KOTH_DELETE_SLOT, button(player, FoStyle.BAD, Material.LAVA_BUCKET, "Delete", List.of(
            FoStyle.WHITE + "Delete this KOTH."
        ), "delete the KOTH"));
        set(inventory, KOTH_BACK_SLOT, buttons.back(player));
        player.openInventory(inventory);
        if (playOpen) {
            editorSounds.open(player);
        }
    }

    public void openRewardCommands(Player player, String id, int page) {
        openRewardCommands(player, id, page, "");
    }

    public void openRewardCommands(Player player, String id, int page, String search) {
        openRewardCommands(player, id, page, search, true);
    }

    private void openRewardCommands(Player player, String id, int page, String search, boolean playOpen) {
        KothConfig koth = kothManager.koth(id);
        if (koth == null) {
            messages.send(player, "general.invalid-koth", Map.of("koth", id));
            openKoths(player, 0, "");
            return;
        }
        String normalizedSearch = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<EntryBrowserRequest.Entry> entries = new ArrayList<>();
        List<String> commands = koth.rewardCommands();
        for (int commandIndex = 0; commandIndex < commands.size(); commandIndex++) {
            String command = commands.get(commandIndex);
            String safeCommand = command == null ? "" : command;
            if (!normalizedSearch.isBlank() && !safeCommand.toLowerCase(Locale.ROOT).contains(normalizedSearch)) {
                continue;
            }
            entries.add(EntryBrowserRequest.Entry.of(String.valueOf(commandIndex), button(player, FoStyle.THEME, Material.COMMAND_BLOCK,
                    "Command #" + (commandIndex + 1), List.of(
                FoStyle.WHITE + "Current:",
                FoStyle.THEME + (command == null ? "" : command),
                "",
                FoStyle.WHITE + "Left-click to edit.",
                FoStyle.WHITE + "Right-click to delete."
            ), "edit the command")));
        }
        EntryBrowserMenus.open(player, EntryBrowserRequest.builder()
                .title("Reward Commands")
                .entries(entries)
                .page(page)
                .filter(normalizedSearch)
                .buttons(buttons)
                .showBack(true)
                .addButton(button(player, FoStyle.GOOD, Material.ANVIL, "Add Command", List.of(
                        FoStyle.WHITE + "Add one console command."), "add a command"))
                .emptyItem(item(player, Material.PAPER, FoStyle.BAD + "No Commands", List.of(
                        FoStyle.WHITE + "Use Add Command to create one.")))
                .context(new RewardBrowserContext(koth.id()))
                .build());
        if (playOpen) {
            editorSounds.open(player);
        }
    }

    public void openSettings(Player player) {
        openSettings(player, true);
    }

    private void openSettings(Player player, boolean playOpen) {
        Inventory inventory = Bukkit.createInventory(new EditorHolder(EditorPage.SETTINGS, "", 0, ""), SETTINGS_SIZE, title("Settings"));
        fill(inventory);
        for (String feature : SETTINGS_FEATURES) {
            boolean enabled = plugin.getConfig().getBoolean("features." + feature, true);
            set(inventory, settingsSlot(feature), toggleItem(player, enabled, featureDisplayName(feature), List.of(
                FoStyle.WHITE + "Current: " + enabledColor(enabled) + enabledText(enabled)
            )));
        }
        set(inventory, SETTINGS_BACK_SLOT, buttons.back(player));
        player.openInventory(inventory);
        if (playOpen) {
            editorSounds.open(player);
        }
    }

    public void openSchedules(Player player) {
        openSchedules(player, 0, "");
    }

    public void openSchedules(Player player, int page) {
        openSchedules(player, page, "");
    }

    public void openSchedules(Player player, int page, String search) {
        openSchedules(player, page, search, true);
    }

    private void openSchedules(Player player, int page, String search, boolean playOpen) {
        String normalizedSearch = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<KothSchedule> entries = sortedSchedules().stream()
                .filter(schedule -> normalizedSearch.isBlank() || schedule.id().toLowerCase(Locale.ROOT).contains(normalizedSearch))
                .toList();
        List<EntryBrowserRequest.Entry> browserEntries = entries.stream()
                .map(schedule -> EntryBrowserRequest.Entry.of(schedule.id(), button(player, FoStyle.THEME, Material.CLOCK, schedule.id(), List.of(
                FoStyle.WHITE + "State: " + enabledColor(schedule.enabled()) + enabledText(schedule.enabled()),
                FoStyle.WHITE + "Cron: " + FoStyle.THEME + schedule.cron(),
                FoStyle.WHITE + "Next: " + FoStyle.THEME + nextScheduleText(schedule),
                FoStyle.WHITE + "Run Mode: " + FoStyle.THEME + scheduleMode(schedule),
                FoStyle.WHITE + "KOTHs: " + FoStyle.THEME + joinedKoths(schedule),
                "",
                FoStyle.WHITE + "Click to edit."
            ), "edit the schedule"))).toList();
        EntryBrowserMenus.open(player, EntryBrowserRequest.builder()
                .title("Schedules")
                .entries(browserEntries)
                .page(page)
                .filter(normalizedSearch)
                .buttons(buttons)
                .showBack(true)
                .addButton(button(player, FoStyle.GOOD, Material.ANVIL, "Add Schedule", List.of(
                        FoStyle.WHITE + "Create timed auto-start."), "add a schedule"))
                .emptyItem(item(player, Material.PAPER, FoStyle.BAD + "No Schedules", List.of(
                        FoStyle.WHITE + "No schedules have been configured.")))
                .context(new ScheduleBrowserContext())
                .build());
        if (playOpen) {
            editorSounds.open(player);
        }
    }

    public void openSchedule(Player player, String id) {
        openSchedule(player, id, true);
    }

    private void openSchedule(Player player, String id, boolean playOpen) {
        KothSchedule schedule = config.schedules().get(id.toLowerCase(Locale.ROOT));
        if (schedule == null) {
            messages.send(player, "editor.invalid-schedule", Map.of("schedule", id));
            openSchedules(player);
            return;
        }
        Inventory inventory = Bukkit.createInventory(new EditorHolder(EditorPage.SCHEDULE_EDIT, schedule.id(), 0, ""), SCHEDULE_EDIT_SIZE, title("Edit Schedule"));
        fill(inventory);
        set(inventory, SCHEDULE_TOGGLE_SLOT, toggleItem(player, schedule.enabled(), "Enabled", List.of(
            FoStyle.WHITE + "Schedule: " + FoStyle.THEME + schedule.id(),
            FoStyle.WHITE + "Current: " + enabledColor(schedule.enabled()) + enabledText(schedule.enabled()),
            FoStyle.WHITE + "Disabled schedules never run."
        )));
        set(inventory, SCHEDULE_CRON_SLOT, button(player, FoStyle.THEME, Material.CLOCK, "Cron", List.of(
            FoStyle.WHITE + "Current:",
            FoStyle.THEME + schedule.cron(),
            FoStyle.WHITE + "Next: " + FoStyle.THEME + nextScheduleText(schedule),
            FoStyle.WHITE + "Format: minute hour day month weekday.",
            FoStyle.WHITE + "Example: 0 18 * * * = daily 18:00."
        ), "edit the schedule expression"));
        set(inventory, SCHEDULE_RANDOM_SLOT, button(player, enabledColor(schedule.random()), Material.ENDER_EYE, "Run Mode", List.of(
            FoStyle.WHITE + "Current: " + FoStyle.THEME + scheduleMode(schedule),
            FoStyle.WHITE + "Off: start every listed KOTH.",
            FoStyle.WHITE + "On: pick one random listed KOTH."
        ), "cycle the run mode"));
        set(inventory, SCHEDULE_KOTHS_SLOT, button(player, FoStyle.THEME, Material.BEACON, "KOTHs", List.of(
            FoStyle.WHITE + "Current:",
            FoStyle.THEME + joinedKoths(schedule),
            FoStyle.WHITE + "These are KOTH ids, not display names.",
            FoStyle.WHITE + "Comma separate ids, or type clear."
        ), "edit scheduled KOTHs"));
        set(inventory, SCHEDULE_BACK_SLOT, buttons.back(player));
        player.openInventory(inventory);
        if (playOpen) {
            editorSounds.open(player);
        }
    }

    public void openConfirmDelete(Player player, String kothId) {
        Inventory inventory = Bukkit.createInventory(new EditorHolder(EditorPage.CONFIRM_DELETE, kothId, 0, ""), CONFIRM_SIZE, title("Confirm"));
        fill(inventory);
        set(inventory, CONFIRM_DELETE_SLOT, button(player, FoStyle.GOOD, Material.LIME_CONCRETE, "Confirm", List.of(
            FoStyle.WHITE + "Delete entry."
        ), "confirm deletion"));
        set(inventory, CONFIRM_CANCEL_SLOT, button(player, FoStyle.BAD, Material.RED_CONCRETE, "Cancel", List.of(
            FoStyle.WHITE + "Return."
        ), "cancel deletion"));
        player.openInventory(inventory);
        editorSounds.open(player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof EntryBrowserHolder holder) {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                event.setCancelled(true);
                handleEntryBrowserClick(player, event.getRawSlot(), event.getClick(), holder);
            } else if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        if (!(event.getInventory().getHolder() instanceof EditorHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("fokoth.admin")) {
            messages.send(player, "general.no-permission");
            editorSounds.error(player);
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory() == null || event.getSlotType() == InventoryType.SlotType.OUTSIDE) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        switch (holder.page()) {
            case MAIN -> clickMain(player, slot);
            case KOTH_EDIT -> clickKothEdit(player, holder.context(), slot, event.getCursor());
            case SETTINGS -> clickSettings(player, slot);
            case SCHEDULE_EDIT -> clickScheduleEdit(player, holder.context(), slot);
            case CONFIRM_DELETE -> clickConfirm(player, holder.context(), slot);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        boolean editorInventory = event.getInventory().getHolder() instanceof EditorHolder
                || event.getInventory().getHolder() instanceof EntryBrowserHolder;
        if (!editorInventory) {
            return;
        }
        if (event.getPlayer() instanceof Player player && plugin.core().inventoryCloseSuppressor().consumeSuppressedClose(player)) {
            return;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        fallbackWarnedPlayers.remove(uuid);
    }

    private void clickMain(Player player, int slot) {
        if (slot == MAIN_KOTHS_SLOT) openKoths(player, 0, "");
        else if (slot == MAIN_SCHEDULES_SLOT) openSchedules(player);
        else if (slot == MAIN_SETTINGS_SLOT) openSettings(player);
    }

    private void handleEntryBrowserClick(Player player, int slot,
                                         org.bukkit.event.inventory.ClickType clickType,
                                         EntryBrowserHolder holder) {
        EntryBrowserClick click = EntryBrowserMenus.handleClick(slot, holder, clickType);
        if (holder.request().context() instanceof ScheduleBrowserContext) {
            handleScheduleEntryBrowserClick(player, click, holder);
            return;
        }
        if (holder.request().context() instanceof RewardBrowserContext context) {
            handleRewardEntryBrowserClick(player, click, holder, context);
            return;
        }
        if (!(holder.request().context() instanceof KothBrowserContext)) {
            return;
        }
        String search = holder.request().filter();
        switch (click.action()) {
            case ENTRY -> openKoth(player, click.entryId());
            case ADD -> prompt(player, "KOTH name", "letters, numbers, - or _", input -> {
                if (!TextUtil.validId(input)) {
                    messages.send(player, "editor.invalid-name");
                    editorSounds.error(player);
                    openKoths(player, holder.request().page(), search, false);
                    return;
                }
                String id = TextUtil.id(input);
                if (kothManager.koth(id) != null) {
                    messages.send(player, "editor.already-exists", Map.of("entry", id));
                    editorSounds.error(player);
                    openKoths(player, holder.request().page(), search, false);
                    return;
                }
                Location loc = player.getLocation().getBlock().getLocation();
                config.createKoth(id, KothMode.CAPTURE, new KothZone(loc, loc.clone().add(5, 5, 5)));
                kothManager.reload();
                messages.send(player, "editor.added", Map.of("entry", id));
                editorSounds.add(player);
                openKoth(player, id, false);
            }, () -> openKoths(player, holder.request().page(), search, false));
            case SEARCH -> prompt(player, "search", "text", input -> {
                messages.send(player, "editor.search-applied", Map.of("search", input));
                editorSounds.search(player);
                openKoths(player, 0, input.toLowerCase(Locale.ROOT), false);
            }, () -> openKoths(player, holder.request().page(), search, false));
            case CLEAR_SEARCH -> {
                messages.send(player, "editor.search-cleared");
                editorSounds.clearSearch(player);
                openKoths(player, 0, "", false);
            }
            case PREVIOUS_PAGE -> {
                editorSounds.previousPage(player);
                openKoths(player, holder.request().page() - 1, search, false);
            }
            case NEXT_PAGE -> {
                editorSounds.nextPage(player);
                openKoths(player, holder.request().page() + 1, search, false);
            }
            case BACK -> {
                editorSounds.back(player);
                openMain(player, false);
            }
            case NONE -> {
            }
        }
    }

    private void handleScheduleEntryBrowserClick(Player player, EntryBrowserClick click, EntryBrowserHolder holder) {
        String search = holder.request().filter();
        switch (click.action()) {
            case ENTRY -> openSchedule(player, click.entryId());
            case ADD -> prompt(player, "schedule name", "letters, numbers, - or _", input -> {
                if (!TextUtil.validId(input)) {
                    messages.send(player, "editor.invalid-name");
                    editorSounds.error(player);
                    openSchedules(player, holder.request().page(), search, false);
                    return;
                }
                String id = TextUtil.id(input);
                if (config.schedules().containsKey(id)) {
                    messages.send(player, "editor.already-exists", Map.of("entry", id));
                    editorSounds.error(player);
                    openSchedules(player, holder.request().page(), search, false);
                    return;
                }
                saveScheduleValues(id, Map.of(
                        "enabled", true,
                        "random", false,
                        "cron", "0 18 * * *",
                        "koths", List.of()
                ));
                messages.send(player, "editor.added", Map.of("entry", id));
                editorSounds.add(player);
                openSchedule(player, id, false);
            }, () -> openSchedules(player, holder.request().page(), search, false));
            case SEARCH -> prompt(player, "search", "text", input -> {
                editorSounds.search(player);
                openSchedules(player, 0, input, false);
            }, () -> openSchedules(player, holder.request().page(), search, false));
            case CLEAR_SEARCH -> {
                editorSounds.clearSearch(player);
                openSchedules(player, 0, "", false);
            }
            case PREVIOUS_PAGE -> {
                editorSounds.previousPage(player);
                openSchedules(player, holder.request().page() - 1, search, false);
            }
            case NEXT_PAGE -> {
                editorSounds.nextPage(player);
                openSchedules(player, holder.request().page() + 1, search, false);
            }
            case BACK -> {
                editorSounds.back(player);
                openMain(player, false);
            }
            case NONE -> {
            }
        }
    }

    private void handleRewardEntryBrowserClick(Player player, EntryBrowserClick click,
                                               EntryBrowserHolder holder, RewardBrowserContext context) {
        KothConfig koth = kothManager.koth(context.kothId());
        if (koth == null) {
            editorSounds.error(player);
            openKoths(player, 0, "", false);
            return;
        }
        String search = holder.request().filter();
        switch (click.action()) {
            case ENTRY -> {
                int commandIndex;
                try {
                    commandIndex = Integer.parseInt(click.entryId());
                } catch (NumberFormatException ignored) {
                    editorSounds.error(player);
                    return;
                }
                List<String> commands = new ArrayList<>(koth.rewardCommands());
                if (commandIndex < 0 || commandIndex >= commands.size()) {
                    editorSounds.error(player);
                    openRewardCommands(player, context.kothId(), holder.request().page(), search, false);
                    return;
                }
                if (click.clickType() != null && click.clickType().isRightClick()) {
                    commands.remove(commandIndex);
                    save("koths." + koth.id() + ".reward-commands", commands);
                    messages.send(player, "editor.deleted", Map.of("entry", "reward command #" + (commandIndex + 1)));
                    editorSounds.delete(player);
                    openRewardCommands(player, context.kothId(), holder.request().page(), search, false);
                    return;
                }
                promptRewardCommand(player, koth.id(), holder.request().page(), input -> {
                    String command = normalizeSingleCommandInput(input);
                    if (command.isEmpty()) {
                        messages.send(player, "editor.invalid-command");
                        editorSounds.error(player);
                    } else if (hasCommandSeparator(command)) {
                        messages.send(player, "editor.single-command-only");
                        editorSounds.error(player);
                    } else {
                        List<String> updated = currentRewardCommands(koth.id());
                        if (commandIndex >= 0 && commandIndex < updated.size()) {
                            updated.set(commandIndex, command);
                            save("koths." + koth.id() + ".reward-commands", updated);
                            messages.send(player, "general.saved", Map.of("setting", "reward command"));
                            editorSounds.save(player);
                        }
                    }
                    openRewardCommands(player, koth.id(), holder.request().page(), search, false);
                });
            }
            case ADD -> promptRewardCommand(player, koth.id(), holder.request().page(), input -> {
                String command = normalizeSingleCommandInput(input);
                if (command.isEmpty() || hasCommandSeparator(command)) {
                    messages.send(player, command.isEmpty() ? "editor.invalid-command" : "editor.single-command-only");
                    editorSounds.error(player);
                    openRewardCommands(player, koth.id(), holder.request().page(), search, false);
                    return;
                }
                List<String> updated = currentRewardCommands(koth.id());
                updated.add(command);
                save("koths." + koth.id() + ".reward-commands", updated);
                messages.send(player, "general.saved", Map.of("setting", "reward command"));
                editorSounds.add(player);
                openRewardCommands(player, koth.id(), holder.request().page(), search, false);
            });
            case SEARCH -> prompt(player, "search", "text", input -> {
                editorSounds.search(player);
                openRewardCommands(player, koth.id(), 0, input, false);
            }, () -> openRewardCommands(player, koth.id(), holder.request().page(), search, false));
            case CLEAR_SEARCH -> {
                editorSounds.clearSearch(player);
                openRewardCommands(player, koth.id(), 0, "", false);
            }
            case PREVIOUS_PAGE -> {
                editorSounds.previousPage(player);
                openRewardCommands(player, koth.id(), holder.request().page() - 1, search, false);
            }
            case NEXT_PAGE -> {
                editorSounds.nextPage(player);
                openRewardCommands(player, koth.id(), holder.request().page() + 1, search, false);
            }
            case BACK -> {
                editorSounds.back(player);
                openKoth(player, koth.id(), false);
            }
            case NONE -> {
            }
        }
    }

    private List<KothConfig> kothBrowserEntries(String search) {
        String normalizedSearch = search == null ? "" : search.toLowerCase(Locale.ROOT);
        return kothManager.allKoths().stream()
            .filter(koth -> normalizedSearch.isBlank() || koth.id().contains(normalizedSearch))
            .sorted(Comparator.comparing(KothConfig::id))
            .toList();
    }

    private void promptRewardCommand(Player player, String kothId, int page, java.util.function.Consumer<String> callback) {
        prompt(player, "reward command", "one console command", callback, () -> openRewardCommands(player, kothId, page, "", false));
    }

    private String normalizeSingleCommandInput(String input) {
        return input == null ? "" : input.trim();
    }

    private boolean hasCommandSeparator(String command) {
        return command.contains(";") || command.contains("|") || command.contains("\n") || command.contains("\r");
    }

    private List<String> currentRewardCommands(String kothId) {
        KothConfig current = kothManager.koth(kothId);
        return current == null ? new ArrayList<>() : new ArrayList<>(current.rewardCommands());
    }

    private void clickKothEdit(Player player, String id, int slot, ItemStack cursor) {
        KothConfig koth = kothManager.koth(id);
        if (koth == null) {
            editorSounds.error(player);
            openKoths(player, 0, "", false);
            return;
        }
        if (slot == KOTH_TOGGLE_SLOT) {
            boolean enabled = !koth.enabled();
            save("koths." + id + ".enabled", enabled);
            messages.send(player, "general.saved", Map.of("setting", "enabled"));
            editorSounds.toggle(player, enabled);
            openKoth(player, id, false);
        } else if (slot == KOTH_MODE_SLOT) {
            KothMode next = koth.mode() == KothMode.CAPTURE ? KothMode.SCORE : KothMode.CAPTURE;
            save("koths." + id + ".mode", next.name());
            messages.send(player, "general.saved", Map.of("setting", "mode"));
            editorSounds.cycle(player);
            openKoth(player, id, false);
        } else if (slot == KOTH_DISPLAY_NAME_SLOT) {
            prompt(player, "display name", "text with colors", input -> {
                save("koths." + id + ".display-name", input);
                messages.send(player, "general.saved", Map.of("setting", "display name"));
                editorSounds.save(player);
                openKoth(player, id, false);
            }, () -> openKoth(player, id, false));
        } else if (slot == KOTH_CAPTURE_TIME_SLOT) {
            promptTime(player, id, "capture time", "koths." + id + ".settings.capture-time-seconds");
        } else if (slot == KOTH_MAX_TIME_SLOT) {
            promptTime(player, id, "max time", "koths." + id + ".settings.max-time-seconds");
        } else if (slot == KOTH_MINIMUM_PLAYERS_SLOT) {
            prompt(player, "minimum players", "whole number", input -> {
                try {
                    save("koths." + id + ".minimum-players", Math.max(0, Integer.parseInt(input)));
                    messages.send(player, "general.saved", Map.of("setting", "minimum players"));
                    editorSounds.save(player);
                } catch (NumberFormatException ex) {
                    messages.send(player, "general.invalid-number", Map.of("hint", "Use 0 or higher."));
                    editorSounds.error(player);
                }
                openKoth(player, id, false);
            }, () -> openKoth(player, id, false));
        } else if (slot == KOTH_STARTER_ITEM_SLOT) {
            Optional<ItemStack> cursorItem = CursorItemEditor.cloneItem(cursor);
            if (cursorItem.isEmpty()) {
                messages.send(player, "editor.cursor-empty");
                editorSounds.addItemError(player);
                openKoth(player, id, false);
                return;
            }
            saveKothValues(id, Map.of(
                "starter", ItemDefinition.fromItem(cursorItem.get()).toMap(),
                "starter.enabled", true
            ));
            messages.send(player, "general.saved", Map.of("setting", "starter item"));
            editorSounds.addItem(player);
            openKoth(player, id, false);
        } else if (slot == KOTH_TELEPORT_SLOT) {
            if (koth.zone().center() != null) {
                player.teleport(koth.zone().center());
                sounds.play(player, "koth.teleported");
            }
        } else if (slot == KOTH_REWARDS_SLOT) {
            openRewardCommands(player, id, 0);
        } else if (slot == KOTH_BOSSBAR_SLOT) {
            boolean enabled = !koth.bossbar().enabled();
            save("koths." + id + ".bossbar.enabled", enabled);
            messages.send(player, "general.saved", Map.of("setting", "bossbar"));
            editorSounds.toggle(player, enabled);
            openKoth(player, id, false);
        } else if (slot == KOTH_SCOREBOARD_SLOT) {
            boolean enabled = !koth.scoreboard().enabled();
            save("koths." + id + ".scoreboard.enabled", enabled);
            messages.send(player, "general.saved", Map.of("setting", "scoreboard"));
            editorSounds.toggle(player, enabled);
            openKoth(player, id, false);
        } else if (slot == KOTH_HOLOGRAM_SLOT) {
            boolean enabled = !koth.hologram().enabled();
            save("koths." + id + ".hologram.enabled", enabled);
            messages.send(player, "general.saved", Map.of("setting", "hologram"));
            editorSounds.toggle(player, enabled);
            openKoth(player, id, false);
        } else if (slot == KOTH_ACTIONBAR_SLOT) {
            boolean enabled = !koth.actionbar().enabled();
            save("koths." + id + ".actionbar.enabled", enabled);
            messages.send(player, "general.saved", Map.of("setting", "actionbar"));
            editorSounds.toggle(player, enabled);
            openKoth(player, id, false);
        } else if (slot == KOTH_TEAM_MODE_SLOT) {
            boolean enabled = !koth.teamMode();
            save("koths." + id + ".settings.team-mode", enabled);
            messages.send(player, "general.saved", Map.of("setting", "team mode"));
            editorSounds.toggle(player, enabled);
            openKoth(player, id, false);
        } else if (slot == KOTH_DELETE_SLOT) {
            openConfirmDelete(player, id);
        } else if (slot == KOTH_BACK_SLOT) {
            editorSounds.back(player);
            openKoths(player, 0, "", false);
        }
    }

    private void clickSettings(Player player, int slot) {
        for (String feature : SETTINGS_FEATURES) {
            if (slot == settingsSlot(feature)) {
                boolean enabled = !plugin.getConfig().getBoolean("features." + feature, true);
                save("features." + feature, enabled);
                messages.send(player, "general.saved", Map.of("setting", feature));
                editorSounds.toggle(player, enabled);
                openSettings(player, false);
                return;
            }
        }
        if (slot == SETTINGS_BACK_SLOT) {
            editorSounds.back(player);
            openMain(player, false);
        }
    }

    private void clickScheduleEdit(Player player, String id, int slot) {
        KothSchedule schedule = config.schedules().get(id.toLowerCase(Locale.ROOT));
        if (schedule == null) {
            messages.send(player, "editor.invalid-schedule", Map.of("schedule", id));
            editorSounds.error(player);
            openSchedules(player, 0, "", false);
            return;
        }
        if (slot == SCHEDULE_TOGGLE_SLOT) {
            boolean enabled = !schedule.enabled();
            save("schedules." + id + ".enabled", enabled);
            messages.send(player, "general.saved", Map.of("setting", "schedule enabled"));
            editorSounds.toggle(player, enabled);
            openSchedule(player, id, false);
        } else if (slot == SCHEDULE_CRON_SLOT) {
            prompt(player, "cron", "minute hour day month weekday, ex: 0 18 * * *", input -> {
                try {
                    new CronExpression(input);
                    save("schedules." + id + ".cron", input.trim());
                    messages.send(player, "general.saved", Map.of("setting", "cron"));
                    editorSounds.save(player);
                } catch (RuntimeException ex) {
                    messages.send(player, "editor.invalid-cron", Map.of("error", ex.getMessage() == null ? "Invalid syntax." : ex.getMessage()));
                    editorSounds.error(player);
                }
                openSchedule(player, id, false);
            }, () -> openSchedule(player, id, false));
        } else if (slot == SCHEDULE_RANDOM_SLOT) {
            save("schedules." + id + ".random", !schedule.random());
            messages.send(player, "general.saved", Map.of("setting", "random KOTH"));
            editorSounds.cycle(player);
            openSchedule(player, id, false);
        } else if (slot == SCHEDULE_KOTHS_SLOT) {
            prompt(player, "scheduled KOTHs", "ids separated by commas, or clear", input -> {
                try {
                    List<String> koths = parseScheduleKoths(input);
                    save("schedules." + id + ".koths", koths);
                    messages.send(player, "general.saved", Map.of("setting", "scheduled KOTHs"));
                    editorSounds.save(player);
                } catch (IllegalArgumentException ex) {
                    messages.send(player, "general.invalid-koth", Map.of("koth", ex.getMessage()));
                    editorSounds.error(player);
                }
                openSchedule(player, id, false);
            }, () -> openSchedule(player, id, false));
        } else if (slot == SCHEDULE_BACK_SLOT) {
            editorSounds.back(player);
            openSchedules(player, 0, "", false);
        }
    }

    private List<KothSchedule> sortedSchedules() {
        return config.schedules().values().stream()
            .sorted(Comparator.comparing(KothSchedule::id))
            .toList();
    }

    private List<String> parseScheduleKoths(String input) {
        String normalized = input == null ? "" : input.trim();
        if (normalized.isBlank() || normalized.equalsIgnoreCase("clear") || normalized.equalsIgnoreCase("none")) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String part : normalized.split("[,;]")) {
            String raw = part.trim();
            if (raw.isBlank()) {
                continue;
            }
            String kothId = TextUtil.id(raw);
            if (!TextUtil.validId(raw) || kothManager.koth(kothId) == null) {
                throw new IllegalArgumentException(raw);
            }
            ids.add(kothId);
        }
        return List.copyOf(ids);
    }

    private String joinedKoths(KothSchedule schedule) {
        return schedule.koths().isEmpty() ? "None" : String.join(", ", schedule.koths());
    }

    private String scheduleMode(KothSchedule schedule) {
        return schedule.random() ? "Pick one random listed KOTH" : "Start every listed KOTH";
    }

    private String nextScheduleText(KothSchedule schedule) {
        int seconds = scheduleManager.secondsUntilNext(schedule.id(), null);
        return seconds < 0 ? "Never" : TimeUtil.format(seconds, config.timerFormat());
    }

    private String enabledText(boolean enabled) {
        return enabled ? "Enabled" : "Disabled";
    }

    private String featureDisplayName(String feature) {
        return switch (feature) {
            case "starter-items" -> "Starter Items";
            case "scoreboards" -> "Scoreboards";
            case "bossbars" -> "Bossbars";
            case "holograms" -> "Holograms";
            case "webhooks" -> "Discord Webhooks";
            case "placeholderapi" -> "PlaceholderAPI";
            case "team-hooks" -> "Team Hooks";
            default -> capitalize(feature);
        };
    }

    private String capitalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return input.substring(0, 1).toUpperCase(Locale.ROOT) + input.substring(1).toLowerCase(Locale.ROOT);
    }

    private String enabledColor(boolean enabled) {
        return enabled ? "&#3ecf8e" : "&#ff5d73";
    }

    private void clickConfirm(Player player, String id, int slot) {
        if (slot == CONFIRM_DELETE_SLOT) {
            kothManager.delete(id);
            messages.send(player, "editor.deleted", Map.of("entry", id));
            editorSounds.delete(player);
            openKoths(player, 0, "", false);
        } else if (slot == CONFIRM_CANCEL_SLOT) {
            editorSounds.back(player);
            openKoth(player, id, false);
        }
    }

    private void promptTime(Player player, String id, String field, String path) {
        prompt(player, field, "300, 10h, 1h30m, or -1", input -> {
            try {
                save(path, TimeUtil.parseSeconds(input));
                messages.send(player, "general.saved", Map.of("setting", field));
                editorSounds.save(player);
            } catch (IllegalArgumentException ex) {
                messages.send(player, "general.invalid-time");
                editorSounds.error(player);
            }
            openKoth(player, id, false);
        }, () -> openKoth(player, id));
    }

    private void prompt(Player player, String field, String format, java.util.function.Consumer<String> callback, Runnable cancel) {
        TextDialogRequest request = request(field, format);
        boolean warnedBefore = warnNativeFallbackIfNeeded(player);
        boolean openedNative = EditorDialogInputs.openTextFromInventory(
            plugin,
            plugin.core().inventoryCloseSuppressor(),
            plugin.core().dialogService(),
            player,
            request,
            callback,
            () -> {
                messages.send(player, "general.cancelled");
                editorSounds.back(player);
                cancel.run();
            }
        );
        if (!openedNative && !warnedBefore) {
            warnNativeFallbackIfNeeded(player);
        }
    }

    private TextDialogRequest request(String field, String format) {
        String lower = (field + " " + format).toLowerCase(Locale.ROOT);
        List<String> body = List.of(
            FoStyle.MUTED + "Enter " + field + ".",
            FoStyle.MUTED + "Expected: " + FoStyle.WHITE + format
        );
        if (lower.contains("search")) {
            return new TextDialogRequest(
                "Search",
                List.of(FoStyle.MUTED + "Enter search text."),
                "Search",
                "",
                format,
                DialogButton.search(),
                DialogButton.cancel(),
                320,
                DEFAULT_INPUT_WIDTH,
                64,
                true,
                true,
                false
            );
        }
        if (lower.contains("command")) {
            return TextDialogRequest.command(body, "", format);
        }
        if (lower.contains("number") || lower.contains("players") || lower.contains("time") || lower.contains("seconds")) {
            return TextDialogRequest.number(body, "", format);
        }
        return TextDialogRequest.text(body, "", format);
    }

    private boolean warnNativeFallbackIfNeeded(Player player) {
        NativeDialogAvailability availability = plugin.core().nativeDialogs().availability();
        if (!availability.configEnabled()
                || plugin.core().nativeDialogs().canUseNativeDialogs(player)
                || !plugin.core().nativeDialogs().warnOnFallback()) {
            return false;
        }
        if (!player.hasPermission("fokoth.admin")) {
            return false;
        }
        if (!fallbackWarnedPlayers.add(player.getUniqueId())) {
            return false;
        }
        messages.send(player, "editor.native-dialog-fallback");
        return true;
    }

    private void save(String path, Object value) {
        config.set(path, value);
        reloadAfterSave();
    }

    private void saveKothValues(String id, Map<String, Object> values) {
        config.setKothValues(id, values);
        reloadAfterSave();
    }

    private void saveScheduleValues(String id, Map<String, Object> values) {
        config.setScheduleValues(id, values);
        reloadAfterSave();
    }

    private void reloadAfterSave() {
        kothManager.reload();
        scheduleManager.reload();
        if (plugin instanceof FoKoTH foKoTH) {
            foKoTH.refreshIntegrations();
        }
    }

    private String title(String title) {
        return GuiTitles.format(title);
    }

    private void fill(Inventory inventory) {
        ItemStack filler = EditorItemFactory.filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private void set(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    private ItemStack item(Player player, Material material, String name, List<String> lore) {
        return EditorItemFactory.item(player, material, name, lore);
    }

    private ItemStack button(Player player, String color, Material material, String name, List<String> information, String action) {
        return EditorItemFactory.button(player, material, color, name, information, action);
    }

    private record KothBrowserContext() {
    }

    private record ScheduleBrowserContext() {
    }

    private record RewardBrowserContext(String kothId) {
    }

    private ItemStack toggleItem(Player player, boolean enabled, String name, List<String> lore) {
        return button(player, enabledColor(enabled), enabled ? Material.LIME_DYE : Material.RED_DYE, name, lore,
                "toggle " + name.toLowerCase(Locale.ROOT));
    }

    private Material starterMaterial(KothConfig koth) {
        Material material = koth.starterItem().material();
        if (material == null || material.isAir() || !material.isItem()) {
            return Material.STONE;
        }
        return material;
    }

    private int settingsSlot(String feature) {
        return switch (feature) {
            case "schedules" -> 10;
            case "starter-items" -> 11;
            case "scoreboards" -> 12;
            case "bossbars" -> 13;
            case "holograms" -> 14;
            case "webhooks" -> 15;
            case "placeholderapi" -> 16;
            case "filter" -> 20;
            case "team-hooks" -> 19;
            default -> -1;
        };
    }

}
