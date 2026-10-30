![FoKoTH cover](docs/assets/cover.png)

# FoKoTH

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/fokoth) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/fokoth/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Scheduled King of the Hill events with rewards and team integrations.

*Developed by Carrotio.*

## Features

- Runs configurable King of the Hill capture events.
- Supports schedules, rewards, score gain, and timed capture progress.
- Integrates with a broad range of team, faction, clan, and party plugins.
- Provides editor screens for hills, schedules, and reward commands.
- Exposes lifecycle Bukkit events and detailed PlaceholderAPI values.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/fokoth). Check the [versions page](https://modrinth.com/plugin/fokoth/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** `PlaceholderAPI`, `BattlePass`, `BetterTeams`, `Factions`, `FactionsUUID`, `GangsPlus`, `Guilds`, `Kingdoms`, `SuperiorSkyblock2`, `Towny`, `UltimateClans`, `Parties`, `AxParties`, `ZelTeams`, `FoTeams`

## Screenshots

![Edit a KOTH event.](docs/assets/screenshots/fokoth-koth-edit.png)

*Edit a KOTH event.*

![Manage KOTH settings in game.](docs/assets/screenshots/fokoth-main-editor.png)

*Manage KOTH settings in game.*

![Configure reward commands.](docs/assets/screenshots/fokoth-reward-commands.png)

*Configure reward commands.*

![Browse event schedules.](docs/assets/screenshots/fokoth-schedule-browser.png)

*Browse event schedules.*

![Edit a schedule.](docs/assets/screenshots/fokoth-schedule-edit.png)

*Edit a schedule.*

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/fokothadmin <version|reload|editor|list|info|start|stop>` — FoKoTH admin command. Aliases: `/kothadmin`, `/fokoth`, `/koth`.

### Permissions

- `fokoth.admin` — Access FoKoTH admin tools. Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

Requires PlaceholderAPI.

- `%fokoth_player_wins%` — Player's recorded KOTH wins.
- `%fokoth_team_wins%` — Player team's recorded KOTH wins.
- `%fokoth_next% / %fokoth_next_formatted%` — Time until the next scheduled KOTH.
- `%fokoth_next_<schedule>% / %fokoth_next_<schedule>_formatted%` — Time until a named schedule.
- `%fokoth_<koth>_active%` — Whether the named KOTH is active.
- `%fokoth_<koth>_name% / %fokoth_<koth>_display_name%` — Configured identifier or display name.
- `%fokoth_<koth>_world% / %fokoth_<koth>_x% / %fokoth_<koth>_y% / %fokoth_<koth>_z%` — Configured capture location.
- `%fokoth_<koth>_capturer_name% / %fokoth_<koth>_capturer_team_name%` — Current capturer information.
- `%fokoth_<koth>_time_left% / %fokoth_<koth>_time_left_formatted%` — Remaining capture time.
- `%fokoth_<koth>_max_time_left% / %fokoth_<koth>_max_time_left_formatted%` — Remaining maximum event time.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `schedules.yml` — KOTH schedule definitions.
- `sounds.yml` — Configurable interaction sounds.
- `koths/*.yml` — KOTH definitions.

Main config areas: `native-dialogs`, `features`, `discord-webhook`, `storage`, `hooks`, `hook-settings`, `timer-format`, `capture-broadcast-every-seconds`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

Compile against the matching jar (`provided` in Maven, `compileOnly` in Gradle); install it locally if needed. Declare `FoKoTH` in your plugin metadata (`softdepend` for an optional integration).

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoKoTH</artifactId>
  <version>1.7</version>
  <scope>provided</scope>
</dependency>
```

```java
import me.foesio.foKoTH.api.FoKothApi;
import me.foesio.foKoTH.api.event.FoKothCaptureEndEvent;

FoKothApi api = Bukkit.getServicesManager().load(FoKothApi.class);
if (api != null) api.hills();

@EventHandler
public void onFoKothCaptureEndEvent(FoKothCaptureEndEvent event) {
    Bukkit.getLogger().info(event.getClass().getSimpleName());
}
```

**API methods** (overloads grouped):

- `FoKothApi`: `hills`, `hill`, `start`, `stop`, `registerTeamResolver`, `unregisterTeamResolver`, `registerParticipationRule`, `unregisterParticipationRule`.
- `TeamResolver`: `teamName`.
- `ParticipationRule`: `mayContest`.

**Other API types:** `HillStatus`, `KothActionResult`.

**Events:** `FoKothCaptureEndEvent`, `FoKothCaptureStartEvent`, `FoKothCapturedEvent`, `FoKothEvent` (base), `FoKothGainScoreEvent`, `FoKothPreStartEvent`, `FoKothStartEvent`, `FoKothStopEvent`.

[Browse API source](src/main/java/me/foesio/foKoTH/api) for signatures and event details.

</details>
