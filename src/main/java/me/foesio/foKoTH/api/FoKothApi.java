package me.foesio.foKoTH.api;

import org.bukkit.plugin.Plugin;

import java.util.Collection;

/** FoKoTH's plugin-owned integration service. Obtain it from Bukkit's ServicesManager. */
public interface FoKothApi {

    /** Current immutable snapshots of configured hills, including inactive hills. */
    Collection<HillStatus> hills();

    /** Current snapshot, or null when the ID is unknown. IDs are case-insensitive. */
    HillStatus hill(String id);

    /** Starts a hill with admin-style minimum-player handling. */
    KothActionResult start(String id);

    /** Stops a running hill with an API finish reason. */
    KothActionResult stop(String id);

    /** Registers a team source selected by hooks.team-plugin. One owner per ID. */
    boolean registerTeamResolver(Plugin owner, String id, TeamResolver resolver);

    void unregisterTeamResolver(Plugin owner, String id);

    /** All enabled rules must allow a player to contest a hill. One rule per owner. */
    void registerParticipationRule(Plugin owner, ParticipationRule rule);

    void unregisterParticipationRule(Plugin owner);
}
