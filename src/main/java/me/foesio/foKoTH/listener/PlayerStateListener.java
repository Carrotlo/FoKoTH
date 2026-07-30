package me.foesio.foKoTH.listener;

import me.foesio.foKoTH.koth.KothManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class PlayerStateListener implements Listener {

    private final KothManager kothManager;

    public PlayerStateListener(KothManager kothManager) {
        this.kothManager = kothManager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        kothManager.handleDeath(event.getEntity());
    }
}
