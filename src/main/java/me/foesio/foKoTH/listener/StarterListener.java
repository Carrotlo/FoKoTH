package me.foesio.foKoTH.listener;

import me.foesio.foKoTH.config.KothConfig;
import me.foesio.foKoTH.koth.KothManager;
import me.foesio.core.message.FoMessageService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class StarterListener implements Listener {

    private final JavaPlugin plugin;
    private final FoMessageService messages;
    private final KothManager kothManager;

    public StarterListener(JavaPlugin plugin, FoMessageService messages, KothManager kothManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.kothManager = kothManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String id = kothManager.starterId(event.getItem());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        KothConfig koth = kothManager.koth(id);
        if (koth == null) {
            messages.send(event.getPlayer(), "general.invalid-koth", Map.of("koth", id));
            return;
        }
        if (!kothManager.starterItemsEnabled() || !koth.starterEnabled()) {
            messages.send(event.getPlayer(), "koth.starter-disabled", Map.of("koth", id));
            return;
        }
        if (kothManager.start(id, event.getPlayer(), KothManager.StartReason.STARTER)) {
            consumeStarter(event);
        }
    }

    private void consumeStarter(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == EquipmentSlot.OFF_HAND) {
            event.getPlayer().getInventory().setItemInOffHand(null);
        } else {
            event.getPlayer().getInventory().setItemInMainHand(null);
        }
    }
}
