# GTNH Speedrun LiveSplit

Splits LiveSplit on milestones that the [GTNH Speedrun Audit](../gtnh-speedrun-audit) mod logs. The server has a
catalog of named milestones. Each runner picks which milestones are the segments of their own LiveSplit file. The timer
starts when the audit starts timing. Each split happens when its milestone is first reached, with LiveSplit's game time
set to the in-game time (IGT) of that moment.

A runner who joins a run in progress gets the run so far: their timer starts and splits each milestone already
reached, at the IGT it was reached.

## Install

- Server: this mod and the audit mod.
- Each runner's client: this mod. Clients without it can still join.
- Singleplayer: both mods.

The server and the clients need the same version of this mod. The server logs `hello.rejected` for a client of
another version, and that client's timer doesn't split. The audit mod needs a version with the history API
(`AuditEvents.subscribeWithHistory`).

## Timer setup

Pick your route first (see [Your route](#your-route)), then join the server and type `/livesplit export` in chat. The
mod writes `.minecraft/livesplit/<category>.lss` with a segment for each entry of your route, with its name and icon. Click the file name in
chat to open the folder. Open the file in desktop LiveSplit (Open Splits) or LiveSplit One (Splits, Import) once,
before the first run. The file has no times, and the first run sets the Personal Best. An export never replaces a
file: a second export of `Moon%` writes `Moon% (2).lss`.

The icons are the items as your client draws them in the inventory, drawn once on export. They need Video Settings,
Enable FBOs.

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

## Joining a run in progress

When a runner joins, the server sends the catalog, then every milestone reached so far, in the order reached, then
the current IGT. The client turns them into splits of the runner's route. A runner who rejoins gets the same list, and
no segment splits twice.

The server rebuilds the list from the audit log on each start, so a server restart loses nothing. LiveSplit's Game
Time of a caught-up split is the IGT of the milestone. Its Real Time is when the client caught up.

After a rollback (a backup restore, or a crash before the world was saved), the milestones that the world lost are
gone from the list. On rejoin, the timer is unsplit back to the milestones the world still has. A lost milestone
splits again when the run reaches it again, at the new IGT. The audit log keeps the lost lines.

The server is the authority. While the run is going, the client checks the timer about once a second, and a split made
by hand past the server's list is undone.

## Milestone file

`config/gtnhspeedrunlivesplit-milestones.json` on the server. The mod writes a sample on first start.

```json
{
  "game": {"name": "GregTech: New Horizons", "category": "Moon%", "icon": "gregtech:gt.blockmachines@1000"},
  "start": {"event": "timing_started"},
  "defaultSplits": ["coke_oven", "nether"],
  "milestones": [
    {
      "id": "coke_oven",
      "name": "Coke Oven",
      "icon": "gregtech:gt.blockmachines@236",
      "event": "multiblock_formed",
      "match": {"class": "MTECokeOven"}
    },
    {"id": "nether", "name": "Nether", "icon": "minecraft:netherrack@0", "event": "dim_first_visit", "match": {"dim": -1}}
  ]
}
```

- `id`: `a`-`z`, `0`-`9`, `_`, `.` and `-`, at most 64 characters, each id once. Runners name milestones by id.
- `event`: an audit log event type. `match`: field values the event must have. A milestone is reached by the first
  matching event. Later matches change nothing.
- `name`: one line, at most 200 characters.
- `icon`: an item, `modid:name@meta`: the item name that `/give` takes, and the data value.
- `defaultSplits`: the route of a runner with no route file, with the entries of [Your route](#your-route). Without
  it, the route is every milestone in file order.
- `game`, `icon` and `defaultSplits` are optional. The defaults for `game` are `GregTech: New Horizons` and `Any%`.

The sample milestones are Coke Oven, Nether, Bricked Blast Furnace, Electric Blast Furnace and Moon. A server of an
older version had `gtnhspeedrunlivesplit-splits.json`. The mod refuses to start with only that file: give each split an
`id`, move the list into `milestones` of the new file, then delete the old file.

## Your route

`config/gtnhspeedrunlivesplit-mysplits.json` on your client. Each entry is one segment of your LiveSplit file, in
order. Without the file, your route is the server's `defaultSplits`.

```json
{"splits": [
  "coke_oven",
  {"id": "nether", "name": "-Nether", "icon": "minecraft:obsidian@0"},
  {"name": "Steel + Tin", "allOf": ["steel", "tin"]},
  {"name": "Any Rubber", "anyOf": ["rubber_tree", "rubber_sapling"]}
]}
```

- A milestone id: the segment takes the milestone's name and icon.
- `{"id": …}` with a `name` or `icon` of its own. A name that starts with `-` is a subsplit in LiveSplit.
- `allOf`: for milestones a run can reach in any order. The segment splits when the last of them is reached, at that
  milestone's IGT.
- `anyOf`: the segment splits at the first of them.

LiveSplit splits its segments in a fixed order. When a later segment is reached first, `skipMissedSplits` decides what
happens (see [Config](#config)). Group milestones that come in any order with `allOf`.

The client reads the file on each join. A segment that names a milestone the server doesn't have is left out, and chat
lists the ids. A file that can't be read gives no segments, and chat says why. Export the LiveSplit file again after you
change your route.

Commands:

- `/livesplit milestones [page]`: the server's milestones, with ids and the IGT of each one reached.
- `/livesplit route [page]`: your segments, with the IGT of each one reached, or how many milestones of a group are
  reached.
- `/livesplit export`: write the LiveSplit file of your route.

## Config

`config/gtnhspeedrunlivesplit.cfg`:

| Setting | Default | Side |
|---|---|---|
| `enabled` | `true` | server |
| `timerTarget` | `BOTH` (`OFF`, `DESKTOP`, `ONE`, `BOTH`) | client |
| `desktopPort` | `16834` | client |
| `lsoPort` | `16835` | client |
| `skipMissedSplits` | `true` | client |

With `skipMissedSplits`, a segment reached before an earlier one skips the earlier ones, and a skipped segment never
splits. Without it, the timer waits for each segment in order. A segment that then splits after a later one gets the
later one's time, so no segment time is negative.

## Build

The audit mod is not on a public maven yet. Publish it locally first:

```bash
cd ../gtnh-speedrun-audit && VERSION=local ./gradlew publishToMavenLocal
```

Test in game with `runServer25` and `runClient25`. On Java 8, `runServer` and `runClient` skip both jars, because they
contain Java 21 classes.
