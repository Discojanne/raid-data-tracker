# Raid Data Tracker handoff

Read this before changing the Chambers work. The code lives in this clone, on branch `cox-missing-splits`, which was created from `raid-data-tracker-main` at `b11a893`. Chambers logging and the screens are `31f2b28`. Live tracking in `ChambersSession`, the settings redraw, the JSON `lootList` write, the death logger, and dropping an unfinished raid on leave are `636d245` (2026-10-02). That commit is on `discojanne/cox-missing-splits`. The commit after it holds the 2026-10-06 target changes (no-bow Vasa `22`, Muttadiles `13`, calculated PPH) and the later ones: Vespula `0:59`, Guardians `1:50`, saved points `59134`, the three money-run point bonuses, the personal-best line, the gray Target total, Total points, and PPH rows, the death-room grace, the safer log rewrite, the live Times generation, and the NPC menu-index mask. Do not put the saved points default back to `64100`. Do not push unless the owner asks. Do not modify the CoXGrind repo. `PROJECT_CONTEXT.md` there is the old plugin. This file is the one for Raid Data Tracker.

Upstream is `https://github.com/Raitab/raid-data-tracker`, branch `raid-data-tracker-main`. Pushing `cox-missing-splits` there was denied for the GitHub account `Discojanne`. The branch is on `https://github.com/Discojanne/raid-data-tracker`, remote name `discojanne`, and the local branch tracks `discojanne/cox-missing-splits`. `origin` is still the upstream. This clone is `C:\Users\DB96\source\repos\raid-data-tracker`.

## What this work is for

Raid Data Tracker logs Chambers of Xeric, Theatre of Blood, and Tombs of Amascut. Chambers room times were already the right splits: each duration is the time since the previous room or floor, and the walk is included. That matches CoXGrind.

The log goal is a Chambers line that can feed the same facts CoXGrind uses, while Theatre and Tombs keep working as they do now. Room times stay on the durations they already store. Do not reuse a field that already has a meaning. Do not replace `tektonTime` or the other room fields. Do not change points, the chest `lootList`, split GP, or the one-JSON-object-per-line log. Old lines must still load. A full-file rewrite must round-trip any value the screens and the uniques list read.

The screen pieces the owner asked for are in `31f2b28` and `636d245`, and the owner has reviewed them in the developer client. CoX keeps the top tab. Under it the row is Times, Loot, then Points. Opening CoX lands on Times. Loot is the old Chambers screen minus the floor bests and the summed points panel. Times and Points reimplement the CoXGrind Chambers views on this log. CoXGrind is the behavior reference. Do not port its panel, and do not copy its source. Read `PROJECT_CONTEXT.md` in the CoXGrind repo when a Chambers rule is unclear. This file wins when the two disagree.

## Log files

One login has four logs. A raid type has a file. A second login is a second folder, because the folder name is `client.getUsername()`.

Under `%USERPROFILE%\.runelite\raid-data tracker\<username>\`:

| File | What is appended |
| --- | --- |
| `cox\raid_tracker_data.log` | Chambers, when `inRaidChambers` is true |
| `tob\raid_tracker_data.log` | Theatre of Blood, when `inTheatreOfBlood` is true |
| `toa\raid_tracker_data.log` | Tombs of Amascut, when `inTombsOfAmascut` is true |
| `unknown\raid_tracker_data.log` | A write whose raid flag was never set |

The four raid files are one JSON object per line, appended. When Track Vanguards is on, a Challenge Mode room is also written to `cox\vanguards-<hash>.json` in that same Chambers folder. That file is a pretty JSON array, rewritten when a room is saved or removed. It is not a raid line. A leagues world puts `vanguards-<hash>-league.json` in `<username>-league\cox`. Hash `0` and hash `-1` are not keys. The study does not change `raid_tracker_data.log`.

One login that also plays leagues has a second set of the same four files. The folder name comes from `FileReadWriter.accountDirectoryName`: the login name, or that name plus `-league`.

A leagues world is `WorldType.SEASONAL`. `RaidTrackerPlugin.useCurrentLogFolder` points the writer at `<username>-league` on startup when already logged in, on `LOGGING_IN`, and on the first game tick, including the tick after a hop. The main-game files are not rewritten. While that login is on a leagues world, the panel reads the league folder.

A world type that is not known yet does not pick a folder. A folder that was already selected stays until the world type is known. Deadman, beta, tournament, fresh-start, quest-speedrunning, legacy, and EoC worlds are not logged. `WorldUtils.playerOnUntrackedWorld` is that check. Those worlds do not create a folder, and the panel shows the disabled view. PvP and the other normal world flags are logged.

Each raid is one JSON object on one line, appended when that raid is logged. The file order is completion order. `FileReadWriter.writeToFile` picks the file from those three flags, in Theatre, then Tombs, then Chambers order. A missing flag goes to `unknown`.

Theatre and Tombs still write a second line for a later purple, kit, dust, or pet. Chambers does not. A later Chambers unique is another entry on `uniques` on the same line.

`killCountID` is from 2020. It groups those extra Theatre and Tombs lines, and any older Chambers extra lines, so the "last X kills" filter counts one kill. It is not the same thing as `completionCount`. Keep both. New Chambers raids still write `killCountID`, one value per raid.

## Status

Chambers logging on `cox-missing-splits` now writes a Chambers-only line. Theatre and Tombs still write the full object. A leagues world logs under `<username>-league`. Deadman, beta, tournament, fresh-start, quest-speedrunning, legacy, and EoC worlds are not logged. Commit `31f2b28` is on the Discojanne fork. It has not been opened as a pull request. The historical Chambers file has not been rewritten.

The uniques list and the split changer read both the old loot fields and `uniques`, so a new Chambers raid and an old line show the same kind of count. Guardians, the Olm phase splits, the head, Pre-Olm, Between rooms, points, and points per hour are drawn on Times when that raid stored them. Points draws the purple rate, dry streaks, item rates, history, points lost to deaths, the rooms those deaths happened in, and tracked drops for every logged Chambers raid. Times has Average, Target, and Best. Target uses one sheet for every filter. Ice milk adds seconds to the Ice demon target. When it is on, Ice demon splits from `231` through `270` seconds stay in averages, bests, and Last N. Over `270` stays out. The `80` second floor is unchanged. Track Vanguards defaults off. When it is on, a Challenge Mode room is written to the study file beside the Chambers log. Regular raids are not written. Nothing from that study is drawn.

On 2026-09-30 the owner looked at Times and Points in the developer client. The CoX row is Times, Loot, Points. Points has no raid-type or party-size filter. Both pages span the sidebar. A later pass the same day packed Times so the splits and the graph fit. The pace graph still grows into whatever sidebar height is left under the splits. The owner then asked where the Olm phases were, because the Olm section was only the total.

That is the log, not the Times list. The Chambers logs already on this machine were checked the same day. No line contains `olmMageHandTimes`, `olmPhaseTimes`, or `olmHeadTime`. Those raids still have `raidTime` and, when it was logged, `lowerTime`, so Times can draw the Olm total and no phase rows. Do not backfill them. A raid logged with the current plugin stores the phase splits, and Times already draws them.

Later on 2026-09-30 the owner looked at Target on Times and said the side panel looks good. The settings page is one Target times section, not four sheets. They then found Target total still editable the first time that section opened. It is gray and calculated when the section joins the settings page. Do not claim they have opened the settings since that fix.

`RaidTrackerTest` was run on this machine on JDK 17 against RuneLite `1.12.39`. 15 tests passed, including the Chambers log-shape round trip and `TestLeagueLogUsesItsOwnFolder`. Three failed because a new Chambers raid stores the drop on `uniques` and leaves the old receiver fields empty: `TestLootSplits` and `TestPurple` (`specialLootReceiver`), and `TestDustAndKitRecipients` (`dustReceiver`). Those tests still expect the old fields. Do not put Chambers drops back on those fields to turn them green. After the untracked-world change, only `TestLeagueLogUsesItsOwnFolder` was run again, and it passed. `TestRaidTrackerPanel.TestFilter` still fails here: it reads a local Chambers log and expects 9 lines, and the file it opened was empty. That failure is from the local log. Do not invent a 9-line log to turn it green.

`ChambersViewTest` passed on the same JDK and client version: Olm, Pre-Olm, Between rooms, Regular / Regular full / CM / solo, an invalid Tekton left out of the best, phases past 8 hidden, the pace gap, two own purples plus an olmlet, an own purple with no price, a legacy Elder Maul, and death totals that ignore `personalDeathCount`. It was run again after Points stopped requiring `value` on an own purple, and it passed. It was run again after Target, and it passed: one sheet on every filter, the adds, Last N, a team of 8 using four Olm phases, and Target total gray when the section joins the page. It was run again after Ice milk and the death rows, and it passed.

The developer client was started on 2026-09-30 with JDK 17 and RuneLite `1.12.39`. The owner has since used the CoX pages in that client. `compileJava` with `-PrlVersion=1.12.39` succeeded after the later Times layout pass. That layout pass did not change `ChambersTimes`, `ChambersPurples`, or `ChambersFilter`, and `ChambersViewTest` was not re-run for the layout itself. Later the same day Ice milk, the death rows, and the Vanguard study were added. `ChambersViewTest`, `VanguardTrackerTest`, and `VanguardLogTest` passed on JDK 17 and RuneLite `1.12.39`. Do not claim the owner has looked at the Ice milk window or a Vanguard file. They have seen the old per-death rows on Points in their own client. They have not reviewed the room list that replaced those rows.

On 2026-10-01 live Chambers tracking moved out of `RaidTrackerPlugin` into `ChambersSession`. The side panel was not edited for that move. Times, Loot, and Points look and behave as they did before it. The same live Times push still skips a tick whose open second and splits have not changed. After the move, one Gradle run on JDK 17 and RuneLite `1.12.39` executed `RaidTrackerTest`, `ChambersViewTest`, `VanguardTrackerTest`, `VanguardLogTest`, and `SplitCopyTest`. 50 tests ran. 47 passed. The same three failed, for the same reason: `TestLootSplits` and `TestPurple` expected `specialLootReceiver` `K1NG DK` and got an empty string, and `TestDustAndKitRecipients` expected `dustReceiver` `Canvasba` and got an empty string. Do not put Chambers drops back on those fields. The developer client was not started after the move. Do not claim the owner has run a raid on `ChambersSession`.

Later on 2026-10-01 two fixes landed in the working tree and were committed in `636d245`. `onConfigChanged` no longer calls `loadRTList`. A `raidtracker` setting calls `RaidTrackerPanel.redraw()`, which redraws from the raids already loaded and does nothing before the first load. `cmSoloTotal` does not redraw. Login, a league hop, the Refresh button, and Clear data still call `loadRTList`. `FileReadWriter.getJSONString` writes the chest `lootList` as a JSON array. The quote-and-bracket replace is gone. `writeToFile` appends that one line. `updateRTLog` and `updateRTList` build the new text, then replace the file. The reader, the character set, and `CHAMBERS_LOG_FIELDS` are unchanged. `updateRTList` still sets `specialLootInOwnName` from the chest loot before it writes. After these fixes, only `RaidTrackerTest` was run again on JDK 17 and RuneLite `1.12.39`: 19 tests, 16 passed, and the same three failed for the same reason. `ChambersViewTest`, `VanguardTrackerTest`, `VanguardLogTest`, and `SplitCopyTest` were not re-run for these fixes. The developer client was not started after them. Do not claim the owner has tried a setting change or a new save on this writer.

On 2026-10-02 the Chambers death logger stopped watching the points drop for `6` ticks. That logger is in `636d245`. The chat line `Oh dear, you are dead!` stores the death immediately. The loss is 40% of the personal points, or the drop when the score has already fallen. The room is the next room on the full fixed layout. Points no longer draws a death rate or one row per death. It draws points lost, as purples, as raids, and one row per room. There is no `deathsTracked` flag. `deaths` stays a list and is omitted when empty, the same way `uniques` is. The same day, `RaidTrackerTest` and `ChambersViewTest` were run together on JDK 17 and RuneLite `1.12.39`: 41 tests, 38 passed, and the same three failed for the same reason. The developer client was not started. Do not claim the owner has run a raid on this death logger. `636d245` is the commit for that day, and it is on `discojanne/cox-missing-splits`. The target changes from 2026-10-06, and the later target, death, log, live Times, and menu-index changes, are in the commit that adds this handoff.

On 2026-10-12 Target times gained a gray Total points row under Target total. The editable Total Points box stays at the bottom and stores the plain number. Only the added amount in a parenthetical is colored. `ChambersViewTest` passed on JDK 17 that morning. The developer client was not started for that settings pass. Do not claim the owner has looked at the colored diffs.

`build.gradle` resolves the client with `findProperty('rlVersion') ?: 'latest.release'`. On 2026-10-11 that release is RuneLite `1.13.1`. Do not pass `-PrlVersion=1.12.39` or `-PrlVersion=1.13.2-SNAPSHOT`. The snapshot has no plugin hub manifest, so the hub plugins disappear from the sidebar. `run` must pass `-ea`, because `ExternalPluginManager.loadBuiltin` will not start without it. On 2026-10-07 the game packed extra bits into NPC menu identifiers. `1.13.1` uses that whole value as the NPC index, so `MenuEntry.getNpc()` is null on hover. Menu Entry Swapper asserts `npc != null`. The event bus does not catch `AssertionError`, so the client thread dies and the game freezes. `PatchMenuIndex` applies the `1.13.2` mask, `identifier & 0xFFFF`, to `getNpc` and `getPlayer` in the released injected client, and `run` loads that jar. `PatchMenuIndexTest` covers the mask. Drop the patch once `latest.release` already masks those lookups. `ItemPrice.getWikiPrice` and `ItemManager.getItemPrice` return `long`. `toIntPrice` narrows them. Guice on JDK 17 needs `--add-opens=java.base/java.lang=ALL-UNNAMED`. The `test` and `run` tasks already pass it. JDK 21 plus Lombok `1.18.20` fails earlier, inside Lombok. The `run` task on this machine uses the JDK 21 that is first on `PATH`.

## What a new Chambers line stores

New raids only. Old lines were not rewritten and were not backfilled. A missing key loads as the Java default. Gson matches names, so key order does not matter to the reader.

Keys, in the order a new line writes them:

- `challengeMode`
- `upperTime`, `middleTime`, `lowerTime`, `raidTime`
- Prep rooms, in Challenge Mode order: `tektonTime`, `crabsTime`, `iceDemonTime`, `shamansTime`, `vanguardsTime`, `thievingTime`, `vespulaTime`, `tightropeTime`, `guardiansTime`, `vasaTime`, `mysticsTime`, `muttadilesTime`
- `olmMageHandTimes`, `olmPhaseTimes`, `olmHeadTime`
- `deaths`, only when you died
- `uniques`, only when a purple, kit, dust, or pet dropped
- `totalPoints`, `personalPoints`, `teamSize`, `completionCount`
- `lootList`
- `uniqueID`, `killCountID`, `date`

`completionCount` is the Chambers kill count from the "count is" chat line. Keep it.

`FreeForAll`, `specialLoot`, `specialLootReceiver`, `specialLootInOwnName`, `specialLootValue`, `kitReceiver`, `dustReceiver`, `petReceiver`, `petInMyName`, `lootSplitReceived`, `lootSplitPaid`, and `extraDrops` are written only when an older line actually has them. A new Chambers raid leaves them out. A rewrite of an old line must keep them.

Left off a new Chambers line, on purpose:

- `accountHash`, `profileType`, `chestOpened`, `raidComplete`, `loggedIn`
- `inRaidChambers`, `inTheatreOfBlood`, `inTombsOfAmascut`
- `raidLevel`, `percentage`, `personalDeathCount`, `totalTeamDeathCount`
- Every Theatre and Tombs room, player, and time field

Loading the cox file sets `inRaidChambers` in memory, because the folder is the raid type and the flag is no longer in the line. Theatre and Tombs files are not given that flag.

`personalDeathCount` stays `0` in memory for Chambers. Tombs uses that field. Do not increment it for a Chambers death, and do not put it back on the cox line.

### Rooms and floors

Room splits take the first time token after `Duration:`. `upperTime`, `middleTime`, and `lowerTime` take the last time token. `Middle level complete! Duration: 7:53 Total: 20:50` stores `20:50`. Floor lines that start with `@mes_hl_mag@` have that token stripped before the `Upper` / `Middle` / `Lower` check.

`raidTime` is the raid-complete duration. The on-screen Olm total is `raidTime - lowerTime` when both are above `0`. There is no stored Olm-total field.

`guardiansTime` is parsed like the other rooms. Times reads it. Loot does not have a Guardians best.

### Olm clock

Varbit `6386`. `100` units is one minute. Leftover units convert at `0.6` seconds: `minutes = units / 100`, `seconds = (units % 100) * 6 / 10`. Store the delta of the clock, then convert. A non-positive result is dropped.

Phase start is a mage-hand spawn, or game object `29881`. Mage-hand NPC ids are `7550` and `7553`. Melee-hand ids are `7552` and `7555`. A second start while a phase is open is ignored.

Expected phases are `3 + partySize / 8`. Unknown size uses `1`, so `3` phases. Party size is varbit `5424`, then `teamSize` on the raid. Mage hand is stored for every phase except the last. Melee hand stores that phase and, on the last phase, starts the head. Head is head-start to the raid-complete clock. The raid-complete chat duration still goes only to `raidTime`.

### Deaths

`deaths` is your own deaths. Teammate deaths are not tracked. You stay in the raid. A death respawns you at the start of the current floor. Once the next floor has been entered, that respawn is the end of the previous floor. Do not treat a Chambers death as a kick outside.

The death line is `Oh dear, you are dead!`. It is shown to the player who died. There is no name on the entry. That line stores the death immediately, on the raid, and the raid line is written when the raid ends.

A deathless raid omits `deaths`, the same way a raid with no unique omits `uniques`. An old line that still says `chambersDeaths` loads into `deaths`. New lines write `deaths` only. There is no `deathsTracked` flag. Do not add one. Death rate is not on Points, so a deathless raid does not need a marker.

Each entry is `pointsLost`, plus `room` when the room is kept. `room` is omitted when it is not. A second death is another entry.

`pointsLost` is 40% of the personal points before the death, rounded. Personal points are `VarPlayer.RAIDS_PERSONAL_POINTS`. If that score has already fallen, the loss is the drop. That drop equals `score / 0.6 - score` when the cut was exactly 40%. Do not divide an unreduced score by `0.6`. A score of `50000` that is still `50000` on the death line stores `20000`. A score that has already fallen from `50000` to `30000` stores `20000` as well.

The room is the next room on the fixed Challenge Mode order, unless the death is under 5 seconds after that room's completion chat. The completion line can arrive first when you die as the room ends, and that death stays on the room that just finished. At 5 seconds it is the next room. The clock is wall time, not the raid timer.

- Nothing finished yet: Tekton
- After Tekton: Crabs
- After Crabs: Ice demon
- After Ice demon: Shamans
- After Shamans: Vanguards
- After Vanguards: Thieving
- After Thieving: Vespula
- After Vespula: Tightrope
- After Tightrope: Guardians
- After Guardians: Vasa
- After Vasa: Mystics
- After Mystics: Muttadiles
- Once an Olm phase or the head has started: Olm, even inside that 5 second window

After Muttadiles, before Olm has started, the room stays unset, unless the death is still inside the 5 second window. Then it is Muttadiles.

Two backups can open a death when the chat line was missed. Varbit `10055` (`ChambersSession.RAID_DEATH_COUNT`, values `1` through `30`) opens one at 40% of the last personal points. `onVarbitChanged` calls `noteRaidDeaths`. A personal-points drop with no death already open stores the measured drop. The varbit and the chat line are the same death until the personal points change. An unchanged points tick does not split them. A second chat line after the points have changed is a new death. A later points drop does not replace a loss that is already above `0`, and it does not add a second death. `finishDeathWatch` fills the loss only when it is still `0`.

`settleChambersDeathRooms` runs before a Chambers write. Challenge Mode, or `11` or more prep rooms with a time above `0`, keeps the room. Any other raid clears the room and keeps the points. Prep rooms are the twelve time fields above, including Guardians.

Converted older lines can contain `deaths` with `pointsLost` `0` and no room. Those rows were produced by converting an older log. They are not live deaths. Do not rewrite them, and do not backfill a raid that has no `deaths` key. On Points they add no points and no room.

### Uniques

`uniques` replaces, on a new Chambers raid, `specialLoot`, `specialLootReceiver`, `specialLootInOwnName`, `specialLootValue`, `kitReceiver`, `dustReceiver`, `petReceiver`, `petInMyName`, and `extraDrops`. One raid, one line. A second unique is another entry.

Each entry is `item`, `receiver`, and `own`. A purple also has `value`, and when they apply, `freeForAll`, `splitReceived`, and `splitPaid`. A kit, dust, or pet does not get those. The chest `lootList` stays. It is the normal chest, not this list.

The uniques screen counts the old fields and the list, so two purples on one raid count as two, and an old line that only has `specialLoot` still counts. Split GP earned is the raid's `lootSplitReceived` plus each entry's `splitReceived`. The split changer edits the list entry for a new Chambers purple, and the old raid fields for an old line or for Theatre and Tombs.

Theatre and Tombs still use the old fields and can still write an extra line. Do not move them onto `uniques` unless the owner asks.

## What the screens show

There is no plugin title above the tabs. Do not put `Raid Data Tracker` back there. The top tabs stay CoX, ToB, and ToA. Theatre and Tombs are unchanged. Under CoX only, a second row is Times, Loot, and Points. The selected subtab is a lighter chip with an orange underline, on a darker bar, so it does not blend into CoX, ToB, and ToA. Opening CoX lands on Times.

Loot is the previous Chambers screen. It keeps the date window, team size, and CM & Normal / Normal Only / CM Only filters, plus Olm (`raidTime - lowerTime`), Overall, kills, uniques seen versus received, split GP, regular chest drops, and the split changer. Best Upper, Middle, and Lower are gone. The summed Personal Points and Total Points panel is gone on CoX. A time that is not above `0` stays absent. No new log keys.

Times has Average, Target, Best, and a narrower Filter tab on that same strip. Filter is not remembered. It starts closed. Opening it shows the raid-type and party-size dropdowns under the strip, and the splits stay. Clicking Filter again closes them. Those two filters are stored in the `raidtracker` config group as `chambersMode` and `chambersSize`. They are not a settings row. Both default to All. Average, Target, Best, the Best choice, Target's This / Last, and Last N are not remembered. The kill count sits on the right of the Rooms header, in that same bar. The slot stays the width of `In progress` on every Times view that draws a section header. Average, Best raid time, and Best PPH fill it with `KC`, `CM`, or `In progress`. Best Splits leaves it empty. Do not hide the slot, and do not put `Fastest in this filter` in it. Do not put the filter dropdowns back on their own row above the strip. Points has no mode or size filter. Points counts every logged Chambers raid, and the summary header says `All raids`. The Times choices are All, Regular, Regular full, or CM, and party size All, Solo, Team, or each size of `2` or more that appears in the log. Regular is not CM and has fewer than `11` prep rooms with a time above `0`. Regular full is not CM and has `11` or more. CM stays CM even with `11` rooms. A raid with `teamSize` of `0` or less matches All only. Solo is `1`. Team is `2` or more. The size menu is built from the unfiltered log. The Times rows use the filtered list.

Times and Points span the sidebar. The pages stay center-aligned in the parent box. A left-aligned page there only receives the right half, which is the empty-left bug. Do not set `ChambersTimesView` or `ChambersPurpleView` to left alignment. Their own children are left-aligned together, and their maximum width is `Short.MAX_VALUE`, not `Integer.MAX_VALUE`. The Average / Target / Best / Filter and Splits / Raid time / PPH switches are painted, so a narrow sidebar cannot ellipsize them. Filter is only as wide as its label. When the type or the size is not All, that label stays orange on the other tabs.

Times has Average, Target, and Best. Best has Splits, Raid time, and PPH. Average, Best, the Best choice, Target's This / Last, and Last N are not remembered. Average shows the latest completed raid in the filter, or the live raid when one is open. Rows are the prep rooms that have a time, including Guardians, then Pre-Olm, mage hands (`Mage P1` onward), phases `1` through `8` (`Olm P1` onward), Olm head, Olm, Between rooms, Raid time, Total points, and PPH. Phase `9` and later stay in the log and are not drawn. The last phase has no mage hand. Pre-Olm is the sum of prep times above `0`. Between rooms is `raidTime - preOlm - olm` when all three are above `0`. Average and Target draw an 8px darker gap after Shamans and after Tightrope, with a 2px light-gray bar inset 10px from each edge. Do not put that line back to 1px on the row color. Best does not draw those gaps. Floor durations are not printed. The average beside a row includes the raid on screen. The pace graph does not: a saved raid is compared with the filtered raids before it, and a live raid is compared with every saved raid in the filter. Zero on the graph is the average pace. Positive is ahead. The personal-best line is `average raid time - fastest raid time` in that comparison. Personal best line in Other turns that line off, and the scale then follows the pace on screen. The on-screen raid still shows a time that fails the floors below. That time is left out of averages and bests. Best hides it.

Target's This is that same raid against the one target sheet. Last N is the average of that many filtered raids against the same sheet. The title is `Last 10`. Minus and plus sit on either side. N starts at `10` and stays from `1` to `100`. A click changes it by `1`. Shift changes it by `10`. A room with no valid time in that window is left out. Last N is calculated when it is opened, when N changes, and when a raid is saved. A live tick does not recalculate it. Every filter uses the same sheet, including All. Do not ask for a mode or a size, and do not put the four CoXGrind sheets back. A team of `8` or more uses that raid's phase count, `3 + teamSize / 8`, and at least as many phases as were stored. A mix of phase counts keeps the `3`-phase Olm total and still copies the phase time onto a later phase. The last phase has no mage-hand target. Pre-Olm, Olm, and raid time are calculated. Pre-Olm is the prep targets that are set, after the adds. Olm is the phase target times the phase count, plus the head, plus the gaps. The gap is `60 * (phases - 1) / 2` seconds, and a count under `3` still uses the `3`-phase gap. The settings total always uses `3` phases. Raid time is Pre-Olm plus Olm plus Between rooms, and only when all three are above `0`. A blank room stays out of the sum. An add does not create a target. The pace line uses `target - actual`. Start is `0`. The personal-best line is `target raid time - fastest raid time` in that comparison, the same dashed line Average draws. This uses the raids before the one on screen, or every saved raid when one is live. Last N and Avg use the raids in that average. A raid under `10` seconds is left out. One raid on This has no line. The caption says `ahead of target`, and the second line is against the PB. Personal best line in Other turns that line off on Average and Target together. Off, the best stays out of the scale, so a best you are not close to does not flatten the graph. Mage hand, Pre-Olm, Between rooms, points, and PPH are not points.

Any split under `10` seconds is left out of averages, bests, and Last N. Ice demon counts from `80` through `230` seconds. When Ice milk is on, that room also counts through `270` seconds (`4:30`). Over `270` stays out. Prep rooms and Between rooms stop at `240` seconds. Room floors: Tekton `20`, Crabs, Thieving, and Muttadiles `35`, Shamans `17`, Vanguards `50`, Vespula `5` (the `10` second floor is what applies), Tightrope `15`, Guardians `25`, Vasa and Mystics `20`.

Right-click a row on the Times table and choose Copy splits. The clipboard gets a Discord code block of the rows on screen: unpadded times, a blank line between Rooms, Olm, and Finish, and no comparison diffs. A Best-splits kill count that starts with `CM ` or `KC ` is kept. The title is the badge (`KC`, `CM`, or `In progress`), `Last N` on Target's Last, or the Best choice (`Splits`, `Raid time`, `PPH`) when the badge is empty. `ChambersTimesView.discordSplits` builds that text. `SplitCopyTest` passed on 2026-10-01. Do not put the diffs back into the paste.

The split rows are packed tight. Room names are 12px. The time and the comparison are 13px bold. The Average / Target / Best strip is 22px. A 3px bar sits to the left of the room name. For a time, 20 seconds or more ahead is cyan, ahead is green, under 10 behind is orange, and further behind is red. Total points and PPH are green when ahead and red when behind. No comparison, including Current, is gray. Best times are gold. Best points stay green or red. The pace graph's floor is `156` pixels and it grows into leftover sidebar height, capped at `300`, so Times fills the panel without the graph pushing the page into a scroll on its own. A filter whose rows are taller than the sidebar can still scroll. The line is 2px. Both captions stay at the bottom while Personal best line is on. Off, the pace caption is the only one, and it sits on the bottom row.

On Points, each item icon is scaled into a 28px slot and the count sits further right, so the sprite is not clipped by the number. The diff stays on the right.

The live raid is in memory only. It is pushed when the sidebar is showing, the tab is CoX, the page is Times, and the switch is Average or Target's This. Last N does not follow the tick. The open segment uses the phase-start clock, otherwise the head-start clock, otherwise the Chambers anchor. The anchor is `0` on raid entry and the raid timer when a room or floor chat line lands. Between phases, Current is the time since the last floor. Current is shown only when that open time is above `0`. A tick that does not change the open second or the splits does not rebuild the page. Leaving before the raid is complete clears the live view and writes nothing. A completed raid stays on the live view until the chest write and `reset()`.

Points is the purple board for every logged Chambers raid, with no mode or size filter. Expected chance is `personalPoints / 867600`. Regular weights are `14, 14, 4, 4, 3, 4, 4, 4, 3, 2, 2, 2` (total `60`). CM weights are `12, 12, 4, 4, 3, 4, 4, 4, 3, 2, 2, 2` (total `56`). Own purples are `uniques` entries with `own` and a purple item name. A missing `value` still counts on Points. The recent purple rows on Loot still require `value`. When `uniques` is empty, a legacy `specialLoot` counts if `specialLootInOwnName` is set. Kit, dust, and pet are side items. Two purples on one raid count as two items and one purple raid. Scrolls are Dexterous plus Arcane. Rate is raids divided by expected. Deaths sum `deaths[].pointsLost` when that value is above `0`. Do not use `personalDeathCount`. The boxes are Points lost, As purples (`pointsLost / 867600`), and As raids (`pointsLost / average personal points`, or `--` when that average is `0`). There is no death rate. Under the boxes, each named room is one row: the room and how many deaths happened there. The fullest room is first. A tie goes by name. A death with no room adds its points and no row. History ends with a NEXT square. Tracked drops are newest first and are not capped.

## Plugin config

Config group `raidtracker`. Values are stored in the RuneLite profile and come back next session. `cmSoloTotal`, `cmPointsLine`, and `cmPph` are unset on startup and after a settings change. Those three gray boxes are the calculation, not a saved string. `cmTotalPoints` is the saved number.

General holds the settings that were already on the page: default FFA, FFA cut off, Last X Kills, and the Show toggles. The hidden ToA filter numbers, and `chambersMode` and `chambersSize`, stay off the page.

Using Twisted bow sits above the sections. Then Items, Strategies, and Money run, open. Target times is collapsed. Other is collapsed and holds Track Vanguards, Raids today, and Personal best line. Track Vanguards defaults off. When it is on, Challenge Mode Vanguard rooms are written to `vanguards-<unsigned hash>.json` in the Chambers folder. A leagues world writes `vanguards-<hash>-league.json` in the `<username>-league` Chambers folder. Regular raids are not written. One object per room. `kc` stays null until the Challenge Mode kill-count sentence arrives. A reset is kept. A regular kill-count sentence removes that room. Nothing is drawn, and the raid log is unchanged. Raids today defaults on. On Average and Target it shows how many Chambers raids in the open log were completed since 4:00 local time. A raid before 4:00 still counts as the day before. `date` is epoch milliseconds, set when the raid is logged. Best does not show the count. The count is every raid in that log, not the Times filter. Personal best line defaults on. It draws the dashed line on Average and Target. Turning it off drops that line, the second caption, and the gold past the best. The scale then follows the pace on screen.

Target times is the only sheet. The keys are the old CM solo keys, `cmTekton` through `cmBetweenRooms` and `cmTotalPoints`, so a saved CM solo value stays. Regular solo, Regular team, and CM team are not in the config. Defaults, with the adds off, are Tekton `1:10`, Crabs `0:56`, Ice demon `2:24`, Shamans `1:03`, Vanguards `2:12`, Thieving `1:15`, Vespula `0:59`, Tightrope `0:47`, Guardians `1:50`, Vasa `1:10`, Mystics `1:40`, Muttadiles `1:25`, Olm mage hand `0:56`, Olm phase `1:54`, Olm head `1:06`, Between room time `0:56`, Total Points `59134`. The `3`-phase total is `25:35`. PPH is not typed. It is total points divided by that raid time, truncated the same way as a logged raid. With these defaults that is `138685`. It updates when the time or the points change, including the adds. A longer Olm fight uses that longer time. Target total, Total points, and PPH are gray. Their keys are `cmSoloTotal`, `cmPointsLine`, and `cmPph`. All three are unset on startup and after a settings change. `cmSoloTotal()` still returns the old placeholder `25:30`. The page replaces that with the calculated total when the section joins. Do not treat `25:30` as the total.

Reset to default sits at the top of Target times. It asks, then clears the saved boxes so those defaults show again. Target total (`cmSoloTotal`) is the first row, then Total points (`cmPointsLine`), then PPH (`cmPph`). Those three are filled in when the section joins the page, and again when a room time, the points, or an add changes. They cannot be edited. Target total shows the full time, then the plain time plus the added time, such as `26:53 (25:35 + 1:18)`. Total points shows the full points, then the saved points plus the bonus, such as `64100 (59134 + 4966)`. Only the added amount is colored: time adds are orange under `10` seconds and red from there, and the points add is green. The box text stays the plain line, including `25:35` when nothing is added. When there is an add, a label on top of the box colors only that added amount. The name, the parentheses, the original, and the plus stay the box color. Do not color the whole line. The editable Total Points box (`cmTotalPoints`) stays at the bottom and stores the plain number. A room label with an add reads `(0:59 + 0:15)`, and only `0:15` is colored. The room boxes are built before the section is attached, so graying the total on the field's own add is too early. `TargetTimeSettings` does it when the section joins the page.

Adds change the comparison only, and only when that room already has a target. Off adds time: no twisted bow is Shamans `6`, Vanguards `2`, Vasa `22`, Muttadiles `13`, Olm head `20`, and Tightrope `8` only when killing rope is on. No slayer helm is Shamans `9`. No lockpick is Thieving `4`. No axe is Ice demon `5`. No salve is Mystics `5`. No pre-veng is Tekton `4`. No vesp pot skip is Tightrope `6`. No crab tank is Crabs `4`. On adds time: killing rope `59` to Tightrope, Vesp milk `15` to Vespula, ice milk `iceMilkSeconds` to Ice demon, over-thieve `4` to Thieving, sipping pools `12` to Between rooms, banking after rope `5` to Between rooms. Vesp milk also adds `1000` points, killing rope `3248`, and over-thieve `718`. Those three are taken off the default total, so the saved total is `59134` and all three checked is `64100` again. A blank total stays blank. Ice milk also keeps Ice demon splits over `3:50`, through `4:30`, in averages, bests, and Last N. `iceMilkSeconds` is `@Range` `0` to `600` and defaults to `70`. New item and strategy boxes other than sipping pools and banking after rope default on, so a missing key does not add time.

## What is left

### 1. Live Chambers tracking is in ChambersSession

Done on 2026-10-01 and committed in `636d245`. Structure only. One plugin stays. No Gradle module was added. The Tombs Guice module stays where it was. `RaidTrackerPlugin` is 1,309 lines. `ChambersSession` is 1,016. The plugin keeps `@Subscribe` and calls the session. Do not copy the methods back onto the plugin. `RaidTrackerTest` calls `olmPhaseStarted`, `mageHandDown`, `meleeHandDown`, `stampOlmHead`, `notePersonalPoints`, `noteRaidDeaths`, and `settleChambersDeathRooms` through `chambers()`.

The session owns `olmPhaseStartUnits`, `olmPhase`, `olmMageRecorded`, `olmHeadStartUnits`, `olmFightStarted`, `lastPrepRoom`, `pendingDeath`, `pendingBaseline`, `pendingMin`, `lastPersonalPoints`, `sawPointsSinceDeath`, `tracking`, `chambersLive`, `chambersAnchorUnits`, `lastLiveSignature`, and the Vanguard tracker, log, key, and directory. It owns the Olm, death, unique, live-push, and Vanguard methods that used to sit on the plugin. There is no `pendingTicks` watch.

`onNpcSpawned` calls `npcSpawned` for a mage hand and a Vanguard spawn. `onNpcChanged`, `onNpcDespawned`, and `onHitsplatApplied` call the session for Vanguards. `onActorDeath` calls `actorDied`. `onGameObjectSpawned` calls `olmObjectSpawned`. `onGameTick` calls `notePersonalPoints`, `watchVanguards`, and `pushLiveChambers` when Chambers is running, then keeps the Tombs timer check. `checkRaidPresence` calls `enteredRaid`. Leaving before the raid is complete calls `leftBeforeComplete`. `shutDown` calls `leaveVanguards`. `reset` calls `resetChambersState`. A Chambers write calls `settleChambersDeathRooms`.

`checkChatMessage` still parses room times and writes `tektonTime` and the other room fields. The branch homes are comments on those calls:

- Chambers only: an own death calls `recordChambersDeath`. `onVarbitChanged` calls `noteRaidDeaths` for varbit `10055`. A prep room calls `prepRoomFinished`. The Vanguards room also calls `finishVanguards`. A floor or room duration calls `noteRoomClock`. A Theatre wave that uses the same duration sentence still reaches that clock anchor.
- Shared raid complete: `RAID_COMPLETE_MESSAGE_COX` and `RAID_COMPLETE_MESSAGE_TOA` stay in one `if`. Only Chambers calls `finishDeathWatch` and `stampOlmHead`. Tombs still sets personal points from the points tracker. Percentage, completion, and the date stay in the plugin.
- Shared kill count: Chambers calls `onChambersKillCount`. Theatre still stores the team and marks the raid complete.
- Shared loot, kit, dust, and pet: Chambers calls `addChambersUnique`. Kit and dust use `receivedByYou`. A Chambers pet uses `petItemName`. Theatre and Tombs still use the old receiver fields and can write another line.
- Theatre or Tombs only, with no session call: the Tombs entry reset, the patient-record filter, Theatre waves, Tombs room and completion lines, both Theatre completion lines, the "found something special" line, and the `Team size:` duration that only writes `raidTime`.

`FileReadWriter` still appends one line and still picks the folder. `CHAMBERS_LOG_FIELDS` stays the allowlist. `RaidTracker` stays one object. `WorldUtils` stays. The panel shell stays. Shared settings stay on `RaidTrackerConfig`. The target keys are `cmSoloTotal`, `cmPointsLine`, `cmPph`, `cmTekton` through `cmBetweenRooms`, and `cmTotalPoints`. `TargetTimeSettings` stays the Chambers sheet. Times, Points, and the pace graph stay in `ui/chambers`. The Vanguard package stays `com.raidtracker.vanguard`. The session calls it.

Do not fold the copied caption, diff, and pace helpers in `ChambersTimes` and `ChambersTargets` unless a floor or a caption has to change. Do not give Theatre a session. Do not move Tombs chat into `toapointstracker`. Do not split `RaidTracker` into one class per raid. Do not change a screen or a log key. Do not change a target number except to match Plugin config. Do not claim the owner has run a raid on the moved code.

The screen list is done. Points shows points lost, as purples, as raids, and the rooms died in. There is no death rate and no row per death. Ice milk keeps Ice demon splits through `4:30` when the box is on. Track Vanguards writes the study file when the box is on, and the box defaults off. Copy splits is on the Times table.

The derived Olm row stays `raidTime - lowerTime`. An old line with no phase arrays shows that total and no Mage, phase, or head rows. That is expected. Do not treat it as a Times bug, and do not rewrite the historical file to invent the phases.

### 2. Settings redraw, and one JSON write

Done later on 2026-10-01. A setting redraws the open page from memory. The log files are reread only on login, a league hop, Refresh, and Clear data. The chest `lootList` is written as a JSON array, in the same Chambers key list. Old lines are still read by the same reader.

Leave these unless the owner asks:

- `TargetTimeSettings` still listens for every component the client adds, so the gray Target total, Total points, and PPH can be written when that section joins the settings page. Do not narrow that listener unless those rows have to change.
- Do not claim the developer client has been run on `ChambersSession`, on the settings redraw, or on this writer.

### 3. Keep the log stable

- New Chambers lines stay Chambers-only. Theatre and Tombs keep their current fields.
- Do not rename a key that is already in a log. Adding a key is fine. A missing key must still load.
- Normal saves stay one append. Do not rewrite the whole log to store one raid.
- A failed Chambers append keeps the live raid and tries again on a later tick. A successful append still clears it.
- Do not rewrite the historical Chambers file unless the owner asks. The rare full rewrite (`updateRTList`, `updateRTLog`) writes a temporary file in the same charset, then replaces the log. It must round-trip every value the screens and the uniques list read, including old loot fields when a line has them. It must not recompute `specialLootInOwnName`.
- An unset time is `-1`. A missing int comes back as `0`. The screens treat a time that is not above `0` as absent. Do not omit a field where `0` is a real value, such as a real split of `0`.
- Do not backfill old raids.
- A leagues raid stays in `<username>-league`. Do not append it to the main-game files.
- Do not log a Deadman, beta, tournament, fresh-start, quest-speedrunning, legacy, or EoC world.

### 4. Leave these alone unless the owner asks

- CoXGrind
- Pushing
- Theatre and Tombs logging, screens, and any session or module split for those raids
- `personalDeathCount` for Chambers
- Points, the chest `lootList`, split GP, and one JSON object per line
- Replacing `tektonTime` or the other existing room fields
- Removing `killCountID` or `completionCount`
- Pinning `runeLiteVersion` in the committed `build.gradle`

### 5. Money-run points

Vesp milk, killing rope, and over-thieve add points on top of the saved total: `1000`, `3248`, and `718`. The default total is `59134`, so all three checked is `64100`. Ice milk still changes time only. The owner has not given point amounts for the other money-run boxes.

## Tests and the client

JDK 17 is `C:\Users\DB96\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.17.10-hotspot`. From this clone:

```
$env:JAVA_HOME = "C:\Users\DB96\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
.\gradlew.bat test --tests com.raidtracker.RaidTrackerTest
.\gradlew.bat test --tests com.raidtracker.ChambersViewTest
.\gradlew.bat test --tests com.raidtracker.VanguardTrackerTest
.\gradlew.bat test --tests com.raidtracker.VanguardLogTest
.\gradlew.bat test --tests com.raidtracker.ui.chambers.SplitCopyTest
.\gradlew.bat run
```

`run` loads `src/test/java/com/raidtracker/RunClientAndPlugin.java` with `--developer-mode` and `--debug`. The installed RuneLite shortcut does not load this working tree. The panel reads `%USERPROFILE%\.runelite\raid-data tracker\<username>\cox\raid_tracker_data.log`, and the tob and toa files beside it. A leagues login uses `<username>-league`.

Covered in `RaidTrackerTest`:

- Guardians uses the first time token (`1:47` from a line that also has `Total: 16:16`)
- A floor line with `@mes_hl_mag@`, still taking the last time token, including a lower-floor line with color tags
- Solo Olm: mage hands `30` and `30`, phases `66`, `78`, `54`, head `60`, and `raidTime` and the floors stay unset
- A death from the chat line at `40000` points stores Tekton and `16000`. After Tekton, the next line stores Crabs and `12800`. The room is kept on Challenge Mode, omitted on a short regular raid, and the points stay. `personalDeathCount` stays `0`
- A points drop from `48330` to `30000` with no chat line stores one death of `18330`
- A chat line at `50000` stores `20000` immediately. A later drop to `41000` does not change it or add a death
- Varbit `10055` and the chat line before the points change are one death, including when an unchanged points tick lands between them. At `48000` that loss is `19200`. After the points change, a later chat line is a second death
- After Crabs, a death 5 seconds later is Ice demon. A death under 5 seconds stays on Crabs. At 5 seconds it is Ice demon. A death under 5 seconds after Muttadiles is Muttadiles. Once Olm has started, the room is Olm even inside that window
- Two Chambers purples stay on `uniques`, with their own prices, and no second line is written
- `getJSONString` writes `deaths` and `uniques`, omits the empty loot slots, still writes `specialLoot` when an old Chambers object has it, loads an old `chambersDeaths` line, and an old Theatre/Tombs line without the new fields still loads
- `TestLeagueLogUsesItsOwnFolder`: a leagues world uses `<username>-league`, a normal world does not, and a null username has no folder name. Leagues, a normal world, and a PvP world are tracked. Deadman, beta, and tournament are not. An empty world-type set does not count as a leagues world and does not count as untracked

Covered in `ChambersViewTest`:

- Olm is `raidTime - lowerTime`, and it is absent when the lower floor is missing
- Pre-Olm ignores a room time of `0`. Between rooms is raid time minus Pre-Olm minus Olm
- Regular, Regular full, CM, and solo filters. CM with `11` rooms stays CM
- A `5` second Tekton is shown on the latest raid and left out of the best
- Phase `9` is not shown. Olm P8 is
- A faster Tekton is ahead on the pace line. The personal-best gap is `0` when the comparison is one raid. Personal best line defaults on. Turning it off drops the gap the graph receives, and the table still keeps it
- Target uses the one sheet on All, Regular full, and a numbered size. Crab tank off makes Crabs `1:00`. No twisted bow adds `6` to Shamans. A blank Shamans box is left alone. Last N drops a `5` second Tekton and keeps a `3`-phase Olm when a solo and a team of `8` are mixed. One team of `8` uses four phases. The settings total is `25:35`, and `25:39` with crab tank off. Target total stays editable until the section joins the page, then it is gray and Reset to default is the first row. One raid on This has no personal-best line. An earlier raid of `23:20` puts that line `2:15` above the `25:35` target on This, on a live raid, on Last N, and on Avg. A `5` second raid time is left out. A `26:40` earlier raid sits `1:05` below the target. Vesp milk adds `15` seconds and `1000` points. Killing rope adds `3248` points and over-thieve adds `718`. All three checked, on the `59134` default, is `64100` points. `settingsTotalLine` is `26:53 (25:35 + 1:18)` and `settingsPointsLine` is `64100 (59134 + 4966)` when those three are on, and `25:35` and `59134` when they are off. A blank points box gains nothing.
- Ice demon over `3:50` stays out of averages, bests, and Last N unless Ice milk is on, and then only through `4:30`
- Raids today starts at `4:00`. A `3:59` raid still belongs to the previous day. At `10:00` only the `4:00` raid counts. At `3:59` that raid and the previous evening count, and the `4:00` raid does not yet
- Two own purples and an olmlet: one purple raid, one scroll, three tracked drops, dry streak `0`
- An own purple with no price still counts. A teammate purple does not
- A legacy Elder Maul with `specialLootInOwnName` counts
- Deaths `8000` and `7000` sum to `15000`. `personalDeathCount` is ignored. The room rows are Crabs then Tekton, one death each, because a tie goes by name. A death with no room adds `1500` points and no row

`TestRaidTrackerPanel` is a separate class and reads the local log. Do not treat its `TestFilter` failure on an empty log as a regression of this branch.

## Where the code is

| Path | Role |
| --- | --- |
| `src/main/java/com/raidtracker/RaidTracker.java` | One logged raid. `deaths` accepts the old `chambersDeaths` name. |
| `src/main/java/com/raidtracker/ChambersDeath.java` | One own death. `room` is omitted when unknown. `pointsLost` stays. |
| `src/main/java/com/raidtracker/UniqueDrop.java` | One Chambers unique: item, receiver, own, and purple split fields when set. |
| `src/main/java/com/raidtracker/WorldUtils.java` | Leagues are `WorldType.SEASONAL`. Deadman, beta, tournament, fresh-start, quest-speedrunning, legacy, and EoC are untracked. |
| `src/main/java/com/raidtracker/RaidTrackerConfig.java` | General, the add boxes, and the one Target times sheet. `cmSoloTotal`, `cmPointsLine`, and `cmPph` are calculated. `cmTotalPoints` is the saved points, default `59134`. |
| `src/main/java/com/raidtracker/RaidTrackerPlugin.java` | Event router, shared chat, and the log folder. Keeps `@Subscribe` and calls `ChambersSession`. A `raidtracker` setting calls `redraw()`. `cmSoloTotal`, `cmPointsLine`, and `cmPph` do not. Theatre and Tombs still use the extra line. |
| `src/main/java/com/raidtracker/chambers/ChambersSession.java` | Live Chambers tracking: Olm clock, own deaths, uniques, the Times push, and the Vanguard study. Plain class. The plugin constructs it. |
| `src/main/java/com/raidtracker/vanguard/VanguardNpcs.java` | Ids `7526` through `7529`. `7526` is the walking form. |
| `src/main/java/com/raidtracker/vanguard/VanguardHoles.java` | `A` west, `B` middle, `C` east. A shared east-west line breaks toward the south. |
| `src/main/java/com/raidtracker/vanguard/VanguardSample.java` | One Challenge Mode room. `kc` stays null until the count line. |
| `src/main/java/com/raidtracker/vanguard/VanguardUptime.java` | One time they were up. The file stores letters, not tiles. |
| `src/main/java/com/raidtracker/vanguard/VanguardTracker.java` | Spawn, dig, heal, kill, and leave. A regular raid is ignored. A dig under `20` ticks, or a heal, is forced. |
| `src/main/java/com/raidtracker/vanguard/VanguardLog.java` | Rewrites `cox\vanguards-<hash>.json`. A leagues key is `vanguards-<hash>-league.json` in the league Chambers folder. Hash `0` and `-1` are not keys. |
| `src/main/java/com/raidtracker/filereadwriter/FileReadWriter.java` | Append one line. The chest `lootList` is a JSON array. There is no quote-and-bracket replace. A full rewrite builds the new text in a temporary file, then replaces the log, and does not change `specialLootInOwnName`. A failed append returns false. The reader is unchanged. Chambers key order is `CHAMBERS_LOG_FIELDS`, prep rooms in Challenge Mode order. Theatre and Tombs still serialize the whole object. The account folder is `<username>` or `<username>-league`. |
| `src/main/java/com/raidtracker/ui/RaidTrackerPanel.java` | Top tabs, the CoX second row, and Loot. Remembers Times mode and size as `chambersMode` and `chambersSize`. `redraw()` uses the raids already loaded. `loadRTList` still runs on login, a league hop, Refresh, and Clear data. Uniques and split GP read the old fields and `uniques`. An untracked world shows the disabled view. |
| `src/main/java/com/raidtracker/ui/chambers/ChambersFacts.java` | Derived Chambers numbers. Own purples count without a price. Nothing here is written back. `copyTimes` is the live snapshot. |
| `src/main/java/com/raidtracker/ui/chambers/ChambersFilter.java` | Raid type and party size for Times only. Points does not use it. |
| `src/main/java/com/raidtracker/ui/chambers/ChambersTimes.java` | Average, best, and the pace line. Invalid splits stay off the averages and bests. |
| `src/main/java/com/raidtracker/ui/chambers/ChambersTargets.java` | One target sheet for every filter. Derived Pre-Olm, Olm, and raid time. Time adds change the comparison only. Vesp milk, killing rope, and over-thieve also add points. `settingsTotalLine` and `settingsPointsLine` are the settings text. |
| `src/main/java/com/raidtracker/ui/chambers/TargetTimeSettings.java` | Gray Target total, gray Total points, gray PPH, and Reset to default. Only the added amount is colored. The editable Total Points box stays at the bottom. Written when the section joins the settings page. |
| `src/main/java/com/raidtracker/ui/chambers/ChambersPurples.java` | Purple rate, dry streaks, items, history, points lost, the room counts, tracked drops. |
| `src/main/java/com/raidtracker/ui/chambers/ChambersPurpleView.java` | Points page. Every logged Chambers raid. Item icons are scaled to 28px. Points lost, as purples, and as raids sit above the room list. |
| `src/main/java/com/raidtracker/ui/chambers/ChambersTimesView.java` | Times page. Average, Target, Best, and Filter. This and Last N. The live Current row, the kill count on the Rooms header, the floor gaps, and the pace bar. |
| `src/main/java/com/raidtracker/ui/chambers/PaceLine.java` | Pace graph on Average and Target. The personal-best line is the gap from the zero line to the fastest raid time. Height grows from 156 to at most 300 to fill the sidebar. |
| `src/main/java/com/raidtracker/ui/SplitChanger.java` | Edits a `UniqueDrop` for a new Chambers purple, or the raid fields for an old line. |
| `src/test/java/com/raidtracker/RaidTrackerTest.java` | Chambers log shape and the old Theatre/Tombs cases. Olm, deaths, and settle go through `chambers()`. |
| `src/test/java/com/raidtracker/ChambersViewTest.java` | Times, Target, and Points calculations. The settings total is gray when the section joins the page. Death points sum from the list, and the room rows skip a death with no room. |
| `src/test/java/com/raidtracker/VanguardTrackerTest.java` | Hole letters, gaps, a forced short dig, a kill, a reset, and a regular raid that writes nothing. |
| `src/test/java/com/raidtracker/VanguardLogTest.java` | The study file replaces one room, and a leagues key is a second file. |
| `src/test/java/com/raidtracker/PatchMenuIndex.java` | Masks NPC and player menu indexes with `0xFFFF` on the released injected client. |
| `src/test/java/com/raidtracker/RunClientAndPlugin.java` | Developer client. `gradlew run` calls this. |
| `build.gradle` | `rlVersion` overrides the client. Default stays `latest.release`. `run` loads that client with the NPC menu index masked. |
