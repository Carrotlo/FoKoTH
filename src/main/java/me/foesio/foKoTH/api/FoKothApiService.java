package me.foesio.foKoTH.api;

import me.foesio.foKoTH.FoKoTH;
import me.foesio.foKoTH.api.event.FoKothFinishReason;
import me.foesio.foKoTH.hook.TeamService;
import me.foesio.foKoTH.koth.KothManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;

/** Runtime adapter; consumers should depend on FoKothApi only. */
public final class FoKothApiService implements FoKothApi, Listener {

    private final FoKoTH plugin;
    private final KothManager hills;
    private final TeamService teams;
    private final boolean folia;
    private final Method globalTickCheck;

    public FoKothApiService(FoKoTH plugin, KothManager hills, TeamService teams) {
        this.plugin = plugin;
        this.hills = hills;
        this.teams = teams;
        this.folia = plugin.core().scheduler().isFolia();
        this.globalTickCheck = folia ? findGlobalTickCheck() : null;
    }

    @Override
    public Collection<HillStatus> hills() {
        checkThread();
        checkAvailable();
        return hills.statuses();
    }

    @Override
    public HillStatus hill(String id) {
        checkThread();
        checkAvailable();
        return hills.status(id);
    }

    @Override
    public KothActionResult start(String id) {
        checkThread();
        return plugin.isEnabled() ? hills.startDetailed(id, null, KothManager.StartReason.API) : KothActionResult.PLUGIN_DISABLED;
    }

    @Override
    public KothActionResult stop(String id) {
        checkThread();
        return plugin.isEnabled() ? hills.stopDetailed(id, FoKothFinishReason.API, true) : KothActionResult.PLUGIN_DISABLED;
    }

    @Override
    public boolean registerTeamResolver(Plugin owner, String id, TeamResolver resolver) {
        checkThread();
        return plugin.isEnabled() && teams.registerResolver(owner, id, resolver);
    }

    @Override
    public void unregisterTeamResolver(Plugin owner, String id) {
        checkThread();
        teams.unregisterResolver(owner, id);
    }

    @Override
    public void registerParticipationRule(Plugin owner, ParticipationRule rule) {
        checkThread();
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("FoKoTH is disabled");
        }
        hills.registerParticipationRule(Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(rule, "rule"));
    }

    @Override
    public void unregisterParticipationRule(Plugin owner) {
        checkThread();
        hills.unregisterParticipationRule(owner);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        Plugin owner = event.getPlugin();
        teams.unregisterOwner(owner);
        hills.unregisterParticipationRule(owner);
    }

    private void checkThread() {
        if (!folia && Bukkit.isPrimaryThread()) {
            return;
        }
        if (folia && globalTickCheck != null) {
            try {
                if (Boolean.TRUE.equals(globalTickCheck.invoke(null))) {
                    return;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        throw new IllegalStateException("FoKoTH API must be called from the server/global tick thread");
    }

    private Method findGlobalTickCheck() {
        try {
            return Bukkit.class.getMethod("isGlobalTickThread");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private void checkAvailable() {
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("FoKoTH is disabled");
        }
    }
}
