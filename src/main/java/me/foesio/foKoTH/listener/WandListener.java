package me.foesio.foKoTH.listener;

import me.foesio.foKoTH.koth.KothManager;
import me.foesio.core.message.FoMessageService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class WandListener implements Listener {

    private final JavaPlugin plugin;
    private final FoMessageService messages;
    private final KothManager kothManager;

    public WandListener(JavaPlugin plugin, FoMessageService messages, KothManager kothManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.kothManager = kothManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!kothManager.isWand(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        if (!event.getPlayer().hasPermission("fokoth.admin")) {
            messages.send(event.getPlayer(), "general.no-permission");
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        Location location = block == null ? event.getPlayer().getLocation().getBlock().getLocation() : block.getLocation();
        kothManager.setSelection(event.getPlayer(), action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK, location);
    }
}
