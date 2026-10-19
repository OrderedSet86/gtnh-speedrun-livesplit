# GTNH Speedrun LiveSplit

Splits LiveSplit on milestones that the [GTNH Speedrun Audit](../gtnh-speedrun-audit) mod logs. The timer starts when
the audit starts timing. Each split happens when its audit event is logged, with LiveSplit's game time set to the IGT
of that event.

## Install

- Server: this mod and the audit mod.
- Each runner's client: this mod. Clients without it can still join.
- Singleplayer: both mods.

## Timer setup

Desktop LiveSplit:

1. Right-click LiveSplit, then Control, then Start TCP/WS Server. To start the server with LiveSplit, set Startup
   Behavior in the settings.
2. Compare against Game Time.

LiveSplit One:

1. Start Minecraft. The mod listens on `ws://localhost:16835` from the main menu on.
2. Settings, Server Connection, Connect, then `ws://localhost:16835`.
3. Compare against Game Time.

LiveSplit One doesn't reconnect by itself. After a Minecraft restart, connect again.

A browser can block the public LiveSplit One page from connecting to `localhost`. Chrome asks for Local Network Access
("Apps on device" in newer versions). Allow it for one.livesplit.org.

## Splits file

`config/gtnhspeedrunlivesplit-splits.json` on the server. The mod writes a sample on first start. The list order is the
order of the splits in the LiveSplit file.

```json
{
  "start": {"event": "timing_started"},
  "splits": [
    {"name": "Nether", "event": "dim_first_visit", "match": {"dim": -1}},
    {"name": "Moon", "event": "dim_first_visit", "match": {"dim": 28}}
  ]
}
```

`event` is an audit log event type. `match` lists field values the event must have.

## Config

`config/gtnhspeedrunlivesplit.cfg`:

| Setting | Default | Side |
|---|---|---|
| `enabled` | `true` | server |
| `timerTarget` | `BOTH` (`OFF`, `DESKTOP`, `ONE`, `BOTH`) | client |
| `desktopPort` | `16834` | client |
| `lsoPort` | `16835` | client |
| `skipMissedSplits` | `true` | client |

With `skipMissedSplits`, a split reached before an earlier one skips the earlier ones. Without it, the timer waits for
each split in order.

## Build

The audit mod is not on a public maven yet. Publish it locally first:

```bash
cd ../gtnh-speedrun-audit && VERSION=local ./gradlew publishToMavenLocal
```

Test in game with `runServer25` and `runClient25`. On Java 8, `runServer` and `runClient` skip both jars, because they
contain Java 21 classes.
